# ENHANCE Backlog — Cải tiến tính năng đã có

> **Tạo:** 2026-08-17 | Gộp từ backlog cũ ([archive/pre_2026-08-17_audit/enhance_existing_features.md](../archive/pre_2026-08-17_audit/enhance_existing_features.md)) + audit độc lập agy/claude-bypass, khử trùng theo chủ đề. Các item đã [DONE] ở backlog cũ (E-05 FAB spring, E-11 widget slideshow) không lặp lại ở đây — xem archive.
>
> Nhiều item ở đây trực tiếp giải quyết gốc rễ 1 hoặc nhiều bug trong [FIX.md](FIX.md) — đã ghi chú "Giải quyết:" khi có liên quan.

Priority: P0/P1/P2. Effort: XS/S/M/L.

---

## Kiến trúc / nền tảng (nên làm trước vì nhiều bug FIX phụ thuộc vào đây)

**ENH-A01 — Dọn source-set debug/release rebrand (`com.mckimquyen.notes` ↔ `com.maltaisn.notes`)**
Gộp 1 epic sửa: `DebugExtensions.kt` (2 bản, xem FIX-C04), `BuildTypeModule` (2 bản, xem FIX-H01), `allOpen` annotation FQN trong `app/build.gradle` (nếu trỏ sai package `OpenClass`). Việc migrate rebrand chưa hoàn tất đúng cấu trúc source-set Android chuẩn — sửa 1 lần thay vì vá từng triệu chứng.
Effort: M. Priority: P0.

**ENH-A02 — Refactor `EditListItem` sang content-equality diffing thay vì identity (`===`)**
Gốc rễ chung của FIX-M17 (uncheck-all nhấp nháy) và các bug DiffUtil liên quan checklist khác. Sửa 1 lần ở tầng diffing thay vì vá từng triệu chứng riêng lẻ.
Effort: M. Priority: P1.

**ENH-A03 — Batch insert khi import thay vì N round-trip DAO riêng lẻ**
`importNotes`/`importLabels` gọi `insert`/`update` từng cái một dù `insertAll`/`updateAll` đã có sẵn trong `NotesDao`. Vừa nhanh hơn vừa là 1 phần cách giải quyết FIX-H05 (atomicity import).
Effort: S. Priority: P1.

**ENH-A04 — Xin quyền `SCHEDULE_EXACT_ALARM` đúng cách + toggle trong Settings**
Bổ sung UI cho user chọn đánh đổi pin vs. đúng giờ, fallback `setAndAllowWhileIdle` khi từ chối. Giải quyết trọn vẹn FIX-H04.
Effort: M. Priority: P1.

**ENH-A05 — Thêm index composite cho bảng `notes` theo `(status, pinned, modified_date)`**
`NotesDao.getByStatus`/`search` filter/sort đúng các cột này mỗi lần render list/search nhưng schema hiện chỉ có PK.
Effort: S. Priority: P1.

**ENH-A06 — Cập nhật `doc/memory_leak.md` cho khớp code hiện tại**
Doc tham chiếu file không còn tồn tại (ads SDK đã tách ra thư viện ngoài). Xem mục "dọn dẹp tài liệu" trong FIX.md.
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
