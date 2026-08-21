# AD MIGRATION — Cat Note → AdmobApplovinWrapper 1.1.3

> **Date:** 2026-04-26 · **Status:** ✅ Build pass (devDebug). Provider: **AppLovin MAX** (`IS_ENABLE_ADMOB=false`).
>
> Migration thay thế hoàn toàn module `com.mckimquyen.notes.sdkadbmob.AdMobManager` (singleton 729 dòng) bằng SDK `com.github.royt93:AdmobApplovinWrapper:1.1.3` (`com.roy.sdkadbmob.AdManager`). KHÔNG thêm bất kỳ touchpoint quảng cáo mới nào (theo yêu cầu).

---

## 1. Touchpoints — TRƯỚC / SAU

| # | Vị trí | Loại ad | TRƯỚC migrate | SAU migrate | Note |
|---|---|---|---|---|---|
| 1 | `RApp.onCreate` | Init SDK | `MobileAds.initialize` + `AdMobManager.init` (2 lệnh, manual scope) | `AdManager.setConfig` + `AdManager.initialize` (1 facade) | SDK orchestrate `earlyInit → MobileAds/AppLovinSdk init → init (GAID/VIP) → registerAppOpenAdLifecycle` |
| 2 | `SplashActivity.onCreate` (tên class thực tế, không phải `SplashAct`) | App Open (splash) | `AdMobManager.initSplashScreen(this, onAdLoaded)` | `AdManager.initSplashScreen(this, onAdLoaded)` | API signature giữ nguyên — drop-in. SDK có HARD CAP 8s + AtomicBoolean callback fire 1 lần |
| 3 | `RApp.onCreate` (auto) | App Open (resume from background) | ❌ Không có (block `AppLifecycleListener` đã bị comment) | ✅ Auto qua `ProcessLifecycle` (SDK đăng ký bên trong `initialize()`) | 3 lớp gate: cold-start skip + ≥5s background + rapid-resume detect. **Auto skip nếu top activity là `AppLovinFullscreenActivity` / `AdActivity`** (fix 1.1.3 cho fullscreen-collision khi user click ad → quay lại app) |
| 4 | `HomeFrm.onCreate` | Interstitial preload | `AdMobManager.loadInterstitial(ctx, BuildConfig.ADMOB_INTERSTITIAL_ID)` | `AdManager.loadInterstitial(ctx)` (ID lấy từ `AdSdkConfig`) | Auto-preload sau dismiss |
| 5 | `HomeFrm` FAB click → tạo note | Interstitial show | `AdMobManager.showInterstitial(act) { ok -> viewModel.createNote() }` | `AdManager.showInterstitial(act) { ok -> viewModel.createNote() }` | Callback fire LUÔN 1 lần (success / fail / skip) |
| 6 | `HomeFrm` quan sát `statusChangeEvent.DELETED` | Interstitial show | `AdMobManager.showInterstitial(act) { … }` | `AdManager.showInterstitial(act) { … }` | Same |
| 7 | `SettingsFrm.onViewCreated` | Banner | `AdMobManager.loadBanner(ctx, ADMOB_BANNER_ID, container, tvLabelAd, AdSize.LARGE_BANNER)` + manual `adView.resume/pause/destroy` | `AdManager.loadBanner(ctx, container, tvLabelAd, AdManager.getAdaptiveBannerSize(act))` + `AdManager.bannerDestroy(adView)` trong `onDestroyView` | **Auto-lifecycle** (`autoManageLifecycle=true` mặc định) — SDK tự hook `ActivityLifecycleCallbacks` cho resume/pause. Adaptive banner thay LARGE_BANNER cố định |

> **Reward ad** — ⚠️ **LỖI THỜI, xem mục 19 Round-5 (2026-08-18).** Dòng này đúng tại thời điểm viết (2026-04-26), nhưng `ui/vip/VipFrm.kt` sau đó đã thêm touchpoint reward ad thật (`AdManager.loadRewarded`/`showRewarded`, nút "Watch Ad → 3 ngày VIP").

---

## 2. Files thay đổi

### Build

| File | Thay đổi |
|---|---|
| `build.gradle` (root) | Thêm plugin `com.google.devtools.ksp:2.0.21-1.0.28 apply false` |
| `app/build.gradle` | Bỏ `id "kotlin-kapt"` → thêm `id "com.google.devtools.ksp"`. `kapt` → `ksp` cho Dagger + Room. Bump Room `2.6.1 → 2.7.0`. Bỏ `play-services-ads` + `applovin-mediation` (SDK pull transitively). Thêm dependency `com.github.royt93:AdmobApplovinWrapper:1.1.3`. Thêm 5 buildConfigField AppLovin (`SDK_KEY`, `BANNER_ID`, `INTERSTITIAL_ID`, `APP_OPEN_ID`, `REWARDED_ID`). Thêm `IS_ENABLE_ADMOB=false` cho cả `debug` + `release`. Thêm `ADMOB_REWARDED_ID` (giá trị test cho debug, rỗng cho release). Bỏ `RoomSchemaArgProvider` class + `javaCompileOptions.annotationProcessorOptions` block. Thêm `ksp { arg("room.schemaLocation", ...) }` |

### Code

| File | Thay đổi |
|---|---|
| `RApp.kt` | Bỏ import `MobileAds`, `AdMobManager`, `appScope`. Thay `setupAdmob()` bằng `setupAds()` gọi `AdManager.setConfig(AdSdkConfig(...)) + AdManager.initialize()`. `vipKeySecret = "CATNOTE_VIP_2026"` (đã đổi khỏi default SDK) |
| `ui/splash/SplashAct.kt` | Đổi import `AdMobManager → AdManager`. Tên hàm `initSplashScreen` giữ nguyên |
| `ui/home/HomeFrm.kt` | Đổi `AdMobManager → AdManager`. Đổi interface `AdMobManager.InterstitialAdListener` → `InterstitialAdListener` (top-level). Bỏ import `com.google.android.gms.ads.AdError, LoadAdError`, thay bằng `com.roy.sdkadbmob.AdError` (sealed class). `loadInterstitial(ctx, id)` → `loadInterstitial(ctx)`. `onAdFailedToLoad(LoadAdError)` → `onAdFailedToLoad(AdError)`. Bỏ gọi `clearPendingCallbacks` (SDK auto-clear) |
| `ui/setting/SettingsFrm.kt` | Đổi `AdMobManager → AdManager`. Bỏ import `AdSize`, `AdView`. Thay `adView: AdView?` → `adView: View?`. Bỏ `onResume/onPause` manual resume/pause. Thêm `onDestroyView` gọi `bannerDestroy(adView)` (sớm hơn Activity-level cleanup của SDK). `AdSize.LARGE_BANNER` → `AdManager.getAdaptiveBannerSize(requireActivity())`. Bỏ `adView?.destroy()` trong `onDestroy` (dead code sau migrate) |
| `di/AppModule.kt` | KSP-Dagger không support `@get:Binds val ext.bind: T` → đổi sang `@Binds abstract fun bindXxx(impl: Impl): Interface` (3 binding) |
| `di/BuildTypeModule.kt` (debug + release source set) | Cùng convert `@get:Binds val` → `@Binds abstract fun` |
| `model/converter/NoteMetadataConverter.kt` | Khai báo explicit return type cho `toMetadata` và `toString` (Room KSP infer sai khi expression-body có `try { } catch { throw }` → infer `Nothing`) |

### Layout

| File | Thay đổi |
|---|---|
| `res/layout/layout_ad_banner.xml` | Rewrite theo cấu trúc SDK: thêm 2 `com.roy.sdkadbmob.ShimmerView` (tag `shimmerLabel`/`shimmerBanner`) — SDK `loadBanner` lookup theo tag + start/stop animation tự động. Default `visibility="gone"` cho cả 4 view (1.1.3 fix VIP-shimmer-loop) |

### Manifest

| File | Thay đổi |
|---|---|
| `AndroidManifest.xml` | **Không thay đổi.** AdMob `APPLICATION_ID` meta-data giữ vì SDK pull `play-services-ads` transitively (Mobile Ads SDK crash nếu thiếu meta-data, kể cả khi không dùng AdMob runtime). AppLovin không cần meta-data — SDK key truyền qua `AdSdkConfig.applovinSdkKey` |

### Files xoá

| File | Lý do |
|---|---|
| `app/src/main/kotlin/com/mckimquyen/notes/sdkadbmob/AdMobManager.kt` (729 dòng) | Bị thay hoàn toàn bởi SDK |
| `app/src/main/kotlin/com/mckimquyen/notes/ext/Applovin.kt` | Toàn bộ comment, dead code |

---

## 3. Cấu hình AppLovin (đã apply)

```groovy
// app/build.gradle defaultConfig
buildConfigField "String", "APPLOVIN_SDK_KEY",         "\"e75FnQfS9XTTqM1Kne69U7PW_MBgAnGQTFvtwVVui6kRPKs5L7ws9twr5IQWwVfzPKZ5pF2IfDa7lguMgGlCyt\""
buildConfigField "String", "APPLOVIN_BANNER_ID",       "\"9f19f76fe682443f\""
buildConfigField "String", "APPLOVIN_INTERSTITIAL_ID", "\"34eaf20efa37ac75\""
buildConfigField "String", "APPLOVIN_APP_OPEN_ID",     "\"7153ba6828a2f672\""
buildConfigField "String", "APPLOVIN_REWARDED_ID",     "\"8eecf725822d50b7\"" // unused — no touchpoint

// Provider switch — both buildTypes
buildConfigField "Boolean", "IS_ENABLE_ADMOB", "false"
```

`vipKeySecret` đã đặt `"CATNOTE_VIP_2026"` trong `RApp.setupAds()` — đổi nếu muốn key nội bộ riêng.

---

## 4. AdSafety active (compliance)

SDK ship preset `CONTENT` (default) — không cần config:

| Lớp | Ngưỡng |
|---|---|
| Min session before 1st ad | 10s (splash bypass) |
| Throttle giữa fullscreen | 60s |
| Session cap | 6 fullscreen / lần mở app |
| Hourly cap | 3 fullscreen / giờ |
| Daily cap | 10 fullscreen / ngày (reset 00:00, persist atomic) |
| App Open Resume — Min background | 5s |
| App Open Resume — Rapid resume | ≤ 3 / phút |
| Banner click rate | ≤ 3 / phút |
| Suspicious CTR | > 7% với impressions ≥ 10 → progressive pause 30→60→120→240→480 phút (cap 24h) |

Cold-start skip cho App Open lần đầu mở app (anti-Invalid Traffic AdMob/AppLovin policy).

---

## 5. Vòng đời banner & app open — chú ý đã xử

### Banner
- `autoManageLifecycle=true` (default): SDK tự `bannerResume` ở `Activity.onResume`, `bannerPause` ở `onPause`, `bannerDestroy` ở `onDestroy` qua `ActivityLifecycleCallbacks`.
- **Fragment-level early cleanup**: `SettingsFrm.onDestroyView` gọi `bannerDestroy(adView)` trước Activity destroy → tránh banner orphan khi Fragment view huỷ mà Activity còn sống.
- Idempotent: `bannerDestroy` gọi nhiều lần không lỗi.
- VIP / no-network → `loadBanner` fail-soft, `tvLabelAd` + `bannerContainer` + shimmer set `gone`.

### App Open
- **Splash**: `initSplashScreen(activity, onAdLoaded)` — HARD CAP 8s. AppLovin init <1s nên gần như luôn show. Callback fire 1 lần qua `AtomicBoolean.compareAndSet`.
- **Resume từ background**: auto qua `ProcessLifecycle.onStart`. SDK skip nếu:
  1. Cold start lần đầu
  2. Background < 5s (rapid foreground/background)
  3. > 3 lần resume / phút (rapid resume)
  4. Top activity là `AppLovinFullscreenActivity` / `com.google.android.gms.ads.AdActivity` (= user vừa click ad → mở Chrome → quay lại app, **không** show App Open lần nữa). Fix 1.1.3.
- WeakReference Activity nội bộ → activity GC bình thường khi finish.

---

## 6. Vi phạm policy đã review — KHÔNG có

| Policy | Status |
|---|---|
| AdMob: cold-start App Open ≥ 5s app foreground | ✅ ProcessLifecycle skip lần đầu |
| AdMob: không show ad đè ad | ✅ `isAppOpenShowing` / `isInterstitialShowing` flag check |
| AdMob: anti Invalid Traffic — CTR cap | ✅ 7% threshold + progressive pause |
| AppLovin: rate limit interstitial | ✅ 60s throttle + daily cap 10 |
| AppLovin: không gọi `MaxInterstitialAd.showAd` khi `isReady=false` | ✅ SDK guard, fire `onAdNotAvailable` callback |
| AdMob/AppLovin: test ad ID trên thiết bị real | ✅ Debug build dùng AdMob sample IDs (sẽ không apply vì AppLovin active); SDK auto-register debug GAID làm AppLovin test device |
| Hide ad badge (không dán "Sponsored Content" giả) | ✅ `tvLabelAd` text "Ad" — SDK string `@string/ad_label` |
| TEST preset trong release | ✅ Không dùng (mặc định CONTENT) |
| Không hardcode SDK key vào res/values/strings (có thể bị scrape) | ✅ Đặt trong `BuildConfigField` (vẫn có trong APK nhưng đỡ public hơn) |

---

## 7. Memory & lifecycle audit

SDK 1.1.3 đã hardening:
- 5 listener (banner / interstitial / appOpen / rewarded / paidEvent) auto-clear khi `Activity.onDestroyed` nếu `listener === activity`.
- `BannerTracker` — WeakHashMap key + WeakReference value, 3 dedicated locks, idempotent `markDestroyed` guard.
- AppLovin `MaxInterstitialAd` / `MaxRewardedAd` owner-WeakRef → destroy khi owner activity destroy.
- `fullScreenContentCallback = null` trước destroy → cắt ref chain.
- Shared `mainHandler` — `removeCallbacksAndMessages(null)` dọn sạch trong `destroy()`.
- `@Volatile` + `Atomic` cho mutable state thread-safe; `AppPreferences` synchronized counter.

Tại layer app:
- `RApp.appScope` cũ đã bỏ — SDK tự quản coroutine scope.
- `HomeFrm.onDestroyView`: `interstitialListener = null` (belt + suspenders với SDK auto-clear).
- `SettingsFrm.onDestroyView`: `bannerDestroy(adView)` + `adView = null`.
- KHÔNG còn raw `Thread.start`, `GlobalScope`, anonymous `CoroutineScope`.

---

## 8. Build verify

```bash
./gradlew :app:assembleDevDebug   # ✅ BUILD SUCCESSFUL in 11s
```

Còn warning deprecation (statusBarColor, overridePendingTransition, setRemoteAdapter) — pre-existing, không liên quan migration.

`devRelease` / `productionRelease` cần keystore.jks thật để sign — chưa verify build full. Tuy nhiên ProGuard rules: `app/proguard-rules.pro` có sẵn keep rules cho AdMob/AppLovin/Play services từ session trước, plus SDK ship `consumer-rules.pro` auto-inherited → an toàn.

---

## 9. Checklist triple-check (đã pass)

| # | Check | Status |
|---|---|---|
| 1 | Splash App Open touchpoint giữ nguyên (`SplashAct.onCreate` → `initSplashScreen`) | ✅ |
| 2 | Background App Open auto-wired qua `AdManager.initialize()` ProcessLifecycle hook | ✅ |
| 3 | Banner touchpoint giữ nguyên (`SettingsFrm.onViewCreated`); auto-lifecycle thay manual resume/pause/destroy | ✅ |
| 4 | Interstitial touchpoint giữ nguyên (HomeFrm FAB click + delete event) | ✅ |
| 5 | Reward ad: config có (theo SDK requires field), KHÔNG có code call → đúng yêu cầu "chỉ thay touchpoint sẵn" | ⚠️ Lỗi thời — xem mục 19, `VipFrm` đã thêm touchpoint reward thật |
| 6 | `AdMobManager` (file 729 dòng) đã xoá; toàn bộ caller trỏ về `com.roy.sdkadbmob.AdManager` | ✅ |
| 7 | Build success `:app:assembleDevDebug` | ✅ |
| 8 | Provider AppLovin (IS_ENABLE_ADMOB=false), 5 ID + SDK key đã đặt | ✅ |
| 9 | Memory: `clearPendingCallbacks` cũ đã bỏ, SDK auto-clear; banner `bannerDestroy` ở Fragment level | ✅ |
| 10 | Policy: AdSafety CONTENT preset, ProcessLifecycle skip khi top activity là ad activity, cold-start skip | ✅ |

---

## 10. Bổ sung cần (nếu muốn nâng cấp tiếp)

Những thứ user **không** yêu cầu nhưng có thể bật khi cần:

| Feature | Cách bật |
|---|---|
| **UMP Consent (GDPR EEA/UK/CH)** | Trong `SplashAct.onCreate`, gọi `AdManager.requestConsentInfoUpdate(this, false) { canRequestAds -> ... }` trước `initSplashScreen`. Bắt buộc Google Play policy 2024+ nếu app có user EEA |
| **Privacy Settings button** trong app | `AdManager.showConsentFormIfAvailable(this) { ... }` |
| **VIP whitelist GAID server-side** | Backend trả list GAID paid users → `AdManager.addVIPMember(list)` |
| **VIP by Key dialog** | `AdManager.showVipByKeyDialog(this, defaultDays = 30) { activated, days -> ... }` (key đang `CATNOTE_VIP_2026`) |
| **Revenue tracking** | `AdManager.paidEventListener = PaidEventListener { adType, valueMicros, currency, _, source -> Firebase.analytics.logEvent(...) }` |
| **Crashlytics hook** | `AdManager.errorReporter = ErrorReporter { t, ctx -> Crashlytics.recordException(t) }` |

---

## 11. Risks / Known caveats

1. **AppLovin SDK key public trong APK**: `BuildConfig` field vẫn ship vào APK — nếu cần rotate key, phải release APK mới. Cân nhắc dùng `local.properties` + đọc từ Gradle.
2. **Build config Java 8** vs SDK compiled với Java 17 toolchain: SDK module cần JVM 17 lúc compile/build artifact — nhưng artifact đã được publish pre-built ở JitPack với target `1.8`/`17` (compatible). Project compileOptions vẫn `JavaVersion.VERSION_1_8` + Java toolchain 17 → OK.
3. **App Open Resume lần đầu kích hoạt** sau migrate (cũ chưa có) — user sẽ thấy ad mỗi lần resume từ background ≥5s. Nếu UX không muốn, gọi `AdManager.appOpenListener` để theo dõi hoặc disable bằng cách xoá `registerAppOpenAdLifecycle` (cần sửa SDK, không khuyến nghị).
4. **Production release variant chưa verify build**: cần keystore.jks thật. ProGuard rules đã có nhưng release-only failure mode chỉ phát hiện sau khi build/sign thật.
5. ~~**Reward ad ID đã set** dù không dùng: nếu sau này thêm reward, đã sẵn.~~ Đã dùng — xem mục 19, `VipFrm` (`ui/vip/VipFrm.kt`) gọi `AdManager.loadRewarded`/`showRewarded` thật cho nút "Watch Ad → 3 ngày VIP".

---

## 12. Tóm tắt 1 dòng

`AdMobManager` (729 dòng monolith) → `com.roy.sdkadbmob.AdManager` (1.1.3) qua KSP-Dagger/Room. AppLovin active, App Open splash + background, banner adaptive auto-lifecycle, interstitial 7-layer AdSafety. Build `:app:assembleDevDebug` pass. KHÔNG thêm touchpoint mới.

---

## 13. Round-2 audit — issues fixed (2026-04-26)

Sau khi triple-check, 3 vấn đề đã được apply fix:

### 13.1. ✅ Renamed `SplashAct` → `SplashActivity` (lifecycle bug)

**Vấn đề**: SDK `ProcessLifecycle.onStart` (`AdManager.kt:1500`) hard-code check `simpleName == "SplashActivity"` để skip App Open Resume khi user còn trên splash. Class cũ `SplashAct` không match → có nguy cơ collision với splash flow đang load App Open của riêng nó (mitigated bởi `isShowing` flag nhưng vẫn nên fix đúng).

**Fix**:
- `app/src/main/kotlin/com/mckimquyen/notes/ui/splash/SplashAct.kt` → `SplashActivity.kt` (rename class + file)
- `AndroidManifest.xml`: `.ui.splash.SplashAct` → `.ui.splash.SplashActivity`
- `di/AppComponent.kt`: import + `inject(activity: SplashActivity)`

**Impact**: Khi user background trong khi splash đang load → foreground → SDK nhận diện đúng và skip App Open Resume, để splash flow tự xử.

### 13.2. ✅ Bỏ `setCurrentActivity` thừa trong `HomeFrm.onCreate`

**Vấn đề**: SDK auto-track `currentActivity` qua `ActivityLifecycleCallbacks.onActivityCreated/Started/Resumed` (`AdManager.kt:1393, 1398, 1403`). Manual `AdManager.setCurrentActivity(requireActivity())` trong `HomeFrm.onCreate` → bị ghi đè ngay sau bởi callback của SDK. Dead code.

**Fix**: Xoá dòng `AdManager.setCurrentActivity(requireActivity())` trong `HomeFrm.onCreate`.

### 13.3. ✅ Bỏ `InterstitialAdListener` implement trong `HomeFrm` (không dùng)

**Vấn đề**: `HomeFrm` implements `InterstitialAdListener` với 7 method override empty `{}`. Listener set ở `onCreate` (chạy 1 lần / Fragment instance), clear ở `onDestroyView` (chạy mỗi lần view huỷ). Khi Fragment vào back-stack rồi quay lại → view recreate nhưng `onCreate` không fire → listener còn null. Dù vậy: 7 override empty → không tác dụng nào.

**Fix**: Bỏ luôn `implements InterstitialAdListener` + 7 method + `interstitialListener = this/null` set/clear. Show callback `{ ok -> viewModel.createNote() }` của `showInterstitial(activity, callback)` vẫn fire bình thường (callback này không phụ thuộc listener).

**Impact**: Bớt 8 dòng dead code, bớt rủi ro lifecycle race.

---

## 14. PLAN — Triển khai tính năng mới của SDK

> **Phạm vi**: PLAN, không tự apply ngay. Mỗi feature có touchpoint, API, dependency và effort estimate. User chọn cái nào triển khai trước, tôi sẽ apply.

### 🔴 Priority 1 — UMP Consent (compliance bắt buộc)

**Lý do**: Google Play policy 2024+ **yêu cầu** show consent form trước khi load ad cho user EEA / UK / Switzerland. Không có → có thể bị Play Store cảnh báo / suspend listing.

| Item | Detail |
|---|---|
| **Touchpoint** | `SplashActivity.onCreate` — gọi UMP **TRƯỚC** `initSplashScreen` |
| **Touchpoint phụ** | `SettingsFrm` — thêm 1 Preference button "Privacy Settings" cho user re-consent (UMP policy yêu cầu) |
| **API** | `AdManager.requestConsentInfoUpdate(activity, false) { canRequestAds -> ... }` + `AdManager.showConsentFormIfAvailable(activity) { }` |
| **Dependency** | UMP SDK đã ship trong wrapper SDK transitively — không cần thêm |
| **User input cần** | (Optional) Test geography flag trong AdMob console nếu muốn force show form |
| **Effort** | ~30–40 dòng code: 15 ở SplashActivity, 15 ở SettingsFrm, 1 string resource |
| **Risk** | UMP form fail → có thể block splash. Cần fallback: timeout 3-5s rồi `navigateToMain()` mà không load ad |

**Sketch code SplashActivity:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.a_splash)
    AdManager.requestConsentInfoUpdate(this, tagForUnderAgeOfConsent = false) { canRequestAds ->
        if (canRequestAds) {
            AdManager.initSplashScreen(this) { goToMain() }
        } else {
            goToMain()  // user từ chối consent → skip ad, vào app luôn
        }
    }
}
```

**Sketch code SettingsFrm**: thêm 1 Preference với key `privacy_settings`, click → `AdManager.showConsentFormIfAvailable(requireActivity()) { }`. String resource VN: `@string/pref_privacy_settings`.

---

### 🔴 Priority 2 — VIP (whitelist GAID + VIP by Key)

VIP user = không thấy bất kỳ ad nào (banner / interstitial / app open / rewarded). 2 cơ chế song song hỗ trợ:

#### 2.1. VIP Whitelist GAID (server-driven)

| Item | Detail |
|---|---|
| **Touchpoint** | Bất kỳ chỗ nào sau khi user đăng nhập / fetch user profile từ backend |
| **API** | `AdManager.addVIPMember(listOf("gaid1", "gaid2"))` / `AdManager.deleteVIPMember(listOf("gaid"))` |
| **Persist** | SDK lưu list vào SharedPreferences, lần app mở sau tự reload |
| **Debug** | `AdManager.getCurrentDeviceGAID()` để lấy GAID device hiện tại |
| **User input cần** | **Backend endpoint** trả list GAID paid users. Dự án hiện không có backend → feature này chỉ kích hoạt khi có |
| **Effort** | ~10 dòng code khi đã có endpoint. ~0 dòng khi chưa có |
| **Risk** | Nếu chưa có server → bỏ qua mục này, chỉ dùng VIP by Key (#2.2) |

#### 2.2. VIP by Key (manual unlock — dùng cho promo code, beta tester, internal team)

`vipKeySecret = "CATNOTE_VIP_2026"` đã set sẵn trong `RApp.setupAds()`.

| Item | Detail |
|---|---|
| **Touchpoint** | `SettingsFrm` — thêm 1 Preference "Enter VIP key" |
| **API** | `AdManager.showVipByKeyDialog(requireActivity(), defaultDays = 30) { activated, days -> if (activated) showVipBadge() }` (SDK ship Material You dialog sẵn) |
| **Activate trực tiếp** (deep-link `myapp://activate?key=xxx&days=N`) | `AdManager.activateVipByKey(this, key, days)` |
| **Đọc trạng thái** | `AdManager.isVipByKeyActive()` / `getVipByKeyExpiry()` |
| **Clear** | `AdManager.clearVipByKey()` |
| **Effort** | ~15 dòng SettingsFrm + 1 string |
| **Risk** | `vipKeySecret` lưu trong `BuildConfig` → ai decompile APK đều thấy. OK với app notes (low-stakes), không phù hợp app tài chính |

**Sketch SettingsFrm** thêm: Preference `vip_key` → `setOnPreferenceClickListener { AdManager.showVipByKeyDialog(...) }`. Nếu `isVipByKeyActive()` → show summary "VIP active, expires <date>".

#### 2.3. Banner toggle theo VIP state (UX nhỏ)

Sau khi VIP active → banner hiện tại trong `SettingsFrm` cần ẩn. SDK `loadBanner` đã skip + ẩn shimmer khi `isVIPMember`, nhưng nếu user vừa activate VIP trong cùng session → cần `bannerDestroy(adView)` thủ công + `adView = null`.

```kotlin
private fun refreshUiForVipState() {
    val isVip = AdManager.isVipByKeyActive() ||
                AdManager.getMyListVipDevice().contains(AdManager.getCurrentDeviceGAID())
    if (isVip && adView != null) {
        AdManager.bannerDestroy(adView)
        adView = null
        binding?.layoutAdBanner?.bannerContainer?.isVisible = false
    }
}
```

---

### 🟡 Priority 3 — PaidEventListener (revenue analytics)

| Item | Detail |
|---|---|
| **Lý do** | Track revenue/impression cho LTV calculation, ROAS analytics. AppLovin + AdMob đều có precision tier (PRECISE / PUBLISHER_PROVIDED / ESTIMATED) |
| **Touchpoint** | `RApp.setupAds()` — set listener TRƯỚC khi gọi `AdManager.setConfig` (hoặc ngay sau, trước `initialize`) |
| **API** | `AdManager.paidEventListener = PaidEventListener { adType, valueMicros, currency, precision, source -> ... }` |
| **Forward đâu?** | Firebase Analytics `logEvent("ad_impression", ...)` → tự động Adjust/AppsFlyer SDK relay nếu có |
| **Dependency** | Hiện app **chưa có Firebase**. Nếu muốn dùng → setup Firebase project + `google-services.json` + `firebase-analytics` dependency. ~30 phút setup |
| **Effort khi đã có Firebase** | ~10 dòng |
| **Effort full setup** | ~1-2h (Firebase project + plugin + json) |

**Sketch:**
```kotlin
// Trong RApp.setupAds() trước setConfig
AdManager.paidEventListener = PaidEventListener { adType, valueMicros, currency, precision, source ->
    val params = Bundle().apply {
        putString(FirebaseAnalytics.Param.AD_FORMAT, adType)
        putString(FirebaseAnalytics.Param.AD_SOURCE, source ?: "unknown")
        putString(FirebaseAnalytics.Param.AD_PLATFORM, BuildConfig.IS_ENABLE_ADMOB.let { if (it) "admob" else "applovin" })
        putString(FirebaseAnalytics.Param.CURRENCY, currency)
        putDouble(FirebaseAnalytics.Param.VALUE, valueMicros / 1_000_000.0)
    }
    FirebaseAnalytics.getInstance(this).logEvent("ad_impression", params)
}
```

---

### 🟡 Priority 4 — ErrorReporter (Crashlytics hook)

| Item | Detail |
|---|---|
| **Lý do** | Forward exception nội bộ SDK sang Crashlytics — phát hiện sớm bug ad layer ở production |
| **Touchpoint** | `RApp.setupAds()` — set listener trước `setConfig` |
| **API** | `AdManager.errorReporter = ErrorReporter { throwable, context -> ... }` |
| **Dependency** | Cần Firebase Crashlytics (đi kèm Firebase project). Nếu đã setup Firebase Analytics (#3) → bonus, chỉ thêm 1 dependency |
| **Effort khi đã có Firebase** | ~5 dòng |

**Sketch:**
```kotlin
AdManager.errorReporter = ErrorReporter { throwable, ctx ->
    FirebaseCrashlytics.getInstance().apply {
        setCustomKey("ad_error_context", ctx)
        recordException(throwable)
    }
}
```

---

### 🟢 Priority 5 — Modern Kotlin API (suspend + Flow)

#### 5.1. Suspend wrappers — thay callback bằng coroutine

| Item | Detail |
|---|---|
| **Lý do** | Code clean hơn, không nested callback. `awaitSplashComplete` đặc biệt elegant cho splash flow |
| **Touchpoint** | `SplashActivity.onCreate` thay `initSplashScreen(callback)` bằng `lifecycleScope.launch { awaitSplashComplete(this); goToMain() }` |
| **API có thể dùng** | `awaitSplashComplete`, `showInterstitialAsync`, `loadAppOpenAdAsync`, `showAppOpenAdAsync`, `initializeAsync` |
| **Effort** | ~5 dòng / chỗ |
| **Risk** | Phải `@OptIn(ExperimentalAdApi::class)` — API SDK đánh dấu experimental |

**Sketch SplashActivity (alternative cho callback hiện tại):**
```kotlin
@OptIn(ExperimentalAdApi::class)
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.a_splash)
    lifecycleScope.launch {
        AdManager.awaitSplashComplete(this@SplashActivity)
        goToMain()
    }
}
```

> Cân nhắc: API hiện tại với callback đang OK, suspend chỉ là syntactic sugar. Không cần ưu tiên.

#### 5.2. AdEvent Flow — observe mọi sự kiện 1 chỗ

| Item | Detail |
|---|---|
| **Lý do** | Thay thế việc set 5 listener riêng, dùng 1 Flow + pattern match |
| **Touchpoint** | Bất kỳ Fragment/Activity muốn track ad event (vd: HomeFrm để log impression cho analytics) |
| **API** | `lifecycleScope.launch { repeatOnLifecycle(STARTED) { AdManager.adEvents.collect { event -> ... } } }` |
| **Effort** | ~15 dòng / consumer |
| **Use case ưu tiên** | Nếu muốn 1 chỗ duy nhất track tất cả ad events cho analytics (sau khi đã có Firebase) — tốt hơn 5 listener separate |

---

### 🟢 Priority 6 — `setTestDeviceIds` (debug-only)

| Item | Detail |
|---|---|
| **Lý do** | AdMob policy: device test phải đăng ký test device hash. Hiện `IS_ENABLE_ADMOB=false` → AppLovin active → SDK auto-register debug GAID. Nhưng nếu sau này bật AdMob, thiếu test device sẽ bị flag invalid traffic và risk suspend |
| **Touchpoint** | `RApp.setupAds()` cuối hàm, wrapped trong `if (BuildConfig.DEBUG)` |
| **API** | `AdManager.setTestDeviceIds("33BE2250B43518CCDA7DE426D04EE231", "...")` |
| **User input cần** | Hash device test — chạy app debug 1 lần, xem logcat AdMob sẽ in `Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("..."))` |
| **Effort** | ~3 dòng |

---

### 🟢 Priority 7 — `AdSafetyLimits` preset alt

Hiện dùng `CONTENT` (default) — phù hợp app notes. Nếu muốn nới lỏng cho test QA:

```kotlin
// RApp.setupAds() AdSdkConfig
safety = if (BuildConfig.DEBUG) AdSafetyLimits.TEST else AdSafetyLimits()
```

`TEST` preset: throttle 5s, daily 9999, hourly 999 — gần như tắt. **CHỈ trong DEBUG**, không ship release (vi phạm policy).

---

## 15. Đề xuất thứ tự triển khai

| Thứ tự | Feature | Effort | Khuyến nghị |
|---|---|---|---|
| 1 | UMP Consent + Privacy Settings button | ~40 dòng | **Làm ngay** trước khi release lên Play Store nếu app target EEA users (default = có) |
| 2 | VIP by Key dialog (#2.2) | ~15 dòng | Dễ nhất, ít deps, có sẵn `vipKeySecret`. Wire 1 button trong Settings |
| 3 | `setTestDeviceIds` | ~3 dòng | Trivial, nên có cho dev hygiene |
| 4 | Firebase setup + PaidEventListener + ErrorReporter | ~1-2h setup + ~15 dòng code | Quan trọng cho production analytics. Cần quyết định setup Firebase trước |
| 5 | VIP Whitelist GAID (#2.1) | ~10 dòng | Chỉ cần nếu có backend trả list paid users. Bỏ qua nếu không |
| 6 | Modern Kotlin API (suspend + Flow) | ~30 dòng | Optional refactor, không bức thiết |

---

## 16. Câu hỏi cho user

1. **App có target EEA users không?** Nếu có → priority 1 (UMP) bắt buộc.
2. **Có Firebase project sẵn không?** Nếu có → cho biết `google-services.json` ở đâu, sẽ wire ngay. Nếu chưa → cần setup project trên Firebase console trước.
3. **VIP có cần whitelist server-side không?** Cần backend endpoint trả list GAID, hoặc chỉ dùng VIP by Key đủ.
4. **Có muốn đổi `vipKeySecret`** từ `"CATNOTE_VIP_2026"` sang giá trị khác?

---

## 17. Round-3 implementation (2026-04-26) — UMP + VIP screen

User trả lời 4 câu hỏi: (1) global → cần UMP, (2) chưa có Firebase → skip Paid/Crashlytics, (3+4) dùng key `---`, yêu cầu VIP UI xịn sò.

### Đã apply

#### 17.1. Update `vipKeySecret`
- `RApp.setupAds()` → `vipKeySecret = "---"`

#### 17.2. UMP Consent (GDPR/EEA compliance)
- `SplashActivity.onCreate`: gọi `AdManager.requestConsentInfoUpdate(this, false) { canRequestAds -> ... }` TRƯỚC `initSplashScreen`. Nếu user từ chối → skip ad, navigate luôn.
- `SettingsFrm`: thêm Preference `privacy_settings` → click gọi `AdManager.showConsentFormIfAvailable(activity)`. UMP tự ẩn nếu user không ở khu vực required.
- Strings: `pref_privacy_settings`, `pref_privacy_settings_summary`.

#### 17.3. VIP Screen (custom UI, animation cao cấp)

**File mới**:
- `ui/vip/VipFrm.kt` — Fragment chính. Logic: render state (active/inactive), activate dialog, reset confirm, hero animations.
- Drawables:
  - `bg_vip_hero.xml` — gradient deep-purple → magenta → gold, rounded 28dp
  - `bg_vip_glow.xml` — radial glow ring
  - `bg_vip_pill_active.xml` / `bg_vip_pill_inactive.xml` — pill states
  - `bg_vip_dot_active.xml` — green dot
  - `ic_vip_crown.xml` — vector crown gold
  - `ic_vip_sparkle.xml` — vector sparkle
  - `ic_vip_check.xml` — vector check
- Animator XML (`res/animator/`):
  - `anim_vip_pulse.xml` — crown scale 1.0 ↔ 1.08 vô hạn (1400ms reverse)
  - `anim_vip_glow.xml` — glow ring alpha 0.55 ↔ 1.0 + slow rotation 20s
  - `anim_vip_sparkle_drift.xml` — sparkle translate-Y + alpha + 360° xoay 9s, 3 sparkle stagger
- Layout:
  - `f_vip.xml` — Toolbar + NestedScrollView + Hero card + benefits card + actions
  - `dlg_vip_activate.xml` — TextInputLayout cho key + ChipGroup 4 duration (7/30/90/365)
- Styles: `VipBenefitRow`, `VipBenefitCheck`, `VipBenefitText`
- Strings (~25 strings VIP)

**Wiring**:
- `nav_graph_main.xml`: thêm `fragment_vip` + action `action_settings_to_vip`
- `prefs.xml`: thêm `PreferenceCategory` "Premium" với 2 entries: `premium_vip` + `privacy_settings`
- `SettingsFrm.kt`: 
  - VIP click → navigate `actionSettingsToVip()`
  - Privacy click → `AdManager.showConsentFormIfAvailable`
  - `updateVipPreferenceSummary(pref)` → show "Active until %s" hoặc "Activate to remove all ads" (refresh ở `onViewCreated`)
- KHÔNG inject vào AppComponent (VipFrm không dùng `@Inject`)

### Animation detail

| Element | Animation |
|---|---|
| Crown icon | Pulse scale 1.0 → 1.08, fast-out-slow-in, infinite reverse |
| Glow ring | Alpha 0.55 → 1.0 + rotation 20s linear loop |
| 3 Sparkles | Drift Y + alpha + 360° rotation (9s), staggered 400ms entry delay |
| Hero card | Entrance: alpha 0→1 (400ms) + translateY 40→0 (480ms) Overshoot |
| On VIP activate | Hero scale 1.0 → 1.04 → 1.0 với OvershootInterpolator(2.5) — celebration burst |
| Fragment transition | `MaterialSharedAxis.X` forward ⇄ return |

### VIP API surface dùng

| API | Vị trí gọi |
|---|---|
| `activateVipByKey(ctx, key, days)` | VipFrm.activate dialog click |
| `isVipByKeyActive()` | VipFrm.renderState() + SettingsFrm.updateVipPreferenceSummary() |
| `getVipByKeyExpiry()` | VipFrm.renderState() (compute days remaining + format expiry) |
| `clearVipByKey()` | VipFrm.reset confirm |
| `requestConsentInfoUpdate(act, false) { }` | SplashActivity.onCreate (TRƯỚC initSplashScreen) |
| `showConsentFormIfAvailable(act)` | SettingsFrm privacy click |

### Build
`./gradlew :app:assembleDevDebug` → BUILD SUCCESSFUL in 6s.

### Compliance
- ✅ UMP form show trước ad đầu tiên
- ✅ Privacy Settings button trong app (UMP yêu cầu)
- ✅ VIP key store ở SharedPreferences với expiry — không lộ
- ✅ VIP UI không over-promise (footer text rõ "no payment processed in-app")

### Caveat
- `vipKeySecret = "---"` lưu trong `BuildConfigField` → ai decompile APK đều thấy. Đối với app notes (low-stakes) chấp nhận được. Nếu cần obfuscation cao hơn → store qua native lib hoặc server-side validation.
- VIP by Key dùng device-local SharedPreferences → user reset app data sẽ mất VIP.
- UMP lifecycle: nếu app process bị kill trước callback `requestConsentInfoUpdate` → form không show, nhưng `canRequestAds=false` ở callback gọi từ subsequent attempt vẫn an toàn.

---

## 18. Round-4 polish (2026-04-26)

| # | Feedback | Apply |
|---|---|---|
| 1 | Light/dark theme | ✅ Verified — hero gradient cố ý dark cho premium feel cả 2 mode; benefits card + stroke + footer dùng `?colorSurface` / `?colorOnSurface` / `?colorOutlineVariant`. Crown trên action bar dùng `ic_crown_24` monochrome có `tint="?attr/colorControlNormal"` (theme-aware) |
| 2 | Reuse Snackbar/Toast | ✅ Project không có shared util — pattern hiện hành là `Snackbar.make(...)` direct. VipFrm + UMP follow đúng |
| 3 | "Manage advertising consent" tap không có gì | ✅ Fix — `SettingsFrm.openPrivacySettings()` check `UserMessagingPlatform.getConsentInformation(ctx).privacyOptionsRequirementStatus` trước. Nếu `!= REQUIRED` (user ngoài EEA/UK/CH) → show snackbar `msg_consent_not_required` thay silent no-op |
| 4 | Touch-point đổi ngôn ngữ | ✅ `DropDownPreference("app_language")` ở Settings → Appearance — 14 entries (System default + 13 locales). Wire `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))` (auto-persist API 33+; AppCompat backport API < 33). Native names trong `donottranslate.xml` |
| 5 | Crown ở action bar | ✅ `R.id.itemVip` đầu menu_toolbar_home.xml, icon `ic_crown_24` monochrome, `showAsAction="always"`, click → `HomeFrmDirections.actionHomeToVip()` |

### Build
`./gradlew :app:assembleDevDebug` → BUILD SUCCESSFUL in 5s.

---

## 19. Round-5 correction (2026-08-18) — Reward ad touchpoint đã có thật

Doc gốc (mục 1, 9, 11) khẳng định "reward ad KHÔNG có touchpoint" — điều này **không còn đúng**. `ui/vip/VipFrm.kt` (màn Premium) đã triển khai đầy đủ flow rewarded ad:

- `setupWatchAdButton()`: `AdManager.loadRewarded(requireContext())` preload khi vào màn (nếu chưa VIP).
- Nút "Watch Ad (Get 3 Days Premium)" → `AdManager.showRewarded(requireActivity()) { rewardedSuccess -> ... }`.
- `AdManager.rewardedListener = object : RewardedAdListener { onUserEarnedReward(...) { grantVip3Days(ctx) } }` — listener được set `null`/swap sang no-op sau mỗi lần dùng để tránh giữ tham chiếu `VipFrm` qua singleton `AdManager` (xem `doc/memory_leak.md` mục FIX-M06 cho phần dialog liên quan cùng file).
- Nếu ad chưa sẵn sàng: dialog "Hold on a second — The ad is not ready yet" (không fallback sang interstitial, đúng chính sách AdMob "incentivized non-rewarded").

Kết luận: `APPLOVIN_REWARDED_ID` không còn là "config thừa" — đang được dùng thật. Các mục 1/9/11 ở trên đã được đánh dấu ⚠️ trỏ về mục này.

## 20. Round-6 (2026-08-19) — Chuyển provider active sang AdMob + nâng SDK 1.1.3 → 1.6.16

**Provider active đổi từ AppLovin MAX → AdMob** (`IS_ENABLE_ADMOB` `false → true` ở cả `debug` +
`release`, `app/build.gradle`). Debug tiếp tục dùng Google demo/test AdMob ID sẵn có; release đã có
banner/interstitial/appopen ID thật (`ca-app-pub-3612191981543807/...`) nhưng **`ADMOB_REWARDED_ID`
release còn rỗng `""`** — chờ user cung cấp, rewarded ad release sẽ fail-soft (log warning, callback
`false`, không crash) cho tới lúc đó. `APPLOVIN_*` buildConfigField giữ nguyên (không xoá) — theo rule
của `AD_PROMPT_AOS.MD`: giữ đủ 2 bộ ID dù chỉ 1 bên active, flip lại sau không cần đổi template.

**SDK nâng `com.github.royt93:AdmobApplovinWrapper` `1.1.3 → 1.6.16`** (tag mới nhất, verify qua
GitHub API tags — trước đó repo private nên `jitpack.io`/`api.github.com` đều chặn, user đã public
repo lại giữa chừng để verify được). Diff API qua Phụ lục A của `AD_PROMPT_AOS.MD` + đọc trực tiếp
source `AdSdkConfig.kt`/`AdManager.kt` ở tag 1.6.16:

- Field cấu hình cốt lõi (`isEnableAdmob`, `admobXxxId`, `applovinXxxId`, `vipKeySecret`, `safety`)
  không đổi tên — `RApp.setupAds()` không cần viết lại cấu trúc.
- ⚠️ **Breaking thật sự cho app này**: `AdSdkConfig.allowLegacyPlaintextVipKey` (mới, default
  `false` từ bản này) khoá nhánh `activateVipByKey` (plain-key). `VipFrm.kt` dùng đúng hàm này cho
  **cả 2** luồng VIP (`showActivateDialog()` nhập key 30 ngày, VÀ `grantVip3Days()` sau khi xem
  rewarded ad) — không bật cờ này thì cả màn VIP chết âm thầm (hàm trả `false`, không crash). Đã
  thêm `allowLegacyPlaintextVipKey = true` vào `AdSdkConfig` trong `RApp.setupAds()`.
- `appOpenExcludedActivities` set tường minh `listOf(SplashActivity::class.java)` — thay cho việc ăn
  may nhờ default deprecated `appOpenExcludedActivityNames=["SplashActivity"]` (field này sẽ bị gỡ
  dần theo doc).
- `AdSafetyStatus.totalImpressions/fullscreenClicks` đổi tên (→ `ctrWindowImpressions`/
  `ctrWindowClicks`) — grep xác nhận code không tham chiếu field nào trong 2 field cũ, an toàn.
- `grantRewardOnEarn` default đổi `false→true` — verify `VipFrm.kt` đã dedupe thưởng qua cờ
  `hasEarnedReward` dùng chung ở cả `rewardedListener.onUserEarnedReward` lẫn callback
  `showRewarded`, nên an toàn, không sửa gì thêm.
- ⚠️ **Bug thật bị miss ở lần audit đầu (2026-08-19), tự phát hiện khi user yêu cầu audit lại +
  tải source thật để diff 1.1.3 vs 1.6.16** (field này không nằm trong `AD_PROMPT_AOS.MD`, chỉ thấy
  khi đọc trực tiếp `AdManager.kt`): `activateVipByKey` bản 1.6.16 thêm cap
  `days > MAX_VIP_DAYS (365, private const, không phải public API)` → trả `false` ngay, không throw.
  `VipFrm.grantVip3Days()` tính `totalDays = remainingDays + REWARD_DAYS` rồi truyền thẳng — user
  gắn bó xem reward ad tích luỹ tới gần 1 năm VIP sẽ bị chặn cứng ở lần xem tiếp theo, kèm Toast
  **"Key không hợp lệ"** (sai — không phải key sai, là chạm trần). Đã verify
  `AppPreferences.accumulateVipByKeyUntil()` dùng `maxOf` (không cộng dồn thật) nên loại trừ được
  nghi ngờ double-count, chỉ riêng cap 365 là vấn đề. **Fix**: `VipFrm.kt` clamp
  `totalDays.coerceAtMost(VIP_KEY_MAX_DAYS_CAP)` (hằng số local `365`, comment ghi rõ đây là con số
  mirror từ SDK internal không public, cần re-verify nếu đổi version SDK sau này).
  `showActivateDialog()` (key 30 ngày cố định) không dính lỗi này vì `days` không phụ thuộc số dư.
- Đã cân nhắc đổi UI VIP sang `AdManager.showVipByKeyDialog()` (dialog có sẵn của SDK) nhưng **giữ
  nguyên `VipFrm.kt`** theo quyết định của user — dialog có sẵn không đa ngôn ngữ, không match theme
  app, còn `VipFrm.kt` đã có i18n vi/en + animation + confetti đầy đủ.
- `ConsentBottomSheetDialog` (dialog consent có sẵn của SDK) chỉ dùng ở nhánh **nongms** (grep
  `ConsentBridge.kt` bản `gms` = 0 match) — app build `gms`/AdMob nên không áp dụng; `SplashActivity`
  tiếp tục dùng đúng Google UMP form chuẩn qua `requestConsentInfoUpdate()`, không đổi gì.

**Manifest**: thêm `<attribution android:tag="ads" .../>` trước `<application>` — targetSdk 37 (≥31)
nên thiếu tag này sinh warning `attributionTag not declared` liên tục trong logcat (Step 6 của
`AD_PROMPT_AOS.MD`).

**Build verify**: `assembleDevDebug` và `assembleProductionRelease -x lintVitalAnalyzeProductionRelease`
đều `BUILD SUCCESSFUL` (kể cả R8 minify release) sau cả 2 thay đổi.

**Còn thiếu từ user**: `ADMOB_REWARDED_ID` cho release (`app/build.gradle` dòng buildType `release`).

## 21. Round-7 (2026-08-19) — Audit lại theo yêu cầu user: tìm + fix bug thật, verify runtime thật

User yêu cầu audit lại Round-6 sau khi thấy điểm 6/10. Diff sâu source thật (không chỉ tin doc) +
verify trên emulator thật (`Pixel_10_Pro_XL`) thay vì chỉ tin `./gradlew assemble*` xanh.

### Bug thật tìm được (không nằm trong `AD_PROMPT_AOS.MD`)

**`activateVipByKey` cap `days > 365` (private `MAX_VIP_DAYS`, không phải public API).**
`VipFrm.grantVip3Days()` tính `totalDays = remainingDays + REWARD_DAYS` rồi truyền thẳng — user gắn
bó xem reward ad tích luỹ gần 1 năm VIP sẽ bị chặn cứng, Toast sai ("Key không hợp lệ" thay vì "đã
chạm trần"). Verify `AppPreferences.accumulateVipByKeyUntil()` dùng `maxOf` (loại trừ nghi ngờ
double-count). **Fix**: tách `computeVipRewardTotalDays()` (top-level `internal`, pure function) +
`.coerceAtMost(VIP_KEY_MAX_DAYS_CAP=365)`, có unit test `VipFrmTest.kt` (5 case) cover luôn.

### Regression thật ở 2 test có sẵn (không phải false alarm)

Chạy `./gradlew testDevDebugUnitTest` lần đầu trong session (trước đó CHƯA từng chạy) →
`AdManagerUnitTest` vỡ 2 test:
- `testVipActivationAndBypass`: thiếu `allowLegacyPlaintextVipKey=true` trong config test → fail
  đúng như lo ngại ở Round-6.
- `testAdSafetyLimitsConfiguration`: dùng positional args cho `AdSafetyLimits(...)` — SDK 1.6.x chèn
  `ctrWindowMs`/`newSessionAfterBackgroundMs` vào giữa constructor, đẩy lệch vị trí
  `maxRapidResumesPerMinute` sang `ctrWindowMs` (Long) một cách ÂM THẦM (compile qua vì Int literal
  tự convert sang Long) — giá trị bị gán sai không lỗi compile, chỉ lỗi assertion.

Cả 2 đã fix (named args cho `AdSafetyLimits`; thêm `allowLegacyPlaintextVipKey=true` +
`AdManager.configureTestHooks(network = { true })` — SDK 1.6.x thêm gate mạng thật (V-03) cho
`activateVipByKey`, Robolectric không có network nên cần seam này). `./gradlew test` (2 flavor) xanh
37/37.

### Bug thật chỉ lộ ra khi CÀI THẬT lên máy (build xanh không bắt được)

`<attribution android:label="Ad Networks (...)">` ở Round-6 dùng string **literal** — biên dịch/merge
manifest OK, nhưng **cài đặt fail**: `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED: <attribution> does not
specify android:label` (Package Manager runtime đòi resource reference, không nhận literal). Fix:
thêm `R.string.ad_attribution_label` + trỏ `android:label="@string/ad_attribution_label"`.

### Verify runtime thật trên emulator (Pixel_10_Pro_XL, API mới nhất)

Cài `devDebug`, chạy full flow qua logcat + screenshot:
- `setConfig: provider=AdMob` — xác nhận flip provider đúng lúc runtime, không chỉ đúng lúc compile.
- Consent UMP resolve (`Publisher misconfiguration` warning — pre-existing do App ID
  `ca-app-pub-3612191981543807~...` chưa Publish European regulations message trên AdMob Console,
  KHÔNG phải lỗi do session này gây ra; UMP fail-open đúng thiết kế, `canRequestAds=true`).
- Splash → MainAct, `loadInterstitial`/`loadRewarded`/`loadAppOpenAd` đều request đúng Google demo ID
  cấu hình ở debug.
- Mở VIP screen → bấm "Watch Ad" → xem hết ad demo thật (nhãn "Test Ad") →
  `grantVip3Days: totalDays(clamped)=3` → `activateVipByKey ✅ legacy VIP 3 ngày` → UI hiện
  "ACTIVE · 2026-08-22". Xác nhận sống cả 2 fix quan trọng nhất (`allowLegacyPlaintextVipKey` +
  clamp 365 ngày) trên thiết bị thật, không chỉ suy luận từ source.
- Quay lại Settings → `loadBanner` gọi đúng, tự skip vì vừa lên VIP (đúng behavior "No banner ads").

### Phát hiện mới cần user biết, không phải bug (đã hỏi, user chấp nhận giữ nguyên)

SDK từ 1.1.4 build-in **auto-trial 1 ngày VIP miễn phí cho MỌI user cài app lần đầu** (release build,
qua Install Referrer, không có flag tắt). Bản 1.1.3 cũ KHÔNG có. Đã hỏi user — quyết định: chấp nhận,
không hạ version (đổi lại mất các fix bảo mật S1-S9 + hardening 1.1.4→1.6.16).

### detekt — fix 1 phần, phần lớn ngoài phạm vi

`app/build.gradle` thiếu `detektPlugins "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8"` →
`formatting:` section trong `detekt.yml` (từ 2021) bị coi "misspelled" → **toàn bộ** `./gradlew
detekt` fail validation từ trước khi chạy bất kỳ rule nào (không riêng ad SDK — mọi rule, mọi file,
có thể đã fail kiểu này rất lâu). Đã thêm dependency (user chọn fix), lỗi validation hết. Nhưng lộ ra
**481 issue tồn đọng, ước tính 2 ngày 20 giờ debt** — toàn bộ codebase, hoàn toàn không liên quan ad
SDK. Chỉ fix 4 dòng `MaxLineLength` trong `VipFrmTest.kt` (file mới của chính session này). 477 issue
còn lại là nợ kỹ thuật có sẵn, để nguyên — cần task dọn dẹp riêng, không phải phạm vi ad SDK.

### Build/test verify cuối

`./gradlew test` (2 flavor) xanh · `assembleDevDebug` + `assembleProductionRelease
-x lintVitalAnalyzeProductionRelease` xanh (kể cả R8) · cài + smoke-test thật trên emulator xanh.

## 22. Round-8 (2026-08-19) — Config thật từ `doc/ad/id.MD` + wire redeem code 3-ngày + test device

User cung cấp `doc/ad/id.MD` (package, policy URL, 2 VIP key, bộ AdMob ID đầy đủ từ account
`ca-app-pub-3004713799155145`) + 2 ảnh screenshot ad unit list (khớp id.MD, không thêm thông tin
mới) + yêu cầu add test device Pixel 7 Pro. 2 ảnh gửi kèm ban đầu (`[Image #2]`, `[Image #3]`) bị lỗi
truyền tải (cache file trùng byte-size, không phải ảnh thật) — không đọc được nội dung.

### Đổi account AdMob hoàn toàn

Manifest `APPLICATION_ID` + 4 ID release (banner/inter/appopen/rewarded) đổi từ account
`ca-app-pub-3612191981543807` (thiếu rewarded) sang `ca-app-pub-3004713799155145` (đủ cả 5 loại, kể
cả `native`). Verify runtime trên emulator: `setConfig: provider=AdMob` với App ID mới, không crash.

### `vipRedeemCodes` — phát hiện + tránh 1 collision nghiêm trọng

id.MD có thêm "key vip 3 days" riêng biệt với "key vip 30 days" — user chọn wire cả 2 vào
`AdSdkConfig.vipRedeemCodes`. Khi implement, phát hiện: **key 30-ngày đã là `vipKeySecret`**, và
`grantVip3Days()` (luồng watch-ad) TRƯỚC ĐÓ gọi `activateVipByKey(ctx, vipKeySecret, days=<tính
tay>)` — nếu thêm `vipRedeemCodes` chứa đúng giá trị `vipKeySecret` mà không đổi luồng reward, mọi
lần xem reward ad sẽ bị `tryActivateRedeemCode` (chạy TRƯỚC nhánh legacy trong `activateVipByKey`)
chặn bắt và cấp **30 ngày thay vì 3** (redeem-code lookup bỏ qua tham số `days` truyền vào, luôn dùng
giá trị map). Đây đúng "hack" mà `AD_PROMPT_AOS.MD` đã cảnh báo từ đầu: *"ĐỪNG hack bằng
activateVipByKey(ctx, vipKeySecret, 3) — dùng grantVipDays() cho reward ad"* — cảnh báo này bị bỏ sót
ở lần migrate gốc, giờ mới áp dụng đúng.

**Fix**: `VipFrm.grantVip3Days()` đổi sang `AdManager.grantVipDays(context, 3)` — API cấp-nội-bộ
riêng, không đụng `vipKeySecret`/`vipRedeemCodes`, tự cộng dồn qua `addVipDaysAtomic` (không cần tính
tay `remainingDays` nữa) → xoá luôn `computeVipRewardTotalDays()` + `VipFrmTest.kt` (dead code sau
khi bỏ path `activateVipByKey` khỏi luồng reward). `showActivateDialog()` (nhập tay) thêm
`keyToDays()` lookup để hiện đúng số ngày thật đã cấp (trước đó hard-code hiện "30 ngày" bất kể key
nào được nhập — sai nếu user gõ mã 3-ngày).

Verify runtime trên emulator: bấm "Watch Ad" → xem hết ad demo → log
`grantVipDays ✅ cấp 3 ngày VIP (until ...)` → UI "ACTIVE · 2026-08-22" (đúng 3 ngày từ lúc test).

### Test device Pixel 7 Pro

User yêu cầu dùng Pixel 7 Pro làm test device nhưng session trước đó đã chốt "chỉ dùng emulator" —
user sau đó tự explicit override cho riêng việc lấy GAID ("tự check đi, tôi đã cắm usb"). Cài
devDebug lên máy thật, đọc GAID qua log `###init GAID=...` (không có cách offline lấy GAID qua
`adb shell settings get secure advertising_id` — trả `null` trên máy này). GAID:
`be39dfe0-67f5-4da4-afb3-8407cd481df4`. Wire vào `RApp.setupAds()`:
`AdManager.setTestDeviceIds(PIXEL_7_PRO_GAID)` sau `initialize()`, gated `BuildConfig.DEBUG`.

### Các điểm khác (theo quyết định user)

- Native ad ID (`ADMOB_NATIVE_ID`) chỉ thêm `buildConfigField`, KHÔNG implement — `AdSdkConfig`
  hiện tại (1.6.16) không có field native ad nào, SDK chưa hỗ trợ; giữ chỗ cho sau.
- `URL_POLICY_NOTION` cập nhật khớp URL trong id.MD (cùng page-id Notion, khác slug — cập nhật theo
  nguồn chính thức mới nhất).

### Test-infra: 1 test mới flaky, root-cause + fix (đã tự sửa sai 1 lần, ghi lại cả 2 vòng)

Thêm `AdManagerUnitTest.testRedeemCode3DaysActivatesThreeDaysNotThirty` +
`testGrantVipDaysUsedByWatchAdRewardDoesNotCollideWithRedeemCodes` — bị fail khi chạy chung với
`testInvalidVipKeyFails` (test cũ, cố ý fail 1 lần).

**Vòng 1 (SAI — đã tự phát hiện khi audit lại theo yêu cầu user)**: kết luận nhầm là "Robolectric chỉ
share `object AdManager` giữa các `@Test` method **cùng file**, reset được giữa các file khác nhau"
— verify bằng `./gradlew testDevDebugUnitTest --tests "...AdManagerUnitTest"` (chỉ chạy 1 class qua
filter `--tests`) thấy xanh 5/5 lần nên tưởng đã xong, tách `testInvalidVipKeyFails` sang file riêng
`AdManagerInvalidKeyTest.kt` rồi dừng ở đó.

**Vòng 2 (audit lại, phát hiện vòng 1 sai)**: chạy **full module** thật (`./gradlew test
--rerun-tasks`, không lọc `--tests`) thay vì chỉ 1 class → fail lại 2/3 lần dù đã tách file riêng.
Nguyên nhân thật: `object AdManager` là JVM singleton sống xuyên suốt **worker JVM của Gradle**, mà
1 worker JVM chạy NHIỀU class test Robolectric liên tiếp không restart giữa các class — tách file
không đủ, `AdManagerInvalidKeyTest` vẫn đầu độc `AdManagerUnitTest` nếu cả 2 chạy cùng worker.
`--tests` filter (dùng ở vòng 1) vô tình giới hạn scope worker nên che mất tương tác thật.

**Fix đúng**: giữ nguyên `ShadowSystemClock.advanceBy` (bypass backoff cooldown) +
`confirmGdprConsent` (bypass consent-state dính từ test khác) trong `AdManagerUnitTest.setup()` —
đây mới là fix thật, KHÔNG phải việc tách file (tách file chỉ giúp code gọn, không giải quyết gốc).
Verify bằng `./gradlew test --rerun-tasks` (full module, ép chạy lại thật không cache) 5 lần liên
tiếp xanh. Comment trong cả 2 file test đã sửa lại cho khớp — bản cũ ghi sai nguyên nhân.

### ANR quan sát được trên emulator (không kết luận là bug app)

2-3 lần "Cat Note DEV isn't responding" xuất hiện trên emulator giữa lúc đang gọi dồn dập
`adb shell` (uiautomator dump + screenshot + logcat liên tục) — logcat cùng lúc show
`WifiService`/`ScanResultUtil` warning lặp lại + `network onAvailable throttled (flap)` đều đặn mỗi
~30s, đặc trưng của AVD có virtual network chập chờn. Khi relaunch sạch (force-stop, không chạy adb
command nào khác song song) và test lại đúng luồng watch-ad → không ANR, chạy mượt, log đúng kỳ vọng.
Kết luận tạm: môi trường emulator/tooling, không phải regression từ code — nhưng CHƯA loại trừ 100%
(không có ANR trace đầy đủ để xác nhận thread nào bị chặn); nếu QA thật gặp lại ANR tương tự trên máy
thật, cần điều tra riêng.

### Build/test verify cuối (Round-8)

`./gradlew test` (2 flavor, x3 lần liên tiếp) xanh ổn định · `assembleDevDebug` +
`assembleProductionRelease -x lintVitalAnalyzeProductionRelease` xanh · cài + smoke-test thật trên
emulator: App ID mới, redeem code, `grantVipDays` reward flow đều verify sống qua log + screenshot.

**Còn thiếu**: không còn — `ADMOB_REWARDED_ID` release đã có từ id.MD.

## 23. Round-9 (2026-08-19) — Audit line-by-line theo yêu cầu user, tìm 1 bug thật bị bỏ sót

User nghi ngờ code ẩu, yêu cầu audit lại toàn bộ diff Round-6→8 từng dòng. Kết quả:

**Bug thật tìm được**: `RApp.onCreate()` nhánh `isTesting` (bypass ads cho UI test — phát hiện qua
`Class.forName("androidx.test.espresso.Espresso")`) gọi
`AdManager.activateVipByKey(this, key30Days, days=365)` — ĐÚNG collision pattern vừa fix ở
`grantVip3Days()` (Round-8) nhưng bỏ sót chỗ này: `key30Days` giờ đã nằm trong `vipRedeemCodes` map
với giá trị `30`, nên `tryActivateRedeemCode` (chạy trước, đọc map trước khi xét tham số `days`) sẽ
chặn bắt và cấp **30 ngày thay vì 365** — không crash, không lỗi rõ, chỉ âm thầm cấp ngắn hơn dự
định. Impact thực tế thấp (UI test suite không chạy 30+ ngày) nhưng đúng loại lỗi im lặng đã cảnh báo
trước đó, bỏ sót vì chỉ audit `VipFrm.kt` mà không grep toàn bộ `activateVipByKey` call site trong
`RApp.kt`. Fix: đổi sang `AdManager.grantVipDays(this, 365)`, thêm const `VIP_TEST_BYPASS_DAYS`. Grep
lại toàn bộ `activateVipByKey`/`grantVipDays` trong `app/src/main/kotlin` xác nhận không còn chỗ nào
khác dính pattern này.

**Comment sai bị tự phát hiện + sửa** (không phải bug runtime, nhưng gây hiểu nhầm cho người đọc
sau): comment trong `AdManagerUnitTest.kt`/`AdManagerInvalidKeyTest.kt` từ Round-8 khẳng định
"Robolectric chỉ share `object AdManager` giữa các `@Test` method cùng file, reset được giữa file
khác" — SAI, xem mục 22 "Round-9" ở trên (đã hợp nhất lại vào phần Round-8) — verify lại bằng
`./gradlew test --rerun-tasks` (full module) mới lộ ra tách file không đủ, cần giữ nguyên
`ShadowSystemClock.advanceBy` + `confirmGdprConsent`. Đã sửa comment khớp sự thật đã verify.

**Đã audit, xác nhận đúng** (không phải bug, review kỹ mà không đổi):
- `keyToDays()` (VipFrm.kt) và `vipRedeemCodes` (RApp.kt) là 2 map độc lập cùng giá trị — trùng lặp
  logic (DRY violation nhẹ) nhưng không phải bug, chỉ là rủi ro drift nếu sau này đổi 1 bên quên đổi
  bên kia. Chấp nhận được ở quy mô hiện tại (2 entry, ít thay đổi).
- `testGrantVipDaysUsedByWatchAdRewardDoesNotCollideWithRedeemCodes` và
  `testRedeemCode3DaysActivatesThreeDaysNotThirty`: verify lại toán học ngày-tính-từ-timestamp (chia
  nguyên `Long/Long`, hướng làm tròn) đúng theo mọi trường hợp biên đã xét.
- `app/build.gradle`, manifest, `strings.xml`, `Context.kt`: diff lại từng dòng, không phát hiện gì
  thêm ngoài các mục đã ghi ở Round-6/7/8.

Build/test cuối (sau fix Round-9): `./gradlew test --rerun-tasks` xanh · `assembleDevDebug` +
`assembleProductionRelease -x lintVitalAnalyzeProductionRelease` xanh.

## 24. Round-10 (2026-08-19) — Test thủ công đầy đủ mọi flow + audit độc lập, 2 issue tìm được

User yêu cầu test thủ công thật (không chỉ unit test) toàn bộ: trial mode, consent, kích hoạt VIP
bằng key, đủ loại ad (banner/interstitial/appopen/rewarded/native), online lẫn offline — build
release thật + giả lập Install Referrer để test trial mode đúng đường sideload. Song song, user nghi
ngờ config AdMob provider sai, yêu cầu check lại `doc/ad/id.MD` từng dòng, và thử tham khảo audit độc
lập từ `codex`/`gemini`/`agy` (cả 3 đều không dùng được — codex hết hạn mức tài khoản ChatGPT, gemini
lỗi tier tài khoản Google, agy đòi tương tác không chạy non-interactive được — không phải lỗi code,
đã báo user trung thực thay vì giả kết quả). User yêu cầu tự audit lại không cần chờ.

**Verify sống trên thiết bị (không chỉ đọc code)**: cả 4 loại ad hiện được (banner/interstitial/
appopen/rewarded qua log + màn hình), cả 2 redeem code (30 ngày, 3 ngày) activate đúng qua UI nhập
tay, offline→online recovery đúng, trial-mode mặc định fail-closed đúng khi sideload (Install
Referrer hiện đại dùng bound-service, không giả lập được qua `adb broadcast` kiểu legacy — xác nhận
qua manifest chỉ có `BIND_GET_INSTALL_REFERRER_SERVICE`, không có receiver cũ). Consent flow verify
được 1 phần — AdMob Console báo "Publisher misconfiguration" (thiếu khai báo European regulations
message cho App ID), đây là gap phía AdMob Console, không phải bug code.

**Issue 1 — nghi ngờ config AdMob provider sai (yêu cầu trực tiếp của user)**: grep lại toàn bộ giá
trị `doc/ad/id.MD` (package, App ID, banner/inter/appopen/native/rewarded) so với `app/build.gradle`
+ `AndroidManifest.xml` — khớp 100%, không lệch giá trị nào. Nguyên nhân khiến user nghi ngờ: comment
cũ ở `app/build.gradle:37` ghi sai "AppLovin MAX (active provider — IS_ENABLE_ADMOB=false)" trong khi
giá trị thật ở cả debug/release là `IS_ENABLE_ADMOB=true` (AdMob mới là provider active) — chỉ là
comment lạc hậu, không phải bug chức năng. Đã sửa lại comment cho khớp giá trị thật. Đồng thời sửa
comment `AndroidManifest.xml` còn ghi "Sample AdMob App ID" dù giờ là App ID thật.

**Issue 2 — tự audit tìm được, đưa 2 lựa chọn qua AskUserQuestion, user chọn fix ngay cả 2**:
1. `SplashActivity.startAdFlow()` dùng timeout cứng `5000ms` để ép chuyển `MainAct` nếu consent flow
   không phản hồi — quá ngắn so với chính watchdog nội bộ của SDK (`consentWatchdogMs=15_000L` pha
   fetch mạng, `consentFormWatchdogMs=180_000L` pha đọc form, cộng lại worst-case ~195s cho người
   dùng EEA thật đang đọc form) → có thể kéo người dùng ra khỏi form đang đọc dở. User chọn "Fix ngay"
   (không chọn phương án giữ nguyên vì "out of scope"). Fix: đổi thành hằng số
   `SAFETY_TIMEOUT_MS = 200_000L` kèm comment giải thích rõ đây là circuit-breaker cuối cùng, không
   phải timeout thường.
2. `AdSdkConfig.allowLegacyPlaintextVipKey` đang bật `true` trong khi cả 2 code VIP thật (30 ngày, 3
   ngày) đã nằm trong `vipRedeemCodes` — map này được `activateVipByKey()` xét TRƯỚC path plaintext
   legacy, nên cờ `true` này thực chất là dead code không bao giờ được path nào chạy tới, chỉ mở thêm
   1 bề mặt tấn công thừa (SDK doc ghi rõ path legacy này sẽ bị gỡ ở major version sau). User ban đầu
   chọn phương án lớn hơn ("migrate sang token ECDSA") nhưng trước khi viết code migration, phát hiện
   ra token ECDSA (single-use per-user) không hợp với thiết kế thật của app (2 code cố định dùng lại
   nhiều lần — đúng pattern "thẻ cào" SDK doc khuyến nghị, không phải pattern token) — quay lại hỏi
   user qua AskUserQuestion lần 2, user chọn phương án nhỏ đúng hơn: tắt
   `allowLegacyPlaintextVipKey = false`. Đã sửa ở `RApp.kt` + `AdManagerUnitTest.kt` +
   `AdManagerInvalidKeyTest.kt` (2 file test dùng để mirror cấu hình thật).

**Bài học quy trình**: đừng lao vào scope lớn (ECDSA migration) chỉ vì đó là option user chọn đầu
tiên — nếu trong lúc triển khai phát hiện scope đó không khớp thiết kế thật, dừng lại hỏi lại ngay
(qua AskUserQuestion) thay vì âm thầm đổi hướng hoặc âm thầm làm tiếp theo hướng cũ.

Build/test cuối (sau cả 2 fix Round-10): `./gradlew test --rerun-tasks` xanh (76 task) ·
`assembleDevDebug` + `assembleProductionRelease -x lintVitalAnalyzeProductionRelease` xanh.

### Verify sống trên thiết bị thật (Pixel 7 Pro, `2B051FDH3006MU`) sau fix `allowLegacyPlaintextVipKey=false`

Cài `devDebug` qua `adb install -r`, mở app từ đầu (Splash → App Open Ad test thật hiện đúng nhãn
"Test Ad" + tên app "Ghi chú về mèo" → MainAct), vào màn Premium (`fragment_vip`), nhập tay cả 2 code
qua dialog `showActivateDialog()`:

- Code 30 ngày (`9fA0q7eN!27cLx04@21993Y2u0I7#Q0`): log
  `activateVipByKey ✅ redeem code +30 ngày (cộng dồn, until Sat Sep 19 2026)`, UI chuyển "ACTIVE",
  "Active · 2026-09-19", snackbar "Premium activated · 30 days".
- Reset (`btnReset` → confirm dialog "Reset Premium?"): log `clearVipByKey ✅`, UI về "NOT ACTIVE".
- Code 3 ngày (`eQ7@93L0f!2Y2707xN04021993u0I#2aK`): log
  `activateVipByKey ✅ redeem code +3 ngày (cộng dồn, until Sun Aug 23 2026)`, UI "Active ·
  2026-08-23", snackbar "Premium activated · 3 days" — xác nhận đúng 3 ngày, không lẫn với code 30
  ngày dù cùng dùng chung 1 hàm `activateVipByKey()`.

Reset lại lần nữa để trả app về trạng thái sạch sau khi test xong.

**Trở ngại khi tự động hoá trên thiết bị thật này (không phải bug app, ghi lại để lần sau đỡ mất
thời gian)**: `uiautomator dump` liên tục lỗi "could not get idle state" trên màn `VipFrm` vì
`startDecorativeAnimations()` chạy animation lặp vô hạn (pulse/glow/sparkle) — dump không bao giờ
thấy UI "đứng yên". Fix: tắt tạm 3 setting hệ thống
`window_animation_scale`/`transition_animation_scale`/`animator_duration_scale` về `0` trước khi
dump, dump được ngay, xong việc bật lại về `1` (giá trị mặc định thiết bị). Trong lúc dump còn lỗi,
suy đoán toạ độ tap từ ảnh chụp màn hình (quy đổi 923×2000 hiển thị → 1440×3120 gốc, hệ số ×1.56) bị
sai nhiều lần liên tiếp — nhầm lẫn lặp lại giữa toạ độ "hiển thị" và toạ độ "gốc" ngay cả sau khi đã
biết công thức, dẫn đến tap trượt khỏi nút nhiều lần không rõ nguyên nhân (không log, không crash, chỉ
im lặng không phản hồi) trước khi chuyển sang lấy `bounds=` trực tiếp từ dump — đáng tin cậy hơn hẳn
so với áng chừng bằng mắt.

## 25. Round-11 (2026-08-20) — Audit theo `AD_PROMPT_AOS.MD` (đọc full 2067 dòng), 6 finding + fix 3

User yêu cầu check lại `doc/AD_PROMPT_AOS.MD` và audit code tích hợp ad tìm rủi ro. Đọc toàn bộ file
gốc (không chỉ đoạn đã trích trước đây), đối chiếu từng field/API còn thiếu trong `RApp.setupAds()`
với 2 agent grep song song quét toàn bộ call site (`loadBanner`/`showInterstitial`/`showRewarded`/
`AdSdkConfig` fields/manifest/build.gradle) + tự verify trực tiếp trên thiết bị thật (file
`shared_prefs/` thật, `allowBackup` resource value). Tìm được 6 finding, user chọn qua AskUserQuestion
fix 5/6 (bỏ finding 6 — chỉ mang tính thông tin, không cần hành động):

**Finding 1 (MEDIUM, đã fix) — `vipKeySecret` trùng giá trị với mã VIP 30 ngày công khai.**
`RApp.kt:104` cũ: `vipKeySecret = vip30DaysKey` — cùng 1 biến vừa dùng làm secret chống-tamper
SharedPreferences (mục đích duy nhất còn lại của field này từ khi tắt `allowLegacyPlaintextVipKey`),
vừa là chuỗi "9fA0q7eN!27cLx04@21993Y2u0I7#Q0" phát công khai cho user nhập kích hoạt VIP (có trong
`doc/ad/id.MD`, trong `vipRedeemCodes` map). Bất kỳ ai biết mã VIP công khai cũng biết luôn "secret"
nội bộ — lớp chống-tamper mất hết ý nghĩa phòng thủ thêm ngoài rào cản root vốn đã tồn tại. Fix: sinh
secret ngẫu nhiên 28 ký tự riêng (mixed case/digit/special, không trùng bất kỳ giá trị
`vipRedeemCodes` nào), Base64-encode, thêm `buildConfigField "VIP_SECRET_KEY_ENCODED"`
(`app/build.gradle`), `RApp.kt` decode field mới thay vì tái dùng `vip30DaysKey`.

**Finding 2 (MEDIUM, đã fix) — lỗ hổng redeem-code qua Android Backup chưa được vá.**
Verify trực tiếp: `bool.xml:3` `allow_backup=true`, và **không có** `backup_rules.xml`/
`data_extraction_rules.xml` nào trong repo trước fix. `adb shell run-as ... ls shared_prefs/` trên
máy thật liệt kê đúng 4 file SDK: `loitp_admob.xml`, `admob.xml`, `app_preferences.xml`,
`paid_storage_sp.xml` — không file nào bị loại khỏi backup. Doc cảnh báo rõ: nếu `allowBackup=true`
mà không loại trừ các file này, Auto Backup/Google Drive/chuyển máy có thể phục hồi ledger "mã đã
dùng" về trạng thái cũ → redeem lại vô hạn cùng 1 mã, **không cần root** (khác accepted-risk gốc
trong doc chỉ nói tới rooted device). Fix: tạo `res/xml/data_extraction_rules.xml` (API 31+, cả
`cloud-backup` lẫn `device-transfer`) + `res/xml/backup_rules.xml` (legacy pre-31), loại trừ đúng 4
file trên qua `domain="sharedpref"`; wire `android:fullBackupContent`/`android:dataExtractionRules`
vào `<application>` trong `AndroidManifest.xml`.

**Finding 3 (LOW-MEDIUM, đã fix) — `paidEventListener` chưa wire → không đo được doanh thu ads.**
Doc đánh dấu 💰 là quan trọng, yêu cầu set **trong `Application.onCreate()`** (không phải Activity —
SDK gắn "chủ sở hữu" listener theo Activity foreground lúc set và tự xoá khi Activity đó destroy,
set nhầm chỗ sẽ mất tracking âm thầm). Grep xác nhận field này không xuất hiện ở bất kỳ đâu trong
repo trước fix. Fix: thêm `AdManager.paidEventListener = PaidEventListener { adType, valueMicros,
currency, precision, adSource -> Log.d(...) }` ngay sau `setConfig()` trong `RApp.setupAds()` — đúng
vị trí Application.onCreate, log-only vì app chưa có Crashlytics/MMP để forward thật.

**Finding 4 (LOW, đã fix) — `errorReporter` chưa wire.** Cùng lý do — thêm `AdManager.errorReporter =
ErrorReporter { throwable, context -> Log.e(...) }`, log-only, sẵn sàng cắm Crashlytics sau này.

**Finding 5 (LOW, cosmetic, đã fix) — `SettingsFrm.kt` gọi tay `bannerDestroy()` dù
`autoManageLifecycle` mặc định `true`.** Doc nói rõ: `autoManageLifecycle=true` → SDK tự hook cả 3
hàm resume/pause/destroy qua `ActivityLifecycleCallbacks`, gọi tay thêm không crash (idempotent-guard)
nhưng "không phải lý do để làm cả hai". Fix: xoá dòng `AdManager.bannerDestroy(adView)` ở
`onDestroyView()`, chỉ còn `adView = null`.

**Finding 6 (info, KHÔNG fix — user chọn bỏ qua) — Interstitial hiện ở mọi lần tap nút "+" tạo note**
(`HomeFrm.kt:129`). Hành vi có sẵn từ trước lần audit này, được `AdSafetyLimits` cap tần suất đúng
(release: `minTimeBetweenFullscreenAds=60s`, `maxFullscreenAdsPerSession=6`,
`maxFullscreenAdsPerHour=3`, `maxFullscreenAdsPerDay=10`) nên không vi phạm 6 policy rule của doc —
chỉ là quan sát UX/aggressiveness, không phải bug.

**Đã audit, xác nhận đúng (không cần fix)**: `appOpenExcludedActivities` đúng
`listOf(SplashActivity::class.java)`; manifest có đủ `APPLICATION_ID` meta-data + `<attribution>` tag;
`IS_ENABLE_ADMOB=true` cả debug/release; VIP fallback rewarded KHÔNG cấp reward (đã verify lại lần
nữa, `VipFrm.kt:144-156`); zero leftover SDK cũ (`AdMobManager`/`MaxManager`/`AdsHelper`); zero dùng
MREC/Leaderboard/RewardedInterstitial nên không cần field tương ứng; `applovinUseNativeCmp` không cần
thiết vì app chỉ build variant `gms` qua JitPack AAR (không có flavor `nongms`); `tagForChildDirected`/
`tagForUnderAgeOfConsent` để mặc định `null` đúng vì app không hướng trẻ em; dedup reward qua 2 kênh
(`rewardedListener` + callback `showRewarded`) bằng cờ `hasEarnedReward` hoạt động đúng tương đương
`rewardTransactionId` chính thức của SDK (không cần đổi, chỉ khác cơ chế).

**Build/test verify sau cả 5 fix**: `./gradlew test --rerun-tasks` xanh (76 task) · `assembleDevDebug`
+ `assembleProductionRelease -x lintVitalAnalyzeProductionRelease` xanh. Verify sống trên Pixel 7 Pro
(uninstall + install sạch để reset ledger redeem-code, tránh nhầm "mã đã dùng" từ session trước với
lỗi do đổi `vipKeySecret`): code 30 ngày activate đúng
(`activateVipByKey ✅ redeem code +30 ngày ... until Sat Sep 19 2026`, UI "ĐANG KÍCH HOẠT · 2026-09-19",
snackbar "Đã kích hoạt Premium · 30 ngày") — xác nhận đổi `vipKeySecret` không ảnh hưởng path
redeem-code (đúng như phân tích code: field này chỉ gắn với chống-tamper/legacy path, không tham gia
`vipRedeemCodes` lookup). Reset lại (`clearVipByKey ✅`) trả app về trạng thái sạch sau khi audit.

## 26. Round-12 (2026-08-20) — Self-audit lại Round-11, tìm 1 bug thật bị bỏ sót + 1 lỗi tài liệu

User không tin Round-11 đã sạch, yêu cầu audit kỹ lại chính những gì vừa fix. Kết quả: tìm được 1 bug
thật (đã fix) trong chính fix Finding 5 của Round-11, và 1 lỗi mô tả không chính xác trong tài liệu
Finding 2 (không phải bug runtime, chỉ sai claim).

**Bug thật — Finding 5 Round-11 (xoá `bannerDestroy()` thủ công) là SAI, đã revert.**
Lý do ban đầu (Round-11): doc SDK nói `autoManageLifecycle=true` (default) khiến SDK "tự hook lifecycle
qua `ActivityLifecycleCallbacks`", nên gọi tay `bannerDestroy()` là dư thừa. Nhưng
`ActivityLifecycleCallbacks` chỉ bắt sự kiện cấp **Activity** (`onActivityPaused`/`onActivityDestroyed`
của `MainAct`) — app này là **single-Activity + Fragment nav** (CLAUDE.md), `SettingsFrm` (host banner
duy nhất) là 1 Fragment sống bên trong `MainAct`. Khi user back khỏi màn Cài đặt, `MainAct` **không hề
bị destroy** — chỉ Fragment view bị huỷ — nên callback cấp-Activity của SDK không có cơ hội chạy, và
banner AdView bị mồ côi (leak) cho tới khi cả `MainAct` đóng.

Verify thực nghiệm trên Pixel 7 Pro (không chỉ suy luận): cài bản đã xoá `bannerDestroy()`, vào Cài đặt
(banner load + log `✅ Loaded`/`👁️ Impression`/`AdsRevenue` — xác nhận đồng thời `paidEventListener`
Finding 3 hoạt động đúng), bấm mũi tên back trong toolbar (popBackStack, không phải system back — system
back có lúc thoát hẳn app do quirk stack, tránh dùng để test) → **zero log** liên quan `banner`/
`onActivity*` xuất hiện, xác nhận không có cleanup nào chạy. Revert lại `AdManager.bannerDestroy(adView)`
trong `onDestroyView()` của `SettingsFrm.kt`, build lại, test lại đúng luồng → log
`bannerDestroy 🗑️ destroying AdView` xuất hiện đúng lúc back. Bài học: guidance của SDK doc viết cho mô
hình Activity-per-screen (đúng cho demo app của lib) không tự động đúng cho app single-Activity+Fragment
— phải verify theo kiến trúc thật của project, không áp dụng máy móc.

**Lỗi tài liệu — Finding 2 Round-11 (backup exclusion) mô tả không chính xác nguyên nhân, đã sửa lại
comment (không đổi hành vi).** Viết lại rằng cả 4 file (`loitp_admob.xml`, `admob.xml`,
`app_preferences.xml`, `paid_storage_sp.xml`) đều là "ledger mã đã dùng" — SAI. Dump nội dung thật qua
`adb shell run-as ... cat shared_prefs/*.xml`: chỉ **`loitp_admob.xml`** chứa dữ liệu VIP/redeem/
safety-counter thật (`keyUsedRedeemCodesList`, `keyVipByKeyUntil`, các `*_sig` HMAC...). 3 file còn lại
là cache/định danh nội bộ của chính Google Mobile Ads SDK, không phải wrapper: `admob.xml` (
`app_settings_json`, `inspector_info`, ad limits — cache cấu hình AdMob), `app_preferences.xml` (chỉ có
`last_review_time` — cooldown Play In-App Review, không liên quan ads), `paid_storage_sp.xml` (
`paidv1_id`/`paidv2_id` — định danh nội bộ Google cho paid-app attribution). Giữ nguyên loại trừ cả 4
file (không hại gì, hygiene hợp lý cho cache/định danh Google) nhưng đã sửa comment trong
`data_extraction_rules.xml`/`backup_rules.xml` cho đúng: chỉ `loitp_admob.xml` mới là file thực sự vá
lỗ hổng doc cảnh báo, 3 file kia loại trừ vì lý do khác (tránh trùng định danh khi restore/device-transfer).

**Đã re-verify không phải lỗi (chủ động test lại dù nghi ngờ) — đổi `vipKeySecret` có làm mất VIP của
user đang active không?** Đây là lo ngại lớn nhất tự đặt ra khi bắt đầu audit lại: `vipKeySecret` ký
HMAC toàn bộ `loitp_admob.xml` (xác nhận qua dump, mọi key đều có `_sig` tương ứng) — đổi secret giữa 2
bản build tưởng như sẽ làm mọi chữ ký cũ sai lệch khi app update. Test thực nghiệm nghiêm ngặt: build
bản secret CŨ (`git checkout` tạm 2 file về commit trước), cài sạch, activate VIP 30 ngày, dump prefs
lấy baseline chữ ký. Khôi phục code secret MỚI, build lại, `adb install -r` (giữ data, mô phỏng đúng
kịch bản app update thật trên Play Store) → log `vipByKey=true → isVIPMember=true`, VIP **sống sót**
nguyên vẹn, dump lại prefs thấy chữ ký `_sig` giữ nguyên y hệt (không bị ghi đè) và không có log
tamper/signature-mismatch nào. Kết luận: đổi `vipKeySecret` **an toàn** với user đã có VIP active từ
trước — SDK không invalidate state cũ khi secret đổi (có thể do thiết kế fail-open nhất quán với
"root-tamper" accepted-risk đã ghi ở đầu file, hoặc do việc verify chữ ký không chạy trên mọi lần đọc).
Lo ngại ban đầu là overthinking — nhưng verify thực nghiệm là đúng quy trình, không nên chỉ tin suy luận.

**Build/test verify cuối (sau revert Finding 5 + sửa comment Finding 2)**:
`./gradlew test --rerun-tasks` xanh (76 task) · `assembleDevDebug` + `assembleProductionRelease -x
lintVitalAnalyzeProductionRelease` xanh · verify sống: banner load + destroy đúng lúc back từ Settings,
VIP redeem-code không bị ảnh hưởng bởi thay đổi secret. Device để lại trạng thái sạch (`keyVipByKeyUntil
= 0`).

## 27. Round-13 (2026-08-20) — Audit lại Round-12, tập trung verify phần chưa test được (backup thật)

User hỏi "kiểm tra sạch chưa" — không tìm thêm bug code mới, nhưng đóng nốt 1 lỗ hổng verify còn để ngỏ
từ Round-11/12: `data_extraction_rules.xml`/`backup_rules.xml` mới chỉ verify đúng cú pháp XML + build
xanh, **chưa từng chạy backup thật** để xác nhận exclude rule có tác dụng runtime.

**Thử nghiệm**: `adb shell bmgr backupnow com.mckimquyen.notes` trên bản `devDebug` đang cài →
**"Backup is not allowed"** — hoá ra debug build (`android:debuggable=true`, mặc định của mọi build
type `debug`) bị `BackupManagerService` chặn backup vô điều kiện, không liên quan gì tới
`allowBackup`/exclude rules của app. Thử `settings put secure backup_enable_debug 1` không có tác
dụng (không phải đúng cờ). Chuyển sang cài bản `productionRelease` đã build sẵn (ký bằng keystore
thật, `debuggable=false`) → `bmgr backupnow` chạy **"Success"** — xác nhận `dataExtractionRules`/
`fullBackupContent` không có lỗi cú pháp/schema nào chặn backup chạy (nếu path/domain sai định dạng,
kỳ vọng sẽ thấy lỗi hoặc backup fail, không thấy).

**Giới hạn chưa verify được (nêu rõ, không giả vờ đã xong)**: không xác nhận được **từng file cụ thể**
có thực sự bị loại khỏi blob backup hay không, vì:
- `adb backup` (cách trực tiếp nhất để lấy archive `.ab` rồi giải nén xem) đã deprecated, cần xác nhận
  tương tác trên màn hình thiết bị (dialog hệ thống) — thử tự động hoá bằng `input keyevent 4` (back)
  vô tình **huỷ** dialog thay vì confirm, backup không chạy được qua đường này.
- Dữ liệu blob thật của `LocalTransport` nằm ở thư mục hệ thống (`/data/misc/backup/...`), không đọc
  được qua `adb shell` thường (không root) và cũng không đọc được qua `run-as` vì bản release
  không-debuggable.

Kết luận: exclude rule **chạy được, không lỗi**, nhưng "loại đúng file" chỉ được suy ra từ cú pháp
đúng chuẩn Android docs (`domain="sharedpref" path="<name>.xml"`, đối chiếu ví dụ chính thức) + nội
dung file đã verify đúng ở Round-12, không phải bằng cách mở blob ra đếm tay. Nếu cần bằng chứng ở
mức "đã mở blob ra xem", phải test trên thiết bị có root hoặc dùng Google Drive backup thật (cần tài
khoản Google + chờ chu kỳ backup tự nhiên, ngoài phạm vi 1 phiên audit).

Đã cài lại `devDebug` trả máy về trạng thái phát triển bình thường sau khi test xong.

**Rà lại toàn bộ diff từ trước Round-11 tới giờ (không tìm thêm bug mới)**: `git diff 1178deb` cho
`RApp.kt`/`SettingsFrm.kt`/`AndroidManifest.xml`/`app/build.gradle`/2 file XML mới — khớp đúng ý định,
không còn đoạn nào tương tự lỗi "Activity-vs-Fragment" đã tìm ở Round-12 (đã grep lại toàn bộ call
site `AdManager.*` trong `app/src/main/kotlin` — chỉ banner có vòng đời View cần cleanup thủ công;
interstitial/rewarded là show một lần, không có View tồn tại lâu nên không dính lớp bug này).

**Full regression sống trên Pixel 7 Pro sau khi user yêu cầu "audit lại 1 round nữa, kiểm tra sạch
chưa"** — trước tiên audit riêng code test-device-id (`PIXEL_7_PRO_GAID` trong `RApp.kt`): khớp đúng
GAID thật của máy (verify qua log `getGAID result id=...` nhiều lần trong session), gọi đúng sau
`initialize()`, gate đúng `BuildConfig.DEBUG`, callback `initialize()` chỉ fire 1 lần nên không có rủi
ro double-register — sạch, không sửa gì. Sau đó chạy full regression từ đầu trên bản `devDebug` cài
sạch (fresh install qua Splash, không launch tắt qua MainAct để tránh bỏ qua bước consent):

| Test | Kết quả | Bằng chứng |
|---|---|---|
| Cold start / consent / Splash | PASS | Log init sạch, 0 exception |
| App Open (first-launch skip + resume sau background) | PASS | `skipAppOpenOnFirstEverLaunch` đúng lần đầu; `✅ Loaded gen=2` khi resume |
| Interstitial (FAB tạo note) | PASS | `showInterstitial called` → `AdsRevenue: interstitial ...` |
| Banner (Settings) load + `bannerDestroy` khi back | PASS | Xác nhận lại fix Round-12 vẫn ổn định qua lần cài mới |
| Rewarded ad → VIP +3 ngày | PASS | `showRewarded`/`SHOWED` → UI "2026-08-23", `AdsRevenue: rewarded ...` |
| VIP redeem code 30 ngày | PASS | `activateVipByKey ✅ redeem code +30 ngày ... 2026-09-19` |
| VIP redeem code 3 ngày | PASS | `activateVipByKey ✅ redeem code +3 ngày ... 2026-08-23` |
| Offline cold start (`svc wifi disable` + airplane mode, xác nhận 0 `NetworkAgentInfo`) | PASS | Mọi `load*` log `⏭️ no internet`, splash navigate nhanh, 0 crash |
| Offline tạo note (interstitial) | PASS | `showInterstitial ⏭️ ad not ready`, màn tạo note vẫn mở bình thường |
| Recovery khi bật mạng lại | PASS | App Open tự load lại (`✅ Loaded gen=2`) và show khi resume, không cần restart app |

Không phát hiện thêm bug nào trong vòng regression này — cả 3 fix của Round-11/12 (vipKeySecret,
backup rules, paidEventListener/errorReporter, revert bannerDestroy) đều sống sót qua 1 chu trình
uninstall/install/offline/online/VIP đầy đủ. Device để lại trạng thái sạch
(`keyVipByKeyUntil = 0`).

## 28. Round-14 (2026-08-21) — Test-device hash không ổn định giữa build type, versionCode 20260821

Tiếp tục audit `RApp.kt` sau Round-13, tập trung riêng vào phần test-device hash vừa được xác nhận
"sạch, không sửa gì" ở cuối Round-13 — audit lại kỹ hơn phát hiện góc chưa test: hash có ổn định
giữa **build type** (debug vs release) trên cùng 1 máy hay không, chỉ mới verify ổn định giữa nhiều
lần mở app của *cùng 1 build*.

**Bug thật: hash test-device gắn theo chữ ký APK, không chỉ theo thiết bị vật lý.** Thu thập hash
Samsung A50s (SM-A507FN) từ log của bản `assembleProductionRelease` (ký bằng keystore thật) — ra
`35F8696D52502118270BCE2A66946380`, khác hẳn hash `813DCF48B3E486F15A60676D49A2AB09` đã thu từ bản
debug ở Round-13 trên cùng máy đó. Hệ quả: nếu chỉ đăng ký hash debug (như code trước fix), máy QA
cài bản release sẽ **không được AdMob nhận diện là test device** → quay lại đúng rủi ro invalid
traffic của lỗi gốc Round-11, nhưng chỉ xảy ra khi test bản release chứ không phải debug (lý do
Round-11/12/13 không bắt được — mọi lần verify trước đó đều làm trên bản debug).

**Fix:** thêm hằng `SAMSUNG_A50S_RELEASE_TEST_DEVICE_HASH = "35F8696D52502118270BCE2A66946380"` vào
`RApp.kt`, đăng ký song song với hash debug qua `AdManager.setTestDeviceIds(...)`. Ghi rõ trong
comment: hash không mang được giữa build type, phải thu thập riêng cho từng variant QA trên cùng máy.

**Verify:** `./gradlew compileDevDebugKotlin compileProductionReleaseKotlin` + `./gradlew test` PASS.
Bump `versionCode 20260820 → 20260821` / `versionName "2026.08.20" → "2026.08.21"`, cập nhật
`CHANGELOG.md`. Chưa re-run full regression device thật cho fix này riêng (bản thân thay đổi chỉ là
thêm 1 hằng số vào danh sách whitelist, không đổi luồng logic nào khác đã regression-test ở Round-13)
— chỉ cần cài lại bản release mới và xác nhận nhãn "Test Ad" hiện đúng trên Samsung A50s ở lần audit
kế tiếp có máy đó trong tay (Pixel 7 Pro hiện có không nằm trong whitelist nên không dùng để verify
nhãn "Test Ad" cho thay đổi này được).

## 29. Round-15 (2026-08-21) — `vipTokenPublicKey` sample-keypair warning: accepted risk, không phải bug

Smoke test bản release 20260821 trên Pixel 7 Pro thấy log `W roy93~AdManager` lúc `RApp.onCreate()`:
`vipTokenPublicKey đang để cặp khoá MẪU công khai — nếu có dùng VIP token
(activateVipByToken/generateVipToken), token có thể bị giả mạo`. Audit ngay để xác nhận có ảnh hưởng
đường redeem-code thật app đang dùng hay không.

**Kết luận: không phải bug, không cần fix.** Grep toàn bộ `app/src/main/kotlin` —
`vipTokenPublicKey`/`activateVipByToken`/`generateVipToken`/`VipSecurity` **0 kết quả**. `RApp.kt`
(`setupAds()`, dòng ~93-134) chỉ set `vipKeySecret` (HMAC anti-tamper cho `loitp_admob.xml`, xem Round
gốc) và `vipRedeemCodes` (map mã redeem → số ngày) — đây là đường VIP **duy nhất** app dùng
(`activateVipByKey`, xác nhận lại ở Round-11/13). `vipTokenPublicKey` là field khác, thuộc cơ chế
token ký-số riêng của SDK mà app chưa từng gọi tới; SDK chỉ cảnh báo vì field đó vẫn giữ giá trị mặc
định (cặp khoá mẫu công khai trong doc SDK) — cảnh báo đúng về mặt SDK, nhưng vô hại với app này vì
đường đó chết (dead path). Không sửa gì trong code.

**Nếu sau này thêm tính năng dùng `activateVipByToken`/`generateVipToken`:** bắt buộc phải tự sinh cặp
khoá riêng bằng `VipSecurity.generateKeyPair()` và set `vipTokenPublicKey` trước khi release — nếu
không, cảnh báo này chuyển từ "vô hại" thành lỗ hổng thật (ai đọc doc SDK cũng forge được token).

