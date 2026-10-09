package org.localar.navigator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.localar.core.geo.GeoPoint
import org.localar.core.geo.GeoUtils
import org.localar.core.routing.Maneuver
import org.localar.core.routing.ManeuverType
import org.localar.navigator.R
import org.localar.navigator.data.Place
import org.localar.navigator.location.LocationQuality
import org.localar.navigator.navigation.NavigationTab
import org.localar.navigator.navigation.NavigationViewModel
import org.localar.navigator.navigation.ScreenMode
import org.localar.navigator.ui.camera.CameraScannerView
import org.localar.navigator.ui.map.MapViewContainer
import kotlin.math.max

// Brand Colors (PinKita Palette)
private val BrandDeepTeal = Color(0xFF0F4C4C)
private val BrandForestGreen = Color(0xFF2F6B45)
private val BrandPineGreen = Color(0xFF3E7C59)
private val BrandSage = Color(0xFF9EB89A)
private val BrandAmber = Color(0xFFD89B2B)
private val BrandBgWarm = Color(0xFFF7F4EC)
private val BrandSurface = Color(0xFFFFFDF8)
private val BrandBorder = Color(0xFFDDD8CC)
private val BrandTextDark = Color(0xFF16302C)
private val BrandTextMuted = Color(0xFF4E5B57)
private val MintLight = Color(0xFFECFDF5)
private val MintBorder = Color(0xFFA7F3D0)
private val ActiveTabBg = Color(0xFFE2ECE0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndRouteScreen(
    viewModel: NavigationViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isSearchFocused by remember { mutableStateOf(false) }

    if (uiState.screenMode == ScreenMode.LANDING) {
        LandingScreen(
            onContinue = { viewModel.setScreenMode(ScreenMode.MAP) },
            onDownloadMaps = {
                viewModel.setScreenMode(ScreenMode.MAP)
                viewModel.showAttributionDialog(true)
            },
            onSignInLater = { viewModel.setScreenMode(ScreenMode.MAP) },
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize().background(BrandBgWarm)) {
        // 1. Primary Full-Bleed Viewport
        when (uiState.screenMode) {
            ScreenMode.MAP -> {
                val destinationPoint = uiState.selectedEntrance?.let {
                    GeoPoint(it.lat, it.lon)
                } ?: uiState.selectedPlace?.let {
                    GeoPoint(it.lat, it.lon)
                }
                MapViewContainer(
                    modifier = Modifier.fillMaxSize(),
                    currentLocation = uiState.currentLocation,
                    headingDeg = uiState.headingDeg,
                    allPathsGeoJson = uiState.allPathsGeoJson,
                    activeRoute = uiState.activeRoute,
                    destinationPoint = destinationPoint,
                    places = uiState.allPlaces,
                    selectedPlace = uiState.selectedPlace,
                    mapStyleIndex = uiState.mapStyleIndex,
                    recenterTrigger = uiState.recenterTrigger,
                    onPlaceSelected = { place ->
                        viewModel.onSelectPlace(place)
                    },
                    onMapClick = { clickedGeoPoint ->
                        viewModel.onMapLocationSelected(clickedGeoPoint)
                    }
                )
            }
            ScreenMode.AR_WALK -> {
                org.localar.navigator.ar.ArWalkView(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ScreenMode.CAMERA_SCANNER -> {
                CameraScannerView(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ScreenMode.LANDING -> {}
        }

        // 2. Top Header Controls (PinKita Brand + Offline Status Pill + Search Bar)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
        ) {
            // Brand Logo & Offline Active Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PinKita Logo & Tagline (tap to return to Welcome)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable { viewModel.setScreenMode(ScreenMode.LANDING) }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pinkita_mark),
                        contentDescription = "PinKita",
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "PinKita",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandTextDark
                        )
                        Text(
                            text = "REAL PLACES. CLOSER.",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandForestGreen,
                            letterSpacing = 1.1.sp
                        )
                    }
                }

                // Offline active pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MintLight,
                    border = BorderStroke(1.dp, MintBorder),
                    shadowElevation = 2.dp,
                    modifier = Modifier.clickable { viewModel.showAttributionDialog(true) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Wifi,
                            contentDescription = "Offline Active",
                            tint = BrandForestGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Offline active",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandForestGreen
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = BrandForestGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Floating Search Bar ("Search nearby places")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                border = BorderStroke(0.5.dp, Color(0x1F000000)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = BrandTextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = {
                            viewModel.onSearchQueryChanged(it)
                            isSearchFocused = true
                        },
                        placeholder = {
                            Text(
                                text = "Search nearby places",
                                color = Color(0xFF9CA3AF),
                                fontSize = 15.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = BrandTextDark,
                            unfocusedTextColor = BrandTextDark
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.onSearchQueryChanged("")
                                isSearchFocused = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = BrandTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Voice",
                            tint = BrandTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Category filter chips row (shown when search is active or non-empty)
            AnimatedVisibility(visible = isSearchFocused || uiState.searchQuery.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    val categories = listOf("All", "Café", "Park", "Museum", "Market", "Hotel")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val filterVal = if (cat == "All") null else cat.lowercase()
                            val isSelected = (uiState.selectedCategoryFilter == filterVal)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) BrandDeepTeal else Color.White,
                                border = BorderStroke(1.dp, if (isSelected) BrandDeepTeal else BrandBorder),
                                shadowElevation = 2.dp,
                                modifier = Modifier.clickable {
                                    viewModel.selectCategoryFilter(filterVal)
                                }
                            ) {
                                Text(
                                    text = cat,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color.White else BrandTextDark
                                )
                            }
                        }
                    }

                    // Search Results List Dropdown
                    if (uiState.searchResults.isNotEmpty() && uiState.searchQuery.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .heightIn(max = 240.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(uiState.searchResults) { place ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                viewModel.onSelectPlace(place)
                                                isSearchFocused = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = place.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = BrandTextDark
                                            )
                                            Text(
                                                text = place.category.replaceFirstChar { it.uppercase() },
                                                fontSize = 12.sp,
                                                color = BrandTextMuted
                                            )
                                        }
                                        val distM = GeoUtils.distanceMeters(uiState.currentLocation, GeoPoint(place.lat, place.lon))
                                        Text(
                                            text = formatDistance(distM),
                                            fontSize = 12.sp,
                                            color = BrandPineGreen,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Right-side Floating Action Controls (Layers, Recenter, Download)
        if (uiState.screenMode == ScreenMode.MAP) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 115.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Style Layers button
                FloatingMapButton(
                    icon = Icons.Rounded.Layers,
                    contentDescription = "Map Style",
                    onClick = { viewModel.cycleMapStyle() }
                )

                // Recenter GPS button
                FloatingMapButton(
                    icon = Icons.Rounded.NearMe,
                    contentDescription = "Recenter",
                    onClick = { viewModel.recenterMap() }
                )

                // Download Area Pack button
                FloatingMapButton(
                    icon = Icons.Rounded.FileDownload,
                    contentDescription = "Download Maps",
                    onClick = { viewModel.showAttributionDialog(true) }
                )
            }
        }

        // 4. Floating Callout Card above selected place on map
        if (uiState.screenMode == ScreenMode.MAP && uiState.selectedPlace != null && !uiState.isNavigating) {
            val place = uiState.selectedPlace!!
            val distM = uiState.activeRoute?.totalDistanceM
                ?: GeoUtils.distanceMeters(uiState.currentLocation, GeoPoint(place.lat, place.lon))
            val etaMin = max(1, (distM / 78.0).toInt())

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0x1F000000)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-70).dp)
                    .clickable { viewModel.onStartNavigation() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = BrandAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = place.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BrandTextDark
                        )
                        Text(
                            text = "${formatDistance(distM)} • $etaMin min",
                            fontSize = 11.sp,
                            color = BrandTextMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = BrandTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 5. Floating Mode Switch Pill ([ Map ] [ AR ]) above bottom sheet
        if (!uiState.isNavigating) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, BrandBorder),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (uiState.selectedPlace != null) 345.dp else 95.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Map Mode Button
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (uiState.screenMode == ScreenMode.MAP) BrandDeepTeal else Color.Transparent,
                        modifier = Modifier.clickable { viewModel.setScreenMode(ScreenMode.MAP) }
                    ) {
                        Text(
                            text = "Map",
                            color = if (uiState.screenMode == ScreenMode.MAP) Color.White else BrandTextDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // AR Mode Button
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (uiState.screenMode != ScreenMode.MAP) BrandDeepTeal else Color.Transparent,
                        modifier = Modifier.clickable {
                            viewModel.setScreenMode(if (uiState.activeRoute != null) ScreenMode.AR_WALK else ScreenMode.CAMERA_SCANNER)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusStrong,
                                contentDescription = "AR",
                                tint = if (uiState.screenMode != ScreenMode.MAP) Color.White else BrandTextDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "AR",
                                color = if (uiState.screenMode != ScreenMode.MAP) Color.White else BrandTextDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // 6. Bottom Place Detail Sheet (Exact Mockup Layout)
        AnimatedVisibility(
            visible = uiState.selectedPlace != null && !uiState.isNavigating,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            uiState.selectedPlace?.let { place ->
                val distM = uiState.activeRoute?.totalDistanceM
                    ?: GeoUtils.distanceMeters(uiState.currentLocation, GeoPoint(place.lat, place.lon))
                val etaMin = max(1, (distM / 78.0).toInt())

                PlaceDetailSheet(
                    place = place,
                    distM = distM,
                    etaMin = etaMin,
                    onSaveToggle = { viewModel.toggleSavePlace(place) },
                    onShare = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out ${place.name} (${place.category}) on PinKita! Coordinates: ${place.lat}, ${place.lon}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share place"))
                    },
                    onMoreInfo = { viewModel.showAttributionDialog(true) },
                    onStartNavigation = { viewModel.onStartNavigation() },
                    onStartAr = { viewModel.startArWithSelectedPlace() },
                    modifier = Modifier.padding(bottom = 72.dp) // Space for bottom bar
                )
            }
        }

        // 7. Active Navigation Turn-by-Turn Panel (Replaces tabs when active)
        AnimatedVisibility(
            visible = uiState.isNavigating,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            uiState.activeRoute?.let { route ->
                val maneuvers = route.maneuvers
                val currentManeuver = maneuvers.getOrNull(uiState.currentManeuverIndex)
                NavigationGuidanceCard(
                    maneuver = currentManeuver,
                    totalManeuvers = maneuvers.size,
                    currentIndex = uiState.currentManeuverIndex,
                    totalDistanceM = route.totalDistanceM,
                    totalCostS = route.totalCostS,
                    destinationName = uiState.selectedPlace?.name ?: "Destination",
                    isSimulating = uiState.isSimulatingWalk,
                    isArrived = uiState.isArrived,
                    onNext = { viewModel.onNextManeuver() },
                    onToggleSimulate = { viewModel.toggleSimulatedWalk() },
                    onStop = { viewModel.onStopNavigation() }
                )
            }
        }

        // 8. Persistent 4-Tab Bottom Navigation Bar (Hidden during active navigation)
        if (!uiState.isNavigating) {
            Surface(
                color = BrandSurface,
                shadowElevation = 16.dp,
                border = BorderStroke(0.5.dp, BrandBorder),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomBarItem(
                        icon = Icons.Rounded.Map,
                        label = "Map",
                        isSelected = uiState.currentTab == NavigationTab.MAP,
                        onClick = { viewModel.selectTab(NavigationTab.MAP) }
                    )
                    BottomBarItem(
                        icon = Icons.Rounded.CenterFocusStrong,
                        label = "AR",
                        isSelected = uiState.currentTab == NavigationTab.AR,
                        onClick = { viewModel.selectTab(NavigationTab.AR) }
                    )
                    BottomBarItem(
                        icon = Icons.Rounded.BookmarkBorder,
                        label = "Saved",
                        isSelected = uiState.currentTab == NavigationTab.SAVED,
                        onClick = { viewModel.selectTab(NavigationTab.SAVED) }
                    )
                    BottomBarItem(
                        icon = Icons.Rounded.Settings,
                        label = "Settings",
                        isSelected = uiState.currentTab == NavigationTab.SETTINGS,
                        onClick = { viewModel.selectTab(NavigationTab.SETTINGS) }
                    )
                }
            }
        }

        // 9. Saved Places Modal Dialog
        if (uiState.isSavedSheetVisible) {
            SavedPlacesDialog(
                savedPlaces = uiState.savedPlaces,
                onSelectPlace = { place ->
                    viewModel.onSelectPlace(place)
                    viewModel.dismissSheets()
                },
                onRemovePlace = { place ->
                    viewModel.toggleSavePlace(place)
                },
                onDismiss = { viewModel.dismissSheets() }
            )
        }

        // 10. Settings & Offline Area Pack Dialog
        if (uiState.isSettingsSheetVisible || uiState.attributionDialogVisible) {
            SettingsAndPackDialog(
                districtName = uiState.districtName,
                totalPlaces = uiState.allPlaces.size,
                isDiagnostics = uiState.diagnosticsVisible,
                onToggleDiagnostics = { viewModel.toggleDiagnostics() },
                onDismiss = {
                    viewModel.dismissSheets()
                    viewModel.showAttributionDialog(false)
                }
            )
        }
    }
}

@Composable
private fun FloatingMapButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        border = BorderStroke(0.5.dp, Color(0x1F000000)),
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = BrandTextDark,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PlaceDetailSheet(
    place: Place,
    distM: Double,
    etaMin: Int,
    onSaveToggle: () -> Unit,
    onShare: () -> Unit,
    onMoreInfo: () -> Unit,
    onStartNavigation: () -> Unit,
    onStartAr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color.White,
        shadowElevation = 16.dp,
        border = BorderStroke(0.5.dp, BrandBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD1D5DB))
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Place Header Row (Thumbnail on left, details on right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Image thumbnail
                Image(
                    painter = painterResource(id = R.drawable.thumb_cafe_scenic),
                    contentDescription = place.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = place.name,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandTextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Details",
                            tint = BrandTextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onMoreInfo() }
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${place.category.replaceFirstChar { it.uppercase() }} • ${place.city ?: place.address ?: "Taal, Philippines"}",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metrics badges row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = BrandForestGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = formatDistance(distM),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandTextDark
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = BrandForestGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$etaMin min",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandTextDark
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Landscape,
                                contentDescription = null,
                                tint = BrandForestGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = place.tagFeature ?: "Scenic view",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandTextDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Pills Row (Save, Share, More info)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Save Button
                OutlinedButton(
                    onClick = onSaveToggle,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = BrandSurface),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (place.isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                        tint = if (place.isSaved) BrandAmber else BrandTextDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (place.isSaved) "Saved" else "Save",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrandTextDark
                    )
                }

                // Share Button
                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = BrandSurface),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        tint = BrandTextDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Share",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrandTextDark
                    )
                }

                // More info Button
                OutlinedButton(
                    onClick = onMoreInfo,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = BrandSurface),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = BrandTextDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "More info",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrandTextDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Buttons (Start navigation & Start AR)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "Start navigation" button (Deep teal with 2 lines)
                Button(
                    onClick = onStartNavigation,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandDeepTeal),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1.6f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NearMe,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Start navigation",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${formatDistance(distM)} • $etaMin min • Offline route",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }

                // "Start AR" button (White with teal border)
                OutlinedButton(
                    onClick = onStartAr,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, BrandDeepTeal),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1.0f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = null,
                            tint = BrandDeepTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Start AR",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandDeepTeal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) ActiveTabBg else Color.Transparent,
            modifier = Modifier.size(width = 56.dp, height = 30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) BrandDeepTeal else Color(0xFF6B7280),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) BrandDeepTeal else Color(0xFF6B7280)
        )
    }
}

@Composable
private fun NavigationGuidanceCard(
    maneuver: Maneuver?,
    totalManeuvers: Int,
    currentIndex: Int,
    totalDistanceM: Double,
    totalCostS: Double,
    destinationName: String,
    isSimulating: Boolean,
    isArrived: Boolean,
    onNext: () -> Unit,
    onToggleSimulate: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = BrandDeepTeal,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (isArrived) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "You have arrived!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Near $destinationName",
                            fontSize = 13.sp,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Finish", color = BrandDeepTeal, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val maneuverIcon = when (maneuver?.type) {
                        ManeuverType.DEPART, ManeuverType.CONTINUE -> Icons.Rounded.NearMe
                        ManeuverType.SLIGHT_LEFT, ManeuverType.LEFT, ManeuverType.SHARP_LEFT -> Icons.Rounded.TurnLeft
                        ManeuverType.SLIGHT_RIGHT, ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT -> Icons.Rounded.TurnRight
                        ManeuverType.ARRIVE -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.DirectionsWalk
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color(0x33FFFFFF),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = maneuverIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = maneuver?.instruction ?: "Proceed along route",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        val etaMin = max(1, (totalCostS / 60.0).toInt())
                        Text(
                            text = "${formatDistance(totalDistanceM)} • $etaMin min left",
                            fontSize = 12.sp,
                            color = Color(0xFFA7F3D0)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = onToggleSimulate,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSimulating) BrandAmber else Color(0x33FFFFFF)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isSimulating) "Sim" else "Demo",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Exit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedPlacesDialog(
    savedPlaces: List<Place>,
    onSelectPlace: (Place) -> Unit,
    onRemovePlace: (Place) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Bookmark, contentDescription = null, tint = BrandDeepTeal)
                Text("Saved Offline Places", fontWeight = FontWeight.Bold, color = BrandTextDark)
            }
        },
        text = {
            if (savedPlaces.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No saved places yet.",
                        fontWeight = FontWeight.SemiBold,
                        color = BrandTextDark
                    )
                    Text(
                        text = "Tap the Save button on any place card to access it offline.",
                        fontSize = 12.sp,
                        color = BrandTextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedPlaces) { place ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BrandSurface,
                            border = BorderStroke(1.dp, BrandBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlace(place) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = BrandTextDark
                                    )
                                    Text(
                                        text = "${place.category} • ${place.city ?: "Local area"}",
                                        fontSize = 12.sp,
                                        color = BrandTextMuted
                                    )
                                }
                                IconButton(onClick = { onRemovePlace(place) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold, color = BrandDeepTeal)
            }
        }
    )
}

@Composable
private fun SettingsAndPackDialog(
    districtName: String,
    totalPlaces: Int,
    isDiagnostics: Boolean,
    onToggleDiagnostics: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Settings, contentDescription = null, tint = BrandDeepTeal)
                Text("PinKita Offline Settings", fontWeight = FontWeight.Bold, color = BrandTextDark)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Pack Status
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MintLight,
                    border = BorderStroke(1.dp, MintBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, tint = BrandForestGreen, modifier = Modifier.size(18.dp))
                            Text("Offline Area Active", fontWeight = FontWeight.Bold, color = BrandForestGreen, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Area: $districtName", fontSize = 13.sp, color = BrandTextDark)
                        Text("Indexed landmarks: $totalPlaces places", fontSize = 12.sp, color = BrandTextMuted)
                        Text("Zero network data used for search & routing", fontSize = 11.sp, color = BrandForestGreen)
                    }
                }

                // Diagnostics switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Technical Diagnostics", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = BrandTextDark)
                        Text("Show GPS accuracy & AI latency", fontSize = 12.sp, color = BrandTextMuted)
                    }
                    Switch(
                        checked = isDiagnostics,
                        onCheckedChange = { onToggleDiagnostics() }
                    )
                }

                // Legal OSM Attribution
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))
                        context.startActivity(browserIntent)
                    }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Map Data: © OpenStreetMap contributors",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTextDark
                        )
                        Text(
                            text = "Open Database License (ODbL) • Tap to view license",
                            fontSize = 11.sp,
                            color = BrandDeepTeal
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BrandDeepTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done", color = Color.White)
            }
        }
    )
}

private fun formatDistance(distM: Double): String {
    return if (distM >= 1000.0) {
        String.format("%.1f km", distM / 1000.0)
    } else {
        "${distM.toInt()} m"
    }
}
