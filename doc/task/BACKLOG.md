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

## Sprint 1 — ✅ HOÀN TẤT 2026-08-17 — 10/10 task P0

**Sprint Goal:** Vá các bug ảnh hưởng trực tiếp tới toàn vẹn dữ liệu và bảo mật người dùng, dọn nợ kỹ thuật kiến trúc DI/source-set làm nền cho các sprint sau.

Mỗi fix: 1 commit riêng, compile xác nhận (`compileDevDebugKotlin` + `compileProductionReleaseKotlin` cho các fix chạm build-variant) trước khi commit.

| # | Task | Effort | Commit | Ghi chú |
|---|---|---|---|---|
| 1 | FIX-C01 — vá export/import mất color/mood/lock | S | `5ef01b4` | |
| 2 | FIX-C02 — vá mergeNotes xoá reminder | S | `f40f3fc` | |
| 3 | FIX-C03 — vá recurrence ngày cuối tháng | XS | `6e347f9` | |
| 4 | FIX-C04 + FIX-H01 (= ENH-A01) | M | `2867827` | **Đổi hướng lúc code:** không tách source-set (AppModule.kt ở src/main không thể import class chỉ tồn tại 1 variant mà không vỡ compile variant kia) — dùng runtime guard `BuildConfig.ENABLE_DEBUG_FEATURES` trong `DebugExtensions.kt`/`DebugBuildTypeBehavior.kt` thay thế, đã hỏi ý kiến qua AskUserQuestion trước khi đổi hướng |
| 5 | FIX-H02 — sửa double-back-to-exit | XS | `ff9197a` | |
| 6 | FIX-H03 — guard binding null trong VipFrm reward callback | S | `ef249d8` | |
| 7 | FIX-H06 — vá leak Input/OutputStream import/export | XS×2 | `437c207` | |
| 8 | FIX-H07 — MainAct.onNewIntent() gọi lại handleIntent() | XS | `a267962` | |
| 9 | FIX-H08 — chuyển rateAppInApp() ra khỏi BaseAct dùng chung | XS | `53dc71c` | |
| 10 | FIX-H09 — vá double interstitial ad sau xoá note | S | `8653f03` | |

**Đã verify:** `compileDevDebugKotlin` + `compileProductionReleaseKotlin` PASS sau fix cuối cùng. `detekt` fail nhưng do lỗi config có sẵn từ trước (`config/detekt/detekt.yml` có property `formatting` không hợp lệ với version detekt hiện tại) — không liên quan tới sprint này, chưa sửa (ngoài phạm vi).
**Chưa làm:** chưa chạy `./gradlew test`/instrumented test, chưa cài lên device thật để smoke-test — cần làm trước khi merge/release.

## Sprint 2 — ✅ HOÀN TẤT 2026-08-17 — 19/19 task P1

**Sprint Goal:** Dọn hết toàn bộ backlog P1 — leak/crash risk còn sót lại, DiffUtil/animation glitch trong EditFrm, atomicity import, widget đồng bộ.

Mỗi fix: 1 commit riêng, `compileDevDebugKotlin` PASS trước khi commit. `./gradlew test` PASS (14 test suite, 0 failure) + `compileProductionReleaseKotlin` PASS sau fix cuối cùng (H05/M05 đổi constructor `DefaultJsonManager`, chạm DI graph).

| # | Task | Commit | Ghi chú |
|---|---|---|---|
| 1 | FIX-M01 — enum pref crash → fallback default | `e2d0bec` | |
| 2 | FIX-M02 — labelAddEventNav observer đăng ký 1 lần | `6d8cac2` | |
| 3 | FIX-L08 — Share intent NoSuchElementException/NPE | `3ccd139` | |
| 4 | FIX-M20 — AlarmReceiver thêm nhánh QUICKBOOT_POWERON | `2a7db3e` | |
| 5 | FIX-M11 — release URI permission khi auto-export lỗi | `fbead2d` | |
| 6 | FIX-L04 — thêm `@Keep` cho `HomeDestination.Reminders` | `19c79b4` | |
| 7 | FIX-M04 — `NoteCountWidget` dùng `goAsync()` | `4a11dd5` | |
| 8 | FIX-M13 — ẩn title note khoá trong widget | `535da8f` + `4d7ef30` (cập nhật test) | Phát hiện lúc code: có unit test cũ assert đúng hành vi lộ title — đã sửa test theo behavior mới |
| 9 | FIX-M14 — PDF export title thiếu `canvas.translate` | `6a9d090` | |
| 10 | FIX-M23 — `NonCancellable` cho các method còn thiếu | `d016cfd` | |
| 11 | FIX-P01 — `MainVM` mutex bọc try/finally | `10a081a` | |
| 12 | FIX-M03 — `EditFrm` back-callback dùng `viewLifecycleOwner` | `a5ea2a5` | |
| 13 | FIX-M07 — cancel ViewPropertyAnimator trong `onDestroyView()` | `40ae8f6` | |
| 14 | FIX-M18 — giữ trạng thái checked khi paste multi-line | `5989ec3` | |
| 15 | FIX-H10 — TextWatcher detach khi RecyclerView recycle | `3ef6af8` | |
| 16 | FIX-M17 — `uncheckAllItems()` mutate in-place thay vì `.copy()` | `61fc3d4` | ⚠️ **Revert sau khi smoke test phát hiện regression tệ hơn bug gốc — xem bên dưới.** |
| 17 | FIX-M15 — disambiguate Timeline header trùng ID | `107562e` | |
| 18 | FIX-H05 — bọc import trong `db.withTransaction{}` + catch bad data | `37b7d7e` | Inject thêm `NotesDb` vào `DefaultJsonManager` |
| 19 | FIX-M05 — refresh widget sau import JSON | `228b0ed` | Inject thêm `Context` vào `DefaultJsonManager` |

**Không đưa vào 2 sprint đầu:** FIX-H04 (exact alarm — cần thiết kế UI xin quyền, effort M), toàn bộ 23 mục P2 (để backlog, xem chi tiết từng mục trong FIX.md).

## Smoke test Sprint 2 trên device thật — 2026-08-17

Cài `devDebug` lên TECNO BG6 (Android 13, thiết bị thật), theo dõi logcat liên tục xuyên suốt, thao tác tay qua adb (screenshot + tap + uiautomator dump để lấy toạ độ chính xác). **Bắt được 2 regression thật trước khi merge** — đúng giá trị của việc smoke-test thay vì chỉ tin compile + unit test:

1. **Regression #1 (từ FIX-M02):** Di chuyển đăng ký `sharedViewModel.labelAddEventNav` vào `setupViewModelObservers()` (chạy trong `onCreate()`) khiến app **crash ngay lần mở đầu tiên** — `IllegalStateException: does not have a NavController set`, vì `NavHostFragment` chưa gắn xong NavController tại thời điểm đó. Biên dịch sạch, unit test pass, nhưng crash 100% trên device thật ngay khi mở app. **Sửa:** revert về đăng ký trong `onStart()`, thêm guard `labelAddObserverRegistered` để vẫn chỉ đăng ký 1 lần đúng như mục tiêu ban đầu của fix. Commit `0c7e7c4`.
2. **Regression #2 (từ FIX-M17):** Đổi `uncheckAllItems()` từ `.copy()` sang mutate in-place khiến `EditDiffCallback` (identity-only `===`) không phát hiện thay đổi gì → RecyclerView không rebind → bấm "Uncheck all items" **không có phản hồi UI nào cả** dù data đã đúng (xác nhận bằng cách thoát note rồi mở lại — Home list hiện đúng trạng thái unchecked). Tệ hơn bug gốc (flicker nhưng ít nhất UI cập nhật). **Sửa:** revert về `.copy()`. Commit `e37076b`.

**Đã xác nhận sống trên device (không chỉ code review):**
- FIX-H02 (double-back-to-exit): PASS — xem chi tiết Sprint 1.
- FIX-M18 (giữ checked state khi split item): PASS trực tiếp — check "Item one", nhấn Enter giữa dòng để split, item mới ("SplitPart") vẫn giữ trạng thái checked.
- FIX-M17 sau revert: PASS — "Uncheck all items" cập nhật UI ngay lập tức trên màn Edit, không cần thoát vào lại.
- FIX-L04 (`HomeDestination.Reminders` + `@Keep`): PASS gián tiếp — màn "Reminders" từ drawer mở bình thường, không crash.
- Settings, Premium/VIP (H03), Home, Notes list: mở bình thường, không crash, không ANR.
- Không có `FATAL EXCEPTION` nào trong logcat từ sau khi sửa 2 regression trên tới cuối phiên test.

**Chưa test được live** (do giới hạn automation qua adb, không phải nghi ngờ code sai): H10 (stress-scroll checklist dài), M04/M05/M13 (cần add widget thật lên home-screen), M07 (cần trúng đúng race ~200-400ms), M11 (cần simulate lỗi `openOutputStream`), M20 (cần reboot thật hoặc broadcast `QUICKBOOT_POWERON` giả lập), M01 (cần corrupt pref value), M23/P01 (cần trúng đúng thời điểm coroutine bị cancel) — các mục này đã được verify qua đọc code + biên dịch + unit test, chưa qua device.

## Sprint 3 — ✅ HOÀN TẤT 2026-08-18 — 16/23 task P2 (còn FIX-H04 để riêng)

**Sprint Goal:** Dọn backlog P2 — leak nhỏ, race hiếm gặp, edge case UX, data-integrity gap — cùng kỷ luật 1 commit/fix như 2 sprint trước.

Mỗi fix: 1 commit riêng, `compileDevDebugKotlin` PASS trước khi commit. `./gradlew test` + `compileProductionReleaseKotlin` PASS sau fix cuối cùng (14 test suite ban đầu, phát hiện 2 unit test stale từ FIX-M12 sprint trước — đã sửa riêng, xem ghi chú # 17).

| # | Task | Commit | Ghi chú |
|---|---|---|---|
| 1 | FIX-L07 — `deleteNoteForeverAndExit()` chạy tuần tự thay vì race với `exit()` | `ea87d64` | |
| 2 | FIX-L09 — không xoá file export vừa tạo trong 5 phút gần nhất | `c792c28` | |
| 3 | FIX-L10 — safe-cast `toolbarLayout.background as MaterialShapeDrawable` | `e0b9e79` | `NoteFrm.kt` + `LabelFrm.kt` |
| 4 | FIX-L11 — null `importDataLauncher` trong `onDestroy()` | `8ed5692` | |
| 5 | FIX-L12 — mở rộng action shorthand `.CREATE`/`.EDIT`/... thành fully-qualified trong Manifest | `d0db28b` | |
| 6 | FIX-P02 — safe-cast thay `as EditItemItem` thô trong `sortBy` | `a6fd3a5` | |
| 7 | FIX-M06 — dismiss `AlertDialog` thường của `VipFrm` trong `onDestroyView()` | `cdbb586` | |
| 8 | FIX-M08 — cancel coroutine validate tên label cũ trước khi launch cái mới | `40b3f3e` | |
| 9 | FIX-M09 — chỉ deselect label sau khi rename thật sự xong (so tên cũ/mới), không phải ở bất kỳ emission tiếp theo nào | `f76bdb9` | Đổi từ boolean flag sang so sánh nội dung — sửa tận gốc thay vì chỉ patch triệu chứng |
| 10 | FIX-M10 — luôn re-push sort prefs hiện tại khi `SortDialog` view được tạo lại | `118a843` | |
| 11 | FIX-M19 — reschedule alarm khi đổi timezone/giờ hệ thống | `36c75b8` | Thêm `ACTION_TIMEZONE_CHANGED`/`ACTION_TIME_CHANGED` vào Manifest + `AlarmReceiver` |
| 12 | FIX-M21 — Undo-xoá reminder định kỳ tính lại lần lặp quá hạn thay vì dùng `reminder.next` cũ | `2187126` | |
| 13 | FIX-M22 — không stack nhiều `fragment_edit` cho cùng 1 note khi tap notification lặp lại | `52b4109` | |
| 14 | FIX-M24 — đồng bộ default `strikethroughChecked` giữa Kotlin và `prefs.xml` | `0c84d3a` | |
| 15 | FIX-M25 — xoá password khỏi bộ nhớ sau khi dùng (`PBEKeySpec.clearPassword()` + `SavedStateHandle`) | `db23b8b` | |
| 16 | FIX-M26 — dedup tên label không phân biệt hoa-thường (`COLLATE NOCASE` + import merge) | `130bfed` | |
| 17 | *(ngoài backlog)* Sửa 2 unit test stale còn dùng `getRecentNotes(5)` từ FIX-M12 sprint trước | `82c9a84` | Phát hiện khi chạy `./gradlew test` cuối sprint — không phải regression từ sprint này |

**Không đưa vào sprint này:** FIX-H04 (exact alarm — cần thiết kế UI xin quyền trước, effort M, không phải 1-dòng-sửa — để riêng chờ quyết định UX).

**Device smoke test:** chưa chạy lại trên device thật cho batch này — toàn bộ 16 fix đều nhỏ hơn/rủi ro thấp hơn batch P0/P1 (leak nhỏ, race hiếm, edge-case UX), đã verify qua đọc code + biên dịch + unit test đầy đủ.
