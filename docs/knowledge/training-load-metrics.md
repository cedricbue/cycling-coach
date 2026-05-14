# Training Load Metrics - TSS, CTL, and ATL

These three metrics form the foundation of the **Performance Management Chart (PMC)**, originally developed by Dr. Andrew Coggan for cycling, now used across endurance sports.

---

## TSS — Training Stress Score

A single number representing the **load of one workout**, combining intensity and duration.

### Formula

```
TSS = (duration_seconds × NP × IF) / (FTP × 3600) × 100 
```

| Variable | Meaning |
|---|---|
| `duration_seconds` | Moving time of the workout |
| `NP` | Normalized Power (watts) |
| `IF` | Intensity Factor = NP / FTP |
| `FTP` | Functional Threshold Power (your baseline) |

### Interpretation

| TSS | Meaning |
|---|---|
| < 150 | Low stress, recovery same day |
| 150–300 | Moderate, some residual fatigue next day |
| 300–450 | High, significant multi-day fatigue |
| > 450 | Extreme (e.g. long gran fondo) |

> 100 TSS = 1 hour at exactly FTP (IF = 1.0)

---

## ATL — Acute Training Load ("Fatigue")

An **exponentially weighted moving average** of TSS over the last ~7 days. Represents short-term fatigue.

### Formula

```
ATL_today = ATL_yesterday × (1 − 1/7) + TSS_today × (1/7)
```

- Time constant: **7 days**
- Responds quickly to recent training
- High ATL = you've been training hard recently = fatigued
- Drops fast during rest weeks

---

## CTL — Chronic Training Load ("Fitness")

An **exponentially weighted moving average** of TSS over the last ~42 days. Represents long-term fitness.

### Formula

```
CTL_today = CTL_yesterday × (1 − 1/42) + TSS_today × (1/42)
```

- Time constant: **42 days**
- Slow to build, slow to decay
- Higher CTL = higher aerobic fitness base
- Takes ~3 months of consistent training to meaningfully raise

---

## TSB — Training Stress Balance ("Form")

Derived from CTL and ATL, TSB represents your **readiness to perform**.

```
TSB_today = CTL_yesterday − ATL_yesterday
```

| TSB Range | State |
|---|---|
| > +25 | Very fresh, possibly detrained |
| +5 to +25 | Fresh, race-ready ("peak form") |
| −10 to +5 | Neutral |
| −10 to −30 | Productive training zone, some fatigue |
| < −30 | Overreaching / overtraining risk |

Note: 
These are reasonable heuristics, but in practice they are highly individual, sport-specific, and dependent on training history—for example, elite athletes often train at a TSB of −30 to −50 without issues, whereas beginners may struggle at −15—so they should be treated as guidelines rather than strict thresholds.
---

## The Relationship — Performance Management Chart

```
High CTL
   │        ← build phase (ATL > CTL, TSB negative)
   │   /\   ← taper (ATL drops faster than CTL)
   │  /  \____  peak TSB here = race day
   │ /
   └──────────────────────────────── time
         CTL builds slowly
         ATL spikes and drops quickly
         TSB = gap between them
```

**The taper mechanic:** Because ATL has a shorter time constant (7d) than CTL (42d), a rest week causes ATL to drop faster than CTL, widening the TSB gap — you get fresher while retaining most of your fitness.

---

## Calculating from a TCX File

From the trackpoint data you'd compute:

1. **Extract power stream** from `TPX/Watts` (or speed + HR for running)
2. **Compute NP** — 30s rolling average of power, raised to 4th power, average, then 4th root
3. **Compute IF** = NP / FTP
4. **Compute TSS** using the formula above
5. **Update ATL/CTL** using the EWMAs with today's TSS

> The state (yesterday's CTL/ATL) must be persisted between workouts — it's a running calculation, not derivable from a single file.