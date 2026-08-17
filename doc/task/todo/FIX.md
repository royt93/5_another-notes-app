# FIX Backlog — Another Notes App

> **Tạo:** 2026-08-17 | **Phương pháp:** Audit độc lập song song bằng 6 nguồn (4 subagent nội bộ chia theo tầng kiến trúc + agy CLI + Claude CLI `--dangerously-skip-permissions`, mỗi nguồn đọc toàn bộ source code từ đầu, không tham khảo backlog cũ), sau đó **dedup + xác minh chéo bằng cách đọc lại code thật** cho các item quan trọng nhất trước khi đưa vào đây. Codex CLI hết quota (usage limit tới 2026-08-20), không có kết quả.
>
> Nguồn thô: [audit_agy.md](../audit_raw/audit_agy.md), [audit_claude_bypass.md](../audit_raw/audit_claude_bypass.md).

## Quy ước độ tin cậy

- ✅ **Verified** — đã tự đọc lại source code thật trong phiên audit này để xác nhận (trích dòng code cụ thể).
- 🔁 **Cross-confirmed (N nguồn)** — N/6 nguồn độc lập cùng phát hiện (chưa tự đọc lại tay, nhưng nhiều nguồn trùng khớp mô tả + vị trí).
- ⚠️ **Single-source, cần xác minh thêm** — chỉ 1 nguồn phát hiện, chưa tự verify. Ưu tiên đọc code trước khi bắt tay sửa.

Priority: P0 (khẩn/chặn release) · P1 (nên làm sớm) · P2 (backlog). Effort: XS/S/M/L.

---

## Critical

> ✅ **User đã duyệt 2026-08-17: cả 4 mục Critical = P0, sửa ngay trong sprint hiện tại.** FIX-C04 gộp chung với FIX-H01 thành epic ENH-A01.

### FIX-C01 — Export/Import JSON xoá mất `color`, `mood`, và **mở khoá lại note đã khoá** ✅🔁(3 nguồn)
**File:** `model/DefaultJsonManager.kt` — class `NoteSurrogate` (~dòng 297-321), dùng ở export (~56-60) và import (~212-223, 259-276).
**Mô tả:** `NoteSurrogate` (DTO export/import) không có field `color`, `mood`, `isLocked` dù `Note` entity đã có 3 field này từ Room migration 5→6, 6→7. Export bỏ sót; import luôn tạo `Note` với giá trị mặc định (`0`, `0`, `false`).
**Kịch bản:** Tô màu note, gắn mood, khoá bằng sinh trắc học → Export → Import lại (kể cả trên chính máy đó, hoặc cài lại app) → mất màu/mood, và **note đã khoá bị mở khoá** — hồi quy bảo mật thật sự, không chỉ mất thẩm mỹ.
**Effort:** S. **Priority:** P0.
**Nguồn:** subagent data-layer (Bug 1), claude-bypass (FIX-004), agy (FIX-01).

### FIX-C02 — `mergeNotes()` xoá reminder ngay cả khi reminder không hề thay đổi ✅
**File:** `model/DefaultJsonManager.kt:259-276`.
**Đã verify bằng cách đọc code:**
```kotlin
val reminder = when {
    old.reminder == null && new.reminder != null -> new.reminder
    old.reminder != null && new.reminder == null -> old.reminder
    old.reminder != null && new.reminder != null &&
            !compareReminders(old.reminder, new.reminder) -> return null
    else -> null   // <-- rơi vào đây khi cả 2 reminder giống hệt nhau
}
return new.copy(reminder = reminder)
```
Nhánh `when` không có case nào xử lý "cả 2 reminder non-null VÀ giống nhau" — rơi vào `else -> null`, xoá sạch reminder dù không có gì đổi.
**Kịch bản:** Tạo note có reminder đang hoạt động → Export → Import lại đúng file đó không sửa gì (đi vào nhánh merge vì `addedDate`/`lastModifiedDate` không đổi) → reminder biến mất khỏi UI. Kèm theo: `ReminderAlarmManager.updateAllAlarms()` chỉ quét note còn `reminder != null` nên **alarm cũ trong `AlarmManager` không bị huỷ** → alarm mồ côi vẫn nổ dù UI không còn hiển thị reminder.
**Effort:** S. **Priority:** P0.
**Nguồn:** claude-bypass (FIX-001), tự verify.

### FIX-C03 — Recurrence "ngày cuối tháng" tính sai vì dùng nhầm field `Calendar` ✅
**File:** `ui/reminder/ReminderVM.kt:167-171` (`updateRecurrenceForDate`).
**Đã verify:**
```kotlin
if (recurrence.byMonthDay == -1 &&
    calendar[Calendar.DATE] != calendar.getActualMaximum(Calendar.MONTH)) {
    recurrence = Recurrence(recurrence) { dayInMonth = 0 }
}
```
`getActualMaximum(Calendar.MONTH)` luôn trả `11` (tháng 12, 0-index) — hoàn toàn không liên quan tới "ngày cuối tháng". Đúng ra phải là `getActualMaximum(Calendar.DAY_OF_MONTH)`.
**Kịch bản:** Chọn ngày bắt đầu 31/1, đặt recurrence "hàng tháng vào ngày cuối tháng". Vì `calendar[Calendar.DATE]` (1-31) hầu như luôn khác 11, cờ `byMonthDay == -1` bị xoá âm thầm → recurrence suy biến thành "cùng ngày-trong-tháng với ngày bắt đầu" thay vì luôn là ngày cuối tháng.
**Effort:** XS. **Priority:** P0.
**Nguồn:** claude-bypass (FIX-003), tự verify.

### FIX-C04 — `debugCheck`/`debugRequire` throw crash thật trong bản RELEASE (source-set nối sai) ✅🔁(2 nguồn)
**File:** `debug/notes/DebugExtensions.kt` (không có khai báo `package` → default package, bản THROW thật) — bản no-op đúng nằm ở `src/release/kotlin/com/maltaisn/notes/DebugExtensions.kt` nhưng **không ai import**. Mọi call site (`import debugCheck`/`debugRequire` trần) resolve về bản throw-thật trong MỌI variant kể cả release.
**Call site bị ảnh hưởng:** `Note.kt` (`debugRequire` kiểm tra invariant ngày tháng/pinned/status), `SortDialog.kt`, `ReminderDlg.kt`, `LabelEditDlg.kt`, `SearchVM.kt`.
**Kịch bản:** 1 dòng DB cũ/hỏng có `addedDate > lastModifiedDate` (migration lỗi, lệch giờ đồng hồ, hoặc import backup hỏng) → `Note` constructor throw `IllegalArgumentException` ngay khi load → **crash bản release thật**, trái với docstring "chỉ check ở debug mode, release phải xử lý đúng".
**Effort:** S (đổi package đúng slot hoặc chuyển file throw vào `src/debug/kotlin` thật). **Priority:** P0.
**Nguồn:** subagent data-layer (Bug 3), claude-bypass (FIX-002).

---

## High

> ✅ **User đã duyệt 2026-08-17:**
> - **P0 (sửa ngay):** FIX-H01 (gộp FIX-C04), FIX-H02, FIX-H03, FIX-H06, FIX-H07, FIX-H08, FIX-H09
> - **P1 (sớm):** FIX-H05, FIX-H10
> - **P2 (backlog):** FIX-H04 (exact alarm — cần thiết kế UI xin quyền trước, không phải 1-dòng-sửa)

### FIX-H01 — `BuildTypeModule` luôn resolve bản Debug ở MỌI variant, kể cả release ✅🔁(2 nguồn)
**File:** `di/AppModule.kt` import cứng `com.mckimquyen.debug.notes.di.BuildTypeModule`. Bản release ở `src/release/kotlin/com/maltaisn/notes/di/BuildTypeModule.kt` (bind `ReleaseBuildTypeBehavior` no-op) không được include ở đâu — dead code.
**Hệ quả:** `HomeVM.doExtraAction()` luôn gọi `DebugBuildTypeBehavior.doExtraAction()` — chèn 3 note rác vào DB thật ở mọi build, kể cả release. Hiện bị chặn gián tiếp bởi `HomeFrm.kt` ẩn menu item khi `BuildConfig.ENABLE_DEBUG_FEATURES=false`, nhưng đây là lớp bảo vệ UI-only, không phải do DI graph tự chặn.
**Effort:** S. **Priority:** P0 (đi cùng FIX-C04, cùng gốc rebrand source-set).
**Nguồn:** subagent data-layer (Bug 4), claude-bypass (FIX-005).

### FIX-H02 — `MainAct`: "Bấm back 2 lần để thoát" hỏng hoàn toàn — thoát app ngay lần bấm đầu ✅
**File:** `ui/main/MainAct.kt:81, 175-184`.
**Đã verify:** `doubleBackToExitPressedOnce` khởi tạo `true` (dòng 81). Callback back tại Home:
```kotlin
if (doubleBackToExitPressedOnce) {
    isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true   // thoát ngay
} else {
    doubleBackToExitPressedOnce = true
    Toast... "Bấm lần nữa để thoát"
    exitHandler.postDelayed(resetExitRunnable, 2500)   // chỉ nhánh này set flag về false sau 2.5s
}
```
Vì flag khởi tạo `true` và chỉ nhánh `else` (không bao giờ chạy trước, vì flag ban đầu là `true`) mới đưa flag về `false`, **toast "bấm lần nữa để thoát" không bao giờ hiện** — mọi lần bấm back ở Home đều thoát app ngay lập tức. Tính năng hoàn toàn không hoạt động, không phải edge case.
**Effort:** XS (đảo giá trị khởi tạo ban đầu thành `false`, hoặc đảo điều kiện nhánh if). **Priority:** P0 (feature chết hoàn toàn, fix cực rẻ).
**Nguồn:** agy (FIX-04), tự verify — nâng severity từ Medium (agy) lên High vì ảnh hưởng 100% người dùng, không phải edge case.

### FIX-H03 — `VipFrm`: callback reward-ad chạm `binding` sau khi `onDestroyView()` đã null hoá → crash NPE ✅
**File:** `ui/vip/VipFrm.kt:107-146` (`setupWatchAdButton`), `150-158` (`onDestroyView`), `306-316` (`onActivationSuccess`).
**Đã verify:** Guard trong callback `AdManager.showRewarded(requireActivity()) { ... val ctx = context ?: return@showRewarded ... }` chỉ check `context` (Fragment vẫn attached), KHÔNG check `_binding == null`. `context` vẫn non-null giữa `onDestroyView()` và `onDetach()`. `grantVip3Days(ctx)` → `onActivationSuccess()` gọi `renderState()`/`Snackbar.make(binding.root, ...)` chạm thẳng `binding` (`_binding!!`).
**Kịch bản:** Bấm "Watch Ad" → back rời `VipFrm` đúng lúc ad trả thưởng/đóng (`onDestroyView()` chạy trước khi callback trả kết quả) → NPE crash. Đây là race qua back-navigation timing bình thường, không phải edge case hiếm.
**Effort:** S (thêm guard `_binding == null` hoặc `!isAdded`). **Priority:** P1.
**Nguồn:** claude-bypass (FIX-013), tự verify.

### FIX-H04 — Reminder dùng inexact alarm — thông báo có thể trễ hàng chục phút tới cả giờ trong Doze mode ✅🔁(3 nguồn)
**File:** `receiver/ReceiverAlarmCallback.kt:23-42`. Dùng `alarmManager.set(AlarmManager.RTC_WAKEUP, ...)` thay vì `setExactAndAllowWhileIdle` (code exact-alarm đã viết sẵn nhưng **bị comment out**, kèm TODO xác nhận đây là hành vi chưa hoàn thiện). Quyền `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` trong `AndroidManifest.xml` cũng đang bị comment.
**Kịch bản:** Đặt reminder, khoá màn hình để máy vào Doze → thông báo trễ 15-60+ phút thay vì đúng giờ.
**Effort:** M (uncomment + xin quyền đúng cách cho Android 12+, có fallback khi user từ chối). **Priority:** P1.
**Nguồn:** subagent reminder-audit (Bug 1+2), claude-bypass (FIX-014), agy (FIX-10).

### FIX-H05 — Import JSON không atomic — crash giữa chừng làm mất toàn bộ liên kết note-label
**File:** `model/DefaultJsonManager.kt` (`importLabels`, `importNotes`). Không có `@Transaction`/`db.withTransaction{}` bọc toàn bộ import; `labelRefs` gom vào list local, chỉ flush **một lần duy nhất ở cuối** vòng lặp.
**Kịch bản:** Import backup lớn (hàng trăm note) trên máy yếu, process bị kill giữa vòng lặp → note đã insert vẫn tồn tại vĩnh viễn nhưng TOÀN BỘ liên kết label bị mất (vì `insertRefs` chưa kịp chạy). Ngoài ra `Note` constructor có `debugRequire` invariant check (xem FIX-C04) — 1 dòng JSON hỏng có thể throw giữa chừng, không được catch ở bất kỳ đâu kể cả nơi gọi (`SettingsVM.importData()`), gây crash mất dữ liệu import dở dang.
**Effort:** M (bọc `db.withTransaction{}`, thêm try/catch ở tầng gọi, đổi sang `insertAll`/`updateAll` batch). **Priority:** P1.
**Nguồn:** subagent data-layer (Bug 2), claude-bypass (FIX-010).

### FIX-H06 — `InputStream`/`OutputStream` bị leak khi import/export gặp lỗi
**File:** `ui/setting/SettingsVM.kt:72-141` (`exportData`, `importData`, `setupAutoExport`). Import: `input.bufferedReader().readText()` không bao giờ `.use{}`/`close()` kể cả nhánh lỗi. Export: `output` chỉ đóng trong nhánh try thứ 2; nếu `jsonManager.exportJsonData()` throw trước đó, `output.use{}` không bao giờ chạm tới.
**Kịch bản:** Dùng Import/Export nhiều lần trong 1 session, hoặc auto-export hàng ngày gặp lỗi serialize → leak file descriptor tích luỹ, có thể `EMFILE`.
**Effort:** XS mỗi chỗ (bọc toàn hàm trong `use{}`/`finally`). **Priority:** P1.
**Nguồn:** claude-bypass (FIX-008, FIX-009).

### FIX-H07 — `MainAct.onNewIntent()` không gọi lại `handleIntent()` — chạm thông báo reminder khi app đang mở bị no-op ✅
**File:** `ui/main/MainAct.kt:276-279` (`onNewIntent` chỉ set `this.intent = intent`), `handleIntent()` chỉ được gọi từ `onResume()` (dòng 308-311).
**Đã verify code khớp đúng mô tả.** `MainAct` là `launchMode="singleTask"`. Theo lifecycle chuẩn Android, activity đang ở trạng thái resumed nhận intent mới thì hệ thống gọi `onNewIntent()` mà KHÔNG kèm `onResume()`.
**Kịch bản:** App đang mở ở bất kỳ màn hình nào → reminder nổ, heads-up notification hiện đè lên → chạm thẳng vào banner mà không rời app trước → không có gì xảy ra, note không mở. Cùng lỗi cho shortcut `SHOW_REMINDERS`/`CREATE` khi app đang hiển thị (split-screen/multi-window).
**Effort:** XS (gọi `handleIntent()` trong `onNewIntent()`). **Priority:** P1.
**Nguồn:** claude-bypass (FIX-011), tự verify.

### FIX-H08 — `BaseAct.onResume()` kích hoạt popup Play In-App Review từ MỌI activity, kể cả `NotificationAct` trong suốt
**File:** `ui/main/BaseAct.kt` — `rateAppInApp()` gọi unconditional trong `onResume()` của class cha, chỉ chặn bởi cooldown ngày. `BaseAct` là cha của cả `SplashActivity`, `MainAct`, `NotificationAct`. Riêng agy còn ghi nhận thêm: giá trị mặc định `last_review_time = 0L` khiến lần mở app đầu tiên sau cài đặt cũng thoả điều kiện ">N ngày" → review popup hiện ngay từ lần mở đầu tiên, trước khi user tạo note nào (claude-bypass gọi đây là bug riêng FIX-012, agy gọi là FIX-13 — cùng gốc `BaseAct.onResume()`, gộp làm 1 item).
**Kịch bản:** Cài mới app, mở lần đầu → popup đánh giá 5 sao hiện ngay. Hoặc: chạm "Postpone" trên thông báo reminder (≥N ngày từ lần review cuối) → `NotificationAct` (theme trong suốt) bất ngờ hiện full Play Store review sheet đè lên.
**Effort:** XS (chuyển `rateAppInApp()` xuống riêng `MainAct.onResume()`, và sửa giá trị default `last_review_time` thành thời điểm cài đặt thay vì `0L`). **Priority:** P1.
**Nguồn:** claude-bypass (FIX-012), agy (FIX-13).

### FIX-H09 — `HomeFrm`: double interstitial ad khi Fragment view recreate ngay sau khi xoá note
**File:** `ui/home/HomeFrm.kt:216-230`. `lastAdEvent` là biến local reset `null` mỗi lần `setupViewModelObservers()` chạy (mỗi `onViewCreated`), trong khi `viewModel.statusChangeEvent` là sticky `LiveData<Event<...>>` — observer mới đăng ký luôn nhận lại giá trị cuối cùng ngay lập tức.
**Kịch bản:** Xoá 1 note (ad hiện) → xoay máy hoặc Home bị recreate → cùng `Event` bị redeliver, guard bị reset → `AdManager.showInterstitial()` nổ lại ad cho hành động user đã thấy ad rồi.
**Effort:** S. **Priority:** P1 (rủi ro chính sách ad network + UX khó chịu).
**Nguồn:** claude-bypass (FIX-015).

### FIX-H10 — EditFrm: TextWatcher chồng chất vô hạn trên EditText bị RecyclerView tái sử dụng ✅
**File:** `ui/edit/EditFrm.kt:178-186`. `addOnChildAttachStateChangeListener` gắn thêm 1 `TextWatcher` (`doAfterTextChanged`) mỗi lần một child ViewHolder (item content HOẶC checklist row — cùng dùng `R.id.contentEdt`) attach vào window; `onChildViewDetachedFromWindow` để trống nên watcher không bao giờ bị gỡ, trong khi RecyclerView tái sử dụng chính View object khi scroll.
**Kịch bản:** Note dạng checklist nhiều dòng, cuộn lên xuống nhiều lần → mỗi vòng attach/detach cộng thêm 1 watcher trùng lặp trên cùng View → gõ phím sau đó gọi `updateLiveStats()` lặp lại hàng chục/hàng trăm lần → giật UI, ANR risk với note dài.
**Effort:** S (gắn watcher 1 lần trong ViewHolder `init`, gỡ ở `onRecycled`, thay vì listener cấp Fragment). **Priority:** P1.
**Nguồn:** subagent UI-audit (Bug 1) — single-source nhưng trích dẫn code cụ thể, độ tin cậy cao.

---

## Medium

> ✅ **User đã duyệt 2026-08-17:**
> - **P1 (sớm):** M01, M02, M03, M04, M05, M07, M11, M13, M14 (đã tự verify: CONFIRMED, xem chi tiết cuối bảng), M15 (đã tự verify: CONFIRMED, xem chi tiết cuối bảng), M17, M18, M20, M23
> - **P2 (backlog):** M06, M08, M09, M10, M12, M19, M21, M22, M24, M25, M26
> - **Loại bỏ khỏi backlog:** M16 — đã tự verify bằng cách đọc toàn bộ `ReminderDlg.onCreateDialog()`: `requestNotificationPermission()` gọi đồng bộ trong `onCreateDialog()` (dòng 164-166), chạy trước STARTED, đúng chuẩn AndroidX. Không phải bug.

| ID | Tiêu đề | File | Nguồn | Effort |
|---|---|---|---|---|
| FIX-M01 | `PrefsManager` enum preference crash (`NoSuchElementException`) nếu giá trị lưu không khớp enum hiện tại — hazard khi tương lai xoá/đổi tên hằng enum | `model/PrefsManager.kt:120-125` | claude-bypass (FIX-007), subagent data-layer (nghi ngờ) | XS |
| FIX-M02 | `MainAct.onStart()` đăng ký lại LiveData Observer mỗi chu kỳ stop→start, không bao giờ gỡ (Activity đơn không recreate như Fragment) | `ui/main/MainAct.kt:295-306` | subagent UI-audit (Bug 2), claude-bypass (FIX-029) | XS |
| FIX-M03 | `OnBackPressedCallback` trong `EditFrm` đăng ký bằng Fragment (`this`) thay vì `viewLifecycleOwner` — tích luỹ callback qua nav back-stack (Nav Component hạ lifecycle Fragment xuống CREATED khi qua Reminder/Labels rồi quay lại) | `ui/edit/EditFrm.kt:126` | subagent UI-audit (Bug 4) | XS |
| FIX-M04 | `NoteCountWidget.onUpdate()`: `CoroutineScope(Dispatchers.IO).launch{}` không `goAsync()`, không huỷ — đúng anti-pattern đã fix ở `AlarmReceiver` nhưng tái diễn ở widget | `widget/NoteCountWidget.kt:64-70` | subagent ads/widget-audit (Bug 1), claude-bypass (FIX-022), agy (FIX-05) | S |
| FIX-M05 | Widget (`NoteCountWidget`, `RecentNotesWidget`) không refresh sau khi Import JSON — import đi thẳng qua DAO, bỏ qua `DefaultNotesRepository` (nơi duy nhất gọi `updateAllWidgets()`) | `model/DefaultJsonManager.kt`, `ui/setting/SettingsVM.kt` | subagent ads/widget-audit (Bug 2) | S |
| FIX-M06 | Dialog thường (không phải `DialogFragment`) trong `VipFrm` (`showActivateDialog`, `showResetConfirm`) leak window nếu Activity recreate khi đang mở (xoay màn hình) | `ui/vip/VipFrm.kt:257-281, 318-329` | subagent UI-audit (Bug 3) | S |
| FIX-M07 | ViewPropertyAnimator trong `EditFrm` (char-limit ring, word count, toolbar) không bị cancel trong `onDestroyView()` — callback `withEndAction` chạm `binding` sau khi đã null → NPE risk trong cửa sổ ~200-400ms | `ui/edit/EditFrm.kt:508-546, 705-729` | subagent UI-audit (Bug 5) | S |
| FIX-M08 | `LabelEditVM.updateError()`: mỗi keystroke launch coroutine validate mới, không huỷ cái cũ/không debounce → race condition có thể để lọt tên label trùng (DB không có UNIQUE constraint) | `ui/labels/LabelEditVM.kt:72-110` | claude-bypass (FIX-016) | S |
| FIX-M09 | `LabelVM.renamingLabel` kẹt `true` sau khi cancel rename, âm thầm xoá selection không liên quan ở lần thay đổi label tiếp theo | `ui/labels/LabelVM.kt:128-183` | claude-bypass (FIX-017) | S |
| FIX-M10 | `SortDialog` mất lựa chọn sort hiện tại khi Fragment recreate (đổi cỡ chữ hệ thống, split-screen resize) — Apply có thể âm thầm ghi đè sort thật của user về mặc định | `ui/sort/SortDialog.kt:80-82` | claude-bypass (FIX-018) | S |
| FIX-M11 | Auto-export: quyền URI persisted bị leak vĩnh viễn nếu `openOutputStream()` lỗi sau `takePersistableUriPermission()` — nhánh lỗi chỉ tắt UI switch, không gọi `disableAutoExport()` | `ui/setting/SettingsFrm.kt:104-123` | claude-bypass (FIX-020) | XS |
| FIX-M12 | `getRecentNotes(5)` hard-code 5 item, khác với doc comment "up to 10" và default 10 của DAO | `widget/RecentNotesWidgetService.kt:37` | claude-bypass (FIX-033) | XS |
| FIX-M13 | Widget hiện tiêu đề note đã khoá dưới dạng rõ chữ, chỉ ẩn phần content — bề mặt lộ lớn hơn cả trong-app vì widget hiện trên home-screen không cần mở khoá | `widget/RecentNotesWidgetService.kt:56-66` | claude-bypass (FIX-034) | XS→S |
| FIX-M14 | `ExportHelper.exportAsPdf()`: tiêu đề PDF vẽ đè tại toạ độ (0,0), thiếu `canvas.translate(marginLeft, currentY)` như các layout khác — mất lề trái/trên | `ui/edit/ExportHelper.kt:86-89` | agy (FIX-07) — ⚠️ chưa tự verify | XS |
| FIX-M15 | Timeline view: `TimelineDateHeaderItem` có thể trùng `headerId` khi sort không theo ngày tạo (Title A-Z, Modified date) — note cùng ngày không còn nằm cạnh nhau → DiffUtil nhận ID trùng | `ui/note/NoteVM.kt:48-66`, `TimelineVH.kt` | agy (FIX-08) — ⚠️ chưa tự verify | S |
| FIX-M16 | `ReminderDlg.requestNotificationPermission()` gọi `registerForActivityResult` không phải trong `onCreate()` trực tiếp mà trong hàm được gọi từ `onCreateDialog()` — **đã kiểm tra: `onCreate()` override tồn tại riêng ở dòng 70, cần verify thêm liệu registration có thực sự nằm ngoài `onCreate()` hay không trước khi coi là bug thật** | `ui/reminder/ReminderDlg.kt:70, 271-274` | agy (FIX-09) — ⚠️ mâu thuẫn nội bộ, cần đọc kỹ toàn bộ `onCreate()` trước khi sửa | XS |
| FIX-M17 | Checklist: `uncheckAllItems()` tạo object mới (`.copy()`) thay vì mutate → phá vỡ identity DiffUtil (`areItemsTheSame` dùng `===`) → nhấp nháy/mất focus khi "Uncheck all" (đã có `// FIXME breaks animation` ngay trong code) | `ui/edit/EditVM.kt:616-624`, `EditDiffCallback.kt:7-18` | claude-bypass (FIX-027) | M |
| FIX-M18 | Checklist mất trạng thái "đã check" khi paste nhiều dòng làm tách item — phụ thuộc tréo ngoe vào setting "move checked to bottom" không liên quan | `ui/edit/EditVM.kt:1034-1064` | claude-bypass (FIX-026) | XS |
| FIX-M19 | Không xử lý `ACTION_TIMEZONE_CHANGED`/`ACTION_TIME_CHANGED` — reminder định kỳ nổ sai giờ địa phương sau khi đổi múi giờ (bay sang timezone khác) | không có receiver nào lắng nghe (grep xác nhận) | claude-bypass (FIX-025) | M |
| FIX-M20 | Reboot chỉ phát `QUICKBOOT_POWERON` (HTC/OEM fast-boot) không reschedule alarm — `AlarmReceiver` chỉ xử lý `ACTION_BOOT_COMPLETED` trong `when`, thiếu nhánh cho action kia dù Manifest có đăng ký cả 3 | `AndroidManifest.xml:55-58` vs `receiver/AlarmReceiver.kt` | claude-bypass (FIX-023) | XS |
| FIX-M21 | Undo-xoá của reminder định kỳ đã quá hạn dùng thẳng `reminder.next` cũ (không tính lại lần lặp đã quá hạn) → có thể nổ thông báo giả/cũ gần như ngay lập tức sau Undo | `model/ReminderAlarmManager.kt:26-33` | claude-bypass (FIX-024) | S |
| FIX-M22 | Thông báo reminder mở note nhấn nhiều lần chồng nhiều `fragment_edit` trên back stack — `navigateSafe(..., allowSameDest = true)` cố ý bỏ qua guard chống trùng destination | `ui/main/MainAct.kt:247-251` | claude-bypass (FIX-030) | S |
| FIX-M23 | `NonCancellable` không nhất quán giữa các method mutate DB cùng 1 repository (`deleteOldNotesInTrash`, `clearAllData`, `deleteLabel(s)` thiếu, trong khi các method khác cùng class có) — coroutine bị huỷ đúng lúc có thể bỏ sót refresh widget dù DB đã ghi xong | `DefaultNotesRepository.kt:81-94`, `DefaultLabelsRepository.kt:25-31` | claude-bypass (FIX-031) | XS |
| FIX-M24 | `PrefsManager` default value giữa code Kotlin và `res/xml/prefs.xml` lệch nhau (vd `strikethrough_checked`: Kotlin `false` vs XML `true`) — hiện bị che vì `RApp.onCreate()` luôn gọi `setDefaults()` trước, nhưng là bẫy cho test dựng `SharedPreferences` in-memory riêng | `model/PrefsManager.kt` vs `res/xml/prefs.xml`, `prefs_preview_lines.xml` | claude-bypass (FIX-019) | S |
| FIX-M25 | Import/export password material không được xoá khỏi bộ nhớ sau khi dùng (`deriveKey` không `clearPassword()`; `ExportPasswordVM`/`ImportPasswordVM` giữ password plaintext trong `SavedStateHandle` không giới hạn thời gian sống) | `ui/setting/SettingsVM.kt:211-218`, `ExportPasswordVM.kt`, `ImportPasswordVM.kt` | claude-bypass (FIX-021) | S |
| FIX-M26 | Dedup label không phân biệt hoa-thường — `"Work"` và `"work"` tồn tại song song | `ui/labels/LabelEditVM.kt` | claude-bypass (ENH-021, xếp lại vào FIX vì là data-integrity gap chứ không phải enhancement thuần) | XS |

---

## Low

> ✅ **User đã duyệt 2026-08-17:**
> - **P1 (sớm, nâng hạng):** L04 (rủi ro R8 rename), L08 — đã tự verify: **CONFIRMED crash thật** (`uri.pathSegments.last()` throw `NoSuchElementException`, không phải subtype `IOException` nên không bị catch bởi try/catch bao quanh — nâng từ Low lên P1)
> - **P2 (backlog, giữ Low):** L01, L02, L03, L05, L06, L07, L09 (đã tự verify: CONFIRMED — `exportDir.listFiles()?.forEach{it.delete()}` không điều kiện ngay trước khi tạo file mới), L10 (đã tự verify: CONFIRMED — ép kiểu thô `as MaterialShapeDrawable` tại 4 chỗ không safe-cast), L11 (đã tự verify: CONFIRMED — `importDataLauncher` thiếu set null trong `onDestroy()`), L12

| ID | Tiêu đề | File | Nguồn | Effort |
|---|---|---|---|---|
| FIX-L01 | `HighlightHelper`: `query.first()`/`substring(1, length-1)` có thể throw `StringIndexOutOfBoundsException` nếu query rỗng hoặc đúng 1 ký tự `"` — hiện an toàn vì FTS4 chặn trước, nhưng utility giòn | `ui/note/HighlightHelper.kt:16-69` | claude-bypass (FIX-032) | XS |
| FIX-L02 | Dead navigation destination `nav_graph_notification.xml` trỏ tới class không tồn tại (`ReminderPostponeDateDialog`/`TimeDialog` đã bị thay bằng `MaterialDatePicker` trực tiếp) — Safe Args vẫn generate action, `ClassNotFoundException` nếu dùng nhầm | `res/navigation/nav_graph_notification.xml:18-32` | claude-bypass (FIX-035) | XS |
| FIX-L03 | `VipFrm`: animation one-shot (confetti, celebrate, `postDelayed` 1700ms) không bị huỷ trong `onDestroyView()` — chỉ animator lặp mới được cancel | `ui/vip/VipFrm.kt:150-158, 306-411` | subagent UI-audit (Bug 6), claude-bypass (FIX-036) | XS |
| FIX-L04 | `@Keep` không nhất quán trên các subtype của `HomeDestination` (`Reminders` thiếu) — rủi ro R8 rename vì là `Parcelable` lưu trong `SavedStateHandle`, release bật `minifyEnabled=true` | `ui/navigation/NavigationDestination.kt:27-44` | claude-bypass (FIX-037) | XS |
| FIX-L05 | `DragTouchHelperCallback` capture `moveCheckedToBottom` 1 lần lúc khởi tạo thay vì đọc live từ `EditVM` | `ui/edit/adt/EditAdt.kt:26-33` | claude-bypass (FIX-038) | XS |
| FIX-L06 | `SplashActivity`: `handler.post { onDone() }` dùng Runnable ẩn danh không lưu reference → không gỡ được nếu user thoát app ngay sau khi chọn ngôn ngữ (locale không đổi) | `ui/splash/SplashActivity.kt:78-88` | subagent UI-audit (Bug 8) | XS |
| FIX-L07 | `EditVM.deleteNoteForeverAndExit()`/`exit()`: 2 coroutine độc lập có thể cùng gọi `deleteNoteInternal()`/`removeAlarm()` 2 lần cho cùng note (race, tác động thấp vì gần idempotent) | `ui/edit/EditVM.kt:426-436, 609-614` | subagent UI-audit (Bug 7) | XS-S |
| FIX-L08 | `NoSuchElementException`/NPE khi nhận Share Content không có path segments hoặc `openInputStream()` trả null (`uri.pathSegments.last()`, `!!`) | `ui/main/MainAct.kt:374-380` | agy (FIX-11) — ⚠️ chưa tự verify | XS |
| FIX-L09 | Race condition xoá sạch thư mục export cache (`exportDir.listFiles()?.forEach{it.delete()}`) ngay khi bấm share lần 2, có thể xoá file đang được app khác (Gmail...) đọc dở | `ui/edit/EditFrm.kt:858-860` | agy (FIX-12) — ⚠️ chưa tự verify | XS |
| FIX-L10 | Ép kiểu thô `toolbarLayout.background as MaterialShapeDrawable` khi vào/thoát Action Mode — crash nếu ROM tuỳ biến đổi background AppBar sang `ColorDrawable`/`GradientDrawable` | `ui/note/NoteFrm.kt:623,641`, `ui/labels/LabelFrm.kt:259,275` | agy (FIX-14) — ⚠️ chưa tự verify | XS |
| FIX-L11 | `importDataLauncher` không được set `null` trong `SettingsFrm.onDestroy()` (khác với `exportDataLauncher`/`autoExportLauncher` đã làm đúng) — leak nhỏ | `ui/setting/SettingsFrm.kt:357-361` | agy (FIX-15) — ⚠️ chưa tự verify | XS |
| FIX-L12 | Manifest `<action android:name=".CREATE">` (và `.EDIT`, `.SHOW_REMINDERS`, `.reminder.*`) dùng shorthand dấu chấm — chỉ được Android mở rộng cho `android:name` của COMPONENT, không áp dụng cho `<action>` bên trong `<intent-filter>` ✅ | `AndroidManifest.xml:35-37,45,53-54` | claude-bypass (FIX-006), tự verify | XS |

**FIX-L12 chi tiết:** Đã verify: các hằng số action thật trong code là chuỗi fully-qualified (`MainAct.INTENT_ACTION_CREATE = "com.mckimquyen.notes.CREATE"`...), nhưng Manifest khai báo `<action android:name=".CREATE" />`. Đã kiểm tra thêm: mọi nơi dispatch đều dùng **explicit Intent** (`Intent(context, MainAct::class.java).apply { action = ... }`), nên intent-filter không được dùng để match — bug hiện chưa lộ ra ngoài vì Android giao explicit Intent bất kể nội dung intent-filter. Rủi ro tương lai: bất kỳ implicit Intent nào (Tasker, App Actions, refactor sau này) gửi đúng action thật sẽ không resolve được.

---

## Cần xác minh thêm — ĐÃ XÁC MINH XONG 2026-08-17, kết quả cuối

- **FIX-P01 — `MainVM._deletionFinishedMutex` có thể treo vĩnh viễn** (`ui/main/MainVM.kt:64,106-110,222`) — CONFIRMED cấu trúc: `Mutex(locked=true)` được unlock ở cuối 1 khối `viewModelScope.launch` tuần tự (không có try/finally); nếu `notesRepository.getLastCreatedNote()`/`deleteNote()` throw giữa chừng, `unlock()` không bao giờ chạy → `createNote().withLock{}` treo vĩnh viễn. Effort XS (bọc try/finally). **✅ P1 — user duyệt 2026-08-17.**
- ~~`EditVM` — `items[item.actualPos] = item` có thể `IndexOutOfBoundsException`~~ — **❌ Đã trace toàn bộ đường mutate (xoá đơn `deleteListItemAt`, xoá hàng loạt `deleteCheckedItems`, paste multi-line, thêm item `addChecklistItem`, swap reorder) — tất cả đều renumber `actualPos` đúng. Không tìm ra đường gây lệch. Loại khỏi backlog theo quyết định user 2026-08-17 (agy false positive).**
- **FIX-P02 — `ClassCastException` khả năng thấp trong `moveCheckedItemsToBottom`** (`EditVM.kt:1198-1200`) — dòng `.sortBy { (it as EditItemItem).actualPos }` ép kiểu thô trên `subList`. Đã đọc code: trong điều kiện bình thường subList chỉ chứa `EditItemItem` (các item đặc biệt đã bị lọc trước). Dựa vào 1 invariant chưa chứng minh tuyệt đối. **✅ P2, giữ backlog với ghi chú độ tin cậy thấp — user duyệt 2026-08-17.** Fix an toàn: đổi `as` → `filterIsInstance`.
- ~~`ReminderDlg.registerForActivityResult` gọi ngoài `onCreate()`~~ — **❌ DEBUNKED, xem FIX-M16 phía trên (Medium section).** Đã đọc toàn bộ `onCreateDialog()`: `requestNotificationPermission()` gọi đồng bộ tại dòng 164-166, trước STARTED, đúng chuẩn AndroidX. Không phải bug.

---

## Việc dọn dẹp tài liệu (không phải bug code)

- **`doc/memory_leak.md` đã lỗi thời** — trỏ tới file không còn tồn tại (`sdkadbmob/AdMobManager.kt`, `SplashAct.kt` nay là `SplashActivity.kt`), claim `RApp` có field `appScope` nhưng thực tế SDK ads đã tách thành thư viện ngoài đóng gói `com.roy.sdkadbmob` (không audit được implementation từ repo này nữa). Cần cập nhật doc để không tốn công audit sai chỗ ở các lần sau. (subagent ads/widget-audit, claude-bypass ENH-003)
- **Rotate signing key** — commit `212225a` ("Move keystore + signing secrets out of repo") gợi ý từng có bí mật lộ trong lịch sử git. Không phải bug code, là rủi ro quy trình cần người có quyền Play Console quyết định (có dùng Play App Signing để rotate upload key được không). (claude-bypass IDEA-007)
