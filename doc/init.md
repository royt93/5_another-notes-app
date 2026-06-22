# Init — Another Notes App

> **Updated:** 2026-06-22 | **Version:** 2026.06.22 (versionCode 20260622)
> **Package:** `com.mckimquyen.notes` | **minSdk:** 24 | **targetSdk:** 36

---

## Mục tiêu chất lượng

- Animation đồng bộ toàn app — mọi fragment transition phải dùng Material Motion
- Zero memory leak — mọi pattern đã được codify trong `doc/memory_leak.md`
- Zero crash — reviewer phải chạy animation test trước khi merge (xem `doc/test/animation_test.md`)

---

## Kiến trúc tổng quan

| Layer | Công nghệ |
|---|---|
| UI | Single-Activity + Navigation Component + Fragment |
| DI | Dagger 2 (kapt) |
| DB | Room v5 (migrations 1→5, FTS4) |
| Ads | AdmobApplovinWrapper 1.1.3 (`com.roy.sdkadbmob.AdManager`) |
| Alarm | AlarmManager + BroadcastReceiver |
| Theming | Material3 DayNight |

---

## Cấu trúc fragment và screen transitions

| Fragment | Enter | Exit | Ghi chú |
|---|---|---|---|
| `HomeFrm` | `MaterialElevationScale(false)` | `Hold()` → `MaterialElevationScale(true)` | Hold giữ FAB khi ContainerTransform chạy |
| `NoteFrm` | `MaterialElevationScale(false)` | `Hold()` | NoteFrm là list note trong drawer |
| `EditFrm` | `MaterialContainerTransform` (shared) | `MaterialContainerTransform` (shared) | Shared element từ card → edit screen |
| `SearchFrm` | `MaterialElevationScale(false)` | `MaterialElevationScale(true)` | |
| `LabelFrm` | `MaterialElevationScale(false)` | `MaterialElevationScale(true)` | + StatusBar ValueAnimator |
| `SettingsFrm` | `MaterialElevationScale(false)` | `MaterialElevationScale(true)` | |
| `VipFrm` | `MaterialSharedAxis(X, true)` | `MaterialSharedAxis(X, false)` | + Entrance ObjectAnimator + looping AnimatorSet |

---

## Ad touchpoints hiện tại (v1.1.3)

Xem chi tiết trong `doc/AD.MD`. Tóm tắt:

| # | Vị trí | Loại |
|---|---|---|
| 1 | `RApp.onCreate` | SDK init |
| 2 | `SplashAct.onCreate` | App Open |
| 3 | Auto (background resume) | App Open |
| 4 | `HomeFrm` — FAB tạo note | Interstitial |
| 5 | `HomeFrm` — swipe delete | Interstitial |
| 6 | `VipFrm` — xem rewarded | Rewarded |

---

## Các file doc liên quan

- `doc/memory_leak.md` — lịch sử fix và pattern bắt buộc
- `doc/quick_win.md` — 5 quick win features đã implement
- `doc/AD.MD` — migration AdMob → AdmobApplovinWrapper 1.1.3
- `doc/multi_language.md` — hỗ trợ ngôn ngữ
- `doc/test/animation_test.md` — bộ test case animation
- `doc/task/` — backlog tính năng (todo / inprogress / done)
