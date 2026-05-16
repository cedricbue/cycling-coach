# Bike Fit Analysis — Implementation Plan

## Context

Add a bike fit analysis feature to the cycling coach app. The user uploads a side-view cycling video, the backend stores it on disk, forwards it to a local pose-estimation service (MediaPipe/RTMPose), stores the returned per-frame landmarks, and notifies the frontend via SSE when done. The frontend renders the skeleton overlay on the video, computes and displays the 6 clinically relevant bike-fit angles per frame, and provides a manual angle-measurement tool. Angle calculations are entirely frontend-side.

The feature follows the app's existing API-first workflow (edit YAML → generate-sources → implement generated interface), NgRx patterns, and domain-package backend structure. It introduces two things that don't exist yet: **disk-based file storage** (videos can be 50–200 MB, unsuitable for SQLite blobs) and **SSE** (analysis takes 30 s–3 min).

Reviewed by the road-cycling-coach agent. All 6 angles and the BDC/TDC detection approach are validated below.

---

## Critical Files

| File | Change |
|------|--------|
| `src/main/resources/api-spec/cycling-coach-api.yaml` | Add bike-fit paths + schemas |
| `src/main/resources/db/migration/V1__init.sql` | Add `bike_fit_analysis` table |
| `src/main/resources/application.yml` | Multipart limits, async timeout, bike-fit config |
| `frontend/src/app/app.routes.ts` | Add bike-fit routes |
| `frontend/src/app/features/shell/shell.component.html` | Add nav link |
| **New**: `src/main/kotlin/com/cyclingcoach/bikefit/` | New backend package |
| **New**: `frontend/src/app/features/bike-fit/` | New frontend feature |

---

## Phase 1 — Schema & Code Generation

### 1.1 DB — add to `V1__init.sql` (before the seed INSERT)

```sql
CREATE TABLE bike_fit_analysis (
    id                TEXT PRIMARY KEY,  -- UUID
    status            TEXT NOT NULL CHECK (status IN ('PROCESSING','DONE','FAILED')),
    video_path        TEXT NOT NULL,     -- relative: data/bike-fit/{uuid}/video.{ext}
    original_filename TEXT NOT NULL,
    pose_model        TEXT NOT NULL CHECK (pose_model IN ('mediapipe','rtmpose')),
    pose_schema       TEXT CHECK (pose_schema IN ('mediapipe_33','coco_17','halpe_26')),
    fps               REAL,
    total_frames      INTEGER,
    landmarks_json    TEXT,              -- full LandmarksReport JSON; set on DONE
    error_message     TEXT,             -- set on FAILED
    created_at        TEXT NOT NULL DEFAULT (datetime('now')),
    completed_at      TEXT
);
CREATE INDEX idx_bike_fit_created ON bike_fit_analysis (created_at DESC);
```

Reset DB: `rm ./data/cycling-coach.db` then `./mvnw spring-boot:run` or `generate-sources`.

### 1.2 API spec — add to `cycling-coach-api.yaml`

**New tag** (in `tags:` list):
```yaml
  - name: bike-fit
    description: Bike fit video analysis — pose landmark processing and angle assessment
```

**New paths** (after existing paths):
```yaml
  /api/bike-fit/analyses:
    post:
      tags: [bike-fit]
      operationId: createBikeFitAnalysis
      summary: Upload cycling video and start pose landmark analysis
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [video, poseModel]
              properties:
                video:
                  type: string
                  format: binary
                poseModel:
                  type: string
                  enum: [mediapipe, rtmpose]
                mediapipeComplexity:
                  type: integer
                  minimum: 0
                  maximum: 2
                rtmposeMode:
                  type: string
                  enum: [lightweight, balanced, performance]
                rtmposeSchema:
                  type: string
                  enum: [coco17, halpe26]
                device:
                  type: string
                  enum: [auto, cpu, cuda, mps]
                  default: auto
      responses:
        "202":
          description: Analysis created and processing started
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/BikeFitAnalysisSummary"
    get:
      tags: [bike-fit]
      operationId: listBikeFitAnalyses
      summary: List all bike fit analyses ordered by created_at desc
      responses:
        "200":
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: "#/components/schemas/BikeFitAnalysisSummary"

  /api/bike-fit/analyses/{id}:
    get:
      tags: [bike-fit]
      operationId: getBikeFitAnalysis
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        "200":
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/BikeFitAnalysisDetail"
        "404":
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"

  /api/bike-fit/analyses/{id}/video:
    get:
      tags: [bike-fit]
      operationId: getBikeFitVideo
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        "200":
          content:
            video/mp4:
              schema:
                type: string
                format: binary
        "404":
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
```

> **SSE endpoint** (`/api/bike-fit/analyses/{id}/events`) is **not** added to the spec — the generated Angular method cannot use native `EventSource`. Implement it as a plain `@GetMapping` on the controller outside the generated interface.

**New schemas** (in `components/schemas`):
```yaml
    BikeFitAnalysisSummary:
      type: object
      required: [id, status, poseModel, originalFilename, createdAt]
      properties:
        id:
          type: string
        status:
          type: string
          enum: [PROCESSING, DONE, FAILED]
        poseModel:
          type: string
          enum: [mediapipe, rtmpose]
        poseSchema:
          type: string
          enum: [mediapipe_33, coco_17, halpe_26]
        originalFilename:
          type: string
        fps:
          type: number
          format: double
        totalFrames:
          type: integer
        errorMessage:
          type: string
        createdAt:
          type: string
          format: date-time
        completedAt:
          type: string
          format: date-time

    BikeFitAnalysisDetail:
      allOf:
        - $ref: "#/components/schemas/BikeFitAnalysisSummary"
        - type: object
          properties:
            landmarksJson:
              type: string
              description: Full LandmarksReport JSON; present only when status=DONE
```

### 1.3 `application.yml` additions

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 502MB
      file-size-threshold: 1MB
  mvc:
    async:
      request-timeout: -1   # disable MVC async timeout — SSE can be open for minutes

server:
  tomcat:
    max-http-form-post-size: -1

bike-fit:
  data-dir: ${BIKE_FIT_DATA_DIR:./data/bike-fit}
  landmarks-api-url: ${LANDMARKS_API_URL:http://0.0.0.0:8002}
```

### 1.4 Regenerate

```bash
rm ./data/cycling-coach.db
./mvnw generate-sources
```

This produces `BikeFitApi`, `BikeFitAnalysisSummary`, `BikeFitAnalysisDetail` (Kotlin + TypeScript) and the jOOQ `BikeFitAnalysis` table class.

---

## Phase 2 — Backend (`com.cyclingcoach.bikefit`)

### File structure

```
bikefit/
  BikeFitProperties.kt       @ConfigurationProperties(prefix="bike-fit")
  SseEmitterRegistry.kt      ConcurrentHashMap<String, SseEmitter>
  LandmarksApiClient.kt      OkHttp multipart POST to landmarks service
  BikeFitRepository.kt       jOOQ: insert, updateDone, updateFailed, findAll, findById
  BikeFitService.kt          UUID, disk write, async dispatch, SSE notify
  BikeFitController.kt       implements BikeFitApi + plain @GetMapping for SSE
```

### Key implementation details

**`BikeFitProperties`**
```kotlin
@Component
@ConfigurationProperties(prefix = "bike-fit")
data class BikeFitProperties(var dataDir: String = "./data/bike-fit",
                              var landmarksApiUrl: String = "http://0.0.0.0:8002")
```

**`SseEmitterRegistry`** — `@Component`
- `register(id: String): SseEmitter` — creates with `Long.MAX_VALUE` timeout, removes on completion/timeout/error callbacks
- `completeOk(id: String, json: String)` — sends event then calls `complete()`
- `completeError(id: String, json: String)` — same
- Guard: if emitter already removed (client disconnected), skip send silently

**`LandmarksApiClient`** — `@Component`
- OkHttp client with `readTimeout(10, MINUTES)`, `writeTimeout(5, MINUTES)`
- Reads video from `Path` (not from `MultipartFile` bytes) — keeps heap clean
- Builds `MultipartBody` with `file.asRequestBody()` (lazy read from disk)
- Deserializes response to internal `LandmarksReport` data class

**`BikeFitRepository`** — `@Repository`
- `findAll()` — SELECT all columns **except** `landmarks_json` (can be 5 MB per row)
- `findById(id)` — SELECT all including `landmarks_json`

**`BikeFitService`**

`startAnalysis(file, params)`:
1. Generate UUID
2. Create `{dataDir}/{uuid}/` directory; write file bytes via `Files.write(path, file.bytes)`
3. `repository.insert(...)` → status = PROCESSING
4. Return `BikeFitAnalysisSummary` (202 response goes out here)
5. Call `processAsync(id, videoPath, params)` (returns immediately)

`@Async(VIRTUAL_THREAD_EXECUTOR) processAsync(...)`:
1. `landmarksApiClient.analyze(videoPath, params)` — blocks on landmarks API
2. On success: `objectMapper.writeValueAsString(report)` → `repository.updateDone(...)` → `sseRegistry.completeOk(id, "{\"status\":\"DONE\"}")`
3. On exception: `repository.updateFailed(id, ex.message)` → `sseRegistry.completeError(id, "{\"status\":\"FAILED\"}")`

**`BikeFitController`**
- Implements `BikeFitApi` (generated) for `createBikeFitAnalysis`, `listBikeFitAnalyses`, `getBikeFitAnalysis`
- `getBikeFitVideo`: return `ResponseEntity<Resource>` with `FileSystemResource` — Spring's `ResourceHttpMessageConverter` automatically handles HTTP Range requests (needed for video seeking)
- **SSE — separate `@GetMapping`** (not generated interface):

```kotlin
@GetMapping("/api/bike-fit/analyses/{id}/events",
            produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
fun streamEvents(@PathVariable id: String): ResponseEntity<SseEmitter> {
    val analysis = bikeFitService.findById(id) ?: return ResponseEntity.notFound().build()
    // Race: analysis already done before client subscribed
    if (analysis.status != "PROCESSING") {
        val emitter = SseEmitter(0L)
        emitter.send("""{"status":"${analysis.status}"}""")
        emitter.complete()
        return ResponseEntity.ok(emitter)
    }
    return ResponseEntity.ok(sseRegistry.register(id))
}
```

---

## Phase 3 — Frontend (`features/bike-fit`)

### Directory structure

```
features/bike-fit/
  +state/
    bike-fit.actions.ts
    bike-fit.reducer.ts
    bike-fit.effects.ts
    bike-fit.selectors.ts
  bike-fit-list/
    bike-fit-list.component.ts/html/scss   — table + Upload button
  bike-fit-detail/
    bike-fit-detail.component.ts/html/scss — video + angles
  components/
    upload-dialog/                         — mat-dialog: params + file picker
    video-landmarks-player/                — canvas overlay + render loop
    angle-display/                         — computed angles panel
    manual-angle-tool/                     — click-to-measure on canvas
```

### NgRx state shape

```typescript
// Actions (createActionGroup)
source: 'BikeFit'
events:
  Load Analyses / Load Analyses Success / Load Analyses Failure
  Upload Analysis / Upload Analysis Success / Upload Analysis Failure
  Analysis Status Updated: { id, status: 'DONE' | 'FAILED' }
  Load Analysis Detail / Load Analysis Detail Success / Load Analysis Detail Failure
  Clear Detail

// State
interface BikeFitState {
  analyses: BikeFitAnalysisSummary[];
  listLoading: boolean;
  listError: string | null;
  uploading: boolean;
  uploadError: string | null;
  detail: BikeFitAnalysisDetail | null;
  detailLoading: boolean;
  detailError: string | null;
}
```

`Upload Analysis Success` prepends the new summary to `analyses` optimistically. `Analysis Status Updated` finds-and-replaces the entry in the list by id.

**SSE effect** (use `mergeMap` — multiple analyses can process simultaneously):
```typescript
ofType(BikeFitActions.uploadAnalysisSuccess),
mergeMap(({ analysis }) => {
  if (analysis.status !== 'PROCESSING') return EMPTY;
  return new Observable(subscriber => {
    const es = new EventSource(`/api/bike-fit/analyses/${analysis.id}/events`);
    es.onmessage = event => {
      const data = JSON.parse(event.data);
      subscriber.next(BikeFitActions.analysisStatusUpdated({ id: analysis.id!, status: data.status }));
      es.close(); subscriber.complete();
    };
    es.onerror = () => { es.close(); subscriber.complete(); };
    return () => es.close();
  });
})
```

### Routes — add to `app.routes.ts` (inside shell children)

```typescript
{
  path: 'bike-fit',
  providers: [provideState(BIKE_FIT_FEATURE_KEY, bikeFitReducer), provideEffects(BikeFitEffects)],
  loadComponent: () => import('./features/bike-fit/bike-fit-list/bike-fit-list.component')
    .then(m => m.BikeFitListComponent),
},
{
  path: 'bike-fit/:id',
  providers: [provideState(BIKE_FIT_FEATURE_KEY, bikeFitReducer), provideEffects(BikeFitEffects)],
  loadComponent: () => import('./features/bike-fit/bike-fit-detail/bike-fit-detail.component')
    .then(m => m.BikeFitDetailComponent),
},
```

### Shell nav — add to `shell.component.html`

```html
<a mat-list-item routerLink="/bike-fit" routerLinkActive="active-link">
  <mat-icon matListItemIcon>straighten</mat-icon>
  <span matListItemTitle>Bike Fit</span>
</a>
```

### Video player + canvas overlay

Port directly from `/Users/cedricbue/git/innuvation/tri-sport-pose-analysis/frontend`:
- Canvas `position:absolute; inset:0; width:100%; height:100%; object-fit:contain` over the `<video>`
- On `loadedmetadata`: `canvas.width = video.videoWidth; canvas.height = video.videoHeight`
- Video src = `/api/bike-fit/analyses/{id}/video` (served with Range support → seeking works)
- `requestAnimationFrame` render loop restarted on `play` event
- Frame lookup by binary search on `frames_landmarks[].ts`
- Connection arrays: `MEDIAPIPE_CONNECTIONS`, `COCO_CONNECTIONS`, `HALPE_CONNECTIONS` (copy from prototype)
- Skip landmarks with visibility score < 0.3

### Bike-fit angles (validated by road-cycling-coach)

Compute per frame in the `video-landmarks-player`. Angles 1–3 are shown at BDC/TDC only; 4–6 are averaged across the full stroke.

| # | Name | Vertex | Arm 1 | Arm 2 | Position | Range |
|---|------|--------|-------|-------|----------|-------|
| 1 | Knee Extension | knee | hip | ankle | BDC (knee.y max) | 140°–150° |
| 2 | Knee Flexion | knee | hip | ankle | TDC (knee.y min) | 65°–75° |
| 3 | Hip Angle | hip | shoulder | knee | TDC | 45°–60° |
| 4 | Torso Angle | hip | shoulder | `{x:hip.x+1, y:hip.y}` | full stroke avg | 40°–50° |
| 5 | Elbow Angle | elbow | shoulder | wrist | full stroke avg | 150°–165° |
| 6 | Ankle Angle | ankle | knee | foot-index* | BDC | 90°–110° |

*MediaPipe only (kp 31/32); skip for COCO, flag as n/a in UI.

**MediaPipe indices (near-side):** knee=25, hip=23, ankle=27, shoulder=11, elbow=13, wrist=15, foot=31
**COCO indices (near-side):** knee=13, hip=11, ankle=15, shoulder=5, elbow=7, wrist=9 (no foot)

**BDC/TDC detection:** smooth `knee.y` with a 7-frame rolling mean → local maxima = BDC, local minima = TDC. Average across strokes; report std-dev as confidence.

**Angle formula:**
```typescript
function angleDeg(p1, vertex, p2): number {
  const v1 = { x: p1.x - vertex.x, y: p1.y - vertex.y };
  const v2 = { x: p2.x - vertex.x, y: p2.y - vertex.y };
  const dot = v1.x * v2.x + v1.y * v2.y;
  return Math.acos(dot / (Math.hypot(v1.x, v1.y) * Math.hypot(v2.x, v2.y))) * 180 / Math.PI;
}
```

Color-code values in the UI: green = in range, amber = ±5° outside, red = >5° outside.

**2D accuracy caveat (show in UI tooltip):** ±3–5° on knee angles, ±5–8° on torso/hip. Camera must be perpendicular to bike axis at pedal axle height.

### Manual angle measurement tool

Mode toggle (`measureMode = signal(false)`). When active, canvas cursor = `crosshair`.

Three-click sequence: p1 → vertex → p2. Coordinate conversion:
```typescript
const normX = (event.clientX - rect.left) / rect.width;
const normY = (event.clientY - rect.top) / rect.height;
```
Store measurements in component as `AngleMeasurement[]`. Render in yellow (#FFD600, 3px) after skeleton draw. Persist across frames. "Clear all" button in the angle panel.

---

## Verification

1. **Backend unit tests** — `BikeFitServiceTest` (MockK): mock `LandmarksApiClient` and `SseEmitterRegistry`; verify UUID generated, file written, status transitions
2. **Integration test** — extend `AbstractApplicationIntegrationTest`: POST multipart with a small test video; stub landmarks API with WireMock returning a minimal `LandmarksReport`; assert DB row status = DONE and `landmarks_json` is valid JSON
3. **Manual E2E**:
   - Start landmarks API on :8002, backend on :8080, frontend on :4200
   - Upload a 10–30 s side-view video; confirm status transitions PROCESSING → DONE in the list
   - Open detail page; confirm video plays, skeleton renders over cyclist
   - Confirm all 6 angle panels populate (or show n/a for ankle on COCO)
   - Step through frames with ← → keys; angles update per frame
   - Enable manual measure tool; click three points; confirm angle displayed in yellow
4. **Video seeking** — seek to 70% of video without prior buffering; confirms Range request support is working
