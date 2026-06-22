# New Exclusive Features — Another Notes App

> **Created:** 2026-06-22 | **Status:** 📋 Picked (chờ estimate và prioritize)

Các tính năng **độc quyền** chưa thấy ở Google Keep, Samsung Notes, hoặc các app ghi chú phổ biến.

---

## F-01: Note Mood / Energy Tag (⭐ Độc quyền cao)

**Mô tả:** User có thể gắn "mood" cho mỗi note — 5 icon cảm xúc (😄 😐 😔 💡 🔥). Hiển thị nhỏ góc card. Filter và sort theo mood.

**Giá trị:** Biến notes thành journal/diary cá nhân hóa. Keep và Samsung Notes không có.

**Tech:**
- Thêm column `mood: Int?` vào entity `Note` → migration Room 5→6
- UI: row icon picker trong EditFrm (bottom toolbar)
- Home: filter chip "By Mood" trong drawer

**Effort:** M (3-4 ngày)
**Files chính:** `Note.kt`, `EditFrm.kt`, `HomeFrm.kt`, Room migration

---

## F-02: Spring-Physics FAB Animation (⭐ Độc quyền animation)

**Mô tả:** FAB bounces với spring physics khi scroll. Khi scroll xuống — FAB co lại (shrink to icon only). Khi scroll lên — FAB expand về đầy đủ với spring overshoot. Dùng `SpringAnimation` từ `dynamicanimation`.

**Giá trị:** Cảm giác "alive" cho UI. Khác với hide/show FAB thông thường.

**Tech:**
- `SpringAnimation(fab, DynamicAnimation.TRANSLATION_Y)`
- `SpringForce.STIFFNESS_MEDIUM`, `SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY`
- Kết hợp với `RecyclerView.addOnScrollListener`

**Effort:** S (1-2 ngày)
**Files chính:** `HomeFrm.kt`, `NoteFrm.kt`

---

## F-03: Note Locking với Biometric (⭐ Bảo mật độc quyền)

**Mô tả:** User có thể lock 1 note cụ thể bằng fingerprint/face. Note bị lock hiển thị icon khóa thay vì content. Cần xác thực để mở.

**Giá trị:** Bảo mật từng note riêng lẻ (không phải app-wide lock). Đây là tính năng ít app ghi chú free làm.

**Tech:**
- `BiometricPrompt` + `CancellationSignal`
- Column `isLocked: Boolean` trong Room
- `NoteListVH` hiển thị placeholder khi locked
- Không encrypt content (chỉ UI gate) hoặc có thể encrypt với `EncryptedSharedPreferences`

**Effort:** M (3-5 ngày)
**Files chính:** `Note.kt`, `NoteListVH.kt`, `EditFrm.kt`, Room migration

---

## F-04: Word Count Milestone Celebration (⭐ Gamification)

**Mô tả:** Khi user đạt milestone từ (100, 500, 1000, 5000 từ trong 1 note), hiển thị một celebration animation nhỏ (confetti/particle burst) và snackbar "🎉 500 words written!".

**Giá trị:** Motivate writing, tạo cảm giác achievement. Unique với notes app.

**Tech:**
- `EditVM` track word count milestones (chỉ trigger 1 lần/milestone)
- Particle view hoặc Lottie animation overlay ngắn 1.5s
- Snackbar màu cam/vàng

**Effort:** S-M (2-3 ngày)
**Files chính:** `EditVM.kt`, `EditFrm.kt`

---

## F-05: Smart Duplicate Detection (⭐ AI-adjacent)

**Mô tả:** Khi user tạo note mới với nội dung giống note đã có (>80% similar), hiện banner cảnh báo "Bạn có thể đã có note tương tự: [Title]" với option xem hoặc bỏ qua.

**Giá trị:** Tránh duplicate notes — vấn đề thực sự khi dùng lâu. Không app nào làm.

**Tech:**
- Simple Jaccard similarity hoặc shingle comparison (không cần AI/ML)
- Tính trong background (Dispatchers.Default) sau khi user dừng gõ 2s
- Result cache bằng `Flow` debounce

**Effort:** M (3-4 ngày)
**Files chính:** `EditVM.kt`, `EditFrm.kt`, `NotesRepository`

---

## F-06: Note Timeline View (⭐ Khác biệt layout)

**Mô tả:** Thêm layout thứ 3 (ngoài List và Grid): Timeline — note sắp xếp theo ngày tạo/chỉnh sửa, nhóm theo ngày, với đường timeline bên trái. Mỗi ngày là 1 section header.

**Giá trị:** Biến notes app thành personal diary. Unique UI so với Keep/Samsung.

**Tech:**
- `StickyHeaderDecoration` hoặc header item trong adapter
- Layout `i_note_timeline.xml` — card nhỏ hơn, ít info hơn
- Toggle view type: `LIST | GRID | TIMELINE`

**Effort:** L (5-7 ngày)
**Files chính:** `NoteAdt.kt`, `HomeFrm.kt`, layout XMLs, `PrefsManager`

---

## F-07: Shake to Undo (⭐ Gesture độc quyền)

**Mô tả:** Khi user vừa xóa/archive 1 note, lắc device trong vòng 5 giây để undo. Hiển thị "Shake to undo" snackbar với countdown.

**Giá trị:** Fun UX, memorable. Không app ghi chú nào làm.

**Tech:**
- `SensorManager` + `SensorEventListener` với threshold acceleration
- Chỉ active trong 5s sau action destructive
- Fallback: snackbar Undo button vẫn còn

**Effort:** S (1-2 ngày)
**Files chính:** `HomeFrm.kt`, `MainAct.kt`

---

## F-08: Note Reading Mode (⭐ Productivity)

**Mô tả:** Trong EditFrm, nút "Reading Mode" — ẩn keyboard và tất cả editing UI, chỉ hiển thị text ở font size lớn hơn, background tối hơn, margin rộng hơn. Như e-reader.

**Giá trị:** Note review dễ hơn, không vô tình chạm vào text. Unique.

**Tech:**
- Toggle state trong `EditFrm`
- Animate hide/show controls với `ViewPropertyAnimator`
- Tăng text size lên 18sp, tăng line spacing

**Effort:** S (1-2 ngày)
**Files chính:** `EditFrm.kt`, `f_edit.xml`

---

## F-09: Note Export to PDF / Image (⭐ Sharing độc quyền)

**Mô tả:** Export note thành file PDF hoặc ảnh PNG để share. PDF dùng `PdfDocument` API, image dùng View → Bitmap.

**Giá trị:** Sharing note như tài liệu đẹp. Keep chỉ share text thuần.

**Tech:**
- `PdfDocument` + `PdfDocument.Page` (Android native, không cần lib)
- `View.drawToBitmap()` cho PNG
- Share via `FileProvider` + `ACTION_SEND`

**Effort:** M (3-4 ngày)
**Files chính:** `EditFrm.kt`, `EditVM.kt`, hoặc thêm `ExportHelper.kt`

---

## F-10: Focus Mode — Hide All, Write Only (⭐ Productivity)

**Mô tả:** Một floating button "Focus" trong EditFrm — khi bật, ẩn status bar, navigation bar, toolbar, word count. Chỉ còn text field. Thoát bằng tap đôi.

**Giá trị:** Distraction-free writing như iA Writer / Obsidian. Chưa có app ghi chú Android free nào làm tốt.

**Tech:**
- `WindowInsetsController.hide(WindowInsets.Type.systemBars())`
- Animate toolbar slide-up, word count fade-out
- Detect double-tap để exit

**Effort:** S-M (2-3 ngày)
**Files chính:** `EditFrm.kt`
