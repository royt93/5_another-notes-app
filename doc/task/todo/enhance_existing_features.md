# Enhance Existing Features — Another Notes App

> **Created:** 2026-06-22 | **Status:** 📋 Picked (chờ prioritize)

Các enhancement cho tính năng **đã có sẵn** — làm tốt hơn, mượt hơn, polished hơn.

---

## E-01: Swipe Animation — Add Momentum + Bounce Back

**Tính năng gốc:** Swipe card → background màu xanh/đỏ, card fade-out khi swipe xong.

**Enhancement:** Khi user swipe chưa đủ ngưỡng rồi thả, card bounce back với spring physics thay vì slide thẳng về. Cảm giác "đàn hồi" tự nhiên hơn.

**Tech:**
- Override `clearView()` trong `SwipeTouchHelperCallback` với `SpringAnimation`
- `SpringForce.STIFFNESS_MEDIUM`, `DAMPING_RATIO_MEDIUM_BOUNCY`

**Effort:** S (1 ngày)
**Files:** `SwipeTouchHelperCallback.kt`

---

## E-02: Word Count — Roll Counter Animation

**Tính năng gốc:** `wordCharCountTxv` update tức thì khi user gõ.

**Enhancement:** Khi số từ/ký tự tăng/giảm, số "cuộn" (roll) như đồng hồ đếm. Dùng `CountingTextView` hoặc custom `ValueAnimator` trên text.

**Tech:**
- `ValueAnimator.ofInt(oldCount, newCount)` với duration ~150ms
- Update `textView.text` trong `addUpdateListener`
- Chỉ animate khi delta > 5 (tránh animate mỗi ký tự)

**Effort:** S (1 ngày)
**Files:** `EditFrm.kt`, `EditVM.kt`

---

## E-03: Pin Animation — Spring Jump to Top

**Tính năng gốc:** Pin note → note move lên đầu list (RecyclerView DiffUtil).

**Enhancement:** Khi pin, note "jump" lên đầu với spring animation (bounce nhẹ khi đến vị trí mới). Highlight bằng flash màu primaryContainer trong 300ms.

**Tech:**
- `ItemAnimator` custom override `animateMove()`
- `SpringAnimation` trên translationY của ViewHolder
- `ViewCompat.setBackgroundTintList()` flash cho highlight

**Effort:** M (2-3 ngày)
**Files:** `NoteListVH.kt`, thêm `SpringItemAnimator.kt`

---

## E-04: Search — Highlight Animation trên kết quả

**Tính năng gốc:** Search term được highlight bằng `SpannableString` màu accent.

**Enhancement:** Khi search term thay đổi, highlight fade-in trên text mới (cross-fade giữa highlight cũ và mới). Không re-render toàn bộ text.

**Tech:**
- `ObjectAnimator` trên `BackgroundColorSpan.bgColor` (custom `AnimatedHighlightSpan`)
- `TextPaint.bgColor` với `invalidate()`
- Chỉ animate khi search term thay đổi, không khi scroll

**Effort:** M (2-3 ngày)
**Files:** `SearchFrm.kt`, `HighlightHelper.kt`

---

## E-05: FAB — Entrance Animation khi mở app

**Tính năng gốc:** FAB xuất hiện tức thì khi HomeFrm load.

**Enhancement:** FAB scale-in từ 0 với spring bounce khi HomeFrm first mount. Chỉ lần đầu, không lặp lại sau navigation.

**Tech:**
- `fab.scaleX = 0f; fab.scaleY = 0f` trước khi show
- `SpringAnimation(fab, DynamicAnimation.SCALE_X).animateToFinalPosition(1f)`
- Track bằng `savedInstanceState` để chỉ run 1 lần per session

**Effort:** XS (0.5 ngày)
**Files:** `HomeFrm.kt`

---

## E-06: Bottom Sheet Sort/Language — Polish Animation

**Tính năng gốc:** `SelectorBottomSheet` mở bằng default BottomSheetDialog animation.

**Enhancement:** 
- Selected item có checkmark fade-in animation khi chọn
- Dismiss sau khi chọn có 150ms delay để user thấy selection
- Ripple màu primaryContainer trên selected item

**Tech:**
- `SelectorBottomSheet.kt:64` — delay dismiss hiện tại đã có ("Animate selection then dismiss")
- Thêm `ImageView` checkmark + `alpha` animator
- Custom `ColorStateList` cho ripple

**Effort:** S (1 ngày)
**Files:** `SelectorBottomSheet.kt`, item layout

---

## E-07: Char Limit Warning — Thêm Progress Ring

**Tính năng gốc:** Snackbar cảnh báo khi ≥90_000 ký tự (CHAR_LIMIT_WARN = 90_000, CHAR_LIMIT = 100_000).

**Enhancement:** Thêm circular progress indicator nhỏ ở góc `wordCharCountTxv`: 
- 0-89%: invisible
- 90-99%: hiện vòng tròn màu cam, fill dần
- 100%: đỏ, pulse animation

**Tech:**
- `CircularProgressIndicator` (Material3) nhỏ 24dp
- `ValueAnimator` animate `progress` property
- Observe cùng `wordCharCount` LiveData

**Effort:** S (1 ngày)
**Files:** `EditFrm.kt`, `f_edit.xml`

---

## E-08: Note Card — Long-press Elevation Lift

**Tính năng gốc:** Long-press note card → selection mode (checkboxes).

**Enhancement:** Trong 200ms đầu của long-press, card "lift" lên (elevation tăng từ 2dp → 8dp + scale 1.02f) TRƯỚC KHI enter selection mode. Cảm giác "nhặt" card.

**Tech:**
- `GestureDetector.onLongPress` hoặc `setOnLongClickListener` với animation trước action
- `ViewCompat.setElevation()` + `ViewPropertyAnimator.scaleX/Y`
- Duration ~150ms

**Effort:** S (1 ngày)
**Files:** `NoteListVH.kt`, `NoteAdt.kt`

---

## E-09: Reminder Dialog — Calendar Date Picker Animation

**Tính năng gốc:** `ReminderDlg` dùng Material `DatePicker` / `TimePicker` mặc định.

**Enhancement:** Khi user chọn ngày trong tương lai, hiện animation nhỏ (calendar icon bounce + checkmark appear) để confirm visual feedback. Thêm "Quick pick" chips: "Tomorrow", "Next week", "In 1 hour".

**Tech:**
- Quick pick chips: `ChipGroup` với 3 `Chip` preset
- Icon animation: `AnimatorSet` scale + alpha
- Kết hợp với `ReminderVM`

**Effort:** M (2-3 ngày)
**Files:** `ReminderDlg.kt`, `ReminderVM.kt`, layout `dlg_reminder.xml`

---

## E-10: Settings — Nested Screen Transition Đồng Bộ

**Tính năng gốc:** `NestedSettingsFrm` navigate từ `SettingsFrm`.

**Enhancement:** Thêm `MaterialSharedAxis(Z, forward)` transition giữa `SettingsFrm` và `NestedSettingsFrm` thay vì default (hoặc không có). Đồng bộ với VipFrm đang dùng SharedAxis.

**Tech:**
- `NestedSettingsFrm.onCreate`: `enterTransition = MaterialSharedAxis(Z, true)`; `returnTransition = MaterialSharedAxis(Z, false)`
- `SettingsFrm`: `exitTransition = MaterialSharedAxis(Z, true)` khi navigate vào nested

**Effort:** XS (0.5 ngày)
**Files:** `NestedSettingsFrm.kt`, `SettingsFrm.kt`

---

## E-11: Widget — Note Preview Animation

**Tính năng gốc:** Widget hiển thị static note content.

**Enhancement:** Widget `RecentNotesWidget` có auto-scroll qua 3-5 notes gần nhất với cross-fade transition mỗi 5 giây (như slideshow).

**Tech:**
- `AppWidgetManager.updateAppWidget()` với mới `RemoteViews` mỗi N giây
- `RemoteViews.setDisplayedChild()` với `ViewFlipper`
- `AlarmManager` trigger update

**Effort:** M (3-4 ngày)
**Files:** `RecentNotesWidget.kt`, widget layout

---

## E-12: Home Empty State — Animated Placeholder

**Tính năng gốc:** Khi không có note, hiện static placeholder image + text.

**Enhancement:** Placeholder có animation: icon "notepad" vẽ bản thân (path animation / AVD), sau đó text fade-in từ dưới lên. Chạy 1 lần khi first open.

**Tech:**
- `AnimatedVectorDrawable` cho icon
- `ViewPropertyAnimator` cho text fade-in
- Track `isFirstOpen` trong `SharedPreferences`

**Effort:** S-M (2 ngày)
**Files:** `PlaceholderData.kt`, `HomeFrm.kt`, drawable AVD

---

## Priority Matrix

| ID | Effort | Impact | Nên làm trước |
|---|---|---|---|
| E-05 | XS | HIGH | ✅ Làm ngay |
| E-10 | XS | MEDIUM | ✅ Làm ngay |
| E-01 | S | HIGH | Tuần tới |
| E-06 | S | MEDIUM | Tuần tới |
| E-07 | S | MEDIUM | Tuần tới |
| E-08 | S | HIGH | Tuần tới |
| E-02 | S | LOW | Backlog |
| E-03 | M | MEDIUM | Backlog |
| E-04 | M | MEDIUM | Backlog |
| E-09 | M | HIGH | Backlog |
| E-11 | M | MEDIUM | Backlog |
| E-12 | M | MEDIUM | Backlog |
