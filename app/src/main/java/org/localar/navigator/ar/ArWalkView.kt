package org.localar.navigator.ar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import org.localar.core.geo.Vector3
import org.localar.core.recognition.RecognitionStatus
import org.localar.navigator.navigation.NavigationViewModel
import org.localar.navigator.ui.camera.CameraScannerView
import org.localar.navigator.vision.CameraFrameUtils

/**
 * AR Walking Guidance View conforming to Section 13, 14, & 16 of spec.md.
 * Mode A: Short-range 2-6m lookahead route rendering anchored via ArAligner when tracking is active.
 * Mode B: Camera HUD with directional compass fallback upon tracking loss or unavailable ARCore.
 * M4.4: Periodic non-competing AR-frame vision sampling feeding on-device MobileNetV3 landmark recognition.
 */
@Composable
fun ArWalkView(
    viewModel: NavigationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 1. Check ARCore availability on target device (Section 14.1)
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

    // Throttle vision sampling: at most 1 frame in flight, ~1 Hz rate (Section 16.1)
    var lastVisionSampleMs by remember { mutableStateOf(0L) }
    var isVisionInFlight by remember { mutableStateOf(false) }
    var lastIndoorPdrMs by remember { mutableStateOf(0L) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (isArSupported) {
            val engine = rememberEngine()
            val materialLoader = rememberMaterialLoader(engine = engine)
            val cameraNode = rememberARCameraNode(engine = engine)
            val childNodes = rememberNodes()

            // Generate blue chevron road segment bitmap matching the reference image
            val chevronBitmap = remember {
                val width = 256
                val height = 512
                val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)

                // 1. Blue pavement ribbon background (#2563EB)
                val bgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#2563EB")
                    isAntiAlias = true
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                // 2. White side borders (like the reference image pavement curb borders)
                val borderPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    strokeWidth = 14f
                    style = android.graphics.Paint.Style.STROKE
                    isAntiAlias = true
                }
                canvas.drawLine(7f, 0f, 7f, height.toFloat(), borderPaint)
                canvas.drawLine((width - 7).toFloat(), 0f, (width - 7).toFloat(), height.toFloat(), borderPaint)

                // 3. Crisp white forward chevron arrows
                val chevronPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 36f
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }

                // Draw two chevrons along the segment length pointing forward (up)
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

            // Real 3D AR Road Ribbon Planes (7 segments connecting up to 8 lookahead points)
            val roadSegments = remember(materialLoader, chevronBitmap) {
                (0 until 7).map {
                    io.github.sceneview.node.ImageNode(
                        materialLoader = materialLoader,
                        bitmap = chevronBitmap,
                        size = dev.romainguy.kotlin.math.Float3(1.2f, 1.0f, 1.0f),
                        normal = dev.romainguy.kotlin.math.Float3(0f, 1f, 0f) // Facing upward horizontally
                    ).apply {
                        isVisible = false
                    }
                }
            }

            // Destination 3D Pin Sphere (Ruby red with white halo at endpoint)
            val destPinMaterial = remember(materialLoader) {
                materialLoader.createColorInstance(
                    color = dev.romainguy.kotlin.math.Float4(0.93f, 0.27f, 0.27f, 1.0f),
                    metallic = 0.3f,
                    roughness = 0.2f,
                    reflectance = 0.6f
                )
            }
            val destinationPin = remember(engine, destPinMaterial) {
                io.github.sceneview.node.SphereNode(
                    engine = engine,
                    radius = 0.32f,
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
                        // Extract horizontal forward vector from AR camera orientation
                        val zAxis = pose.zAxis
                        val fW = Vector3(-zAxis[0], 0f, -zAxis[2]).normalized()
                        arCameraForward = if (fW.length() > 1e-4f) fW else Vector3(0f, 0f, -1f)

                        // Ground level: ~1.4m below phone eye-level
                        arOriginW = Vector3(pose.tx(), pose.ty() - 1.4f, pose.tz())

                        // Mode A: Update real 3D AR Road ribbon segments
                        val lookahead = uiState.lookaheadPoints
                        if (lookahead.size >= 2) {
                            val aligner = ArAligner(
                                geoOrigin = uiState.currentLocation,
                                arOriginW = arOriginW,
                                headingThetaRad = Math.toRadians(uiState.headingDeg.toDouble()),
                                cameraForwardW = arCameraForward
                            )

                            // Align each consecutive pair into a blue ribbon road tile
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

                            // Place destination marker over final lookahead point
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
                            val aligner = ArAligner(
                                geoOrigin = uiState.currentLocation,
                                arOriginW = arOriginW,
                                headingThetaRad = Math.toRadians(uiState.headingDeg.toDouble()),
                                cameraForwardW = arCameraForward
                            )
                            val currentCamPos = Vector3(pose.tx(), pose.ty() - 1.4f, pose.tz())
                            val computedGeo = aligner.worldToGeo(currentCamPos)
                            viewModel.updateIndoorArPosition(computedGeo)
                        }
                    } else {
                        roadSegments.forEach { it.isVisible = false }
                        destinationPin.isVisible = false
                    }

                    // Concurrent AR Vision Sampling without camera contention (Section 16.1 of spec.md)
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastVisionSampleMs >= 900L && !isVisionInFlight) {
                        lastVisionSampleMs = nowMs
                        isVisionInFlight = true
                        try {
                            val image: android.media.Image? = try {
                                frame.acquireCameraImage()
                            } catch (_: com.google.ar.core.exceptions.NotYetAvailableException) {
                                null
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

        // 3. Mode B: Degraded Mode & Tracking Fallback HUD (Section 14.5)
        // Activated when ARCore is tracking loss/paused or device lacks ARCore
        AnimatedVisibility(
            visible = !isTrackingActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            ModeBDirectionalCompassHud(
                headingDeg = uiState.headingDeg,
                targetBearingDeg = uiState.targetBearingDeg,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 4. Tracking Status Badge at Top Center (Clean, non-intrusive)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 70.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xF2111827),
            border = BorderStroke(1.dp, Color(0x334B5563)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dotColor = when {
                    uiState.isIndoorAnchored -> Color(0xFF10B981) // Emerald
                    isTrackingActive -> Color(0xFF38BDF8) // Sky Blue
                    else -> Color(0xFFFBBF24) // Amber warning
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape)
                )
                Text(
                    text = when {
                        uiState.isIndoorAnchored -> "Indoor AR Active • Anchored to ${uiState.indoorAnchorName ?: "Landmark"}"
                        isTrackingActive -> "AR 3D Tracking • Scan landmark to anchor indoor"
                        else -> "Compass Guidance (Calibrating AR…)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0)
                )
            }
        }

        // 5. Floating AR Landmark Identification Badge HUD
        AnimatedVisibility(
            visible = uiState.scanState.status == RecognitionStatus.IDENTIFIED ||
                      uiState.scanState.status == RecognitionStatus.POSSIBLE,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 114.dp, end = 16.dp)
        ) {
            val scan = uiState.scanState
            val isPromoted = scan.status == RecognitionStatus.IDENTIFIED
            val badgeColor = if (isPromoted) Color(0xFF10B981) else Color(0xFFFBBF24)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xF2111827),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(badgeColor, CircleShape)
                    )
                    Column {
                        Text(
                            text = if (isPromoted) "LANDMARK RECOGNIZED" else "ANALYZING TARGET…",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = scan.landmarkName ?: "Historical Landmark",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Match ${(scan.bestScore * 100).toInt()}% • On-Device AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}



/**
 * Mode B: 2D Directional Compass & Navigation HUD (Section 14.2 & 14.5 of spec.md)
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
