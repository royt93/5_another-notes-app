# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

The project is a single-module Android app (`:app`) using the Gradle wrapper. The Gradle daemon expects Java 17 (`toolchain.languageVersion = 17`); `gradle.properties` pins `org.gradle.java.home` to a local JDK 20 install — adjust that line if your machine has a different JDK path.

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

There is a separate `src/release/kotlin` source root that **only** the release variant compiles. It contains a parallel `BuildTypeBehavior` implementation under the legacy package `com.maltaisn.notes` (the project is a fork of maltaisn/notes). Anything in `app/src/release/kotlin/com/maltaisn/notes/` (`OpenForTesting.kt`, `ReleaseBuildTypeBehavior.kt`, `BuildTypeModule.kt`, `DebugExtensions.kt`) ships only in release builds. There is **no equivalent debug Kotlin source set** today — if you add a `debug` variant of `BuildTypeBehavior` you must mirror the file under `app/src/debug/kotlin/...` or the debug build will fail to find the Dagger binding.

`app/src/debug/` currently holds resources only (manifest overrides, debug strings, debug shortcut XML).

## Architecture

The codebase is a fork/rebrand: runtime package is `com.mckimquyen.notes` but a few release-only files still live under `com.maltaisn.notes`. Don't "fix" that mismatch — the variant source sets depend on it.

### Dependency injection — Dagger 2 (kapt)

`RApp` constructs `DaggerAppComponent` via `appComponent.create(applicationContext)`. **Every injectable activity/fragment/dialog/receiver must be listed explicitly in `AppComponent`'s `inject(...)` overloads** (see `di/AppComponent.kt`). When you add a new `@Inject`-using class, add a matching `inject()` line there or it will fail at runtime, not compile time.

`@OpenClass` (custom annotation) drives the `kotlin-allopen` plugin so DI/test classes can be opened without `open` keyword on every class. The annotation lives in `com.mckimquyen.debug.notes.OpenForTesting` (debug variant) and `com.maltaisn.notes.OpenForTesting` (release variant) — note the dual-package shape.

Modules:

- `AppModule` — `@Binds` repositories (`Default*Repository` → interface), `JsonManager`, `ReminderAlarmCallback`, plus `SharedPreferences` and configured kotlinx `Json` providers.
- `DbModule` — Room database singleton (`notes_db`) with migrations, exposes `notesDao()` / `labelsDao()`.
- `BuildTypeModule` — variant-specific bindings; only the release-variant version exists today.

### Data layer — Room v5 + repositories + Json

`NotesDb` (`model/NotesDb.kt`) is at version `5` with hand-written migrations 1→2, 2→3, 3→4, 4→5. **When you change an entity you must:** bump `VERSION`, add a `Migration(N, N+1)` to `ALL_MIGRATIONS`, and let Room export the new schema to `app/schemas/` (configured via `RoomSchemaArgProvider` in `app/build.gradle`). Schemas are committed and used as `androidTest` assets for migration tests.

Entities: `Note`, `NoteFts` (FTS4 search index), `Label`, `LabelRef` (many-to-many), plus value enums `NoteStatus`, `NoteType`, `PinnedStatus`. Custom `TypeConverters` cover dates, status enums, JSON metadata, and `Recurrence` (from `com.maltaisn:recurpicker`). FTS means search queries go through `NotesDao` FTS-bound queries — don't bypass it with `LIKE` joins.

Repositories follow `Default<X>Repository` (impl) + `<X>Repository` (interface) pairing. `JsonManager` handles import/export of notes (used by Settings → password-protected import/export dialogs).

### Reminder/alarm system

`ReminderAlarmManager` schedules alarms; `AlarmReceiver` (registered in manifest with `BOOT_COMPLETED` to reschedule after reboot) wakes up and routes through `ReceiverAlarmCallback` → `ReminderAlarmCallback` (DI-bound). Receiver creates a coroutine scope **per broadcast** and cancels it in `finally` after `pendingResult.finish()` — preserve that pattern (see `doc/memory_leak.md` HIGH-1) when editing.

### UI — single-Activity + Navigation Component

- `SplashAct` is the launcher (`MAIN`/`LAUNCHER`); after a short delay it routes to `MainAct`.
- `MainAct` hosts `nav_graph_main.xml` via `NavHostFragment` and owns the drawer. It implements `NavController.OnDestinationChangedListener` to swap toolbars/FABs as fragments change.
- `NotificationAct` is a transparent activity hosting `nav_graph_notification.xml` for postpone/snooze flows from notifications.
- Fragments per feature: `home`, `search`, `edit`, `labels`, `reminder`, `sort`, `setting`, `noti`, `guide`, plus `navigation/` for drawer destination model classes.
- ViewModels are obtained through helpers in `ui/ViewModels.kt`: `viewModel { factory.create(savedStateHandle) }` and `navGraphViewModel(R.id.nav_graph_main) { ... }`. Use `navGraphViewModel` to share state across fragments inside the same nav graph (e.g. `SharedViewModel`).
- `SharedViewModel` carries cross-fragment events (status changes, share data) via the `Event` wrapper — observe with `observeEvent(...)` to consume once.
- View binding is enabled (`buildFeatures.viewBinding = true`); generated classes follow `<LayoutName>Binding` (e.g. `f_edit.xml` → `FEditBinding`). Layout file prefixes: `a_` activity, `f_` fragment, `dlg_` dialog, `i_` item, `v_` view, `widget_` app widget.

### Widgets

Four `AppWidgetProvider` receivers in `widget/` (also registered in `AndroidManifest.xml`): `QuickNoteWidget`, `NoteCountWidget`, `QuickListWidget`, `RecentNotesWidget` (+ its `RecentNotesWidgetService` for `RemoteViewsFactory`). The widget service is `inject`ed via Dagger — see `AppComponent.inject(factory: RecentNotesRemoteViewsFactory)`.

### Ads — AdMob (+ AppLovin mediation)

`sdkadbmob/AdMobManager` is a `object` singleton initialized from `RApp.setupAdmob()`. AdMob unit IDs come from `BuildConfig` fields injected per build type (test IDs in debug, real IDs in release). The manager has been hardened against the leaks documented in `doc/memory_leak.md` — keep `WeakReference` usage on listeners/activities and the `appScope` pattern in `RApp` when modifying.

## Memory-leak conventions (codified in this codebase)

`doc/memory_leak.md` is the historical record of fixes. The patterns established there are still live constraints:

- Activities/fragments must clear handler callbacks in `onStop`/`onDestroyView` (`exitHandler.removeCallbacksAndMessages(null)`, `binding.recyclerView.setOnTouchListener(null)`, etc.).
- Application-scoped coroutines use a named `appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` — do **not** create anonymous `CoroutineScope(...)` at field-init time.
- `BroadcastReceiver` coroutine scopes must be cancelled in `finally` after `goAsync()`/`pendingResult.finish()`.
- AdMob singleton listeners hold Activity references via `WeakReference`; `SplashAct` ad-loading uses `EventBus.eventFlow.first {}` (not `collectLatest`) to avoid leaking the activity.

## Translations

Strings live in `app/src/main/res/values-<locale>/strings.xml` (ar, de, es, fr, it, nb, …). `TRANSLATING.md` is empty; treat `values/strings.xml` as the source of truth. The `aboutlibraries` plugin generates attribution at build time.

## Things that look broken but aren't

- Root `settings.gradle` includes only `:app` — `sharedTest/` is intentionally excluded (`//include "sharedTest"`).
- `play { serviceAccountCredentials = file("fake-key.json") }` is a placeholder; real credentials are loaded by an optional `app/publishing.gradle` (gitignored).
- `README.md`, `CHANGELOG.md`, `PRIVACY_POLICY.md`, `TRANSLATING.md` are all empty placeholders.
- The release source set lives under `com.maltaisn.notes` while runtime is `com.mckimquyen.notes`. This is the upstream-fork seam — leave it.

## Working language

`doc/*.md` and many code comments are in Vietnamese. Keep that voice when extending those docs; code identifiers stay in English.
