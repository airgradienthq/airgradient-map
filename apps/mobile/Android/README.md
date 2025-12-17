# AG-MAP Android

AG-MAP Android is a Kotlin + Jetpack Compose client for the AirGradient sensor network. It renders live particulate, CO2, temperature, and humidity data on an interactive map, exposes location-level insights, and surfaces AirGradient community content inside a modern Android experience.

## Feature Highlights
- Live map built with OSMDroid and Compose interop, including viewport throttling, clustering, search with recent history, and "locate me" support
- Location detail bottom sheet with measurements, history charts, WHO guideline analysis, share flows, bookmarks, and push-notification entry points
- My Locations tab to review bookmarked sensors, jump into detail, and manage scheduled or threshold alerts
- Community hub powered by AirGradient RSS feeds with hero metrics, featured and partner projects, blog posts, and an in-app article reader
- Settings surface for AQI display units, widget location, notification summaries, profile experiments, plus a Material 3 home screen widget
- Push-notification support through OneSignal player registration and AirGradient notification APIs for both scheduled and threshold alerts

## Tech Stack
- Kotlin 2.0.21 with Jetpack Compose and Material 3
- Android Gradle Plugin 8.7.3 / Gradle 8.10.2
- Hilt + KSP, Retrofit + OkHttp, Gson, Kotlin Serialization
- OSMDroid map tiles and OpenStreetMap geocoding (Nominatim)
- Coil for images, Vico charts for historical visualisation
- Jetpack Navigation Compose, DataStore, Coroutines + StateFlow
- OneSignal SDK and AirGradient notification REST endpoints

## Common Gradle Commands
- `./gradlew assembleDebug` - build and package the debug APK
- `./gradlew installDebug` - deploy the debug build to a connected device
- `./gradlew lint` - run Android Lint (Compose / XML)
- `./gradlew test` - execute JVM unit tests (currently minimal scaffolding)
- `./gradlew connectedAndroidTest` - launch instrumentation tests (suites are placeholders)
- `./gradlew clean` - clear build outputs

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
