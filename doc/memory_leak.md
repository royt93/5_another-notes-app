# Memory Leak Analysis — Another Notes App

> Scanned: `app/src/main/kotlin/com/mckimquyen/notes/`
> Sorted: mức độ nghiêm trọng **tăng dần** (LOW → MEDIUM → HIGH → CRITICAL)
> **Cập nhật 2026-08-18:** đã audit lại toàn bộ, sửa file/dòng bị lệch, thêm mục "Fix mới" bên dưới.

> ⚠️ **4 mục dưới đây (MEDIUM-1 phần ads, MEDIUM-3, HIGH-2, CRITICAL-1) trỏ vào `AdMobManager.kt` — file này ĐÃ BỊ XOÁ khỏi repo.** Toàn bộ logic AdMob/AppLovin đã được tách ra thư viện ngoài closed-source `com.roy.sdkadbmob` (dùng qua `AdManager`/`AdSdkConfig`), không còn source để audit lại từ repo này. Giữ các mục này lại như tư liệu lịch sử (bug từng tồn tại, cách nghĩ khi sửa), nhưng **không thể xác nhận trạng thái fix hiện tại** — nếu nghi ngờ leak liên quan ads, phải hỏi bên giữ thư viện `com.roy.sdkadbmob`.

---

## 🟡 LOW — Tiềm ẩn, ít khi gây vấn đề thực tế

---

### ✅ [FIXED] LOW-1 — `MainAct` — `exitHandler` giữ Activity qua Runnable

**File:** `app/src/main/kotlin/com/mckimquyen/notes/ui/main/MainAct.kt` — field khai báo dòng 78, cleanup dòng 345

**Vấn đề:** `resetExitRunnable` lambda giữ strong reference tới `MainAct`. Trong 2.5s sau khi user nhấn back, handler giữ activity không được GC.

**Fix đã áp dụng:**

```kotlin
override fun onStop() {
    super.onStop()
    exitHandler.removeCallbacksAndMessages(null)
}
```

---

### ✅ [FIXED] LOW-2 — `SplashActivity` — `finishRunnable` có thể crash khi Activity đã destroyed

**File:** `app/src/main/kotlin/com/mckimquyen/notes/ui/splash/SplashActivity.kt` — dòng 36-40

> Tên class thực tế là `SplashActivity`, không phải `SplashAct` — **cố ý giữ nguyên** vì `AdManager`'s ProcessLifecycle match theo `simpleName == "SplashActivity"` để biết khi nào bỏ qua App Open Resume (xem comment đầu file).

**Vấn đề:** `Runnable { finish() }` không kiểm tra trạng thái activity trước khi gọi `finish()` trong 300ms delay window.

**Fix đã áp dụng:**

```kotlin
private val finishRunnable = Runnable {
    if (!isDestroyed && !isFinishing) {
        finish()
    }
}
```

---

## 🟠 MEDIUM — Có thể gây vấn đề, nên sửa

---

### ⚠️ [UNAUDITABLE] MEDIUM-1 — `RApp.setupAdmob()` (nay là `setupAds()`) — `CoroutineScope` ẩn danh, không track được

**File:** `app/src/main/kotlin/com/mckimquyen/notes/RApp.kt` — hàm `setupAds()` dòng 88

**Vấn đề gốc:** `CoroutineScope(Dispatchers.IO).launch { }` được tạo và không có tham chiếu nào giữ lại để cancel.

**Trạng thái hiện tại:** `RApp.kt` hiện **không còn bất kỳ `CoroutineScope`/`appScope` nào** — `setupAds()` chỉ gọi thẳng `AdManager.setConfig(...)`/`AdManager.initialize(...)` (SDK ngoài tự quản lý coroutine scope của nó). Không thể xác nhận SDK ngoài có áp dụng pattern `appScope` (`SupervisorJob() + Dispatchers.IO`) hay không.

---

### ✅ [FIXED] MEDIUM-2 — `EditFrm` — `setOnTouchListener` không được xóa trong `onDestroyView`

**File:** `app/src/main/kotlin/com/mckimquyen/notes/ui/edit/EditFrm.kt` — `onDestroyView()` dòng 746-760 (đã dịch chuyển so với dòng 399 ghi trước đây)

**Vấn đề:** Lambda trong `setOnTouchListener` capture `binding.viewBackground`, binding null sau `onDestroyView` nhưng listener không được remove.

**Fix đã áp dụng:** (giữ nguyên logic, chỉ dịch dòng)

```kotlin
override fun onDestroyView() {
    super.onDestroyView()
    binding.recyclerView.setOnTouchListener(null)
    (sharedElementReturnTransition as? MaterialContainerTransform)?.removeListener(transitionListener)
    _binding = null
}
```

---

### ⚠️ [UNAUDITABLE] MEDIUM-3 — `AdMobManager.getGAID()` — Raw `Thread` không timeout, không cancel

File `AdMobManager.kt` đã bị xoá khỏi repo (xem cảnh báo đầu file). Không thể audit lại.

---

## 🔴 HIGH — Cần sửa sớm

---

### ✅ [FIXED] HIGH-1 — `AlarmReceiver` — CoroutineScope tạo mới mỗi broadcast, không bao giờ cancel

**File:** `app/src/main/kotlin/com/mckimquyen/notes/receiver/AlarmReceiver.kt`

**Vấn đề:** `CoroutineScope(SupervisorJob() + Dispatchers.Main)` tạo mới mỗi broadcast. Nhiều alarm → nhiều scope tích lũy.

**Fix đã áp dụng:**

```kotlin
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
scope.launch {
    try {
        ...
    } finally {
        pendingResult.finish()
        scope.cancel()
    }
}
```

---

### ⚠️ [UNAUDITABLE] HIGH-2 — `AdMobManager.interstitialListener` — Singleton giữ strong ref tới Activity

File `AdMobManager.kt` đã bị xoá khỏi repo (xem cảnh báo đầu file). Không thể audit lại.

---

## 🔴 CRITICAL — Nghiêm trọng nhất

---

### ⚠️ [UNAUDITABLE] CRITICAL-1 — `AdMobManager.initSplashScreen()` — `collectLatest` vô hạn capture `SplashAct`

File `AdMobManager.kt` đã bị xoá khỏi repo (xem cảnh báo đầu file). Không thể audit lại. Ghi chú lịch sử: fix gốc dùng `EventBus.eventFlow.first {}` (không phải `collectLatest`) + `WeakReference` để tránh leak `SplashActivity` — pattern này vẫn được nhắc lại trong `CLAUDE.md` như quy ước bắt buộc khi có ai chạm lại vào code load App Open ad trong `SplashActivity`, dù bản thân code đó giờ nằm trong thư viện ngoài.

---

## 📊 Tổng kết (bản gốc, tới 2026-02-20)

| # | File | Loại | Severity | Status |
|---|------|-------|----------|--------|
| LOW-1 | `MainAct.kt` | Handler/Runnable giữ Activity 2.5s | 🟡 LOW | ✅ FIXED |
| LOW-2 | `SplashActivity.kt` | Runnable không guard isDestroyed | 🟡 LOW | ✅ FIXED |
| MEDIUM-1 | `RApp.kt` | CoroutineScope ẩn danh, không track/cancel | 🟠 MEDIUM | ⚠️ Đã đổi kiến trúc, không audit lại được |
| MEDIUM-2 | `EditFrm.kt` | TouchListener không remove onDestroyView | 🟠 MEDIUM | ✅ FIXED |
| MEDIUM-3 | `AdMobManager.kt` | Raw Thread không cancel, không timeout | 🟠 MEDIUM | ⚠️ File đã xoá, không audit lại được |
| HIGH-1 | `AlarmReceiver.kt` | CoroutineScope tạo mới mỗi broadcast | 🔴 HIGH | ✅ FIXED |
| HIGH-2 | `AdMobManager.kt` | Singleton giữ strong InterstitialListener ref | 🔴 HIGH | ⚠️ File đã xoá, không audit lại được |
| CRITICAL-1 | `AdMobManager.kt` | collectLatest vô hạn capture SplashAct | 🔴 CRITICAL | ⚠️ File đã xoá, không audit lại được |

---

## 🆕 Fix mới phát hiện — Sprint 1-3 (2026-08-17 → 2026-08-18)

Phát hiện qua audit toàn diện source code + code review, xem chi tiết đầy đủ ở `doc/task/BACKLOG.md` và `doc/task/todo/FIX.md`.

| FIX-ID | File | Vấn đề | Commit |
|---|---|---|---|
| FIX-H09 | `ui/home/HomeFrm.kt` | Sticky LiveData redeliver khi fragment recreate → double interstitial ad sau khi xoá note; dedup guard `lastAdEvent` bị đặt local trong hàm nên mất mỗi lần view tạo lại | `8653f03` |
| FIX-M04 | `widget/NoteCountWidget.kt` | `onUpdate()` dùng `CoroutineScope(Dispatchers.IO).launch{}` không `goAsync()`, không huỷ — cùng anti-pattern với HIGH-1 gốc nhưng tái diễn ở widget | `4a11dd5` |
| FIX-M23 | `model/DefaultNotesRepository.kt`, `model/DefaultLabelsRepository.kt` | Một số method ghi DB thiếu bọc `withContext(NonCancellable)`, có thể bị huỷ giữa chừng khi `onPause`/scope cancel, để dữ liệu dở dang | `d016cfd` |
| FIX-L03 | `ui/vip/VipFrm.kt` | Animation one-shot (confetti, celebrate, `postDelayed` 1700ms) không bị huỷ trong `onDestroyView()` — chỉ animator lặp mới được cancel trước đó | `e8d8a6b` |
| FIX-M06 | `ui/vip/VipFrm.kt` | Dialog thường (không phải `DialogFragment`) trong `showActivateDialog()`/`showResetConfirm()`/nhánh "ad not ready" leak window nếu Activity recreate khi đang mở | `cdbb586` |

**Quy ước memory-leak hiện hành (trích từ `CLAUDE.md`, vẫn còn hiệu lực):**
- Activity/fragment phải clear handler callback trong `onStop`/`onDestroyView`.
- Coroutine scope cấp Application phải đặt tên (`appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`), không tạo `CoroutineScope(...)` ẩn danh tại field-init.
- `BroadcastReceiver` dùng `goAsync()` phải cancel scope trong `finally` sau `pendingResult.finish()`.
- Listener singleton (AdMob/AppLovin, hiện nằm trong thư viện ngoài `com.roy.sdkadbmob`) phải giữ Activity qua `WeakReference`.
- Dialog thường (`AlertDialog`/`MaterialAlertDialogBuilder`, không phải `DialogFragment`) mở trong Fragment/Activity phải được track và `dismiss()` trong `onDestroyView()`/`onDestroy()` — quy ước mới thêm sau FIX-M06.
