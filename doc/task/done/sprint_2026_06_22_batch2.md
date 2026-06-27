# Sprint Batch 2 — Done ✅

> **Implemented:** 2026-06-22 | **Build:** PASS (661ms)
> 7 items: E-03, E-04, E-06, E-12 (enhancements) + F-01, F-06 (new features) + E-05 fix

---

## Fixes

| ID | Fix | File |
|---|---|---|
| E-05 | FAB spring: dùng `translationY` thay `scaleX/Y` → không conflict với `fab.show()` internal animation | `HomeFrm.kt` |

---

## Enhancements

| ID | Tên | Files |
|---|---|---|
| E-12 | Empty State Animation: placeholder scale-in `OvershootInterpolator(1.5)` + text fade-in delay 120ms | `NoteFrm.kt` |
| E-06 | Bottom Sheet Checkmark: payload-based `onBindViewHolder` → checkmark scale+fade `OvershootInterpolator(2)` 180ms | `SelectorBottomSheet.kt` |
| E-04 | Search Highlight Animation: `DefaultItemAnimator(changeDuration=120)` thay vì `supportsChangeAnimations=false` | `SearchFrm.kt` |
| E-03 | Pin Spring Jump: `SpringItemAnimator` — override `animateMove()`, khi `deltaY < 0` dùng `OvershootInterpolator(1.6)` 480ms | `SpringItemAnimator.kt` (mới), `NoteFrm.kt` |

---

## New Features

| ID | Tên | Files |
|---|---|---|
| F-01 | Note Mood Tag: `mood: Int = 0` trên `Note` entity, Room migration 5→6, picker 5 emoji trong EditFrm, badge trên card | `Note.kt`, `NotesDb.kt`, `EditVM.kt`, `EditFrm.kt`, `NoteListVH.kt`, `f_edit.xml`, `v_item_note_text.xml` |
| F-06 | Timeline View: layout thứ 3 (`NoteListLayoutMode.TIMELINE`), `TimelineVH.kt`, date header items, `LinearLayoutManager`, toggle trong toolbar | `NoteListLayoutMode.kt`, `NoteListItem.kt`, `NoteAdt.kt`, `NoteListDiffCallback.kt`, `NoteVM.kt`, `NoteFrm.kt`, `HomeFrm.kt`, 3 layout XMLs mới |
