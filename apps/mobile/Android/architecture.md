# AG-MAP Android Architecture

## Overview
AG-MAP Android is organised as a single Gradle module that follows Clean Architecture principles and the MVVM pattern. Data acquisition, domain logic, and Compose UI live in distinct packages under `app/src/main/java/com/agmap/android`. Hilt wires dependencies together and Retrofit handles all network access.

```
app/src/main/java/com/agmap/android/
|-- app/                 # Application entry (AGMapApplication, AGMapApp, MainActivity)
|-- data/                # DTOs, Retrofit services, repositories, DI modules
|-- domain/              # Use cases, domain models, repository interfaces
|-- navigation/          # Compose NavHost and routes
|-- ui/                  # Feature UIs grouped by functional area
|-- utils/               # Cross-cutting helpers (AQI utilities, formatting)
\-- widget/             # Home-screen widget entry points
```

## Layer Responsibilities
### Data Layer (`data`)
- Retrofit services (`data/services`) encapsulate HTTP endpoints for AirGradient map data, notifications, and Nominatim search.
- Repositories (`data/repositories`) translate DTOs to domain models, merge multiple endpoints, and handle simple retry logic (for example, `AirQualityRepositoryImpl.executeWithRetry`).
- Dependency injection modules live in `data/di` and `data/network`, providing Retrofit instances, OkHttp clients, repositories, and utility stores.
- Local persistence includes SharedPreferences (`PreferencesManager`) and multiple DataStore wrappers (`AppSettingsDataStore`, `BookmarksDataStore`).

### Domain Layer (`domain`)
- Repository interfaces define what data the UI expects without exposing Retrofit or persistence details.
- Domain models normalise sensor readings, health insights, bookmarks, community posts, and notification settings.
- Use cases orchestrate repository calls and encapsulate business rules. Examples include `GetMapMarkersUseCase`, `ToggleBookmarkUseCase`, and `GetFeaturedCommunityProjectsUseCase`.

### Presentation Layer (`ui` + `navigation`)
- Each feature keeps separate `ViewModels` and `Views` subpackages (capitalised to match existing package declarations).
- ViewModels expose immutable `StateFlow` state and delegate to domain use cases or repositories.
- Compose screens render state, call back into ViewModels for events, and coordinate child components such as bottom sheets or markers.
- `navigation/Navigation.kt` hosts a `NavHost` with bottom navigation destinations (Map, My Locations, Community) and modal routes (Settings, Location Detail).

## Major Data Flows
### Map -> Location Detail
1. `OSMMapScreen` forwards map gestures to `MapViewportManager`.
2. `MapViewportManager` debounces and calls back into `MapViewModel.fetchDataForViewport`.
3. `MapViewModel` invokes `GetMapMarkersUseCase`, which consults `AirQualityRepositoryImpl` for clustered measurements.
4. Selecting a marker triggers `LocationDetailViewModel.showLocationDetail`, which chains domain use cases for location info, historical data, WHO compliance, bookmarks, and notifications.
5. `LocationDetailBottomSheet` renders the combined state and exposes share/bookmark/notification actions.

### Bookmarks and Widget
1. `BookmarkRepositoryImpl` uses `BookmarksDataStore` to persist saved locations and migrates legacy SharedPreferences on startup.
2. `MyLocationsViewModel` streams bookmarks, resolves display names, and provides navigation events.
3. `AirQualityWidgetDataService` pulls the selected widget location (`AppSettingsDataStore`) and fetches fresh measurements through `AirQualityRepositoryImpl` before updating `RemoteViews`.

### Notifications
1. `AGMapApplication` initialises OneSignal and writes the player id to `PushNotificationMetadataStore`.
2. `NotificationSettingsViewModel` reads existing registrations by calling `NotificationsRepository` with that player id.
3. CRUD operations hit the AirGradient notification API via `NotificationApiService` and update the UI state immediately on success.
4. `LocationDetailViewModel` keeps a boolean flag so the Location Detail header can display whether notifications are active for the sensor.

### Community Content
1. `CommunityViewModel` calls `CommunityProjectsRepository` and `KnowledgeHubRepository` to download XML feeds.
2. Repositories use `XmlPullParser`, sanitise HTML with `HtmlCompat`, and apply locale fallbacks.
3. Compose screens render carousels, hero stats, partner lists, blog cards, and inline article sheets.

## Dependency Injection
- Hilt entry point at `AGMapApplication`.
- Modules: `data/network/NetworkModule.kt` (Retrofit, OkHttp, Gson), `data/di/RepositoryModule.kt` (binds repositories), and various `@Singleton` stores.
- Compose ViewModels use `hiltViewModel()` or injected constructors to keep dependencies constructor-injected.
- The widget relies on an `EntryPoint` (`PushNotificationMetadataStoreEntryPoint`) to reach Hilt-managed stores from broadcast receivers.

## Persistence Strategy
- **DataStore**: `AppSettingsDataStore` for display units and widget selection, `BookmarksDataStore` for saved locations, plus migration helpers (`SharedPreferencesToDataStore`).
- **SharedPreferences**: `PreferencesManager` stores map camera state and recent searches where quick synchronous access is needed.
- **In-memory**: ViewModels track transient UI state (viewport throttle info, sheet visibility, share events) without additional caching layers.

## External Services
- AirGradient Map API (`https://map-data-int.airgradient.com/`) for clustered measurements, location info, history, cigarette equivalents, and notification registration endpoints.
- Nominatim (`https://nominatim.openstreetmap.org/`) for forward geocoding; requests attach a custom User-Agent per API requirements.
- AirGradient XML feeds (`https://www.airgradient.com/app-featured-communities/index.xml`, `https://www.airgradient.com/blog/index.xml`) for community and knowledge hub content.
- OneSignal SDK for push notification player ids; server-side registrations are stored through AirGradient endpoints.

## Error Handling and Resilience
- `ApiResult` wraps network calls so ViewModels can surface loading and error states consistently.
- `MapViewportManager` enforces a 30 requests/minute budget and reuses previous viewport bounds when movements are minor.
- Notification repository inspects HTTP status codes and retries updates when the server reports conflicts.
- XML parsing uses explicit exceptions with logging so malformed feeds do not crash the app; fallbacks default to English feeds.

## Testing Status
- Unit and instrumentation directories contain only scaffolding placeholders. No automated coverage currently protects use cases, repositories, or Compose screens.
- When adding features, colocate tests with the relevant package (for example, `app/src/test/java/com/agmap/android/ui/map`).

## Known Improvement Areas
- Package names inside `ui` still include capitalised segments (`ViewModels`, `Views`) due to legacy structure; refactors should rename them carefully across imports.
- The app remains a single module. Planned modularisation (`:core:data`, `:feature:map`, etc.) will reduce build times and enforce stricter boundaries.
- Persistent caching is minimal. The current implementation relies on viewport throttling rather than storing responses; consider introducing Room or in-memory caches if API limits tighten.
- Automated tests and screenshot coverage need to be built out before enabling CI gates.

Refer to `README.md` for contributor onboarding and `ANDROID_DEVELOPMENT_GUIDE.md` for workflow details.
