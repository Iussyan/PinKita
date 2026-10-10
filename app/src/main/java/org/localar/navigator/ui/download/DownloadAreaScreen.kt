package org.localar.navigator.ui.download

import android.graphics.PointF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.localar.navigator.navigation.NavigationViewModel
import org.localar.navigator.navigation.ScreenMode
import org.localar.navigator.packs.AreaBounds
import org.localar.navigator.packs.DownloadState
import org.localar.navigator.ui.map.getMapStyleJson
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.*

private val BrandDeepTeal = Color(0xFF0F4C4C)
private val BrandForestGreen = Color(0xFF2F6B45)
private val BrandAmber = Color(0xFFD89B2B)
private val BrandSurface = Color(0xFFFFFDF8)
private val BrandBorder = Color(0xFFE5E0D4)
private val BrandTextDark = Color(0xFF1E2621)
private val BrandTextMuted = Color(0xFF6B7280)
private val MintLight = Color(0xFFE8F5E9)
private val MintBorder = Color(0xFFA5D6A7)

@Composable
fun DownloadAreaScreen(
    viewModel: NavigationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedBounds by remember {
        mutableStateOf(
            AreaBounds(
                minLat = uiState.currentLocation.lat - 0.006,
                minLon = uiState.currentLocation.lon - 0.007,
                maxLat = uiState.currentLocation.lat + 0.006,
                maxLon = uiState.currentLocation.lon + 0.007,
                name = "Selected Area"
            )
        )
    }

    val boxWidthDp = 300.dp
    val boxHeightDp = 220.dp
    val boxOffsetY = (-40).dp

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                mapInstance = map
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.isLogoEnabled = false
                map.setMinZoomPreference(11.0)
                map.setMaxZoomPreference(19.0)

                val startPos = CameraPosition.Builder()
                    .target(LatLng(uiState.currentLocation.lat, uiState.currentLocation.lon))
                    .zoom(15.2)
                    .build()
                map.cameraPosition = startPos

                map.setStyle(Style.Builder().fromJson(getMapStyleJson(0))) {}

                fun updateBounds() {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    if (w <= 0f || h <= 0f) return

                    val density = resources.displayMetrics.density
                    val bw = boxWidthDp.value * density
                    val bh = boxHeightDp.value * density
                    val dy = boxOffsetY.value * density

                    val cx = w / 2f
                    val cy = (h / 2f) + dy

                    val left = cx - bw / 2f
                    val top = cy - bh / 2f
                    val right = cx + bw / 2f
                    val bottom = cy + bh / 2f

                    val nw = map.projection.fromScreenLocation(PointF(left, top))
                    val se = map.projection.fromScreenLocation(PointF(right, bottom))

                    if (nw != null && se != null) {
                        val minLat = min(nw.latitude, se.latitude)
                        val maxLat = max(nw.latitude, se.latitude)
                        val minLon = min(nw.longitude, se.longitude)
                        val maxLon = max(nw.longitude, se.longitude)

                        selectedBounds = AreaBounds(
                            minLat = minLat,
                            minLon = minLon,
                            maxLat = maxLat,
                            maxLon = maxLon,
                            name = "Custom Area (${String.format("%.3f", (minLat + maxLat)/2)}, ${String.format("%.3f", (minLon + maxLon)/2)})"
                        )
                    }
                }

                map.addOnCameraMoveListener { updateBounds() }
                map.addOnCameraIdleListener { updateBounds() }
            }
        }
    }

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
            viewModel.resetDownloadState()
        }
    }

    // Calculate approximate area in km² and storage size
    val latSpan = abs(selectedBounds.maxLat - selectedBounds.minLat)
    val lonSpan = abs(selectedBounds.maxLon - selectedBounds.minLon)
    val centerLat = (selectedBounds.minLat + selectedBounds.maxLat) / 2.0
    val areaKm2 = (latSpan * 111.0) * (lonSpan * 111.0 * cos(Math.toRadians(centerLat)))
    val estSizeMb = max(1.2, areaKm2 * 1.5)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. Interactive Map View
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Google Maps-style Selection Frame Overlay with darkened borders
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bw = boxWidthDp.toPx()
            val bh = boxHeightDp.toPx()
            val dy = boxOffsetY.toPx()

            val cx = size.width / 2f
            val cy = (size.height / 2f) + dy

            val left = cx - bw / 2f
            val top = cy - bh / 2f
            val right = cx + bw / 2f
            val bottom = cy + bh / 2f

            val overlayColor = Color(0x77000000)

            // 4 darkened rectangles outside the selection window
            drawRect(color = overlayColor, topLeft = Offset(0f, 0f), size = Size(size.width, top))
            drawRect(color = overlayColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
            drawRect(color = overlayColor, topLeft = Offset(0f, top), size = Size(left, bh))
            drawRect(color = overlayColor, topLeft = Offset(right, top), size = Size(size.width - right, bh))

            // Selection box border
            drawRoundRect(
                color = BrandDeepTeal,
                topLeft = Offset(left, top),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )

            // Accent viewfinder corner brackets
            val cornerLen = 22.dp.toPx()
            val strokeW = 4.dp.toPx()
            val cornerColor = BrandForestGreen

            // Top-Left
            drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLen), strokeWidth = strokeW, cap = StrokeCap.Round)
            // Top-Right
            drawLine(cornerColor, Offset(right, top), Offset(right - cornerLen, top), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLen), strokeWidth = strokeW, cap = StrokeCap.Round)
            // Bottom-Left
            drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeWidth = strokeW, cap = StrokeCap.Round)
            // Bottom-Right
            drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeWidth = strokeW, cap = StrokeCap.Round)
        }

        // 3. Top Floating Header Card
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = BrandSurface,
            border = BorderStroke(1.dp, BrandBorder),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFFF3F4F6), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = BrandDeepTeal
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download Offline Map",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = BrandTextDark
                    )
                    Text(
                        text = "Pan and zoom inside the rectangle to frame area",
                        fontSize = 12.sp,
                        color = BrandTextMuted
                    )
                }
            }
        }

        // 4. Bottom Info & Download Card
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = BrandSurface,
            border = BorderStroke(1.dp, BrandBorder),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Size & Area Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Selected Region",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BrandTextDark
                        )
                        Text(
                            text = String.format("Coverage: %.1f km²", areaKm2),
                            fontSize = 12.sp,
                            color = BrandTextMuted
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MintLight,
                        border = BorderStroke(1.dp, MintBorder)
                    ) {
                        Text(
                            text = String.format("Size: ~%.1f MB", estSizeMb),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BrandForestGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Included Data Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.DirectionsWalk, contentDescription = null, tint = BrandDeepTeal, modifier = Modifier.size(14.dp))
                            Text("Walk routes", fontSize = 11.sp, color = BrandTextDark)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Place, contentDescription = null, tint = BrandDeepTeal, modifier = Modifier.size(14.dp))
                            Text("Local POIs", fontSize = 11.sp, color = BrandTextDark)
                        }
                    }
                }

                // Download Progress or Status
                when (val state = uiState.downloadState) {
                    is DownloadState.Progress -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(state.message, fontSize = 12.sp, color = BrandDeepTeal, fontWeight = FontWeight.SemiBold)
                                Text("${state.percent}%", fontSize = 12.sp, color = BrandDeepTeal, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = state.percent / 100f,
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = BrandForestGreen,
                                trackColor = Color(0xFFE5E7EB)
                            )
                        }
                    }
                    is DownloadState.Success -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MintLight,
                            border = BorderStroke(1.dp, MintBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = BrandForestGreen)
                                Column {
                                    Text(
                                        text = if (state.isSyntheticFallback) "Offline area generated successfully!" else "Downloaded from OpenStreetMap!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = BrandForestGreen
                                    )
                                    Text(
                                        text = "Indexed ${state.totalPlaces} places & ${state.totalWays} pedestrian paths",
                                        fontSize = 11.sp,
                                        color = BrandTextDark
                                    )
                                }
                            }
                        }
                    }
                    is DownloadState.Error -> {
                        Text(state.message, fontSize = 12.sp, color = Color(0xFFDC2626))
                    }
                    DownloadState.Idle -> {}
                }

                // Primary Action Button
                val isDownloading = uiState.downloadState is DownloadState.Progress
                val isDownloaded = uiState.downloadState is DownloadState.Success

                Button(
                    onClick = {
                        if (isDownloaded) {
                            viewModel.setScreenMode(ScreenMode.MAP)
                        } else if (!isDownloading) {
                            viewModel.startAreaDownload(selectedBounds)
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandDeepTeal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Downloading Package...", fontWeight = FontWeight.Bold, color = Color.White)
                    } else if (isDownloaded) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activate & Return to Map", fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Selected Area", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
