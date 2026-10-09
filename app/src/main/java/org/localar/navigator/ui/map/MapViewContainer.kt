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
import org.localar.core.routing.RoutePlan

private const val SOURCE_NETWORK = "network-source"
private const val LAYER_NETWORK = "network-layer"
private const val SOURCE_ROUTE = "route-source"
private const val LAYER_ROUTE_CASING = "route-casing-layer"
private const val LAYER_ROUTE = "route-line-layer"
private const val SOURCE_POINTS = "points-source"
private const val LAYER_POINTS = "points-layer"

private const val OFFLINE_DARK_STYLE_JSON = """{
  "version": 8,
  "name": "LocalOfflineDark",
  "sources": {},
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": {
        "background-color": "#090d16"
      }
    }
  ]
}"""

@Composable
fun MapViewContainer(
    modifier: Modifier = Modifier,
    currentLocation: GeoPoint,
    allPathsGeoJson: String,
    activeRoute: RoutePlan?,
    destinationPoint: GeoPoint? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleInstance by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                mapInstance = map
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.isLogoEnabled = false

                // Position camera initially at district coordinates
                val startPos = CameraPosition.Builder()
                    .target(LatLng(currentLocation.lat, currentLocation.lon))
                    .zoom(16.5)
                    .build()
                map.cameraPosition = startPos

                map.setStyle(Style.Builder().fromJson(OFFLINE_DARK_STYLE_JSON)) { style ->
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

    // Synchronize Walkable Paths
    LaunchedEffect(allPathsGeoJson, styleInstance) {
        val style = styleInstance ?: return@LaunchedEffect
        if (allPathsGeoJson.isNotEmpty()) {
            val netSource = style.getSourceAs<GeoJsonSource>(SOURCE_NETWORK)
            netSource?.setGeoJson(allPathsGeoJson)
        }
    }

    // Synchronize Route & Endpoints
    LaunchedEffect(activeRoute, destinationPoint, currentLocation, styleInstance) {
        val style = styleInstance ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect

        val routeSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)
        val pointsSource = style.getSourceAs<GeoJsonSource>(SOURCE_POINTS)

        if (activeRoute != null && activeRoute.polyline.isNotEmpty()) {
            // 1. Update Route LineString
            val polylineCoordinates = activeRoute.polyline.joinToString(separator = ",") {
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

            // 2. Update Start and Destination Points
            val dest = destinationPoint ?: activeRoute.polyline.last()
            val pointsGeoJson = """{
                "type": "FeatureCollection",
                "features": [
                    {
                        "type": "Feature",
                        "geometry": { "type": "Point", "coordinates": [${currentLocation.lon}, ${currentLocation.lat}] },
                        "properties": { "color": "#10b981" }
                    },
                    {
                        "type": "Feature",
                        "geometry": { "type": "Point", "coordinates": [${dest.lon}, ${dest.lat}] },
                        "properties": { "color": "#f43f5e" }
                    }
                ]
            }""".trimIndent()
            pointsSource?.setGeoJson(pointsGeoJson)

            // 3. Animate camera to fit route bounds
            try {
                val boundsBuilder = LatLngBounds.Builder()
                boundsBuilder.include(LatLng(currentLocation.lat, currentLocation.lon))
                for (pt in activeRoute.polyline) {
                    boundsBuilder.include(LatLng(pt.lat, pt.lon))
                }
                val bounds = boundsBuilder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            } catch (_: Exception) {
                // Bounds could be degenerate if single point
            }
        } else {
            // Clear route and show user location pin
            routeSource?.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
            val userLocationGeoJson = """{
                "type": "FeatureCollection",
                "features": [
                    {
                        "type": "Feature",
                        "geometry": { "type": "Point", "coordinates": [${currentLocation.lon}, ${currentLocation.lat}] },
                        "properties": { "color": "#38bdf8" }
                    }
                ]
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
            PropertyFactory.lineColor(Color.parseColor("#334155")), // Slate 700
            PropertyFactory.lineWidth(3.0f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    }
    style.addLayer(netLayer)

    // 2. Active Route Source and Layers (Casing + Glow)
    val routeSource = GeoJsonSource(SOURCE_ROUTE)
    style.addSource(routeSource)

    val routeCasing = LineLayer(LAYER_ROUTE_CASING, SOURCE_ROUTE).apply {
        setProperties(
            PropertyFactory.lineColor(Color.parseColor("#0284c7")), // Sky 600
            PropertyFactory.lineWidth(7.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    }
    style.addLayer(routeCasing)

    val routeLine = LineLayer(LAYER_ROUTE, SOURCE_ROUTE).apply {
        setProperties(
            PropertyFactory.lineColor(Color.parseColor("#38bdf8")), // Vibrant Sky 400
            PropertyFactory.lineWidth(4.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    }
    style.addLayer(routeLine)

    // 3. Points Source and Layer (Start & Destination Pins)
    val pointsSource = GeoJsonSource(SOURCE_POINTS)
    style.addSource(pointsSource)

    val pointsLayer = CircleLayer(LAYER_POINTS, SOURCE_POINTS).apply {
        setProperties(
            PropertyFactory.circleRadius(7.5f),
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleStrokeWidth(2.5f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
    }
    style.addLayer(pointsLayer)
}
