package org.localar.navigator.ui.map

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.localar.core.geo.GeoPoint
import org.localar.core.geo.GeoUtils
import org.localar.core.routing.RoutePlan
import org.localar.navigator.data.Place

private const val SOURCE_NETWORK = "network-source"
private const val LAYER_NETWORK = "network-layer"
private const val SOURCE_ROUTE = "route-source"
private const val LAYER_ROUTE_CASING = "route-casing-layer"
private const val LAYER_ROUTE = "route-line-layer"
private const val SOURCE_POIS = "pois-source"
private const val LAYER_POIS_OUTER = "pois-outer-layer"
private const val LAYER_POIS_INNER = "pois-inner-layer"
private const val SOURCE_POINTS = "points-source"
private const val LAYER_USER_HALO = "user-halo-layer"
private const val LAYER_POINTS = "points-layer"

// Map Styles: Clean OSM, Topo, and Dark
private fun getMapStyleJson(styleIndex: Int): String {
    val (tiles, bgColor) = when (styleIndex % 3) {
        1 -> listOf("https://a.tile.opentopomap.org/{z}/{x}/{y}.png", "https://b.tile.opentopomap.org/{z}/{x}/{y}.png") to "#E5E7EB"
        2 -> listOf("https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/{z}/{x}/{y}.png", "https://cartodb-basemaps-b.global.ssl.fastly.net/dark_all/{z}/{x}/{y}.png") to "#111827"
        else -> listOf("https://a.tile.openstreetmap.org/{z}/{x}/{y}.png", "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png", "https://c.tile.openstreetmap.org/{z}/{x}/{y}.png") to "#F7F4EC"
    }

    val tileListJson = tiles.joinToString(",") { "\"$it\"" }
    return """{
      "version": 8,
      "name": "PinKitaRasterStyle",
      "sources": {
        "osm-tiles": {
          "type": "raster",
          "tiles": [$tileListJson],
          "tileSize": 256,
          "maxzoom": 19,
          "attribution": "© OpenStreetMap contributors"
        }
      },
      "layers": [
        {
          "id": "background",
          "type": "background",
          "paint": {
            "background-color": "$bgColor"
          }
        },
        {
          "id": "osm-raster-layer",
          "type": "raster",
          "source": "osm-tiles",
          "minzoom": 0
        }
      ]
    }"""
}

@Composable
fun MapViewContainer(
    modifier: Modifier = Modifier,
    currentLocation: GeoPoint,
    headingDeg: Float = 0f,
    allPathsGeoJson: String = "",
    activeRoute: RoutePlan? = null,
    destinationPoint: GeoPoint? = null,
    places: List<Place> = emptyList(),
    selectedPlace: Place? = null,
    mapStyleIndex: Int = 0,
    recenterTrigger: Long = 0L,
    onPlaceSelected: ((Place) -> Unit)? = null,
    onMapClick: ((GeoPoint) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleInstance by remember { mutableStateOf<Style?>(null) }
    var hasCenteredInitialLocation by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                mapInstance = map
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.isLogoEnabled = false
                map.setMinZoomPreference(2.0)
                map.setMaxZoomPreference(22.0)

                // High-precision touch detection for POI pins
                map.addOnMapClickListener { latLng ->
                    val clickPt = map.projection.toScreenLocation(latLng)
                    var nearestPlace: Place? = null
                    var minDistancePx = Double.MAX_VALUE

                    for (p in places) {
                        val pPt = map.projection.toScreenLocation(LatLng(p.lat, p.lon))
                        val dPx = Math.hypot((clickPt.x - pPt.x).toDouble(), (clickPt.y - pPt.y).toDouble())
                        if (dPx < minDistancePx) {
                            minDistancePx = dPx
                            nearestPlace = p
                        }
                    }

                    // Tapping within ~44px (~12dp on high-density) selects that landmark
                    if (nearestPlace != null && minDistancePx <= 54.0) {
                        onPlaceSelected?.invoke(nearestPlace)
                        return@addOnMapClickListener true
                    }

                    onMapClick?.invoke(GeoPoint(latLng.latitude, latLng.longitude))
                    true
                }

                // Initial position centered on user
                val startPos = CameraPosition.Builder()
                    .target(LatLng(currentLocation.lat, currentLocation.lon))
                    .zoom(15.5)
                    .build()
                map.cameraPosition = startPos

                map.setStyle(Style.Builder().fromJson(getMapStyleJson(mapStyleIndex))) { style ->
                    styleInstance = style
                    setupLayers(style)
                }
            }
        }
    }

    // Forward Android lifecycle events to MapView
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Switch Map Style dynamically
    LaunchedEffect(mapStyleIndex) {
        val map = mapInstance ?: return@LaunchedEffect
        map.setStyle(Style.Builder().fromJson(getMapStyleJson(mapStyleIndex))) { style ->
            styleInstance = style
            setupLayers(style)
        }
    }

    // Synchronize Walkable Paths
    LaunchedEffect(allPathsGeoJson, styleInstance) {
        val style = styleInstance ?: return@LaunchedEffect
        if (allPathsGeoJson.isNotEmpty()) {
            val netSource = style.getSourceAs<GeoJsonSource>(SOURCE_NETWORK)
            netSource?.setGeoJson(allPathsGeoJson)
        }
    }

    // Synchronize POI markers
    LaunchedEffect(places, selectedPlace, styleInstance) {
        val style = styleInstance ?: return@LaunchedEffect
        val poiSource = style.getSourceAs<GeoJsonSource>(SOURCE_POIS) ?: return@LaunchedEffect

        val features = places.map { place ->
            val isSelected = (selectedPlace?.id == place.id)
            val colorHex = when (place.category.lowercase()) {
                "cafe", "coffee" -> "#D89B2B" // Amber
                "park", "nature", "recreation" -> "#2F6B45" // Forest Green
                "museum", "attraction", "tourism", "heritage" -> "#0F4C4C" // Deep Teal
                "restaurant", "food", "fast_food" -> "#EA580C" // Orange
                "market", "shop", "supermarket" -> "#854D0E" // Brown
                "hotel", "hostel", "lodging" -> "#0284C7" // Blue
                else -> "#0F4C4C"
            }
            val radius = if (isSelected) 18.0 else 12.0
            val strokeWidth = if (isSelected) 3.5 else 2.0

            """{
                "type": "Feature",
                "geometry": { "type": "Point", "coordinates": [${place.lon}, ${place.lat}] },
                "properties": {
                    "id": "${place.id}",
                    "name": "${place.name.replace("\"", "\\\"")}",
                    "color": "$colorHex",
                    "radius": $radius,
                    "strokeWidth": $strokeWidth,
                    "isSelected": $isSelected
                }
            }"""
        }

        val geoJson = """{
            "type": "FeatureCollection",
            "features": [${features.joinToString(",")}]
        }"""
        poiSource.setGeoJson(geoJson)
    }

    // Animate to user location when recenter triggered
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0L) {
            val map = mapInstance ?: return@LaunchedEffect
            map.easeCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(currentLocation.lat, currentLocation.lon),
                    16.5
                ),
                800
            )
        }
    }

    // Camera follow on first real GPS fix only
    LaunchedEffect(currentLocation, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!hasCenteredInitialLocation && (currentLocation.lat != 14.5990 || currentLocation.lon != 120.9840)) {
            hasCenteredInitialLocation = true
            map.easeCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(currentLocation.lat, currentLocation.lon),
                    15.8
                ),
                1000
            )
        }
    }

    // Animate camera to fit route ONLY when activeRoute changes
    LaunchedEffect(activeRoute?.routeId, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        val route = activeRoute ?: return@LaunchedEffect
        if (route.polyline.isNotEmpty()) {
            try {
                val boundsBuilder = LatLngBounds.Builder()
                boundsBuilder.include(LatLng(currentLocation.lat, currentLocation.lon))
                for (pt in route.polyline) {
                    boundsBuilder.include(LatLng(pt.lat, pt.lon))
                }
                val bounds = boundsBuilder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140), 1200)
            } catch (_: Exception) {}
        }
    }

    // Synchronize GeoJSON Points and Route Line
    LaunchedEffect(activeRoute, destinationPoint, currentLocation, styleInstance) {
        val style = styleInstance ?: return@LaunchedEffect

        val routeSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
        val pointsSource = style.getSourceAs<GeoJsonSource>(SOURCE_POINTS)

        if (activeRoute != null && activeRoute.polyline.isNotEmpty()) {
            // Update Route LineString anchored directly to currentLocation
            val routePoints = mutableListOf<GeoPoint>()
            routePoints.add(currentLocation)
            if (activeRoute.polyline.size > 1) {
                val d0 = GeoUtils.distanceMeters(currentLocation, activeRoute.polyline[0])
                val startIndex = if (d0 < 5.0) 1 else 0
                routePoints.addAll(activeRoute.polyline.subList(startIndex, activeRoute.polyline.size))
            } else {
                routePoints.addAll(activeRoute.polyline)
            }

            val polylineCoordinates = routePoints.joinToString(separator = ",") {
                "[${it.lon}, ${it.lat}]"
            }
            val routeGeoJson = """{
                "type": "Feature",
                "geometry": {
                    "type": "LineString",
                    "coordinates": [$polylineCoordinates]
                }
            }""".trimIndent()
            routeSource?.setGeoJson(routeGeoJson)

            // Start (user) and Destination Points
            val dest = destinationPoint ?: activeRoute.polyline.last()
            val pointsGeoJson = """{
                "type": "FeatureCollection",
                "features": [
                    {
                        "type": "Feature",
                        "geometry": { "type": "Point", "coordinates": [${currentLocation.lon}, ${currentLocation.lat}] },
                        "properties": { "color": "#0284C7", "radius": 8.0, "isUser": true }
                    },
                    {
                        "type": "Feature",
                        "geometry": { "type": "Point", "coordinates": [${dest.lon}, ${dest.lat}] },
                        "properties": { "color": "#D89B2B", "radius": 14.0, "isUser": false }
                    }
                ]
            }""".trimIndent()
            pointsSource?.setGeoJson(pointsGeoJson)
        } else {
            // Clear route and show user location
            routeSource?.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
            val features = mutableListOf<String>()
            features.add("""{
                "type": "Feature",
                "geometry": { "type": "Point", "coordinates": [${currentLocation.lon}, ${currentLocation.lat}] },
                "properties": { "color": "#0284C7", "radius": 8.0, "isUser": true }
            }""")
            if (destinationPoint != null) {
                features.add("""{
                    "type": "Feature",
                    "geometry": { "type": "Point", "coordinates": [${destinationPoint.lon}, ${destinationPoint.lat}] },
                    "properties": { "color": "#D89B2B", "radius": 14.0, "isUser": false }
                }""")
            }
            val userLocationGeoJson = """{
                "type": "FeatureCollection",
                "features": [${features.joinToString(",")}]
            }""".trimIndent()
            pointsSource?.setGeoJson(userLocationGeoJson)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}

private fun setupLayers(style: Style) {
    // 1. Walkable Network Source and Layer
    val netSource = GeoJsonSource(SOURCE_NETWORK)
    style.addSource(netSource)

    val netLayer = LineLayer(LAYER_NETWORK, SOURCE_NETWORK).apply {
        setProperties(
            PropertyFactory.lineColor(Color.parseColor("#94A3B8")), // Soft Slate
            PropertyFactory.lineWidth(2.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    }
    style.addLayer(netLayer)

    // 2. Active Route Source and Layers (Forest Green Casing + Emerald/Mint Line)
    val routeSource = GeoJsonSource(SOURCE_ROUTE)
    style.addSource(routeSource)

    val routeCasing = LineLayer(LAYER_ROUTE_CASING, SOURCE_ROUTE).apply {
        setProperties(
            PropertyFactory.lineColor(Color.parseColor("#064E3B")), // Deep Forest 900
            PropertyFactory.lineWidth(8.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    }
    style.addLayer(routeCasing)

    val routeLine = LineLayer(LAYER_ROUTE, SOURCE_ROUTE).apply {
        setProperties(
            PropertyFactory.lineColor(Color.parseColor("#10B981")), // Vibrant Emerald
            PropertyFactory.lineWidth(5.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    }
    style.addLayer(routeLine)

    // 3. POIs Landmark Layer (Amber/Green/Teal Pins)
    val poiSource = GeoJsonSource(SOURCE_POIS)
    style.addSource(poiSource)

    val poisOuter = CircleLayer(LAYER_POIS_OUTER, SOURCE_POIS).apply {
        setProperties(
            PropertyFactory.circleRadius(Expression.get("radius")),
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleStrokeWidth(Expression.get("strokeWidth")),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
    }
    style.addLayer(poisOuter)

    val poisInner = CircleLayer(LAYER_POIS_INNER, SOURCE_POIS).apply {
        setProperties(
            PropertyFactory.circleRadius(4.5f),
            PropertyFactory.circleColor(Color.WHITE)
        )
    }
    style.addLayer(poisInner)

    // 4. Points Source and Layer (User Puck & Destination Pin)
    val pointsSource = GeoJsonSource(SOURCE_POINTS)
    style.addSource(pointsSource)

    val userHalo = CircleLayer(LAYER_USER_HALO, SOURCE_POINTS).apply {
        setProperties(
            PropertyFactory.circleRadius(20.0f),
            PropertyFactory.circleColor(Color.parseColor("#38BDF8")),
            PropertyFactory.circleOpacity(0.35f),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor(Color.parseColor("#0284C7"))
        )
    }
    style.addLayer(userHalo)

    val pointsLayer = CircleLayer(LAYER_POINTS, SOURCE_POINTS).apply {
        setProperties(
            PropertyFactory.circleRadius(Expression.get("radius")),
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleStrokeWidth(3.0f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
    }
    style.addLayer(pointsLayer)
}
