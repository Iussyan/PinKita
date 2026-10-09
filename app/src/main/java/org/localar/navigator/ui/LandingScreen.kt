package org.localar.navigator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.localar.navigator.R
import org.localar.navigator.ui.theme.PinKitaColors

@Composable
fun LandingScreen(
    onContinue: () -> Unit,
    onDownloadMaps: () -> Unit,
    onSignInLater: () -> Unit = onContinue,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PinKitaColors.BackgroundWarm)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            // 1. Header Landscape Illustration with Trees & Mountains
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(135.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.landing_header_bg),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Headline & Subheading
            Text(
                text = "even offline.",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 27.sp,
                    lineHeight = 33.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = PinKitaColors.TextDark,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Use PinKita locally with downloaded maps.\nNo account required.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = PinKitaColors.SlateText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Hero Feature Preview Card (Scenic Trail + AR HUD + Inset Map)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = PinKitaColors.SurfaceWarm,
                border = BorderStroke(1.dp, PinKitaColors.BorderWarm),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .aspectRatio(454f / 235f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.landing_hero_preview),
                    contentDescription = "PinKita Offline Navigation & AR Guidance",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Feature Badges Pill Card (4 Columns)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PinKitaColors.SurfaceWarm,
                border = BorderStroke(1.dp, PinKitaColors.BorderWarm),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LandingBadgeItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFFEF3C7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = PinKitaColors.AmberAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        title = "Works",
                        subtitle = "Offline"
                    )

                    HorizontalDividerVertical(modifier = Modifier.height(30.dp))

                    LandingBadgeItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE6EFEB), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonOff,
                                    contentDescription = null,
                                    tint = PinKitaColors.DeepTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        title = "No Account",
                        subtitle = "Needed"
                    )

                    HorizontalDividerVertical(modifier = Modifier.height(30.dp))

                    // Replaced "Guest Access" with "Instant Access" per user specification
                    LandingBadgeItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE2EFE0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = PinKitaColors.ForestGreen,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        },
                        title = "Instant",
                        subtitle = "Access"
                    )

                    HorizontalDividerVertical(modifier = Modifier.height(30.dp))

                    LandingBadgeItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE0F2FE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Explore,
                                    contentDescription = null,
                                    tint = PinKitaColors.PineGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        title = "AR Nav",
                        subtitle = "Ready"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Primary Action Button: "Continue" (replaces "Continue as Guest")
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PinKitaColors.DeepTeal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0x2BFFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Secondary Action Button: "Download Local Maps"
            OutlinedButton(
                onClick = onDownloadMaps,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PinKitaColors.SurfaceWarm,
                    contentColor = PinKitaColors.TextDark
                ),
                border = BorderStroke(1.dp, PinKitaColors.BorderWarm),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(50.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null,
                            tint = PinKitaColors.DeepTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Download Local Maps",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            ),
                            color = PinKitaColors.TextDark
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = PinKitaColors.SlateText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        
            Spacer(modifier = Modifier.height(10.dp))

            // 8. "What you can do offline" Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "What you can do offline",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = PinKitaColors.TextDark
                    )
                    Text(
                        text = "See all >",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = PinKitaColors.SlateText,
                        modifier = Modifier.clickable { onContinue() }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OfflineActionCard(
                        modifier = Modifier.weight(1f),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFE2EFE0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = PinKitaColors.ForestGreen,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        },
                        title = "Search\nsaved places",
                        description = "Find bookmarks & local spots."
                    )

                    OfflineActionCard(
                        modifier = Modifier.weight(1f),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFFEF3C7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NearMe,
                                    contentDescription = null,
                                    tint = PinKitaColors.AmberAccent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        },
                        title = "Start\nlocal routes",
                        description = "Turn-by-turn routing on device."
                    )

                    OfflineActionCard(
                        modifier = Modifier.weight(1f),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFE0F2FE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Explore,
                                    contentDescription = null,
                                    tint = PinKitaColors.PineGreen,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        },
                        title = "Use AR\nguidance",
                        description = "Real-world camera directions."
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 9. Bottom Privacy Notice Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEAE6DC),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = PinKitaColors.ForestGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Your data stays on this device unless you choose to sign in.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = PinKitaColors.SlateText
                    )
                }
            }
        }
    }
}

@Composable
private fun LandingBadgeItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(66.dp)
    ) {
        icon()
        Text(
            text = "$title\n$subtitle",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                lineHeight = 13.sp
            ),
            color = PinKitaColors.TextDark,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OfflineActionCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PinKitaColors.SurfaceWarm,
        border = BorderStroke(1.dp, PinKitaColors.BorderWarm),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp
                ),
                color = PinKitaColors.TextDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                ),
                color = PinKitaColors.SlateText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HorizontalDividerVertical(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .background(PinKitaColors.BorderWarm)
    )
}
