# Detailed Breakdown: Features F-11 to F-15 📋

> **Status:** 📋 Planning & Prioritizing (Todo)

This document provides a highly detailed technical breakdown and implementation plan for the proposed exclusive features (F-11 through F-15).

---

## F-11: Time-Travel Note Slider (Thanh trượt du hành thời gian)

**Mô tả:** Xem lại và khôi phục lịch sử chỉnh sửa ghi chú thông qua thanh trượt Slider trực quan thay vì chỉ dùng Undo/Redo.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Tạo Database Model & DAO**
    *   Tạo entity [NoteHistory.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/entity/NoteHistory.kt) lưu lịch sử ghi chú:
        ```kotlin
        @Entity(
            tableName = "note_history",
            foreignKeys = [ForeignKey(
                entity = Note::class,
                parentColumns = ["id"],
                childColumns = ["noteId"],
                onDelete = ForeignKey.CASCADE
            )]
        )
        data class NoteHistory(
            @PrimaryKey(autoGenerate = true) val id: Long = 0,
            val noteId: Long,
            val title: String,
            val content: String,
            val timestamp: Long = System.currentTimeMillis()
        )
        ```
    *   Tạo `NoteHistoryDao` và tích hợp vào [NotesDb.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/NotesDb.kt).
    *   Room migration: Thêm bảng `note_history` vào database (Room v7 -> v8).

*   [ ] **Task 2: Cơ chế tự động lưu snapshot trong EditViewModel**
    *   Trong `EditVM.kt`, lắng nghe thay đổi của Tiêu đề và Nội dung ghi chú.
    *   Sử dụng Coroutine Flow with `debounce(10000)` (mỗi 10 giây không có hoạt động gõ máy) để tạo một bản lưu lịch sử mới nếu nội dung thay đổi đáng kể so với bản lưu trước đó (ví dụ: delta > 10 ký tự).
    *   Giới hạn số lượng bản ghi lịch sử tối đa (ví dụ: tối đa 30 bản ghi/note) bằng cách xoá các bản ghi cũ hơn trong database.

*   [ ] **Task 3: Thiết kế giao diện Time-Travel UI**
    *   Trong `f_edit.xml`, thêm một cụm điều khiển ẩn ở thanh công cụ dưới hoặc trong một bottom sheet:
        - `Slider`: Dùng để kéo qua lại các phiên bản. Mức độ từ `0` đến `N-1` tương ứng với số lượng snapshot lịch sử.
        - `TextView` hiển thị thời gian lưu snapshot (ví dụ: "Được lưu 2 giờ trước", "Hôm qua lúc 15:30").
        - Nút `Hủy (Cancel)` để tắt chế độ preview lịch sử và quay về nội dung hiện tại.
        - Nút `Khôi phục (Restore)` để chính thức thay thế nội dung hiện tại bằng nội dung lịch sử đang xem.

*   [ ] **Task 4: Logic hiển thị Preview & Restore trong EditFrm**
    *   Khi người dùng kéo Slider, tạm thời vô hiệu hóa chế độ chỉnh sửa của EditText (hoặc set `enabled = false`).
    *   Hiển thị text của snapshot tương ứng lên EditText Tiêu đề và Nội dung.
    *   Nếu nhấn Restore, cập nhật giá trị hiện tại của Note trong VM và bật lại chỉnh sửa.
    *   Nếu nhấn Cancel, khôi phục lại nội dung nháp chưa lưu trước khi kéo Slider.

---

## F-12: Auto-Theme & Color Association (Tự động tô màu theo nội dung)

**Mô tả:** Phân tích văn bản thời gian thực khi người dùng gõ để gợi ý/áp dụng màu nền ghi chú thích hợp với chủ đề hoặc cảm xúc.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Xây dựng bộ lọc và nhận diện từ khóa (Sentiment/Theme Matcher)**
    *   Tạo class `ThemeAssociationHelper.kt` chứa cơ chế phân loại (Heuristics):
        - **Work / Urgent (Gấp/Công việc):** Các từ khóa như `lịch họp`, `deadline`, `khẩn cấp`, `quan trọng`, `meeting`, `task` -> gợi ý màu Đỏ/Cam nhạt (như màu Coral/Orange).
        - **Idea / Creative (Ý tưởng sáng tạo):** Các từ khóa như `ý tưởng`, `idea`, `sáng tạo`, `nghiên cứu`, `sáng kiến` -> gợi ý màu Vàng nhạt.
        - **Relax / Health (Sức khỏe/Thư giãn):** Các từ khóa như `gym`, `thiền`, `spa`, `du lịch`, `nghỉ ngơi`, `yoga` -> gợi ý màu Xanh lá nhạt.
        - **Finance / Shopping (Tài chính/Mua sắm):** Các từ khóa như `mua sắm`, `chi tiêu`, `lương`, `hóa đơn`, `shopping`, `money` -> gợi ý màu Xanh teal/Xanh dương nhạt.
    *   Hỗ trợ cả tiếng Anh và tiếng Việt.

*   [ ] **Task 2: Phân tích Background Thread & Gợi ý UI**
    *   Trong `EditVM.kt`, sử dụng Flow `debounce(2500)` để theo dõi nội dung người dùng gõ.
    *   Chạy phương thức phân tích từ khóa trên `Dispatchers.Default` để tránh gây giật lag UI (main thread).
    *   Khi phát hiện chủ đề tương ứng, đẩy một trạng thái đề xuất màu nền (`suggestedColor: LiveData<Int?>`).

*   [ ] **Task 3: Thiết kế UI Gợi ý Màu Sắc**
    *   Trong `f_edit.xml`, thêm một banner nhỏ tự động hiển thị phía trên bàn phím:
        - Text: "Gợi ý đổi màu nền ghi chú sang [Tên màu] để phù hợp với nội dung?"
        - Nút hành động: "Áp dụng" (Apply) và "Bỏ qua" (Dismiss).
    *   Khi nhấn "Áp dụng", gọi hàm đổi màu nền ghi chú hiện tại kèm hiệu ứng `ValueAnimator` chuyển đổi màu nền (Background color cross-fade) mượt mà trong 300ms.

---

## F-13: Interactive Note Link Map (Sơ đồ liên kết ghi chú tương tác)

**Mô tả:** Hiển thị sơ đồ đồ thị 2D mạng lưới liên kết giữa các ghi chú dựa trên cú pháp liên kết `[[Tiêu đề ghi chú]]`.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Thiết lập Parser liên kết**
    *   Xây dựng regex parser tìm các chuỗi dạng `\[\[(.*?)\]\]` trong nội dung của ghi chú.
    *   Trong `NotesRepository.kt`, tạo phương thức để quét và phân tích liên kết giữa các ghi chú:
        - Duyệt tất cả ghi chú, tìm các tiêu đề ghi chú khác được đề cập trong nội dung.
        - Xuất ra danh sách các cặp liên kết (Source Note ID -> Target Note ID).

*   [ ] **Task 2: Tạo Custom View đồ thị 2D (NoteGraphView)**
    *   Tạo class `NoteGraphView.kt` kế thừa `View`.
    *   Sử dụng thuật toán mô phỏng vật lý lực đẩy đơn giản (Force-Directed Graph) trong một Coroutine lặp để tính toán vị trí của các note (nodes) và đường liên kết (edges).
    *   Vẽ lên Canvas:
        - Vẽ các Node là các vòng tròn màu primary, chứa text là tiêu đề ghi chú thu nhỏ. Ghi chú được ghim (pinned) hoặc chứa nhiều liên kết có kích thước vòng tròn lớn hơn.
        - Vẽ các đường line (edges) liên kết giữa các node, có mũi tên chỉ hướng liên kết.
    *   Implement các cử chỉ vuốt, kéo thả (Drag node), phóng to/thu nhỏ (Pinch to zoom) sử dụng `GestureDetector` và `ScaleGestureDetector`.

*   [ ] **Task 3: Tạo màn hình Map View (GraphFrm)**
    *   Tạo `GraphFrm.kt` và layout tương ứng hiển thị toàn màn hình `NoteGraphView`.
    *   Thêm Toolbar có thanh tìm kiếm nhanh ghi chú để highlight node tương ứng trên sơ đồ.
    *   Sự kiện click vào node: Hiển thị popup preview nội dung ghi chú nhanh, hoặc double-click để điều hướng trực tiếp sang màn hình `EditFrm` của ghi chú đó.

---

## F-14: Pomodoro Writing Session (Chế độ viết Pomodoro tích hợp nhạc nền)

**Mô tả:** Đưa người dùng vào không gian tập trung viết ghi chú với bộ đếm ngược 25 phút Pomodoro kết hợp âm thanh thư giãn.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Tạo Pomodoro Service (Foreground Service)**
    *   Tạo `PomodoroService.kt` chạy dưới nền để quản lý bộ đếm ngược 25 phút nhằm đảm bảo tiến trình đếm giờ không bị Android OS tắt đi khi tắt màn hình.
    *   Tích hợp Custom Notification hiển thị thời gian còn lại (MM:SS) cùng nút "Tạm dừng" (Pause) và "Kết thúc" (Stop).

*   [ ] **Task 2: Trình phát nhạc nền thư giãn (Ambient Audio Player)**
    *   Chuẩn bị sẵn 3 asset âm thanh chất lượng tốt dạng loop (khoảng 30-60 giây mỗi file, nén dung lượng thấp):
        - `rain.mp3` (tiếng mưa rơi)
        - `lofi.mp3` (nhạc lofi không lời)
        - `forest.mp3` (tiếng rừng thông/chim hót)
    *   Sử dụng `MediaPlayer` trong service hỗ trợ phát lặp lại (looping = true) và điều chỉnh âm lượng độc lập với âm lượng hệ thống.

*   [ ] **Task 3: Giao diện soạn thảo Pomodoro trong EditFrm**
    *   Thêm tuỳ chọn "Bắt đầu Pomodoro" trong menu của màn hình soạn thảo.
    *   Khi bật, tự động kích hoạt `Focus Mode` (ẩn toàn bộ toolbar, status bar) đồng thời hiển thị một đồng hồ đếm ngược bán trong suốt ở một góc màn hình.
    *   Cung cấp một bảng điều khiển nhỏ:
        - Chọn loại nhạc nền (Lofi / Mưa / Rừng / Tắt nhạc).
        - Hiển thị thống kê trực tiếp: Số từ đã gõ được trong phiên Pomodoro hiện tại, tốc độ viết (từ/phút).
    *   Khi kết thúc 25 phút: Phát âm thanh chuông báo hiệu nhẹ và hiển thị Dialog chúc mừng hoàn thành phiên tập trung.

---

## F-15: Floating Quick-Note Bubble (Bong bóng ghi chú nổi đa nhiệm)

**Mô tả:** Bong bóng nổi di động (Chat head style) giúp ghi chép nhanh hoặc dán dữ liệu từ ứng dụng khác mà không cần mở trực tiếp app.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Cấp quyền & Setup Service**
    *   Thêm permission `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />` vào `AndroidManifest.xml`.
    *   Tạo `FloatingNoteService.kt` kế thừa `Service`.
    *   Viết logic kiểm tra và yêu cầu quyền vẽ trên ứng dụng khác (Draw over other apps) trước khi start service.

*   [ ] **Task 2: Thiết kế bong bóng nổi & Xử lý kéo thả**
    *   Tạo custom view bong bóng nổi hiển thị logo app dạng hình tròn nhỏ.
    *   Sử dụng `WindowManager.LayoutParams` loại `TYPE_APPLICATION_OVERLAY`.
    *   Implement sự kiện chạm `onTouchListener` để tính toán khoảng cách di chuyển khi người dùng kéo thả bong bóng nổi trên màn hình. Hỗ trợ tự động hít vào cạnh màn hình bên trái/phải khi người dùng thả tay.

*   [ ] **Task 3: Cửa sổ soạn thảo mini (Quick-Note Window)**
    *   Khi người dùng click vào bong bóng nổi, thu nhỏ/ẩn bubble và mở một Dialog/Window soạn thảo mini:
        - Tiêu đề EditText và nội dung ghi chú EditText gọn gàng.
        - Nút "Dán nhanh" (Quick Paste) tự động lấy text từ clipboard.
        - Nút "Lưu" (Save) và "Đóng" (Close).
    *   Khi nhấn Lưu, sử dụng coroutine ghi dữ liệu thẳng vào Room database thông qua `NotesRepository` (không cần giao tiếp thông qua Activity). Hiển thị hiệu ứng Toast thông báo thành công.
