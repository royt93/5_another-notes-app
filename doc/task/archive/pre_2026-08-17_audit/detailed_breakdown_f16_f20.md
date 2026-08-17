# Detailed Breakdown: Features F-16 to F-20 📋

> **Status:** 📋 Planning & Prioritizing (Todo)

This document contains the detailed technical specifications and task lists for next-level exclusive features (F-16 to F-20).

---

## F-16: Acoustic Note Atmosphere (Không gian ghi chú đa giác quan)

**Mô tả:** Tự động phát nhạc nền/tiếng động thư giãn phù hợp với không gian của ghi chú được chọn khi người dùng mở đọc hoặc viết.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Cập nhật Model & Database Migration**
    *   Thêm trường `soundscapeId: Int? = null` vào entity [Note.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/entity/Note.kt).
    *   Thêm câu lệnh SQL `ALTER TABLE notes ADD COLUMN soundscape_id INTEGER` vào Room migration.

*   [ ] **Task 2: Trình quản lý âm thanh (SoundscapePlayer)**
    *   Tạo class helper `SoundscapePlayer.kt` để quản lý `MediaPlayer` hoặc `ExoPlayer`.
    *   Đảm bảo hỗ trợ fade-in/fade-out âm thanh (giảm âm lượng từ từ khi tắt hoặc chuyển bài để tạo cảm giác dễ chịu).
    *   Quản lý danh sách tài nguyên âm thanh (Ví dụ: `R.raw.rain`, `R.raw.lofi`, `R.raw.fireplace`).

*   [ ] **Task 3: Kết hợp vào EditFrm Lifecycle**
    *   Trong `EditFrm.kt`:
        - `onStart()`: Đọc `soundscapeId` của ghi chú hiện tại từ ViewModel và yêu cầu `SoundscapePlayer` khởi chạy.
        - `onStop()`: Dừng phát nhạc để tránh phát nhạc khi app chạy ngầm hoặc người dùng chuyển màn hình.
    *   Tạo menu chọn Không gian âm thanh ở thanh công cụ dưới, hiển thị popup các biểu tượng (🌧️ Mưa, ☕ Quán cafe, 🔥 Lửa trại, 🔇 Tắt).

---

## F-17: Self-Destruct & Decoy Notes (Ghi chú tự hủy & Ngụy trang bảo mật)

**Mô tả:** Thiết lập thời gian tự động xóa ghi chú và hỗ trợ chế độ ngụy trang khi đăng nhập bằng mật khẩu giả.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Thiết lập tự hủy (Self-Destruct)**
    *   Thêm trường `selfDestructTime: Long? = null` (lưu thời điểm tự động xóa) vào entity [Note.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/entity/Note.kt).
    *   Tạo `SelfDestructWorker.kt` (sử dụng `WorkManager`) để định kỳ kiểm tra và xóa các ghi chú đã đến hạn tự hủy.
    *   Trong `EditFrm.kt`, thêm tùy chọn thiết lập bộ đếm ngược (ví dụ: Tự hủy sau 5 phút, 1 giờ, 1 ngày).

*   [ ] **Task 2: Cơ chế mã PIN ngụy trang (Decoy PIN)**
    *   Trong `PrefsManager.kt`, thêm trường lưu `decoy_pin_hash` bên cạnh `main_pin_hash`.
    *   Khi xác thực mật khẩu lúc mở ứng dụng:
        - Nếu người dùng nhập đúng PIN chính -> Cho phép tải toàn bộ dữ liệu ghi chú.
        - Nếu người dùng nhập PIN ngụy trang -> Đặt cờ `isDecoySession = true` trong ứng dụng.
    *   Trong `NotesRepository.kt` và `NoteDao.kt`, nếu `isDecoySession` là `true`, chỉ trả về danh sách ghi chú thông thường, tự động ẩn tất cả ghi chú có gắn cờ bảo mật hoặc nhạy cảm.

---

## F-18: Voice-to-Mindmap Canvas (Chuyển giọng nói thành sơ đồ tư duy)

**Mô tả:** Thu âm giọng nói, tự động trích xuất các từ khóa chính và tổ chức thành một sơ đồ tư duy 2D trực quan trên màn hình.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Nhận diện giọng nói và chuyển text**
    *   Sử dụng Android `SpeechRecognizer` để xử lý âm thanh đầu vào từ micro.
    *   Implement listener để lấy kết quả text theo thời gian thực và hiển thị lên màn hình.

*   [ ] **Task 2: Trích xuất cấu trúc phân cấp (Parser)**
    *   Tạo thuật toán phân tích text đơn giản dựa trên dấu câu hoặc từ khóa đặc biệt (Ví dụ: khi nói "nhánh một", "nhánh hai", "nhánh con" hoặc dựa trên khoảng nghỉ giọng nói 1.5 giây để chuyển thành node mới).
    *   Xây dựng mô hình dữ liệu cấu trúc cây `MindmapNode` (id, text, parentId, children).

*   [ ] **Task 3: Vẽ sơ đồ tư duy trên Canvas (MindmapCanvasView)**
    *   Tạo `MindmapCanvasView.kt` kế thừa `View` để render sơ đồ tư duy dạng mạng lưới:
        - Vẽ hình tròn đại diện cho các Node kèm text bên trong.
        - Tự động tính toán vị trí để các node không đè lên nhau (thuật toán Force-directed tree).
        - Hỗ trợ cuộn phóng to/thu nhỏ (pinch-zoom), nhấn giữ kéo node để thay đổi vị trí.

---

## F-19: Context-Aware Smart Widgets (Widget thông minh nhận diện ngữ cảnh)

**Mô tả:** Widget tự động cập nhật ghi chú hiển thị dựa theo địa điểm GPS, thời gian thực tế, hoặc Wi-Fi đang kết nối.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Nhận diện Wi-Fi và Vị trí (Context Monitor)**
    *   Tạo `ContextMonitorService.kt` để quản lý các yếu tố ngữ cảnh:
        - Đăng ký `BroadcastReceiver` theo dõi thay đổi kết nối Wi-Fi (SSID).
        - Đăng ký `GeofencingClient` nhận diện khi người dùng bước vào các khu vực định sẵn (nhà, văn phòng, siêu thị).
    *   Lưu thông tin ngữ cảnh hiện tại vào `SharedPreferences`.

*   [ ] **Task 2: Liên kết ghi chú với ngữ cảnh**
    *   Thêm trường `contextWifi: String? = null` và `contextGeofence: String? = null` vào [Note.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260426_another-notes-app/app/src/main/kotlin/com/mckimquyen/notes/model/entity/Note.kt).
    *   Cho phép người dùng liên kết ghi chú với Wi-Fi hiện tại hoặc vị trí hiện tại trong màn hình `EditFrm`.

*   [ ] **Task 3: Cập nhật Widget tự động**
    *   Khi ngữ cảnh thay đổi (ví dụ: kết nối vào Wi-Fi công ty), `ContextMonitorService` kích hoạt sự kiện cập nhật widget.
    *   Trong `RecentNotesWidget.kt`, thực hiện truy vấn các ghi chú phù hợp nhất với ngữ cảnh hiện tại để đưa lên giao diện widget.

---

## F-20: Collaborative P2P Local Sync (Chia sẻ ghi chú ngang hàng offline)

**Mô tả:** Chia sẻ và cùng chỉnh sửa ghi chú real-time giữa hai thiết bị ở gần nhau mà không cần kết nối mạng internet.

### 📋 Task List & Breakdown

*   [ ] **Task 1: Tích hợp Google Nearby Connections API**
    *   Thêm dependency Google Play Services Nearby vào `build.gradle`.
    *   Xin các quyền cần thiết: `ACCESS_FINE_LOCATION`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES`.

*   [ ] **Task 2: Thiết lập kết nối P2P**
    *   Tạo `P2PManager.kt` quản lý việc:
        - Thiết bị chủ (Host): Phát quảng bá dịch vụ (Advertise).
        - Thiết bị khách (Client): Quét tìm thiết bị chủ (Discover) và gửi yêu cầu kết nối.
        - Thiết lập kênh truyền dữ liệu dạng byte stream bảo mật.

*   [ ] **Task 3: Đồng bộ chỉnh sửa thời gian thực (Real-time Editor Sync)**
    *   Khi gõ phím, gửi các bản tin cập nhật văn bản nhỏ dạng JSON chứa vị trí con trỏ và nội dung chèn/xóa qua kết nối P2P.
    *   Áp dụng thuật toán giải quyết xung đột cơ bản (Conflict Resolution) để tự động trộn (merge) văn bản gõ từ cả 2 thiết bị mà không làm mất mát ký tự.
