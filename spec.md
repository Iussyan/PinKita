# Implementation Specification: Offline-First AR Pedestrian Navigation with On-Device Landmark Recognition

**File:** spec.md  
**Specification version:** 1.0  
**Prepared:** 2026-10-09  
**Status:** Implementation baseline for a hackathon prototype  
**Official product name:** PinKita (Tagline: "Real Places. Closer.")  
**Platform:** Android first  
**Theme:** Local AI  
**Cost objective:** No paid cloud inference, mapping APIs, routing APIs, or required hosted backend

## 1. Purpose and reading guide

Build an Android pedestrian navigation application that searches destinations, calculates routes, renders an offline map, provides short-range AR guidance, and recognizes selected landmarks using a neural network running on the phone.

The product is intended for downloadable public areas such as city districts, tourist areas, parks, and transport surroundings. It is **not confined to a campus**. The hackathon release demonstrates one fully prepared public region. Adding regions must be a data preparation task rather than rewriting navigation code.

This document defines the product requirements, component boundaries, data schemas, algorithms, integration rules, acceptance criteria, and implementation sequence. It is suitable as the primary instruction file for a developer or coding agent. It does not claim an application has already been built or tested.

**Requirement language:**

- **MUST:** required to call the prototype complete.
- **SHOULD:** expected unless a documented implementation constraint prevents it.
- **MAY:** optional improvement.
- **P0:** mandatory hackathon capability.
- **P1:** improvement after P0 passes.
- **P2:** future work.

All numeric thresholds below are **initial engineering defaults**, not measured facts about the team's device or region. Recognition thresholds in particular MUST be replaced by validation results before an honest demonstration.

Read sections 2–6 for scope; 7–15 for implementation contracts; 16–21 for integration and quality; 22–25 for delivery. Section 26 lists unresolved inputs. Section 27 lists official sources checked while preparing this specification.

## 2. Product outcome and theme alignment

### 2.1 Problem

People navigating unfamiliar public areas may have limited connectivity, confusing street layouts, or difficulty identifying the correct destination from a map alone. An offline route can guide them toward an area; local visual recognition can help them identify the supported landmark they are looking at.

### 2.2 Core experience

1. Install the APK and import a prepared region pack.
2. Search for a destination in the region's local place database.
3. Use a current outdoor location fix or a clearly labeled manually selected start.
4. Calculate a pedestrian route locally.
5. Follow the route in the 2D map.
6. Switch to AR mode where device support and alignment quality permit.
7. Point the camera at a supported landmark.
8. Run the embedding neural network on the phone and compare its output to stored reference embeddings.
9. Show a stable landmark identity when evidence passes validation thresholds.
10. Treat physical arrival at the selected entrance separately from recognizing the building.

### 2.3 What qualifies as Local AI

The required ML operation is **camera image embedding inference on the phone**, followed by local landmark retrieval. The system MUST remain capable of this operation with Wi-Fi and mobile data disabled after installation and data preparation.

GPS, coordinate conversion, map drawing, and geometric progress checks are not ML inference. A* is a classical search algorithm commonly discussed in AI; it is not learned visual intelligence and MUST NOT be presented as the project's meaningful on-device ML component.

A prerecorded result, hard-coded landmark label, camera overlay without inference, or cloud model hidden behind an API does not satisfy this specification.

### 2.4 Boundaries of the claims

The MVP recognizes **selected enrolled landmarks within installed packs**, not every building in a city. A general image embedder may fail to distinguish similar façades; validation is a release gate.

Image similarity does not produce a landmark's pixel boundary, an entrance location, distance, camera pose, or an exact GPS correction. Landmark identity, navigation position, and AR alignment are separate outputs.

The MVP provides approximate AR guidance. It does not promise lane-level or sidewalk-level global localization, obstacle avoidance, safe crossing decisions, or accessibility certification.

## 3. Scope and priorities

| Capability | Priority | Required behavior |
|---|---|---|
| Offline pack import | P0 | Import a validated local archive; no hosting required |
| Installed region selection | P0 | List packs, bounds, versions, data dates, and supported landmarks |
| Offline map | P0 | Local PMTiles, local style, required glyphs and sprites |
| Place search | P0 | Search names, aliases, and categories locally |
| Local routing | P0 | A* over an audited pedestrian graph |
| Live location | P0 | Foreground outdoor GNSS updates with freshness and accuracy handling |
| Map navigation | P0 | Route, position uncertainty, next maneuver, remaining distance |
| AR capability detection | P0 | Supported devices enter AR; others retain map navigation |
| AR alignment | P0 | Explicit initialization, quality state, and recalibration |
| AR guidance | P0 | Short-range arrows or a ribbon; directional overlay when quality is poor |
| On-device inference | P0 | Real camera frames processed by a packaged embedding model |
| Landmark matching | P0 | GPS candidate filtering, unknown rejection, temporal stability |
| Destination status | P0 | Recognition and proximity reported separately |
| Rerouting | P0 | Sustained deviation with reliable location triggers a local route calculation |
| Offline proof | P0 | Cold-start test after preparation with network unavailable |
| About and attribution | P0 | OSM attribution and dependency/model/data notices |
| Diagnostics and replay | P0 | Clearly labeled debug metrics and deterministic fixtures |
| HTTP pack download | P1 | Optional static catalogue and resumable download |
| Extra public regions | P1 | Same pack contract; no code changes |
| Haptics or device TTS | P1 | Optional guidance; selected voice must work offline |
| Depth occlusion | P1 | Device-dependent rendering improvement |
| Reference-image feature matching | P2 | Separate geometric pipeline for visual pose estimation |
| Scene segmentation | P2 | Optional scene context; not a safe-route authority |
| Indoor/floor navigation | P2 | Separate map and localization design |
| Cloud VPS or Cloud Anchors | Excluded | Not a dependency of the offline prototype |
| Full-country coverage | Deferred | Product direction, not a hackathon completion condition |

**Demo dataset target:** one public district, five distinct landmarks, at least ten reviewed start-to-destination routes, and representative unknown scenes. The product architecture MUST not hard-code the district or its landmarks. The team MAY reduce the number of landmarks after measuring recognition; document the actual supported set.

**Region limit for the first implementation:** a graph small enough to load into memory on the target phone; aim initially below 100,000 nodes and 300,000 directed edges. These are development budgets, not scalability claims. Measure and shrink the region or change indexing if memory exceeds section 19.

## 4. Technology and dependency policy

| Concern | Selected technology | Integration rule |
|---|---|---|
| IDE and language | Android Studio and Kotlin | Gradle wrapper and version catalogue committed |
| UI | Jetpack Compose and Material 3 | MVVM; navigation state owned outside composables |
| AR tracking | ARCore local session | Geospatial and Cloud Anchor modes disabled |
| AR rendering | SceneView with Filament | Isolate library-specific types behind an adapter |
| Map | MapLibre Native Android | Compose AndroidView wrapper; local sources |
| Tiles | PMTiles vector archive | Copy/import to an actual readable local file |
| Basemap data | OpenStreetMap-derived tiles | Respect tile schema and attribution |
| Places and graph persistence | SQLite; Room where suitable | Pack data immutable during navigation |
| User settings | DataStore | Selected region, diagnostics preference, settings |
| Routing | Kotlin A* | No routing service calls |
| Location | Android LocationManager GNSS adapter | Fused provider MAY be added with equivalent offline behavior |
| Heading | Android rotation vector and geomagnetic correction | World direction must correspond to rear-camera forward |
| Vision | MediaPipe Tasks Image Embedder | Compatible packaged model; CPU baseline |
| Model candidate | Official MobileNetV3 image embedding model | Smoke-test small and large; choose by measured retrieval |
| Pack tooling | Python, pyosmium/Osmium, PMTiles CLI | Deterministic build stages; pinned tool versions |
| GIS review | QGIS | Inspect entrances, graph topology, and regional bounds |
| Basemap generation | Protomaps basemaps/Planetiler | Required only if not extracting an existing permitted archive |
| Testing | JUnit, Android instrumentation, Python tests | Fixtures for routing, transforms, matching, packs |

### 4.1 Version policy

Do not use dynamic versions such as latest.release, plus signs, or unpinned Git branches.

Before building product features, record a successful compatibility matrix in docs/dependencies.md: Kotlin, JDK, Android Gradle Plugin, Gradle, Compose BOM, SceneView, ARCore, MapLibre, MediaPipe, Room, minSdk, compileSdk, targetSdk, CPU ABI, and device OS.

**Initial minSdk target:** API 26. Raise it if the selected dependency versions require it; do not downgrade dependency requirements to preserve this arbitrary initial target. Select compileSdk and targetSdk supported by the chosen stable toolchain. Validate the actual phone, not only an emulator.

MapLibre's official Android documentation reports PMTiles support starting at 11.7.0 and documents local file sources. Select a stable release supporting this feature. PMTiles sources are not downloaded by MapLibre's normal offline-pack system; the app manages archive transfer itself. [R1]

MediaPipe's Android Image Embedder uses tasks-vision and accepts a task-compatible trained model. Package the model rather than fetching it at inference time. A standalone classifier output is not automatically a useful retrieval embedding. [R2, R3]

### 4.2 Cost policy

No paid service is required for the architecture. Development hardware, initial internet access, data collection, and storage remain real resource requirements. APK sideloading is the demo distribution route.

Software being open source does not establish that all model weights, photographs, fonts, map derivatives, or hosted endpoints have identical rights. Keep an asset licence manifest and notices. Do not make blanket licence promises.

## 5. User flows and UI contract

### 5.1 Screens

| Screen | Contents and actions |
|---|---|
| First launch | Explain offline preparation; import pack; show installed AR service requirement |
| Regions | Installed packs, bounds preview, version/date, recognition coverage, import/delete |
| Map and search | Search box, place list, current location, visible OSM attribution |
| Place details | Name/category, entrance choice, supported visual recognition indicator |
| Route preview | Start, destination entrance, distance, estimated walking time, warnings, Start |
| Navigation map | Route, uncertainty circle, next maneuver, remaining distance, AR button |
| AR calibration | Tracking status, position age/accuracy, heading quality, optional alignment control |
| AR navigation | Camera, short-range route, maneuver, destination direction, landmark status |
| Landmark scan | Camera capture and recognition when AR is unavailable |
| Arrival | Near entrance, visual identity result if available, user-confirmed completion |
| Settings/About | Licences, model and pack versions, clear local data, diagnostic export |

All user-facing text MUST distinguish **No matching landmark**, **Possible match**, **Landmark identified**, and **Near destination**.

Do not label cosine similarity as a percentage confidence. A developer view MAY show “Similarity 0.84”; a public view SHOULD show a plain status backed by a calibrated decision rule.

If the phone cannot enter AR, the user can still navigate and use the separate CameraX landmark scanner. When scanning without AR, show directions as a screen overlay only; do not imply world-anchored geometry.

### 5.2 Journey and failure behavior

- Empty search: show locally available examples, not a web geocoder.
- Outside installed bounds: explain that this region is unavailable.
- No routable entrance: show details, disable Start, and report missing routing data.
- No GNSS fix: allow map planning with an explicitly selected start; live progress is unavailable.
- Approximate location permission: map browsing remains usable; precise AR alignment and automatic arrival are disabled.
- Camera denied: map navigation remains usable.
- Unsupported ARCore: open map mode and offer the landmark scanner.
- Low recognition quality: keep navigating; show unknown rather than inventing identity.
- Near a landmark but away from its entrance: show “Landmark identified” and continue route guidance.
- Tracking loss: hide world-anchored route immediately; show status and map fallback.
- Backgrounding: pause camera, inference, sensors, and active guidance; resume through quality checks.
- Pack update available: do not replace an active navigation pack mid-session.

### 5.3 UX constraints

Use large maneuver controls and readable text outdoors. Distance and time are estimates. Avoid continuous scanning instructions while crossing a road. Show a brief “Stop somewhere safe to scan” prompt when launching the scanner.

The camera MUST not open before the user chooses AR or Scan. No login is needed. No developer terms such as embeddings, ENU, or quaternions belong in normal navigation flows.

## 6. Architecture and ownership

### 6.1 Dependency direction

UI depends on application controllers and domain interfaces. Domain routing, matching, and coordinate utilities MUST not depend on Android UI classes.

Data adapters implement domain contracts. The AR adapter owns the ARCore session and scene lifecycle. The ML adapter owns model resources and worker threads. The PackRepository owns installation and version pinning.

~~~mermaid
flowchart TD
    UI["Compose interface"] --> NC["Navigation controller"]
    UI --> VC["Vision controller"]
    NC --> RT["Local route engine"]
    NC --> LOC["Location and alignment"]
    RT --> PACK["Installed region data"]
    VC --> ML["On-device image embedder"]
    VC --> PACK
    LOC --> AR["ARCore and scene adapter"]
    NC --> AR
    AR --> VC
~~~

### 6.2 Suggested repository

~~~text
project/
  app/
    src/main/java/<package>/
      ui/
      navigation/
      location/
      ar/
      vision/
      packs/
      data/
      diagnostics/
    src/main/assets/
      models/
      bootstrap/
  core/
    geo/
    routing/
    recognition/
    contracts/
  tools/
    packs/
      extract_osm.py
      build_graph.py
      build_places.py
      build_embeddings.py
      validate_pack.py
      package_region.py
  fixtures/
    tiny_graph/
    routes/
    recognition/
    coordinates/
  docs/
    dependencies.md
    data_licences.md
    device_results.md
    demo.md
  spec.md
~~~

For a short hackathon, these MAY be directories in a single Android module rather than separate Gradle modules. Keep the logical boundaries.

### 6.3 State ownership

NavigationController owns one active navigation session. VisionController publishes observations but cannot move the user or end the route. AlignmentController publishes a transform and quality state; it cannot change graph connectivity. PackRepository exposes immutable installed snapshots.

Use StateFlow for state and coroutine cancellation for long-running search/routing. ViewModels outlive UI recomposition. Render nodes MUST not be rebuilt on every recomposition.

Every asynchronous operation carries navigationSessionId, routeId or revision, packVersion, and capture timestamp where relevant. Drop late results from old sessions. Update navigation state atomically.

## 7. Offline region pack contract

### 7.1 Directory contents

Distribute a ZIP containing a root manifest and the following files:

~~~text
manifest.json
map/
  map.pmtiles
  style.template.json
  sprites/
    sprite.json
    sprite.png
    sprite@2x.json
    sprite@2x.png
  glyphs/
    <fontstack>/<range>.pbf
data/
  region.sqlite
  buildings.geojson
ai/
  model.tflite
  model.json
  embeddings.f32
  embedding_index.json
  thresholds.json
licences/
  NOTICE.txt
  assets.json
~~~

The model MAY be app-bundled and shared by packs. If shared, the manifest references its model ID and hash, and install MUST fail or mark AI unavailable when that exact model is absent. For the first release, putting the selected model in each pack is acceptable.

Reference images are not required on the phone. Keep them in the preparation dataset with provenance and split information. Include thumbnails only if licences permit.

### 7.2 Manifest example

Values below illustrate structure, not actual pack sizes or verified coordinates.

~~~json
{
  "schema_version": 1,
  "region_id": "demo-public-district",
  "display_name": "Demo Public District",
  "pack_version": "1.0.0",
  "built_at": "2026-10-09T00:00:00Z",
  "source_data_date": "2026-10-01",
  "min_app_pack_api": 1,
  "bounds": {
    "west": 120.96,
    "south": 14.57,
    "east": 120.99,
    "north": 14.60
  },
  "routing_profile": "pedestrian-v1",
  "tile_schema": "protomaps-profile-pinned-at-build",
  "supported_languages": ["en"],
  "coverage": {
    "landmark_count": 5,
    "boundary_policy": "routes_must_remain_inside_installed_graph"
  },
  "ai": {
    "model_id": "selected-image-embedder-v1",
    "model_path": "ai/model.tflite",
    "model_sha256": "<replace-with-real-64-character-hash>",
    "embedding_dimension": 0,
    "preprocess_id": "rear-camera-center-crop-v1",
    "embedding_format": "float32-little-endian-l2",
    "calibration_id": "demo-validation-v1"
  },
  "files": [
    {
      "path": "map/map.pmtiles",
      "size_bytes": 0,
      "sha256": "<replace-with-real-64-character-hash>"
    }
  ],
  "attributions": [
    {
      "label": "OpenStreetMap contributors",
      "url": "https://www.openstreetmap.org/copyright"
    }
  ]
}
~~~

The builder MUST generate real sizes, dimension, and hashes and inventory every required file except the manifest itself. Hashing the manifest inside itself is intentionally avoided. The installer MUST reject placeholders, zero-length required files, nonpositive embedding dimensions, unsupported schemas, invalid bounds, and missing files.

Use a routing extraction buffer around the desired demo area; the advertised navigation bounds MUST be inside graph coverage. Record graph coverage separately if different. Tile coverage alone does not establish routing or recognition coverage.

### 7.3 Install transaction

1. Select archive through Android's Storage Access Framework or obtain a P1 download.
2. Check available space for the archive, extraction, and retained previous version.
3. Extract to a staging directory in app-private storage.
4. Reject absolute paths, parent traversal, symlinks, duplicate paths, excessive file counts, and decompression beyond configured limits.
5. Parse and validate the manifest and compatibility.
6. Verify all required file hashes and sizes.
7. Validate database integrity, foreign keys, graph invariants, style resource inventory, model compatibility, and embedding index ranges.
8. Run a small model smoke test if AI is included.
9. Atomically move to packs/<region_id>/<pack_version>/.
10. Commit the installed version pointer only after successful validation.
11. Retain the last valid version until the new version is usable.

A failed install MUST not corrupt the existing pack. Active sessions retain their selected pack version through a reference count or session lease. Deleting an active pack is blocked until navigation ends.

Hash checks detect corruption; they do not authenticate a malicious producer. P0 imports are user-selected trusted team packs. P1 remote delivery SHOULD use HTTPS plus signed manifests or an equally explicit trust policy.

### 7.4 Local map resources

Generate absolute local URIs at runtime from a style template. The PMTiles source uses a form such as pmtiles://file:///absolute/app/path/map.pmtiles. Do not keep the raw SAF content URI as the source; import it to a local readable file.

All style glyph and sprite references MUST resolve locally. The tile schema's source-layer names MUST match the style. Include required glyph ranges for actual place names and UI languages. No hosted font URL, satellite URL, remote terrain source, or icon CDN is allowed in the offline style.

PMTiles is a tile archive, not a pedestrian graph or search database. Both must be prepared separately. Do not reconstruct reliable routing topology from simplified display vector tiles.

## 8. Local database schema

Use one immutable region.sqlite per pack and a separate mutable app database for installed metadata, settings references, and user favourites. Never write user data into the hash-verified pack database.

All geographic positions use WGS84 latitude/longitude degrees. GeoJSON uses longitude, latitude ordering. Distances use metres. Costs use seconds. IDs are stable strings at API boundaries; dense integer indices MAY be used internally.

### 8.1 Required tables

~~~sql
CREATE TABLE metadata (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE nodes (
  node_id INTEGER PRIMARY KEY,
  lat REAL NOT NULL CHECK(lat BETWEEN -90 AND 90),
  lon REAL NOT NULL CHECK(lon BETWEEN -180 AND 180),
  layer INTEGER NOT NULL DEFAULT 0,
  component_id INTEGER NOT NULL,
  barrier TEXT
);

CREATE TABLE edges (
  edge_id INTEGER PRIMARY KEY,
  from_node INTEGER NOT NULL REFERENCES nodes(node_id),
  to_node INTEGER NOT NULL REFERENCES nodes(node_id),
  osm_way_id TEXT,
  length_m REAL NOT NULL CHECK(length_m > 0),
  cost_s REAL NOT NULL CHECK(cost_s > 0),
  geometry_json TEXT NOT NULL,
  path_class TEXT NOT NULL,
  crossing_kind TEXT,
  access_status TEXT NOT NULL,
  stairs INTEGER NOT NULL DEFAULT 0,
  layer INTEGER NOT NULL DEFAULT 0,
  reviewed INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX edges_from_idx ON edges(from_node);
CREATE INDEX edges_to_idx ON edges(to_node);

CREATE TABLE places (
  place_id TEXT PRIMARY KEY,
  osm_ref TEXT,
  name TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  category TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  building_id TEXT,
  recognition_supported INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE place_aliases (
  place_id TEXT NOT NULL REFERENCES places(place_id),
  alias TEXT NOT NULL,
  normalized_alias TEXT NOT NULL,
  PRIMARY KEY(place_id, normalized_alias)
);

CREATE TABLE entrances (
  entrance_id TEXT PRIMARY KEY,
  place_id TEXT NOT NULL REFERENCES places(place_id),
  name TEXT,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  graph_node_id INTEGER NOT NULL REFERENCES nodes(node_id),
  access_status TEXT NOT NULL,
  reviewed INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE landmarks (
  landmark_id TEXT PRIMARY KEY,
  place_id TEXT NOT NULL REFERENCES places(place_id),
  display_name TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  recognition_radius_m REAL NOT NULL,
  model_id TEXT NOT NULL,
  reference_count INTEGER NOT NULL
);
~~~

Enable foreign keys during build and validation. Validate edge geometry starts/ends against endpoint node coordinates within the builder's recorded tolerance. Check finite values, directed reverse-edge policy, connected components, reachable entrances, and stable identifiers.

Add FTS5 for place search if supported by the selected SQLite/Room configuration, with a tested fallback to indexed normalized text search. Use parameterized queries. Escape wildcard characters for literal LIKE search; never concatenate a search string into SQL.

Room integration with a prebuilt database MUST match its expected schema and identity requirements. Alternatively, use a read-only SQLite adapter for region.sqlite and Room for mutable app state. Decide in the dependency spike; do not assume an arbitrary SQLite file is automatically a valid Room database.

### 8.2 Search behavior

Normalize case, Unicode, whitespace, and common punctuation; preserve original display names. Rank exact normalized names, then prefix matches, then token/alias matches. Nearby distance is a tie-breaker when location is available.

Return stable place IDs and entrances. A destination without a verified routable entrance is searchable but not navigable. Do not silently route to a building centroid.

## 9. Region preparation pipeline

### 9.1 Select and audit a public region

Choose a real walking area accessible to the team. Audit five visually distinct POIs, entrances, crossings, pedestrian access, and known barriers. Record dates and manual corrections.

District-scale support does not mean unreviewed OSM data is a verified safe walking route. The demo MUST use physically reviewed routes. Closed gates, construction, opening hours, and missing sidewalks can invalidate map assumptions.

### 9.2 Obtain separate display and routing data

**Basemap option A:** extract an area from a permitted Protomaps PMTiles build using the PMTiles CLI. Check the current official build location and retention policy; do not hard-code an ephemeral archive URL into the app. [R9]

**Basemap option B:** obtain an OSM PBF and build tiles using a pinned Protomaps/Planetiler profile. This step is a tile-generation pipeline, not merely renaming or directly converting raw OSM into PMTiles. [R10]

**Routing and POIs:** obtain raw OSM data covering the area plus a boundary buffer, for example an available Geofabrik extract. Use Osmium or pyosmium to preserve node/way topology. QGIS is for inspection and manual review; it is not a complete routing graph builder. [R11, R12]

Do not bulk-download tile.openstreetmap.org. Its tile policy prohibits offline prefetching, and its public raster tiles are not the source for this architecture. [R8]

### 9.3 Reproducible commands

The project MUST implement these team-owned commands. These are proposed CLI contracts for tools the team will write, not claims that these scripts already exist.

~~~bash
python tools/packs/extract_osm.py --input sources/region.osm.pbf --bounds config/demo_bounds.json --buffer-m 500 --output build/demo/buffered.osm.pbf
python tools/packs/build_graph.py --input build/demo/buffered.osm.pbf --profile config/pedestrian-v1.json --overrides data/demo_overrides.json --output build/demo/data/region.sqlite
python tools/packs/build_places.py --input build/demo/buffered.osm.pbf --database build/demo/data/region.sqlite --entrances data/reviewed_entrances.json
python tools/packs/build_embeddings.py --dataset data/landmark_dataset --model models/selected_model.tflite --preprocess config/preprocess.json --output build/demo/ai
python tools/packs/validate_pack.py --directory build/demo --report build/demo_validation.json
python tools/packs/package_region.py --directory build/demo --output dist/demo-public-district-1.0.0.zip
~~~

For obtaining basemap tiles, document the actual upstream CLI version and command in the pack build record. All scripts MUST return nonzero on invalid input, write useful error reports, and avoid marking failed outputs as valid.

Build records include OSM snapshot, tile build/profile, source hashes, manual override revision, model hash, preprocessing ID, threshold calibration ID, tool versions, and output checksums. Identical inputs SHOULD produce identical database/embedding bytes; normalize timestamps when checking reproducibility.

## 10. Pedestrian graph construction

### 10.1 Access profile

Implement one documented pedestrian profile:

- Prefer mapped footways, pedestrian ways, permitted paths, and reviewed walkable connections.
- Exclude motorway/trunk carriageways and explicit foot=no.
- Exclude access=no/private unless a valid foot-specific permission overrides general access or a documented team review establishes permitted access.
- Treat foot=designated/yes/permissive explicitly; permissive access should carry a warning.
- Include residential/service street segments only under a documented policy and local review. highway=residential does not prove a sidewalk exists.
- Preserve stairs and mark them in instructions.
- Interpret pedestrian one-way restrictions such as oneway:foot. Vehicle one-way alone does not always imply pedestrian one-way.
- Preserve bridge/tunnel/layer separation; do not connect projected crossings unless shared topology or a reviewed pedestrian connector permits it.
- Respect barriers/gates and conditional access when supported. Unsupported conditional restrictions MUST produce a validation warning or conservative exclusion.
- Do not advertise wheelchair routing in P0. It requires a separate reviewed profile and data completeness standard.

### 10.2 Topology

Keep OSM node identity. Create graph nodes at intersections, endpoints, entrances, barriers, and relevant instruction points. Collapse intermediate degree-two geometry into edge polylines while preserving length.

Shared OSM nodes establish graph intersections unless layer/access logic prevents traversal. Crossing line shapes alone do not establish connectivity. Manual connectors MUST have provenance, access review, and geometry.

Create directed edges for each permitted traversal. Reverse geometries for reverse edges. Isolated components are allowed but must be flagged. No route may cross between disconnected components using an invented straight segment.

### 10.3 Edge costs

Initial profile:

~~~text
walk_speed_mps = 1.3
base_cost_s = length_m / walk_speed_mps
cost_s = base_cost_s + crossing_penalty_s + stairs_penalty_s + review_defined_penalty_s
~~~

All added penalties are nonnegative. The shortest route by cost may be longer by distance. ETA is estimated from these costs; it is not dynamic traffic or crowd prediction.

Compute lengths from complete geographic edge polylines with a consistent geodesic utility. Do not use Web Mercator map pixel distance as ground distance.

### 10.4 Start snapping

Project a reliable start fix onto candidate walkable edge geometries, not only the nearest node. Initial maximum snap distance: 30 m with acceptable location accuracy.

Create a virtual start node and partial directed edges consistent with the underlying edge direction and proportional costs. Preserve actual connectors and path geometry in the result.

When two nearby parallel paths are plausible, use route continuity, heading if reliable, component reachability, and map context; show uncertainty when unresolved. Do not silently snap across a wall, river, grade separation, or divided road.

A small verified off-network access connector MAY join an entrance to the graph. It must be explicitly represented and reviewed, not automatically inferred from nearest Euclidean distance.

## 11. Routing and route progress

### 11.1 A* contract

Input: immutable graph snapshot, routable start position, selected entrance, pedestrian profile, cancellation token.

Output: Success(RoutePlan), NoPath, OutsideCoverage, StartNotRoutable, DestinationNotRoutable, Cancelled, or InvalidGraph.

Use a min-priority queue, gScore, predecessor map, and stale-entry rejection. Update a predecessor only on a lower cost. Stop when the goal is popped with its best known cost. Search runs off the UI thread.

Initial heuristic:

~~~text
h(node) = straight_line_geographic_distance(node, goal) / walk_speed_mps
f(node) = gScore(node) + h(node)
~~~

This is valid only while every edge cost is at least its geodesic endpoint distance divided by that same speed. If the profile changes, prove a lower bound or use h=0. Do not multiply the heuristic arbitrarily and still claim an optimal A* result.

### 11.2 Route result

A RoutePlan contains:

- routeId, navigationSessionId, graphVersion, packVersion, profileId;
- chosen entrance and start snap information;
- ordered node and edge IDs;
- full geographic polyline and cumulative distance array;
- total distance and cost;
- maneuver list;
- stairs/crossing/access warnings;
- creation timestamp and actual live/manual start source.

Build instructions from edge transitions and shape bearings. Initial turn classification: below 25 degrees = continue, 25–60 = slight turn, 60–135 = turn, above 135 = sharp/U-turn depending on topology. Review these against real routes. Crossing and entrance instructions override purely angular labels.

### 11.3 Progress

Use closest segment projection and cumulative distance. Limit candidate segments around the last credible progress and account for loops, hairpins, and parallel segments. Allow legitimate backtracking after repeated evidence; do not force monotonic progress when the user turns around.

Map matching MUST not conceal a sustained deviation. Display the raw fix and uncertainty in diagnostics. Use matched location for route progress only when quality and geometry support it.

Turn advance: initial default is within 8 m of the maneuver and credible progress onto the outgoing segment. If accuracy is poorer than that distance, use a larger quality-aware window and avoid pretending the turn is exact.

### 11.4 Deviation and rerouting

Initial rule:

~~~text
eligible = fresh_fix AND horizontal_accuracy_m <= 15
off_route = cross_track_distance_m > max(20, 1.5 * horizontal_accuracy_m)
reroute = off_route for 3 consecutive eligible updates spanning at least 5 seconds
~~~

Use a 10 s reroute cooldown. Cancel an older calculation when a newer valid reroute supersedes it. Retain the last route until replacement succeeds; NoPath must be visible.

AR-estimated relative motion alone MUST not trigger global rerouting unless alignment is explicitly validated. GPS jumps and stale data cannot trigger arrival or route changes.

### 11.5 Arrival

Initial eligibility: a fresh location with horizontal accuracy <= 10 m, within 15 m of the selected entrance for three updates spanning at least 5 s. Show “Near destination” rather than claiming exact entrance verification. The user can confirm completion.

A stable visual match for the selected landmark allows “Landmark identified” in addition. It does not establish arrival at the entrance. Matching another landmark must not complete the route.

If quality is insufficient, offer manual completion; do not end navigation from a weak GNSS fix or a single high similarity frame.

## 12. Location, heading, and alignment quality

### 12.1 Foreground location

Implement a LocationProvider adapter using GNSS-capable Android location APIs. Request approximately 1 Hz during active navigation; stop when navigation stops or app leaves the foreground in P0.

Store latitude, longitude, altitude when available, horizontal accuracy, bearing and bearing accuracy when available, speed, elapsed-realtime timestamp, and provider. Use monotonic time for freshness.

Initial thresholds:

| State | Rule | Allowed behavior |
|---|---|---|
| Good | Accuracy <= 10 m; age <= 5 s | Candidate for AR initialization and arrival checks |
| Usable | Accuracy <= 25 m; age <= 10 s | Map navigation; approximate directional overlay |
| Poor | Worse accuracy or older data | Planning and manual start; no precise progress claims |
| Missing | No valid fix | Browse and plan; live guidance unavailable |

Reported accuracy is an estimate, not a guaranteed error bound. Display uncertainty and measure real field behavior. Airplane mode may still permit GNSS on the selected device, but the team MUST test it outdoors. A cold fix can take time without assistance.

### 12.2 Heading

Use rotation-vector orientation where suitable and translate device coordinates to the **rear camera's viewing direction**. The phone's top edge and camera forward vector are not interchangeable when held upright.

Correct magnetic heading to true north with Android's geomagnetic declination calculation using location, available altitude, and time. Remap axes for display rotation and portrait/landscape changes.

Use a circular filter, not arithmetic averaging across 359 and 1 degrees. Monitor sensor accuracy and heading instability. A game rotation vector alone is not an absolute north reference.

When compass measurements are unstable, use an explicit manual alignment workflow at a known position/straight route segment. Do not continuously rotate the anchored route from every compass reading.

### 12.3 Quality state

AlignmentState is one of Uninitialized, Initializing, Approximate, Usable, Degraded, Lost.

Its payload includes GNSS quality, heading quality, AR tracking state, plane confidence, initialization time, session ID, transform revision, and the origin used.

This is a **quality heuristic**, not a fully solved sensor-fusion estimator. Do not claim sub-metre localization from combining sensor names in a diagram. A later EKF or pose-graph implementation requires an explicit measurement model and validation.

## 13. Geographic-to-AR coordinate implementation

### 13.1 Coordinate frames

Define:

- G: WGS84 geographic coordinates;
- E: local East/North/Up coordinates in metres;
- W: the current ARCore world frame in metres;
- C: ARCore camera pose frame.

ARCore's world origin and horizontal axes are arbitrary for each session. Local tracking does not know geographic north or the user's global position.

Use double precision for geographic calculations; convert to floats only for nearby renderer coordinates. Establish a new local geographic origin when resetting alignment or moving beyond the local linear approximation's intended range.

### 13.2 Local conversion

For a small neighborhood near origin latitude phi0 and longitude lambda0:

~~~text
E = R * cos(phi0) * (lambda - lambda0)
N = R * (phi - phi0)
U = height - height0
R = 6378137 metres
~~~

Angles are radians. This approximation is the MVP option for short local AR windows; keep origin-to-rendered-point distance below 1 km and test error against a geodesic reference.

For broader transformations use WGS84 ECEF followed by ENU rotation. City-wide route planning remains geographic; never convert the entire city into one giant floating-point AR scene.

The spherical radius above is an approximation, not an exact ellipsoidal solution.

### 13.3 Initial horizontal alignment

At calibration, obtain:

1. A stable geographic origin from accepted location fixes or a user-selected known position.
2. Current AR camera world position cW.
3. A normalized horizontal projection fW of the AR camera forward vector.
4. True-north camera heading theta.
5. A usable ground plane or an explicitly approximate ground-height setting.

For a right-handed world with Y up:

~~~text
rW = normalize(cross(fW, upW))
eastW  = sin(theta) * fW + cos(theta) * rW
northW = cos(theta) * fW - sin(theta) * rW
worldPoint = originW + E * eastW + N * northW + U * upW
~~~

This definition maps east/north correctly when the camera faces north, east, south, or west. Validate it rather than adjusting signs visually.

For a manually aligned route segment, theta comes from that segment's known bearing and the user's explicit aiming action. This is an approximation, especially on curved or ambiguous segments.

The geographic origin's horizontal point corresponds to the camera's accepted horizontal position. Set originW's Y from a selected ground plane beneath that position. If no ground plane is available, do not claim ground contact; use the fallback in section 14.

### 13.4 Heading error and GPS error

Even a visually stable world anchor can be globally misplaced. A 10-degree heading error offsets a point 20 m away by about 3.5 m. GNSS error adds further displacement. The app MUST show approximate guidance and offer recalibration.

Never regenerate the full transform from raw GPS every render frame. Keep an accepted transform during a short interval. Compare credible subsequent GNSS observations to the transformed AR camera path. On sustained discrepancy, degrade or request recalibration rather than snapping the scene silently.

Do not “correct” location to the landmark's centroid after recognizing it. The camera can see a building from many positions and distances.

### 13.5 Coordinate fixtures

Automated tests MUST cover:

- east/north displacements at a known origin;
- headings 0, 90, 180, and 270 degrees;
- longitude wrap and latitude validity;
- portrait/landscape axis changes;
- camera pitch with horizontal projection;
- inverse ENU transform round trip;
- geographic and rendering units;
- transform revision/session changes;
- expected relative positions after rotating the phone without translating.

## 14. AR session and rendering

### 14.1 Availability

Configure AR as **optional** because map navigation and non-AR recognition are useful. Check support and handle Google Play Services for AR installation/update before the offline demo. First-time setup is not guaranteed offline. [R4, R5]

Do not add a Geospatial API key or cloud project as a runtime requirement. Geospatial relies on hosted functionality and requires internet configuration, so it is outside this offline baseline. [R6]

### 14.2 Render behavior

P0 render mode A: anchored short-range arrows or ribbon when tracking and alignment are usable.

P0 render mode B: camera HUD with approximate maneuver direction when global alignment or ground plane is uncertain. Clearly label it approximate; this is a fallback, not successful world placement.

Initial anchored window: render points about 2–6 m ahead, spaced around 1.5 m. Keep the maximum visible distance configurable. Do not draw a kilometre-long route or thousands of anchors.

When a usable ground plane exists, place arrows just above it, around 0.05 m. The local plane is not a global terrain model; stairs and hills require recalibration or map guidance.

### 14.3 Anchors and continuity

Use a small set of local anchors and pooled scene nodes. Keep geometry near its anchor. ARCore recommends avoiding objects more than about 8 m from an anchor and releasing unused anchors. [R7]

The first implementation SHOULD use a short route window around a current local anchor. As the user advances, replace the window and detach obsolete anchors. Reanchoring improves local render stability; it does not solve geographic drift.

If a renderer combines raw world coordinates and anchored child coordinates, the adapter MUST explicitly convert between them. Do not apply the ENU transform twice.

### 14.4 Route geometry

Interpolate route samples by cumulative metre distance. Generate arrow orientation from the local path tangent; asset forward-axis convention must be documented. Avoid allocating new mesh buffers every frame.

For a ribbon, use a narrow approximate corridor around the path centre line and cap sharp mitres. A ribbon is visual guidance, not a physics collider or proof that the ground is walkable.

Destination display: show a label and approximate bearing/distance. If beyond the short render window, use a screen-direction indicator rather than a faraway ground anchor. Recognition can update the label state; it cannot make the marker pixel-aligned without extra localization.

### 14.5 Tracking loss

On PAUSED or STOPPED tracking, hide world route nodes immediately and pause alignment-dependent output. Keep maneuver text and offer map mode. After recovery, confirm session/origin consistency and recalibrate when uncertain.

An AR session restart invalidates previous transforms and anchors. Never serialize ARCore world coordinates and assume they remain valid on the next launch.

Depth and occlusion MAY be added after P0. Do not require ARCore Depth hardware for the MVP.

## 15. On-device landmark recognition

### 15.1 Model selection gate

Start with an official MediaPipe-compatible MobileNetV3 embedding model. The task overview lists small/large options; these are general image embeddings, not guaranteed landmark classifiers. [R3]

Before building recognition UI, measure whether the selected model distinguishes the actual enrolled landmarks under different angles and lighting. If it fails, change the model, reference collection, or supported set. Do not compensate with false “confidence” labels.

A possible escalation is a compatible retrieval-oriented embedding model or lightweight fine-tuning on permitted data. Any replacement requires input/output verification, licence review, regeneration of references, and new threshold calibration.

### 15.2 Reference collection

Initial target per landmark:

- 12–20 enrolment photos from practical viewing positions, distances, and lighting;
- validation and held-out test images taken in separate sessions;
- partial occlusion examples and visually similar nearby buildings;
- unknown scenes: streets, trees, storefronts, people, vehicles, unrelated buildings.

Avoid using consecutive near-identical frames in both reference and evaluation sets. Split by capture session and viewpoint before augmentation. Remove duplicates. Reference images must represent the camera perspectives expected in the demo.

Store source, author/permission, date, capture session, landmark ID, approximate camera position if recorded, and split. Prefer the team's own lawful photographs and do not include unnecessary faces.

The MVP does not need model training. It does need data collection and evaluation.

### 15.3 Preprocessing contract

Reference and runtime pipelines MUST share:

- exact model hash and output head;
- image orientation and EXIF handling;
- unmirrored rear camera convention;
- center ROI strategy and aspect-preserving crop/resize;
- colour space and channel order;
- model-prescribed normalization;
- L2 normalization policy;
- output dimension and float/quantized representation.

Initial ROI: an on-screen center reticle containing the building; crop consistently before embedding. Document its normalized bounds in model.json. This is not object detection.

Do not independently normalize twice without checking model semantics. Prefer float output and normalized float32 references for the first release. Switching to quantized outputs changes the contract and requires recalibration.

### 15.4 Embedding storage

embeddings.f32 stores row-major float32 little-endian normalized vectors of dimension D.

embedding_index.json example:

~~~json
{
  "schema_version": 1,
  "model_id": "selected-image-embedder-v1",
  "model_sha256": "<actual-model-hash>",
  "preprocess_id": "rear-camera-center-crop-v1",
  "dimension": 1024,
  "vector_count": 2,
  "rows": [
    {"row": 0, "landmark_id": "poi-a", "reference_id": "a-session1-01"},
    {"row": 1, "landmark_id": "poi-b", "reference_id": "b-session1-01"}
  ]
}
~~~

The dimension 1024 is illustrative; derive D from the selected model. Validate file size exactly equals vector_count * D * 4, all rows exist once, vectors are finite, and norms are acceptable. Do not compare embeddings from different models.

### 15.5 Candidate retrieval

When location is fresh and useful, retrieve enrolled landmarks within:

~~~text
radius_m = clamp(max(300, 3 * horizontal_accuracy_m), 300, 1000)
~~~

Include the selected destination only if it is geographically plausible; do not force a distant selected landmark into every search. Record candidate IDs in diagnostics.

When location is unavailable, the user MAY scan against all enrolled landmarks in the selected pack. Mark geographic confirmation unavailable. A match in this mode cannot trigger arrival or position correction.

Candidate filtering reduces work and some confusion, but it can exclude the correct landmark when GNSS is wrong. Keep an unknown state and a “Scan installed landmarks” fallback. Do not force the nearest POI to be the visual result.

### 15.6 Similarity and decision rule

For normalized vectors, cosine similarity is their dot product. Group references by landmark. Initial class score: mean of the top two similarities for that landmark; require at least two usable reference vectors.

Let best be the highest class score and second be the next **different landmark's** score. The margin is best - second, not the difference between two photos of the same building.

A frame qualifies only if:

~~~text
best_score >= calibrated_threshold_for_best_landmark
AND (only_one_candidate OR best_score - second_score >= calibrated_margin)
AND image_quality_is_usable
~~~

A single candidate still needs an absolute threshold and unknown-scene evaluation. Cosine scores are not probabilities and MUST NOT be displayed as “92% confident.”

Image quality checks SHOULD reject severe blur, exposure failure, or an empty reticle. Pick thresholds on validation data; avoid device-specific unmeasured claims about blur filters.

Temporal rule: the same landmark qualifies in at least three of the last five **processed** frames, with those qualifying frames spanning at least 1 second. Initial recognition rate: up to 2 processed frames/s. Expire a result after 3 seconds without continuing usable evidence. Store capture time, not callback time, for this decision.

If two identities remain close, return Ambiguous. If none pass, return Unknown. Do not keep a stale success label when the camera turns away.

### 15.7 Calibration and evaluation

thresholds.json MUST contain model/preprocess hashes, dataset split version, score aggregation rule, per-landmark thresholds, margin, temporal rule, sample counts, and validation metrics. It MUST identify an uncalibrated configuration explicitly.

Initial release goals on held-out data:

- accepted known-match precision >= 95%;
- known-landmark recall >= 80%;
- unknown-scene false acceptance <= 5%;
- median stable identification time <= 3 s during a planned supported view.

These are targets, not promises. Report denominators and false matches per landmark. For small datasets, provide raw counts and do not imply statistical certainty.

Held-out final evaluation MUST happen after thresholds are frozen. Reusing the final test set to tune thresholds requires a new held-out set. Temporal video metrics should be counted by distinct scan attempts/session, not thousands of adjacent frames.

When a goal fails, reduce supported coverage or improve data/model. If no credible visual model works, the navigation app may still be useful, but the Local AI P0 gate has not passed.

### 15.8 Output and privacy

RecognitionObservation contains landmarkId if accepted, status, similarity score for diagnostics, second score/margin, capture timestamp, modelId/hash, packVersion, candidate source, and inference duration.

Only embeddings and decisions are retained transiently. No camera frames are uploaded. Do not persist captured frames by default. An explicit debug capture mode must be visibly enabled and must keep files local with a clear delete action.

Identity recognition MUST not directly mutate the navigation pose, graph, entrance, route, or arrival state.

## 16. Camera integration and resource lifecycle

This is a critical integration constraint: independent ARCore and CameraX sessions must not compete for the same rear camera.

### 16.1 During AR mode

ARCore/SceneView owns the rear camera. Obtain occasional CPU images from the current AR frame using the documented acquireCameraImage API or the renderer's supported frame callback. ARCore returns YUV_420_888 and may temporarily report image unavailability. [R13]

Pipeline:

1. On a current frame callback, check the sampling interval and worker availability.
2. Acquire only that current frame's image.
3. Copy/downsample required planes into an owned buffer, respecting row and pixel strides.
4. Close the Android Image in finally as soon as copying completes.
5. On a worker, rotate/crop/convert using the established preprocessing contract.
6. Run image embedding and local matching.
7. Publish a timestamped observation.
8. Drop old/session-mismatched results; release all temporary resources.

Do not hand a closed Image to asynchronous inference. Do not retain a Frame and call acquireCameraImage later after it is no longer current. Handle NotYetAvailable as a skipped sample, not an application failure.

Use at most one inference operation in flight and a latest-frame/drop policy. Never queue every camera frame.

### 16.2 Without AR

Pause and close the AR camera session before binding CameraX for a standalone landmark scanner. When returning to AR, unbind CameraX before creating/resuming AR.

Use the same preprocessing/model/matcher for both sources. CameraX-specific rotation data and ARCore image display transforms differ; the adapter must standardize the resulting upright ROI.

Shared Camera can be explored later if required, but it is not the P0 integration strategy.

### 16.3 Inference runtime

A dedicated CPU worker is the baseline. GPU delegates MAY be added after profiling; camera rendering and ML may compete for GPU resources, and some delegate APIs have thread affinity.

Use synchronous IMAGE mode on a bounded worker for the initial MVP, or LIVE_STREAM with explicit ownership and lifetime handling. Never call a blocking embed operation on the UI or AR render thread. The MediaPipe guide describes these modes and asynchronous behavior. [R2]

Clean up embedder resources on scanner shutdown, app destruction, model changes, and pack changes. Cancel stale work before releasing model resources.

## 17. Application contracts and state machine

### 17.1 Core interfaces

The following Kotlin-style contracts are design shapes, not copy/paste API declarations from external libraries.

~~~kotlin
interface PackRepository {
    suspend fun importPack(input: PackInput): InstallResult
    fun installedPacks(): Flow<List<InstalledPack>>
    suspend fun openSnapshot(regionId: String, version: String): PackSnapshot
}

interface PlaceRepository {
    suspend fun search(pack: PackSnapshot, text: String, limit: Int): List<Place>
    suspend fun entrances(pack: PackSnapshot, placeId: String): List<Entrance>
}

interface RouteEngine {
    suspend fun calculate(request: RouteRequest): RouteResult
}

interface LocationProvider {
    fun fixes(): Flow<LocationFix>
    fun start()
    fun stop()
}

interface LandmarkRecognizer {
    suspend fun recognize(frame: OwnedCameraFrame, context: RecognitionContext): RecognitionObservation
    fun close()
}

interface ArNavigator {
    fun updateRoute(route: RoutePlan)
    fun updateAlignment(alignment: AlignmentState)
    fun hideAnchoredGuidance()
    fun close()
}
~~~

PackSnapshot is a session lease on immutable data, not a mutable global singleton. OwnedCameraFrame has explicit buffer ownership and timestamp; adapters cannot pass native frame handles across arbitrary threads.

### 17.2 States

| State | Entry condition | Exit condition |
|---|---|---|
| NoPack | No valid pack installed | Import succeeds |
| Ready | Pack selected, no active route | Destination selected |
| RoutePreview | Route or route error produced | Start, revise destination/start, or cancel |
| NavigatingMap | Valid active route | Enter AR, scan, complete, or cancel |
| ArInitializing | Camera permission/support accepted | Usable alignment or fallback |
| NavigatingAr | Tracking and accepted alignment | Degrade, map, scan, complete, cancel |
| ArDegraded | Lost tracking or poor alignment | Recalibrate or map |
| Scanning | Camera owned by chosen source | Accepted/unknown result or close |
| NearDestination | Credible entrance proximity | User completion or leave proximity |
| Completed | User confirms completion | New route |
| Error | Recoverable operation failed | Retry or return to usable screen |

~~~mermaid
stateDiagram-v2
    Ready --> RoutePreview: Select destination
    RoutePreview --> Map: Start
    Map --> ARInit: Enter AR
    ARInit --> AR: Alignment accepted
    ARInit --> Map: Unsupported or cancelled
    AR --> Degraded: Quality lost
    Degraded --> ARInit: Recalibrate
    Degraded --> Map: Use map
    AR --> Map: Switch view
    Map --> NearDestination: Entrance proximity
    AR --> NearDestination: Entrance proximity
    NearDestination --> Completed: User confirms
    NearDestination --> Map: Leave entrance area
    Completed --> Ready: New navigation
~~~

Recognition state is orthogonal to navigation state: Idle, Scanning, Unknown, Ambiguous, Possible, Identified, Expired, Error. Do not create a route completion transition directly from Identified.

### 17.3 Integration invariants

- Exactly one rear-camera owner at a time.
- Every route uses one immutable pack and profile version.
- Every accepted embedding uses the matching model/preprocess contract.
- Every AR transform belongs to one live AR session and revision.
- Stale inference/routing results cannot affect a newer session.
- All offline navigation operations avoid network dependencies.
- MapLibre and AR session lifecycles are explicitly forwarded through the Compose adapter.
- Switching between map and AR does not change destination or route unnecessarily.

## 18. Permissions, privacy, and trust

Request precise foreground location for live navigation, and camera permission when the user chooses AR/Scan. Android may grant only approximate location; detect and handle that state. [R14]

P0 requires no background location, contacts, microphone, broad storage permission, login, or analytics SDK. Use scoped app storage and the system file picker.

INTERNET permission is required only by a build that offers optional downloads. The offline validation build SHOULD omit it if selected dependencies permit, while the app still undergoes device preparation before testing. If dependencies add it through manifest merging, audit and document the final merged manifest and verify no app network use.

ARCore service behavior is partly outside the app's process. Do not claim a network permission check alone proves every vendor service is silent; demonstrate the app's feature behavior under blocked connectivity.

Treat imported packs as structured inputs: enforce size limits, path safety, supported schema/model format, and database validation. Do not execute code contained in a pack.

Logs MUST not contain raw images or precise location histories by default. Debug location recording is opt-in, local, visibly indicated, and deletable. Diagnostic exports require user action.

Show OSM attribution on the map and retain appropriate notices in the pack and About screen. Record licence obligations for derived databases and redistributed assets; the OSM copyright page identifies the database's ODbL licence. [R15]

## 19. Performance and quality budgets

Measure on the actual hackathon phone and record device model, OS, pack size, graph counts, model version, and thermal state. These are initial goals:

| Metric | Initial budget | Measurement |
|---|---:|---|
| Installed-pack cold launch to usable map | <= 5 s | Ten cold starts; median and P95 |
| Search latency | P95 <= 200 ms | At least 100 queries on device |
| Route calculation | P95 <= 1 s | Reviewed route pairs and worst case |
| Render frame rate in AR | Target >= 30 fps | Five-minute AR session |
| Inference input rate | Up to 2 fps | Submitted/processed/dropped counters |
| Embedding and matching latency | P95 <= 300 ms | Measured including preprocessing |
| Stable recognition | Median <= 3 s | Distinct supported scan attempts |
| Process memory | Initial target <= 500 MB | Android profiler; native memory included |
| Pack size | Initial target <= 250 MB | Actual ZIP and installed size |
| Camera session stability | No resource leaks | Twenty map/AR/scanner switches |
| Navigation endurance | 15 min with no crash | Outdoor field test |
| Offline app network requests | Zero during P0 navigation | Code audit and observed test |

Do not infer phone performance from published benchmark timings on another device. If targets fail, reduce visible geometry, sampling rate, model size, region size, or rendering detail. Keep a documented CPU fallback when a GPU delegate fails.

Pause the camera and inference when not visible. Handle memory pressure and thermal warnings by lowering inference rate and switching to map guidance if necessary.

## 20. Verification plan and acceptance criteria

### 20.1 Automated tests

**Graph/routing:**

- A* total cost equals Dijkstra on small fixtures and randomized nonnegative graphs.
- Unreachable destinations return NoPath.
- Directed pedestrian restrictions are respected.
- Bridge/tunnel line crossings remain disconnected without a valid connector.
- Barriers and explicit access exclusions do not become routable.
- Start snapping splits an edge correctly in both permitted directions.
- Entrance routing uses reviewed entrances, not centroids.
- Cancellation returns safely without publishing a stale route.

**Geography/alignment:**

- Metres/radians/degrees conversions are consistent.
- ENU and renderer transform pass section 13 fixtures.
- Cardinal heading tests do not mirror left/right or east/west.
- Position noise does not cause per-frame route jumps.
- Session resets invalidate transforms.

**Recognition:**

- Float bytes round-trip with correct endianness and dimension.
- Zero/NaN vectors are rejected.
- Equal-model embeddings have expected cosine behavior.
- Margin compares different landmark identities.
- Unknown, ambiguous, single-candidate, and expired cases work.
- Temporal rule uses processed-frame capture timestamps.
- Model/preprocess mismatch blocks recognition.
- Old callbacks are ignored.

**Pack/security:**

- Valid import succeeds; interrupted import leaves old pack valid.
- Hash mismatch, path traversal, duplicate path, oversized extraction, malformed database, missing glyphs, missing model, invalid bounds, and bad embedding index fail predictably.
- Active pack update does not alter current session.
- Unsupported pack version produces a clear error.
- Local style references contain no remote dependencies.

### 20.2 Device tests

| ID | Test | Passing result |
|---|---|---|
| D01 | Clean APK install, prepare services and pack | App usable after import; model loaded |
| D02 | Disable Wi-Fi/mobile data; force-stop; cold launch | Map labels/icons, search, route, recognition all work |
| D03 | Start outdoor live route offline | Current GNSS fix used; uncertainty displayed |
| D04 | Deny camera | Map route still usable |
| D05 | Grant approximate location only | Precision-dependent behavior disabled |
| D06 | Unsupported AR device | Map and separate scanner remain usable |
| D07 | Enter AR and rotate through cardinal directions | World direction matches tested transform |
| D08 | Tracking loss and session restart | Anchored route hidden, recalibration required |
| D09 | Switch map/AR/scanner twenty times | No camera contention, leaks, or crash |
| D10 | Point at enrolled POI under supported conditions | Stable correct identification under calibrated rule |
| D11 | Point at similar unrelated façade | Unknown/ambiguous; no false completion |
| D12 | Recognize destination from across street | Identity shown; arrival not falsely asserted |
| D13 | Reach selected entrance with good location | Near-destination state and manual completion |
| D14 | Walk away from route | Sustained valid deviation causes local reroute |
| D15 | Inject a single GPS jump | No immediate reroute/arrival |
| D16 | Import damaged pack | Clear failure; existing pack usable |
| D17 | End route or background app | Camera/inference/sensors stop |
| D18 | Restart after previous AR session | New alignment required; no reused world positions |

### 20.3 Field evaluation

Walk ten reviewed route pairs, including at least one turn, crossing, entrance approach, route deviation, and GNSS-poor segment.

Record map-route validity, incorrect turns, time to route, missed reroutes, AR directional correctness, tracking recovery, recognition failures, and manual intervention.

For AR, separately report:

- screen-direction correctness;
- visible route displacement at a few measured checkpoints;
- heading error if measured;
- frequency of fallback/recalibration.

Stable-looking arrows are not evidence of globally correct placement.

For recognition, record confusion counts on held-out known and unknown scans. Include hard negatives and conditions outside intended support. Write actual results in docs/device_results.md.

### 20.4 Definition of done

The MVP is complete only when:

1. One audited public-region pack installs transactionally.
2. Search and routing are local and valid on the reviewed routes.
3. A prepared phone cold-starts and performs the core flow with connectivity unavailable.
4. Live outdoor location works or its absence is honestly reported.
5. Short-range AR guidance works with explicit approximate alignment and tracking-loss behavior.
6. A real neural network processes live frames on-device.
7. Recognition rejects unknowns and meets the recorded supported-set quality decision.
8. Recognition cannot falsely set an exact user pose or complete a route by itself.
9. Resource lifecycle and switching tests pass.
10. Dependencies, licences, actual measurements, limitations, and demo steps are documented.

## 21. Diagnostics, fixtures, and demo integrity

Create a debug-only diagnostics panel showing:

- pack/version and graph counts;
- location age/accuracy/provider;
- heading/AR quality and transform revision;
- route ID, total/progress/cross-track distances;
- camera source and inference busy state;
- model/preprocess hashes;
- candidate count, accepted identity, similarity/margin;
- processed/dropped frames and inference latency;
- render frame rate and app memory where available.

Replay fixtures MAY use recorded GPS tracks and sample images for deterministic development. The UI MUST visibly label replay/simulation. Never present replayed recognition or a precomputed camera result as live inference.

The live demo can include a separate replay fallback for a venue with no outdoor GNSS or insufficient visual texture. Explain which parts are live and which inputs are replayed.

Diagnostic evidence for local inference includes the packaged model, application logs showing invocation timing, offline success, and source-level absence of cloud inference calls. Do not claim absolute privacy from an animated “offline” badge alone.

## 22. Milestones and implementation sequence

Time estimates depend on team skill, device availability, and existing code. Do not promise a complete city-scale app in a specific number of hours. Use gates that expose difficult integration work early.

### M0 — Compatibility and feasibility spikes

Deliverables:

- Android project builds and installs on the intended phone.
- MapLibre renders a local PMTiles with local labels/icons.
- ARCore/SceneView opens and tracks a simple local node.
- Embedding model runs on a sample image offline.
- AR-owned camera frame feeds the same model without CameraX contention.
- A small real landmark dataset produces measured retrieval results.
- Dependency matrix recorded.

**Gate:** all five technical paths work independently and the landmark approach is credible. If recognition fails, fix data/model before expanding UI.

### M1 — Pack contract and deterministic domain code

Deliverables:

- pack validator/importer;
- tiny graph and sample places;
- search/routing contracts;
- A* and Dijkstra comparison tests;
- geographic transform fixtures;
- matching/temporal unit tests.

**Gate:** fixture pack installs, routes, and matches deterministically.

### M2 — Real public-region pack and offline map journey

Deliverables:

- audited region graph, entrances, places, basemap assets;
- search, route preview, map navigation;
- live foreground location, progress, deviation, arrival-proximity logic;
- first complete network-disabled map test.

**Gate:** reviewed real walking routes work locally.

### M3 — Live recognition

Deliverables:

- enrolled dataset and provenance;
- consistent reference/runtime preprocessing;
- thresholds calibrated;
- CameraX scanner;
- unknown/ambiguous/stable states;
- AR-frame inference bridge.

**Gate:** real live images pass the documented supported-set evaluation, offline.

### M4 — AR navigation integration

Deliverables:

- quality-aware calibration;
- tested ENU-to-world transform;
- short-range arrows/ribbon;
- approximate HUD fallback;
- tracking loss/recovery and resource lifecycle;
- landmark labels integrated without changing user pose.

**Gate:** full map-to-AR-to-recognition flow runs without camera contention.

### M5 — Hardening and demonstration

Deliverables:

- cold-start offline test;
- permissions/error tests;
- field results;
- notices/About;
- release APK and region pack;
- demo instructions and backup replay clearly labeled;
- no secrets or dynamic dependency versions.

**Gate:** section 20 definition of done passes.

### Optional team division

This is a proposed division for the user's team; it does not assume a particular team size.

| Workstream | Responsibility | Interface delivered early |
|---|---|---|
| Maps/data | Tile resources, pack builder, places, audited graph | PackSnapshot and fixture pack |
| Routing/location | A*, search, progress, reroute, entrance handling | RoutePlan and LocationFix |
| AR | Session, calibration, transforms, local rendering | AlignmentState and ArNavigator |
| Vision | Model, dataset, preprocessing, thresholds, camera bridge | RecognitionObservation |
| Integration owner | UI/state, lifecycle, offline proof, packaging | NavigationController |

If fewer people are available, combine maps with routing and combine AR with integration. Freeze contracts early and integrate every milestone rather than waiting for all components to finish.

## 23. Implementation backlog

| ID | Task | Priority | Dependency | Completion evidence |
|---|---|---|---|---|
| B01 | Create reproducible Android build and dependency catalogue | P0 | None | APK installs on target device |
| B02 | Prove local map and style resources | P0 | B01 | Cold-start map without network |
| B03 | Prove AR and AR-frame inference | P0 | B01 | Live sampled frame produces embedding |
| B04 | Build landmark feasibility dataset | P0 | None | Measured known/unknown retrieval |
| B05 | Define contracts and fixtures | P0 | B01 | Deterministic test pack |
| B06 | Implement pack validation/import | P0 | B05 | Corruption/interruption tests |
| B07 | Build audited real graph and POIs | P0 | B05 | Reachable verified entrances |
| B08 | Implement search and place details | P0 | B06, B07 | Offline results |
| B09 | Implement A* and route preview | P0 | B05, B07 | Dijkstra equivalence tests |
| B10 | Add location, progress, reroute | P0 | B09 | Field/replay deviation tests |
| B11 | Build references and calibrate recognition | P0 | B03, B04 | Frozen threshold report |
| B12 | Add non-AR scanner and recognition states | P0 | B11 | Live unknown rejection |
| B13 | Implement alignment transforms | P0 | B03, B10 | Cardinal/geographic fixtures |
| B14 | Render short-range AR guidance | P0 | B13 | Field directional tests |
| B15 | Integrate recognition and navigation | P0 | B12, B14 | No pose/arrival coupling |
| B16 | Harden lifecycle and permissions | P0 | B15 | Switching and denial tests |
| B17 | Complete offline evaluation and notices | P0 | B16 | Release checklist signed off by team |
| B18 | Optional static catalogue/download | P1 | B17 | Resumable validated pack transfer |
| B19 | Add second public-region pack | P1 | B17 | No navigation code changes |

## 24. Instructions for a coding agent

When this file is used as an implementation prompt:

1. Inspect the existing repository and its instructions before modifying it. Do not replace an established project unnecessarily.
2. Treat this as an Android application, not a browser mockup. A web AR simulation does not meet the phone/ARCore runtime requirement.
3. Implement M0 first and report unsupported device/toolchain constraints. Do not begin with a large UI while map/camera/model compatibility is unresolved.
4. Pin actual dependencies after compiling; do not invent version numbers or use pseudocode external APIs.
5. Build one vertical slice using a tiny local fixture before adding a real region.
6. Keep routing, geography, and matching testable without Android UI.
7. Use real on-device inference. Never add a fake landmark service to make the feature appear finished.
8. Keep simulated inputs in clearly labeled debug paths; release behavior uses live data.
9. Do not add paid/cloud services, API keys, remote geocoding, telemetry, or required authentication.
10. Do not claim landmark recognition performs visual positioning or segmentation.
11. Add meaningful tests for routing, transforms, unknown rejection, pack integrity, and lifecycle.
12. Run applicable Gradle/unit/instrumentation checks and document what cannot be tested without the real phone.
13. Produce working milestone artifacts and measured results. A successful build alone is not navigation validation.
14. If model quality fails, surface the failure and improve the supported set/model; do not silently weaken unknown rejection.
15. Keep the product region-independent. Region IDs, names, POIs, and embeddings come from packs.
16. Preserve P0 map/scanner fallback if AR is unavailable.

Suggested first instruction to the agent:

> Implement M0 and the fixture-based M1 vertical slice from spec.md. Establish the Android project, pin compatible dependencies after successful builds, render a fully local PMTiles map, implement and test A* on the fixture graph, prove local image embedding, and prove that an ARCore-owned camera frame can be processed without another camera owner. Record device-dependent checks honestly before expanding to M2.

Do not interpret this suggested prompt as restricting the complete product scope; it is the first implementation checkpoint.

## 25. Release artifacts and demonstration

### 25.1 Required deliverables

- Installable release/demo APK with build commit and dependency versions.
- Validated region ZIP and manifest.
- Reproducible pack tooling and input provenance.
- Licence/notice inventory.
- Recognition calibration and held-out evaluation report.
- Device/field test results.
- Demo instructions with setup and limitations.
- Clearly labeled fixture/replay data for development and backup.
- Source code and this specification.

No server is needed to distribute the hackathon pack: a local ZIP transfer and APK sideload are sufficient. Optional hosting can be added independently.

### 25.2 Live demo sequence

1. State the actual supported region, landmark set, phone, and approximate AR limits.
2. Show the installed pack/model and prior service preparation.
3. Disable Wi-Fi/mobile data; use airplane mode if it preserves GNSS on the test phone.
4. Force-stop and relaunch the app to avoid relying on an already loaded online session.
5. Search for an installed landmark, choose its entrance, and calculate a route.
6. Show map guidance and a credible outdoor location fix.
7. Enter AR, calibrate, and show short-range guidance.
8. Point at a supported landmark; show a stable live on-device identity.
9. Point at an unrelated façade; show unknown/ambiguous behavior.
10. Demonstrate that identity and near-entrance arrival are separate.
11. Show tracking fallback or rerouting if venue/time permits.
12. Close with measured results rather than universal coverage claims.

If presenting indoors, explain that GNSS may be unavailable and use a labeled recorded track for routing while keeping camera ML live where possible. Do not claim an indoor replay proves live outdoor localization.

### 25.3 Honest pitch

> The application packages maps, pedestrian routes, places, and selected landmark references for offline use. A neural network processes camera images directly on the phone to help identify supported landmarks. Route calculation and navigation continue without a cloud inference service. AR guidance uses local tracking with explicit approximate alignment and map fallback.

Adapt the wording to demonstrated features. Do not advertise unimplemented segmentation, universal building recognition, collision-based routing, or precision GPS correction.

## 26. Open inputs and decision log

The defaults in this spec allow work to begin. The team should record these inputs before finalizing a real demo:

| Input | Default assumption | Decision effect |
|---|---|---|
| Hackathon duration/team size | Unknown; milestones instead of fixed hour promises | Scheduling and optional features |
| Actual Android phone | ARCore-supported phone available | AR availability, performance, minSdk |
| Development computer | Can run Android Studio and build tools | Tile-generation option and build speed |
| Demo public region | One audited district | Graph/tiles, POIs, field testing |
| Landmark reference rights | Team-owned or permitted photos | Distributable references and provenance |
| Model quality | Unproven until M0 | Supported set or model replacement |
| Initial pack delivery | Local import ZIP | No required hosting |
| Language support | English UI; data may contain local names | Glyph coverage and normalization |
| GPS/heading conditions | Outdoor testing available | AR quality and fallback |
| Product name | PinKita ("Real Places. Closer.") | Final branding |

Decisions already established:

- This is a hackathon implementation, not a campus-only capstone proposal.
- Android is the first platform.
- Product expansion uses region packs.
- Local vision ML is a required theme component.
- Landmark recognition is identity retrieval, not visual pose localization.
- Arrival targets an entrance, not a building centroid.
- Offline operation includes tiles, style assets, graph, search data, model, and references.
- P0 uses no cloud VPS, cloud inference, or paid navigation APIs.
- The team must measure quality on its actual device and region.

## 27. Official implementation references

Checked on 2026-10-09. These support platform capability statements; the detailed thresholds, schemas, and milestone choices in this document are engineering proposals for this project.

- **R1 — MapLibre Android PMTiles:** local-file protocol, supported release baseline, and PMTiles/offline-pack distinction. https://maplibre.org/maplibre-native/android/examples/data/PMTiles/
- **R2 — MediaPipe Image Embedder Android guide:** dependencies, model setup, runtime modes, result processing. https://developers.google.com/edge/mediapipe/solutions/vision/image_embedder/android
- **R3 — MediaPipe Image Embedder task overview:** model options and embedding behavior. https://developers.google.com/edge/mediapipe/solutions/vision/image_embedder
- **R4 — Enable AR in an Android app:** AR Optional configuration and runtime installation/support handling. https://developers.google.com/ar/develop/java/enable-arcore
- **R5 — ARCore supported devices:** verify the actual demo phone. https://developers.google.com/ar/devices
- **R6 — ARCore Geospatial setup:** internet/cloud infrastructure requirements. https://developers.google.com/ar/develop/java/geospatial/enable
- **R7 — ARCore anchors:** local anchor behavior, distance guidance, and cleanup. https://developers.google.com/ar/develop/anchors
- **R8 — OSM tile usage policy:** restrictions on offline tile prefetch from the public tile service. https://operations.osmfoundation.org/policies/tiles/
- **R9 — Protomaps getting started:** extracting a permitted PMTiles area. https://docs.protomaps.com/guide/getting-started
- **R10 — Protomaps custom basemap build:** generating tiles from OSM data with its build tooling. https://docs.protomaps.com/basemaps/build
- **R11 — Geofabrik Philippine OSM extracts:** raw-data source option. https://download.geofabrik.de/asia/philippines.html
- **R12 — Osmium Tool manual:** OSM extraction and processing. https://osmcode.org/osmium-tool/manual.html
- **R13 — ARCore Frame API:** acquireCameraImage, YUV format, availability, and resource exceptions. https://developers.google.com/ar/reference/java/com/google/ar/core/Frame
- **R14 — Android location permissions:** precise/approximate permission handling. https://developer.android.com/develop/sensors-and-location/location/permissions
- **R15 — OpenStreetMap copyright:** attribution and ODbL information. https://www.openstreetmap.org/copyright
- **R16 — Android position sensors:** orientation and sensor coordinate behavior. https://developer.android.com/develop/sensors-and-location/sensors/sensors_position
- **R17 — SceneView source and documentation:** verify current renderer integration and dependency compatibility. https://github.com/sceneview/sceneview

When a dependency changes, repeat its smoke tests and update the compatibility matrix. Documentation capability alone does not prove the complete application works on the team's phone.

