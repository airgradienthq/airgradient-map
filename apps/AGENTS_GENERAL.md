# AGENTS_GENERAL.md — Monorepo Agent Guidelines

These instructions apply to *any* code in this monorepo—frontend, backend, cron jobs, and supporting services. App-specific details (frameworks, build/run commands, deployment nuances) belong in the corresponding `apps/<name>/AGENTS.md` files; once those exist, this document will link to each of them.

## 1. Monorepo Architecture Overview
- **Web & mobile UI:** `apps/website` (Nuxt 3) serves the web app; native iOS and Android apps live under `apps/mobile`.
- **Core API & cron:** `apps/api` is a NestJS service exposing the HTTP API and a `cron-main` entrypoint for scheduled ingestion/backfills against PostgreSQL/PostGIS.
- **Wind data pipeline:** `apps/wind-service` is an isolated TypeScript/cron worker that writes wind data into the shared database.
- **Shared assets:** `apps/shared` holds canonical constants, AQI band definitions, localization files, and reusable assets consumed by multiple apps. When agents need self-contained inputs (e.g., mobile builds, API packaging, or automation sandboxes), copy the authoritative file from `apps/shared` into the target app folder (e.g., `apps/api/src/aqi-standards/aqi_standards.json`). Always treat the `apps/shared` copy as the source of truth and keep duplicates in sync as part of your change—add a note to your PR or run any sync script provided by the app.
- **Runtime composition:** `docker-compose-*.yml` orchestrates `postgrex-mono` (database), `db-migrate-mono` (Knex migrations), `mapapi-mono` (API), `cron-mono` (scheduled jobs), and `website-mono` (frontend). The frontend talks to the API; the API and cron jobs read/write the same database.
- **App-specific guidance:** Each app should maintain its own `AGENTS.md` with deeper instructions; consult those when present for overrides.

## 2. Role & Scope
- **Role:** Senior Engineer (full-stack).
- **Goal:** Ship high-quality, accessible user experiences and reliable services with safe, maintainable code.
- **Scope:** Follow these rules for any files under this repo unless a more specific `AGENTS.md` overrides them.

## 3. Non-Negotiable Rules
1. **Plan–Act–Verify is required** for any logic change or new feature.
2. **No ad-hoc logging:** Don’t use raw `print`, `console.log`, or similar. Use the project’s standard logging/telemetry approach.
3. **Localization required:** Any new user-facing string must be added to the canonical base locale and translated to **all supported locales** immediately (applies to UI text and API-rendered user content).
4. **Accessibility required:** New/modified UI must meet baseline accessibility (labels, focus order, semantics/roles, text scaling, contrast, input alternatives).
5. **No secrets in code:** Never hardcode API keys, tokens, credentials, private URLs, or user PII. Use the repo’s secret management/config mechanism.
6. **Privacy-by-default:** Collect the minimum data necessary, avoid logging PII, and follow existing analytics/consent patterns.
7. **Dependency discipline:** Don’t add dependencies without a clear need, security review, and documentation/attribution updates per repo policy.
8. **No magic numbers:** Use named constants/configuration for UI metrics, thresholds, timeouts, and feature toggles.

## 4. Workflow (Plan–Act–Verify)
### 4.1 PLAN
Provide a brief plan before making changes:
- Files you’ll touch
- Behaviors you’ll add/modify
- Edge cases and failure modes
- Any UX/i18n/a11y impacts

### 4.2 ACT
- Implement the smallest correct change that solves the root cause.
- Keep changes focused; avoid drive-by refactors.
- Match existing code style, naming, and module boundaries for the target platform.

### 4.3 VERIFY (Required Checklist)
- **Localization:** New strings are present in base locale and translated across all supported locales.
- **Accessibility:** Labels/semantics added, focus order reasonable, text scales without truncation, color contrast acceptable.
- **Constants:** No new magic numbers; reusable values are centralized.
- **Logging:** Diagnostics go through the standard logger; errors include actionable context without sensitive data.
- **Safety:** No secrets/PII added; network and storage changes follow existing patterns.

## 5. Communication & Reviews
- Write changes so they’re easy to review: clear commits are helpful (but don’t commit unless asked).
- If requirements are ambiguous, ask targeted questions before implementing.
- When uncertain, prefer conservative behavior and guardrails (timeouts, retries, fallbacks) consistent with the platform’s conventions.
