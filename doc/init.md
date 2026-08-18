# Init — Another Notes App

> **Updated:** 2026-08-18 | **Version:** 2026.08.18 (versionCode 20260818)
> **Package:** `com.mckimquyen.notes` | **minSdk:** 24 | **targetSdk:** 37

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
| DB | Room v8 (migrations 1→8, FTS4) — 5→6 mood tag, 6→7 `is_locked`, 7→8 bảng `note_history` |
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
| `SettingsFrm` | `MaterialElevationScale(false)` | `MaterialSharedAxis(Z, true)` khi → Nested | Chỉ override khi navigate to NestedSettingsFrm |
| `NestedSettingsFrm` | `MaterialSharedAxis(Z, true)` | `MaterialSharedAxis(Z, false)` (return) | Đồng bộ với VipFrm pattern |
| `VipFrm` | `MaterialSharedAxis(X, true)` | `MaterialSharedAxis(X, false)` | + Entrance ObjectAnimator + looping AnimatorSet |

---

## Ad touchpoints hiện tại (v1.1.3)

Xem chi tiết trong `doc/AD.MD`. Tóm tắt:

| # | Vị trí | Loại |
|---|---|---|
| 1 | `RApp.onCreate` | SDK init |
| 2 | `SplashActivity.onCreate` (tên class thực tế, không phải `SplashAct`) | App Open |
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
- `doc/task/todo/` — backlog chưa làm (ENHANCE/NEW_FEATURE/IDEA/EXCLUSIVE; `FIX.md` đã đóng — xem `doc/task/BACKLOG.md`)
- `doc/task/BACKLOG.md` — nhật ký Sprint 1-3 (2026-08-17/18): 39 bug fix + 1 revert (exact-alarm permission bị rút vì rủi ro Play Store), có commit hash từng fix
- `doc/task/done/sprint_2026_06_22.md` — sprint 2026-06-22: 9 animation enhancements + 3 new features, build PASS

## Features nổi bật đã implement (sprint 2026-06-22)

| Feature | Mô tả | File |
|---|---|---|
| FAB Spring Entrance | Scale-in OvershootInterpolator khi mở app | `HomeFrm.kt` |
| FAB Scroll Hide/Show | `fab.hide/show()` khi scroll RecyclerView | `HomeFrm.kt` |
| Swipe Spring Bounce | Card bounces back với OvershootInterpolator(1.8) | `SwipeTouchHelperCallback.kt` |
| Card Lift Long-press | Scale + translationZ lift trước selection mode | `NoteListVH.kt` |
| Word Count Roll | ValueAnimator counter khi delta >5 | `EditFrm.kt` |
| Char Progress Ring | CircularProgressIndicator 20dp (primary→error) | `EditFrm.kt`, `f_edit.xml` |
| Word Milestone Celebration | Snackbar + bounce tại 100/500/1k/5k words | `EditVM.kt`, `EditFrm.kt` |
| Focus Mode | Toolbar mờ 0.15, ẩn bottom UI, Back để thoát | `EditFrm.kt` |
| Reminder Quick Pick | 3 chips in 1h / tomorrow / next week | `ReminderDlg.kt`, `dlg_reminder.xml` |
| Empty State Animation | Placeholder scale-in + text fade khi list trống | `NoteFrm.kt` |
| Bottom Sheet Checkmark | Checkmark scale+fade khi chọn sort/language | `SelectorBottomSheet.kt` |
| Search Highlight Crossfade | `DefaultItemAnimator(changeDuration=120)` | `SearchFrm.kt` |
| Pin Spring Jump | `SpringItemAnimator` overshoot khi pin note | `SpringItemAnimator.kt` |
| Note Mood Tag | 5 emoji moods, Room migration 5→6, picker + badge trên card | `Note.kt`, `NotesDb.kt`, `EditVM.kt`, `EditFrm.kt` |
| Timeline View | Layout thứ 3 — nhóm theo ngày, đường timeline trái | `NoteListLayoutMode.kt`, `NoteVM.kt`, `NoteAdt.kt` |
