package org.localar.navigator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.localar.core.geo.GeoPoint
import org.localar.core.routing.Maneuver
import org.localar.core.routing.ManeuverType
import org.localar.navigator.data.Place
import org.localar.navigator.location.LocationQuality
import org.localar.navigator.navigation.NavigationViewModel
import org.localar.navigator.navigation.ScreenMode
import org.localar.navigator.ui.camera.CameraScannerView
import org.localar.navigator.ui.map.MapViewContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndRouteScreen(
    viewModel: NavigationViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchActive by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF090D16))) {
        // 1. Primary View Layer: Either MapLibre 2D Canvas or CameraX AI Scanner
        when (uiState.screenMode) {
            ScreenMode.MAP -> {
                val destinationPoint = uiState.selectedEntrance?.let {
                    GeoPoint(it.lat, it.lon)
                }
                MapViewContainer(
                    modifier = Modifier.fillMaxSize(),
                    currentLocation = uiState.currentLocation,
                    allPathsGeoJson = uiState.allPathsGeoJson,
                    activeRoute = uiState.activeRoute,
                    destinationPoint = destinationPoint
                )
            }
            ScreenMode.CAMERA_SCANNER -> {
                CameraScannerView(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. Top Header Controls: Mode Pill Switcher & Status Indicators
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Toggle: Map vs Camera Scanner
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xDD0F172A),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ModeTabButton(
                            title = "🗺️ Map",
                            isSelected = uiState.screenMode == ScreenMode.MAP,
                            onClick = { viewModel.setScreenMode(ScreenMode.MAP) }
                        )
                        ModeTabButton(
                            title = "📷 AI Scanner",
                            isSelected = uiState.screenMode == ScreenMode.CAMERA_SCANNER,
                            onClick = { viewModel.setScreenMode(ScreenMode.CAMERA_SCANNER) }
                        )
                    }
                }

                // GNSS Quality / Demo Walk Status Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xDD0F172A),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val indicatorColor = when {
                            uiState.isSimulatingWalk -> Color(0xFF38BDF8)
                            uiState.locationQuality == LocationQuality.GOOD -> Color(0xFF10B981)
                            uiState.locationQuality == LocationQuality.USABLE -> Color(0xFFFBBF24)
                            else -> Color(0xFF94A3B8)
                        }

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )
                        Text(
                            text = when {
                                uiState.isSimulatingWalk -> "Demo Walking"
                                uiState.locationQuality == LocationQuality.GOOD -> "GNSS ±${uiState.locationAccuracyM.toInt()}m"
                                uiState.locationQuality == LocationQuality.USABLE -> "GNSS Usable"
                                else -> "Offline Pack"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Top Action: Active Maneuver Banner OR Search Bar
            if (uiState.isNavigating && uiState.activeRoute != null) {
                val maneuvers = uiState.activeRoute?.maneuvers ?: emptyList()
                val currentManeuver = maneuvers.getOrNull(uiState.currentManeuverIndex)
                if (currentManeuver != null) {
                    ActiveManeuverBanner(
                        modifier = Modifier.fillMaxWidth(),
                        maneuver = currentManeuver,
                        totalManeuvers = maneuvers.size,
                        currentIndex = uiState.currentManeuverIndex,
                        onNext = { viewModel.onNextManeuver() }
                    )
                }
            } else {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onSearch = { searchActive = false },
                    active = searchActive,
                    onActiveChange = { searchActive = it },
                    placeholder = {
                        Text(
                            text = "Search public places & landmarks…",
                            color = Color(0xFF64748B)
                        )
                    },
                    colors = SearchBarDefaults.colors(
                        containerColor = Color(0xF00F172A),
                        dividerColor = Color(0xFF334155),
                        inputFieldColors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFE2E8F0)
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.searchResults) { place ->
                            PlaceSearchResultItem(
                                place = place,
                                onClick = {
                                    viewModel.onSelectPlace(place)
                                    searchActive = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Bottom Area: Place Detail Sheet
        AnimatedVisibility(
            visible = uiState.selectedPlace != null && !uiState.isNavigating && !uiState.isArrived,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            uiState.selectedPlace?.let { place ->
                PlaceDetailCard(
                    place = place,
                    entranceName = uiState.selectedEntrance?.name ?: "Main Entrance",
                    routeDistanceM = uiState.activeRoute?.totalDistanceM,
                    routeCostS = uiState.activeRoute?.totalCostS,
                    hasStairs = uiState.activeRoute?.orderedEdges?.any { it.stairs } == true,
                    hasCrossing = uiState.activeRoute?.orderedEdges?.any { it.crossingKind != null } == true,
                    onStartWalking = { viewModel.onStartNavigation() },
                    onDismiss = { viewModel.onClearSelection() }
                )
            }
        }

        // 5. Bottom Navigation Controls (When Actively Navigating)
        AnimatedVisibility(
            visible = uiState.isNavigating && !uiState.isArrived,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            uiState.activeRoute?.let { route ->
                TripProgressCard(
                    totalDistanceM = route.totalDistanceM,
                    totalCostS = route.totalCostS,
                    destinationName = uiState.selectedPlace?.name ?: "Destination",
                    isSimulating = uiState.isSimulatingWalk,
                    onToggleSimulate = { viewModel.toggleSimulatedWalk() },
                    onStop = { viewModel.onStopNavigation() }
                )
            }
        }

        // 6. Arrival Celebratory Card (When Arrived)
        AnimatedVisibility(
            visible = uiState.isArrived,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF50F172A),
                tonalElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎯", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You Have Arrived!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Near ${uiState.selectedPlace?.name ?: "destination"} (${uiState.selectedEntrance?.name ?: "entrance"})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.onStopNavigation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Complete Route", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF0284C7) else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun PlaceSearchResultItem(
    place: Place,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x661E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = place.category.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
            if (place.recognitionSupported) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x3300E5FF)
                ) {
                    Text(
                        text = "AI Vision",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceDetailCard(
    place: Place,
    entranceName: String,
    routeDistanceM: Double?,
    routeCostS: Double?,
    hasStairs: Boolean,
    hasCrossing: Boolean,
    onStartWalking: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF20F172A),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Entrance: $entranceName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF38BDF8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Text("✕", color = Color(0xFF94A3B8), fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Badges row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (place.recognitionSupported) {
                    BadgeChip(text = "Visual POI Supported", color = Color(0xFF00E5FF))
                }
                if (hasStairs) {
                    BadgeChip(text = "Stairs on Route", color = Color(0xFFFBBF24))
                }
                if (hasCrossing) {
                    BadgeChip(text = "Street Crossing", color = Color(0xFFA78BFA))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Route metrics
            if (routeDistanceM != null && routeCostS != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val minutes = (routeCostS / 60.0).coerceAtLeast(1.0).toInt()
                        Text(
                            text = "${routeDistanceM.toInt()} m",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Est. $minutes min walk",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Button(
                        onClick = onStartWalking,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Start Walking",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                Text(
                    text = "Computing optimal pedestrian path…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun ActiveManeuverBanner(
    modifier: Modifier = Modifier,
    maneuver: Maneuver,
    totalManeuvers: Int,
    currentIndex: Int,
    onNext: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF50F172A),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = Color(0x330284C7)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when (maneuver.type) {
                            ManeuverType.DEPART -> "🚩"
                            ManeuverType.CONTINUE -> "⬆"
                            ManeuverType.SLIGHT_LEFT -> "↖"
                            ManeuverType.LEFT, ManeuverType.SHARP_LEFT -> "⬅"
                            ManeuverType.SLIGHT_RIGHT -> "↗"
                            ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT -> "➡"
                            ManeuverType.STAIRS -> "🪜"
                            ManeuverType.CROSSING -> "🚶"
                            ManeuverType.ARRIVE -> "🎯"
                        },
                        fontSize = 22.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = maneuver.instruction,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (maneuver.distanceToNextM > 0) {
                    Text(
                        text = "in ${maneuver.distanceToNextM.toInt()} meters",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            FilledTonalButton(
                onClick = onNext,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B))
            ) {
                Text(
                    text = "${currentIndex + 1}/$totalManeuvers",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TripProgressCard(
    totalDistanceM: Double,
    totalCostS: Double,
    destinationName: String,
    isSimulating: Boolean,
    onToggleSimulate: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF20F172A),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val mins = (totalCostS / 60.0).coerceAtLeast(1.0).toInt()
                Text(
                    text = "$mins min • ${totalDistanceM.toInt()} m",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "To $destinationName",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onToggleSimulate,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isSimulating) Color(0xFF0369A1) else Color(0xFF1E293B)
                    )
                ) {
                    Text(
                        text = if (isSimulating) "⏸ Walk" else "▶ Demo",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("End", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
