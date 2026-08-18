# Changelog

## 2026.08.18 (versionCode 20260818)

Bản vá lỗi lớn — sửa 39 lỗi được phát hiện qua audit toàn diện source code, bao gồm các vấn đề bảo mật, mất dữ liệu, crash và rò rỉ bộ nhớ.

### Đáng chú ý nhất
- Sửa lỗi Export/Import JSON làm mất màu, mood và **mở khoá lại note đã khoá bằng sinh trắc học** — hồi quy bảo mật nghiêm trọng.
- Sửa tính năng "bấm back 2 lần để thoát" hoàn toàn không hoạt động.
- Sửa crash khi bấm "Watch Ad" ở màn Premium rồi thoát ra ngay lúc quảng cáo đang tải.
- Sửa reminder bị xoá mất khi merge dữ liệu JSON dù không có gì thay đổi.
- Sửa reminder lặp lại "ngày cuối tháng" tính sai ngày.
- Sửa loạt rò rỉ bộ nhớ (leak) trong quảng cáo, widget, animation, BroadcastReceiver.
- Cải thiện độ chính xác giờ nổ thông báo reminder khi máy vào chế độ tiết kiệm pin (Doze).
- Dedup tên nhãn (label) không phân biệt hoa-thường.
- Nhiều sửa lỗi nhỏ khác về đồng bộ dữ liệu, UI và độ ổn định.

Chi tiết đầy đủ từng fix: xem `doc/task/BACKLOG.md` (Sprint 1/2/3) và `doc/task/todo/FIX.md`.
