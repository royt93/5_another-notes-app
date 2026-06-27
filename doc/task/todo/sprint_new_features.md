# Sprint New Features — Todo 📋

> **Created:** 2026-06-27 | **Status:** 📋 Planning & Prioritizing
> Breakdown of remaining exclusive features into actionable technical tasks.

---

## F-03: Note Locking với Biometric (Vân tay/Khuôn mặt)

**Mô tả:** Khóa ghi chú riêng tư. Khi bị khóa, ghi chú chỉ hiển thị tiêu đề và biểu tượng khóa trên màn hình danh sách, nội dung ghi chú sẽ được ẩn đi và chỉ truy cập được sau khi xác thực sinh trắc học thành công.

*   **Task 1: Cập nhật Model & Database Migration (Room 6 -> 7)**
    *   Thêm trường `isLocked: Boolean = false` vào [Note.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/entity/Note.kt).
    *   Tạo bản nâng cấp Database trong [NotesDb.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/NotesDb.kt) và thêm câu lệnh `ALTER TABLE notes ADD COLUMN is_locked INTEGER NOT NULL DEFAULT 0` vào migration.
    *   Tự động xuất schema JSON Room v7.
*   **Task 2: Tích hợp BiometricPrompt API**
    *   Tạo Helper hoặc Extension để gọi `BiometricPrompt` hiển thị hộp thoại xác thực hệ thống.
    *   Kiểm tra tính khả dụng của cảm biến sinh trắc học trước khi hiển thị.
*   **Task 3: Thay đổi UI ở danh sách (Home screen)**
    *   Cập nhật `NoteListVH` để ẩn nội dung và hiển thị placeholder ổ khóa nếu ghi chú bị khóa.
    *   Ngăn không cho xem trước (preview) nội dung nhạy cảm của ghi chú.
*   **Task 4: Thêm cơ chế bảo vệ khi vào màn hình soạn thảo**
    *   Trong `HomeFrm` khi click vào ghi chú bị khóa, yêu cầu xác thực vân tay/khuôn mặt trước khi thực hiện điều hướng sang `EditFrm`.
    *   Trong `EditFrm`, thêm nút menu "Khoá ghi chú" (Lock note) để người dùng khóa/mở khóa ghi chú hiện tại.

*   **Files:** [Note.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/entity/Note.kt), [NotesDb.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/NotesDb.kt), `NoteListVH.kt`, `EditFrm.kt`, `EditVM.kt`, `HomeFrm.kt`
*   **Effort:** M (3-5 ngày)

---

## F-05: Smart Duplicate Detection (Phát hiện trùng lặp thông minh)

**Mô tả:** Tự động phát hiện nếu người dùng đang viết một ghi chú có nội dung cực kỳ giống với ghi chú cũ (độ tương đồng > 80%) và hiển thị cảnh báo để tránh trùng lặp thông tin.

*   **Task 1: Tạo bộ lọc độ tương đồng (Text Similarity Helper)**
    *   Tạo file `SimilarityHelper.kt` chứa thuật toán so sánh văn bản (như Jaccard Similarity hoặc Levenshtein Distance).
*   **Task 2: Lắng nghe và so sánh trong Background Thread**
    *   Trong `EditVM`, sử dụng Flow `debounce(2000)` để theo dõi nội dung người dùng gõ.
    *   Khi dừng gõ 2 giây, thực hiện so sánh song song trong background thread (`Dispatchers.Default`) với tất cả ghi chú hiện có trong database (loại trừ note hiện tại).
*   **Task 3: Triển khai UI cảnh báo người dùng**
    *   Thêm một layout nhỏ dạng Banner hoặc Card cảnh báo ở trên bàn phím (`f_edit.xml`).
    *   Khi phát hiện trùng lặp > 80%, hiển thị cảnh báo: "Bạn có ghi chú tương tự: [Tiêu đề]. Nhấp để xem."
    *   Nhấp vào cảnh báo sẽ mở dialog cho xem trước ghi chú cũ.

*   **Files:** `SimilarityHelper.kt` (mới), `EditVM.kt`, `EditFrm.kt`, `f_edit.xml`
*   **Effort:** M (3-4 ngày)

---

## F-07: Shake to Undo (Lắc máy để hoàn tác)

**Mô tả:** Khi người dùng vừa thực hiện hành động xóa hoặc lưu trữ ghi chú, họ có thể lắc nhẹ điện thoại trong vòng 5 giây tiếp theo để hoàn tác hành động đó một cách nhanh chóng.

*   **Task 1: Xây dựng ShakeDetector**
    *   Tạo class `ShakeDetector` triển khai `SensorEventListener` để tính toán gia tốc dựa trên cảm biến `Sensor.TYPE_ACCELEROMETER`.
    *   Xác định ngưỡng rung lắc phù hợp để tránh kích hoạt nhầm khi người dùng đi bộ hoặc chuyển động nhẹ.
*   **Task 2: Lắng nghe chuyển động trong Home Screen**
    *   Khi người dùng xóa/lưu trữ ghi chú, đăng ký lắng nghe cảm biến chuyển động trong 5 giây.
    *   Nếu phát hiện chuyển động lắc máy, gọi hàm `undo()` tương tự như khi click nút Undo trên Snackbar.
    *   Tự động hủy đăng ký cảm biến (unregister) sau 5 giây hoặc sau khi đã hoàn tác để tối ưu pin.

*   **Files:** `ShakeDetector.kt` (mới), `HomeFrm.kt`, `MainAct.kt`
*   **Effort:** S (1-2 ngày)

---

## F-08: Note Reading Mode (Chế độ đọc ghi chú)

**Mô tả:** Chế độ xem ghi chú không chỉnh sửa, ẩn toàn bộ UI điều khiển và bàn phím ảo, tăng kích thước font chữ giúp người dùng dễ dàng xem lại ghi chú dài mà không vô tình chỉnh sửa.

*   **Task 1: Thiết kế giao diện chuyển đổi**
    *   Thêm một biểu tượng nút "Reading Mode" trên thanh công cụ của `EditFrm`.
*   **Task 2: Vô hiệu hóa tính năng nhập liệu**
    *   Khi bật chế độ đọc, ẩn bàn phím ảo.
    *   Set thuộc tính `inputType = InputType.TYPE_NULL` hoặc ẩn trỏ chuột và chặn focus trên EditText của Tiêu đề và Nội dung ghi chú.
*   **Task 3: Thay đổi định dạng Text dễ đọc**
    *   Tăng kích thước font chữ (Ví dụ: 18sp) và tăng giãn dòng (`lineSpacingMultiplier = 1.3f`).
    *   Chuyển đổi mượt mà giữa 2 trạng thái bằng `TransitionManager.beginDelayedTransition()`.

*   **Files:** `EditFrm.kt`, `f_edit.xml`
*   **Effort:** S (1-2 ngày)

---

## F-09: Note Export to PDF / Image (Xuất ghi chú PDF & Ảnh)

**Mô tả:** Cho phép xuất nội dung ghi chú thành tài liệu PDF chính thức hoặc một bức ảnh PNG định dạng đẹp mắt để dễ dàng lưu trữ và chia sẻ ra bên ngoài.

*   **Task 1: Triển khai vẽ giao diện lên Canvas**
    *   Tạo `ExportHelper.kt` phụ trách render nội dung text/checklist của ghi chú lên một đối tượng `Canvas`.
*   **Task 2: Tạo File PDF và File Ảnh**
    *   Sử dụng API Android native `PdfDocument` để xuất nội dung từ Canvas ra file `.pdf`.
    *   Sử dụng Bitmap lưu từ Canvas để ghi thành file ảnh `.png`.
*   **Task 3: Chia sẻ thông qua FileProvider**
    *   Lưu trữ file tạm thời vào thư mục cache của ứng dụng.
    *   Sử dụng `FileProvider` để tạo URI an toàn cho file.
    *   Khởi chạy `Intent(Intent.ACTION_SEND)` gửi file đến các ứng dụng khác (Zalo, Gmail, Messenger...).

*   **Files:** `ExportHelper.kt` (mới), `EditFrm.kt`, `EditVM.kt`
*   **Effort:** M (3-4 ngày)
