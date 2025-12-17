# AG-MAP Android Development Guide

## Overview
AG-MAP Android visualises live sensor measurements from the AirGradient network and layers in community content, bookmarks, widgets, and push notifications. The codebase follows a single-module Clean Architecture layout with Hilt dependency injection, Retrofit networking, and Jetpack Compose for UI.

This guide summarises the day-to-day developer workflow, highlights where major features live, and lists the key integration points so you can ship changes confidently.

## Environment Setup
- **Android Studio**: Ladybug or newer with the embedded JBR 21 runtime. Install Android SDK Platform 34 and the Google Play Services packages your device or emulator requires.
- **Command line builds**: Point `JAVA_HOME` at `.../Android Studio/jbr` (Windows `setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"`, macOS/Linux `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr"`).
- **Gradle / Kotlin versions**: Gradle wrapper 8.10.2, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01.
- **Optional services**: `google-services.json` is not required. The Gradle script only applies the Google Services plugin when a config file is present or `-PUSE_GOOGLE_SERVICES=true` is set.
- **OneSignal**: `app/AGMapApplication.kt` initialises the SDK with a demo app id. Replace this value before shipping to production and ensure devices are registered with OneSignal to exercise scheduled/threshold notifications.

Run `./gradlew --version` after cloning to confirm your environment and caches are ready.

## Build and Run
1. **Android Studio**
   - File > Open..., select the project root.
   - Wait for Gradle sync and Hilt code generation to finish.
   - Use the `app` run configuration. The default start destination is the Map screen (or My Locations when bookmarks exist).
2. **Command line**
   - `./gradlew assembleDebug` builds an APK.
   - `./gradlew installDebug` deploys to the connected device or emulator.
3. **Build variants**
   - `assembleDebug` enables verbose logging and skips minification.
   - `assembleRelease` builds the release APK (`minifyEnabled` is currently false).
4. **Google services override**
   - Supply `-PUSE_GOOGLE_SERVICES=true` if you need to force the plugin without committing service JSON files.

## Core Feature Walkthrough
### Map and Search
- `ui/map/ViewModels/MapViewModel.kt` manages camera events, viewport throttling, marker selection, search state, and interactions with location detail.
- `ui/map/Views/OSMMapScreen.kt` hosts OSMDroid inside Compose, renders overlays, legend, and bottom sheets, and requests permissions through `ActivityResultContracts`.
- `ui/map/Utils/MapViewportManager.kt` debounces map movement, rate-limits API requests (30/minute), and tracks current bounds so repeated pans do not spam the backend.
- `data/repositories/AirQualityRepositoryImpl.kt` wraps AirGradient cluster, location, history, and cigarette endpoints with a retry helper.
- `data/services/LocationSearchService.kt` uses the Nominatim REST API (`GeocodingService.kt`) and filters results to cities and countries. Recent searches persist in `data/local/PreferencesManager.kt`.

### Location Detail, Sharing, and Health Insights
- `ui/locationdetail/ViewModels/LocationDetailViewModel.kt` orchestrates use cases (`LoadLocationDetailUseCase`, `LoadLocationHistoricalDataUseCase`, `LoadWhoComplianceUseCase`, `LoadCigaretteEquivalenceUseCase`) and exposes a state-flow consumed by the bottom sheet.
- `ui/locationdetail/Views/LocationDetailBottomSheet.kt` renders measurement cards, charts, WHO compliance summaries, share actions, bookmarks, and notification management entry points.
- `ui/locationdetail/Utils/WHOComplianceChecker.kt` compares measurements against WHO guidelines.
- Share flows are emitted via `ui/shared/ShareEvent` and handled in the bottom sheet to start appropriate `Intent`s.

### Notifications Pipeline
- `app/AGMapApplication.kt` initialises OneSignal, keeps the player id current, and stores it with `data/local/PushNotificationMetadataStore.kt`.
- `ui/location/ViewModels/NotificationSettingsViewModel.kt` and `ui/location/Views/NotificationComponents.kt` manage scheduled and threshold alerts from the bottom sheet and Settings screens.
- `data/services/NotificationApiService.kt` and `data/repositories/NotificationsRepositoryImpl.kt` integrate with the AirGradient notification endpoints for CRUD operations on registrations.
- `domain/models/NotificationModels.kt` defines scheduling enums, threshold units, and DTO transforms.

### My Locations and Bookmarks
- `ui/mylocations/ViewModels/MyLocationsViewModel.kt` reads from `BookmarkRepository` and surfaces navigation events to detail and settings.
- `data/repositories/BookmarkRepositoryImpl.kt` persists bookmarks with `data/local/datastore/BookmarksDataStore.kt`, performs SharedPreferences migrations, and can fetch fresh measurements when needed.
- Bookmarks, recent searches, and widget settings all migrate through `data/local/migrations/SharedPreferencesToDataStore.kt` during app start.

### Settings and Widget
- `ui/settings/ViewModels/SettingsViewModel.kt` exposes display unit selection, widget location picker, notification summaries, and profile flags.
- `data/local/datastore/AppSettingsDataStore.kt` stores AQI units and widget configuration with DataStore.
- `widget/AirQualityWidgetProvider.kt` and `widget/AirQualityWidgetDataService.kt` manage the Material 3 widget update flow. Widget location defaults to the DataStore values set in Settings.

### Community and Content Hub
- `ui/community/ViewModels/CommunityViewModel.kt` fetches hero stats (static), featured projects, partner projects, blog posts, and article details.
- `data/repositories/CommunityProjectsRepositoryImpl.kt` parses XML feeds (`.../app-featured-communities/index.xml`) and applies locale fallbacks.
- `data/repositories/KnowledgeHubRepositoryImpl.kt` consumes the AirGradient blog RSS feed and article detail XML.
- `ui/community/Views/CommunityScreen.kt` renders carousels, partner lists, knowledge hub cards, and detail sheets.

### Navigation and Splash
- `navigation/Navigation.kt` defines the `NavHost`, bottom navigation, route arguments, and transitions between Map, My Locations, Community, Settings, and detail overlays.
- `ui/splash/SplashScreen.kt` provides the animated splash shown by `MainActivity` before `AGMapApp` is displayed.

## Data and Networking
- Retrofit instances are built in `data/network/NetworkModule.kt`: one for the AirGradient base URL (`https://map-data.airgradient.com/`), one for Nominatim. OkHttp logging is set to BODY for debug builds.
- API responses are wrapped in `data/network/ApiResult` to normalise success, error, and loading states.
- `AirQualityRepositoryImpl` contains a local `executeWithRetry` helper (three attempts with exponential delay) but no long-lived cache. Viewport throttling is the primary protection against rapid requests.
- Notification endpoints live under `map/api/v1/notifications/...` and require a valid OneSignal player id supplied via repository calls.
- Community and blog feeds are parsed with `XmlPullParser` and sanitized with `HtmlCompat` to produce Compose-friendly text.

## State and Persistence
- DataStore wrappers (`AppSettingsDataStore`, `BookmarksDataStore`) hold display units, widget configuration, and bookmark lists.
- SharedPreferences remain in use for lightweight map state (last camera position) and recent searches via `PreferencesManager`.
- Migrations from legacy SharedPreferences stores run automatically at repository init via `SharedPreferencesToDataStore`.
- Domain models (`domain/models`) keep presentation logic independent of API DTOs; converters live in `data/models` extensions.

## Development Workflow
- **Linting**: `./gradlew lint` covers Compose and XML. Fix warnings before merging feature branches.
- **Unit tests**: `./gradlew test` currently executes only scaffolding. Add deterministic tests for new use cases or repositories and keep them side-by-side with production packages.
- **Instrumentation tests**: `./gradlew connectedAndroidTest` launches placeholder suites. If you add Compose UI tests, ensure they gate important flows like bookmarking and notifications.
- **Compose previews**: `app/AGMapApp.kt` and individual screen composables expose previews for design iteration. Keep preview data providers in `ui/shared`.
- **Logging**: prefer `Log.d` with class-specific tags already present across ViewModels and repositories to avoid new log spam categories.

## Troubleshooting
- **Blank map tiles**: confirm network access, watch `adb logcat -s "OSMMapScreen" "MapViewportManager"`, and ensure the emulator has Google Play Services when requesting location.
- **Location permission loops**: `LocationService` surfaces `PermissionDenied` and `LocationDisabled` states. Verify the `ACCESS_FINE_LOCATION` grant and that device GPS is enabled.
- **Notification calls failing**: check for a stored OneSignal player id (`PushNotificationMetadataStore#playerId()`) and look for HTTP errors in `NotificationsRepo` logs. The backend requires matching player id and location id.
- **Widget stuck**: trigger `adb shell cmd appwidget update com.airgradient.android.app` and inspect `AirQualityWidgetProvider` logs. Ensure the widget location is set in Settings.
- **Gradle sync errors about missing KSP output**: rebuild the project. Hilt and KSP must run before importing classes from generated sources.

## Next Steps and Known Gaps
- Automated tests are effectively empty. Add coverage for `MapViewModel`, `LocationDetailViewModel`, and repository converters as new work lands.
- Packages inside `ui` still contain capitalised segments (`ViewModels`, `Views`). Keep them stable until a coordinated refactor renames packages and updates imports.
- Modularisation is planned (core/data, feature/map, etc.) but not yet started. Changes should continue to respect existing boundaries to ease future splits.
- Performance telemetry (Crashlytics, performance metrics) is not configured. Profile long-running map sessions manually before introducing new workloads.

Refer back to `README.md` for a project-level overview and `architecture.md` for deeper architectural rationale.
