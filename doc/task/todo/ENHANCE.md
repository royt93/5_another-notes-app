# ENHANCE Backlog — Cải tiến tính năng đã có

> **Tạo:** 2026-08-17 | Gộp từ backlog cũ ([archive/pre_2026-08-17_audit/enhance_existing_features.md](../archive/pre_2026-08-17_audit/enhance_existing_features.md)) + audit độc lập agy/claude-bypass, khử trùng theo chủ đề. Các item đã [DONE] ở backlog cũ (E-05 FAB spring, E-11 widget slideshow) không lặp lại ở đây — xem archive.
>
> Nhiều item ở đây trực tiếp giải quyết gốc rễ 1 hoặc nhiều bug trong [FIX.md](FIX.md) — đã ghi chú "Giải quyết:" khi có liên quan.

Priority: P0/P1/P2. Effort: XS/S/M/L.

---

## Kiến trúc / nền tảng (nên làm trước vì nhiều bug FIX phụ thuộc vào đây)

**ENH-A01 — ✅ HOÀN TẤT 2026-08-18 — Dọn source-set debug/release rebrand (`com.mckimquyen.notes` ↔ `com.maltaisn.notes`)**
Thử lần 2, thành công (lần 1 ở Sprint 1 bị bỏ vì tưởng nhầm là hard blocker — xem BACKLOG.md). Đã điều tra lại bằng agent, phát hiện: "blocker" gốc không phải giới hạn Dagger/Kotlin/Gradle thật — chỉ do lần code đầu đặt file debug-side sai chỗ (`src/main` thay vì `src/debug/kotlin`) với package khác `com.maltaisn.notes` (package release-side dùng), khiến `AppModule.kt` phải import cứng đường dẫn `src/main` luôn compile mọi variant. Đã sửa: chuyển `DebugExtensions.kt`/`DebugUtils.kt`/`DebugBuildTypeBehavior.kt`/`di/BuildTypeModule.kt` vào `app/src/debug/kotlin/com/maltaisn/notes/**` (khớp package release-side dùng), xoá guard `BuildConfig.ENABLE_DEBUG_FEATURES` khỏi `debugCheck`/`debugRequire` (không cần nữa, đã tách compile-time), sửa 6 call site import `debugCheck`/`debugRequire`, xoá luôn `allOpen` custom `OpenClass`/`OpenForTesting` chết (không ai dùng) và trỏ `allOpen` sang `androidx.annotation.OpenForTesting` thật (đang dùng ở `PrefsManager`/`ReminderAlarmManager`). Verify bằng cách đọc bytecode `classes.dex`: `DebugBuildTypeBehavior` có trong APK dev debug, **hoàn toàn không có** trong APK production release. Xem chi tiết `CLAUDE.md` mục "Build-variant source sets".
Effort: M. Priority: P0.

**ENH-A02 — ✅ HOÀN TẤT 2026-08-18 (giải pháp hẹp hơn ban đầu, xem lý do bên dưới) — Sửa flicker uncheck-all mà không đổi identity-based diffing**
**Đổi hướng lúc code, đã hỏi ý kiến user qua AskUserQuestion trước khi đổi:** đọc kỹ `EditableText` (interface `content`/`title` dùng cho `EditTitleItem`/`EditContentItem`/`EditItemItem`) thì thấy nó **cố tình** mutate-in-place (`append()`/`replaceAll()`) để tránh gọi `toString()` mỗi keystroke — nghĩa là content-equality diffing thuần tuý (so `content.text` giữa old/new) sẽ **không bao giờ** bắt được thay đổi text, vì cả 2 phía so sánh trỏ cùng 1 object đang được mutate sống. Đây chính là lý do identity-based diffing (`EditDiffCallback` hiện tại) tồn tại ngay từ đầu — đổi hẳn sang content-equality sẽ phá vỡ live-typing performance, không phải chỉ "sửa 1 lần ở tầng diffing" đơn giản như mô tả gốc.

**Giải pháp đã làm (nhỏ hơn, đúng scope thật của bug):** giữ nguyên `EditDiffCallback` identity-based làm mặc định an toàn. Với `uncheckAllItems()` — nơi duy nhất còn dùng `.copy()` gây flicker/mất focus (không tìm thấy chỗ nào khác dùng pattern tương tự) — đổi sang mutate `item.checked` in-place (giữ nguyên identity) + thêm `EditVM.itemsChangedInPlaceEvent` (`LiveData<Event<List<Int>>>`) để `EditFrm` gọi `adapter.notifyItemChanged(pos)` tường minh cho từng vị trí đã đổi, thay vì trông cậy vào `submitList()`/DiffUtil (vốn không thấy được thay đổi vì identity không đổi). `moveCheckedItemsToBottom()` gọi ngay sau đó vẫn qua `submitList()`/DiffUtil bình thường cho phần sắp xếp lại — và vì giờ giữ identity, DiffUtil nhận diện đúng đây là **move** thay vì remove+insert giả như trước, giảm flicker thêm ở phần reorder.

**Verify:** compile + `./gradlew test` + `compileProductionReleaseKotlin` PASS. Test tay thật trên Pixel 7 Pro: tạo list note 3 item, check 2/3, bấm "Bỏ chọn tất cả" — **cả 3 item unchecked ngay lập tức, không cần thoát vào lại**, không crash (logcat sạch `FATAL EXCEPTION`). Đúng bug FIX-M17 từng tái hiện (uncheck-all không update UI) — lần này pass thật trên device, không chỉ code review.
Effort: S thực tế (không phải M như ước tính gốc, vì scope hẹp lại). Priority: P1.

**ENH-A03 — ✅ HOÀN TẤT 2026-08-18 — Batch insert khi import thay vì N round-trip DAO riêng lẻ**
`DefaultJsonManager.importNotes()` viết lại: phân loại từng note vào 2 nhóm (insert/update) thay vì gọi `notesDao.insert()`/`update()` riêng lẻ trong loop, rồi flush 1 lần bằng `insertAll()`/`updateAll()`. `NotesDao.insertAll()` đổi return type `Unit` → `List<Long>` (Room trả đúng row ID theo thứ tự submit, kể cả note giữ ID gốc lẫn note auto-generate ID mới) để vẫn build đúng `labelRefs` mà không cần round-trip riêng lấy ID. `labelsDao.insert()` (import labels) giữ nguyên per-item — không đổi, vì logic dedup tên nhãn (FIX-M26) chạy tuần tự theo state tích luỹ, batch sẽ phức tạp hoá không tương xứng effort S của item này.
**Verify:** compile sạch cả 2 variant, `./gradlew test` PASS toàn bộ. **Chưa verify được round-trip Export→Import thật qua UI** — thử lại SAF automation qua adb (đã note lỗi ở BACKLOG.md Sprint 2) nhưng vẫn không tap trúng nút "LƯU" một cách ổn định qua uiautomator/toạ độ (grid layout DocumentsUI đổi vị trí liên tục). Đã trace kỹ logic thay thế branch-by-branch khớp code cũ, tự tin cao nhưng đây là giới hạn thật của môi trường test, không phải đã verify runtime đầy đủ.
Effort: S. Priority: P1.

**ENH-A04 — ❌ KHÔNG LÀM — Xin quyền `SCHEDULE_EXACT_ALARM` (đã thử và revert 2026-08-18)**
Đã triển khai thật (permission + toggle UI trong Settings + `setExactAndAllowWhileIdle`), nhưng **user chặn ngay sau khi push**: `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` là "sensitive permission" của Play Console, chỉ app khai báo đúng core-functionality (alarm clock/calendar) mới được duyệt — notes app không đủ điều kiện, dùng vào rủi ro bị từ chối/gỡ khỏi Store. Đã revert về `setAndAllowWhileIdle()` (không cần permission đặc biệt, vẫn tốt hơn `set()` gốc qua Doze). Xem `doc/task/BACKLOG.md` mục FIX-H04 (2 lần code, có link commit cả 2 chiều) và `doc/AD.MD`/`CLAUDE.md` phần reminder/alarm. **Đừng làm lại item này trừ khi có kế hoạch pháp lý rõ ràng để khai báo core-functionality với Google Play.**

**ENH-A05 — ✅ HOÀN TẤT 2026-08-18 — Thêm index composite cho bảng `notes` theo `(status, pinned, modified_date)`**
Room `VERSION` 8→9, migration `MIGRATION_8_9` tạo `index_notes_status_pinned_modified_date`. Verify: compile pass (Room tự validate schema/migration khớp lúc kapt), `./gradlew test` PASS, cài đè lên device thật đang có DB v8 sẵn dữ liệu — migration chạy sạch, note cũ giữ nguyên, không crash.
Effort: S. Priority: P1.

**ENH-A06 — ✅ HOÀN TẤT 2026-08-18 — Cập nhật `doc/memory_leak.md` cho khớp code hiện tại**
Làm trong đợt audit toàn bộ `doc/*.md` + `CLAUDE.md` (13 file, 3 commit riêng: `b124b0a`, `b2fd133`, `77e16e3`). `memory_leak.md` viết lại toàn bộ, đánh dấu rõ phần nào unauditable (ads SDK ngoài) và thêm mục fix leak mới của Sprint 1-3.
Effort: S. Priority: P1.

---

## UX / tính năng đã có

| ID | Tên | Mô tả | Effort | Priority | Files |
|---|---|---|---|---|---|
| ENH-01 | Checklist Bulk Actions | Toolbar nhanh: đánh dấu tất cả xong, bỏ chọn tất cả, xoá mục đã xong, sort A-Z | S | P0 | `EditVM.kt`, `EditFrm.kt` |
| ENH-02 | Độ tương phản chữ tự động theo màu nền thẻ | Tính luminance màu note để đổi màu chữ đen/trắng tương ứng — khắc phục khó đọc khi chọn màu sáng ở Dark theme | XS | P1 | `NoteListVH.kt`, `TimelineVH.kt`, `EditFrm.kt` |
| ENH-03 | Time Travel: Live Diff Highlighting | Khi kéo slider lịch sử, highlight xanh (thêm)/đỏ gạch ngang (xoá) so với bản hiện tại | M | P1 | `EditVM.kt`, `EditFrm.kt`, `model/entity/NoteHistory.kt` |
| ENH-04 | Tìm kiếm tiếng Việt không dấu | FTS hiện chưa chuẩn hoá dấu — gõ "ghi chu" không tìm ra "Ghi chú" | M | P1 | `ui/search/SearchQueryCleaner.kt`, `NotesDao.kt` |
| ENH-05 | Debounce/throttle `updateLiveStats()` và autolink rescan | Mỗi keystroke chạy full `toString()`+regex+milestone+`LinkifyCompat` trên toàn bộ note (tới 100.000 ký tự) — giật lag máy tầm trung/thấp | M | P1 | `EditFrm.kt:178-186`, `EditVM.kt:737-783` |
| ENH-06 | Nhắc nhở nhanh theo thói quen (preset chip) | "Tối nay 20h", "Sáng mai 8h", "Đầu tuần tới", tuỳ chỉnh được thay vì hard-code 3 preset cố định | S | P1 | `ReminderDlg.kt`, `ReminderVM.kt` |
| ENH-07 | Snooze nhanh ngay trên notification | Thêm action +10 phút/+1 giờ/ngày mai trực tiếp trên `NotificationCompat.Action`, không cần mở `NotificationAct` | S | P1 | `receiver/AlarmReceiver.kt` |
| ENH-08 | Auto-save debounce có flush khi rời màn hình | Debounce ghi Room DB khi gõ liên tục, đảm bảo flush ngay khi Back/chuyển app (`onPause`) | S | P1 | `EditVM.kt`, `EditFrm.kt` |
| ENH-09 | Pin animation — Spring Jump to Top | Note "nhảy" lên đầu với spring bounce nhẹ khi pin, highlight flash 300ms | M | P2 | `NoteListVH.kt`, `SpringItemAnimator.kt` (đã có từ sprint trước, mở rộng) |
| ENH-10 | Search Highlight Animation | Cross-fade highlight khi search term đổi, không re-render toàn bộ text | M | P2 | `SearchFrm.kt`, `HighlightHelper.kt` |
| ENH-11 | Đồng bộ highlight tìm kiếm với tokenizer FTS thật | `HighlightHelper` search substring đơn giản, không khớp stemming/prefix của `notes_fts` — note có thể match kết quả search mà không phần nào được highlight | M | P2 | `ui/note/HighlightHelper.kt` |
| ENH-12 | Char Limit Warning — thêm Progress Ring | Vòng tròn tiến độ nhỏ ở góc word-count khi gần chạm giới hạn ký tự | S | P2 | `EditFrm.kt`, `f_edit.xml` |
| ENH-13 | Reminder Dialog — Calendar animation + quick-pick chip | Animation nhỏ xác nhận chọn ngày + chip "Tomorrow"/"Next week"/"In 1 hour" | M | P2 | `ReminderDlg.kt`, `ReminderVM.kt` |
| ENH-14 | Widget: cấu hình được bộ lọc note-count, số lượng Recent Notes, ẩn note khoá | Không widget nào trong 4 loại khai báo `android:configure` — không có configuration Activity chuẩn AppWidget | M | P2 | `widget/*.kt`, `res/xml/widget_provider*.xml` |
| ENH-15 | Bộ lọc Widget theo Nhãn | `RecentNotesWidget` chỉ hiển thị note thuộc 1 Label cụ thể thay vì toàn bộ | M | P2 | `RecentNotesWidget.kt`, `RecentNotesWidgetService.kt` |
| ENH-16 | Widget theo dark mode giống list trong app | `RecentNotesRemoteViewsFactory` render `note.color` full-opacity, trong khi list trong app giảm alpha ở dark mode | XS | P2 | `RecentNotesWidgetService.kt`, `NoteListVH.kt:106-113` |
| ENH-17 | Debounce/coalesce refresh widget khi thao tác hàng loạt | Mỗi lệnh CRUD gọi refresh widget riêng — nên có tiện ích debounce chung phòng ngừa loop-per-item tương lai | S | P2 | `DefaultNotesRepository.kt` |
| ENH-18 | Export thêm `note_history` vào backup JSON | Hiện `exportJsonData()` không export version history — mất hoàn toàn khi cài lại app dù dữ liệu vẫn còn trong Room lúc export | S | P2 | `DefaultJsonManager.kt` |
| ENH-19 | Xem trước file đính kèm/preview dạng lưới ảnh (chuẩn bị hạ tầng cho NEW-01) | — | — | P2 | — |
| ENH-20 | PrefsManager: tránh copy toàn bộ `getAll()` mỗi lần đọc 1 property | Dùng trực tiếp `getBoolean/getInt/getString` thay vì copy map mỗi lần | S | P2 | `model/PrefsManager.kt:104-125` |
| ENH-21 | Thêm sort theo label/ngày reminder, nhớ sort riêng theo từng view | Hiện chỉ 3 field × 2 hướng, không nhớ sort riêng theo label/trạng thái | M | P2 | `ui/sort/`, `NotesDao.kt` |
| ENH-22 | Thứ tự note thủ công (kéo-thả) như 1 SortField mới | Không có cách sắp xếp thủ công hiện tại | M | P2 | `ui/sort/`, `NotesDao.kt` (thêm cột order) |
| ENH-23 | Toast báo lỗi khi share-to-note thất bại | `catch (e: IOException) {}` im lặng hiện tại, không có phản hồi UI | XS | P2 | `MainAct.kt:365-393` |
| ENH-24 | Phản hồi UI khi deep-link/thông báo trỏ tới note đã bị xoá | `MainVM.editNote(id)` hiện no-op im lặng nếu note không còn tồn tại | XS | P2 | `MainVM.kt` |
| ENH-25 | Đồng bộ logging: `e.printStackTrace()` → `Log.i/Log.e` nhất quán | | XS | P2 | `SettingsFrm.kt:112-132` |
| ENH-26 | Composite index `(noteId, timestamp)` cho `note_history` | `NoteHistoryDao` filter theo `noteId`, order/limit theo `timestamp`, hiện chỉ index đơn `noteId` | XS | P2 | `NotesDb.kt`, `model/entity/NoteHistory.kt` |
| ENH-27 | Paging 3 cho danh sách note | Hữu ích khi user có hàng nghìn note; hiện `Flow<List<...>>` load hết vào bộ nhớ mỗi render | L | P2 | `NotesDao.kt`, `HomeVM.kt` |
| ENH-28 | Haptic feedback khi swipe đạt ngưỡng threshold | Rung nhẹ khi vuốt thẻ note đạt 60% ngưỡng archive/delete | XS | P2 | `SwipeTouchHelperCallback.kt` |
| ENH-29 | Thu gọn/mở rộng nhóm ngày trong Timeline view | Chạm header ngày để đóng/mở danh sách note của ngày đó | S | P2 | `TimelineVH.kt`, `NoteVM.kt` |
| ENH-30 | Dedup `gradle.properties`: ~24 version property không dùng, một số mâu thuẫn với hard-code trong `app/build.gradle` (vd `recyclerViewVersion`, `materialVersion`, `daggerVersion`) | Rủi ro: người sau tưởng "bump version" qua sửa properties nhưng không có tác dụng | S | P2 | `gradle.properties`, `app/build.gradle` |
| ENH-31 | Tích hợp thật LeakCanary hoặc bỏ comment "done" gây hiểu lầm | `RApp.kt` ghi `//leakcanary` trong TODO "done" nhưng không có dependency nào trong gradle | XS | P2 | `RApp.kt` |

---

## Đã archive (không lặp lại chi tiết, xem file gốc)

Các item còn lại từ backlog cũ chưa merge trùng ở trên (E-04 Search Highlight đã gộp vào ENH-10, E-06 Bottom Sheet polish, E-08 Long-press elevation, E-09 Reminder calendar animation đã gộp ENH-13, E-12 Empty state animation) — xem nguyên văn tại [archive/pre_2026-08-17_audit/enhance_existing_features.md](../archive/pre_2026-08-17_audit/enhance_existing_features.md), vẫn còn giá trị, chưa bị bug audit này phủ định.
