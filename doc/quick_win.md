# Quick Wins — Another Notes App

> Updated: 2026-02-20 | Tất cả 5 tính năng đã được implement ✅

| # | Feature | Files changed | Status |
|---|---------|--------------|--------|
| 1 | **Word/Char Count** — Footer hiển thị số từ và ký tự trong Edit screen | `EditVM`, `EditFrm`, `f_edit.xml` | ✅ DONE |
| 2 | **Swipe Color Feedback** — Nền xanh (archive) / đỏ (delete) khi swipe note | `SwipeTouchHelperCallback` | ✅ DONE |
| 3 | **Char Limit Warning** — Snackbar cảnh báo khi content ≥9000 hoặc ≥10000 ký tự | `EditVM`, `EditFrm` | ✅ DONE |
| 4 | **Ad After Delete** — Interstitial ad khi user xóa (move to trash) note | `HomeFrm` | ✅ DONE |
| 5 | **Quick Note Widget** — Home screen widget tap để tạo note ngay | `QuickNoteWidget`, `AndroidManifest`, layouts | ✅ DONE |

---

## Chi tiết

### Feature 1 — Word/Char Count Footer

- `EditVM.wordCharCount: LiveData<Pair<Int,Int>>` — cập nhật sau mỗi `updateNote()`
- `f_edit.xml` — thêm `wordCharCountTxv` ở góc bottom-end
- `EditFrm` — observe và format `"N words · M chars"`

### Feature 2 — Swipe Color Feedback

- `SwipeTouchHelperCallback.onChildDraw()` — vẽ `Canvas.drawRoundRect()` phía sau card
- Màu: `#4CAF50` (archive) / `#F44336` (delete), alpha 200, corner 16dp

### Feature 3 — Character Limit Warning

- `EditVM.CHAR_LIMIT = 10_000`, `CHAR_LIMIT_WARN = 9_000`
- Chỉ fire 1 lần mỗi threshold (reset khi user xóa bớt về dưới 9000)
- `EditFrm` — Snackbar cam (90%) và đỏ (100%)

### Feature 4 — Ad After Delete

- `HomeFrm.setupViewModelObservers()` — observe `sharedViewModel.statusChangeEvent`
- Nếu `newStatus == DELETED` → `AdMobManager.showInterstitial()` (fire-and-forget)

### Feature 5 — Quick Note Widget

- `QuickNoteWidget.kt` — `AppWidgetProvider` với `PendingIntent → INTENT_ACTION_CREATE`
- `res/xml/widget_provider.xml` + `res/layout/widget_quick_note.xml` + `drawable/widget_background.xml`
- Đăng ký trong `AndroidManifest.xml`
