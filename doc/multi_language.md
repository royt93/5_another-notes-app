# Multi-Language — Another Notes App

> **Updated:** 2026-06-22

---

## Ngôn ngữ được hỗ trợ

| Locale | Folder | Trạng thái |
|---|---|---|
| English (default) | `values/` | ✅ Source of truth |
| Arabic | `values-ar/` | ✅ Có |
| German | `values-de/` | ✅ Có |
| Spanish | `values-es/` | ✅ Có |
| French | `values-fr/` | ✅ Có |
| Italian | `values-it/` | ✅ Có |
| Norwegian Bokmål | `values-nb/` | ✅ Có |

---

## Quy tắc

1. **Source of truth** luôn là `values/strings.xml` (English).
2. Khi thêm string mới: thêm vào `values/strings.xml` trước, sau đó dịch sang các locale.
3. **Không xóa** key cũ nếu chưa kiểm tra tất cả locale — tránh string debt.
4. `TRANSLATING.md` là placeholder trống — không cần cập nhật.

---

## Thêm ngôn ngữ mới

1. Tạo folder `app/src/main/res/values-<locale>/`
2. Copy `values/strings.xml` → dịch nội dung
3. Build và chạy để verify font render (đặc biệt với RTL như Arabic)
4. Test RTL layout bằng Developer Options → Force RTL layout direction

---

## Strings quan trọng cần kiểm tra khi thêm tính năng

- `app_name` — tên app (không nên dịch)
- `note_*` — mọi string liên quan đến note
- `label_*` — mọi string liên quan đến label
- `reminder_*` — mọi string liên quan đến reminder
- `settings_*` — mọi string cài đặt
