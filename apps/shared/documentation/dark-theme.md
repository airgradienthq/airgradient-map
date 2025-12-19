# Dark Theme Design Guide

The AirGradient dark theme ensures consistent visuals for the map web app and the upcoming iOS/Android native apps. This guide captures the canonical palette, how it is stored, and the expectations for keeping every platform in sync.

## Palette Sources

- **Shared source of truth**: `apps/shared/constants/dark-theme.json` exposes the complete palette (background, surface, typography, semantic statuses, and map overrides). Every platform can import or replicate these tokens.
- **Website implementation file**: `apps/website/constants/shared/colors.ts` houses the Nuxt/Vue specific mappings that translate the shared palette into CSS variables, chart scales, and utility classes used across the frontend.

> **Important:** If you adjust any color token, you must update **both** files. Website-specific derivatives (e.g., chart scales or Leaflet overrides) also need to be reviewed. Mobile teams should mirror the same hexadecimal values inside their native resource files (SwiftUI color sets / Android XML).

## Core Tokens

| Token            | Hex       | Purpose                                  |
| ---------------- | --------- | ---------------------------------------- |
| `background`     | `#121212` | Page background                          |
| `surface`        | `#1c1c1c` | Cards, dialogs                           |
| `surfaceAlt`     | `#222222` | Elevated cards/dropdowns                 |
| `border`         | `#2f2f2f` | Subtle separators                        |
| `textPrimary`    | `#f5f5f5` | Default text                             |
| `textSecondary`  | `#cfcfcf` | Muted/secondary text                     |
| `accentPrimary`  | `#00c2b7` | Actions, toggles, links                  |
| `accentSecondary`| `#58d32f` | Secondary actions / positive emphasis    |
| `success`        | `#22c55e` | Status-success indicators                |
| `warning`        | `#fbbf24` | Warning banners/icons                    |
| `danger`         | `#ef4444` | Errors, destructive actions              |

### Map Overrides

`apps/shared/constants/dark-theme.json` also defines neutral grays for the MapLibre layers (`map.background`, `map.water`, `map.land`, `map.boundary`, `map.road`, `map.label`). The website’s `Map.vue` loads these colors by customizing the MapLibre style once the dark map tiles are applied. Mobile map views should use the same values (or nearest equivalents in the Map SDK being used).

## Cross-Platform Implementation Notes

1. **Website (Nuxt/Vue)**  
   - Imports tokens via `apps/website/constants/shared/colors.ts`.  
   - Applies map-specific overrides inside `apps/website/components/map/Map.vue` (see `customizeDarkStyleColors`).  
   - Leaflet overlays and chart components rely on the shared token set to keep typography and backgrounds consistent.

2. **iOS**  
   - Mirror the palette inside an asset catalog (named colors) or a Swift enum.  
   - When tokens change, regenerate the asset colors so they match `dark-theme.json`.  
   - Reuse the map overrides for MKMapView/MapLibre by mapping to the platform’s styling JSON or overlays.

3. **Android**  
   - Define colors in `colors.xml` (or Compose `Color` objects).  
   - Update the XML/Compose palette whenever the shared constants change.  
   - Ensure MapLibre/Google Maps styling JSON carries the same map override colors.

## Change Workflow Checklist

1. Update `apps/shared/constants/dark-theme.json` with the new hex values.  
2. Reflect the change in `apps/website/constants/shared/colors.ts` (plus any derivative scale files).  
3. Notify mobile teams to sync their native resources; include the token list and reasoning.  
4. Validate the map overrides (web + mobile) to prevent regressions in contrast/accessibility.  
5. Record the change in release notes or design documentation for future reference.

Following this process ensures every app (web, iOS, Android) renders the dark theme consistently and avoids drift between platforms.
