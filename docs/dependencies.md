# Toolchain & Dependency Compatibility Matrix

**Specification Reference:** [spec.md#4.1](file:///c:/Users/QCU/Documents/AppBuilders%20Hackathon/spec.md#L126-L137)  
**Status:** Baseline established for Milestone M0 / M1  

---

## 1. System Toolchain Matrix

| Component | Pinned Version / Path | Purpose |
|---|---|---|
| **OS** | Windows 10 Pro 64-bit | Development host |
| **JDK** | Eclipse Temurin OpenJDK 17.0.20.1+1 (`hotspot`) | Android Gradle Plugin & Kotlin compiler baseline |
| **JAVA_HOME** | `%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17.0.20.1+1` | Gradle runtime environment |
| **Python** | 3.11.9 (64-bit) | Region pack build pipeline & validation tools |
| **Python Libraries** | `numpy 2.4.6`, `shapely 2.2.0`, `pillow 12.3.0`, `requests 2.34.2` | GeoJSON/OSM processing, bounding polygon operations |
| **Android SDK** | `%LOCALAPPDATA%\Android\Sdk` | Android toolchain root |
| **cmdline-tools** | 11076708 (latest) | `sdkmanager`, `avdmanager` |
| **minSdk** | 26 (Android 8.0 Oreo) | ARCore & MapLibre baseline compatibility |
| **compileSdk** | 34 (Android 14) | Modern Android API surface |
| **targetSdk** | 34 (Android 14) | Foreground location & runtime permissions |
| **Gradle** | 8.6 | Gradle build system |
| **Android Gradle Plugin** | 8.3.2 | Gradle build plugin |
| **Kotlin** | 1.9.23 | Standard Kotlin language version |

---

## 2. Core Android Dependencies (Pinned)

| Concern | Dependency / Artifact | Version | Notes / Integration Rule |
|---|---|---|---|
| **Map Rendering** | `org.maplibre.gl:android-sdk` | `11.7.0` | Required for local `pmtiles://` file URI support (Section 4.1) |
| **AR Engine** | `com.google.ar:core` | `1.43.0` | `AR Optional` in manifest; Geospatial disabled |
| **AR Scene** | `io.github.sceneview:arsceneview` | `2.2.1` | Filament-based scene adapter behind clean domain boundary |
| **Vision Inference** | `com.google.mediapipe:tasks-vision` | `0.10.14` | MediaPipe Tasks Image Embedder with packaged MobileNetV3 |
| **Local Database** | `androidx.room:room-runtime`, `androidx.room:room-ktx` | `2.6.1` | Read-only adapter for `region.sqlite`, Room for app state |
| **UI Framework** | `androidx.compose:compose-bom` | `2024.06.00` | Jetpack Compose Material 3 UI |
| **Camera Fallback** | `androidx.camera:camera-camera2`, `androidx.camera:camera-lifecycle`, `androidx.camera:camera-view` | `1.3.4` | Non-AR standalone landmark scanner |
| **Coroutines** | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.8.1` | Non-blocking A* search and background inference |
| **Preferences** | `androidx.datastore:datastore-preferences` | `1.1.1` | Region selection and user settings |

---

## 3. Tooling Policy
- **No dynamic versions:** Dynamic version syntax (`latest.release`, `+`, or unpinned Git tags) is strictly prohibited.
- **Offline First Verification:** Zero cloud/network dependencies permitted during P0 navigation. All style glyphs, sprites, vector tiles, models, and embeddings reside in local app storage.
