# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

The project is a single-module Android app (`:app`) using the Gradle wrapper. The Gradle daemon expects Java 17 (`toolchain.languageVersion = 17`); `gradle.properties` pins `org.gradle.java.home` to a local JDK 17 install (JetBrains Runtime) — adjust that line if your machine has a different JDK path. Note: `assembleProductionRelease`'s `lintVitalAnalyzeProductionRelease` task currently crashes (`NoSuchMethodError: List.removeLast()`) on this JDK 17 setup — a known AGP-lint-vs-JDK mismatch, not a code bug. Use `-x lintVitalAnalyzeProductionRelease` to build a release APK/AAB anyway.

Common Gradle tasks (run from repo root):

```bash
./gradlew assembleDevDebug                 # primary dev build (debug AdMob test IDs)
./gradlew assembleProductionRelease        # signed release; needs keystore.properties at repo root (gitignored, not KS_PW/KS_ALIAS in gradle.properties anymore)
./gradlew installDevDebug                  # install to connected device/emulator
./gradlew detekt                           # static analysis (config/detekt/detekt.yml, autoCorrect=true)
./gradlew test                             # unit tests for the JVM
./gradlew connectedDevDebugAndroidTest     # instrumented tests on a device/emulator
./gradlew clean
```

The root `build` task is wired to skip `check` (`tasks.named("build") { setDependsOn(... - "check") }`), so you must invoke `detekt`/`test` explicitly.

APKs are renamed on output to `com.mckimquyen.notes<buildType>_<versionName>_<versionCode>.apk` — look in `app/build/outputs/apk/<flavor>/<buildType>/`.

## Build variants

Two product flavors × two build types:

- `dev` / `production` (flavor dimension `type`) — both share `applicationId = com.mckimquyen.notes`; only `app_name` and `FLAVOR_buildEnv` differ.
- `debug` / `release` — debug uses Google's sample AdMob unit IDs; release wires real IDs and enables `minifyEnabled` + `shrinkResources` + signing via `keystore.properties` (gitignored, repo root; loaded in `app/build.gradle` as `storeFile`/`storePassword`/`keyAlias`/`keyPassword`). The actual `.jks` lives outside this repo in the private `myKeyStore` repo (`com.mckimquyen.notes/keystore.jks`) — never commit the keystore file or its passwords here.

`BuildConfig.ENABLE_DEBUG_FEATURES` is overridden to `false` when env var `taking_screenshots=true` is set (used by `app/screenshots.sh`).

## Build-variant source sets (important)

`app/src/release/kotlin` and `app/src/debug/kotlin` are real, mutually-exclusive Kotlin source sets (ENH-A01, 2026-08-18) — only one is ever on the compile classpath per build type, both wired via `sourceSets { debug { java.srcDirs += "src/debug/kotlin" } ... }` in `app/build.gradle`. Each provides a parallel `BuildTypeBehavior` implementation and `debugCheck`/`debugRequire` under the shared legacy package `com.maltaisn.notes` (the project is a fork of maltaisn/notes — this package is the intentional debug/release DI seam, not a stray reference to fix):

- `app/src/release/kotlin/com/maltaisn/notes/`: `ReleaseBuildTypeBehavior.kt` (`doExtraAction()` no-ops), `DebugExtensions.kt` (`debugCheck`/`debugRequire` no-op), `di/BuildTypeModule.kt` (binds the release behavior; declares `package com.mckimquyen.notes.di`, not `com.maltaisn.notes.di` — matches the debug-side module below so `AppModule.kt` needs one unqualified import for both).
- `app/src/debug/kotlin/com/maltaisn/notes/`: `DebugBuildTypeBehavior.kt` (`doExtraAction()` inserts 3 placeholder notes, still gated by `BuildConfig.ENABLE_DEBUG_FEATURES` as defense-in-depth for `taking_screenshots=true`), `DebugExtensions.kt` (`debugCheck`/`debugRequire` really `check`/`require`), `DebugUtils.kt` (random note generator), `di/BuildTypeModule.kt` (binds the debug behavior).

`AppModule.kt` (`di/AppModule.kt`, in `src/main`, compiles for every variant) references `BuildTypeModule::class` by its shared FQN (`com.mckimquyen.notes.di.BuildTypeModule`) — it never imports either variant's concrete `*BuildTypeBehavior` class directly, which is what makes the split actually work. If you add a new variant-specific binding, give both sides the **same FQN** (mirroring this pattern) rather than inventing a new package per variant — that's what broke this the first time (see git history around commit `2867827` for the abandoned first attempt, which put the debug half in `src/main` under a divergent package and papered over it with a runtime `BuildConfig` guard instead).

`app/src/debug/` also holds resources (manifest overrides, debug strings, debug shortcut XML) alongside the Kotlin above.

## Architecture

The codebase is a fork/rebrand: runtime package is `com.mckimquyen.notes` but a few release-only files still live under `com.maltaisn.notes`. Don't "fix" that mismatch — the variant source sets depend on it.

### Dependency injection — Dagger 2 (kapt)

`RApp` constructs `DaggerAppComponent` via `appComponent.create(applicationContext)`. **Every injectable activity/fragment/dialog/receiver must be listed explicitly in `AppComponent`'s `inject(...)` overloads** (see `di/AppComponent.kt`). When you add a new `@Inject`-using class, add a matching `inject()` line there or it will fail at runtime, not compile time.

`kotlin-allopen` targets `androidx.annotation.OpenForTesting` (see `allOpen { annotation "..." }` in `app/build.gradle`) — classes like `PrefsManager`/`ReminderAlarmManager` marked `@OpenForTesting` get opened for mocking without needing `open` on every member. (A custom `com.mckimquyen.notes.OpenClass`/`OpenForTesting` pair used to exist per-variant for this but was dead — nothing referenced it — and was removed as part of ENH-A01.)

Modules:

- `AppModule` — `@Binds` repositories (`Default*Repository` → interface), `JsonManager`, `ReminderAlarmCallback`, plus `SharedPreferences` and configured kotlinx `Json` providers.
- `DbModule` — Room database singleton (`notes_db`) with migrations, exposes `notesDao()` / `labelsDao()`.
- `BuildTypeModule` — variant-specific bindings; only the release-variant version exists today.

### Data layer — Room v8 + repositories + Json

`NotesDb` (`model/NotesDb.kt`) is at version `8` with hand-written migrations 1→2 through 7→8 (`color` column at 4→5, `mood` at 5→6, `is_locked` at 6→7, new `note_history` table at 7→8). **When you change an entity you must:** bump `VERSION`, add a `Migration(N, N+1)` to `ALL_MIGRATIONS`, and let Room export the new schema to `app/schemas/` (configured via `RoomSchemaArgProvider` in `app/build.gradle`). Schemas are committed and used as `androidTest` assets for migration tests.

Entities: `Note`, `NoteFts` (FTS4 search index), `Label`, `LabelRef` (many-to-many), `NoteHistory` (via `NoteHistoryDao`, added migration 7→8), plus value enums `NoteStatus`, `NoteType`, `PinnedStatus`. Custom `TypeConverters` cover dates, status enums, JSON metadata, and `Recurrence` (from `com.maltaisn:recurpicker`). FTS means search queries go through `NotesDao` FTS-bound queries — don't bypass it with `LIKE` joins.

Repositories follow `Default<X>Repository` (impl) + `<X>Repository` (interface) pairing. `JsonManager` handles import/export of notes (used by Settings → password-protected import/export dialogs).

### Reminder/alarm system

`ReminderAlarmManager` schedules alarms; `AlarmReceiver` (registered in manifest with `BOOT_COMPLETED`/`QUICKBOOT_POWERON`/`TIMEZONE_CHANGED`/`TIME_SET` to reschedule after reboot or a clock/timezone change) wakes up and routes through `ReceiverAlarmCallback` → `ReminderAlarmCallback` (DI-bound). Receiver creates a coroutine scope **per broadcast** and cancels it in `finally` after `pendingResult.finish()` — preserve that pattern (see `doc/memory_leak.md` HIGH-1) when editing. `ReceiverAlarmCallback` deliberately uses `alarmManager.setAndAllowWhileIdle()`, not `setExactAndAllowWhileIdle()` — the exact-alarm variant needs `SCHEDULE_EXACT_ALARM`, a Play Console "sensitive permission" gated behind a core-functionality declaration (alarm clock/calendar) this notes app doesn't qualify for. Don't reintroduce it without discussing the Play Store risk first.

### UI — single-Activity + Navigation Component

- `SplashActivity` is the launcher (`MAIN`/`LAUNCHER`); after a short delay it routes to `MainAct`. Kept as `SplashActivity` — not shortened to `SplashAct` like the other `*Act` classes — because `AdManager`'s `ProcessLifecycle` matches on `simpleName == "SplashActivity"` to skip showing App Open Resume while the splash's own App Open flow is running.
- `MainAct` hosts `nav_graph_main.xml` via `NavHostFragment` and owns the drawer. It implements `NavController.OnDestinationChangedListener` to swap toolbars/FABs as fragments change.
- `NotificationAct` is a transparent activity hosting `nav_graph_notification.xml` for postpone/snooze flows from notifications.
- Fragments per feature: `home`, `search`, `edit`, `labels`, `reminder`, `sort`, `setting`, `noti`, `guide`, `vip` (Premium/VIP screen), plus `navigation/` for drawer destination model classes.
- ViewModels are obtained through helpers in `ui/ViewModels.kt`: `viewModel { factory.create(savedStateHandle) }` and `navGraphViewModel(R.id.nav_graph_main) { ... }`. Use `navGraphViewModel` to share state across fragments inside the same nav graph (e.g. `SharedViewModel`).
- `SharedViewModel` carries cross-fragment events (status changes, share data) via the `Event` wrapper — observe with `observeEvent(...)` to consume once.
- View binding is enabled (`buildFeatures.viewBinding = true`); generated classes follow `<LayoutName>Binding` (e.g. `f_edit.xml` → `FEditBinding`). Layout file prefixes: `a_` activity, `f_` fragment, `dlg_` dialog, `i_` item, `v_` view, `widget_` app widget.

### Widgets

Four `AppWidgetProvider` receivers in `widget/` (also registered in `AndroidManifest.xml`): `QuickNoteWidget`, `NoteCountWidget`, `QuickListWidget`, `RecentNotesWidget` (+ its `RecentNotesWidgetService` for `RemoteViewsFactory`). The widget service is `inject`ed via Dagger — see `AppComponent.inject(factory: RecentNotesRemoteViewsFactory)`.

### Ads — external SDK, not in this repo

Ad logic (`AdMobManager`, a 729-line singleton that used to live under `sdkadbmob/`) was fully removed and replaced by the closed-source library `com.roy.sdkadbmob` (`com.github.royt93:AdmobApplovinWrapper`, `AdManager`/`AdSdkConfig`), initialized from `RApp.setupAds()` (`RApp.kt`). **There is no ad-SDK source in this repo to audit or modify** — `RApp.setupAds()` just builds an `AdSdkConfig` from `BuildConfig` fields (`ADMOB_*`/`APPLOVIN_*` IDs, `IS_ENABLE_ADMOB` — currently `false`, AppLovin MAX is the active provider) and calls `AdManager.initialize()`. Any WeakReference/leak-safety pattern for ad listeners now lives inside that external library and can't be verified from here — see `doc/memory_leak.md` and `doc/AD.MD` for the historical migration record and what's still knowable. `RApp` itself has **no `appScope`/`CoroutineScope`** anymore — don't assume one exists when adding app-scoped coroutine work; name and scope it explicitly per the memory-leak conventions below. The VIP/Premium screen (`ui/vip/VipFrm.kt`) does have a real rewarded-ad touchpoint (`AdManager.loadRewarded`/`showRewarded`) — see `doc/AD.MD` §19.

## Memory-leak conventions (codified in this codebase)

`doc/memory_leak.md` is the historical record of fixes. The patterns established there are still live constraints:

- Activities/fragments must clear handler callbacks in `onStop`/`onDestroyView` (`exitHandler.removeCallbacksAndMessages(null)`, `binding.recyclerView.setOnTouchListener(null)`, etc.).
- Any component-scoped coroutine (Application, widget provider, etc.) uses a named `CoroutineScope(SupervisorJob() + Dispatchers.X)` — do **not** create anonymous `CoroutineScope(...)` at field-init time. (`RApp` itself no longer has one — ad-SDK init moved to the external `com.roy.sdkadbmob` library; see `widget/NoteCountWidget.kt` for a live example of this pattern via `goAsync()`.)
- `BroadcastReceiver`/`AppWidgetProvider` coroutine scopes must be cancelled in `finally` after `goAsync()`/`pendingResult.finish()`.
- Ad-SDK singleton listeners holding Activity references via `WeakReference`, and `SplashActivity`'s `EventBus.eventFlow.first {}` (not `collectLatest`) pattern to avoid leaking the activity, both now live inside the external `com.roy.sdkadbmob` library — unverifiable from this repo (see `doc/memory_leak.md`).
- Plain `AlertDialog`/`MaterialAlertDialogBuilder` (not a `DialogFragment`) shown from a Fragment/Activity must be tracked and `dismiss()`-ed in `onDestroyView()`/`onDestroy()`, or it leaks a window across configuration change (see `ui/vip/VipFrm.kt`'s `activeDialog` field for the established pattern).

## Translations

Strings live in `app/src/main/res/values-<locale>/strings.xml` — ~30 locales shipped (ar, bg, cs, da, de, el, es, fi, fr, hi, hr, hu, id, it, ja, ko, ms, nb, nl, pl, pt, ro, ru, sk, sv, th, tr, uk, vi, zh; see `doc/multi_language.md` for the full generated table). `TRANSLATING.md` is empty; treat `values/strings.xml` as the source of truth. The `aboutlibraries` plugin generates attribution at build time.

## Things that look broken but aren't

- Root `settings.gradle` includes only `:app` — `sharedTest/` is intentionally excluded (`//include "sharedTest"`).
- `play { serviceAccountCredentials = file("fake-key.json") }` is a placeholder; real credentials are loaded by an optional `app/publishing.gradle` (gitignored).
- `README.md`, `PRIVACY_POLICY.md`, `TRANSLATING.md` are empty placeholders. `CHANGELOG.md` is **not** — it's a real per-release changelog (Vietnamese), maintained starting 2026.08.18.
- The release source set lives under `com.maltaisn.notes` while runtime is `com.mckimquyen.notes`. This is the upstream-fork seam — leave it.

## Working language

`doc/*.md` and many code comments are in Vietnamese. Keep that voice when extending those docs; code identifiers stay in English.
