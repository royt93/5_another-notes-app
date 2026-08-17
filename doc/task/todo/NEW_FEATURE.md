# NEW FEATURE Backlog — Tính năng mới hợp lý, chưa tồn tại

> **Tạo:** 2026-08-17 | Gộp từ audit agy + claude-bypass, khử trùng.

## ⚠️ Lưu ý quan trọng — backlog cũ đã lỗi thời, nhiều "tính năng mới" đề xuất trước đây ĐÃ ĐƯỢC LÀM

Audit lần này xác nhận qua code thật: **mood tag, note color, khoá note bằng sinh trắc học, Timeline view, Time Travel (lịch sử phiên bản), export PDF/ảnh, Reading mode, màn hình VIP/premium (rewarded ads)** đều đã tồn tại trong codebase hiện tại — dù backlog cũ (`archive/pre_2026-08-17_audit/new_features_exclusive.md`) liệt các mục F-01, F-03, F-06, F-08, F-09, F-11 như "chưa làm". **Đừng lập lại các tính năng này** — nếu cần cải tiến, xem [ENHANCE.md](ENHANCE.md); nếu có bug, xem [FIX.md](FIX.md) (đặc biệt FIX-C01 ảnh hưởng trực tiếp tới mood/color/lock qua backup).

---

| ID | Tính năng | Mô tả | Effort | Priority | Files liên quan |
|---|---|---|---|---|---|
| NEW-01 | Đính kèm ảnh/file | Chụp ảnh/chọn từ thư viện, hiển thị dạng lưới ảnh co giãn trong note. Khoảng trống lớn nhất so với đối thủ — hoàn toàn không có bảng `attachments` trong schema hiện tại. | L | P1 | entity `Attachment` mới, `NotesDb.kt` (migration), `ui/edit/` |
| NEW-02 | Ghi âm & phát Voice Note (waveform player) | Ghi âm trực tiếp trong note, player dạng sóng âm với Play/Pause + tiến trình | L | P1 | entity mới, `ui/edit/`, `AudioRecorderHelper.kt` |
| NEW-03 | Markdown / rich text cơ bản | Đậm, nghiêng, tiêu đề H1-H3, code block, trích dẫn — hiện chỉ có auto-bullet + autolink, không span định dạng nào được lưu | L | P2 | `ui/edit/`, `model/entity/Note.kt` (format lưu ở content/metadata) |
| NEW-04 | Note template có sẵn | "Biên bản họp", "Kế hoạch tuần", "Danh sách mua sắm"... `EditVM.start()` đã nhận sẵn tham số title/content/type — chỉ cần màn hình chọn template | S | P1 | `ui/edit/EditVM.kt`, `ui/edit/EditFrm.kt` |
| NEW-05 | Gán màu/mood hàng loạt trong multi-select | Action bar multi-select đã có pin/reminder/labels/move/delete — chỉ thiếu action màu/mood hàng loạt | S | P2 | `ui/note/NoteFrm.kt`, `ui/note/NoteVM.kt` |
| NEW-06 | App-link/deep link trỏ thẳng tới note | Chưa có `<data android:scheme>` intent-filter nào ngoài action nội bộ; tận dụng lại `INTENT_ACTION_EDIT` | M | P2 | `AndroidManifest.xml`, `ui/main/MainAct.kt` |
| NEW-07 | Recurring reminder preset 1-chạm | "Mỗi thứ 2 9h", "ngày 1 hàng tháng" — hạ tầng `Recurrence`/`RecurrenceFinder` đã đủ mạnh (xem FIX-C03 cần sửa trước) | S | P2 | `ui/reminder/ReminderDlg.kt` |
| NEW-08 | Quick-add inline ngay trên widget | `QuickNoteWidget`/`QuickListWidget` hiện chỉ deep-link vào app, chưa nhập liệu trực tiếp trên widget | L | P2 | `widget/QuickNoteWidget.kt`, `widget/QuickListWidget.kt` |
| NEW-09 | Digest tổng hợp reminder quá hạn | Thông báo tóm tắt hàng ngày các reminder quá hạn chưa xong, bổ sung cho luồng thông báo từng note | M | P2 | mới, tích hợp `receiver/AlarmReceiver.kt` |
| NEW-10 | Màu sắc/icon cho Label | `Label` entity hiện chỉ có `id/name/hidden` — cần thêm cột `color` + migration | M | P1 | `model/entity/Label.kt`, `NotesDb.kt`, `ui/labels/` |
| NEW-11 | Khoá app bằng sinh trắc học/PIN cấp toàn app | Khác với khoá từng note đã có — hạ tầng mã hoá PBKDF2+AES-GCM+AndroidKeyStore đã có sẵn cho export/import, tái sử dụng được cho app-lock toàn cục | L | P1 | mới + `RApp.kt`, `ui/setting/` |
| NEW-12 | Backup/đồng bộ cloud tuỳ chọn | `DefaultJsonManager` đã có full pipeline export/import local — chỉ thiếu đích lưu trữ cloud (Drive/Dropbox) | L | P2 | `DefaultJsonManager.kt` |
| NEW-13 | Xem diff trực quan giữa các phiên bản Time Travel | `NoteHistory` hiện chỉ lưu full-text snapshot, restore chỉ preview toàn văn — chưa có diff (xem ENH-03 cùng chủ đề) | M | P2 | `ui/edit/` (màn hình history đã có), `NoteHistory.kt` |
| NEW-14 | Ghim ghi chú lên thanh thông báo hệ thống | "Sticky note" luôn hiển thị trên Notification Center, không tự trôi mất | S | P2 | `receiver/`, `ui/main/MainAct.kt` |
| NEW-15 | Tự động gắn Tag bằng `#tag` khi gõ | Gõ `#` gợi ý danh sách label hiện có, tự liên kết note với label đó khi lưu | S | P2 | `EditVM.kt`, `LabelsRepository` |
| NEW-16 | Tuỳ chỉnh thời gian tự dọn Thùng rác | 7/14/30/60 ngày hoặc "Không bao giờ" thay vì hard-code | S | P2 | `PrefsManager.kt`, `SettingsFrm.kt` |
| NEW-17 | OCR nhận dạng chữ từ ảnh (ML Kit on-device) | Quét chữ từ ảnh chụp hoá đơn/tài liệu, chèn thẳng vào nội dung — phụ thuộc NEW-01 (đính kèm ảnh) làm trước | M | P2 | `OcrHelper.kt`, `build.gradle` |
| NEW-18 | Quick Settings Tile tạo note nhanh | Tile trên thanh Quick Settings Android mở popup ghi chú nhanh, không cần mở toàn app | S | P2 | `QuickNoteTileService.kt`, `AndroidManifest.xml` |
