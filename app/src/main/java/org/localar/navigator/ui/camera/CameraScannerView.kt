package org.localar.navigator.ui.camera

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.localar.core.recognition.RecognitionStatus
import org.localar.navigator.navigation.NavigationViewModel
import org.localar.navigator.vision.CameraFrameUtils
import java.util.concurrent.Executors

@Composable
fun CameraScannerView(
    viewModel: NavigationViewModel,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val scanState = uiState.scanState

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. CameraX PreviewView
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    var lastAnalysisTimeMs = 0L

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val nowMs = System.currentTimeMillis()
                                // Throttle inference to 2 fps to preserve battery & thermals
                                if (nowMs - lastAnalysisTimeMs >= 500L) {
                                    lastAnalysisTimeMs = nowMs
                                    try {
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val bitmap = CameraFrameUtils.yuv420ToBitmap(mediaImage)
                                            viewModel.processCameraFrame(bitmap)
                                        }
                                    } catch (_: Exception) {
                                    } finally {
                                        imageProxy.close()
                                    }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Center Reticle Target HUD
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val strokeWidth = 3.dp.toPx()
                val cornerLength = 32.dp.toPx()
                val reticleColor = when (scanState.status) {
                    RecognitionStatus.IDENTIFIED -> Color(0xFF10B981) // Emerald
                    RecognitionStatus.POSSIBLE -> Color(0xFFFBBF24)   // Amber
                    RecognitionStatus.UNKNOWN -> Color(0xFF64748B)    // Slate
                    else -> Color(0xFF00E5FF)                         // Cyan
                }

                // Top-Left corner
                drawLine(reticleColor, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth, StrokeCap.Round)
                drawLine(reticleColor, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth, StrokeCap.Round)

                // Top-Right corner
                drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width - cornerLength, 0f), strokeWidth, StrokeCap.Round)
                drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width, cornerLength), strokeWidth, StrokeCap.Round)

                // Bottom-Left corner
                drawLine(reticleColor, Offset(0f, size.height), Offset(cornerLength, size.height), strokeWidth, StrokeCap.Round)
                drawLine(reticleColor, Offset(0f, size.height), Offset(0f, size.height - cornerLength), strokeWidth, StrokeCap.Round)

                // Bottom-Right corner
                drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width - cornerLength, size.height), strokeWidth, StrokeCap.Round)
                drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width, size.height - cornerLength), strokeWidth, StrokeCap.Round)

                // Center reticle ring
                drawCircle(
                    color = reticleColor.copy(alpha = 0.4f),
                    radius = 16.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Back / Close button at Top-Start
        IconButton(
            onClick = { viewModel.setScreenMode(org.localar.navigator.navigation.ScreenMode.MAP) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .background(Color(0x88000000), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back to Map",
                tint = Color.White
            )
        }

        // 3. Status Badge HUD at Bottom Center
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xF20F4C4C),
            border = BorderStroke(1.dp, Color(0x33A7F3D0)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (scanState.status) {
                    RecognitionStatus.IDENTIFIED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                            Text(
                                text = "LANDMARK IDENTIFIED",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = scanState.landmarkName ?: "Recognized POI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Confidence: ${(scanState.bestScore * 100).toInt()}% • Margin: +${String.format("%.2f", scanState.margin)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    RecognitionStatus.POSSIBLE -> {
                        Text(
                            text = "Evaluating candidate: ${scanState.landmarkName}…",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFFBBF24)
                        )
                        Text(
                            text = "Holding phone steady for temporal validation",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    RecognitionStatus.UNKNOWN -> {
                        Text(
                            text = "Unknown Scene",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "Point camera directly toward an audited landmark",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                    else -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFA7F3D0)
                            )
                            Text(
                                text = "MobileNetV3 Scanning (Zero Cloud)…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}
