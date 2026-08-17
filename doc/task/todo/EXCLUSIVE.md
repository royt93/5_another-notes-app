# EXCLUSIVE Backlog — Tính năng độc quyền / khác biệt cạnh tranh thật

> **Tạo:** 2026-08-17 | Gộp từ backlog cũ ([archive/pre_2026-08-17_audit/new_features_exclusive.md](../archive/pre_2026-08-17_audit/new_features_exclusive.md) mục F-02, F-07, F-13→F-20) + audit agy + claude-bypass, khử trùng theo chủ đề. So sánh với Google Keep, Samsung Notes, Notion mobile, Obsidian mobile.
>
> **Đã có hạ tầng, chỉ cần hoàn thiện + quảng bá** (không phải xây từ 0) — đây là nhóm nên ưu tiên vì ROI cao nhất:

## Nhóm A — Đã có hạ tầng, cần hoàn thiện (ROI cao nhất)

**EXC-A01 — Time-Travel version history đầy đủ, hoàn toàn offline, có diff trực quan**
`NoteHistory` + `NoteHistoryDao` đã tồn tại và hoạt động (Time Travel slider đã [DONE] theo backlog cũ), nhưng chỉ lưu title/content/metadata — không lưu reminder/labels/color/mood/pinned/status. Google Keep không có version history; Notion có nhưng bắt buộc cloud/account; Obsidian chỉ qua plugin cộng đồng. Một note app offline-first với version history native, đầy đủ field, không cần tài khoản, là câu chuyện định vị mạnh — đầu tư thêm diff UI (xem NEW-13, ENH-03) và mở rộng field lưu trữ.
Effort: L. Priority: P1. Files: `model/entity/NoteHistory.kt`, `NotesDb.kt` (migration).

**EXC-A02 — Backup/mã hoá hoàn toàn local, không tài khoản, không server tin cậy**
Pipeline export/import hiện tại (AES-GCM/PBKDF2WithHmacSHA512 120.000 vòng lặp/AndroidKeyStore — đã xác nhận qua code không phải "weak crypto") là nền tảng tốt cho câu chuyện "zero-account, zero-cloud, client-side encryption" — khác Keep (bắt buộc tài khoản Google) và Notion (bắt buộc tài khoản). **Điều kiện tiên quyết: phải vá FIX-C01/FIX-M25 trước khi quảng bá làm điểm bán hàng** — nếu không sẽ phản tác dụng (note khoá bị mở khoá sau restore, đúng lúc đang PR tính năng bảo mật).
Effort: S (sau khi vá bug) + marketing. Priority: P0 (vì phụ thuộc bug Critical).

**EXC-A03 — Reminder định kỳ mạnh ngang app lịch, gắn trực tiếp vào note**
Dùng `com.maltaisn:recurpicker` — mạnh hơn Keep (chỉ nhắc 1 lần/lặp cơ bản) và Samsung Notes (gần như không có reminder định kỳ thật). Sau khi vá FIX-C03 (bug ngày cuối tháng) + FIX-H04 (exact alarm) và bổ sung NEW-07 (preset), đây là tính năng lõi đã mạnh sẵn, chỉ cần hoàn thiện để thành điểm bán hàng rõ ràng.
Effort: M (sau khi vá bug). Priority: P1.

**EXC-A04 — Note mood như metadata hạng nhất (nhật ký cảm xúc nhẹ)**
`Note.mood` đã tồn tại trong entity nhưng chưa có UI hàng loạt/lọc/thống kê theo mood. Không đối thủ nào coi cảm xúc là trường ghi chú hạng nhất — phát triển thành góc nhìn "nhật ký cảm xúc" (lọc/thống kê note theo mood theo thời gian).
Effort: M. Priority: P2.

**EXC-A05 — Bộ widget đa dạng nhất phân khúc → "dashboard" cấu hình sâu**
App đã có 4 loại widget riêng biệt (QuickNote, NoteCount, QuickList, RecentNotes) — nhiều hơn Keep (1 loại) hay Samsung Notes. Kết hợp NEW-08 (quick-add inline) + ENH-15 (lọc theo label) + ENH-14 (configuration activity) thành 1 "widget suite" cấu hình sâu.
Effort: L (tổng hợp nhiều item ENHANCE/NEW ở trên). Priority: P2.

---

## Nhóm B — Xây mới, độc quyền thật sự (chưa có hạ tầng)

**EXC-B01 — Decoy PIN & Camouflage Panic Vault (két sắt nguỵ trang 2 tầng)**
Mã PIN thật mở toàn bộ note thật; mã PIN nguỵ trang mở app bình thường nhưng nạp DB ảo chỉ chứa note thông thường, không dấu vết note nhạy cảm. Panic gesture (úp máy/lắc mạnh) tự thoát về Home + xoá RAM đệm. Kết hợp với khoá note đã có (`Note.isLocked`) làm nền — bảo vệ quyền riêng tư tuyệt đối trong tình huống bị ép mở máy.
Effort: M-L. Priority: P1. Files mới: `SecurityHelper.kt`, tích hợp `PrefsManager.kt`, `MainAct.kt`.

**EXC-B02 — Acoustic Focus Atmosphere & Dynamic Soundscapes**
Soundscape nền (mưa, quán cafe, sóng biển, lửa trại) đồng bộ theo màu note, phát khi mở note để đọc/viết, tự dừng khi thoát. Biến app thành công cụ viết lách "gây nghiện" thay vì công cụ CRUD đơn điệu.
Effort: S-M. Priority: P1. Files mới: `SoundscapeManager.kt`.

**EXC-B03 — Kinetic Physics Drag & Shake-to-Disintegrate**
Kéo-thả sắp xếp note/checklist có trọng lượng, gia tốc, độ nảy tự nhiên (spring dynamics — đã có `SpringItemAnimator.kt` làm nền). Hiệu ứng hạt bụi phân rã + haptic giảm dần khi dọn Thùng rác vĩnh viễn.
Effort: S. Priority: P2. Files: `SpringItemAnimator.kt`, `NoteAdt.kt`.

**EXC-B04 — Interactive Note Link Map (Wiki-links + Graph View)**
Cú pháp `[[Tên note]]` liên kết chéo, màn hình Graph View 2D dạng mạng nhện. Biến app từ note-taking đơn giản thành "second brain" — chưa app note di động phổ thông nào tích hợp mượt. (Trùng chủ đề IDEA-09 — cân nhắc mức cam kết đầu tư trước khi promote lên đây, vì effort L-XL và cần định vị sản phẩm rõ ràng.)
Effort: L. Priority: P2. Files mới: `GraphView.kt`, `GraphFrm.kt`.

**EXC-B05 — Shake to Undo**
Lắc máy trong 5s sau khi xoá/archive note để undo, thay vì chỉ chạm nút Undo trên Snackbar. Fun UX, memorable — chưa app note nào làm.
Effort: S. Priority: P2. Files: `HomeFrm.kt`, `MainAct.kt` (SensorManager).

**EXC-B06 — Context-Aware Geofence Widget**
Tự động đẩy note/checklist lên đầu Widget khi vào bán kính địa điểm đã gán (siêu thị → checklist mua sắm, văn phòng → checklist công việc). Cân nhắc kỹ chi phí pin + Google Play policy cho background location trước khi cam kết (xem IDEA-14).
Effort: L. Priority: P2 (cần review policy trước khi lên P1).

**EXC-B07 — Zero-Cloud P2P Encrypted Sync**
Đồng bộ trực tiếp giữa 2 thiết bị qua Wi-Fi/QR động, mã hoá AES-256-GCM end-to-end, không server trung gian, không tài khoản. Đạt chuẩn bảo mật cho nhóm người dùng coi trọng quyền riêng tư tuyệt đối.
Effort: L. Priority: P2. Files mới: `P2pSyncManager.kt`.

**EXC-B08 — Pomodoro Writing Session tích hợp nhạc nền**
Bộ đếm Pomodoro trong Edit screen, tự chuyển Focus Mode tối giản khi kích hoạt, phát nhạc nền nhẹ (Lo-fi, mưa, sóng biển) — hấp dẫn nhóm học sinh/sinh viên/nhà văn.
Effort: M. Priority: P2. Files mới: `PomodoroService.kt`.

**EXC-B09 — Floating Quick-Note Bubble**
Bong bóng nổi trên màn hình (kiểu Chat Head Messenger) mở nhanh cửa sổ note mini khi đang dùng app khác — tiện copy-paste nhanh. Cần permission `SYSTEM_ALERT_WINDOW`, cân nhắc friction xin quyền.
Effort: M-L. Priority: P2. Files mới: `FloatingNoteService.kt`.
