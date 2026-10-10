# PinKita (Pedestrian AR Navigator)

> **Real Places. Closer.**  
> An offline-first augmented reality pedestrian navigation app powered by on-device Local AI.

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%201.9.23-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-blue.svg)](https://developer.android.com/jetpack/compose)
[![AI](https://img.shields.io/badge/Local%20AI-Google%20MediaPipe%20Vision-orange.svg)](https://developers.google.com/mediapipe)
[![Offline](https://img.shields.io/badge/Network-100%25%20Offline%20First-success.svg)](https://github.com/Iussyan/AppBuildersPH)

---

## 📖 Overview

**PinKita** is an offline-first pedestrian navigation app designed for environments where traditional navigation tools fail: urban canyons, dense street markets, indoor facilities, basements, and campuses with weak or zero mobile connectivity.

### The Problem
- **Map-to-Reality Disorientation:** 2D blue dots and flat top-down maps leave pedestrians confused about which direction they are facing and which building is their actual destination.
- **Cellular Dead Zones:** Mainstream navigation apps crash or freeze when mobile data drops.
- **Privacy & Latency:** Cloud-based vision solutions require streaming high-definition camera feeds over the internet, causing multi-second latency, high cellular data consumption, and serious privacy concerns.

### How PinKita Solves It
1. **Pin on 2D Map:** Search or pin any destination using high-performance local offline maps.
2. **Switch to AR View:** With a single tap or step, PinKita seamlessly switches to live camera AR mode.
3. **Follow Real-World Waypoints:** AR directional arrows and guidance cards lead you step-by-step along verified pedestrian walkways and sidewalks.
4. **Local AI Destination Locking:** An on-device vision model (Google MediaPipe Image Embedder) identifies and visually highlights the target building in real time with sub-40ms latency and **zero internet connection**.

---

## ✨ Key Features

- **🚶 Pedestrian-First Routing (A* Graph Engine):** Custom routing graph prioritizing walkways, footpaths, zebra crossings, and stairs over vehicular highways.
- **🤖 On-Device Vision AI (Zero Cloud):** Runs MobileNetV3 image embeddings locally to recognize landmarks and destinations using cosine similarity against pre-calibrated embedding banks.
- **📱 Real-Time Camera AR:** Google ARCore local 6DoF tracking and SceneView ground-plane projections without requiring cloud anchors.
- **🗺️ MapLibre Offline Maps:** Vector and raster map rendering backed by local SQLite databases.
- **📦 Google Maps-Style Offline Area Downloader:** Pan and zoom an interactive rectangular selector to download and compile any OpenStreetMap region into an offline SQLite package on device.
- **🔒 100% Private & Battery Efficient:** Camera frames are processed strictly in local RAM and never uploaded.

---

## 🛠️ Tech Stack & Architecture

| Component | Technology | Role |
|---|---|---|
| **Language & Runtime** | Kotlin 1.9.23, JDK 17 | Core application logic and coroutines |
| **User Interface** | Jetpack Compose (Material 3) | Declarative UI, state-driven screens, warm teal/forest design system |
| **Local Vision AI** | Google MediaPipe Tasks Vision (`0.10.14`) | On-device MobileNetV3 image embedding extraction |
| **Augmented Reality** | Google ARCore (`1.43.0`) + SceneView (`2.2.1`) | 6DoF camera tracking, spatial anchors, ground-plane rendering |
| **Offline Mapping** | MapLibre Native Android SDK (`11.7.0`) | High-performance offline map rendering |
| **Routing & Database** | Android SQLite + Custom A* Engine | Dijkstra/A* pathfinding, spatial indices, POI search |
| **Camera Fallback** | Android CameraX (`1.3.4`) | High-efficiency camera frame analysis for non-AR scanning |

---

## 🚀 How to Build & Run (Instructions for Judges)

### Prerequisites

1. **Java Development Kit (JDK):** JDK 17 installed (e.g., Eclipse Temurin 17 or OpenJDK 17). Ensure `JAVA_HOME` points to your JDK 17 installation.
2. **Android SDK:**
   - Android SDK Platform `android-34`
   - Android SDK Build-Tools `34.0.0`
3. **Hardware / Device:**
   - Physical Android device running Android 8.0+ (API 26 or higher).
   - An ARCore-compatible device is recommended for AR mode (or use the built-in 2D Map & Camera Scanner modes).

---

### Step 1: Clone the Repository

```bash
git clone https://github.com/Iussyan/AppBuildersPH.git
cd AppBuildersPH
```

---

### Step 2: Compile the Debug APK

Run the Gradle build command:

#### On Windows (PowerShell / Command Prompt):
```powershell
.\gradlew.bat assembleDebug
```

#### On macOS / Linux:
```bash
./gradlew assembleDebug
```

The build will compile all modules (`:core` and `:app`) and generate the debug APK at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

### Step 3: Install onto Device

Connect your Android phone via USB (with USB Debugging enabled) or ADB over Wi-Fi, then run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in **Android Studio** (Hedgehog / Iguana / Jellyfish) and click **Run 'app'** (`Shift + F10`).

---

## 🧭 Testing the App Experience

1. **Landing Screen:**
   - Tap **"Explore Map"** to enter the 2D map directly, or tap **"Download Local Maps"** to test the Google Maps-style offline area downloader.
2. **2D Map Mode:**
   - Search for a local landmark (e.g., "Library", "Admin Building", "Café", "Student Plaza") or tap any pin on the map.
   - Tap **"Walk Route"** to calculate the optimal pedestrian path.
3. **AR Walk Mode:**
   - Tap the **AR button** in the bottom navigation pill or start navigation.
   - Point your camera forward: 3D waypoints guide you along the route, and the top calibration pill confirms on-device sensor health.
   - As you approach the destination, the Local AI visually identifies and locks onto the destination.
4. **Offline Map Downloader:**
   - Open **Settings** (top right) → Tap **"Download New Area on Map"**.
   - Pan and zoom the map inside the rectangular viewfinder to frame your target region.
   - Tap **"Download Selected Area"** to compile the live OSM routing graph and POIs into an active local SQLite package.

---

## 📄 Open Source & Data Disclosures

- **Map Data:** © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors (Licensed under Open Database License / ODbL).
- **Vision Model:** Google MediaPipe Tasks Vision (`mobilenet_v3_small` feature extractor).
- **Cloud Usage:** Zero cloud AI APIs or telemetry servers are used during app runtime.

---

## 👥 Authors & Team

- **Julius Silvano** ([@Iussyan](https://github.com/Iussyan)) — Lead Developer
- **John Rey Calipes**
- **Raffy Elmedo**
- **Jayson Dela Cruz**
- Built for the **AppBuilders Hackathon**
