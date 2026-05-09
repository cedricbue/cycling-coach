# FTP Estimation from Historical Ride Data

When no `current_ftp` is set on the user profile, the system estimates FTP from historical
best-power data across recent rides. This document defines the algorithm, field priorities,
confidence levels, and edge-case handling.

---

## Background

**FTP (Functional Threshold Power)** is the highest average power a rider can sustain for
~60 minutes. Two well-validated methods let us estimate it from shorter efforts:

- **Coggan's 20-min rule**: `FTP ≈ best_power_20min × 0.95`
- **Critical Power (CP) model**: fits a power-duration curve to multiple data points;
  the asymptote (CP) is mathematically very close to FTP (typically within 1–5%).

The CP model is the primary method when enough data points exist; single-point estimates
act as weighted fallbacks.

---

## Phase 1 — Data Collection & Filtering

```
INPUTS:
  rides[]                  // All historical ride records
  LOOKBACK_DAYS  = 90      // Only use rides within the last 90 days
  MIN_DURATION_S = 1200    // Ignore rides shorter than 20 minutes

STEP 1.1 — Filter rides
  eligible = rides
    .filter(ride.date >= today - LOOKBACK_DAYS)
    .filter(ride.duration_seconds >= MIN_DURATION_S)

  if eligible.count < 1:
    return { ftp: null, confidence: "insufficient_data" }

STEP 1.2 — Extract all-time bests across eligible rides
  // Always use MAX — FTP is a ceiling metric, not an average.
  // Apply a per-ride duration guard before extracting each field:
  //   exclude best_power_5min  if ride.duration_seconds < 300
  //   exclude best_power_10min if ride.duration_seconds < 600
  //   exclude best_power_20min if ride.duration_seconds < 1200
  //   exclude best_power_60min if ride.duration_seconds < 3600

  best = {
    "5min":  MAX(ride.best_power_5min  across eligible, filtered as above),
    "10min": MAX(ride.best_power_10min across eligible, filtered as above),
    "20min": MAX(ride.best_power_20min across eligible, filtered as above),
    "60min": MAX(ride.best_power_60min across eligible, filtered as above),
  }

  // best_power_5s and best_power_30s are EXCLUDED — these are neuromuscular /
  // anaerobic durations and will severely overestimate FTP.
  // normalized_power is EXCLUDED — it is ride-pattern-dependent, not a best effort.
```

---

## Phase 2 — CP Model (Primary Method)

The 2-parameter Critical Power model:

```
P(t) = CP + W' / t

where:
  P(t) = average power at duration t (seconds)
  CP   = Critical Power ≈ FTP  (the asymptote)
  W'   = Anaerobic Work Capacity (joules, always positive)
```

Rearranged into linear form for least-squares regression:

```
P(t) × t  =  CP × t  +  W'
    Y      =  m × X  +  b     (simple linear regression)
```

```
STEP 2.1 — Build regression data points
  DURATION_SECONDS = { "5min": 300, "10min": 600, "20min": 1200, "60min": 3600 }

  data_points = []
  for each (label, secs) in DURATION_SECONDS:
    if best[label] is not null and best[label] > 0:
      data_points.append({ x: secs, y: best[label] * secs })   // work in joules

STEP 2.2 — Fit regression if enough points
  if data_points.count >= 2:
    (CP, W_prime, r_squared) = linear_regression(x_values, y_values)

    model_valid = (
      CP > 50          // below 50W is physiologically implausible
      AND CP < 600     // above 600W is implausible
      AND W_prime > 0  // must be positive
      AND W_prime < 100000  // above 100 kJ is implausible
    )
```

> **Why exclude 5s/30s from regression?** Those durations are dominated by the W'/t term,
> providing no useful signal about CP and actively distorting the regression intercept.

---

## Phase 3 — Single-Point Fallback Estimates

Calculated independently; combined with the CP model in Phase 4.

| Source | Correction factor | Weight |
|---|---|---|
| `best_power_60min` | × 1.00 | **1.00** — direct definition |
| CP model output | — | **0.90 × R²** — scales with fit quality |
| `best_power_20min` | × 0.95 | **0.85** — Coggan, widely validated |
| `best_power_10min` | × 0.85 | **0.60** — broader correction, noisier |
| `best_power_5min` | ❌ standalone | Used in regression only, not as direct estimate |

```
STEP 3.1 — Collect estimates
  estimates = []

  if best["60min"] is not null:
    estimates.append({ value: round(best["60min"] * 1.00), weight: 1.00, method: "60min_direct" })

  if best["20min"] is not null:
    estimates.append({ value: round(best["20min"] * 0.95), weight: 0.85, method: "20min_coggan" })

  if best["10min"] is not null:
    estimates.append({ value: round(best["10min"] * 0.85), weight: 0.60, method: "10min_estimate" })

  if model_valid:
    estimates.append({ value: round(CP), weight: 0.90 * r_squared, method: "cp_model" })
```

---

## Phase 4 — Combine & Return

```
STEP 4.1 — Weighted average
  if estimates is empty:
    return { ftp: null, confidence: "insufficient_data" }

  final_ftp = round( SUM(e.value * e.weight) / SUM(e.weight) )

STEP 4.2 — Confidence level
  strong_signals = count of estimates with weight >= 0.85
  total_signals  = estimates.count

  if strong_signals >= 2 OR (model_valid AND r_squared >= 0.98):
    confidence = "high"
  else if total_signals >= 2 OR (model_valid AND r_squared >= 0.90):
    confidence = "medium"
  else:
    confidence = "low"

RETURN {
  estimated_ftp:    final_ftp,
  confidence:       "high" | "medium" | "low" | "insufficient_data",
  methods_used:     [e.method ...],
  r_squared:        r_squared if model_valid else null,
  ride_count:       eligible.count,
  data_window_days: 90
}
```

---

## Field Priority Summary

```
Priority order for FTP calculation in a ride:

1. user_profile.current_ftp            (explicit, always preferred)
2. FTP estimation — CP model            (≥2 long-duration data points, R² ≥ 0.90)
3. FTP estimation — 60min direct        (highest single-point trust)
4. FTP estimation — 20min × 0.95       (Coggan, widely validated)
5. FTP estimation — 10min × 0.85       (lower confidence fallback)
6. null                                 (prompt user to complete FTP test)
```

---

## Minimum Data Requirements

| Threshold | Ride count | Notes |
|---|---|---|
| Minimum viable | 1 ride | Must contain at least one qualifying power field |
| Recommended | 10–20 rides | Better coverage across all duration fields |
| Lookback window | 90 days | Beyond that, fitness may have changed materially |

If no qualifying data exists, return `null` and surface a prompt for a structured FTP test.

---

## Edge Cases

| Scenario | Handling |
|---|---|
| All rides shorter than 20 min | Return `null`; prompt for longer effort |
| CP model yields negative W' | Discard CP result; use single-point fallbacks |
| Only 5s/30s power data | Return `null` — insufficient for FTP estimation |
| Estimated FTP < 80W or > 550W | Clamp and flag for manual review |
| Rider returning from injury (data > 90 days old) | Optionally expand window to 180 days with confidence = `"low"` |

---

## Implementation Notes

- This algorithm lives in `com.cyclingcoach.ftp.FtpEstimationService`.
- Called by `RideComputeService` when `UserProfileRepository.findCurrentFtp()` returns `null`.
- The estimated value is used **only** for that ride's computation; it is not automatically
  persisted to `user_profile.current_ftp`. Persist to `ftp_test` (type `ESTIMATED`) for
  auditability so users can review and confirm or override.
- Re-estimation happens per ride computation — rides imported before an FTP is set will
  each independently estimate from the rides that preceded them on that date.
