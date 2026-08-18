# Multi-Language — Another Notes App

> **Updated:** 2026-08-18 — bảng dưới regen trực tiếp từ `app/src/main/res/values-*/` (30 locale thật, không tính `-night`/`-v23`/`-w600dp`... là qualifier khác, không phải ngôn ngữ)

---

## Ngôn ngữ được hỗ trợ

| Locale | Folder | Trạng thái |
|---|---|---|
| English (default) | `values/` | ✅ Source of truth |
| Arabic | `values-ar/` | ✅ Có |
| Bulgarian | `values-bg/` | ✅ Có |
| Czech | `values-cs/` | ✅ Có |
| Danish | `values-da/` | ✅ Có |
| German | `values-de/` | ✅ Có |
| Greek | `values-el/` | ✅ Có |
| Spanish | `values-es/` | ✅ Có |
| Finnish | `values-fi/` | ✅ Có |
| French | `values-fr/` | ✅ Có |
| Hindi | `values-hi/` | ✅ Có |
| Croatian | `values-hr/` | ✅ Có |
| Hungarian | `values-hu/` | ✅ Có |
| Indonesian | `values-id/` | ✅ Có |
| Italian | `values-it/` | ✅ Có |
| Japanese | `values-ja/` | ✅ Có |
| Korean | `values-ko/` | ✅ Có |
| Malay | `values-ms/` | ✅ Có |
| Norwegian Bokmål | `values-nb/` | ✅ Có |
| Dutch | `values-nl/` | ✅ Có |
| Polish | `values-pl/` | ✅ Có |
| Portuguese | `values-pt/` | ✅ Có |
| Romanian | `values-ro/` | ✅ Có |
| Russian | `values-ru/` | ✅ Có |
| Slovak | `values-sk/` | ✅ Có |
| Swedish | `values-sv/` | ✅ Có |
| Thai | `values-th/` | ✅ Có |
| Turkish | `values-tr/` | ✅ Có |
| Ukrainian | `values-uk/` | ✅ Có |
| Vietnamese | `values-vi/` | ✅ Có |
| Chinese | `values-zh/` | ✅ Có |

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
