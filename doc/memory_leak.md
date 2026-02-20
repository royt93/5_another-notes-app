# Memory Leak Analysis — Another Notes App

> Scanned: `app/src/main/kotlin/com/mckimquyen/notes/`
> Sorted: mức độ nghiêm trọng **tăng dần** (LOW → MEDIUM → HIGH → CRITICAL)
> **Updated 2026-02-20: Tất cả đã được FIX ✅**

---

## 🟡 LOW — Tiềm ẩn, ít khi gây vấn đề thực tế

---

### ✅ [FIXED] LOW-1 — `MainAct` — `exitHandler` giữ Activity qua Runnable

**File:** [`MainAct.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1107another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/main/MainAct.kt) — line 279

**Vấn đề:** `resetExitRunnable` lambda giữ strong reference tới `MainAct`. Trong 2s sau khi user nhấn back, handler giữ activity không được GC.

**Fix đã áp dụng:**

```kotlin
override fun onStop() {
    super.onStop()
    // Fix LOW-1: Remove pending callbacks early so the Runnable cannot hold MainAct in memory
    exitHandler.removeCallbacksAndMessages(null)
}
```

---

### ✅ [FIXED] LOW-2 — `SplashAct` — `finishRunnable` có thể crash khi Activity đã destroyed

**File:** [`SplashAct.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1107another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/splash/SplashAct.kt) — line 21

**Vấn đề:** `Runnable { finish() }` không kiểm tra trạng thái activity trước khi gọi `finish()` trong 300ms delay window.

**Fix đã áp dụng:**

```kotlin
private val finishRunnable = Runnable {
    val act = this@SplashAct
    if (!act.isDestroyed && !act.isFinishing) {
        act.finish()
    }
}
```

---

## 🟠 MEDIUM — Có thể gây vấn đề, nên sửa

---

### ✅ [FIXED] MEDIUM-1 — `RApp.setupAdmob()` — `CoroutineScope` ẩn danh, không track được

**File:** [`RApp.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1107another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/RApp.kt) — line 41

**Vấn đề:** `CoroutineScope(Dispatchers.IO).launch { }` được tạo và không có tham chiếu nào giữ lại để cancel.

**Fix đã áp dụng:**

```kotlin
// Fix MEDIUM-1: Named scope so it is trackable and cancellable
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun setupAdmob() {
    appScope.launch { ... }
}
```

---

### ✅ [FIXED] MEDIUM-2 — `EditFrm` — `setOnTouchListener` không được xóa trong `onDestroyView`

**File:** [`EditFrm.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1107another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/edit/EditFrm.kt) — line 399

**Vấn đề:** Lambda trong `setOnTouchListener` capture `binding.viewBackground`, binding null sau `onDestroyView` nhưng listener không được remove.

**Fix đã áp dụng:**

```kotlin
override fun onDestroyView() {
    super.onDestroyView()
    // Fix MEDIUM-2: Remove TouchListener to release the lambda that captures binding.viewBackground
    binding.recyclerView.setOnTouchListener(null)
    (sharedElementReturnTransition as? MaterialContainerTransform)?.removeListener(transitionListener)
    _binding = null
}
```

---

### ✅ [FIXED] MEDIUM-3 — `AdMobManager.getGAID()` — Raw `Thread` không timeout, không cancel

**File:** [`AdMobManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1107another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/sdkadbmob/AdMobManager.kt) — line 163

**Vấn đề:** Raw `Thread` không thể cancel, có thể block vô thời hạn nếu Play Services không phản hồi.

**Fix đã áp dụng:**

```kotlin
// Fix MEDIUM-3: Replace raw Thread with coroutine + 5s timeout
fun getGAID(context: Context, callback: (String) -> Unit) {
    adManagerScope.launch(Dispatchers.IO) {
        val id = try {
            withTimeout(5_000L) {
                AdvertisingIdClient.getAdvertisingIdInfo(context).id ?: ""
            }
        } catch (e: Exception) { "" }
        withContext(Dispatchers.Main) { callback(id) }
    }
}
```

---

## 🔴 HIGH — Cần sửa sớm

---

### ✅ [FIXED] HIGH-1 — `AlarmReceiver` — CoroutineScope tạo mới mỗi broadcast, không bao giờ cancel

**File:** [`AlarmReceiver.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1107another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/receiver/AlarmReceiver.kt) — line 44

**Vấn đề:** `CoroutineScope(SupervisorJob() + Dispatchers.Main)` tạo mới mỗi broadcast. Nhiều alarm → nhiều scope tích lũy.

**Fix đã áp dụng:**

```kotlin
// Fix HIGH-1: Save scope reference and cancel it after work is done
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
scope.launch {
    try {
        ...
    } finally {
        pendingResult.finish()
        scope.cancel()   // ← scope được cancel sau khi xong
    }
}
```

---

### ✅ [FIXED] HIGH-2 — `AdMobManager.interstitialListener` — Singleton giữ strong ref tới Activity

**File:** [`AdMobManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1107another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/sdkadbmob/AdMobManager.kt) — line 88

**Vấn đề:** `object AdMobManager` là singleton. `interstitialListener` public var giữ anonymous inner class từ Activity → Activity không bao giờ được GC.

**Fix đã áp dụng:**

```kotlin
// Fix HIGH-2: Use WeakReference so listener cannot prevent Activity from being GC'd
private var _interstitialListenerRef: WeakReference<InterstitialAdListener>? = null
var interstitialListener: InterstitialAdListener?
    get() = _interstitialListenerRef?.get()
    set(value) {
        _interstitialListenerRef = value?.let { WeakReference(it) }
    }
```

---

## 🔴 CRITICAL — Nghiêm trọng nhất

---

### ✅ [FIXED] CRITICAL-1 — `AdMobManager.initSplashScreen()` — `collectLatest` vô hạn capture `SplashAct`

**File:** [`AdMobManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1107another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/sdkadbmob/AdMobManager.kt) — line 538

**Vấn đề:** `adManagerScope` (singleton scope) dùng `collectLatest` → loop vô tận, giữ strong reference tới `activity: SplashAct`. Mỗi lần mở app → 1 `SplashAct` instance bị leak vĩnh viễn.

**Fix đã áp dụng:**

```kotlin
// Fix CRITICAL-1: WeakReference + first{} thay vì collectLatest vô hạn
val weakActivity = WeakReference(activity)
val weakCallback = WeakReference(onAdLoaded)
adManagerScope.launch(Dispatchers.Default) {
    // first{} dừng sau khi nhận 1 event — không loop vô hạn
    val value = EventBus.eventFlow.first { it }
    withContext(Dispatchers.Main) {
        val act = weakActivity.get()
        val callback = weakCallback.get()
        if (act == null || act.isDestroyed || callback == null) return@withContext
        loadAppOpenAd(context = act, ...) { result ->
            val currentAct = weakActivity.get()
            val cb = weakCallback.get()
            if (currentAct == null || currentAct.isDestroyed || cb == null) return@loadAppOpenAd
            if (result) showAppOpenAd(currentAct) { cb.invoke() }
            else cb.invoke()
        }
    }
}
```

---

## 📊 Tổng kết — Tất cả đã FIXED ✅

| # | File | Loại | Severity | Status |
|---|------|-------|----------|--------|
| LOW-1 | `MainAct.kt` | Handler/Runnable giữ Activity 2s | 🟡 LOW | ✅ FIXED |
| LOW-2 | `SplashAct.kt` | Runnable không guard isDestroyed | 🟡 LOW | ✅ FIXED |
| MEDIUM-1 | `RApp.kt` | CoroutineScope ẩn danh, không track/cancel | 🟠 MEDIUM | ✅ FIXED |
| MEDIUM-2 | `EditFrm.kt` | TouchListener không remove onDestroyView | 🟠 MEDIUM | ✅ FIXED |
| MEDIUM-3 | `AdMobManager.kt` | Raw Thread không cancel, không timeout | 🟠 MEDIUM | ✅ FIXED |
| HIGH-1 | `AlarmReceiver.kt` | CoroutineScope tạo mới mỗi broadcast | 🔴 HIGH | ✅ FIXED |
| HIGH-2 | `AdMobManager.kt` | Singleton giữ strong InterstitialListener ref | 🔴 HIGH | ✅ FIXED |
| CRITICAL-1 | `AdMobManager.kt` | collectLatest vô hạn capture SplashAct | 🔴 CRITICAL | ✅ FIXED |
