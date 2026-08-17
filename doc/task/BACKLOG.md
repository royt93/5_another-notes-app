# Product Backlog — Another Notes App

> **Tạo:** 2026-08-17 | **Người lập:** Claude Code, tổng hợp từ 6 nguồn audit độc lập (4 subagent nội bộ chia theo tầng kiến trúc data/UI/ads-widget/reminder + agy CLI + Claude CLI `--dangerously-skip-permissions`, mỗi nguồn đọc toàn bộ ~130 file Kotlin từ đầu độc lập với nhau). Codex CLI hết quota, không có kết quả (usage limit tới 2026-08-20).
>
> Các claim quan trọng nhất đã được **tự đọc lại source code thật để xác minh** trước khi đưa vào backlog — xem nhãn ✅/🔁/⚠️ trong từng file.

## Cấu trúc backlog

| File | Nội dung | Số item |
|---|---|---|
| [todo/FIX.md](todo/FIX.md) | Bug thật trong code hiện tại — Critical/High/Medium/Low, có file:line + kịch bản tái hiện | 4 Critical, 10 High, 26 Medium, 12 Low + 4 cần xác minh thêm |
| [todo/ENHANCE.md](todo/ENHANCE.md) | Cải tiến tính năng đã có | 6 kiến trúc-nền tảng + 31 UX |
| [todo/NEW_FEATURE.md](todo/NEW_FEATURE.md) | Tính năng mới hợp lý, chưa tồn tại | 18 |
| [todo/IDEA.md](todo/IDEA.md) | Ý tưởng thô, cần nghiên cứu thêm | 15 |
| [todo/EXCLUSIVE.md](todo/EXCLUSIVE.md) | Tính năng độc quyền/khác biệt cạnh tranh | 5 (đã có hạ tầng) + 9 (xây mới) |
| [archive/pre_2026-08-17_audit/](archive/pre_2026-08-17_audit/) | Backlog cũ (2026-06-22/27), giữ lại tham khảo — đã supersede bởi bộ trên nhưng vẫn còn item chưa bị phủ định | — |
| [audit_raw/](audit_raw/) | Output thô, chưa lọc, từ agy CLI và Claude CLI bypass-permission — nguồn cho các file trên | 2 |

## Phát hiện quan trọng nhất

1. **FIX-C01 (Critical, xác minh 3 nguồn độc lập):** Export/Import JSON backup xoá mất `color`, `mood`, và **mở khoá lại note đã khoá bằng sinh trắc học**. Đây là hồi quy bảo mật thật sự — bất kỳ ai backup rồi restore đều bị mất khoá bảo vệ note nhạy cảm mà không có cảnh báo gì. **Nên sửa trước khi làm bất cứ việc gì khác.**
2. **FIX-C02/C03 (Critical, tự verify):** 2 bug logic âm thầm trong tính năng lõi — reminder bị xoá khi merge JSON dù không đổi gì; recurrence "ngày cuối tháng" tính sai vì nhầm `Calendar.MONTH` với `Calendar.DAY_OF_MONTH`.
3. **FIX-C04/FIX-H01 (Critical+High, cùng gốc):** Cơ chế source-set debug/release từ việc rebrand `com.maltaisn.notes` → `com.mckimquyen.notes` chưa hoàn tất đúng — khiến release build vẫn chạy code debug thật (throw invariant check thật thay vì no-op, chèn note rác Lorem-Ipsum vào DB thật). Nên gộp sửa 1 lần (xem ENH-A01).
4. **FIX-H02:** Tính năng "bấm back 2 lần để thoát" hoàn toàn không hoạt động — mọi lần bấm back ở Home đều thoát app ngay lập tức, không phải edge case. Fix cực rẻ (XS), impact 100% người dùng — ưu tiên cao dù nhìn qua có vẻ "chỉ là UX nhỏ".
5. **Nhiều tính năng "mới" trong backlog cũ thực ra đã được làm rồi:** mood tag, note color, khoá note bằng sinh trắc học, Timeline view, Time Travel, export PDF/ảnh, Reading mode, VIP/premium screen. Xem cảnh báo đầu [NEW_FEATURE.md](todo/NEW_FEATURE.md) — đừng làm lại.
6. **`doc/memory_leak.md` đã lỗi thời:** ads SDK (`AdMobManager`) đã được tách ra thư viện ngoài closed-source `com.roy.sdkadbmob`, không audit được implementation từ repo này nữa. Cần cập nhật doc.

## Kết quả duyệt priority — user 2026-08-17

Toàn bộ 52 issue trong [FIX.md](todo/FIX.md) (4 Critical + 10 High + 26 Medium + 12 Low) cộng 4 mục "cần xác minh thêm" đã được review từng cái một qua `AskUserQuestion`, với 3 mục được tự đọc lại code để xác minh thêm ngay trong lúc duyệt (FIX-M14 PDF export, FIX-M15 Timeline header ID, FIX-L08/L09/L10/L11, EditVM actualPos, ClassCastException). Kết quả:

| Priority | Số lượng | Danh sách |
|---|---|---|
| **P0 — sửa ngay** | 10 | C01, C02, C03, C04+H01 (gộp ENH-A01), H02, H03, H06, H07, H08, H09 |
| **P1 — sớm** | 19 | H05, H10, M01, M02, M03, M04, M05, M07, M11, M13, M14✅, M15✅, M17, M18, M20, M23, L04, L08✅(nâng từ Low, crash thật), P01 (MainVM mutex) |
| **P2 — backlog** | 23 | H04, M06, M08, M09, M10, M12, M19, M21, M22, M24, M25, M26, L01, L02, L03, L05, L06, L07, L09✅, L10✅, L11✅, L12, P02 |
| **Loại bỏ (debunked)** | 2 | M16 (ReminderDlg permission — code đúng chuẩn), EditVM actualPos IndexOutOfBounds (đã trace hết đường mutate, không có gap) |

✅ = đã tự đọc lại source code xác nhận trong lúc duyệt priority, không chỉ dựa vào audit gốc.

## Đề xuất Sprint kế tiếp (2 tuần, theo phong cách Scrum) — 10 task P0

**Sprint Goal:** Vá các bug ảnh hưởng trực tiếp tới toàn vẹn dữ liệu và bảo mật người dùng, dọn nợ kỹ thuật kiến trúc DI/source-set làm nền cho các sprint sau.

| # | Task | Effort | Vì sao ưu tiên |
|---|---|---|---|
| 1 | FIX-C01 — vá export/import mất color/mood/lock | S | Hồi quy bảo mật, ảnh hưởng mọi user backup/restore |
| 2 | FIX-C02 — vá mergeNotes xoá reminder | S | Mất dữ liệu âm thầm, không có cảnh báo |
| 3 | FIX-C03 — vá recurrence ngày cuối tháng | XS | 1 dòng, impact cao cho user dùng reminder định kỳ |
| 4 | FIX-C04 + FIX-H01 (= ENH-A01) — dọn source-set debug/release | M | Chặn release build "sạch" thật sự, nền cho toàn bộ variant sau này |
| 5 | FIX-H02 — sửa double-back-to-exit | XS | Rẻ, impact 100% user |
| 6 | FIX-H03 — guard binding null trong VipFrm reward callback | S | Crash path thật, dễ tái hiện, ảnh hưởng doanh thu ad |
| 7 | FIX-H06 — vá leak Input/OutputStream import/export | XS×2 | Rẻ, dễ |
| 8 | FIX-H07 — MainAct.onNewIntent() gọi lại handleIntent() | XS | Reminder tap khi app đang mở bị no-op |
| 9 | FIX-H08 — chuyển rateAppInApp() ra khỏi BaseAct dùng chung | XS | Ảnh hưởng rating Store trực tiếp |
| 10 | FIX-H09 — vá double interstitial ad sau xoá note | S | Rủi ro chính sách AdMob |

**Sprint kế tiếp (19 task P1, gợi ý gom theo khu vực code để giảm context-switch):**
- Nhóm import/export (chung `DefaultJsonManager.kt`/`SettingsVM.kt`): H05, H06(đã làm ở sprint 1, bỏ qua), M05, M11
- Nhóm EditFrm/checklist: H10, M07, M17, M18
- Nhóm reminder/alarm: M20, P01 (MainVM mutex, khác file nhưng cùng chủ đề "tạo note/reminder ổn định")
- Nhóm lẻ còn lại: M01, M02, M03, M04, M13, M14, M15, M23, L04, L08

**Không đưa vào 2 sprint đầu:** FIX-H04 (exact alarm — cần thiết kế UI xin quyền, effort M), toàn bộ 23 mục P2 (để backlog, xem chi tiết từng mục trong FIX.md).
