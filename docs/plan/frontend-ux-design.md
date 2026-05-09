# Frontend UX / UI Design Plan

## Overview

The Cycling Coach frontend is a **desktop-first** single-page application built with Angular 21 and Angular Material. It targets a single user (the athlete) who wants a professional, clean coaching interface — similar in style to Strava or TrainingPeaks but focused on power-based training data.

---

## Design Principles

- **Clean & minimal light theme** — white cards on a light gray background, no dark mode for now
- **Data-dense but readable** — coaching metrics always visible at a glance, details on demand
- **Desktop-first** — optimised for laptop/monitor; no mobile breakpoints required in the current scope
- **Angular Material only** — no Tailwind or custom design systems; uses Material M3 component set

---

## Visual Design

### Color Palette

| Role | Value | Usage |
|---|---|---|
| Primary | `mat.$violet-palette` (M3) | Nav active states, buttons, accents |
| Page background | `#F5F7FA` | Main content area |
| Card surface | `#FFFFFF` | All `.cc-card` components |
| Border / divider | `--mat-sys-outline-variant` | Card borders, dividers |
| Text primary | `--mat-sys-on-surface` | Headings, values |
| Text secondary | `--mat-sys-on-surface-variant` | Labels, subtitles, meta |
| CTL accent | `#3F51B5` (blue) | Fitness metric, PMC line |
| ATL accent | `#FF9800` (orange) | Fatigue metric, PMC line |
| TSB positive | `#4CAF50` (green) | Fresh/ready form state |
| TSB warning | `#FF9800` (orange) | Slightly fatigued |
| TSB negative | `#F44336` (red) | High fatigue |

### Typography
- Font family: **Roboto** (Angular Material default)
- Metric values: 36px, weight 700
- Secondary values (e.g. W/kg under FTP): 15px, weight 600
- Card labels: 11px, weight 600, uppercase, 0.8px letter-spacing
- Body / metadata: 12–14px

### Shared CSS Utilities (global `styles.scss`)
- `.cc-card` — white card with 12px border-radius, subtle box-shadow, 24px padding
- `.cc-page` — page wrapper with 32px padding, max-width 1280px, centered

---

## Application Shell

### Layout

```
+----------------------+----------------------------------------+
|  SIDEBAR  (240px)    |  MAIN CONTENT (fluid, max 1280px)      |
|                      |                                        |
|  🚴 Cycling Coach    |  <router-outlet>                       |
|  ────────────────    |                                        |
|  📊 Dashboard        |                                        |
|  🚴 Rides            |                                        |
|  ⚙️  Settings         |                                        |
|                      |                                        |
|  [Sync ↻]  (footer)  |                                        |
+----------------------+----------------------------------------+
```

**Component:** `ShellComponent` (`features/shell/`)

- `mat-sidenav-container` with `mat-sidenav[mode="side"][opened]` — always visible on desktop
- Active nav link uses `routerLinkActive="active-link"` which applies `secondary-container` background + rounded pill style
- Sync button is full-width at the bottom of the sidebar
- No top toolbar — the sidebar header doubles as the app identity

---

## Routes

| Path | Component | NgRx state scope |
|---|---|---|
| `/` | `DashboardComponent` | `dashboard` feature (route-scoped) |
| `/rides` | `RidesListComponent` | `rides` feature (root-scoped) |
| `/rides/:id` | `RideDetailComponent` | `rides` feature (root-scoped) |
| `/settings` | `SettingsComponent` | `settings` feature (route-scoped) |

All routes are lazy-loaded. NgRx state is provided at route level (`provideState` / `provideEffects` in route `providers` array) so features are cleanly isolated.

---

## Pages

### Dashboard (`/`)

The primary daily-use screen. Loads in parallel: PMC data (last 90 days), 10 most recent rides, FTP history, app settings (for weight).

#### Metric Cards Row (4 cards, CSS grid)

| Card | Primary value | Secondary value | Accent |
|---|---|---|---|
| **CTL — Fitness** | e.g. `68` | "Chronic Training Load (42d)" | Blue left border |
| **ATL — Fatigue** | e.g. `74` | "Acute Training Load (7d)" | Orange left border |
| **TSB — Form** | e.g. `−6` | Contextual label (see below) | Color-coded (green/orange/red) |
| **FTP** | e.g. `285 W` | e.g. `4.32 W/kg` | Violet left border |

**TSB color logic:**
- TSB > 10 → blue, "Fresh — possibly under-trained"
- TSB 0–10 → green, "Ready to perform"
- TSB −10–0 → orange, "Slightly fatigued"
- TSB < −10 → red, "High fatigue — rest recommended"

**Info tooltips:** Each card has a small `info_outline` icon in the label row. Hovering shows a `matTooltip` with a cycling coach explanation of the metric:
- **CTL:** 42-day EWMA of TSS — aerobic fitness bank, slow to build and lose
- **ATL:** 7-day EWMA of TSS — recent fatigue, spikes and drops quickly
- **TSB:** CTL_yesterday − ATL_yesterday — form; sweet spot +5 to +15 for racing
- **FTP:** Highest sustainable 60-min power; auto-detected from 20min best × 0.95; W/kg is the key climbing metric (elite > 5.5 W/kg, competitive amateurs 3.5–4.5)

#### PMC Chart (Performance Management Chart)

Full-width Chart.js line chart below the metric cards:
- **X-axis:** Last 90 days (rolling, date labels, max 10 ticks)
- **Lines:** CTL (blue, filled), ATL (orange, filled), TSB (green, no fill)
- All lines use `tension: 0.4` (smooth), `pointRadius: 0` (clean), hover shows all three values
- Legend shown inline above the chart (colored dots + labels)

#### Recent Rides List

Scrollable list of the 10 most recent rides below the chart:
- Each row: ride name + date/duration/distance on the left; NP, TSS, IF badge on the right
- IF badge color: green (< 0.75), orange (0.75–0.90), red (≥ 0.90)
- Empty state shown if no rides synced yet

---

### Settings (`/settings`)

Two-tab layout using `mat-tab-group`:

#### Tab 1 — Power Zones

Read-only `mat-table` with 7 rows (Z1–Z7):

| Column | Content |
|---|---|
| Zone | Colored badge (Z1=blue, Z2=green, Z3=amber, Z4=orange, Z5=red, Z6=purple, Z7=black) |
| Name | Active Recovery / Endurance / Tempo / Lactate Threshold / VO₂max / Anaerobic / Neuromuscular |
| % FTP | Range derived from zone upper bounds in `SettingsProperties` |
| Watts | Calculated from current FTP (shown only if FTP is known) |
| Training purpose | Short description |

Zone bounds come from `cycling.zones.power.*` in `application.yml` (read-only).

#### Tab 2 — FTP History

Timeline list (sorted newest first) from `GET /api/ftp`:
- Each entry shows: FTP value (large), "Current" badge on the newest, date, source icon (auto/manual)
- Visual timeline with dot + connector line
- Empty state message if no FTP entries

---

## Component Inventory

### Shell
| Component | Path | Notes |
|---|---|---|
| `ShellComponent` | `features/shell/` | `mat-sidenav` layout, owns nav + sync button |

### Dashboard
| Component | Path | Notes |
|---|---|---|
| `DashboardComponent` | `features/dashboard/` | Page container, dispatches `loadDashboard` on init |
| `MetricCardComponent` | `features/dashboard/components/metric-card/` | Reusable; inputs: label, value, unit, secondaryValue, secondaryUnit, subtitle, description, accent |
| `PmcChartComponent` | `features/dashboard/components/pmc-chart/` | Chart.js wrapper; input: `PmcDataPoint[]` |
| `RecentRidesComponent` | `features/dashboard/components/recent-rides/` | Input: `RideSummary[]` |

### Settings
| Component | Path | Notes |
|---|---|---|
| `SettingsComponent` | `features/settings/` | Page container with `mat-tab-group` |
| `PowerZonesTableComponent` | `features/settings/components/power-zones/` | Inputs: `PowerZoneSettings`, `ftp: number` |
| `FtpHistoryComponent` | `features/settings/components/ftp-history/` | Input: `FtpEntry[]` |

---

## NgRx State Architecture

Each feature has its own slice registered at route level:

```
dashboard/+state/
  dashboard.actions.ts   — loadDashboard, loadDashboardSuccess, loadDashboardFailure
  dashboard.reducer.ts   — DashboardState: pmcData, recentRides, ftpHistory, appSettings, loading, error
  dashboard.effects.ts   — parallel forkJoin: PMC + rides + FTP + settings APIs
  dashboard.selectors.ts — selectLatestPmc, selectCurrentFtp, selectFtpPerKg, selectPmcData, etc.

settings/+state/
  settings.actions.ts    — loadSettings, loadSettingsSuccess, loadSettingsFailure
  settings.reducer.ts    — SettingsState: appSettings, ftpHistory, loading, error
  settings.effects.ts    — parallel forkJoin: settings + FTP APIs
  settings.selectors.ts  — selectPowerZones, selectFtpHistory, selectCurrentFtp, selectSettingsLoading
```

---

## Backend: Settings Domain

| Class | Responsibility |
|---|---|
| `SettingsController` | Thin delegate — one line, calls `settingsService.getAppSettings()` |
| `SettingsService` | Assembles `AppSettings` DTO from `SettingsProperties` + `UserProfileService` |
| `SettingsProperties` | `@ConfigurationProperties(prefix = "cycling")` — zone thresholds from `application.yml` |

`AppSettings` DTO fields: `aiProvider`, `aiModel`, `weightKg` (from latest `user_weight` row), `powerZones`, `hrZones`.

---

## Out of Scope (future pages)

- Calendar (monthly view with rides + planned workouts)
- Training Plan (AI-generated weekly plan)
- Coaching (AI chat + per-ride analysis)
- Nutrition (AI-generated plans)
- Mobile / responsive layout
- Ride detail charts (power curve, map, zone distribution)
