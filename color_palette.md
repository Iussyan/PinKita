# PinKita Color Palette

> **"Calm. Local. Trustworthy. Built for offline navigation."**  
> Core colors that define the PinKita brand identity. Inspired by nature, exploration, and real places.

![PinKita Color Palette](file:///c:/Users/QCU/Documents/AppBuilders%20Hackathon/docs/assets/color_palette.jpg)

---

## 1. Primary Brand Colors

Core colors that define the PinKita brand identity. Inspired by nature, exploration, and real places.

| Color Name | Hex Code | RGB | Sample | Role & Description |
|---|---|---|---|---|
| **Deep Teal** | `#0F4C4C` | `rgb(15, 76, 76)` | `■` | **Primary brand color.** Used for key UI elements, buttons, and navigation. |
| **Forest Green** | `#2F6B45` | `rgb(47, 107, 69)` | `■` | **Main brand color.** Represents nature, trust, and exploration. |
| **Pine Green** | `#3E7C59` | `rgb(62, 124, 89)` | `■` | **Secondary brand color.** Used for icons, highlights, and visual elements. |
| **Sage** | `#9EB89A` | `rgb(158, 184, 154)` | `■` | **Support brand color.** Adds calm and balance to the palette. |
| **Amber Accent** | `#D89B2B` | `rgb(216, 155, 43)` | `■` | **Accent color.** Used for highlights, important actions, and AR wayfinding elements. |

---

## 2. Secondary / Support Colors

Additional tones for illustrations, maps, and UI accents.

| Color Name | Hex Code | RGB | Sample | Role & Description |
|---|---|---|---|---|
| **Mist Green** | `#C9D8C1` | `rgb(201, 216, 193)` | `■` | Soft background accents, illustration tinting, and gentle divider shading. |
| **Moss** | `#6F8F68` | `rgb(111, 143, 104)` | `■` | Mid-tone greenery, park geometries, and environmental map features. |
| **Soft Olive** | `#8A9B6E` | `rgb(138, 155, 110)` | `■` | Auxiliary map features, terrain accents, and muted badge backgrounds. |
| **Lake Teal** | `#5B8D87` | `rgb(91, 141, 135)` | `■` | Water bodies, secondary indicators, and calm accent containers. |

---

## 3. Neutral UI Colors

Clean and versatile neutrals for backgrounds, surfaces, and typography.

| Token Name | Hex Code | RGB | Sample | UI Application |
|---|---|---|---|---|
| **Background** | `#F7F4EC` | `rgb(247, 244, 236)` | `■` | Primary app screen background (warm, paper-like readability). |
| **Surface** | `#FFFDF8` | `rgb(255, 253, 248)` | `■` | Floating cards, bottom sheets, search overlays, and dialogue surfaces. |
| **Border** | `#DDD8CC` | `rgb(221, 216, 204)` | `■` | Card outlines, dividers, pill borders, and list item separators. |
| **Soft Gray** | `#B9B5AA` | `rgb(185, 181, 170)` | `■` | Disabled states, inactive icons, placeholder elements, and subtle rules. |
| **Slate Text** | `#4E5B57` | `rgb(78, 91, 87)` | `■` | Secondary copy, subheadings, metadata captions, and distance labels. |
| **Text Dark** | `#16302C` | `rgb(22, 48, 44)` | `■` | Primary headings, prominent titles, button labels, and high-contrast text. |

---

## 4. Semantic Colors

Consistent colors for system feedback and status indicators.

| Status | Hex Code | RGB | Sample | System Indicator |
|---|---|---|---|---|
| **Success** | `#4C8B5F` | `rgb(76, 139, 95)` | `■` | Good GPS signal, route reached, valid download, arrival trigger. |
| **Warning** | `#D89B2B` | `rgb(216, 155, 43)` | `■` | Degraded tracking, usable GPS, approaching turn, battery advisory. |
| **Error** | `#C65A4B` | `rgb(198, 90, 75)` | `■` | GPS lost, off-route alert, pack missing, permission denied. |
| **Info** | `#4E8C89` | `rgb(78, 140, 137)` | `■` | Pack details, district notices, open data attribution, simulation active. |

---

## 5. Gradient Styles

Smooth gradients for backgrounds, hero sections, and map visuals.

| Gradient Name | Start Color | End Color | CSS / Canvas Specification |
|---|---|---|---|
| **Forest Gradient** | `#0F4C4C` *(Deep Teal)* | `#2F6B45` *(Forest Green)* | `linear-gradient(135deg, #0F4C4C 0%, #2F6B45 100%)` |
| **Nature Blend** | `#3E7C59` *(Pine Green)* | `#9EB89A` *(Sage)* | `linear-gradient(135deg, #3E7C59 0%, #9EB89A 100%)` |
| **Trail Accent** | `#2F6B45` *(Forest Green)* | `#D89B2B` *(Amber Accent)* | `linear-gradient(135deg, #2F6B45 0%, #D89B2B 100%)` |

---

## 6. Color Usage Examples & UI Components

### Primary Button
- **Role:** Used for main actions (e.g., *"Continue as Guest →"*, *"Start Navigation"*).
- **Background:** `Deep Teal (#0F4C4C)`
- **Label / Icon:** `#FFFFFF` (White)
- **Corner Radius:** `12.dp` to `20.dp` pill shape

### Secondary Button
- **Role:** Used for secondary actions (e.g., *"Download Local Maps"*, *"Recenter"*).
- **Background:** `Surface (#FFFDF8)`
- **Border:** `1.dp` solid `Deep Teal (#0F4C4C)` or `Border (#DDD8CC)`
- **Label / Icon:** `Deep Teal (#0F4C4C)`

### Map & AR Wayfinding Highlights
- **Role:** Waypoints, turns, landmark pins, and AR 3D guidance ribbons.
- **Turn Waypoints / Nav Pin:** `Pine Green (#3E7C59)` badge with white directional chevron.
- **Scenic Viewpoints & POIs:** `Amber Accent (#D89B2B)` badge with camera / binoculars icon.
- **Route Ribbon:** `Forest Gradient (#0F4C4C → #2F6B45)` or high-contrast Amber line for night/AR overlay.

### Cards & Surfaces
- **Role:** Bottom sheets, search suggestions, destination cards.
- **Background:** `Surface (#FFFDF8)`
- **Border:** `1.dp` solid `Border (#DDD8CC)`
- **Shadow:** Soft warm elevation (`color = #16302C`, `alpha = 0.08`, `blur = 8.dp`)

### Text Hierarchy
- **Header 1 / Titles:** `Text Dark (#16302C)` — bold, clear contrast on warm paper backgrounds.
- **Subheaders / Descriptions:** `Slate Text (#4E5B57)` — readable, reduced eyestrain in outdoor daylight.
- **Captions / Distance Metrics:** `Slate Text (#4E5B57)` or `Forest Green (#2F6B45)` for emphasis.

---

## 7. Android Resource Tokens

### Jetpack Compose (`Color.kt`)
```kotlin
package org.localar.navigator.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Brand
val DeepTeal = Color(0xFF0F4C4C)
val ForestGreen = Color(0xFF2F6B45)
val PineGreen = Color(0xFF3E7C59)
val Sage = Color(0xFF9EB89A)
val AmberAccent = Color(0xFFD89B2B)

// Secondary / Support
val MistGreen = Color(0xFFC9D8C1)
val Moss = Color(0xFF6F8F68)
val SoftOlive = Color(0xFF8A9B6E)
val LakeTeal = Color(0xFF5B8D87)

// Neutral UI
val WarmBackground = Color(0xFFF7F4EC)
val WarmSurface = Color(0xFFFFFDF8)
val CardBorder = Color(0xFFDDD8CC)
val SoftGray = Color(0xFFB9B5AA)
val SlateText = Color(0xFF4E5B57)
val TextDark = Color(0xFF16302C)

// Semantic
val StatusSuccess = Color(0xFF4C8B5F)
val StatusWarning = Color(0xFFD89B2B)
val StatusError = Color(0xFFC65A4B)
val StatusInfo = Color(0xFF4E8C89)
```

### Android XML (`res/values/colors.xml`)
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Primary Brand -->
    <color name="pinkita_deep_teal">#0F4C4C</color>
    <color name="pinkita_forest_green">#2F6B45</color>
    <color name="pinkita_pine_green">#3E7C59</color>
    <color name="pinkita_sage">#9EB89A</color>
    <color name="pinkita_amber_accent">#D89B2B</color>

    <!-- Secondary / Support -->
    <color name="pinkita_mist_green">#C9D8C1</color>
    <color name="pinkita_moss">#6F8F68</color>
    <color name="pinkita_soft_olive">#8A9B6E</color>
    <color name="pinkita_lake_teal">#5B8D87</color>

    <!-- Neutral UI -->
    <color name="pinkita_bg_warm">#F7F4EC</color>
    <color name="pinkita_surface_warm">#FFFDF8</color>
    <color name="pinkita_border">#DDD8CC</color>
    <color name="pinkita_soft_gray">#B9B5AA</color>
    <color name="pinkita_slate_text">#4E5B57</color>
    <color name="pinkita_text_dark">#16302C</color>

    <!-- Semantic -->
    <color name="pinkita_status_success">#4C8B5F</color>
    <color name="pinkita_status_warning">#D89B2B</color>
    <color name="pinkita_status_error">#C65A4B</color>
    <color name="pinkita_status_info">#4E8C89</color>
</resources>
```

---

## 8. Brand Pillars

- **Offline First:** Works everywhere without cellular connection or cloud dependencies.
- **Real Places:** Discover locally audited entrances, scenic viewpoints, and heritage landmarks.
- **Trustworthy:** Practical, reliable navigation designed for outdoor readability and pedestrian safety.
