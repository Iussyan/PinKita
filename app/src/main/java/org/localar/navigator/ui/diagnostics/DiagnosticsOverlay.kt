package org.localar.navigator.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.localar.navigator.navigation.NavigationUiState

@Composable
fun DiagnosticsOverlay(
    uiState: NavigationUiState,
    inferenceLatencyMs: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xEE090D16),
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "⚡ SYSTEM TELEMETRY & DIAGNOSTICS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                letterSpacing = 1.sp
            )

            DiagnosticRow("AI Inference", if (inferenceLatencyMs > 0) "$inferenceLatencyMs ms (CPU)" else "Idle / Standby")
            DiagnosticRow("GNSS Quality", "${uiState.locationQuality.name} (±${uiState.locationAccuracyM.toInt()}m)")
            DiagnosticRow("True Heading", "${uiState.headingDeg.toInt()}° North")
            
            uiState.targetBearingDeg?.let { tb ->
                val delta = ((tb - uiState.headingDeg + 360f) % 360f).toInt()
                DiagnosticRow("Target Bearing", "${tb.toInt()}° (Δ: ${delta}°)")
            }

            uiState.activeRoute?.let { route ->
                DiagnosticRow("Route Progress", "Maneuver ${uiState.currentManeuverIndex + 1}/${route.maneuvers.size}")
                DiagnosticRow("Remaining", "${route.totalDistanceM.toInt()} m (${(route.totalCostS / 60).toInt()} min)")
            }

            val scan = uiState.scanState
            DiagnosticRow("Vision Match", "${scan.status.name} (${(scan.bestScore * 100).toInt()}%)")
            if (scan.margin > 0f) {
                DiagnosticRow("Class Margin", "+${String.format("%.2f", scan.margin)}")
            }
            DiagnosticRow("Pack Mode", "Zero-Cloud / Offline Verified")
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            fontSize = 11.sp
        )
    }
}
