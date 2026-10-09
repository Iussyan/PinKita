# PinKita — Offline Landmark Discovery & Tappable Places

**Implementation guide for a coding agent**  
**Status:** Proposed specification, not an implemented feature  
**Priority:** Hackathon MVP  
**Goal:** Let users find, tap, inspect, save, and navigate to local landmarks and businesses without signing in or requiring the internet after an area pack is installed.

> **Agent instruction:** First inspect the current repository, its platform, map renderer, persistence layer, offline pack format, and navigation implementation. Reuse working infrastructure. Do not replace the existing stack just because this guide names example technologies. If critical context is missing, document the assumption before implementing.

## 1. Product requirements

### Must have

1. Display locally indexed landmarks/points of interest (POIs) as tappable markers on the main PinKita map.
2. Tap any marker to open a bottom sheet with its name, category, optional address/opening hours, data source, and actions: **Directions**, **Save**, and **Details**.
3. Search downloaded POIs by name and category without network connectivity.
4. Show nearby places using device position when location permission exists; otherwise search around map center.
5. Compute and display a route using the existing navigation engine if it supports offline routing. If routing data is missing, clearly explain that a route pack is required—never draw a straight line and claim it is a walking route.
6. Hand the destination coordinate and place ID to the existing AR navigation flow. AR must use existing device tracking/calibration and must not claim precise building alignment without a verified positioning strategy.
7. Support **Saved Places** locally, without registration, and persist across app restarts.
8. Make all of the above work in airplane mode **after relevant assets are installed**.
9. Display proper **OpenStreetMap attribution** and identify source/update information.
10. Preserve PinKita's four-tab normal navigation: **Map / AR / Saved / Settings**. Hide the tab bar during active navigation.

### Should have (post-MVP)

- Categories: cafés, restaurants, shops, hospitals, schools, tourist spots, government buildings.
- Local user-created pins (private to the device by default), edit/delete, and backup/export.
- Downloaded region manager, last-updated timestamps, and update/delete actions.
- Optional online refresh and user-submitted corrections to a moderation/contribution workflow.
- Optional **Open in Google Maps** deep link for user convenience when online; no Google data scraping or bulk copying.

### Explicitly out of scope for MVP

- Google reviews, Google star ratings, Google business photos or Google Places content copied into an offline database.
- Google Places API integration or guaranteed parity with Google's listing coverage.
- Cloud accounts, mandatory login, social sync, business claiming, crowdsourced moderation.
- Guaranteed offline photographs, live open/closed status, or live business updates.
- Production-grade city-scale visual localization/SLAM.

## 2. Recommended technology architecture

| Concern | Recommended implementation | Notes |
|---|---|---|
| Geographic source | OpenStreetMap (OSM) | Data availability varies; not the same as Google listings |
| Initial extraction | Geofabrik `.osm.pbf` regional extract + Osmium/pyosmium, or Overpass for small *development* queries | Prefer downloaded extracts for repeatable production processing |
| Map UI | Existing MapLibre renderer, if present | Keep the current working renderer |
| Local DB | SQLite / Room for Android | Use built-in SQLite FTS5 if supported |
| Map tiles | Existing licensed offline map packs | POI import does not automatically create map tiles |
| Routes | Existing offline routing engine and bundled routing graph | Routing is separate from geocoding, POIs, and map rendering |
| AR | Existing ARCore / camera-navigation layer | Only pass validated coordinates and route data |
| Pack distribution | Prebuilt demo area pack or user-downloaded pack | Respect tile server and data licensing terms |

**Important:** Free open-source software is not the same as unlimited free hosted services. Never bulk-download from the public `tile.openstreetmap.org` server as an offline tile source. Verify the tile provider’s caching, bulk-download, redistribution, and attribution terms before using it.

## 3. Data processing pipeline

```text
Licensed OSM regional extract (.osm.pbf)
             |
        POI extractor
             |
   Normalize tags and names
             |
 Deduplicate / geometry centroids
             |
  Validate coordinates and fields
             |
    SQLite POI database + FTS
             |
    Versioned offline area pack
             |
 Download / bundle into PinKita
             |
 Local map markers + searches
             |
  Place sheet -> Save / Route / AR
```

### 3.1 Region and scope

- For the hackathon, choose **one small demonstrable geographic area** (e.g., the team's selected local area). Do not hardcode Tagaytay/Taal merely because mockups use it.
- Define a bounding box or polygon with authoritative latitude/longitude values.
- Extract POIs inside the precise intended boundary; clip or filter the larger regional OSM extract.
- Define a pack ID such as `demo-area-v1`, version, dataset snapshot date, source URL, required map asset versions, and required routing graph version.
- Bundle a small area pack with the demo app when feasible. If it must be downloaded, show an explicit **download-first** step, progress, errors, and storage estimate.

### 3.2 OSM tags to support initially

| OSM tag | Example values | PinKita category |
|---|---|---|
| `amenity` | `cafe`, `restaurant`, `fast_food` | Food & Drink |
| `amenity` | `hospital`, `clinic`, `pharmacy` | Health |
| `amenity` | `school`, `university`, `college` | Education |
| `amenity` | `bank`, `atm`, `police`, `townhall` | Services |
| `shop` | any useful mapped shop type | Shopping |
| `tourism` | `attraction`, `museum`, `viewpoint`, `hotel` | Attractions / Accommodation |
| `historic` | `monument`, `memorial`, etc. | Heritage |
| `leisure` | `park`, `playground`, etc. | Recreation |
| `building` + `name` | named building if otherwise relevant | Landmark |

Store both **normalized category** and original OSM tags. Do not invent missing hours, phone numbers, ratings, reviews, or addresses.

### 3.3 Geometry and identity

- Support OSM **nodes, ways, and relations**. Do not silently drop all ways/relations.
- For nodes, use node coordinates. For ways and multipolygon relations, compute an appropriate point-on-surface (preferred for usable map pins) or centroid as a fallback, using a geometry-aware library.
- Record stable provenance identity as `(source_type, source_id)` such as `(osm_way, 12345)` rather than trusting the display name as a unique ID.
- Keep source metadata and extract date; never claim real-time accuracy.
- Handle multiple OSM elements for the same establishment with a reviewable deduplication rule, not name-only auto-merge.
- Normalize strings for search while preserving original display names, diacritics, and multilingual labels when available.

## 4. Local database contract

Recommended SQLite schema (adjust to the existing repository conventions):

```sql
CREATE TABLE IF NOT EXISTS places (
    id TEXT PRIMARY KEY,
    source_type TEXT NOT NULL, -- osm_node | osm_way | osm_relation | local
    source_id TEXT,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    subcategory TEXT,
    latitude REAL NOT NULL CHECK(latitude BETWEEN -90 AND 90),
    longitude REAL NOT NULL CHECK(longitude BETWEEN -180 AND 180),
    address TEXT,
    opening_hours TEXT,
    phone TEXT,
    website TEXT,
    source_updated_at TEXT,
    area_pack_id TEXT,
    raw_tags_json TEXT,
    UNIQUE(source_type, source_id)
);

CREATE INDEX IF NOT EXISTS idx_places_category ON places(category);
CREATE INDEX IF NOT EXISTS idx_places_area ON places(area_pack_id);

-- Optional bounding-box acceleration; populate and synchronize via your data importer.
CREATE VIRTUAL TABLE IF NOT EXISTS place_bounds USING rtree(
    row_id, min_lon, max_lon, min_lat, max_lat
);

-- FTS5 availability must be verified on target Android SQLite build.
CREATE VIRTUAL TABLE IF NOT EXISTS place_search USING fts5(
    place_id UNINDEXED,
    name,
    category,
    address,
    tokenize='unicode61 remove_diacritics 2'
);

CREATE TABLE IF NOT EXISTS saved_places (
    place_id TEXT PRIMARY KEY,
    saved_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS area_packs (
    pack_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    version INTEGER NOT NULL,
    source_snapshot_date TEXT,
    installed_at TEXT NOT NULL,
    status TEXT NOT NULL, -- installing | ready | failed
    bounds_json TEXT NOT NULL
);
```

**Implementation caution:** The R-tree uses numeric SQLite `rowid`, while `places.id` is text; the importer must map to `places.rowid`. Populate `place_search` with inserts during import or maintain it using triggers. Do not ship nonfunctional schema-only FTS/R-tree tables. Use transactions for inserts and pack updates.

**Saved places and deleted packs:** Avoid losing saved places silently. Either preserve lightweight saved-place snapshots when removing area packs, or prompt users and document the removal behavior. User-created places should not be deleted with third-party area packs.

## 5. Repository interfaces / contracts

Use equivalent interfaces in the actual app language:

```kotlin
data class Place(
    val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val openingHours: String?,
    val sourceType: String
)

interface PlaceRepository {
    suspend fun search(query: String, categories: Set<String>, limit: Int = 30): List<Place>
    suspend fun getById(id: String): Place?
    suspend fun inBounds(west: Double, south: Double, east: Double, north: Double, limit: Int = 300): List<Place>
    suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Double, limit: Int = 50): List<Place>
    suspend fun save(placeId: String)
    suspend fun unsave(placeId: String)
    suspend fun getSaved(): List<Place>
}

interface NavigationGateway {
    suspend fun calculateWalkingRoute(destination: Place): RouteResult
    suspend fun startArNavigation(placeId: String, routeId: String): NavigationResult
}
```

`RouteResult` and `NavigationResult` should be sealed success/failure results, distinguishing **no map pack**, **no routing pack**, **unsupported AR**, **no GPS permission**, and **no viable route**.

## 6. UI requirements

### 6.1 Map screen

- Keep the existing PinKita branding: dark teal/forest green, cream, muted gray, amber selected pins.
- Keep only four persistent tabs on regular screens: `Map`, `AR`, `Saved`, `Settings`. No `Home` or required `Profile` tab.
- Render POI pins from the local repository for the current viewport. Use clustering or map symbol layers to prevent lag at dense zoom levels.
- Distinguish user location, selected destination, and standard POI markers.
- When a pin is tapped, select its `place.id` and open a bottom sheet. Tapping another pin updates the same sheet. Tapping an empty map area may dismiss it.
- Show `Place name`, `Category`, any **available** address, and source/update details. Hide missing fields gracefully.
- Actions: `Directions`, `Start AR` (only when a compatible route/AR flow is available), `Save/Unsave`, `More info`.
- Keep OSM attribution visible or accessible in a clearly compliant form.

### 6.2 Search

- Search locally with FTS5 where available, falling back to indexed prefix/LIKE search for unsupported builds.
- Debounce typing (about 200–300 ms), limit results, and cancel stale work.
- Rank exact names first; then prefix/partial matches; optionally consider nearby distance.
- Offer category chips and an explicit empty state: `No matching places in your downloaded area.`
- Never make an online request in the offline search code path.

### 6.3 Place details

```text
┌───────────────────────────────┐
│ Café Example                  │
│ Food & Drink • Local OSM data │
│ Address (only if available)   │
│ Updated: YYYY-MM-DD           │
│                               │
│ [Directions] [Save] [Details] │
└───────────────────────────────┘
```

- Photos: do not assume OSM supplies reliable, licensed business photos. Show a category illustration/icon unless an image is independently licensed and available offline.
- Hours: show provided text without asserting `Open now` unless the app can parse and evaluate time zones/exception rules correctly.
- Ratings: don't fabricate or represent these as Google ratings.

### 6.4 Routing and AR handoff

1. User selects a POI.
2. `Directions` requests a walking route from the local routing engine.
3. If route exists, show route geometry, distance, and **computed** ETA (no invented values).
4. `Start AR` opens AR guidance using that same route and destination. Keep map/AR route state consistent.
5. If GPS/heading confidence is poor, show calibration guidance and a 2D-map fallback. Do not position AR arrows as exact street-level guidance unless alignment is trustworthy.
6. While actively navigating, hide the four-tab bar; provide `Map view`, `Mute`, and `Exit AR` controls.

## 7. Pack lifecycle and offline reliability

1. Download or bundle the map tiles, POI SQLite database, and routing graph **as separate versioned assets** that refer to the same geographic boundary.
2. Validate checksums and manifest compatibility before setting a pack to `ready`.
3. Store downloads in app-private storage unless the user explicitly chooses another location.
4. Use an atomic install: stage downloads -> verify -> install -> activate. On failure retain the previous valid pack.
5. Provide download size, remaining device storage warning, cancel/retry, and delete options.
6. For a first-run device with no available local map, show `Download a local area when connected` rather than falsely claiming full offline coverage.
7. When network is absent, **never** block opening an already downloaded map, accessing a place, searching, or using a locally available route.
8. Record failures locally for debugging; avoid sharing precise user location without explicit consent.

## 8. Google Maps interoperability (optional)

- If the user wants Google Maps, provide an **Open in Google Maps** action that opens a normal external Google Maps search/coordinate URL, e.g. `https://www.google.com/maps/search/?api=1&query=<encoded-lat>,<encoded-lon>`.
- This should be an optional external handoff, not an app dependency, and may require connectivity or another app.
- Do **not** scrape Google Maps pages, copy ratings/reviews/photos, or persist Google Places responses as a permanent substitute for OSM data.
- Avoid showing a Google mark on OSM-derived information or implying Google endorsement.

## 9. Licensing and source policies

- OpenStreetMap data is licensed under the ODbL. Show meaningful attribution and link to license information: https://www.openstreetmap.org/copyright.
- Evaluate ODbL share-alike and data distribution obligations if distributing a derived dataset. Track data sources and licenses in an app `About / Map data` screen and in the pack manifest.
- Public Overpass instances are shared query services; use restrained development queries. **Do not** design a large-scale extraction or runtime lookup dependency around free public servers.
- Public Nominatim is not a source for bulk POI downloading and must not power client-side search-as-you-type. Use local FTS instead.
- OSM standard map tile servers have separate usage restrictions. Use a legal offline tile package or an explicitly offline-permitting provider; do not mass-download OSM-hosted tiles.
- Validate rights for any third-party photos, fonts, icons, or other bundled assets.

## 10. Security and data quality

- Validate all imported JSON/OSM tags; escape user-controlled strings; use parameterized SQL.
- Limit text lengths and result counts. Guard against corrupt or oversized area packs.
- Sanitize website links and phone URLs before opening externally.
- Keep local custom pins private unless the user explicitly exports/shares them.
- Resolve stale or missing OSM attributes gracefully. No fabricated addresses, operating hours, review scores, or landmarks.
- Use a predictable sort and stable keys to prevent flickering markers on pan/zoom.

## 11. Acceptance tests

### Functional

- [ ] A prepackaged/demo area loads with no login.
- [ ] POI markers appear for actual local DB records.
- [ ] Tapping two different markers opens two correct place sheets.
- [ ] Marker selection stays stable during zoom/pan.
- [ ] Searching by name/category returns expected matches from local DB.
- [ ] Search handles empty input, accents, and zero matches.
- [ ] Saved place persists after a full app restart.
- [ ] Valid local destination opens route preview with actual computed route.
- [ ] Missing route pack produces a clear, non-crashing state.
- [ ] AR handoff receives the same destination and route.
- [ ] Active navigation hides the main 4-tab bar.
- [ ] Standard UI uses exactly `Map / AR / Saved / Settings`.

### Offline / resilience

- [ ] Enable airplane mode and force-close/reopen app: map, search, markers, details, and saved places still work.
- [ ] Where route graph is installed, routing works in airplane mode.
- [ ] Corrupt/incomplete pack is rejected and previous valid pack is retained.
- [ ] Download can be interrupted and retried without duplicate broken records.
- [ ] The UI explains missing coverage instead of blank screens.
- [ ] No Google Maps or cloud POI calls are required for core operations.

### Quality

- [ ] POI queries and markers are limited to viewport/nearby bounds; no full-table query on every camera frame.
- [ ] A dense viewport uses clustering or efficient style layers.
- [ ] Legal attribution is present and readable.
- [ ] On-screen accessibility labels exist for tappable pins and controls.
- [ ] All visible distances/ETAs are calculated, not placeholders, in production code.

## 12. Suggested implementation order

### Phase A — Repository inspection and minimal vertical slice

1. Locate existing map screen, map abstraction, routing service, AR navigation entry point, assets, and storage.
2. Define `Place` model + repository contract.
3. Import 15–50 real OSM-based POIs for the selected demo area into SQLite.
4. Render real POI markers and implement on-tap bottom sheet.
5. Demonstrate the above in airplane mode.

**Exit criterion:** Tap a real local map pin and view the correct locally sourced information offline.

### Phase B — Discovery and saved places

1. Add local FTS/indexed search and categories.
2. Add viewport/nearby queries.
3. Add Save/Unsave and the Saved tab.
4. Add graceful empty and missing-data states.

**Exit criterion:** Search, select, and save multiple real places offline.

### Phase C — Navigation integration

1. Connect place selection to existing route calculation.
2. Show realistic route line and distance/ETA from the route engine.
3. Integrate AR handoff with route ID/destination ID.
4. Keep active navigation layout distinct and provide a map fallback.

**Exit criterion:** Pick a place, route to it, then transition to existing AR guidance with consistent destination metadata.

### Phase D — Area packs and polish

1. Create repeatable OSM-to-POI import script.
2. Package maps + POIs + routing metadata with manifest/checksum.
3. Add pack install, update, deletion, attribution, and documentation.
4. Run airplane-mode, performance, and corruption tests.

**Exit criterion:** Another developer can install the app, get a prepared area pack, and reproduce a reliable offline demonstration.

## 13. Agent deliverables

The implementation agent should produce:

- Working code that follows the repository's framework and structure.
- A repeatable `tools/` or `scripts/` POI import pipeline, with reproducible command examples.
- Database schema/migrations + seeded demo data with provenance.
- Map markers, local search, place sheet, and Saved tab integration.
- Route/AR connection where the required infrastructure already exists; otherwise an explicit integration interface and documented blocker.
- Tests covering search, selection, offline behavior, and data provenance.
- Documentation: local setup, pack generation/import, attribution, data caveats, limitations, and next development steps.
- A short `IMPLEMENTATION_REPORT.md` detailing what was completed, what is mocked, what is blocked, and what was tested on a real device.

## 14. Source documentation

- OpenStreetMap data and attribution: https://www.openstreetmap.org/copyright
- Geofabrik Philippines extracts: https://download.geofabrik.de/asia/philippines.html
- MapLibre Android offline SDK: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/index.html
- MapLibre Compose offline packs: https://maplibre.org/maplibre-compose/offline/
- MapLibre Android local PMTiles documentation: https://github.com/maplibre/maplibre-native/blob/main/platform/android/docs/data/PMTiles.md
- Overpass API: https://wiki.openstreetmap.org/wiki/Overpass_API
- Nominatim usage policy: https://operations.osmfoundation.org/policies/nominatim/
- OSM tile policy: https://operations.osmfoundation.org/policies/tiles/
- Google Maps URLs: https://developers.google.com/maps/documentation/urls/guide

---

**Guiding principle:** PinKita can offer a Google Maps-like *interaction*, but it must not pretend to have Google Maps' proprietary listing database. Every core feature should depend on licensed, locally available data and degrade honestly when coverage is missing.
