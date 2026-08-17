Duoc viet boi Claude CLI (--dangerously-skip-permissions) - audit doc lap 2026-08-17

# Backlog toàn diện — Another Notes (audit độc lập)

Tài liệu này được tạo bằng cách đọc toàn bộ `app/src/main/kotlin` (~130 file), `app/src/release/kotlin`, `app/src/debug`, `AndroidManifest.xml` (main + variant), `app/build.gradle`, `build.gradle` root, `gradle.properties`, `settings.gradle`, và các file `doc/*.md` (đặc biệt `doc/memory_leak.md`, `CLAUDE.md`). Không sửa/xoá bất kỳ file nào khác trong repo. Các backlog cũ trong `doc/task/todo/*.md` không được tham chiếu làm nguồn — đây là một đánh giá độc lập từ đầu.

Quy ước: mỗi item có ID duy nhất, effort XS/S/M/L, priority P0 (khẩn/chặn release) / P1 (nên làm sớm) / P2 (backlog). Nhóm FIX sắp theo mức độ nghiêm trọng giảm dần.

---

## 1. FIX — Lỗi thật trong code hiện tại

### Critical

**FIX-001 — Import lại backup chưa chỉnh sửa sẽ âm thầm xoá reminder của note**
File: `app/src/main/kotlin/com/mckimquyen/notes/model/DefaultJsonManager.kt:259-276` (hàm `mergeNotes`), logic so sánh `compareReminders:278-282`.
Mô tả: `when` branch xử lý reminder chỉ có 3 nhánh (old null/new có, old có/new null, khác nhau → return null). Khi `old.reminder` và `new.reminder` đều non-null và **giống hệt nhau**, không nhánh nào khớp, rơi vào `else -> null` → reminder bị xoá dù không có gì thay đổi.
Kịch bản tái hiện: Tạo note có reminder đang hoạt động → Settings → Export Data → không sửa gì → Settings → Import Data chọn đúng file vừa export. Vì `addedDate`/`lastModifiedDate` không đổi nên đi vào nhánh merge, `mergeNotes` xoá reminder. Hệ quả kèm theo: `reminderAlarmManager.updateAllAlarms()` chỉ quét note còn `reminder != null` nên alarm cũ trong `AlarmManager` **không bị huỷ** — tạo alarm mồ côi vẫn nổ trong khi UI không còn hiển thị reminder.
Mức độ: Critical. Effort: S.

**FIX-002 — `debugCheck`/`debugRequire` throw crash trong bản release do source-set debug/release bị nối sai**
File: `app/src/main/kotlin/com/mckimquyen/debug/notes/DebugExtensions.kt` (không có khai báo `package` → default package) chứa bản throw thật; bản no-op đúng nằm ở `app/src/release/kotlin/com/maltaisn/notes/DebugExtensions.kt` (package `com.maltaisn.notes`) nhưng **không ai import** vì các call site dùng `import debugCheck` trần (resolve về default package, tức bản throw, trong MỌI variant kể cả release).
Call site bị ảnh hưởng: `Note.kt:115,119,122,126,154` (`debugRequire` kiểm tra invariant ngày tháng/pinned/status), `SortDialog.kt:99`, `ReminderDlg.kt:180`, `LabelEditDlg.kt:47` (check dialog show 2 lần), `SearchVM.kt:77` (`debugCheck(false)` unconditional khi regex chuẩn hoá query lỗi).
Kịch bản tái hiện: một dòng DB cũ/hỏng có `addedDate > lastModifiedDate` (do migration lỗi, lệch giờ đồng hồ, hoặc import backup) khiến `Note` constructor throw `IllegalArgumentException` ngay khi load — crash bản release thật, không phải debug.
Mức độ: Critical. Effort: S (sửa: thêm `package com.mckimquyen.debug.notes` đúng slot, hoặc chuyển file throw vào `src/debug/kotlin` thật và để `src/main` trống).

**FIX-003 — Recurrence "ngày cuối tháng" bị hỏng do dùng sai field Calendar**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/reminder/ReminderVM.kt:165-171` (`updateRecurrenceForDate`).
```kotlin
if (recurrence.byMonthDay == -1 &&
    calendar[Calendar.DATE] != calendar.getActualMaximum(Calendar.MONTH)) {
    recurrence = Recurrence(recurrence) { dayInMonth = 0 }
}
```
`getActualMaximum(Calendar.MONTH)` luôn trả `11` (tháng 12, 0-index) — không phải "ngày cuối cùng của tháng". Lẽ ra phải là `Calendar.DAY_OF_MONTH`.
Kịch bản tái hiện: Chọn ngày bắt đầu 31/1/2026, đặt recurrence "Hàng tháng, vào ngày cuối tháng". Vì `calendar[Calendar.DATE]=31 != 11` luôn đúng cho ~97% ngày trong tháng, cờ `byMonthDay == -1` bị xoá âm thầm (`dayInMonth = 0`), làm recurrence suy biến thành "cùng ngày-trong-tháng với ngày bắt đầu" thay vì luôn là ngày cuối tháng (31/1 → 28/2 → 31/3 …). Đúng với kịch bản "ngày cuối tháng + recurrence hàng tháng" được nêu trong yêu cầu audit.
Mức độ: Critical. Effort: XS.

**FIX-004 — Export/import xoá mất `color`, `mood`, và đặc biệt là `isLocked` — note bị khoá trở thành mở khoá sau khi restore**
File: `app/src/main/kotlin/com/mckimquyen/notes/model/DefaultJsonManager.kt:297-321` (`NoteSurrogate`), dùng ở export (`:56-59`) và import (`:212-223`, `:275`).
Mô tả: `NoteSurrogate` không serialize `color`, `mood`, `isLocked`. Khi tạo lại `Note(...)` từ import, 3 field này luôn về giá trị mặc định (`0`, `0`, `false`).
Kịch bản tái hiện: Tô màu note, gắn mood, khoá note (bảo vệ sinh trắc học) → export → cài lại app hoặc import trên máy khác (hoặc import lại trên cùng máy với note có ngày khác, đi vào nhánh "insert như note mới" ở dòng 250) → màu/mood mất, và quan trọng nhất: **note không còn bị khoá**, lộ nội dung mà user đã chủ động hạn chế truy cập.
Mức độ: Critical (phần `isLocked` là hồi quy bảo mật thật sự). Effort: S.

### High

**FIX-005 — `BuildTypeModule` luôn resolve về bản Debug trong mọi variant (kể cả release), chèn 3 note rác Lorem-Ipsum vào DB thật**
File: `app/src/main/kotlin/com/mckimquyen/notes/di/AppModule.kt:6,23` import tường minh `com.mckimquyen.debug.notes.di.BuildTypeModule` (sống trong `src/main`, luôn compile). Bản `app/src/release/kotlin/com/maltaisn/notes/di/BuildTypeModule.kt` có `package` là `com.mckimquyen.notes.di` (trùng FQN với chính `AppModule`) nhưng không ai import — dead code.
Hệ quả: `HomeVM.doExtraAction()` luôn gọi `DebugBuildTypeBehavior.doExtraAction()` — chèn 3 note rác vào DB thật ở mọi build. Hiện bị chặn gián tiếp bởi `HomeFrm.kt:113` ẩn menu item khi `BuildConfig.ENABLE_DEBUG_FEATURES=false` (release), nhưng lớp phòng vệ thứ 2 (đúng class theo variant) đã bị vô hiệu hoá hoàn toàn — chỉ còn 1 lớp bảo vệ duy nhất, rất mong manh trước thay đổi trong tương lai.
Mức độ: High. Effort: S.

**FIX-006 — Manifest `<action>` trong intent-filter dùng shorthand dấu chấm không được Android mở rộng — action string không khớp hằng số trong code**
File: `app/src/main/AndroidManifest.xml:34-38` (MainAct), `:45` (NotificationAct postpone), `:51-59` (AlarmReceiver: `.reminder.ALARM`, `.reminder.MARK_DONE`).
Mô tả: shorthand `android:name="."` chỉ được Android manifest-merger mở rộng cho `android:name` của component (`<activity>/<receiver>/...`), KHÔNG áp dụng cho giá trị `<action>` bên trong `<intent-filter>`. Đã verify qua merged manifest build output — action thực sự đăng ký là chuỗi literal `.CREATE`, `.reminder.ALARM`... không khớp hằng số thật (`"com.mckimquyen.notes.CREATE"`, `"com.mckimquyen.notes.reminder.ALARM"`...) dùng trong `MainAct.kt:398-400`, `AlarmReceiver.kt:152`, `shortcuts.xml`.
Hiện chưa lộ vì mọi dispatch trong code đều dùng explicit Intent (bypass intent-filter matching). Rủi ro: bất kỳ implicit intent nào (Tasker, App Actions/Assistant shortcut, PendingIntent implicit, refactor tương lai) gửi đúng action thật sẽ KHÔNG resolve được → no-op hoặc `ActivityNotFoundException`.
Mức độ: High. Effort: XS (sửa thành fully-qualified action string).

**FIX-007 — `PrefsManager` enum preference delegate crash khi giá trị lưu trữ không khớp enum hiện tại**
File: `app/src/main/kotlin/com/mckimquyen/notes/model/PrefsManager.kt:120-125` (`enumPreference`) dùng `enumValues<T>().first { it.value == value }` — throw `NoSuchElementException` nếu không tìm thấy. Áp dụng cho `theme`, `listLayoutMode`, `swipeActionLeft/Right`, `shownDateField`, `sortField`, `sortDirection`.
Kịch bản tái hiện: release tương lai đổi tên/xoá 1 hằng enum, hoặc prefs bị chỉnh tay/hỏng, hoặc import từ bản app mới hơn còn giá trị enum mới → app crash ngay khi chạm màn hình Settings/Sort/note-list/swipe-action ở lần mở tiếp theo — gần như brick app cho tới khi user xoá data.
Mức độ: High (migration hazard tiềm ẩn, dễ khoá toàn app). Effort: XS.

**FIX-008 — `InputStream` bị leak ở mọi lần import dữ liệu**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/setting/SettingsVM.kt:123-141` (`importData`) — `input.bufferedReader().readText()` không bao giờ `.use{}`/`close()`, kể cả nhánh lỗi. Caller `SettingsFrm.kt:129-136` mở stream qua `contentResolver.openInputStream(uri)` và không tự đóng.
Kịch bản tái hiện: dùng Settings → Import Data nhiều lần trong 1 session → leak file descriptor tích luỹ, có thể dẫn tới `EMFILE`.
Mức độ: High. Effort: XS.

**FIX-009 — Export bị leak `OutputStream` nếu serialization throw trước khi vào block `use{}`**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/setting/SettingsVM.kt:72-91` (`exportData`) và `:93-116` (`setupAutoExport`) — `output` chỉ được đóng trong try-block thứ 2; nếu `jsonManager.exportJsonData()` throw (lỗi serialize/DB) thì hàm `return@launch` trước khi chạm `output.use{}`.
Kịch bản tái hiện: dữ liệu note bất thường khiến kotlinx.serialization throw giữa lúc export (thủ công hoặc auto-export hàng ngày) → stream leak lặp lại theo thời gian.
Mức độ: High. Effort: XS (bọc cả hàm trong `output.use{}` hoặc `finally`).

**FIX-010 — Import không atomic — crash giữa chừng làm mất toàn bộ label-note association**
File: `app/src/main/kotlin/com/mckimquyen/notes/model/DefaultJsonManager.kt:103-168, 170-202, 204-257`. Không có `@Transaction` bọc toàn bộ import; `labelRefs` được gom vào list local và chỉ flush 1 lần duy nhất ở cuối (`labelsDao.insertRefs(labelRefs)`, dòng 256).
Kịch bản tái hiện: import backup lớn (hàng trăm note) trên máy yếu/thiếu RAM, process bị kill giữa vòng lặp → note/label đã insert thì tồn tại vĩnh viễn nhưng TOÀN BỘ liên kết note-label bị mất vì `insertRefs` chưa chạy. Import lại lần 2 không tự sửa được vì note giờ khớp ngày → đi nhánh merge, và `labels` union với refs cũ vốn rỗng.
Mức độ: High (thực tế trên máy yếu/note nhiều). Effort: M.

**FIX-011 — `MainAct.onNewIntent` không gọi lại `handleIntent()` — chạm thông báo reminder khi app đang mở (foreground) bị no-op**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/main/MainAct.kt:276-279` (`onNewIntent` chỉ set `this.intent = intent`), `handleIntent()` chỉ được gọi từ `onResume():308-311`.
Mô tả: `MainAct` là `launchMode="singleTask"`. Theo lifecycle chuẩn Android, khi activity đang resumed nhận intent mới thì hệ thống gọi `onNewIntent()` mà KHÔNG kèm `onResume()` (không có chu kỳ stop/restart).
Kịch bản tái hiện: App đang mở ở bất kỳ màn hình nào → reminder nổ, heads-up notification (`PRIORITY_MAX`) hiện đè lên → user chạm thẳng vào banner mà không rời app trước → không có gì xảy ra, note không mở. Cùng lỗi áp dụng cho shortcut `SHOW_REMINDERS`/`CREATE` khi app đang hiển thị (split-screen/multi-window).
Mức độ: High. Effort: XS (gọi `handleIntent()` trong `onNewIntent()`).

**FIX-012 — `BaseAct.onResume()` kích hoạt popup Play In-App Review từ MỌI activity, kể cả `NotificationAct` trong suốt và `SplashActivity` đang load ad**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/main/BaseAct.kt:24-30,56-87` — `rateAppInApp()` gọi unconditional trong `onResume()`, chỉ chặn bởi cooldown 7 ngày. `BaseAct` là cha của cả `SplashActivity`, `MainAct`, `NotificationAct`.
Kịch bản tái hiện: sau ≥7 ngày kể từ lần review cuối, chạm "Postpone" trên thông báo reminder → `NotificationAct` (theme trong suốt, chỉ để hiện date/time picker) bất ngờ hiện full Play Store review sheet đè lên. Tương tự có thể race với UMP consent form ở SplashActivity.
Mức độ: High (UX gây khó chịu, dễ tái hiện thật). Effort: XS (chuyển `rateAppInApp()` xuống riêng `MainAct.onResume()`).

**FIX-013 — `VipFrm`: callback reward-ad chạm `binding` sau khi `onDestroyView()` đã null hoá → crash NPE**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/vip/VipFrm.kt:114-146, 150-158, 288-301, 306-316`. `onDestroyView()` null hoá `_binding` và `AdManager.rewardedListener`, nhưng lambda callback truyền cho `AdManager.showRewarded(...)` là closure riêng, không bị clear. Guard hiện tại chỉ check `context ?: return` (Fragment còn attached), không check `_binding == null`.
Kịch bản tái hiện: bấm "Watch Ad" → back ra khỏi `VipFrm` (pop back stack) đúng lúc ad trả thưởng/đóng → `onDestroyView()` chạy trước khi callback trả kết quả → `grantVip3Days()`→`onActivationSuccess()` chạm `binding.statusDot` v.v. → `_binding!!` NPE crash.
Mức độ: High (crash path thật, dễ tái hiện qua back-navigation timing bình thường). Effort: S (thêm guard `!isAdded || _binding == null`).

**FIX-014 — Không dùng exact alarm / Doze-safe scheduling cho reminder — thông báo có thể trễ hàng chục phút tới cả giờ**
File: `app/src/main/kotlin/com/mckimquyen/notes/receiver/ReceiverAlarmCallback.kt:23-42` dùng `alarmManager.set(AlarmManager.RTC_WAKEUP, time, alarmIntent)` — code `setExactAndAllowWhileIdle` bị comment (`//TODO roy93~`), quyền `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` trong manifest cũng bị comment out.
Kịch bản tái hiện: đặt reminder 5 phút sau, khoá màn hình để máy vào Doze → thông báo tới trễ đáng kể hoặc chỉ tới ở lần maintenance-window tiếp theo — nghiêm trọng với app nhắc việc.
Mức độ: High. Effort: M (cần UI xin quyền exact alarm + fallback `setAndAllowWhileIdle`).

**FIX-015 — Note đầu tiên bị double interstitial ad khi Fragment view được recreate sau khi xoá note**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/home/HomeFrm.kt:216-230`. `lastAdEvent` là biến local reset về `null` mỗi lần `setupViewModelObservers()` chạy (mỗi `onViewCreated`), trong khi `viewModel.statusChangeEvent` là sticky `LiveData<Event<...>>` — observer mới luôn nhận lại giá trị cuối cùng ngay khi đăng ký.
Kịch bản tái hiện: xoá 1 note (ad hiện) → xoay máy hoặc quay lại Home khiến view bị recreate → cùng `Event` bị redeliver, guard bị reset → `AdManager.showInterstitial()` nổ lại ad cho 1 hành động xoá mà user đã thấy ad rồi.
Mức độ: High (rủi ro chính sách ad network + UX khó chịu). Effort: S.

### Medium

**FIX-016 — `LabelEditVM.updateError()` là race condition — có thể để lọt tên label trùng**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/labels/LabelEditVM.kt:72-75,95-110`. Mỗi keystroke launch coroutine mới không huỷ coroutine cũ, không debounce; DB query bất đồng bộ có thể hoàn thành sai thứ tự.
Kịch bản tái hiện: gõ nhanh tên label ("MyLabel" → xoá → "Work", trong khi "Work" đã tồn tại) lúc máy/DB đang bận → kết quả validate cũ hoàn thành sau, ghi đè `_labelError.value` thành `NONE` sai → OK button bật cho tên trùng, insert/update label trùng tên (DB không có constraint UNIQUE, chỉ có index).
Mức độ: Medium. Effort: S (huỷ Job cũ trước khi launch mới, hoặc dùng `debounce`+`collectLatest`).

**FIX-017 — `LabelVM.renamingLabel` bị "kẹt" true sau khi huỷ rename, âm thầm xoá selection không liên quan**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/labels/LabelVM.kt:128-144,176-183`. Flag chỉ được set `true` lúc bắt đầu rename, chỉ được reset trong khối `collect` của Flow (chỉ emit khi bảng `labels`/`label_refs` thật sự đổi).
Kịch bản tái hiện: chọn label A → tap "Rename" → cancel dialog (không lưu) → không đụng vào selection, tạo label B mới hoặc xoá label C khác → Flow emit lại vì bảng đổi → `renamingLabel` vẫn `true` từ bước trước → selection của A bị xoá bất ngờ, action mode tự thoát dù user chưa từng bỏ chọn.
Mức độ: Medium. Effort: S.

**FIX-018 — `SortDialog` mất lựa chọn sort hiện tại khi fragment bị recreate, có thể âm thầm ghi đè sort thật của user**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/sort/SortDialog.kt:80-82` (`if (savedInstanceState == null) viewModel.start()`), kết hợp `SortViewModel` gửi field/direction hiện tại chỉ dưới dạng one-shot `Event` (đã consume thì không redeliver).
Kịch bản tái hiện: dialog bị recreate (đổi cỡ chữ hệ thống, split-screen resize...) → không radio nào được check → user tap Apply → fallback về `MODIFIED_DATE`/`DESCENDING`, âm thầm ghi đè sort thật (vd Title/Ascending) mà không có cảnh báo gì.
Mức độ: Medium. Effort: S.

**FIX-019 — `SharedPreferences` default value giữa code Kotlin và XML lệch nhau**
File: `app/src/main/kotlin/com/mckimquyen/notes/model/PrefsManager.kt:31,37,56-75` vs `app/src/main/res/xml/prefs.xml:44,49` và `prefs_preview_lines.xml:9,17,29,37`. Ví dụ `strikethrough_checked`: Kotlin fallback `false`, XML `defaultValue="true"`; `preview_labels`: Kotlin `0`, XML `2`.
Hiện bị che vì `RApp.onCreate()` luôn gọi `prefs.setDefaults(this)` trước khi UI đọc prefs — nhưng đây là bẫy tiềm ẩn cho test (test dựng `PrefsManager` trực tiếp trên `SharedPreferences` in-memory mới sẽ nhận default sai) và cho tương lai nếu key bị xoá/resource id gõ sai.
Mức độ: Medium. Effort: S (đồng bộ 2 nguồn default, thêm test regression).

**FIX-020 — Auto-export: quyền URI đã persist bị leak khi `openOutputStream()` lỗi sau `takePersistableUriPermission()`**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/setting/SettingsFrm.kt:104-123`. Nhánh lỗi chỉ set `autoExportPref.isChecked = false` (UI) chứ không gọi `viewModel.disableAutoExport()` — quyền ghi persisted bị treo vĩnh viễn trong `ContentResolver.getPersistedUriPermissions()`.
Mức độ: Medium. Effort: XS.

**FIX-021 — Import/export password material không được xoá khỏi bộ nhớ sau khi dùng**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/setting/SettingsVM.kt:211-218` (`deriveKey` không gọi `keySpec.clearPassword()`); `ExportPasswordVM.kt:35-45`, `ImportPasswordVM.kt:25-29` giữ password dạng plaintext trong `SavedStateHandle` không giới hạn thời gian sống.
Mức độ: Medium (vệ sinh mật mã, không phải lỗ hổng khai thác từ xa). Effort: S.

**FIX-022 — `NoteCountWidget` tái tạo đúng anti-pattern "untracked CoroutineScope" mà `doc/memory_leak.md` từng khẳng định đã fix ở AlarmReceiver**
File: `app/src/main/kotlin/com/mckimquyen/notes/widget/NoteCountWidget.kt:64-70` — `CoroutineScope(Dispatchers.IO).launch{...}` mới toanh, không `goAsync()`, không huỷ, chạy trong `onUpdate()` (là `BroadcastReceiver.onReceive()`), lặp lại mỗi 30 phút (`android:updatePeriodMillis="1800000"`) kể cả khi app đã force-stop.
Kịch bản tái hiện: đặt widget đếm note, force-stop app, đợi update định kỳ trong lúc máy idle — process có thể bị hệ thống thu hồi trước khi coroutine hoàn tất → widget hiện số cũ/trắng liên tục.
Mức độ: Medium-High. Effort: S (dùng `goAsync()` như `AlarmReceiver`, hoặc tracked scope + cancel).

**FIX-023 — Reboot trên thiết bị chỉ phát `QUICKBOOT_POWERON` (HTC/OEM fast-boot) không reschedule alarm**
File: `AndroidManifest.xml:55-58` đăng ký 3 action boot nhưng `AlarmReceiver.kt:50-54` chỉ xử lý `ACTION_BOOT_COMPLETED` trong `when`, không có nhánh cho `QUICKBOOT_POWERON`.
Kịch bản tái hiện: reboot thiết bị chỉ gửi `QUICKBOOT_POWERON` → alarm bị hệ điều hành xoá sạch sau reboot và không được rearm → reminder ngừng nổ âm thầm cho tới khi user tự mở app (gọi `updateAllAlarms()` từ `MainVM`).
Mức độ: Medium. Effort: XS.

**FIX-024 — Undo-xoá của reminder định kỳ đã quá hạn có thể nổ thông báo giả/cũ**
File: `app/src/main/kotlin/com/mckimquyen/notes/model/ReminderAlarmManager.kt:26-33` (`setNoteReminderAlarm`) dùng thẳng `reminder.next` (giá trị trước khi xoá) mà không tính lại các lần lặp đã quá hạn, khác với logic đúng ở `setNextNoteReminderAlarmInternal:42-71`.
Kịch bản tái hiện: note có reminder định kỳ mà `next` đã ở quá khứ (app bị background lâu) → user xoá note → tap Undo trên Snackbar → `alarmManager.set()` với thời điểm trong quá khứ → nổ thông báo gần như ngay lập tức cho lần lặp đã cũ.
Mức độ: Medium. Effort: S.

**FIX-025 — Không xử lý `ACTION_TIMEZONE_CHANGED`/`ACTION_TIME_CHANGED` — reminder định kỳ nổ sai giờ địa phương sau khi đổi múi giờ**
File: không có receiver nào lắng nghe 2 action này (grep xác nhận). Alarm đã lên lịch dùng epoch tuyệt đối nên vẫn nổ đúng thời điểm tuyệt đối, nhưng với reminder **định kỳ** thì lần kế tiếp đã tính theo giờ địa phương cũ.
Kịch bản tái hiện: tạo reminder hàng ngày 8:00 sáng ở múi giờ A, bay sang múi giờ B (lệch 3 tiếng) trước lần nổ kế tiếp → thông báo nổ lúc 11:00 sáng giờ địa phương mới, không phải 8:00.
Mức độ: Medium. Effort: M.

**FIX-026 — Checklist: mất trạng thái "đã check" khi paste nhiều dòng làm tách item, tuỳ thuộc setting không liên quan**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/edit/EditVM.kt:1034-1064`, dòng 1050: `checked = item.checked && moveCheckedToBottom`.
Kịch bản tái hiện: tắt "Move checked items to bottom", check 1 item checklist, paste văn bản nhiều dòng vào (tách thành nhiều `EditItemItem`) → tất cả item mới đều unchecked bất kể trạng thái gốc — chỉ khi bật setting kia mới giữ đúng trạng thái. Không nhất quán, mất dữ liệu bất ngờ.
Mức độ: Medium. Effort: XS.

**FIX-027 — `uncheckAllItems()` phá vỡ identity DiffUtil, gây nhấp nháy/mất focus (đã có FIXME trong code nhưng chưa fix)**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/edit/EditVM.kt:616-624` (`// FIXME breaks animation`) + `EditDiffCallback.kt:7-18` (`areItemsTheSame` dùng `===`). `listItems[i] = item.copy(checked = false)` tạo object mới → DiffUtil coi là remove+insert thay vì update.
Kịch bản tái hiện: checklist có nhiều item đã check, bấm "Uncheck all" → toàn bộ row nhấp nháy, EditText đang focus (nếu có) mất trạng thái.
Mức độ: Medium. Effort: M (refactor sang content-equality diffing).

**FIX-028 — Checklist drag-handle để lại khoảng trống ma sau khi check tương tác (INVISIBLE thay vì GONE)**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/edit/adt/EditListVH.kt:179` (set `isInvisible`) vs `:261` (bind dùng `isVisible = !checked`, tức GONE). Vì `EditDiffCallback` so bằng `===`, item được reorder không bị rebind lại từ đầu nên giữ trạng thái INVISIBLE thay vì GONE.
Mức độ: Medium. Effort: S.

**FIX-029 — `MainAct.onStart()` đăng ký lại `LiveData` observer mỗi lần start/stop mà không bao giờ gỡ**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/main/MainAct.kt:295-306`. `observeEvent()` tạo `Observer` mới mỗi lần gọi; `onStart()` chạy lại mỗi chu kỳ stop→start trong cùng 1 activity instance → observer tích luỹ không giới hạn cho tới khi activity destroy. Hiện được "cứu" nhờ cơ chế `Event.hasBeenHandled`, nhưng đây là anti-pattern LiveData kinh điển, dễ bị copy-paste sai trong tương lai.
Mức độ: Medium (hiện đúng nhờ may mắn, không nhờ thiết kế). Effort: XS (chuyển đăng ký vào `onCreate()`).

**FIX-030 — Thông báo reminder mở note nhấn nhiều lần chồng nhiều `fragment_edit` trên back stack**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/main/MainAct.kt:247-251` — `navigateSafe(..., allowSameDest = true)` cố ý bỏ qua guard "không navigate tới cùng destination 2 lần" mà mọi nơi khác trong app đều dùng.
Kịch bản tái hiện: có 2 thông báo reminder đang chờ, tap thông báo A rồi tap thông báo B từ khay thông báo → back stack thành `home → edit(A) → edit(B)`, back từ B về lại A chứ không về home. Double-tap 1 thông báo → 2 màn edit trùng lặp y hệt trên stack.
Mức độ: Medium. Effort: S.

**FIX-031 — Inconsistent `NonCancellable` giữa các method mutate DB trong cùng 1 repository**
File: `DefaultNotesRepository.kt:81-88` (`deleteOldNotesInTrash`), `:90-94` (`clearAllData`), `DefaultLabelsRepository.kt:25-31` (`deleteLabel`, `deleteLabels`) — không bọc `NonCancellable` trong khi các method khác cùng class (insert/update/delete note thường, emptyTrash) đều có, trái với comment thiết kế của chính class đó.
Hệ quả cụ thể: nếu coroutine bị huỷ đúng lúc, lệnh gọi refresh widget theo sau (`NoteCountWidget`/`RecentNotesWidget.updateAllWidgets`) có thể bị bỏ qua dù DB đã ghi xong → widget hiện dữ liệu cũ sau khi xoá.
Mức độ: Medium/Low. Effort: XS.

### Low

**FIX-032 — `HighlightHelper` có thao tác string không được guard, có thể throw**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/note/HighlightHelper.kt:16-37,44-69`. `query.first()` throw nếu query rỗng; `substring(1, length-1)` throw `StringIndexOutOfBoundsException` nếu query đúng 1 ký tự `"`; vòng `while` ở dòng 56 có thể chạy vượt `text.length`.
Hiện an toàn vì FTS4 `MATCH ''`/`MATCH '""'` trả về 0 dòng nên đường gọi tới không kích hoạt được từ UI hiện tại, nhưng utility bản thân rất giòn.
Mức độ: Low. Effort: XS.

**FIX-033 — `RecentNotesWidget` hard-code hiện 5 item, trái với doc comment và default 10 của DAO**
File: `RecentNotesWidgetService.kt:37` (`getRecentNotes(5)`) vs doc comment "up to 10" ở `RecentNotesWidget.kt:16` và default `NotesDao.kt:177`.
Mức độ: Low. Effort: XS.

**FIX-034 — Widget hiện tiêu đề note đã khoá dưới dạng rõ chữ, chỉ ẩn phần content**
File: `RecentNotesWidgetService.kt:56-66` — `isLocked` chỉ ẩn `content` (`"Locked"`), tiêu đề luôn hiện. Bề mặt lộ lớn hơn trong-app vì widget hiện trên màn hình chính không cần mở khoá máy hay xác thực app.
Mức độ: Medium (privacy) nhưng tách riêng do cần quyết định sản phẩm. Effort: XS (ẩn cả title) tới S (thêm setting).

**FIX-035 — Dead navigation destination trong `nav_graph_notification.xml` trỏ tới class không tồn tại**
File: `app/src/main/res/navigation/nav_graph_notification.xml:18-32` — `ReminderPostponeDateDialog`/`ReminderPostponeTimeDialog` không tồn tại trong codebase (đã bị thay bằng `MaterialDatePicker`/`MaterialTimePicker` trực tiếp trong `NotificationAct.kt:60-98`). Navigation resolve class bằng reflection lúc runtime nên chưa vỡ build, nhưng Safe Args vẫn generate action gọi tới sẽ crash `ClassNotFoundException` nếu ai đó dùng nhầm.
Mức độ: Low. Effort: XS (xoá node orphan).

**FIX-036 — `VipFrm`: animation one-shot (confetti, celebrate) không bị huỷ trong `onDestroyView`**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/vip/VipFrm.kt:150-158` chỉ huỷ animator lặp (`animators` list), không huỷ `playEntranceAnimation`, `celebrateActivation`, `launchConfetti` (kèm `postDelayed` 1700ms không bị remove).
Kịch bản tái hiện: kích hoạt VIP rồi back ngay trong ~1.2-1.7s (giữa lúc confetti/celebrate đang chạy) → animation/Handler tiếp tục chạy trên view đã detach thêm tới ~1.7s.
Mức độ: Low. Effort: XS.

**FIX-037 — Inconsistent `@Keep` trên các subtype của `HomeDestination` — rủi ro R8 rename**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/navigation/NavigationDestination.kt:27-44` — `Status`, `Labels` có `@Keep`, `Reminders` (object, dòng 42-43) thì không. `HomeDestination` là `Parcelable` lưu trong `SavedStateHandle`; release bật `minifyEnabled=true`.
Mức độ: Low. Effort: XS.

**FIX-038 — Stale preference snapshot trong `DragTouchHelperCallback`**
File: `app/src/main/kotlin/com/mckimquyen/notes/ui/edit/adt/EditAdt.kt:26-33` — capture `callback.moveCheckedToBottom` 1 lần lúc khởi tạo thay vì đọc live như `EditVM.moveCheckedToBottom` (computed property). Rủi ro thấp do view hiện không sống sót qua thay đổi setting mà không bị recreate, nhưng là code smell.
Mức độ: Low. Effort: XS.

---

## 2. ENHANCE — Cải tiến tính năng đã có

**ENH-001 — Gộp source-set debug/release thành 1 epic dọn dẹp**
Mô tả: FIX-002/FIX-005/H2 (allOpen annotation FQN sai ở `app/build.gradle:229-231`, trỏ `com.mckimquyen.notes.OpenClass` trong khi annotation thật là `com.mckimquyen.debug.notes.OpenClass`) cùng chung 1 gốc — việc migrate rebrand `com.maltaisn.notes` → `com.mckimquyen.notes` chưa hoàn tất đúng cấu trúc source-set Android chuẩn. Gộp thành 1 epic sửa 1 lần thay vì 3 ticket rời rạc. Files: `di/AppModule.kt`, `di/BuildTypeModule.kt` (cả 2 bản), `DebugExtensions.kt` (cả 2 bản), `app/build.gradle`. Effort: M. Priority: P0 (vì kéo theo FIX-002/FIX-005 Critical/High).

**ENH-002 — Dọn `gradle.properties`: ~24 version property không dùng, một số mâu thuẫn với version hard-code trong `app/build.gradle`**
Ví dụ `recyclerViewVersion=1.3.1` trong properties nhưng `app/build.gradle` hard-code `1.4.0`; tương tự với `materialVersion`, `daggerVersion`, `roomVersion`. Rủi ro: người sau tưởng "bump version" qua sửa properties nhưng không có tác dụng gì. Effort: S. Priority: P2.

**ENH-003 — Cập nhật `doc/memory_leak.md` cho khớp code hiện tại**
Doc tham chiếu file không còn tồn tại (`sdkadbmob/AdMobManager.kt`, `SplashAct.kt` — nay là `SplashActivity.kt`), claim RApp có `appScope` field nhưng thực tế không có (ad SDK giờ là thư viện ngoài `com.roy.sdkadbmob`, đóng gói closed-source, không audit được từ repo này). Đồng thời note rõ ràng phần audit ad SDK không còn verify được từ source. Effort: S. Priority: P1.

**ENH-004 — Tích hợp thật LeakCanary hoặc bỏ comment "done" gây hiểu lầm**
File: `RApp.kt:28` ghi `//leakcanary` trong khối TODO "done" nhưng không có dependency LeakCanary nào trong gradle. Effort: XS. Priority: P2.

**ENH-005 — Thêm index cho bảng `notes` theo (status, pinned, modified_date)**
`NotesDao.getByStatus`/`search` filter/sort đúng các cột này trên mọi lần render list/search nhưng schema hiện không có index composite nào ngoài PK. Effort: S. Priority: P1.

**ENH-006 — Composite index `(noteId, timestamp)` cho `note_history`**
`NoteHistoryDao.getHistoryForNote`/`pruneHistory` filter theo `noteId` và order/limit theo `timestamp`, hiện chỉ có index đơn `noteId`. Effort: XS. Priority: P2.

**ENH-007 — Thêm Paging 3 cho danh sách note**
Hiện `getByStatus`/`getByLabel`/`search`/`getAllWithReminder` trả `Flow<List<...>>` không phân trang — ổn với bộ sưu tập cá nhân nhỏ nhưng user có hàng nghìn note sẽ load hết vào bộ nhớ mỗi lần re-render. Effort: L. Priority: P2.

**ENH-008 — Batch insert khi import thay vì N round-trip DAO riêng lẻ**
`importNotes`/`importLabels` gọi `insert`/`update` từng cái một dù `insertAll`/`updateAll` đã tồn tại sẵn trong `NotesDao`. Batch vừa nhanh hơn vừa là 1 phần cách sửa FIX-010 (atomicity). Effort: S. Priority: P1.

**ENH-009 — Export thêm `note_history` vào backup JSON**
Hiện `exportJsonData()` không export version history — mất hoàn toàn khi cài lại app dù dữ liệu vẫn còn trong Room lúc export. Effort: S. Priority: P2.

**ENH-010 — Xin quyền `SCHEDULE_EXACT_ALARM` đúng cách + toggle trong Settings**
Bổ sung UI cho phép user chọn đánh đổi pin vs. đúng giờ, fallback `setAndAllowWhileIdle` khi user từ chối — giải quyết trọn vẹn FIX-014. Effort: M. Priority: P1.

**ENH-011 — Widget: cấu hình được bộ lọc note-count, số lượng item Recent Notes, ẩn note khoá**
Không widget nào trong 4 loại (`QuickNoteWidget`, `NoteCountWidget`, `QuickListWidget`, `RecentNotesWidget`) khai báo `android:configure` — không có configuration Activity chuẩn AppWidget. Effort: M. Priority: P2.

**ENH-012 — Snooze nhanh ngay trên notification (không cần mở app)**
Hiện chỉ có "Mark as done" và "Postpone" (mở `NotificationAct`). Thêm action +10 phút/+1 giờ/ngày mai trực tiếp trên `NotificationCompat.Action`. Effort: S. Priority: P1.

**ENH-013 — Widget theo dark mode giống list trong app**
`RecentNotesRemoteViewsFactory` luôn render `note.color` full opacity, trong khi list trong app giảm alpha ở dark mode (`NoteListVH.kt:106-113`). Effort: XS. Priority: P2.

**ENH-014 — Debounce/coalesce refresh widget khi có thao tác hàng loạt**
Mỗi lệnh CRUD repository gọi refresh widget riêng — hiện ổn vì bulk-op gọi 1 lần, nhưng nên có tiện ích debounce chung để phòng ngừa khi thêm code loop-per-item trong tương lai. Effort: S. Priority: P2.

**ENH-015 — Debounce/throttle `updateLiveStats()` và autolink rescan cho note lớn**
File: `EditFrm.kt:178-186` → `EditVM.updateLiveStats:737-783`, `EditEditText.kt:38-58` — mỗi keystroke chạy full `toString()`+regex split+milestone loop+full-text `LinkifyCompat.addLinks` trên toàn bộ note, note có thể tới 100.000 ký tự (`EditVM.CHAR_LIMIT`). Có thể giật lag rõ rệt trên máy tầm trung/thấp. Effort: M. Priority: P1.

**ENH-016 — Refactor `EditListItem` sang content-equality diffing thay vì identity (`===`)**
Đây là gốc rễ chung của FIX-026/027/028 — sửa 1 lần ở tầng diffing thay vì vá từng triệu chứng riêng lẻ. Effort: M. Priority: P1.

**ENH-017 — Đồng bộ logic highlight tìm kiếm với tokenizer FTS thật**
`HighlightHelper.findHighlightsInString` search substring case-insensitive đơn giản, không khớp hành vi stemming/prefix của `notes_fts` — note có thể xuất hiện trong kết quả tìm kiếm mà không có phần nào được highlight, gây mất niềm tin vào kết quả. Effort: M. Priority: P2.

**ENH-018 — PrefsManager: tránh copy toàn bộ `SharedPreferences.getAll()` mỗi lần đọc 1 property**
File: `PrefsManager.kt:104-105,122-123` — mỗi lần đọc 1 pref lại copy toàn bộ map. Nên dùng trực tiếp `getBoolean/getInt/getString`. Effort: S. Priority: P2.

**ENH-019 — Thêm lựa chọn sort theo label/ngày reminder, ghi nhớ sort riêng theo từng view**
Hiện chỉ 3 field (Added/Modified/Title) × 2 hướng, không nhớ sort riêng cho từng label/trạng thái. Effort: M. Priority: P2.

**ENH-020 — Cho phép tuỳ chỉnh preset nhắc nhanh (quick-pick chip) trong `ReminderDlg`**
Hiện hard-code "1 giờ nữa / mai 9h / thứ 2 tuần sau 9h". Effort: S. Priority: P2.

**ENH-021 — Dedup label không phân biệt hoa-thường**
`LabelEditVM` chỉ chặn trùng chính xác sau trim — `"Work"` và `"work"` vẫn tồn tại song song, gây nhầm lẫn phổ biến. Effort: XS. Priority: P2.

**ENH-022 — Toast báo lỗi khi share-to-note thất bại thay vì im lặng**
File: `MainAct.kt:365-393` (`createNoteFromIntent`) hiện `catch (e: IOException) {}` không có phản hồi UI nào. Effort: XS. Priority: P2.

**ENH-023 — Phản hồi UI khi thông báo/deep-link trỏ tới note đã bị xoá**
`MainVM.editNote(id)` hiện no-op im lặng nếu note không còn tồn tại. Effort: XS. Priority: P2.

**ENH-024 — Đồng bộ quy ước logging: thay `e.printStackTrace()` bằng `Log.i/Log.e` như phần còn lại của file**
File: `SettingsFrm.kt:112-113,131-132`. Effort: XS. Priority: P2.

---

## 3. NEW FEATURE — Tính năng mới hợp lý, chưa tồn tại

**NEW-001 — Hỗ trợ đính kèm ảnh/file**
Xác nhận: hoàn toàn không có code image/camera/gallery/attachment nào trong `ui/edit`, không có bảng `attachments` trong schema Room. Đây là khoảng trống lớn nhất so với các app ghi chú cùng phân khúc. Cần: entity `Attachment` mới + migration, UI chọn ảnh, hiển thị trong `EditFrm`/`NoteListVH`. Effort: L. Priority: P1. Files liên quan: `model/entity/`, `NotesDb.kt`, `ui/edit/`.

**NEW-002 — Markdown / rich text cơ bản (đậm, nghiêng, tiêu đề)**
Hiện chỉ có auto-bullet (`BulletTextWatcher`) và autolink; không có span định dạng nào được lưu trữ. Effort: L. Priority: P2. Files: `ui/edit/`, `model/entity/Note.kt` (cần format lưu trong `content`/`metadata`).

**NEW-003 — Note template**
`EditVM.start()` đã nhận sẵn tham số `title`/`content`/`type` — chỉ cần thêm màn hình chọn template feed vào cùng tham số đó, rủi ro thấp. Effort: S. Priority: P1. Files: `ui/edit/EditVM.kt`, `ui/edit/EditFrm.kt`.

**NEW-004 — Gán màu/mood hàng loạt trong multi-select**
`NoteVM` action bar đã có pin/reminder/labels/move/delete cho multi-select; `Note.color`/`Note.mood` đã tồn tại sẵn — chỉ thiếu action hàng loạt. Effort: S. Priority: P2. Files: `ui/note/NoteFrm.kt`, `ui/note/NoteVM.kt`.

**NEW-005 — App-link/deep link (`notes://` hoặc App Link https) trỏ thẳng tới note**
Hiện không có `<data android:scheme>` intent-filter nào ngoài action nội bộ. Tận dụng lại `INTENT_ACTION_EDIT` đã có. Effort: M. Priority: P2. Files: `AndroidManifest.xml`, `ui/main/MainAct.kt`.

**NEW-006 — Recurring reminder template ("mỗi thứ 2 9h", "ngày 1 hàng tháng")**
Hạ tầng `Recurrence`/`RecurrenceFinder` đã đủ mạnh, chỉ cần thêm preset 1-chạm trong `ReminderDlg`. Effort: S. Priority: P2. Files: `ui/reminder/ReminderDlg.kt`.

**NEW-007 — Quick-add ngay trên widget (nhập liệu inline, không cần mở app)**
`QuickNoteWidget`/`QuickListWidget` hiện chỉ deep-link vào `MainAct`. Effort: L. Priority: P2. Files: `widget/QuickNoteWidget.kt`, `widget/QuickListWidget.kt`.

**NEW-008 — Digest tổng hợp reminder quá hạn (thông báo định kỳ)**
Thông báo tóm tắt hàng ngày các reminder quá hạn chưa đánh dấu xong, bổ sung cho luồng thông báo từng note hiện có. Effort: M. Priority: P2. Files: mới, tích hợp `receiver/AlarmReceiver.kt`.

**NEW-009 — Lọc widget Recent Notes/Quick List theo label + configuration Activity**
Kết hợp với ENH-011. Effort: M. Priority: P2.

**NEW-010 — Màu sắc/icon cho label**
`Label` entity hiện chỉ có `id/name/hidden` — cần thêm cột `color`+migration. Effort: M. Priority: P1. Files: `model/entity/Label.kt`, `NotesDb.kt` (migration mới), `ui/labels/`.

**NEW-011 — Khoá app bằng sinh trắc học/PIN ở cấp toàn app**
Hạ tầng mã hoá PBKDF2+AES-GCM+AndroidKeyStore đã có sẵn cho export/import — có thể tái sử dụng cho app-lock toàn cục, độc lập với khoá từng note hiện tại. Effort: L. Priority: P1. Files: mới + `RApp.kt`, `ui/setting/`.

**NEW-012 — Backup/đồng bộ cloud (tuỳ chọn) cho pipeline export/import JSON hiện có**
`DefaultJsonManager` đã có full pipeline export/import local — chỉ thiếu đích lưu trữ cloud. Effort: L. Priority: P2.

**NEW-013 — Thứ tự note thủ công (kéo-thả) như 1 SortField mới**
Hiện không có cách sắp xếp thủ công/tuỳ ý. Effort: M. Priority: P2. Files: `ui/sort/`, `NotesDao.kt` (cần thêm cột order).

**NEW-014 — Xem diff trực quan giữa các phiên bản Time Travel**
`NoteHistory` hiện chỉ lưu full-text snapshot, restore chỉ có preview toàn văn. Effort: M. Priority: P2. Files: `ui/edit/` (màn hình history hiện có), `model/entity/NoteHistory.kt`.

---

## 4. IDEA — Ý tưởng thô, cần nghiên cứu thêm

**IDEA-001 — Undo/redo thật (per-keystroke) trong editor**
Khác với Time Travel (snapshot thô ~10s debounce), đây là undo/redo tức thời như trình soạn thảo văn bản chuẩn. Cần nghiên cứu chi phí bộ nhớ cho note dài (tới 100k ký tự) và tương tác với `BulletTextWatcher`/autolink. Effort: L.

**IDEA-002 — Widget khoá màn hình (always-on-display) hiện reminder ưu tiên cao nhất**
Cần nghiên cứu API always-on-display hiện có trên Android (giới hạn nhiều theo OEM) và giải quyết trước vấn đề riêng tư ở FIX-034 (không được hiện tiêu đề note khoá trên bề mặt còn công khai hơn cả home-screen widget).

**IDEA-003 — Nested/phân cấp label (label cha-con)**
Cần nghiên cứu UX cho tập label lớn, và migration schema `Label` phức tạp hơn NEW-010.

**IDEA-004 — Rate-limit/lockout khi nhập sai password import nhiều lần**
Rủi ro thực tế thấp (kẻ tấn công cần có sẵn file export local), nhưng đáng cân nhắc thêm đếm số lần thử + delay tăng dần cho nhất quán với UX bảo mật khác trong app.

**IDEA-005 — Multi-window/freeform chính thức**
Cả 3 activity hiện khoá `screenOrientation="portrait"` không khai báo `resizeableActivity`. FIX-011 (onNewIntent) dễ tái hiện nhất qua split-screen — cần quyết định rõ: hỗ trợ chính thức (fix toàn bộ gap liên quan) hay khoá cứng `resizeableActivity="false"`.

**IDEA-006 — Chuẩn hoá bộ 3 package `com.mckimquyen.notes` / `com.mckimquyen.debug.notes` / `com.maltaisn.notes` còn sót lại từ rebrand**
Dọn dẹp lớn, rủi ro cao vì đụng vào cấu trúc DI/source-set đang hoạt động — cần lên kế hoạch riêng, không làm vội (xem CLAUDE.md: "Don't fix that mismatch — variant source sets depend on it" — cần đọc kỹ trước khi động vào).

**IDEA-007 — Xoay vòng lại khoá ký (signing key) sau sự cố lộ trong lịch sử git (commit `212225a` và các commit trước đó)**
Không phải bug code, nhưng là rủi ro bảo mật nghiêm trọng cần xử lý ở cấp quy trình: xác nhận app có dùng Play App Signing hay không (quyết định có rotate được upload key không), cân nhắc rewrite git history, xoay `KS_PW`. Cần thảo luận với người có quyền truy cập Play Console trước khi hành động — không nằm trong phạm vi sửa code của audit này.

---

## 5. EXCLUSIVE — Khác biệt cạnh tranh thật so với Keep/Samsung Notes/Notion/Obsidian

**EXC-001 — "Time Travel" version history đầy đủ, hoàn toàn offline, có diff trực quan**
Hạ tầng `NoteHistory` đã tồn tại nhưng đang thiếu field (chỉ lưu title/content/metadata, không lưu reminder/labels/color/mood/pinned/status — xem gap ở phần data-layer). Đầu tư hoàn thiện thành "lịch sử phiên bản" đầy đủ + diff UI (NEW-014) là điểm khác biệt thật: Google Keep không có version history; Notion có nhưng bắt buộc cloud/account; Obsidian chỉ có qua plugin cộng đồng, không native. Một note app offline-first với version history native, đầy đủ field, không cần tài khoản, là câu chuyện định vị mạnh. Effort: L. Priority: P1. Files: `model/entity/NoteHistory.kt`, `NotesDb.kt` (migration), `ui/edit/`.

**EXC-002 — Backup/mã hoá hoàn toàn local, không tài khoản, không server tin cậy**
Pipeline export/import hiện tại (AES-GCM/PBKDF2WithHmacSHA512 120.000 vòng lặp/AndroidKeyStore, xác nhận qua code không phải "weak crypto") đã là nền tảng tốt cho câu chuyện riêng tư "zero-account, zero-cloud, client-side encryption" — khác hẳn Keep (bắt buộc tài khoản Google) và Notion (bắt buộc tài khoản, lưu cloud của Notion). Cần vá FIX-004/FIX-021 trước khi quảng bá tính năng này làm điểm bán hàng, nếu không sẽ phản tác dụng (locked note bị mở khoá sau restore). Effort: S (sau khi vá bug) + marketing. Priority: P1.

**EXC-003 — Note gắn "mood" như metadata hạng nhất**
Trường `Note.mood` đã tồn tại trong entity nhưng theo audit hiện chưa có UI hàng loạt/lọc/thống kê theo mood — không đối thủ nào (Keep/Samsung Notes/Notion/Obsidian) coi cảm xúc là trường ghi chú hạng nhất. Phát triển thành góc nhìn "nhật ký cảm xúc nhẹ" (lọc/thống kê note theo mood theo thời gian) là hướng khác biệt thật, không phải tính năng đã có sẵn ở nơi khác. Effort: M. Priority: P2.

**EXC-004 — Bộ widget đa dạng nhất phân khúc, phát triển thành "dashboard" cấu hình được**
App hiện đã có 4 loại widget riêng biệt (QuickNote, NoteCount, QuickList, RecentNotes) — nhiều hơn Keep (1 loại) hay Samsung Notes (không có widget mạnh). Kết hợp NEW-007 (quick-add inline) + NEW-009 (lọc theo label) + ENH-011 (configuration activity) thành 1 "widget suite" cấu hình sâu là hướng khác biệt khả thi, tận dụng hạ tầng đã có thay vì xây từ đầu. Effort: L (tổng hợp nhiều item trên). Priority: P2.

**EXC-005 — Reminder định kỳ mạnh ngang app lịch, gắn trực tiếp vào note**
Đã dùng `com.maltaisn:recurpicker` — mạnh hơn hẳn Keep (chỉ nhắc 1 lần/lặp cơ bản) và Samsung Notes (gần như không có reminder định kỳ thật sự). Sau khi vá FIX-003 (bug ngày cuối tháng) và bổ sung NEW-006 (template) + ENH-010 (exact alarm), đây là 1 tính năng lõi đã mạnh sẵn, chỉ cần hoàn thiện để trở thành điểm bán hàng rõ ràng thay vì "cũng có reminder" chung chung. Effort: M (sau khi vá bug). Priority: P1.
