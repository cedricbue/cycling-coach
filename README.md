# Cycling Coach

Personal road cycling training analysis tool. Syncs rides from Garmin Connect, computes training metrics (NP, IF, TSS, CTL/ATL/TSB), and provides AI coaching via Ollama or Anthropic.

## Running with Docker Compose

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (includes Docker Compose)
- A Garmin Connect account
- [Ollama](https://ollama.com) running locally (or an Anthropic API key)

### 1. Create a `.env` file

Create `compose/.env` with your credentials:

```env
GARMIN_EMAIL=your@email.com
GARMIN_PASSWORD=yourpassword

# AI provider — choose one:
AI_PROVIDER=ollama                                       # local Ollama (default)
OLLAMA_BASE_URL=http://host.docker.internal:11434        # Ollama on the host machine

# AI_PROVIDER=anthropic                                  # or Anthropic cloud
# ANTHROPIC_API_KEY=sk-ant-...
```

> `compose/.env` is gitignored — your credentials are never committed.

### 2. Start the application

```bash
cd compose
docker compose up --build
```

The first build takes a few minutes (downloads dependencies, builds the Angular frontend). Subsequent builds are faster due to Docker layer caching.

The app is available at **http://localhost:8081**.

### 3. Verify it's running

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

### Stopping and restarting

```bash
docker compose down          # stop containers (data is preserved)
docker compose up            # start again (no rebuild)
docker compose up --build    # rebuild after code changes
```

Your SQLite database is stored in `compose/data/` on the host and survives restarts.

---

## Configuration reference

All configuration is via environment variables (set in `compose/.env`).

| Variable | Required | Default | Description |
|---|---|---|---|
| `GARMIN_EMAIL` | Yes | — | Garmin Connect login email |
| `GARMIN_PASSWORD` | Yes | — | Garmin Connect password |
| `AI_PROVIDER` | No | `ollama` | AI backend: `ollama` or `anthropic` |
| `OLLAMA_BASE_URL` | No | `http://host.docker.internal:11434` | Ollama API base URL |
| `ANTHROPIC_API_KEY` | No | — | Required when `AI_PROVIDER=anthropic` |

### Garmin sync tuning (optional)

These can be added to `compose/.env` to override defaults:

| Variable | Default | Description |
|---|---|---|
| `SYNC_GARMIN_SYNC_INITIAL-FETCH-DAYS` | `365` | Days of history to fetch on first sync |
| `SYNC_GARMIN_SYNC_PAGE-SIZE` | `100` | Activities fetched per Garmin API page |
| `SYNC_GARMIN_SYNC_MAX-CONCURRENT-DOWNLOADS` | `5` | Parallel TCX downloads per page |
| `SYNC_GARMIN_SYNC_INTERVAL-MS` | `21600000` | Sync interval in ms (default 6 hours) |

---

## Local development (without Docker)

### Prerequisites

- Java 25
- Maven 3.9+
- Node 20 (or let the Maven build download it)

### Backend

```bash
mvn spring-boot:run
# API available at http://localhost:8002
```

### Frontend

```bash
cd frontend
ng serve
# UI available at http://localhost:4200 (proxies API to :8002)
```

### Run tests

```bash
mvn verify
```
