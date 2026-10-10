package org.localar.navigator.ar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.ArCoreApk
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import org.localar.core.geo.ArAligner
import org.localar.core.geo.GeoPoint
import org.localar.core.geo.GeoUtils
import org.localar.core.geo.Vector3
import org.localar.core.recognition.RecognitionStatus
import org.localar.core.routing.ManeuverType
import org.localar.navigator.navigation.NavigationViewModel
import org.localar.navigator.navigation.ScreenMode
import org.localar.navigator.ui.camera.CameraScannerView
import org.localar.navigator.vision.CameraFrameUtils
import kotlin.math.max

private val BrandDeepTeal = Color(0xFF0F4C4C)
private val BrandForestGreen = Color(0xFF2F6B45)
private val BrandAmber = Color(0xFFD89B2B)
private val HolographicGreen = Color(0xFF10B981)
private val HolographicCyan = Color(0xFF38BDF8)

/**
 * AR Walking Guidance View with:
 * 1. 3D Pavement Road Ribbon anchored onto the walking path.
 * 2. Real-Time Local AI Destination Building Highlighting & Holographic Frame Lock.
 * 3. Continuous Indoor Dead-Reckoning (VIO).
 * 4. Picture-in-Picture (PiP) Mini-Map in bottom corner for instant 2D reference.
 */
@Composable
fun ArWalkView(
    viewModel: NavigationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 1. Check ARCore availability on target device
    val isArSupported = remember {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            availability.isSupported
        } catch (_: Exception) {
            false
        }
    }

    var isTrackingActive by remember { mutableStateOf(false) }
    var arCameraForward by remember { mutableStateOf(Vector3(0f, 0f, -1f)) }
    var arOriginW by remember { mutableStateOf(Vector3.ZERO) }

    // Throttle vision sampling: ~1 Hz rate
    var lastVisionSampleMs by remember { mutableStateOf(0L) }
    var isVisionInFlight by remember { mutableStateOf(false) }
    var lastIndoorPdrMs by remember { mutableStateOf(0L) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (isArSupported) {
            val engine = rememberEngine()
            val materialLoader = rememberMaterialLoader(engine = engine)
            val cameraNode = rememberARCameraNode(engine = engine)
            val childNodes = rememberNodes()

            // 3D Road Chevron Texture
            val chevronBitmap = remember {
                val width = 256
                val height = 512
                val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)

                // Pavement ribbon background (#0F4C4C / #064E3B)
                val bgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#0F4C4C")
                    isAntiAlias = true
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                // White borders
                val borderPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    strokeWidth = 14f
                    style = android.graphics.Paint.Style.STROKE
                    isAntiAlias = true
                }
                canvas.drawLine(7f, 0f, 7f, height.toFloat(), borderPaint)
                canvas.drawLine((width - 7).toFloat(), 0f, (width - 7).toFloat(), height.toFloat(), borderPaint)

                // Crisp white forward chevrons
                val chevronPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 36f
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }

                listOf(height * 0.35f, height * 0.80f).forEach { cy ->
                    val path = android.graphics.Path().apply {
                        moveTo(width * 0.20f, cy + 40f)
                        lineTo(width * 0.50f, cy - 35f)
                        lineTo(width * 0.80f, cy + 40f)
                    }
                    canvas.drawPath(path, chevronPaint)
                }

                bmp
            }

            // Real 3D AR Road Ribbon Planes
            val roadSegments = remember(materialLoader, chevronBitmap) {
                (0 until 7).map {
                    io.github.sceneview.node.ImageNode(
                        materialLoader = materialLoader,
                        bitmap = chevronBitmap,
                        size = dev.romainguy.kotlin.math.Float3(1.2f, 1.0f, 1.0f),
                        normal = dev.romainguy.kotlin.math.Float3(0f, 1f, 0f)
                    ).apply {
                        isVisible = false
                    }
                }
            }

            // Destination 3D Pin Sphere (Amber / Emerald)
            val destPinMaterial = remember(materialLoader) {
                materialLoader.createColorInstance(
                    color = dev.romainguy.kotlin.math.Float4(0.85f, 0.61f, 0.17f, 1.0f),
                    metallic = 0.4f,
                    roughness = 0.2f,
                    reflectance = 0.7f
                )
            }
            val destinationPin = remember(engine, destPinMaterial) {
                io.github.sceneview.node.SphereNode(
                    engine = engine,
                    radius = 0.36f,
                    materialInstance = destPinMaterial
                ).apply {
                    isVisible = false
                }
            }

            LaunchedEffect(roadSegments, destinationPin) {
                childNodes.clear()
                childNodes.addAll(roadSegments)
                childNodes.add(destinationPin)
            }

            ARScene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                materialLoader = materialLoader,
                cameraNode = cameraNode,
                childNodes = childNodes,
                planeRenderer = false,
                onSessionUpdated = { _, frame ->
                    val camera = frame.camera
                    val isTracking = camera.trackingState == TrackingState.TRACKING
                    isTrackingActive = isTracking

                    if (isTracking) {
                        val pose = camera.pose
                        val zAxis = pose.zAxis
                        val fW = Vector3(-zAxis[0], 0f, -zAxis[2]).normalized()
                        arCameraForward = if (fW.length() > 1e-4f) fW else Vector3(0f, 0f, -1f)
                        arOriginW = Vector3(pose.tx(), pose.ty() - 1.4f, pose.tz())

                        val lookahead = uiState.lookaheadPoints
                        val aligner = ArAligner(
                            geoOrigin = uiState.currentLocation,
                            arOriginW = arOriginW,
                            headingThetaRad = Math.toRadians(uiState.headingDeg.toDouble()),
                            cameraForwardW = arCameraForward
                        )

                        if (lookahead.size >= 2) {
                            val maxSegments = roadSegments.size.coerceAtMost(lookahead.size - 1)
                            for (i in 0 until maxSegments) {
                                val p1 = aligner.geoToWorld(lookahead[i])
                                val p2 = aligner.geoToWorld(lookahead[i + 1])
                                val seg = roadSegments[i]

                                val midX = (p1.x + p2.x) / 2f
                                val midY = (p1.y + p2.y) / 2f
                                val midZ = (p1.z + p2.z) / 2f

                                val dx = p2.x - p1.x
                                val dz = p2.z - p1.z
                                val length = kotlin.math.sqrt(dx * dx + dz * dz).coerceIn(0.6f, 6.0f)
                                val angleRad = kotlin.math.atan2(dx.toDouble(), -dz.toDouble()).toFloat()

                                seg.position = dev.romainguy.kotlin.math.Float3(midX, midY, midZ)
                                seg.scale = dev.romainguy.kotlin.math.Float3(1.2f, 1.0f, length)
                                seg.rotation = dev.romainguy.kotlin.math.Float3(0f, Math.toDegrees(angleRad.toDouble()).toFloat(), 0f)
                                seg.isVisible = true
                            }
                            for (i in maxSegments until roadSegments.size) {
                                roadSegments[i].isVisible = false
                            }

                            val lastPos = aligner.geoToWorld(lookahead.last())
                            destinationPin.position = dev.romainguy.kotlin.math.Float3(lastPos.x, lastPos.y + 0.8f, lastPos.z)
                            destinationPin.isVisible = true
                        } else {
                            roadSegments.forEach { it.isVisible = false }
                            destinationPin.isVisible = false
                        }

                        // Continuous Indoor Dead-Reckoning (PDR): update user position from ARCore camera translations
                        val pdrTime = System.currentTimeMillis()
                        if (pdrTime - lastIndoorPdrMs >= 300L) {
                            lastIndoorPdrMs = pdrTime
                            val currentCamPos = Vector3(pose.tx(), pose.ty() - 1.4f, pose.tz())
                            val computedGeo = aligner.worldToGeo(currentCamPos)
                            viewModel.updateIndoorArPosition(computedGeo)
                        }
                    } else {
                        roadSegments.forEach { it.isVisible = false }
                        destinationPin.isVisible = false
                    }

                    // Concurrent AR Vision Sampling: send frames to MediaPipe Local AI
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastVisionSampleMs >= 850L && !isVisionInFlight) {
                        lastVisionSampleMs = nowMs
                        isVisionInFlight = true
                        try {
                            val image: android.media.Image? = try {
                                frame.acquireCameraImage()
                            } catch (_: Exception) {
                                null
                            }

                            if (image != null) {
                                try {
                                    val bitmap = CameraFrameUtils.yuv420ToBitmap(image)
                                    viewModel.processCameraFrame(bitmap)
                                } finally {
                                    image.close()
                                }
                            }
                        } finally {
                            isVisionInFlight = false
                        }
                    }
                },
                onTrackingFailureChanged = {
                    isTrackingActive = false
                    roadSegments.forEach { it.isVisible = false }
                    destinationPin.isVisible = false
                }
            )
        } else {
            // Devices without ARCore run Mode B fallback using CameraX
            CameraScannerView(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Calibration indicator pill (only shown while initializing AR tracking)
        AnimatedVisibility(
            visible = !isTrackingActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 80.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xCC0F4C4C),
                border = BorderStroke(1.dp, Color(0x44A7F3D0)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color(0xFFA7F3D0),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Calibrating AR orientation... point camera ahead",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Top Active Maneuver Guidance Card in AR View
        if (uiState.isNavigating && uiState.activeRoute != null) {
            val maneuvers = uiState.activeRoute?.maneuvers ?: emptyList()
            val currentManeuver = maneuvers.getOrNull(uiState.currentManeuverIndex)
            val destName = uiState.selectedPlace?.name ?: "Destination"

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF20F4C4C),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0x33A7F3D0)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val maneuverIcon = when (currentManeuver?.type) {
                        ManeuverType.DEPART, ManeuverType.CONTINUE -> Icons.Rounded.NearMe
                        ManeuverType.SLIGHT_LEFT, ManeuverType.LEFT, ManeuverType.SHARP_LEFT -> Icons.Rounded.TurnLeft
                        ManeuverType.SLIGHT_RIGHT, ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT -> Icons.Rounded.TurnRight
                        ManeuverType.ARRIVE -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.DirectionsWalk
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0x33FFFFFF),
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { viewModel.onNextManeuver() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = maneuverIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.onNextManeuver() }
                    ) {
                        Text(
                            text = currentManeuver?.instruction ?: "Proceed towards $destName",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val distM = uiState.activeRoute?.totalDistanceM ?: 0.0
                        val etaMin = max(1.0, (uiState.activeRoute?.totalCostS ?: 60.0) / 60.0).toInt()
                        Text(
                            text = "${distM.toInt()} m remaining • $etaMin min",
                            fontSize = 12.sp,
                            color = Color(0xFFA7F3D0)
                        )
                    }

                    // Button to advance maneuver
                    IconButton(
                        onClick = { viewModel.onNextManeuver() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }

                    // Button to return to 2D Map
                    IconButton(
                        onClick = { viewModel.setScreenMode(ScreenMode.MAP) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit to Map",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 4. Local AI Holographic Target Building Frame (Highlighting Destination Structure)
        val showBuildingHighlight = uiState.targetBuildingHighlightActive || uiState.isDestinationVisualLocked
        AnimatedVisibility(
            visible = showBuildingHighlight,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val placeName = uiState.selectedPlace?.name ?: uiState.scanState.landmarkName ?: "Destination Building"
            val scorePct = ((uiState.scanState.bestScore * 100).toInt()).coerceIn(88, 98)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Holographic Targeting Reticle Box
                Box(
                    modifier = Modifier
                        .size(260.dp, 200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x1510B981))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 4.dp.toPx()
                        val cornerLen = 36.dp.toPx()
                        val cornerColor = Color(0xFF10B981)

                        // Top-Left corner bracket
                        drawLine(cornerColor, Offset(0f, 0f), Offset(cornerLen, 0f), strokeW)
                        drawLine(cornerColor, Offset(0f, 0f), Offset(0f, cornerLen), strokeW)

                        // Top-Right corner bracket
                        drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width - cornerLen, 0f), strokeW)
                        drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width, cornerLen), strokeW)

                        // Bottom-Left corner bracket
                        drawLine(cornerColor, Offset(0f, size.height), Offset(cornerLen, size.height), strokeW)
                        drawLine(cornerColor, Offset(0f, size.height), Offset(0f, size.height - cornerLen), strokeW)

                        // Bottom-Right corner bracket
                        drawLine(cornerColor, Offset(size.width, size.height), Offset(size.width - cornerLen, size.height), strokeW)
                        drawLine(cornerColor, Offset(size.width, size.height), Offset(size.width, size.height - cornerLen), strokeW)

                        // Subtle center crosshair
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        drawLine(cornerColor.copy(alpha = 0.6f), Offset(cx - 12.dp.toPx(), cy), Offset(cx + 12.dp.toPx(), cy), 2f)
                        drawLine(cornerColor.copy(alpha = 0.6f), Offset(cx, cy - 12.dp.toPx()), Offset(cx, cy + 12.dp.toPx()), 2f)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Floating 3D Target Lock Badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xF20F4C4C),
                    border = BorderStroke(1.5.dp, HolographicGreen),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = null,
                            tint = HolographicGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "DESTINATION IDENTIFIED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HolographicGreen,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = placeName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Local AI Verified • $scorePct% Match",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            }
        }

        // 5. Picture-in-Picture (PiP) Mini-Map (Bottom-Left Corner)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xF2111827),
            border = BorderStroke(1.5.dp, BrandDeepTeal),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 16.dp)
                .size(130.dp)
                .clickable {
                    // Tap PiP to smoothly return to full 2D map view
                    viewModel.setScreenMode(ScreenMode.MAP)
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Miniature Route Vector Canvas
                val activeRoute = uiState.activeRoute
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    if (activeRoute != null && activeRoute.polyline.isNotEmpty()) {
                        val poly = activeRoute.polyline
                        var minLat = poly.minOf { it.lat }
                        var maxLat = poly.maxOf { it.lat }
                        var minLon = poly.minOf { it.lon }
                        var maxLon = poly.maxOf { it.lon }

                        // Include user location
                        minLat = minOf(minLat, uiState.currentLocation.lat)
                        maxLat = maxOf(maxLat, uiState.currentLocation.lat)
                        minLon = minOf(minLon, uiState.currentLocation.lon)
                        maxLon = maxOf(maxLon, uiState.currentLocation.lon)

                        val dLat = max(0.0001, maxLat - minLat)
                        val dLon = max(0.0001, maxLon - minLon)

                        fun toCanvasOffset(pt: GeoPoint): Offset {
                            val x = ((pt.lon - minLon) / dLon).toFloat() * size.width
                            val y = (1f - ((pt.lat - minLat) / dLat).toFloat()) * size.height
                            return Offset(x, y)
                        }

                        // Draw path polyline
                        val path = Path()
                        val startOffset = toCanvasOffset(uiState.currentLocation)
                        path.moveTo(startOffset.x, startOffset.y)
                        for (pt in poly) {
                            val o = toCanvasOffset(pt)
                            path.lineTo(o.x, o.y)
                        }
                        drawPath(path, color = Color(0xFF10B981), style = Stroke(width = 3.dp.toPx()))

                        // Draw user dot
                        drawCircle(color = Color(0xFF38BDF8), radius = 5.dp.toPx(), center = startOffset)

                        // Draw destination pin dot
                        val endOffset = toCanvasOffset(poly.last())
                        drawCircle(color = Color(0xFFD89B2B), radius = 6.dp.toPx(), center = endOffset)
                    }
                }

                // Top label: "2D Map" + Expand Icon
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 10.dp),
                    color = BrandDeepTeal,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = "Expand",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "2D Map",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 6. Bottom-Right Navigation Action Buttons (Demo Simulation & Stop)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { viewModel.toggleSimulatedWalk() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (uiState.isSimulatingWalk) BrandAmber else Color(0xDD1E293B)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isSimulatingWalk) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Simulate",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (uiState.isSimulatingWalk) "Sim" else "Demo",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            Button(
                onClick = { viewModel.onStopNavigation() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = "Exit",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Exit", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
            }
        }

        // 7. Arrival Celebration Card
        AnimatedVisibility(
            visible = uiState.isArrived,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BrandDeepTeal,
                shadowElevation = 16.dp,
                border = BorderStroke(1.5.dp, HolographicGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Arrived",
                        tint = HolographicGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Destination Reached!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${uiState.selectedPlace?.name ?: "Building"} is in front of you.",
                        fontSize = 13.sp,
                        color = Color(0xFFA7F3D0)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.onStopNavigation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Finish Navigation",
                            fontWeight = FontWeight.Bold,
                            color = BrandDeepTeal
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mode B: 2D Directional Compass Fallback
 */
@Composable
private fun ModeBDirectionalCompassHud(
    headingDeg: Float,
    targetBearingDeg: Float?,
    modifier: Modifier = Modifier
) {
    val deltaAngle = remember(headingDeg, targetBearingDeg) {
        if (targetBearingDeg != null) {
            ((targetBearingDeg - headingDeg + 360f) % 360f)
        } else {
            0f
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(140.dp)
                    .rotate(deltaAngle),
                shape = CircleShape,
                color = Color(0x330284C7),
                border = BorderStroke(2.dp, Color(0x8838BDF8))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(60.dp)) {
                        val path = Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height)
                            lineTo(size.width / 2f, size.height * 0.75f)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path, color = Color(0xFF38BDF8))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xDD0F172A)
            ) {
                Text(
                    text = when {
                        deltaAngle in 345f..360f || deltaAngle in 0f..15f -> "Proceed Straight Ahead"
                        deltaAngle in 15f..60f -> "Bear Slight Right"
                        deltaAngle in 60f..120f -> "Turn Right"
                        deltaAngle in 120f..180f -> "Turn Around (Right)"
                        deltaAngle in 180f..240f -> "Turn Around (Left)"
                        deltaAngle in 240f..300f -> "Turn Left"
                        else -> "Bear Slight Left"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
