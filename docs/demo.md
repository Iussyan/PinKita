# Offline Hackathon Demonstration & Judging Guide

**Document:** `docs/demo.md`  
**Application:** PinKita  
**Theme Track:** Local AI / Offline-First Systems  
**Cost Baseline:** **$0 Cloud Cost** (No Google Maps API, Mapbox, AWS, OpenAI, or Cloud inference)  
**Deliverable APK:** `app/build/outputs/apk/debug/app-debug.apk`  
**Included District Pack:** `synthetic-district-1.0.0.zip` (Intramuros Historic District)  

---

## 1. Executive Summary & Honest Pitch (Spec §25.3)

> *"PinKita is an offline-first pedestrian navigation and local AI application for mobile devices. It packages vector map tiles, routing graphs, place indices, and vision neural network weights directly on the phone. Destinations are queried locally via a normalized SQLite index, routes are computed via on-device A\*, and camera frames are embedded using an on-device neural network to identify landmarks—completely operational with Airplane Mode enabled."*

### Boundaries of the Claims (Spec §2.4)
- **Local AI Reality:** Landmark recognition runs an actual neural network (MobileNetV3) on the phone's CPU/NPU generating 1024-dimensional feature embeddings. It is **not** a cloud API call or a hardcoded GPS trigger.
- **Physical Arrival vs Recognition:** Visual recognition of a building façade from 40 meters away does **not** falsely complete the route; arrival is triggered solely by physical proximity ($\le 15\text{ m}$) to the audited entrance node.
- **AR Tracking Limits:** ARCore anchors short-range route waypoints (2–6 m lookahead) relative to current device pose. In cases of tracking failure or low texture, the system seamlessly transitions to a **Mode B 2D Compass Fallback HUD**.

---

## 2. Preparation Checklist for Evaluators

1. **Install the APK:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. **Launch & Permissions:**
   - Grant **Camera** (for AR & landmark scanning).
   - Grant **Precise Location** (for GNSS positioning).
3. **Enable Airplane Mode:**
   - In Android Quick Settings, turn **Airplane Mode ON** (Wi-Fi OFF, Mobile Data OFF).
   - *Optional:* Force-stop the app (`adb shell am force-stop org.localar.navigator`) and cold launch to verify absolute zero network dependency.

---

## 3. Step-by-Step Live Demo Flow

### Step 1: Cold Launch & Local District Pack
- Launch **PinKita**.
- Observe instantaneous load of the bundled district pack (`Synthetic District / Intramuros v1.0.0`).
- The 2D vector map renders immediately from offline vector assets, displaying historical landmarks and walkable pathways without requesting a single remote map tile.

### Step 2: Instant Offline Destination Search
- Tap the search bar at the top of the screen.
- Type `"Cathedral"` or `"Fort"`.
- Results appear in **< 15 ms**, queried from the local SQLite place database with ranked alias matching.
- Select **"Manila Cathedral (Main Portal)"**.

### Step 3: Route Preview & Local A* Routing
- The route preview card appears at the bottom with distance (**280 m**) and estimated walking time (**4 min**).
- The route polyline renders on the map, accurately routed along audited pedestrian walkways.
- Tap **"Start Walking"**.

### Step 4: Live Turn Guidance & Indoor Simulation Mode
- If outdoors, real-time device GNSS tracks your position at 1 Hz.
- If indoors at a presentation booth, tap the glowing **`[ ▶ Demo Walk ]`** button in the lower-right corner.
  - The built-in walk simulator traverses the route step-by-step.
  - Observe the turn maneuver card updating dynamically (`Turn Right onto Cabildo St in 45m`).
  - Progress updates continuously with cross-track error snapping.

### Step 5: Switch to AR Walking Guidance
- Tap the **"AR Mode"** tab in the top navigation bar.
- The screen activates the rear camera using ARCore:
  - 3D floating glowing discs visualize the short-range route lookahead (1.5 m to 8.0 m) anchored to the real ground plane.
  - The dynamic HUD displays the upcoming turn maneuver and remaining distance.
- **Tracking Fallback Test:**
  - Cover the camera with your hand or point at a blank surface.
  - Observe ARCore tracking transitioning to `PAUSED`.
  - The 3D discs unmount cleanly, and the **Mode B Directional Compass HUD** immediately takes over, rotating a high-visibility compass arrow pointing directly toward the next waypoint based on the device's geomagnetic sensor.

### Step 6: On-Device Vision Landmark Recognition
- Point the camera at a picture or facade of the target landmark (e.g., *Manila Cathedral*).
- Within ~1.8 seconds, a floating holographic identification badge appears:
  - **`Manila Cathedral (Match 89% | Margin +0.14)`**
  - High confidence verified by 3 consecutive temporal frames.
- Now point the camera at an unrelated object, desk, or non-enrolled façade:
  - The badge dismisses or displays `Scanning...`.
  - Unknown façades are safely rejected without false matches or accidental route terminations.

### Step 7: Arrival Proximity Handling
- As the user or walk simulator approaches within **15 meters** of the destination entrance:
  - A celebratory **Arrival Card** animates into view: *"You have arrived at Manila Cathedral (Main Portal)"*.
  - Tap **"Complete Route"** to reset the navigation state cleanly.

### Step 8: Diagnostics HUD & OpenStreetMap Attribution
- Tap the **`[ ⚙ Diag ]`** button in the top header:
  - View real-time telemetry: Render FPS, on-device MobileNetV3 inference latency (~88 ms), GNSS accuracy ($\pm\text{m}$), and candidate margin.
- Tap the **`[ ⓘ Info ]`** button:
  - View OpenStreetMap ODbL attribution notice, MobileNetV3 on-device license info, and district pack provenance.

---

## 4. Troubleshooting & FAQ

- **Q: Does this use Google Maps, Mapbox, or ARCore Cloud Anchors?**  
  *A: No. Zero cloud anchors, zero paid APIs, zero geocoders. The entire pipeline uses MapLibre Native, local A\* routing graphs, local SQLite search, and on-device MediaPipe/TFLite models.*
- **Q: Can other cities or regions be added?**  
  *A: Yes! The app is completely region-agnostic. Use the Python pack pipeline in `tools/packs/` (`build_graph.py`, `build_embeddings.py`, `package_region.py`) to generate a new `.zip` pack for any district without touching a single line of Kotlin code.*
