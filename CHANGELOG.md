# Changelog

## 2026.08.20 (versionCode 20260820)

Audit toàn bộ tích hợp quảng cáo (AdMob/AppLovin) so với tài liệu `AD_PROMPT_AOS.MD` — phát hiện và sửa 1 lỗi nghiêm trọng ảnh hưởng an toàn tài khoản AdMob, cộng thêm loạt sửa bảo mật/vận hành nhỏ hơn.

### Đáng chú ý nhất
- **Sửa lỗi nghiêm trọng**: `AdManager.setTestDeviceIds()` dùng sai loại ID (GAID thay vì hash test-device do AdMob SDK yêu cầu) — khiến máy QA/dev **không hề được nhận diện là test device**, mọi click quảng cáo release thật đều tính invalid traffic, rủi ro khoá tài khoản AdMob. Đã verify bằng log thật + nhãn "Test Ad" hiển thị đúng sau khi sửa (Samsung A50s, OPPO CPH1989).
- Chuyển `vipKeySecret` (secret chống giả mạo VIP) ra khỏi `app/build.gradle` sang `keystore.properties` (gitignored) — trước đó bị commit thẳng vào source, dù đã Base64-obfuscate.
- Sửa callback `AdManager.initialize()` không phân biệt "đang chờ user đồng ý quảng cáo" với lỗi thật.
- Disable nhánh legacy plaintext VIP key, cập nhật logic timeout theo khuyến nghị SDK 1.6.x.
- Thêm `FLAG_KEEP_SCREEN_ON` toàn app — màn hình không tự khoá khi đang dùng.
- Đối chiếu lại toàn bộ Security Checklist của `AD_PROMPT_AOS.MD` (backup rules, paidEventListener lifecycle, App Open exclusion, COPPA config...) — không phát hiện thêm lỗi crash/mất tiền nào khác.

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
