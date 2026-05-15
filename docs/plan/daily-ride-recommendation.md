# Daily AI Training Recommendation — Feature Design

Branch: `feat/daily-ride-recommendation`

## Overview

Each day the app recommends one of four training types based on the athlete's current PMC state and live local weather:

| Type | Trigger |
|------|---------|
| `OUTDOOR` | Good weather + form calls for structured work |
| `OUTDOOR_FUN` | Well-rested or base-building phase — structure would hurt more than help |
| `INDOOR` | Cold (< 5°C), rain (> 70%), strong gusts (> 60 km/h), or dark during workout |
| `REST` | TSB < -25, ATL/CTL ratio > 1.3, or 7+ consecutive days of negative TSB |

The recommendation is generated once per day (cached in `daily_recommendation` table) with a manual regenerate button. Weather is fetched from [Open-Meteo](https://open-meteo.com) (free, no API key). User location comes from the browser Geolocation API. Both Anthropic Claude and Ollama are supported, switchable via `AI_PROVIDER` env var.

---

## Architecture Decisions

### Weather abstraction (`weather/` package)

`WeatherProvider` is an interface — `CoachingService` depends on the interface, not the implementation. This means:
- Unit tests can inject a `MockWeatherProvider` without HTTP
- Future providers (OpenWeatherMap, WeatherAPI.com) implement `WeatherProvider` and register as `@Primary` or via `@ConditionalOnProperty`
- The Open-Meteo implementation (`OpenMeteoClient`) is just `@Component` — no special wiring required

```
weather/
  WeatherProvider.kt       — interface: fetchWeather(lat, lon): WeatherData
  WeatherData.kt           — data class: hourlyTemps, precipProbs, windGusts, sunrise, sunset
                             computed: minTemp, maxPrecipProb, maxWindGust, wouldBeDark
  OpenMeteoClient.kt       — @Component implements WeatherProvider; OkHttp + Jackson
```

### AI provider switching (`config/AiConfig.kt`)

Both `spring-ai-starter-model-anthropic` and `spring-ai-starter-model-ollama` are on the classpath. `AiConfig` injects both `AnthropicChatModel?` and `OllamaChatModel?` as nullable and selects the active one based on `ai.provider`. The inactive provider's bean may be absent without causing startup failure.

**Best Ollama model:** `qwen2.5:14b` (32 GB RAM) or `qwen2.5:7b` (16 GB). Highest structured JSON output reliability among local models.

When `AI_PROVIDER=ollama`, `AiConfig.@PostConstruct` calls `OllamaHealthChecker` which GETs `{OLLAMA_BASE_URL}/api/tags`. If unreachable: `WARN` log, app starts anyway, endpoint returns 503 until Ollama is up.

### Caching

`daily_recommendation` table, keyed by `date` (TEXT, UNIQUE). On `GET /api/coaching/recommendation?lat=&lon=&regenerate=false`, the backend returns the cached row if one exists for today. `regenerate=true` skips the cache and overwrites the row.

---

## Thresholds (road-cycling-coach reviewed)

| Signal | Threshold | Rule |
|--------|-----------|------|
| REST — fatigue | TSB < -25 | High accumulated load |
| REST — overreaching | ATL/CTL > 1.3 | Acute load spiked vs chronic baseline |
| REST — extended fatigue | 7+ consecutive negative TSB days | No recovery day detected |
| INDOOR — temperature | < 5°C | Road cycling safety |
| INDOOR — rain | > 70% probability | Acceptable rain risk threshold |
| INDOOR — wind | > 60 km/h gusts | Safety / control on road bike |
| INDOOR — darkness | workout end (now+3h) after sunset | Visibility |
| OUTDOOR_FUN — fresh | TSB > +15 | Rested enough that play beats structure |
| OUTDOOR_FUN — base phase | TSB −5 to +10 AND CTL < 40 | Structure not warranted at this fitness level |
| OUTDOOR_FUN — compliance | 3+ consecutive structured rides | Mental recovery; avoid training monotony |

---

## AI Context Sent Per Request

```
ATHLETE STATE
CTL / ATL / TSB
ATL/CTL ratio (overreaching signal > 1.3)
Training monotony = weeklyAvgTSS / weeklyTSSStdDev (warning > 2.0)
FTP (W)
Power zones Z1–Z5 (absolute watts, computed from FTP × zone% from settings)

LAST 7 RIDES
date, name, TSS, IF per ride

WEATHER (next 6 hours)
min/max temp (°C), max rain probability (%), max wind gust (km/h), dark flag

DECISION RULES (embedded in prompt)
```

Future context to add when available: days to next event/race, ride completion rate last 14 days.

---

## Prompt Output Format

AI is asked to return only:
```json
{"type": "OUTDOOR|OUTDOOR_FUN|INDOOR|REST", "content": "...workout description..."}
```

Content guidelines by type:
- **OUTDOOR**: zone + duration + interval format (e.g. "4×8 min at 95–105% FTP, 4 min recovery")
- **OUTDOOR_FUN**: duration range only, explicit instruction to disable targets, permission to disconnect
- **INDOOR**: Zwift workout name, ERG watts, warmup/intervals/cooldown breakdown
- **REST**: sleep target (8–9h), nutrition timing, whether tomorrow resumes or stays easy

---

## Database

Migration: `V3__coaching.sql`

```sql
CREATE TABLE daily_recommendation (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    date             TEXT NOT NULL UNIQUE,
    type             TEXT NOT NULL CHECK (type IN ('OUTDOOR', 'OUTDOOR_FUN', 'INDOOR', 'REST')),
    content          TEXT NOT NULL,
    weather_snapshot TEXT,        -- raw Open-Meteo JSON for audit/debug
    ai_provider      TEXT NOT NULL,
    generated_at     TEXT NOT NULL
);
```

---

## Backend Package Summary

```
config/
  AiConfig.kt              — @Configuration: selects ChatClient (Anthropic or Ollama)
  AiProperties.kt          — @ConfigurationProperties(prefix="ai"): provider, model

weather/
  WeatherProvider.kt       — interface
  WeatherData.kt           — data class + computed properties
  OpenMeteoClient.kt       — @Component implements WeatherProvider

coaching/
  CoachingController.kt    — GET /api/coaching/recommendation, implements CoachingApi
  CoachingService.kt       — orchestrator (cache → weather → context → AI → store)
  DailyRecommendationRepository.kt — jOOQ: findByDate, upsert
  OllamaHealthChecker.kt   — startup connectivity check, WARN if Ollama unreachable
```

---

## Frontend Summary

Recommendation card sits above the metric cards on the dashboard.

```
features/dashboard/
  +state/
    dashboard.actions.ts    — loadRecommendation, loadRecommendationSuccess/Failure,
                              regenerateRecommendation
    dashboard.reducer.ts    — recommendation, recommendationLoading, recommendationError state
    dashboard.selectors.ts  — selectRecommendation, selectRecommendationLoading, selectRecommendationError
    dashboard.effects.ts    — geolocation Promise → CoachingService.getDailyRecommendation()
  components/
    recommendation-card/    — type badge (OUTDOOR green, OUTDOOR_FUN teal, INDOOR blue, REST orange)
                              content, weather summary, regenerate button, loading/error states
  dashboard.component.ts    — dispatches loadRecommendation on init, onRegenerate()
  dashboard.component.html  — <app-recommendation-card> above metrics row
```

---

## Configuration (environment variables)

| Variable | Default | Description |
|----------|---------|-------------|
| `AI_PROVIDER` | `anthropic` | `anthropic` or `ollama` |
| `AI_MODEL` | `claude-3-5-sonnet-20241022` | Model name for Anthropic; for Ollama use `qwen2.5:14b` |
| `ANTHROPIC_API_KEY` | — | Required when `AI_PROVIDER=anthropic` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |

---

## Verification Checklist

1. `./mvnw generate-sources` — generates `CoachingApi.getDailyRecommendation` + Angular `DailyRecommendation`
2. `./mvnw test -Dgroups="unit"` — all 69 unit tests pass
3. `./mvnw spring-boot:run` with `ANTHROPIC_API_KEY=sk-...` — starts cleanly, Ollama WARN only if `AI_PROVIDER=ollama` and Ollama is down
4. Open dashboard → browser geolocation prompt → card loads with recommendation
5. Click Regenerate → new AI call, card updates
6. Restart backend → same-day recommendation served from DB cache (no AI call)
7. `AI_PROVIDER=ollama` with Ollama running (`ollama pull qwen2.5:14b`) → card works
