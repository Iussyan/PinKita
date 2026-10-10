package org.localar.navigator.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.localar.navigator.R

private val BrandDeepTeal = Color(0xFF0F4C4C)
private val BrandForestGreen = Color(0xFF2F6B45)
private val BrandAmber = Color(0xFFD89B2B)
private val BrandBgWarm = Color(0xFFF7F4EC)
private val BrandSurface = Color(0xFFFFFDF8)
private val BrandBorder = Color(0xFFDDD8CC)
private val BrandTextDark = Color(0xFF16302C)
private val BrandTextMuted = Color(0xFF4E5B57)
private val MintLight = Color(0xFFECFDF5)
private val MintBorder = Color(0xFFA7F3D0)

@Composable
fun AttributionDialog(
    districtName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold, color = BrandDeepTeal)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_pinkita_mark),
                    contentDescription = "PinKita Logo",
                    modifier = Modifier.size(36.dp)
                )
                Column {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandTextDark
                    )
                    Text(
                        text = stringResource(id = R.string.app_tagline),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandForestGreen,
                        letterSpacing = 1.1.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: OpenStreetMap Attribution
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandBgWarm,
                    border = BorderStroke(1.dp, BrandBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Map & Pedestrian Data",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandDeepTeal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Map data © OpenStreetMap contributors. Available under the Open Database License (ODbL). Footways, crossings, and audited entrances are processed locally into offline district packs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandTextMuted,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 2: Local AI & On-Device Vision
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MintLight,
                    border = BorderStroke(1.dp, MintBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Local AI & Vision Engine",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandForestGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Powered by MobileNetV3 TFLite and MediaPipe Tasks Vision on CPU delegate. Zero cloud inference, zero network transmission, and zero remote logging. All embedding extraction and temporal matching run 100% on-device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandTextDark,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 3: Pack Provenance
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandBgWarm,
                    border = BorderStroke(1.dp, BrandBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Installed District Pack",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Active District: $districtName\nFormat: SQLite Pedestrian Topology + float32 Vector Index\nIntegrity: SHA-256 Verified Pack Archive",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandTextMuted,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        containerColor = BrandSurface,
        shape = RoundedCornerShape(20.dp)
    )
}
