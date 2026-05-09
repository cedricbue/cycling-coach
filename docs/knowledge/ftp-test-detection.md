# FTP Test Detection from Ride Profile

When a ride is computed, the system checks whether it represents an FTP test based on the
activity name and the ride's power profile. If detected, it records the result in `ftp_test`
and notifies the user profile to update `current_ftp`.

This document defines the test types, detection heuristics, FTP formulas, and validation
rules — authored with guidance from the road cycling coach.

---

## Test Types

| `test_type` value | Protocol | Core metric |
|---|---|---|
| `RAMP_TEST` | Progressive ramp to failure (TrainerRoad, SYSTM, British Cycling) | `best_power_1min` |
| `TWENTY_MIN_TEST` | Coggan 20-minute sustained effort | `best_power_20min` |
| `SIXTY_MIN_TEST` | True 60-min / "Hour of Power" time trial | `best_power_60min` |
| `UNKNOWN` | Detected as FTP test by name but profile is ambiguous | fallback chain |
| `ESTIMATED` | FTP estimated from previous rides (no test performed) | weighted CP model |

> **Why no 8-minute test?** The Bossi/Carmichael 2×8-min protocol requires `best_power_8min`,
> which is not in the data model. Without that field no reliable formula can be applied.

---

## Detection Algorithm

### Step 1 — Name-based pre-filter

An activity qualifies for FTP detection only if its name (case-insensitive) contains at least
one of:

```
"ftp", "ramp test", "20 min test"
```

If none match, skip all further processing.

### Step 2 — Test type classification

Apply name signals first; use power-profile signals to confirm or override.

#### `RAMP_TEST`

**Name signals:** `"ramp"`, `"ramp test"`, `"map test"`

**Power-profile signals** — require **3 of 4**:

| Signal | Threshold | Rationale |
|---|---|---|
| `variability_index` | > 1.08 | Power increases continuously → high VI |
| `duration_seconds` | 900 – 2400 s | Ramps are short (15–40 min) |
| `best_power_1min / avg_power` | > 1.40 | Final 1-min sprint far above average |
| `best_power_5min / best_power_20min` | > 1.20 | Steep power-duration curve (classic ramp shape) |

#### `TWENTY_MIN_TEST`

**Name signals:** `"20 min"`, `"20min"`, `"20-min"`, `"twenty min"`, `"20 minute"`,
`"threshold test"`, or the generic `"ftp test"` / `"ftp"` **only if** the name does NOT also
contain `"ramp"` or `"60"`.

**Power-profile signals** — require **3 of 4**:

| Signal | Threshold | Rationale |
|---|---|---|
| `duration_seconds` | 2100 – 5400 s (35–90 min) | Warm-up + blowouts + 20-min effort + cooldown |
| `variability_index` | 1.01 – 1.08 | Mostly steady-state; warm-up adds some variance |
| `best_power_20min / avg_power` | 1.05 – 1.25 | 20-min block pulls 20-min power above average |
| `best_power_20min / best_power_60min` | > 1.10 (or 60min = 0) | Ride not long enough for a real 60-min effort |

#### `SIXTY_MIN_TEST`

**Name signals:** `"60 min"`, `"60min"`, `"60-min"`, `"one hour"`, `"1 hour"`,
`"hour of power"`, `"hour power"`

**Power-profile signals** — require **all 3**:

| Signal | Threshold | Rationale |
|---|---|---|
| `duration_seconds` | > 3300 s (55 min) | Must have a valid 60-min window |
| `variability_index` | < 1.05 | True TT-style effort is very steady |
| `avg_power / best_power_60min` | > 0.88 | Average power stays close to best 60-min power |

### Step 3 — Fallback (type = `UNKNOWN`)

When no confident classification is reached, apply in order:

```
1. duration > 3300 AND variability_index < 1.05
       → SIXTY_MIN_TEST

2. variability_index > 1.08 AND duration < 2700
       → RAMP_TEST

3. best_power_20min > 0 AND duration >= 2100
       → TWENTY_MIN_TEST  (most common; conservative 0.95 discount makes it the safe fallback)

4. None of the above
       → UNKNOWN — store in ftp_test, do NOT update current_ftp
```

---

## FTP Formulas

| `test_type` | Formula |
|---|---|
| `RAMP_TEST` | `FTP = round(best_power_1min × 0.75)` |
| `TWENTY_MIN_TEST` | `FTP = round(best_power_20min × 0.95)` |
| `SIXTY_MIN_TEST` | `FTP = best_power_60min` (no discount — 60-min power **is** FTP by definition) |
| `UNKNOWN` | No FTP update |

---

## Validation Rules

Applied in sequence before writing to `ftp_test` or updating `user_profile`.

### Absolute bounds

| Constraint | Threshold |
|---|---|
| Minimum FTP | ≥ 60 W |
| Maximum FTP | ≤ 550 W |

### Source data sanity (per type)

| Type | Pre-condition required |
|---|---|
| `RAMP_TEST` | `best_power_1min > 80` |
| `TWENTY_MIN_TEST` | `best_power_20min > 80` AND `duration_seconds ≥ 1500` |
| `SIXTY_MIN_TEST` | `best_power_60min > 0` AND `duration_seconds ≥ 3300` |

### Change guard (requires a prior FTP on record)

```
delta_pct = (new_ftp - previous_ftp) / previous_ftp × 100

+15% to +25%   → save to ftp_test with notes = "NEEDS_REVIEW: large increase"; update ftp
> +25%         → save to ftp_test with notes = "REJECTED: implausible gain"; do NOT update ftp
-20% to -35%   → save to ftp_test with notes = "NEEDS_REVIEW: large drop"; update ftp
< -35%         → save to ftp_test with notes = "REJECTED: implausible regression"; do NOT update ftp
```

Gains are held to a stricter cap than losses: overclaiming FTP is more damaging to training
prescription than being slightly conservative.

### Cross-signal consistency

```
FTP < best_power_5min   (5-min power always exceeds FTP)
FTP < best_power_10min  (same)
FTP > best_power_60min × 0.85  (FTP shouldn't be implausibly below any 60-min effort)
```

Failures here set `notes = "NEEDS_REVIEW: inconsistent power curve"` but do NOT block the update.

---

## Implementation Notes

- Lives in `com.cyclingcoach.ftp.FtpTestDetectionService`.
- Triggered by `RideCalculatedEvent` (event-driven).
- Publishes `FtpTestDetectedEvent` when a valid FTP is detected → `UserProfileRepository`
  listener updates `current_ftp`.
- Activity name queried from `ActivityRepository.findNameById()`.
- Ride metrics queried from `RideRepository.findMetricsById()`.
- `ftp_test.test_type` DB constraint expanded in migration V3 to include all new enum values.
