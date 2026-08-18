# IDEA Backlog — Ý tưởng thô, cần nghiên cứu thêm

> **Tạo:** 2026-08-17 | Gộp từ audit agy + claude-bypass, khử trùng. Đây là các ý tưởng chưa chắc khả thi/chưa nên cam kết ngay — ghi lại để cân nhắc sau, không có estimate effort chính thức.

- **IDEA-01 — Undo/redo per-keystroke thật trong editor.** Khác Time Travel (snapshot debounce ~10s) — đây là undo/redo tức thời chuẩn text-editor. Cần nghiên cứu chi phí bộ nhớ cho note dài (tới 100k ký tự) và tương tác với `BulletTextWatcher`/autolink.
- **IDEA-02 — Widget khoá màn hình (always-on-display) hiện reminder ưu tiên cao nhất.** Cần nghiên cứu API AOD giới hạn nhiều theo OEM. Vấn đề riêng tư ở FIX-M13 ✅ đã vá 2026-08-17 (widget đã ẩn tiêu đề note khoá) — không còn là blocker, nhưng cùng nguyên tắc "không hiện nội dung khoá trên bề mặt công khai" vẫn phải áp dụng cho widget AOD mới nếu làm.
- **IDEA-03 — Nested/phân cấp Label (label cha-con).** Cần nghiên cứu UX cho tập label lớn, migration schema `Label` phức tạp hơn NEW-10.
- **IDEA-04 — Rate-limit/lockout khi nhập sai password import nhiều lần.** Rủi ro thực tế thấp (kẻ tấn công cần có sẵn file export local), nhưng đáng cân nhắc cho nhất quán UX bảo mật.
- **IDEA-05 — Multi-window/freeform chính thức.** Cả 3 activity hiện khoá `screenOrientation="portrait"`, không khai báo `resizeableActivity`. FIX-M22/FIX-H07 (onNewIntent) dễ tái hiện nhất qua split-screen — cần quyết định rõ: hỗ trợ chính thức (fix toàn bộ gap liên quan) hay khoá cứng `resizeableActivity="false"`.
- **IDEA-06 — Chuẩn hoá bộ 3 package `com.mckimquyen.notes` / `com.mckimquyen.debug.notes` / `com.maltaisn.notes`.** Dọn dẹp lớn, rủi ro cao vì đụng cấu trúc DI/source-set đang hoạt động. CLAUDE.md ghi rõ "đừng tự ý fix mismatch — variant source sets phụ thuộc vào nó" — chỉ nên làm sau khi có kế hoạch riêng, không làm vội. (Xem ENH-A01 cho phần hẹp/an toàn hơn của việc này.)
- **IDEA-07 — On-device AI (Gemini Nano/AICore) tóm tắt & tạo action items.** Cần Android 14+ và thiết bị hỗ trợ NPU — khả thi trung bình, phụ thuộc thị phần thiết bị support.
- **IDEA-08 — Vẽ tay tự do / hỗ trợ stylus (S-Pen).** Canvas vẽ vector với cảm ứng lực, màu mực, tẩy — khả thi cao về mặt kỹ thuật (Canvas custom), nhưng là hạng mục UI lớn.
- **IDEA-09 — Liên kết 2 chiều Wiki-Links `[[Note]]` + Graph View.** Parser `[[...]]` + màn hình bản đồ mạng lưới 2D. Cần thuật toán force-directed graph trên Canvas — effort lớn (L-XL), chỉ nên làm nếu định vị sản phẩm rõ ràng là "second brain" app.
- **IDEA-10 — Tìm kiếm ngữ nghĩa bằng local vector embeddings.** Model nhúng nhẹ (MiniLM-class) + SQLite-vec để tìm theo ý nghĩa thay vì từ khoá — khả thi trung bình, cần tối ưu RAM/dung lượng model.
- **IDEA-11 — Đồng bộ P2P cục bộ (Wi-Fi/Nearby Connections), không cần cloud.** Khả thi bằng Nearby Connections API hoặc WebSocket cục bộ — effort lớn, cần thiết kế giao thức đồng bộ delta.
- **IDEA-12 — Chia sẻ note 1 chạm qua NFC.** API NFC tiêu chuẩn Android có sẵn — khả thi cao, effort nhỏ, nhưng thị phần thiết bị hỗ trợ NFC + nhu cầu thực tế cần đánh giá.
- **IDEA-13 — Flashcard ôn tập ngắt quãng (Spaced Repetition).** Biến câu hỏi/gạch đầu dòng trong note thành thẻ ôn theo SM-2 — dễ tích hợp vào cấu trúc Reminder hiện có, khả thi cao.
- **IDEA-14 — Geofence: đẩy note lên đầu widget theo vị trí.** "Đến siêu thị tự hiện danh sách mua sắm". Cần `GeofencingClient`, cân nhắc kỹ chi phí pin và độ phức tạp permission (background location bị Google Play siết chặt).
- **IDEA-15 — Xoay vòng lại signing key sau sự cố lộ khoá trong lịch sử git.** Không phải bug code — commit `212225a` gợi ý từng có bí mật lộ. Cần người có quyền Play Console quyết định trước khi hành động (có dùng Play App Signing để rotate upload key được không, có cần rewrite git history không).
