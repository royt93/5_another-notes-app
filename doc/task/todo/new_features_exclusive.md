# New Exclusive Features — Another Notes App

> **Created:** 2026-06-22 | **Status:** 📋 Picked (chờ estimate và prioritize)

Các tính năng **độc quyền** chưa thấy ở Google Keep, Samsung Notes, hoặc các app ghi chú phổ biến.

---

## F-01: Note Mood / Energy Tag (⭐ Độc quyền cao)

**Mô tả:** User có thể gắn "mood" cho mỗi note — 5 icon cảm xúc (😄 😐 😔 💡 🔥). Hiển thị nhỏ góc card. Filter và sort theo mood.

**Giá trị:** Biến notes thành journal/diary cá nhân hóa. Keep và Samsung Notes không có.

**Tech:**
- Thêm column `mood: Int?` vào entity `Note` → migration Room 5→6
- UI: row icon picker trong EditFrm (bottom toolbar)
- Home: filter chip "By Mood" trong drawer

**Effort:** M (3-4 ngày)
**Files chính:** `Note.kt`, `EditFrm.kt`, `HomeFrm.kt`, Room migration

---

## F-02: Spring-Physics FAB Animation (⭐ Độc quyền animation)

**Mô tả:** FAB bounces với spring physics khi scroll. Khi scroll xuống — FAB co lại (shrink to icon only). Khi scroll lên — FAB expand về đầy đủ với spring overshoot. Dùng `SpringAnimation` từ `dynamicanimation`.

**Giá trị:** Cảm giác "alive" cho UI. Khác với hide/show FAB thông thường.

**Tech:**
- `SpringAnimation(fab, DynamicAnimation.TRANSLATION_Y)`
- `SpringForce.STIFFNESS_MEDIUM`, `SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY`
- Kết hợp với `RecyclerView.addOnScrollListener`

**Effort:** S (1-2 ngày)
**Files chính:** `HomeFrm.kt`, `NoteFrm.kt`

---

## F-03: Note Locking với Biometric (⭐ Bảo mật độc quyền)

**Mô tả:** User có thể lock 1 note cụ thể bằng fingerprint/face. Note bị lock hiển thị icon khóa thay vì content. Cần xác thực để mở.

**Giá trị:** Bảo mật từng note riêng lẻ (không phải app-wide lock). Đây là tính năng ít app ghi chú free làm.

**Tech:**
- `BiometricPrompt` + `CancellationSignal`
- Column `isLocked: Boolean` trong Room
- `NoteListVH` hiển thị placeholder khi locked
- Không encrypt content (chỉ UI gate) hoặc có thể encrypt với `EncryptedSharedPreferences`

**Effort:** M (3-5 ngày)
**Files chính:** `Note.kt`, `NoteListVH.kt`, `EditFrm.kt`, Room migration

---

## F-04: Word Count Milestone Celebration (⭐ Gamification)

**Mô tả:** Khi user đạt milestone từ (100, 500, 1000, 5000 từ trong 1 note), hiển thị một celebration animation nhỏ (confetti/particle burst) và snackbar "🎉 500 words written!".

**Giá trị:** Motivate writing, tạo cảm giác achievement. Unique với notes app.

**Tech:**
- `EditVM` track word count milestones (chỉ trigger 1 lần/milestone)
- Particle view hoặc Lottie animation overlay ngắn 1.5s
- Snackbar màu cam/vàng

**Effort:** S-M (2-3 ngày)
**Files chính:** `EditVM.kt`, `EditFrm.kt`

---

## F-05: Smart Duplicate Detection (⭐ AI-adjacent)

**Mô tả:** Khi user tạo note mới với nội dung giống note đã có (>80% similar), hiện banner cảnh báo "Bạn có thể đã có note tương tự: [Title]" với option xem hoặc bỏ qua.

**Giá trị:** Tránh duplicate notes — vấn đề thực sự khi dùng lâu. Không app nào làm.

**Tech:**
- Simple Jaccard similarity hoặc shingle comparison (không cần AI/ML)
- Tính trong background (Dispatchers.Default) sau khi user dừng gõ 2s
- Result cache bằng `Flow` debounce

**Effort:** M (3-4 ngày)
**Files chính:** `EditVM.kt`, `EditFrm.kt`, `NotesRepository`

---

## F-06: Note Timeline View (⭐ Khác biệt layout)

**Mô tả:** Thêm layout thứ 3 (ngoài List và Grid): Timeline — note sắp xếp theo ngày tạo/chỉnh sửa, nhóm theo ngày, với đường timeline bên trái. Mỗi ngày là 1 section header.

**Giá trị:** Biến notes app thành personal diary. Unique UI so với Keep/Samsung.

**Tech:**
- `StickyHeaderDecoration` hoặc header item trong adapter
- Layout `i_note_timeline.xml` — card nhỏ hơn, ít info hơn
- Toggle view type: `LIST | GRID | TIMELINE`

**Effort:** L (5-7 ngày)
**Files chính:** `NoteAdt.kt`, `HomeFrm.kt`, layout XMLs, `PrefsManager`

---

## F-07: Shake to Undo (⭐ Gesture độc quyền)

**Mô tả:** Khi user vừa xóa/archive 1 note, lắc device trong vòng 5 giây để undo. Hiển thị "Shake to undo" snackbar với countdown.

**Giá trị:** Fun UX, memorable. Không app ghi chú nào làm.

**Tech:**
- `SensorManager` + `SensorEventListener` với threshold acceleration
- Chỉ active trong 5s sau action destructive
- Fallback: snackbar Undo button vẫn còn

**Effort:** S (1-2 ngày)
**Files chính:** `HomeFrm.kt`, `MainAct.kt`

---

## F-08: Note Reading Mode (⭐ Productivity)

**Mô tả:** Trong EditFrm, nút "Reading Mode" — ẩn keyboard và tất cả editing UI, chỉ hiển thị text ở font size lớn hơn, background tối hơn, margin rộng hơn. Như e-reader.

**Giá trị:** Note review dễ hơn, không vô tình chạm vào text. Unique.

**Tech:**
- Toggle state trong `EditFrm`
- Animate hide/show controls với `ViewPropertyAnimator`
- Tăng text size lên 18sp, tăng line spacing

**Effort:** S (1-2 ngày)
**Files chính:** `EditFrm.kt`, `f_edit.xml`

---

## F-09: Note Export to PDF / Image (⭐ Sharing độc quyền)

**Mô tả:** Export note thành file PDF hoặc ảnh PNG để share. PDF dùng `PdfDocument` API, image dùng View → Bitmap.

**Giá trị:** Sharing note như tài liệu đẹp. Keep chỉ share text thuần.

**Tech:**
- `PdfDocument` + `PdfDocument.Page` (Android native, không cần lib)
- `View.drawToBitmap()` cho PNG
- Share via `FileProvider` + `ACTION_SEND`

**Effort:** M (3-4 ngày)
**Files chính:** `EditFrm.kt`, `EditVM.kt`, hoặc thêm `ExportHelper.kt`

---

## F-10: Focus Mode — Hide All, Write Only (⭐ Productivity)

**Mô tả:** Một floating button "Focus" trong EditFrm — khi bật, ẩn status bar, navigation bar, toolbar, word count. Chỉ còn text field. Thoát bằng tap đôi.

**Giá trị:** Distraction-free writing như iA Writer / Obsidian. Chưa có app ghi chú Android free nào làm tốt.

**Tech:**
- `WindowInsetsController.hide(WindowInsets.Type.systemBars())`
- Animate toolbar slide-up, word count fade-out
- Detect double-tap để exit

**Effort:** S-M (2-3 ngày)
**Files chính:** `EditFrm.kt`

---

## F-11: Time-Travel Note Slider (Thanh trượt du hành thời gian) (⭐ Độc quyền đỉnh cao)

**Mô tả:** Người dùng có thể kéo một thanh trượt (slider) ở dưới màn hình soạn thảo để "quay ngược thời gian" xem lại các phiên bản cũ của ghi chú. Thay vì chỉ nhấn Undo/Redo mù mờ, thanh trượt hiển thị trực quan các mốc thời gian đã sửa đổi và thay đổi nội dung ghi chú tức thì để người dùng xem lại.

**Giá trị:** Cực kỳ hữu dụng khi người dùng vô tình xóa mất một đoạn văn lớn vài giờ trước. Hầu hết các app khác chỉ lưu phiên bản cuối cùng hoặc phải mở danh sách lịch sử rất phức tạp.

**Tech:**
- Tạo database table `NoteHistory` lưu các snapshot/diff kèm timestamp.
- Sử dụng `Slider` (Material 3) trong `EditFrm`. Kéo slider sẽ cập nhật text hiển thị tạm thời trên màn hình để preview.
- Cho phép khôi phục phiên bản đang xem bằng nút "Restore".

**Effort:** M-L (4-5 ngày)
**Files chính:** `NoteHistory.kt` (mới), `NotesDb.kt`, `EditVM.kt`, `EditFrm.kt`

---

## F-12: Auto-Theme & Color Association (Tự động tô màu & đổi theme theo nội dung) (⭐ Độc quyền AI-adjacent)

**Mô tả:** Khi người dùng nhập liệu, ứng dụng sẽ tự động phân tích từ khóa hoặc sắc thái cảm xúc của văn bản (ví dụ: việc gấp, buồn bã, ý tưởng mới, cá nhân) để tự động gợi ý đổi màu nền ghi chú (note card background) và icon cảm xúc phù hợp mà không cần chọn thủ công.

**Giá trị:** Giúp danh sách ghi chú tự động được phân loại màu sắc một cách trực quan, đẹp mắt và cá nhân hóa cao.

**Tech:**
- Xây dựng bộ heuristic/regex matching đơn giản (không cần ML nặng) chạy trên background thread.
- Khi nhận diện được từ khóa (ví dụ: "họp", "quan trọng", "deadline" -> màu đỏ cảnh báo; "ý tưởng", "sáng tạo" -> màu vàng sáng).
- Hiển thị tooltip gợi ý nhẹ: "Áp dụng màu nền phù hợp cho ghi chú này?" hoặc tự động đổi kèm hiệu ứng chuyển màu mượt mà.

**Effort:** S-M (2-3 ngày)
**Files chính:** `SentimentHelper.kt` (mới), `EditVM.kt`, `EditFrm.kt`

---

## F-13: Interactive Note Link Map (Sơ đồ liên kết ghi chú tương tác) (⭐ Siêu độc quyền)

**Mô tả:** Cho phép người dùng liên kết chéo các ghi chú với nhau bằng cú pháp `[[Tiêu đề ghi chú]]` (giống Obsidian hoặc Roam Research). Ứng dụng cung cấp màn hình "Map View" hiển thị sơ đồ mạng lưới 2D trực quan nơi mỗi ghi chú là một nút tròn (node) và các đường nối thể hiện mối liên kết giữa các ghi chú.

**Giá trị:** Biến ứng dụng ghi chú đơn giản thành một hệ thống quản lý tri thức cá nhân (Second Brain). Chưa có ứng dụng ghi chú di động phổ thông nào tích hợp tính năng này một cách mượt mà.

**Tech:**
- Parser regex để tìm cú pháp liên kết trong nội dung ghi chú.
- Custom view hoặc sử dụng thư viện vẽ đồ thị để vẽ sơ đồ node mạng lưới trên Canvas, hỗ trợ zoom, kéo và click để mở trực tiếp ghi chú.

**Effort:** L (6-8 ngày)
**Files chính:** `GraphView.kt` (mới), `GraphFrm.kt` (mới), `NotesRepository.kt`

---

## F-14: Pomodoro Writing Session (Chế độ viết Pomodoro tích hợp nhạc nền) (⭐ Độc quyền Productivity)

**Mô tả:** Tích hợp bộ đếm giờ Pomodoro trực tiếp trong màn hình soạn thảo. Khi kích hoạt Pomodoro Session, ứng dụng tự động đưa màn hình về chế độ tối giản (Focus Mode), bắt đầu đếm ngược 25 phút viết, đồng thời phát các bản nhạc nền nhẹ nhàng (Lo-fi, Tiếng mưa, Tiếng sóng biển) được tích hợp sẵn.

**Giá trị:** Kết hợp ghi chú và quản trị thời gian tập trung, cực kỳ thu hút học sinh, sinh viên và nhà văn.

**Tech:**
- Coroutine-based timer trong `EditVM` gửi cập nhật lên UI và Notification (dùng Foreground Service để tránh bị giết).
- MediaPlayer phát loop các file âm thanh nhỏ local.
- Thống kê hiệu suất viết (số từ đã viết được trong session).

**Effort:** M (3-4 ngày)
**Files chính:** `PomodoroService.kt` (mới), `EditFrm.kt`, `EditVM.kt`

---

## F-15: Floating Quick-Note Bubble (Bong bóng ghi chú nổi đa nhiệm) (⭐ Độc quyền Android)

**Mô tả:** Một bong bóng nổi trên màn hình (tương tự Chat Head của Messenger) cho phép người dùng mở nhanh một cửa sổ ghi chú nhỏ ngay trên màn hình chính hoặc khi đang dùng ứng dụng khác để ghi chép nhanh hoặc sao chép nội dung.

**Giá trị:** Tiện ích tối đa cho việc copy-paste và ghi chép nhanh mà không cần chuyển đổi qua lại giữa các ứng dụng.

**Tech:**
- Sử dụng Android Service và `WindowManager` với permission `SYSTEM_ALERT_WINDOW`.
- Thiết kế layout bong bóng nổi và cửa sổ pop-up soạn thảo mini.
- Lưu trực tiếp vào database của ứng dụng chính.

**Effort:** M-L (4-5 ngày)
**Files chính:** `FloatingNoteService.kt` (mới), `FloatingLayout.xml` (mới)

---

## F-16: Acoustic Note Atmosphere (Không gian ghi chú đa giác quan) (⭐ Độc quyền trải nghiệm)

**Mô tả:** Cho phép gắn một "không gian âm thanh" (soundscape) vào một ghi chú cụ thể. Khi người dùng mở ghi chú đó ra đọc hoặc viết, ứng dụng sẽ tự động phát nhạc nền nhẹ nhàng tương ứng (tiếng mưa rơi, quán cafe, thư viện, tiếng lửa trại tí tách).

**Giá trị:** Tạo không gian viết/đọc chìm đắm và cá nhân hóa, giúp gia tăng cảm xúc và độ tập trung khi làm việc với từng ghi chú cụ thể.

**Tech:**
- Cột `soundscapeId: Int?` trong bảng `Note`.
- Trình phát nhạc nền loop bằng `MediaPlayer` trong `EditFrm`, tự động dừng khi thoát màn hình soạn thảo.

**Effort:** S-M (2 ngày)
**Files chính:** `Note.kt`, `EditFrm.kt`

---

## F-17: Self-Destruct & Decoy Notes (Ghi chú tự hủy & Ngụy trang bảo mật) (⭐ Độc quyền bảo mật)

**Mô tả:** 
1. *Ghi chú tự hủy*: Hỗ trợ thiết lập bộ đếm ngược tự hủy ghi chú (ví dụ: tự động xóa sau 1 giờ tạo, hoặc sau 10 giây kể từ khi mở đọc).
2. *Mật khẩu ngụy trang (Decoy PIN)*: Khi người dùng mở tính năng bảo mật sinh trắc học/mật khẩu, nếu nhập một mã PIN ngụy trang đặc biệt, ứng dụng vẫn mở bình thường nhưng ẩn tất cả ghi chú bí mật thực sự và chỉ hiển thị các ghi chú thông thường/giả lập.

**Giá trị:** Bảo vệ tuyệt đối quyền riêng tư và dữ liệu nhạy cảm của người dùng trước các tình huống bị ép buộc mở app.

**Tech:**
- `AlarmManager` để kích hoạt tính năng tự hủy.
- Lưu cấu hình mã PIN chính và PIN ngụy trang riêng biệt. Khi đăng nhập bằng PIN ngụy trang, lọc query SQL để ẩn các ghi chú đã được đánh dấu nhạy cảm.

**Effort:** M-L (4-5 ngày)
**Files chính:** `SecurityHelper.kt` (mới), `NotesRepository.kt`, `MainAct.kt`

---

## F-18: Voice-to-Mindmap Canvas (Chuyển giọng nói thành sơ đồ tư duy) (⭐ Độc quyền AI-UX)

**Mô tả:** Người dùng nói vào ứng dụng (thu âm ý tưởng), ứng dụng tự động nhận diện giọng nói thành văn bản, sau đó trích xuất các từ khóa/bullet points chính và tự động vẽ thành một sơ đồ tư duy (mindmap) 2D trực quan trên màn hình.

**Giá trị:** Cực kỳ hữu ích cho việc động não (brainstorming) rảnh tay khi đang di chuyển hoặc nảy ra ý tưởng nhanh mà không muốn gõ phím.

**Tech:**
- Sử dụng API `SpeechRecognizer` của Android để nhận diện giọng nói thời gian thực.
- Xử lý tách câu và từ khóa dựa trên dấu câu và từ nối cơ bản.
- Custom Canvas để render cấu trúc cây sơ đồ tư duy, cho phép chỉnh sửa nội dung từng nhánh.

**Effort:** L (7-9 ngày)
**Files chính:** `SpeechMindmapView.kt` (mới), `SpeechMindmapFrm.kt` (mới)

---

## F-19: Context-Aware Smart Widgets (Widget thông minh nhận diện ngữ cảnh) (⭐ Độc quyền Widget)

**Mô tả:** Widget màn hình chính tự động thay đổi ghi chú hiển thị dựa trên ngữ cảnh thực tế của người dùng: vị trí địa lý (hiển thị danh sách mua sắm khi ở siêu thị, note công việc khi ở văn phòng), thời gian trong ngày (nhắc nhở sáng, trưa, tối), hoặc tên Wi-Fi đang kết nối.

**Giá trị:** Mang thông tin người dùng cần đến ngay trước mắt họ một cách chủ động mà không cần mở app tìm kiếm.

**Tech:**
- Sử dụng `GeofencingClient` và `LocationServices` để định vị địa giới.
- Đăng ký `BroadcastReceiver` nhận diện thay đổi trạng thái kết nối Wi-Fi (`WifiManager`).
- Cập nhật Widget thông qua `AppWidgetManager`.

**Effort:** M-L (5-6 ngày)
**Files chính:** `ContextReceiver.kt` (mới), `ContextWidgetService.kt` (mới), `RecentNotesWidget.kt`

---

## F-20: Collaborative P2P Local Sync (Chia sẻ ghi chú ngang hàng không cần Internet) (⭐ Độc quyền kết nối)

**Mô tả:** Cho phép hai thiết bị ở gần nhau chia sẻ và chỉnh sửa ghi chú cùng nhau (real-time collaboration) thông qua kết nối Wi-Fi Direct hoặc Bluetooth cục bộ, hoàn toàn không cần kết nối Internet hay đăng nhập tài khoản.

**Giá trị:** An toàn bảo mật tuyệt đối, hoạt động tốt ở các khu vực không có sóng mạng (trên máy bay, tàu điện ngầm, vùng sâu vùng xa).

**Tech:**
- Sử dụng thư viện `Google Nearby Connections API` để kết nối ngang hàng P2P.
- Đồng bộ hóa các delta thay đổi văn bản (text operations) qua kết nối socket cục bộ.

**Effort:** L (8-10 ngày)
**Files chính:** `P2PConnectHelper.kt` (mới), `P2PEditFrm.kt` (mới)
