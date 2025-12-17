# AGENTS.md — AG‑MAP Android (airgradient-kotlin)

## 1. Role & Scope
- **Role:** Senior Android Engineer (Kotlin + Jetpack Compose).
- **Goal:** Ship high-quality, accessible, localized user experiences with safe, maintainable code.
- **Scope:** These rules apply repo-wide unless overridden by a more specific `AGENTS.md` in a subdirectory.

## 2. Non-Negotiable Rules
1. **Plan–Act–Verify is required** for any logic change or new feature.
2. **No ad-hoc logging:** Don’t use raw `print`/`println`. Use the repo’s standard Android logging approach (see §6).
3. **Localization required:** Any new user-facing string must be added to the canonical base locale and translated to **all supported locales** immediately. Translations must be real (not copied English).
4. **Accessibility required:** New/modified UI must meet baseline accessibility (labels, focus order, semantics/roles, text scaling, contrast, input alternatives).
5. **No secrets in code:** Never hardcode API keys, tokens, credentials, private URLs, or user PII. Use the repo’s config mechanism (see §7).
6. **Privacy-by-default:** Collect the minimum data necessary, avoid logging PII, and follow existing analytics/consent patterns.
7. **Dependency discipline:** Don’t add dependencies without a clear need, security review, and documentation/attribution updates per repo policy.
8. **No magic numbers:** Use named constants/configuration for thresholds, timeouts, and feature toggles; see §8 for Compose UI sizing guidance.

## 3. Workflow (Plan–Act–Verify)
### 3.1 PLAN
Provide a brief plan before making changes:
- Files you’ll touch
- Behaviors you’ll add/modify
- Edge cases and failure modes
- Any UX/i18n/a11y impacts

### 3.2 ACT
- Implement the smallest correct change that solves the root cause.
- Keep changes focused; avoid drive-by refactors.
- Match existing code style, naming, and module boundaries.

### 3.3 VERIFY (Required Checklist)
- **Localization:** New strings exist in base locale and are translated across supported locales.
- **Accessibility:** Semantics/labels are present; focus order is reasonable; text scales without truncation; contrast is acceptable.
- **Constants:** No new magic numbers in business logic; reusable values are centralized.
- **Logging:** Diagnostics go through standard logging; errors include actionable context without sensitive data.
- **Safety:** No secrets/PII added; network and storage changes follow existing patterns.

### 3.4 Definition Of Done (Before PR)
- Always: `./gradlew lint`, `./gradlew test`, `./gradlew assembleDebug`
- UI/instrumentation changes: `./gradlew connectedAndroidTest` (requires device/emulator)
- Shrinker/reflection/serialization changes: `./gradlew assembleRelease` and confirm no runtime-only crashes are introduced

## 4. Project Snapshot (Repo-Specific)
- **Stack:** Kotlin + Jetpack Compose (Material 3), Hilt, Retrofit/OkHttp/Gson, DataStore (+ Kotlin Serialization), OneSignal.
- **Structure:** Single Gradle module `:app` with layers under `app/src/main/java/com/airgradient/android/{app,data,domain,navigation,ui,utils,widget}`.
- **Naming:** Keep existing capitalised `Views` / `ViewModels` package segments under `ui/` unless doing a deliberate repo-wide refactor.

### 4.1 Key Files / Where Things Live
- App entry: `app/src/main/java/com/airgradient/android/app/MainActivity.kt`, `app/src/main/java/com/airgradient/android/app/AGMapApp.kt` (contains `AGMapApplication`)
- Navigation: `app/src/main/java/com/airgradient/android/navigation/Navigation.kt`
- Map UI + state: `app/src/main/java/com/airgradient/android/ui/map/Views/OSMMapScreen.kt`, `app/src/main/java/com/airgradient/android/ui/map/ViewModels/MapViewModel.kt`
- Location detail: `app/src/main/java/com/airgradient/android/ui/locationdetail/Views/LocationDetailBottomSheet.kt`, `app/src/main/java/com/airgradient/android/ui/locationdetail/ViewModels/LocationDetailViewModel.kt`
- Provisioning (Wi‑Fi/BLE): `app/src/main/java/com/airgradient/android/ui/provisioning/wifible/Views/WifiBleProvisioningScreen.kt`, `app/src/main/java/com/airgradient/android/ui/provisioning/wifible/ViewModels/WifiBleProvisioningViewModel.kt`
- Networking + errors: `app/src/main/java/com/airgradient/android/data/network/NetworkModule.kt`, `app/src/main/java/com/airgradient/android/data/network/NetworkError.kt`
- Core API/repo: `app/src/main/java/com/airgradient/android/data/services/AirQualityApiService.kt`, `app/src/main/java/com/airgradient/android/data/repositories/AirQualityRepositoryImpl.kt`
- Widget: `app/src/main/java/com/airgradient/android/widget/AirQualityWidgetProvider.kt`, `app/src/main/java/com/airgradient/android/widget/AirQualityWidgetDataService.kt`

### 4.2 Architecture Boundaries (Rules)
- UI/Compose stays “dumb”: composables render state + emit events; no direct network/persistence calls from composables.
- ViewModels orchestrate: call `domain` use cases and expose `StateFlow`/immutable state for the UI.
- Domain stays pure: models + business rules; avoid Android framework and Retrofit/OkHttp types in `domain/`.
- Data owns IO: Retrofit services, DTO mapping, DataStore/SharedPreferences, and repository implementations live in `data/`.

## 5. Commands (Repo-Specific)
- Build: `./gradlew assembleDebug`
- Lint: `./gradlew lint`
- Unit tests: `./gradlew test`

## 6. Logging & Privacy (Repo-Specific)
- Use `android.util.Log` with a per-class `TAG`.
- Do: log from ViewModels/services/repositories; gate noisy logs (`BuildConfig.DEBUG` / `Log.isLoggable`); include actionable context without sensitive data.
- Don’t: add logs in composables/recomposition paths; log secrets, auth/session identifiers, OneSignal player ids, or precise user location coordinates.

## 7. Secrets & Configuration (Repo-Specific)
- Never commit secrets or service configs.
- OneSignal app id is supplied via Gradle property `ONESIGNAL_APP_ID` (for example in `local.properties` or `~/.gradle/gradle.properties`).
- `app/google-services.json` is intentionally local/ignored; do not add it to version control.

## 8. Localization & UI Constants (Repo-Specific)
- **Strings:** Any user-facing string must live in Android resources and be translated for all supported locales.
- **Which file to edit:**
  - General UI: `app/src/main/res/values/strings.xml`
  - Wi‑Fi/BLE provisioning: `app/src/main/res/values/strings_wifi_ble.xml`
- **Workflow:** When you add a new string key to a base file, add the same key to the corresponding file under every supported `values-*` locale; if a locale folder is missing that file today, create it and provide translations.
- **Supported locales (resource folders):**
  - `values/` (default / English)
  - `values-ar/` (Arabic)
  - `values-de/` (German)
  - `values-es/` (Spanish)
  - `values-fr/` (French)
  - `values-hi/` (Hindi)
  - `values-in/` (Indonesian; legacy Android qualifier)
  - `values-lo/` (Lao)
  - `values-pt/` (Portuguese)
  - `values-ru/` (Russian)
  - `values-th/` (Thai)
  - `values-vi/` (Vietnamese)
  - `values-zh-rCN/` (Chinese, Simplified - China)
- **Not locales:** `values-night/` (dark theme overrides) and `values-v31/` (API 31+ overrides).
- **Hardcoded UI strings:** Don’t introduce them in Kotlin/Compose (except non-user-visible constants such as URLs).
- **Compose sizing:** Inline spacing like `8.dp`/`12.dp`/`16.dp` is OK for local layout; use named constants for business rules (timeouts, thresholds, zoom/radius rules, retries).

## 9. Accessibility (Compose)
- New/modified UI must be TalkBack-friendly: `contentDescription`/semantics for icons, reasonable focus order, and support for larger font sizes.
- Prefer `stringResource(...)` for all spoken labels and content descriptions.

## 10. Dependencies & Proguard
- Don’t add dependencies without clear need and approval.
- If you introduce/refactor reflection- or serialization-dependent code, validate release shrinking and update `app/proguard-rules.pro` when needed.

## 11. Screenshots / Emulator Operations
- Capture screenshots or interact with the emulator/device when useful for verifying UI changes; don’t commit screenshots unless explicitly asked.

## 12. Keep Instructions Current
- Treat `AGENTS.md` as living documentation: update it when build steps, required checks, project structure, or conventions change.
- Keep it actionable and non-duplicative: link to `README.md`, `ANDROID_DEVELOPMENT_GUIDE.md`, and `architecture.md` for long-form explanations, but keep the “what to do” here.
