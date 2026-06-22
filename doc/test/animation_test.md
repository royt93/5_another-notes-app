# Animation Test Cases — Another Notes App

> **Created:** 2026-06-22 | **Version:** 2026.06.22
> **Mục đích:** Kiểm tra toàn bộ animation/transition trong app trước mỗi release
> **Phạm vi:** Fragment transitions, shared element, local animators, swipe, RecyclerView

---

## Hướng dẫn chạy test

1. Build `assembleDevDebug` và cài lên device vật lý (không dùng emulator — animation bị lag)
2. Bật **Developer Options → Window animation scale: 1x, Transition animation scale: 1x, Animator duration scale: 1x**
3. Thực hiện từng test case theo thứ tự
4. Mỗi test case: ✅ Pass / ❌ Fail / ⚠️ Flaky — ghi lại kết quả vào cột Status

---

## A. Fragment Transitions — Material Motion

### A-1: Home → Edit (MaterialContainerTransform từ FAB)

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| A-1-1 | Mở app, vào Home screen | FAB hiển thị bình thường |
| A-1-2 | Tap FAB "+" | FAB morphing expand → màn hình Edit; background mờ dần; không có blink/flash |
| A-1-3 | Nhấn Back | Edit screen morphing shrink → FAB; FAB tái xuất hiện đúng vị trí |
| A-1-4 | Lặp lại A-1-2 và A-1-3 × 5 lần | Không có jank, FAB luôn quay về đúng vị trí |

**Implementation:** `HomeFrm` — `exitTransition = Hold()`, `EditFrm` — `sharedElementEnterTransition = MaterialContainerTransform`
**Priority:** CRITICAL

---

### A-2: Note card → Edit (MaterialContainerTransform từ card)

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| A-2-1 | Home screen có ít nhất 1 note | List note hiển thị |
| A-2-2 | Tap 1 note card | Card morphing expand thành Edit screen; card background màu giống card; không blink |
| A-2-3 | Nhấn Back | Edit screen morphing shrink về đúng card vị trí cũ |
| A-2-4 | Scroll list → tap note ở giữa màn hình | Transition chạy đúng, không tính toán sai vị trí |
| A-2-5 | Scroll list → tap note gần đáy màn hình | Transition chạy đúng, không clip cứng |
| A-2-6 | Tap note → ngay lập tức nhấn Back (trước khi transition xong) | Không crash, transition reverse gracefully |

**Implementation:** `NoteFrm.kt:81-86` — `sharedElementEnterTransition = MaterialContainerTransform`; `transitionName = "noteContainer$noteId"`
**Priority:** CRITICAL

---

### A-3: MaterialElevationScale — các screen navigation

| Screen | Hành động | Kỳ vọng |
|---|---|---|
| A-3-1 Search | Home → mở Search (tap search icon) | Scale-down fade-out cho Home, scale-up fade-in cho Search |
| A-3-2 Search | Nhấn Back từ Search | Reverse: scale-down fade-out cho Search, scale-up fade-in cho Home |
| A-3-3 Labels | Home → Drawer → Labels | Scale-down/up animation giữa hai screen |
| A-3-4 Labels | Nhấn Back từ Labels | Reverse animation |
| A-3-5 Settings | Drawer → Settings | Scale-down/up animation |
| A-3-6 Settings | Nhấn Back từ Settings | Reverse animation |
| A-3-7 Chain | Home → Search → Back → Labels → Back | Mỗi transition độc lập, không ghosting từ transition trước |

**Duration expected:** ~300ms mỗi chiều (default Material)
**Priority:** HIGH

---

### A-4: VipFrm — MaterialSharedAxis(X)

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| A-4-1 | Navigate đến VipFrm (từ Settings hoặc banner) | Slide-in từ phải sang trái (X-axis forward); đồng thời màn hình cũ slide ra trái |
| A-4-2 | Nhấn Back từ VipFrm | Slide-in từ trái sang phải (X-axis backward) |
| A-4-3 | Vào VipFrm và chờ 1 giây | Crown icon đang pulse, ring đang glow, 3 sparkle đang drift với stagger 400ms |
| A-4-4 | Rotate device khi ở VipFrm | AnimatorSet restart đúng sau rotation, không bị freeze |

**Priority:** HIGH

---

## B. VipFrm — Local Animators

### B-1: Entrance animation

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| B-1-1 | Mở VipFrm lần đầu | `heroContainer` fade-in từ alpha=0 + translateY=40dp về 0; duration ~400ms |
| B-1-2 | Các row benefits bên dưới | Stagger fade-in từ trên xuống, delay ~60-80ms mỗi row |
| B-1-3 | Trong khi entrance đang chạy, nhấn Back ngay | Không crash; animators bị cancel, không leak |

**File:** `VipFrm.kt:214` — `playEntranceAnimation()`
**Priority:** MEDIUM

---

### B-2: Decorative loop animations

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| B-2-1 | Quan sát `crownIcon` | Pulse liên tục (scale/alpha oscillating), không bao giờ dừng |
| B-2-2 | Quan sát `glowRing` | Glow animation (alpha + radius) liên tục |
| B-2-3 | Quan sát 3 sparkle | Sparkle 1 bắt đầu ngay, sparkle 2 delay 400ms, sparkle 3 delay 800ms |
| B-2-4 | Background app hoặc lock screen, quay lại | Animation tiếp tục đúng, không bị "glitch" jump |
| B-2-5 | Điều hướng ra khỏi VipFrm | `animators.forEach { it.cancel() }` → không có gì leak |

**File:** `VipFrm.kt:196-212` — `startDecorativeAnimations()`, `runLoopAnim()`
**Priority:** MEDIUM

---

## C. Swipe Animation

### C-1: AVD swipe icon

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| C-1-1 | Swipe note sang phải (archive) | Sau khi đạt ngưỡng ITEM_SWIPE_LOCK (7.5%), icon archive xuất hiện và bắt đầu AVD animate |
| C-1-2 | Swipe note sang trái (delete) | Icon delete xuất hiện và AVD animate |
| C-1-3 | Swipe đủ ngưỡng rồi thả | Icon continue animate cho đến khi item biến mất |
| C-1-4 | Swipe đến ngưỡng rồi kéo ngược lại | Icon biến mất, note trở về vị trí cũ (clearView reset alpha=1, translationX=0) |

**File:** `SwipeTouchHelperCallback.kt` — `AnimatedVectorDrawable.start()`
**Priority:** HIGH

---

### C-2: Swipe background color

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| C-2-1 | Swipe sang phải (archive) | Background xanh `#4CAF50` xuất hiện sau card, alpha 200 |
| C-2-2 | Swipe sang trái (delete) | Background đỏ `#F44336` xuất hiện sau card, alpha 200 |
| C-2-3 | Swipe với corner radius | Background có corner 16dp, không vuông cứng |
| C-2-4 | Swipe note với dark theme | Background đủ contrast trên nền tối |

**File:** `SwipeTouchHelperCallback.kt:SWIPE_COLOR_*`, `swipeBgPaint`
**Priority:** HIGH

---

## D. Status Bar Animation

### D-1: LabelFrm status bar color transition

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| D-1-1 | Navigate vào LabelFrm (label màu đỏ) | Status bar transition mượt sang màu đỏ của label |
| D-1-2 | Navigate vào LabelFrm (label màu xanh) | Status bar transition mượt sang màu xanh |
| D-1-3 | Nhấn Back từ LabelFrm | Status bar transition mượt về màu mặc định |
| D-1-4 | Rotate device ở LabelFrm | Status bar giữ đúng màu, không flash |

**File:** `LabelFrm.kt:230-234` — `ValueAnimator.ofObject(ArgbEvaluator(), ...)`
**Priority:** MEDIUM

---

### D-2: NoteFrm status bar color transition

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| D-2-1 | Vào note list thuộc label có màu | Status bar animate sang màu label |
| D-2-2 | Navigate ra khỏi | Transition mượt về màu default |
| D-2-3 | Animate đang chạy → Back nhanh | `statusBarAnimator?.cancel()` trước khi null — không crash |

**File:** `NoteFrm.kt:230-234`
**Priority:** MEDIUM

---

## E. RecyclerView Item Animator

### E-1: HomeFrm / NoteFrm item animator

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| E-1-1 | Tạo note mới → quay về Home | Note mới xuất hiện ở đầu list (nếu mới nhất) với fade-in animation |
| E-1-2 | Pin 1 note | Note jump lên đầu list; các note khác trượt xuống với animate |
| E-1-3 | Unpin 1 note | Note trượt xuống đúng vị trí |
| E-1-4 | Change status note (archive) | Note fade-out và remove khỏi list với animation |
| E-1-5 | Đổi layout Grid ↔ List | Không có animation bị duplicate; `supportsChangeAnimations = false` đảm bảo không blink |

**File:** `NoteFrm.kt:165` — custom `DefaultItemAnimator` với `supportsChangeAnimations = false`
**Priority:** MEDIUM

---

### E-2: SearchFrm item animator

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| E-2-1 | Gõ 1 ký tự vào search box | Kết quả update — không có blink/flash trên items (vì `supportsChangeAnimations = false`) |
| E-2-2 | Xóa từng ký tự | Kết quả update mượt, không jumpy |
| E-2-3 | Clear search text | List về rỗng hoặc all notes, không crash |

**File:** `SearchFrm.kt:61` — `(rcv.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false`
**Priority:** LOW

---

## F. Window Transitions

### F-1: Activity enter/exit (fade)

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| F-1-1 | Mở app từ launcher | SplashAct fade-in (từ splash background) |
| F-1-2 | SplashAct → MainAct | Fade transition giữa hai activity |
| F-1-3 | Back từ app | MainAct fade-out về launcher |

**Theme:** `WindowAnimationTransition` — `fade_in` / `fade_out`
**Priority:** LOW

---

## G. Edge Cases & Regression

### G-1: Transition dưới low memory

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| G-1-1 | Bật Developer Options → Limit background processes: No background processes | Mở app → navigate → animation vẫn chạy đúng |
| G-1-2 | Background app, mở app khác nặng, quay lại | SharedElement transition vẫn chạy, không bị skip |

---

### G-2: Rapid navigation (stress test)

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| G-2-1 | Tap note → Back → Tap note → Back × 10 lần nhanh | Không crash, không ANR, không memory leak |
| G-2-2 | Tap note → rotate ngay → Back | Không crash, transition reset đúng |
| G-2-3 | Navigate VipFrm → Back → VipFrm → Back × 5 | AnimatorSet không bị double-start, không leak |

---

### G-3: Accessibility animation off

| Bước | Hành động | Kỳ vọng |
|---|---|---|
| G-3-1 | Settings → Accessibility → Remove animations (hoặc animation scale = 0) | App vẫn hoạt động đúng, không crash; transitions tức thì |
| G-3-2 | Navigation giữa các screen khi animation off | Không có layout glitch hay blank frame |

---

## H. Checklist trước release

```
[ ] A-1: Home → Edit FAB transition chạy đúng
[ ] A-2: Card → Edit transition chạy đúng
[ ] A-3: MaterialElevationScale trên tất cả screens
[ ] A-4: VipFrm SharedAxis transition
[ ] B-1: VipFrm entrance animation
[ ] B-2: VipFrm decorative loops không leak
[ ] C-1: AVD swipe icons
[ ] C-2: Swipe background colors
[ ] D-1: LabelFrm status bar animation
[ ] E-1: RecyclerView item animations
[ ] G-2: Rapid navigation stress test — no crash
[ ] G-3: Accessibility animation off — no crash
```
