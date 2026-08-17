Duoc viet boi agy CLI - audit doc lap 2026-08-17

# PRODUCT BACKLOG & AUDIT TOÀN DIỆN - ANOTHER NOTES APP
**Người lập:** Scrum Master / Lead Auditor (agy CLI)  
**Ngày phát hành:** 2026-08-17  
**Phạm vi:** Toàn bộ source code Android (`app/src/main/kotlin/`, `res/`, `AndroidManifest.xml`, `build.gradle`, Room Migrations v1-v8, Dagger DI, Widget Layer, Lifecycle & Coroutine Scopes)

---

## MỤC LỤC
1. [NHÓM 1: FIX - LỖI VÀ RỦI RO KỸ THUẬT TRONG CODE HIỆN TẠI](#1-nhom-1-fix---loi-va-rui-ro-ky-thuat-trong-code-hien-tai)
2. [NHÓM 2: ENHANCE - CẢI TIẾN TÍNH NĂNG ĐÃ CÓ](#2-nhom-2-enhance---cai-tien-tinh-nang-da-co)
3. [NHÓM 3: NEW FEATURE - TÍNH NĂNG MỚI TIÊU CHUẨN](#3-nhom-3-new-feature---tinh-nang-moi-tieu-chuan)
4. [NHÓM 4: IDEA - Ý TƯỞNG ĐỘT PHÁ & THỬ NGHIỆM](#4-nhom-4-idea---y-tuong-dot-pha--thu-nghiem)
5. [NHÓM 5: EXCLUSIVE - TÍNH NĂNG ĐỘC QUYỀN & LỢI THẾ CẠNH TRANH](#5-nhom-5-exclusive---tinh-nang-doc-quyen--loi-the-canh-tranh)

---

## 1. NHÓM 1: FIX - LỖI VÀ RỦI RO KỸ THUẬT TRONG CODE HIỆN TẠI

| ID | Tiêu đề lỗi | File & Dòng | Mức độ | Ước lượng | Priority |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FIX-01** | Mất dữ liệu `color`, `mood`, `isLocked` khi Backup/Restore JSON | `DefaultJsonManager.kt:57-60, 212-223, 299-321` | **Critical** | S | **P0** |
| **FIX-02** | Treo Coroutine vĩnh viễn khi tạo ghi chú nếu init lỗi (`_deletionFinishedMutex`) | `MainVM.kt:64, 106-110, 222-224` | **High** | XS | **P0** |
| **FIX-03** | `IndexOutOfBoundsException` khi lưu Checklist Note có khoảng trống vị trí | `EditVM.kt:713-718` | **High** | S | **P0** |
| **FIX-04** | Logic "Double Back to Exit" bị đảo ngược khiến app thoát ngay lần bấm đầu | `MainAct.kt:81, 175-184` | **Medium** | XS | **P1** |
| **FIX-05** | CoroutineScope không quản lý trong `NoteCountWidget.onUpdate` có nguy cơ bị OS kill | `NoteCountWidget.kt:64-70` | **Medium** | XS | **P1** |
| **FIX-06** | `ClassCastException` trong `moveCheckedItemsToBottom` khi sắp xếp checklist | `EditVM.kt:1198-1200` | **Medium** | XS | **P1** |
| **FIX-07** | Tiêu đề PDF Export bị vẽ đè tại toạ độ (0, 0) mất lề Margin | `ExportHelper.kt:86-89` | **Medium** | XS | **P1** |
| **FIX-08** | Duplicate `TimelineDateHeaderItem` ID và crash DiffUtil khi sort không theo ngày tạo | `NoteVM.kt:48-66` | **Medium** | S | **P1** |
| **FIX-09** | Gọi `registerForActivityResult` sau `onCreate` trong `ReminderDlg` gây crash Android 13+ | `ReminderDlg.kt:164-166, 274` | **Medium** | XS | **P1** |
| **FIX-10** | Thiếu `alarmManager.cancel()` và dùng Inexact Alarm khi máy vào Doze Mode | `ReceiverAlarmCallback.kt:38-47` | **Medium** | S | **P1** |
| **FIX-11** | `NoSuchElementException` & NPE khi nhận Share Content không có path segments | `MainAct.kt:374-380` | **Low** | XS | **P2** |
| **FIX-12** | Race Condition xoá sạch thư mục export cache khi share đa ứng dụng | `EditFrm.kt:858-860` | **Low** | XS | **P2** |
| **FIX-13** | Google In-App Review bị kích hoạt ngay lần đầu tiên mở app sau cài đặt | `BaseAct.kt:29, 68-75` | **Low** | XS | **P2** |
| **FIX-14** | `ClassCastException` khi cast `toolbarLayout.background as MaterialShapeDrawable` | `NoteFrm.kt:623, 641`, `LabelFrm.kt:259, 275` | **Low** | XS | **P2** |
| **FIX-15** | Rò rỉ ActivityResultLauncher trong `SettingsFrm.onDestroy` (`importDataLauncher`) | `SettingsFrm.kt:357-361` | **Low** | XS | **P2** |

---

### CHI TIẾT CÁC LỖI NHÓM 1 (FIX)

#### FIX-01: Mất dữ liệu `color`, `mood`, `isLocked` khi Backup/Restore JSON
- **Vị trí:** [DefaultJsonManager.kt:57-60, 212-223, 299-321](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/DefaultJsonManager.kt#L57-L60)
- **Mô tả lỗi:** Entity `Note` đã được nâng cấp Room Database lên Version 8 (thêm cột `color` ở v5, `mood` ở v6, `is_locked` ở v7). Tuy nhiên, class DTO trung gian `NoteSurrogate` và method `importNotes()` chưa từng được cập nhật các field này. Khi export ra file JSON (cả thủ công và tự động), 3 thuộc tính trên bị bỏ qua hoàn toàn. Khi restore file JSON, toàn bộ ghi chú bị reset về màu mặc định (0), không có mood (0) và bị mở khoá (`isLocked = false`).
- **Kịch bản tái hiện:**
  1. Người dùng tạo ghi chú A: đổi màu nền sang Vàng, chọn mood 😄, bật khoá vân tay (`isLocked = true`).
  2. Vào Cài đặt -> Xuất dữ liệu ra file `notes.json`.
  3. Xoá app hoặc cài đặt trên thiết bị mới -> Nhập dữ liệu từ file `notes.json`.
  4. **Kết quả:** Ghi chú A mất màu sắc, mất mood emoji, và khoá bảo mật biến mất.
- **Mức độ:** **Critical** (Mất mát dữ liệu người dùng & vi phạm cam kết bảo mật khoá).
- **Effort:** `S` | **Priority:** `P0`
- **Files/Class liên quan:** `DefaultJsonManager.kt` (`NoteSurrogate`, `exportJsonData`, `importNotes`), `Note.kt`.

---

#### FIX-02: Treo Coroutine vĩnh viễn khi tạo ghi chú nếu startup init lỗi
- **Vị trí:** [MainVM.kt:64, 106-110, 222-224](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/main/MainVM.kt#L64)
- **Mô tả lỗi:** `_deletionFinishedMutex` được khởi tạo ở trạng thái khoá `Mutex(locked = true)`. Trong khối `init`, `viewModelScope.launch` thực hiện xoá blank notes rồi gọi `_deletionFinishedMutex.unlock()`. Nếu quá trình truy vấn Room DB bị lỗi ngoại lệ (ví dụ `SQLiteException` hoặc cancellation), lệnh `unlock()` bị bỏ qua vĩnh viễn. Khi người dùng bấm tạo ghi chú từ Drawer hoặc Launcher Shortcut, hàm `createNote()` gọi `_deletionFinishedMutex.withLock { ... }` sẽ bị treo vĩnh viễn (hang) mà không thể mở màn hình soạn thảo.
- **Kịch bản tái hiện:**
  1. Giả lập Room DB gặp lỗi transient read/write lúc mở app hoặc coroutine bị huỷ trước khi đến dòng `unlock()`.
  2. Bấm nút tạo ghi chú trên App Shortcut hoặc Floating Action Button.
  3. **Kết quả:** Không có phản hồi, coroutine bị suspend vô thời hạn, UI đứng im.
- **Mức độ:** **High** (Gây tê liệt chức năng cốt lõi tạo ghi chú).
- **Effort:** `XS` | **Priority:** `P0`
- **Files/Class liên quan:** `MainVM.kt` (`_deletionFinishedMutex`, `deleteBlankNotes`, `createNote`).

---

#### FIX-03: `IndexOutOfBoundsException` khi lưu Checklist Note có vị trí gián đoạn
- **Vị trí:** [EditVM.kt:713-718](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/edit/EditVM.kt#L713-L718)
- **Mô tả lỗi:** Trong `noteContent` (phần serialize danh sách checklist sang String), code khởi tạo `val items = MutableList(listItems.size - unactualItemCount) { "" }` và gán `items[item.actualPos] = item.content`. Giả định `item.actualPos < items.size` bị sai khi danh sách checklist trải qua thao tác xoá item ở giữa hoặc dán nhiều dòng vào item đã checked, khiến `actualPos` lớn hơn kích thước mảng cấp phát, ném ra `IndexOutOfBoundsException` làm crash ứng dụng ngay khi lưu ghi chú.
- **Kịch bản tái hiện:**
  1. Tạo checklist gồm 5 mục (mục 0 đến 4).
  2. Tick chọn mục 2 và 3, sau đó xoá mục 1.
  3. Thao tác dán nội dung nhiều dòng vào một mục con.
  4. Bấm quay lại hoặc thoát khỏi màn hình Edit để kích hoạt `saveNote()`.
  5. **Kết quả:** App crash với `java.lang.IndexOutOfBoundsException: Index: X, Size: Y`.
- **Mức độ:** **High** (Crash app và mất nội dung ghi chú đang nhập).
- **Effort:** `S` | **Priority:** `P0`
- **Files/Class liên quan:** `EditVM.kt` (`noteContent`, `actualPos`).

---

#### FIX-04: Logic "Double Back to Exit" bị đảo ngược khiến app thoát ngay lần bấm đầu
- **Vị trí:** [MainAct.kt:81, 175-184](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/main/MainAct.kt#L81)
- **Mô tả lỗi:** Biến `doubleBackToExitPressedOnce` được khởi tạo là `true` (dòng 81) và reset thành `true` trong `onStop()`. Khi người dùng đang ở `HomeFrm` và bấm nút Back phần cứng lần đầu tiên, điều kiện `if (doubleBackToExitPressedOnce)` đánh giá thành `true` và gọi ngay `finish()` thoát app, làm mất hoàn toàn tính năng bảo vệ "Bấm lần nữa để thoát".
- **Kịch bản tái hiện:**
  1. Mở ứng dụng vào màn hình chính `HomeFrm`.
  2. Bấm nút Back 1 lần duy nhất.
  3. **Kết quả:** App đóng ngay lập tức thay vì hiện thông báo "Bấm lần nữa để thoát".
- **Mức độ:** **Medium** (Trải nghiệm người dùng bị lỗi logic điều hướng).
- **Effort:** `XS` | **Priority:** `P1`
- **Files/Class liên quan:** `MainAct.kt` (`doubleBackToExitPressedOnce`, `OnBackPressedCallback`).

---

#### FIX-05: CoroutineScope không quản lý trong `NoteCountWidget.onUpdate` có nguy cơ bị OS kill
- **Vị trí:** [NoteCountWidget.kt:64-70](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/widget/NoteCountWidget.kt#L64-L70)
- **Mô tả lỗi:** Trong `AppWidgetProvider.onUpdate()`, code khởi tạo một coroutine độc lập dạng `CoroutineScope(Dispatchers.IO).launch { ... }` mà không sử dụng `goAsync()` và không quản lý vòng đời (ngược lại hoàn toàn với chuẩn mực đã fix ở `AlarmReceiver.kt`). Khi hệ điều hành Android phát broadcast widget update lúc thiết bị đang thiếu RAM, tiến trình BroadcastReceiver bị kết thúc ngay khi `onUpdate()` trả về đồng bộ, làm coroutine nền bị huỷ giữa chừng khiến widget hiển thị sai số lượng ghi chú.
- **Kịch bản tái hiện:**
  1. Đặt widget Note Count lên màn hình launcher.
  2. Chạy nhiều app nặng để Android rơi vào tình trạng Low Memory Killer (LMK).
  3. Hệ thống kích hoạt update widget định kỳ.
  4. **Kết quả:** Widget không cập nhật số lượng do BroadcastReceiver process bị thu hồi ngay lập tức.
- **Mức độ:** **Medium** (Widget mất đồng bộ dữ liệu thực tế).
- **Effort:** `XS` | **Priority:** `P1`
- **Files/Class liên quan:** `NoteCountWidget.kt` (`onUpdate`, `updateWidgetInternal`).

---

#### FIX-06: `ClassCastException` trong `moveCheckedItemsToBottom` khi sắp xếp checklist
- **Vị trí:** [EditVM.kt:1198-1200](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/edit/EditVM.kt#L1198-L1200)
- **Mô tả lỗi:** Đoạn code `listItems.subList(fromIndex, toIndex).sortBy { (it as EditItemItem).actualPos }` giả định tất cả item trong khoảng index đều là kiểu `EditItemItem`. Nếu trong danh sách tồn tại item dạng Header, Chip hoặc loại khác, thao tác ép kiểu thô `it as EditItemItem` sẽ ném `ClassCastException`.
- **Kịch bản tái hiện:**
  1. Mở ghi chú checklist ở chế độ chỉnh sửa.
  2. Bật tuỳ chọn di chuyển các mục đã tick xuống dưới cùng.
  3. **Kết quả:** App ném lỗi `java.lang.ClassCastException` nếu có phần tử đặc biệt trong list.
- **Mức độ:** **Medium** (Crash khi tương tác với checklist phức tạp).
- **Effort:** `XS` | **Priority:** `P1`
- **Files/Class liên quan:** `EditVM.kt` (`moveCheckedItemsToBottom`, `EditItemItem`).

---

#### FIX-07: Tiêu đề PDF Export bị vẽ đè tại toạ độ (0, 0) mất lề Margin
- **Vị trí:** [ExportHelper.kt:86-89](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/edit/ExportHelper.kt#L86-L89)
- **Mô tả lỗi:** Trong hàm `exportAsPdf()`, các phần tử `metaLayout`, `paraLayout`, `itemLayout` đều được bao bởi `canvas.save(); canvas.translate(marginLeft, currentY); ...; canvas.restore()`. Riêng `titleLayout.draw(canvas)` ở dòng 88 được gọi trực tiếp mà **không có** `canvas.translate(marginLeft, currentY)`. Do đó tiêu đề ghi chú bị vẽ dính sát góc trên bên trái của trang PDF tại toạ độ `(0, 0)`, đè lên viền trang giấy và mất toàn bộ căn lề lề trái / lề trên.
- **Kịch bản tái hiện:**
  1. Tạo ghi chú có tiêu đề "BÁO CÁO CÔNG VIỆC THÁNG 8" và nội dung văn bản.
  2. Chọn menu Xuất PDF -> Mở file PDF đã xuất.
  3. **Kết quả:** Tiêu đề bị lệch lên sát mép trên góc trái ngoài lề quy định, không thẳng hàng với nội dung bên dưới.
- **Mức độ:** **Medium** (Lỗi hiển thị xuất bản tài liệu PDF).
- **Effort:** `XS` | **Priority:** `P1`
- **Files/Class liên quan:** `ExportHelper.kt` (`exportAsPdf`).

---

#### FIX-08: Duplicate `TimelineDateHeaderItem` ID và crash DiffUtil khi sort không theo ngày tạo
- **Vị trí:** [NoteVM.kt:48-66](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/note/NoteVM.kt#L48-L66)
- **Mô tả lỗi:** Trong `buildDisplayedList()`, các header ngày tháng `TimelineDateHeaderItem` được chèn vào danh sách dựa trên sự thay đổi giữa các phần tử liên tiếp (`dateLabel != lastDateLabel`). Khi người dùng sắp xếp danh sách theo Tiêu đề (A-Z) hoặc Ngày sửa đổi, các ghi chú có cùng ngày tạo không nằm cạnh nhau. Điều này khiến `TimelineDateHeaderItem` cho cùng một ngày (ví dụ "17 Aug 2026") bị tạo lặp lại nhiều lần với cùng một `headerId` âm giống hệt nhau, làm `RecyclerView` DiffUtil gặp lỗi xung đột ID hoặc ném `IllegalArgumentException: Two different items with the same id`.
- **Kịch bản tái hiện:**
  1. Chuyển giao diện sang chế độ Dòng thời gian (Timeline).
  2. Vào Menu Sắp xếp -> Chọn sắp xếp theo "Tiêu đề (A-Z)".
  3. Tạo 3 ghi chú xen kẽ ngày tạo.
  4. **Kết quả:** RecyclerView giật lag, nhảy item sai vị trí hoặc crash do trùng lặp ViewHolder ID.
- **Mức độ:** **Medium** (Lỗi hiển thị và crash tiềm ẩn ở chế độ Timeline).
- **Effort:** `S` | **Priority:** `P1`
- **Files/Class liên quan:** `NoteVM.kt` (`buildDisplayedList`), `TimelineVH.kt`.

---

#### FIX-09: Gọi `registerForActivityResult` sau `onCreate` trong `ReminderDlg` gây crash Android 13+
- **Vị trí:** [ReminderDlg.kt:164-166, 274](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/reminder/ReminderDlg.kt#L164-L166)
- **Mô tả lỗi:** Trong `ReminderDlg`, `requestPermissionLauncher = registerForActivityResult(...)` được gọi bên trong hàm `requestNotificationPermission()`, vốn được kích hoạt từ `onCreateDialog()`. Theo nguyên tắc của AndroidX Activity Result API, `registerForActivityResult` **bắt buộc** phải được đăng ký vô điều kiện trước hoặc trong vòng đời `onCreate()`. Việc gọi đăng ký khi Fragment đã bước vào trạng thái `CREATED`/`STARTED` có thể ném `IllegalStateException: LifecycleOwner is attempting to register while current state is RESUMED/STARTED`.
- **Kịch bản tái hiện:**
  1. Mở app trên thiết bị chạy Android 13 (API 33) trở lên chưa cấp quyền thông báo.
  2. Mở một ghi chú -> Bấm đặt nhắc nhở (Reminder).
  3. Dialog nhắc nhở mở ra và kích hoạt xin quyền.
  4. **Kết quả:** Có nguy cơ crash văng ứng dụng với lỗi `IllegalStateException`.
- **Mức độ:** **Medium** (Lỗi vi phạm vòng đời AndroidX trên Android 13+).
- **Effort:** `XS` | **Priority:** `P1`
- **Files/Class liên quan:** `ReminderDlg.kt` (`requestNotificationPermission`, `onCreateDialog`).

---

#### FIX-10: Thiếu `alarmManager.cancel()` và dùng Inexact Alarm khi máy vào Doze Mode
- **Vị trí:** [ReceiverAlarmCallback.kt:38-47](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/receiver/ReceiverAlarmCallback.kt#L38-L47)
- **Mô tả lỗi:**
  1. Tại hàm `addAlarm()`, code sử dụng `alarmManager.set(AlarmManager.RTC_WAKEUP, time, alarmIntent)`. Từ Android 6.0 (API 23), phương thức `set()` không thể đánh thức máy khi thiết bị bước vào trạng thái Doze Mode (ngủ sâu), dẫn đến chuông nhắc nhở bị hoãn lại hàng giờ cho tới khi người dùng bật sáng màn hình.
  2. Tại hàm `removeAlarm()`, code chỉ gọi `getAlarmPendingIndent(noteId).cancel()` mà **quên** không gọi `alarmManager.cancel(...)`, khiến hệ điều hành vẫn giữ trigger trong bảng quản lý Alarm của hệ thống.
- **Kịch bản tái hiện:**
  1. Đặt nhắc nhở cho 15 phút sau -> Khoá màn hình và để điện thoại nằm yên trên bàn 15 phút.
  2. **Kết quả:** Đúng giờ chuông không reo; chuông chỉ reo khi người dùng cầm máy và mở khoá màn hình.
- **Mức độ:** **Medium** (Chức năng nhắc nhở không đáng tin cậy khi thiết bị ngủ).
- **Effort:** `S` | **Priority:** `P1`
- **Files/Class liên quan:** `ReceiverAlarmCallback.kt` (`addAlarm`, `removeAlarm`), `AlarmManager`.

---

#### FIX-11: `NoSuchElementException` & NPE khi nhận Share Content không có path segments
- **Vị trí:** [MainAct.kt:374-380](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/main/MainAct.kt#L374-L380)
- **Mô tả lỗi:** Khi xử lý `Intent.ACTION_SEND` nhận file văn bản từ ứng dụng thứ ba: `val title = uri.pathSegments.last()` sẽ ném `NoSuchElementException` nếu URI chia sẻ không có path segments (ví dụ root content URI `content://com.example.provider/`). Đồng thời `InputStreamReader(contentResolver.openInputStream(uri)!!)` sử dụng toán tử non-null assertion `!!`, gây crash NPE nếu Content Provider từ chối mở luồng stream.
- **Kịch bản tái hiện:**
  1. Sử dụng ứng dụng quản lý file hoặc app chat chia sẻ tệp tin dạng content URI đặc biệt sang Another Notes.
  2. **Kết quả:** App crash ngay khi vừa mở `MainAct`.
- **Mức độ:** **Low** (Edge case khi nhận dữ liệu từ app bên ngoài).
- **Effort:** `XS` | **Priority:** `P2`
- **Files/Class liên quan:** `MainAct.kt` (`handleSendIntent`).

---

#### FIX-12: Race Condition xoá sạch thư mục export cache khi share đa ứng dụng
- **Vị trí:** [EditFrm.kt:858-860](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/edit/EditFrm.kt#L858-L860)
- **Mô tả lỗi:** Đoạn code `exportDir.listFiles()?.forEach { it.delete() }` xoá toàn bộ các file tạm trong thư mục `cacheDir/exports/` ngay lập tức mỗi khi người dùng bấm nút chia sẻ xuất file. Nếu người dùng vừa bấm chia sẻ PDF sang Gmail (FileProvider đang phục vụ cho Gmail đọc file ở background), sau đó quay lại app bấm xuất ảnh PNG, file PDF cũ bị xoá ngay lập tức khiến tiến trình đính kèm của Gmail báo lỗi thiếu file.
- **Kịch bản tái hiện:**
  1. Trong màn hình Edit, chọn Xuất PDF -> Chọn ứng dụng lưu trữ / chia sẻ.
  2. Ngay lập tức quay lại và bấm Xuất Hình ảnh.
  3. **Kết quả:** File PDF đang chia sẻ bị xoá mất, ứng dụng bên ngoài không thể đọc được dữ liệu.
- **Mức độ:** **Low** (Race condition khi thao tác xuất file liên tục).
- **Effort:** `XS` | **Priority:** `P2`
- **Files/Class liên quan:** `EditFrm.kt` (`shareExportedFile`).

---

#### FIX-13: Google In-App Review bị kích hoạt ngay lần đầu tiên mở app sau cài đặt
- **Vị trí:** [BaseAct.kt:29, 68-75](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/main/BaseAct.kt#L29)
- **Mô tả lỗi:** Biến `last_review_time` trong SharedPreferences mặc định có giá trị `0L`. Khi người dùng cài mới ứng dụng và mở lần đầu, phép tính `(currentTime - 0L) / (1000 * 60 * 60 * 24)` cho ra kết quả cực lớn (> 19,000 ngày), thoả mãn điều kiện `> 14 ngày` và kích hoạt ngay luồng Google In-App Review Flow trong `onResume()` ở màn hình mở đầu.
- **Kịch bản tái hiện:**
  1. Cài mới ứng dụng (fresh install).
  2. Mở ứng dụng lần đầu tiên.
  3. **Kết quả:** Dialog xin đánh giá 5 sao xuất hiện ngay lập tức trước khi người dùng kịp tạo bất kỳ ghi chú nào.
- **Mức độ:** **Low** (Trải nghiệm người dùng kém chuyên nghiệp).
- **Effort:** `XS` | **Priority:** `P2`
- **Files/Class liên quan:** `BaseAct.kt` (`checkShowInAppReview`).

---

#### FIX-14: `ClassCastException` khi cast `toolbarLayout.background as MaterialShapeDrawable`
- **Vị trí:** [NoteFrm.kt:623, 641](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/note/NoteFrm.kt#L623), [LabelFrm.kt:259, 275](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/labels/LabelFrm.kt#L259)
- **Mô tả lỗi:** Khi kích hoạt hoặc thoát Action Mode (chọn nhiều ghi chú/nhãn), code thực hiện đổi màu status bar bằng cách ép kiểu `(binding.toolbarLayout.background as MaterialShapeDrawable).resolvedTintColor`. Nếu trên một số dòng máy tùy biến (ColorOS, MIUI, OneUI) hoặc theme tuỳ chỉnh mà background của `AppBarLayout` là `ColorDrawable`, `GradientDrawable` hoặc `null`, lệnh ép kiểu thô sẽ ném `ClassCastException` và crash app.
- **Kịch bản tái hiện:**
  1. Áp dụng theme hoặc cấu hình layout thay đổi background của AppBar.
  2. Nhấn giữ một ghi chú để vào chế độ đa lựa chọn (Action Mode).
  3. **Kết quả:** Crash app với lỗi ép kiểu `MaterialShapeDrawable`.
- **Mức độ:** **Low** (Crash trên một số thiết bị tùy biến theme).
- **Effort:** `XS` | **Priority:** `P2`
- **Files/Class liên quan:** `NoteFrm.kt`, `LabelFrm.kt` (`onCreateActionMode`, `onDestroyActionMode`).

---

#### FIX-15: Rò rỉ ActivityResultLauncher trong `SettingsFrm.onDestroy` (`importDataLauncher`)
- **Vị trí:** [SettingsFrm.kt:357-361](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/ui/setting/SettingsFrm.kt#L357-L361)
- **Mô tả lỗi:** Trong `SettingsFrm.onDestroy()`, `exportDataLauncher` và `autoExportLauncher` đều được gán bằng `null` để tránh rò rỉ bộ nhớ, nhưng `importDataLauncher` bị bỏ sót. Khi người dùng ra vào màn hình Cài đặt nhiều lần, tham chiếu `importDataLauncher` giữ lại context của Fragment cũ.
- **Kịch bản tái hiện:**
  1. Mở Cài đặt -> Quay lại màn hình chính -> Lặp lại nhiều lần.
  2. Chạy profiler kiểm tra rò rỉ bộ nhớ.
  3. **Kết quả:** Giữ lại tham chiếu không cần thiết tới ActivityResultLauncher cũ.
- **Mức độ:** **Low** (Rò rỉ tài nguyên nhỏ).
- **Effort:** `XS` | **Priority:** `P2`
- **Files/Class liên quan:** `SettingsFrm.kt` (`onDestroy`).

---

## 2. NHÓM 2: ENHANCE - CẢI TIẾN TÍNH NĂNG ĐÃ CÓ

| ID | Tiêu đề cải tiến | Mô tả ngắn gọn | Effort | Files / Class liên quan | Priority |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ENH-01** | **Checklist Bulk Actions (Thao tác hàng loạt)** | Thêm thanh công cụ nhanh cho checklist: "Đánh dấu tất cả hoàn thành", "Bỏ chọn tất cả", "Xóa các mục đã xong", "Sắp xếp theo bảng chữ cái A-Z". | S | `EditVM.kt`, `EditFrm.kt`, `v_item_edit_item.xml` | **P0** |
| **ENH-02** | **Bộ lọc Widget theo Nhãn (Label Widget Filter)** | Cho phép người dùng cấu hình `RecentNotesWidget` để chỉ hiển thị các ghi chú thuộc về một Nhãn (Label) cụ thể thay vì chỉ hiển thị toàn bộ ghi chú mới. | M | `RecentNotesWidget.kt`, `RecentNotesWidgetService.kt` | **P1** |
| **ENH-03** | **Độ tương phản chữ thông minh theo màu nền thẻ (Dynamic Contrast Text)** | Tự động tính toán độ sáng (Luminance) của màu ghi chú được chọn để đổi màu chữ tiêu đề/nội dung sang đen hoặc trắng tương ứng, khắc phục tình trạng khó đọc khi chọn màu vàng/cam trên Dark theme. | XS | `NoteListVH.kt`, `TimelineVH.kt`, `EditFrm.kt` | **P1** |
| **ENH-04** | **Nâng cấp Time Travel Slider (Live Diff Highlighting)** | Khi kéo thanh trượt Time Travel trong lịch sử ghi chú, hiển thị highlight màu xanh cho các ký tự mới thêm và màu đỏ gạch ngang cho các ký tự bị xoá so với bản ghi hiện tại. | M | `EditVM.kt`, `EditFrm.kt`, `TimeTravelHelper.kt` | **P1** |
| **ENH-05** | **Tìm kiếm FTS5 thông minh (Fuzzy Search & Không dấu)** | Cải tiến `SearchQueryCleaner` và FTS5 để hỗ trợ tìm kiếm tiếng Việt không dấu (ví dụ gõ "ghi chu" tìm ra "Ghi chú") và xử lý từ khoá gần đúng khi gõ sai 1 ký tự. | M | `SearchQueryCleaner.kt`, `NotesDao.kt`, `SearchVM.kt` | **P1** |
| **ENH-06** | **Tự động lưu trễ thông minh (Debounced Auto-Save)** | Áp dụng debounce 400ms khi gõ văn bản liên tục để giảm tải ghi Room DB I/O, đồng thời đảm bảo flush ngay lập tức khi người dùng bấm Back hoặc chuyển ứng dụng (`onPause`). | S | `EditVM.kt`, `EditFrm.kt` | **P1** |
| **ENH-07** | **Nhắc nhở lặp nâng cao theo thói quen (Contextual Smart Reminders)** | Thêm các preset nhắc nhở nhanh: "Tối nay (20:00)", "Sáng mai (08:00)", "Đầu tuần tới (Thứ Hai 09:00)", "Cuối tuần (Thứ Bảy 10:00)" kèm tính toán múi giờ chuẩn xác. | S | `ReminderVM.kt`, `ReminderDlg.kt`, `DlgReminderBinding` | **P1** |
| **ENH-08** | **Cải tiến Animation Swipe-to-Action & Haptic Feedback** | Thêm phản hồi rung nhẹ (Haptic Click) khi vuốt thẻ ghi chú đạt ngưỡng threshold (60%), kèm icon chuyển động mượt mà hơn. | XS | `SwipeTouchHelperCallback.kt`, `NoteAdt.kt` | **P2** |
| **ENH-09** | **Tự động sao lưu định kỳ nền (WorkManager Auto-Export)** | Thay vì chỉ kích hoạt auto-export khi người dùng mở app, tích hợp `WorkManager PeriodicWork` chạy ngầm mỗi 24 giờ để tự động đồng bộ file JSON backup khi cắm sạc. | M | `DefaultNotesRepository.kt`, `SettingsVM.kt`, `RApp.kt` | **P2** |
| **ENH-10** | **Thu gọn / Mở rộng nhóm ngày trong Timeline (Collapsible Timeline Groups)** | Cho phép người dùng chạm vào header ngày tháng ("17 Aug 2026") để đóng/mở danh sách ghi chú của ngày đó, giúp quản lý nhật ký gọn gàng hơn. | S | `TimelineVH.kt`, `NoteVM.kt`, `NoteAdt.kt` | **P2** |

---

## 3. NHÓM 3: NEW FEATURE - TÍNH NĂNG MỚI TIÊU CHUẨN

| ID | Tiêu đề tính năng | Mô tả ngắn gọn | Effort | Files / Class liên quan | Priority |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FEAT-01** | **Ghi âm & Đính kèm Voice Note (Audio Recording)** | Cho phép ghi âm trực tiếp ngay trong ghi chú, hiển thị thanh phát âm thanh dạng sóng (Waveform Player) có nút Play/Pause và thanh tiến trình. | L | Entity `NoteAudio`, `AudioRecorderHelper.kt`, `EditFrm.kt` | **P0** |
| **FEAT-02** | **Đính kèm Hình ảnh vào Ghi chú (Image Attachments)** | Cho phép chụp ảnh từ Camera hoặc chọn từ Thư viện để đính kèm vào ghi chú, hiển thị dạng lưới ảnh co giãn (Image Grid) kèm tính năng xem phóng to full màn hình. | L | Entity `NoteImage`, `ImageGridAdapter.kt`, `EditFrm.kt` | **P0** |
| **FEAT-03** | **Ghim ghi chú lên thanh thông báo hệ thống (Sticky Notification)** | Cho phép người dùng chọn "Ghim lên thông báo" để giữ nội dung ghi chú quan trọng luôn hiển thị trên Android Notification Center, không bị trôi mất. | S | `NotificationHelper.kt`, `MainAct.kt`, `EditFrm.kt` | **P1** |
| **FEAT-04** | **Trình soạn thảo Markdown Rich Preview (Markdown Support)** | Hỗ trợ định dạng văn bản Markdown trực quan: Tiêu đề H1-H3, **In đậm**, *In nghiêng*, `Code Block`, Trích dẫn `>` và đường kẻ ngang, có thể chuyển đổi giữa chế độ Soạn thảo và Xem trước. | M | `MarkdownParser.kt`, `EditFrm.kt`, `v_item_edit_text.xml` | **P1** |
| **FEAT-05** | **Khóa ứng dụng độc lập với mã PIN dự phòng (Custom Vault PIN)** | Cho phép người dùng đặt mã PIN 4-6 số riêng cho ứng dụng và cho các ghi chú bị khoá, hoạt động độc lập ngay cả khi thiết bị không cài vân tay/Face Unlock. | M | `PinLockActivity.kt`, `PinHelper.kt`, `BiometricHelper.kt` | **P1** |
| **FEAT-06** | **Tự động gắn Tag thông minh bằng `#tag` (Inline Tagging)** | Khi người dùng gõ ký tự `#` trong nội dung ghi chú, app tự động gợi ý danh sách nhãn hiện có và tự động liên kết ghi chú với nhãn đó khi lưu. | S | `EditVM.kt`, `TagSuggestionPopup.kt`, `LabelsRepository.kt` | **P1** |
| **FEAT-07** | **Bộ mẫu ghi chú có sẵn (Note Templates)** | Cung cấp sẵn các mẫu ghi chú thông dụng khi tạo mới: "Biên bản cuộc họp", "Kế hoạch tuần", "Nhật ký biết ơn", "Danh sách mua sắm", "Ma trận Eisenhower". | S | `TemplateManager.kt`, `TemplatePickerDlg.kt`, `HomeFrm.kt` | **P2** |
| **FEAT-08** | **Tùy chỉnh thời gian tự dọn Thùng rác (Custom Trash Retention)** | Cho phép người dùng chọn thời gian tự động xóa vĩnh viễn ghi chú trong thùng rác: Sau 7 ngày, 14 ngày, 30 ngày, 60 ngày hoặc Không bao giờ. | S | `PrefsManager.kt`, `SettingsFrm.kt`, `DefaultNotesRepository.kt` | **P2** |
| **FEAT-09** | **Nhận dạng chữ viết từ hình ảnh (OCR Text Recognition)** | Tích hợp Google ML Kit Text Recognition on-device để tự động quét chữ từ ảnh chụp hoá đơn/tài liệu và chèn trực tiếp vào nội dung văn bản của ghi chú. | M | `OcrHelper.kt`, `EditFrm.kt`, `build.gradle` | **P2** |
| **FEAT-10** | **Shortcut tạo ghi chú nhanh trên Quick Settings Tile** | Bổ sung 1 Tile trên thanh Cài đặt nhanh của Android (Quick Settings) để bấm mở ngay cửa sổ popup ghi chú nhanh mà không cần mở toàn bộ app. | S | `QuickNoteTileService.kt`, `AndroidManifest.xml` | **P2** |

---

## 4. NHÓM 4: IDEA - Ý TƯỞNG ĐỘT PHÁ & THỬ NGHIỆM

| ID | Ý tưởng | Mô tả & Định hướng nghiên cứu | Đánh giá khả thi | Priority |
| :--- | :--- | :--- | :--- | :--- |
| **IDEA-01** | **Tóm tắt & Tạo Action Items bằng On-Device AI (Gemini Nano)** | Tích hợp AI trên thiết bị thông qua Android AICore / Gemini Nano để tự động tạo bản tóm tắt 3 ý chính và trích xuất danh sách việc cần làm (action items) từ văn bản dài mà không gửi dữ liệu lên cloud. | Trung bình (Cần Android 14+ và thiết bị hỗ trợ NPU) | **P2** |
| **IDEA-02** | **Vẽ tay tự do & Hỗ trợ Stylus Canvas (Freehand Sketch)** | Thêm lớp vẽ Canvas tự do hỗ trợ bút cảm ứng (S-Pen / Active Stylus) với cảm ứng lực, màu mực và tẩy xoá vector ngay trong lòng ghi chú văn bản. | Cao (Có thể dùng thư viện Ink / Canvas custom) | **P2** |
| **IDEA-03** | **Liên kết 2 chiều Wiki-Links (`[[Note]]`) & Biểu đồ mạng (Graph View)** | Hỗ trợ cú pháp `[[Tên ghi chú]]` để liên kết giữa các ghi chú với nhau, hiển thị bản đồ tri thức dạng mạng nhện tương tác 2D (Knowledge Graph) tương tự Obsidian Mobile. | Cao (Cần thuật toán Force-Directed Graph trên Canvas) | **P2** |
| **IDEA-04** | **Chuyển giọng nói thành Sơ đồ tư duy (Voice-to-Mindmap)** | Ghi âm bài phát biểu hoặc ý tưởng, sau đó tự động phân nhánh thành mindmap cây phân cấp trực quan dựa trên các từ nối và cấu trúc ngữ nghĩa. | Thấp (Cần nghiên cứu NLP parse ngữ nghĩa sâu) | **P2** |
| **IDEA-05** | **Đồng bộ cục bộ ngang hàng P2P qua Wi-Fi / WebRTC không cần Cloud** | Cho phép 2 thiết bị (điện thoại và máy tính/tablet) trong cùng mạng Wi-Fi tự động tìm thấy nhau qua mDNS/NSD và đồng bộ cơ sở dữ liệu đã mã hóa trực tiếp mà không cần server trung gian. | Cao (Khả thi bằng WebSockets cục bộ / Nearby Connections) | **P2** |
| **IDEA-06** | **Tìm kiếm ngữ nghĩa bằng Local Vector Embeddings (Semantic Search)** | Nhúng model embedding on-device cực nhẹ (như MiniLM-L6) và SQLite-Vec để tìm kiếm ghi chú theo ý nghĩa (ví dụ gõ "mua hoa quả" tìm ra ghi chú có chữ "táo, chuối, cam"). | Trung bình (Cần tối ưu dung lượng RAM và model) | **P2** |
| **IDEA-07** | **Chia sẻ ghi chú 1 chạm qua NFC Beam (NFC Encrypted Transfer)** | Chạm lưng 2 điện thoại để bắn ngay 1 ghi chú được mã hoá sang máy đối phương qua chuẩn Android NFC / Wi-Fi Direct. | Rất cao (Có sẵn API Android NFC tiêu chuẩn) | **P2** |
| **IDEA-08** | **Chế độ Ôn tập ngắt quãng Flashcard (Spaced Repetition Mode)** | Tự động biến các câu hỏi / gạch đầu dòng trong ghi chú thành các thẻ Flashcard để người dùng ôn tập kiến thức hàng ngày theo thuật toán SuperMemo SM-2. | Rất cao (Dễ dàng tích hợp vào cấu trúc Reminder hiện có) | **P2** |

---

## 5. NHÓM 5: EXCLUSIVE - TÍNH NĂNG ĐỘC QUYỀN & LỢI THẾ CẠNH TRANH
*(Các tính năng tạo sự khác biệt vượt trội hoàn toàn so với Google Keep, Samsung Notes, Notion mobile, Obsidian mobile)*

```
                   ┌─────────────────────────────────────────┐
                   │  ANOTHER NOTES - CORE COMPETITIVE MOAT │
                   └────────────────────┬────────────────────┘
                                        │
        ┌───────────────────┬───────────┴───────────┬───────────────────┐
        ▼                   ▼                       ▼                   ▼
 ┌─────────────┐     ┌─────────────┐         ┌─────────────┐     ┌─────────────┐
 │ EXCL-01     │     │ EXCL-02     │         │ EXCL-03     │     │ EXCL-04     │
 │ Time-Travel │     │ Decoy Vault │         │ Acoustic    │     │ Geofence    │
 │ Replay Diff │     │ Panic PIN   │         │ Soundscapes │     │ Smart Board │
 └─────────────┘     └─────────────┘         └─────────────┘     └─────────────┘
```

### EXCL-01: Time-Travel Chrono-Slider with Live Visual Diff & Ghost Replay
- **Ý tưởng khác biệt:** Các app khác (Google Keep, Samsung Notes) chỉ lưu bản ghi cuối cùng hoặc lịch sử dạng danh sách text khô khan. Another Notes đã có nền tảng `note_history` và `TimeTravelBottomSheet`. Chúng ta nâng cấp nó thành cỗ máy **"Xem lại quá trình suy nghĩ (Cognitive Replay)"**:
- **Cơ chế hoạt động:**
  - Thanh trượt mượt mà cho phép tua lại từng lần chỉnh sửa của ghi chú theo trục thời gian.
  - Khi kéo slider, màn hình hiển thị hoạt ảnh dạng "gõ phím ma thuật (Ghost Typing)" thể hiện chính xác những đoạn văn đã được viết thêm (màu xanh lá) hoặc xoá đi (màu đỏ gạch ngang).
  - Có nút "Phục hồi về thời điểm này (Revert to Snapshot)" với 1 chạm.
- **Giá trị cạnh tranh:** Cực kỳ hữu ích cho nhà văn, nhà báo, lập trình viên, học sinh theo dõi tiến trình tư duy ý tưởng mà không app ghi chú di động nào trên thị trường có được.
- **Effort:** `M` | **Priority:** `P0`
- **Files liên quan:** `TimeTravelHelper.kt`, `EditVM.kt`, `EditFrm.kt`, `NoteHistoryDao.kt`.

---

### EXCL-02: Decoy PIN & Camouflage Panic Vault (Két sắt nguỵ trang 2 tầng)
- **Ý tưởng khác biệt:** Khi bị người khác ép mở khoá app ghi chú (hoặc khi cho bạn bè mượn máy), tính năng khoá thông thường sẽ lộ ra các ghi chú nhạy cảm. Another Notes cung cấp chế độ **Bảo mật 2 tầng thực sự**:
- **Cơ chế hoạt động:**
  - **Mã PIN thật (Master PIN):** Mở toàn bộ ghi chú thật, bao gồm cả ghi chú bí mật và nhật ký tài chính.
  - **Mã PIN nguỵ trang (Decoy PIN):** Khi nhập mã PIN này, app vẫn mở ra bình thường nhưng nạp một cơ sở dữ liệu ảo chỉ chứa các ghi chú mua sắm thông thường, hoàn toàn không có bất kỳ dấu vết nào của các ghi chú nhạy cảm.
  - **Panic Gesture (Úp máy để khoá tức thì):** Khi đang mở ghi chú nhạy cảm, chỉ cần úp màn hình điện thoại xuống mặt bàn hoặc lắc mạnh, app tự động thoát về Home và xoá sạch RAM đệm.
- **Giá trị cạnh tranh:** Bảo vệ quyền riêng tư tuyệt đối cho người dùng trong mọi tình huống thực tế đời thường.
- **Effort:** `M` | **Priority:** `P0`
- **Files liên quan:** `BiometricHelper.kt`, `PrefsManager.kt`, `MainAct.kt`, `NotesRepository.kt`.

---

### EXCL-03: Acoustic Focus Atmosphere & Dynamic Soundscapes (Không gian âm thanh tập trung)
- **Ý tưởng khác biệt:** Biến ứng dụng ghi chú thành một trạm tập trung tâm trí (Deep Work & Flow State) kết hợp thị giác và thính giác.
- **Cơ chế hoạt động:**
  - Tích hợp bộ tạo âm thanh nền chất lượng cao (Ambient Sound Engine): Tiếng mưa rơi trên mái tôn, tiếng quán cafe Paris, tiếng sóng biển ban đêm, tiếng củi cháy tí tách, tiếng ồn trắng (White Noise/Binaural Beats).
  - Âm thanh được **đồng bộ theo Theme màu** của ghi chú (ví dụ: ghi chú màu Xanh ngọc phát tiếng mưa nhẹ; ghi chú màu Cam phát tiếng củi lửa ấm áp).
  - Chế độ Focus Mode ẩn toàn bộ toolbar, chỉ còn chữ viết và nhịp điệu âm thanh thư giãn.
- **Giá trị cạnh tranh:** Biến Another Notes thành công cụ viết lách gây nghiện, vượt trội hoàn toàn sự đơn điệu của Google Keep hay Notion.
- **Effort:** `M` | **Priority:** `P1`
- **Files liên quan:** `SoundscapeManager.kt`, `EditFrm.kt`, `AudioPlayerHelper.kt`.

---

### EXCL-04: Context-Aware Smart Geofence & Dynamic Location Board (Bảng ghi chú theo định vị)
- **Ý tưởng khác biệt:** Nhắc nhở theo giờ thường bị bỏ qua nếu người dùng chưa đến địa điểm cần làm việc. Another Notes kết hợp Geofencing thông minh không tốn pin.
- **Cơ chế hoạt động:**
  - Gán vị trí cho ghi chú (ví dụ: "Siêu thị Go", "Văn phòng công ty", "Nhà riêng").
  - Khi điện thoại đi vào bán kính 150m của địa điểm, Widget trên màn hình chính và thông báo ngầm sẽ tự động đẩy ghi chú tương ứng lên đầu danh sách (ví dụ: đến siêu thị tự hiện danh sách đồ cần mua; đến văn phòng tự hiện checklist công việc hôm nay).
  - Tự động ẩn đi khi rời khỏi địa điểm.
- **Giá trị cạnh tranh:** Trải nghiệm ghi chú tự động hoá 100%, giải quyết triệt để vấn đề quên việc khi di chuyển.
- **Effort:** `L` | **Priority:** `P1`
- **Files liên quan:** `GeofenceManager.kt`, `LocationReceiver.kt`, `RecentNotesWidget.kt`, `Note.kt`.

---

### EXCL-05: Zero-Cloud P2P Encrypted Mesh & QR Air-Drop (Đồng bộ ngang hàng không dấu vết)
- **Ý tưởng khác biệt:** Người dùng ngày càng e ngại việc lưu trữ dữ liệu cá nhân trên server của bên thứ ba (Google Drive, Dropbox, Notion Server).
- **Cơ chế hoạt động:**
  - Tạo mạng lưới đồng bộ cục bộ Direct P2P: Chuyển dữ liệu giữa 2 thiết bị qua mạng nội bộ hoặc qua chuỗi mã QR động tốc độ cao (Animated QR Stream).
  - Mọi gói tin truyền đi đều được mã hoá end-to-end bằng khoá AES-256-GCM được sinh tức thời.
  - Hoàn toàn không có máy chủ lưu vết, không yêu cầu đăng ký tài khoản email.
- **Giá trị cạnh tranh:** Đạt chuẩn bảo mật tối thượng cho người dùng coi trọng quyền riêng tư (Privacy-First Enthusiasts).
- **Effort:** `L` | **Priority:** `P2`
- **Files liên quan:** `P2pSyncManager.kt`, `DefaultJsonManager.kt`, `SettingsFrm.kt`.

---

### EXCL-06: Kinetic Physics Drag & Tactile Shake-to-Disintegrate (Tương tác vật lý sống động)
- **Ý tưởng khác biệt:** Đưa chuyển động vật lý đàn hồi (Spring Dynamics) và phản hồi xúc giác (Haptics) vào từng thao tác chạm nhỏ nhất.
- **Cơ chế hoạt động:**
  - **Kinetic Reordering:** Kéo thả sắp xếp ghi chú và checklist với cảm giác có trọng lượng, gia tốc và độ nảy tự nhiên.
  - **Shake-to-Disintegrate (Lắc để xoá tan biến):** Khi chọn xoá ghi chú trong Thùng rác, hiệu ứng hạt bụi phân rã (Particle Disintegration) xuất hiện kèm nhịp rung haptic giảm dần, tạo cảm giác dọn dẹp cực kỳ thoả mãn thị giác.
- **Giá trị cạnh tranh:** Thiết kế giao diện cao cấp, xóa bỏ hoàn toàn cảm giác của một ứng dụng CRUD đơn điệu.
- **Effort:** `S` | **Priority:** `P2`
- **Files liên quan:** `SpringItemAnimator.kt`, `NoteAdt.kt`, `EditFrm.kt`.

---

## 6. KẾ HOẠCH TRIỂN KHAI ĐỀ XUẤT (SPRINT ROADMAP)

```
 Sprint 1 (Tuần 1-2): ỔN ĐỊNH CỐT LÕI (STABILIZATION)
 ├── Fix toàn bộ 15 lỗi Nhóm 1 (FIX-01 -> FIX-15), đặc biệt FIX-01, FIX-02, FIX-03, FIX-04
 └── Triển khai ENH-01 (Checklist Bulk Actions) & ENH-03 (Dynamic Contrast Text)

 Sprint 2 (Tuần 3-4): NÂNG TẦM TRẢI NGHIỆM (CORE ENHANCEMENT)
 ├── Triển khai FEAT-01 (Audio Recording) & FEAT-02 (Image Attachments)
 ├── Nâng cấp ENH-04 (Time Travel Slider Diff) & ENH-05 (Fuzzy Vietnamese Search)
 └── Triển khai FEAT-03 (Sticky Notification) & FEAT-05 (Vault PIN Lock)

 Sprint 3 (Tuần 5-6): BỨT PHÁ KHÁC BIỆT (KILLER DIFFERENTIATION)
 ├── Triển khai độc quyền EXCL-01 (Time-Travel Chrono Ghost Replay)
 ├── Triển khai độc quyền EXCL-02 (Decoy PIN & Camouflage Panic Vault)
 └── Triển khai độc quyền EXCL-03 (Acoustic Focus Soundscapes)
```

---
*Tài liệu được sinh tự động bởi agy CLI - Bản quyền phân tích thuộc về Đội ngũ Kỹ thuật.*
