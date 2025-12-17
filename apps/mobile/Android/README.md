# AG-MAP Android

AG-MAP Android is a Kotlin + Jetpack Compose client for the AirGradient sensor network. It renders live particulate, CO2, temperature, and humidity data on an interactive map, exposes location-level insights, and surfaces AirGradient community content inside a modern Android experience.

## Feature Highlights
- Live map built with OSMDroid and Compose interop, including viewport throttling, clustering, search with recent history, and "locate me" support
- Location detail bottom sheet with measurements, history charts, WHO guideline analysis, share flows, bookmarks, and push-notification entry points
- My Locations tab to review bookmarked sensors, jump into detail, and manage scheduled or threshold alerts
- Community hub powered by AirGradient RSS feeds with hero metrics, featured and partner projects, blog posts, and an in-app article reader
- Settings surface for AQI display units, widget location, notification summaries, profile experiments, plus a Material 3 home screen widget
- Push-notification support through OneSignal player registration and AirGradient notification APIs for both scheduled and threshold alerts

## Architecture Snapshot
- Single `app` module organised by layer (`app`, `data`, `domain`, `navigation`, `ui`, `widget`)
- Clean-architecture-inspired separation: repositories and services in `data`, use cases and models in `domain`, Compose screens in `ui`
- Hilt DI modules (`data/di`, `data/network`) wire Retrofit, DataStore, repositories, and widget entry points
- Compose navigation host at `navigation/Navigation.kt` drives bottom navigation between Map, My Locations, and Community screens
- OSMDroid map wrapped inside `ui/map/Views/OSMMapScreen.kt` with helper utilities (`MapViewportManager`, marker renderer) for rate limiting and animations
- Data persistence split between SharedPreferences (`PreferencesManager`) for map state and Jetpack DataStore (`AppSettingsDataStore`) for user settings
- Push notifications initialised in `app/AGMapApplication.kt` via OneSignal and persisted by `data/local/PushNotificationMetadataStore.kt`

## Tech Stack
- Kotlin 2.0.21 with Jetpack Compose and Material 3
- Android Gradle Plugin 8.7.3 / Gradle 8.10.2
- Hilt + KSP, Retrofit + OkHttp, Gson, Kotlin Serialization
- OSMDroid map tiles and OpenStreetMap geocoding (Nominatim)
- Coil for images, Vico charts for historical visualisation
- Jetpack Navigation Compose, DataStore, Coroutines + StateFlow
- OneSignal SDK and AirGradient notification REST endpoints

## Getting Started
1. Install Android Studio Ladybug or newer (with embedded JBR 21) and the Android 14 (API 34) SDK platforms. No separate JDK install is required for Studio; CLI builds can point `JAVA_HOME` at the same `jbr` folder.
2. Clone the repository:
   `git clone https://github.com/yourusername/airgradient-kotlin.git && cd airgradient-kotlin`
3. Open the project in Android Studio via *File > Open...*. Allow Gradle sync to finish.
4. Select the `app` configuration and press *Run*. For CLI builds use `./gradlew assembleDebug` (macOS/Linux) or `gradlew.bat assembleDebug` (Windows).

Copy `app/google-services.example.json` to `app/google-services.json` and fill in your Firebase project details if you want push notifications during development. Provide your OneSignal app identifier via `ONESIGNAL_APP_ID=your-app-id` in `local.properties` (or `~/.gradle/gradle.properties`). The build script only applies the Google Services plugin when the file exists or when `-PUSE_GOOGLE_SERVICES=true` is supplied. OneSignal uses the build-time app id; supply a production value for release builds.

## Common Gradle Commands
- `./gradlew assembleDebug` - build and package the debug APK
- `./gradlew installDebug` - deploy the debug build to a connected device
- `./gradlew lint` - run Android Lint (Compose / XML)
- `./gradlew test` - execute JVM unit tests (currently minimal scaffolding)
- `./gradlew connectedAndroidTest` - launch instrumentation tests (suites are placeholders)
- `./gradlew clean` - clear build outputs

## Project Layout (truncated)
```
app/src/main/java/com/agmap/android/
|-- app/
|   |-- AGMapApp.kt
|   |-- AGMapApplication.kt
|   \-- MainActivity.kt
|-- data/
|   |-- di/RepositoryModule.kt
|   |-- local/
|   |   |-- PreferencesManager.kt
|   |   |-- PushNotificationMetadataStore.kt
|   |   \-- datastore/AppSettingsDataStore.kt
|   |-- network/NetworkModule.kt
|   |-- repositories/... (AirQualityRepositoryImpl, NotificationsRepositoryImpl, etc.)
|   \-- services/... (AirQualityApiService, LocationSearchService, NotificationApiService, GeocodingService)
|-- domain/
|   |-- models/...
|   |-- repositories/...
|   \-- usecases/...
|-- navigation/Navigation.kt
|-- ui/
|   |-- community/{ViewModels,Views}
|   |-- location/{ViewModels,Views}
|   |-- locationdetail/{DI,Utils,ViewModels,Views}
|   |-- map/{Utils,ViewModels,Views,Views/marker}
|   |-- mylocations/{ViewModels,Views}
|   |-- search/Views
|   |-- settings/{ViewModels,Views}
|   |-- shared/{Utils,Views}
|   \-- splash/SplashScreen.kt
\-- widget/
    |-- AirQualityWidgetDataService.kt
    \-- AirQualityWidgetProvider.kt
```
Folder names inside `ui` keep capitalised `ViewModels` and `Views` segments to match existing package declarations.

## Backend Integrations
- **AirGradient Map API** (`https://map-data-int.airgradient.com/`): clustered measurements, location details, historical series, cigarette equivalents, and notification management (`map/api/v1/...`).
- **AirGradient Notification API**: create, update, and delete scheduled or threshold alerts for an OneSignal player id.
- **OpenStreetMap Nominatim** (`https://nominatim.openstreetmap.org/`): forward geocoding for search suggestions via `LocationSearchService`.
- **AirGradient Community & Blog feeds** (`https://www.airgradient.com/.../index.xml`): featured projects, partner projects, and blog posts parsed with XML pull parsers.

All remote access runs through Retrofit. Rate limiting and viewport throttling happen client-side in `MapViewportManager`.

## Logging and Debugging
- Enable verbose map logs: `adb logcat -s "MapViewModel:*" "AirQualityRepo:*" "MapViewportManager:*"`
- Track notifications: `adb logcat -s "NotificationsRepo:*" "NotificationSettingsVM:*"`
- Inspect network calls with OkHttp logging (BODY level is enabled in debug builds)
- Widget diagnostics: `adb logcat -s "AirQualityWidget:*"` and `adb shell dumpsys appwidget`

## Testing Status
Automated tests are currently placeholders (`app/src/test` and `app/src/androidTest` contain empty scaffolding). Add targeted unit tests for use cases and repositories, plus Compose UI tests for critical flows before relying on continuous integration.

## Additional Documentation
- `ANDROID_DEVELOPMENT_GUIDE.md` - hands-on workflows for contributors
- `architecture.md` - deep-dive into layers, data flow, and future improvements
- `Documentation/code-review-report.md` - historical structure review

## Contributing
1. Create a feature branch from `main`.
2. Keep modules aligned with the documented architecture and update relevant docs when you add capabilities.
3. Run `./gradlew lint` and any tests you add before opening a pull request.
4. Attach screenshots or screen recordings for UI-facing changes and describe notification edge cases if touched.

## License
- Licensed under **AGPL-3.0**. See `LICENSE.md`.
- **Additional attribution requirement:** the app UI must display the attribution text described at the top of `LICENSE.md`.
