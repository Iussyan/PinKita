package org.localar.navigator.navigation

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.localar.core.geo.GeoPoint
import org.localar.core.geo.GeoUtils
import org.localar.core.recognition.LandmarkMatcher
import org.localar.core.recognition.RecognitionStatus
import org.localar.core.routing.AStarRouter
import org.localar.core.routing.PedestrianGraph
import org.localar.core.routing.RoutePlan
import org.localar.core.routing.RouteResult
import org.localar.core.routing.StartSnapper
import org.localar.navigator.data.DistrictDatabase
import org.localar.navigator.data.Place
import org.localar.navigator.data.PlaceEntrance
import org.localar.navigator.data.SavedPlacesRepository
import org.localar.navigator.location.LocationFix
import org.localar.navigator.location.LocationProvider
import org.localar.navigator.location.LocationQuality
import org.localar.navigator.packs.DistrictPackManager
import org.localar.navigator.vision.MediaPipeEmbedder
import org.localar.navigator.vision.PackEmbeddingLoader
import java.io.File
import kotlin.math.max

enum class ScreenMode {
    LANDING,
    MAP,
    CAMERA_SCANNER,
    AR_WALK
}

enum class NavigationTab {
    MAP,
    AR,
    SAVED,
    SETTINGS
}

data class ScanUiState(
    val status: RecognitionStatus = RecognitionStatus.IDLE,
    val landmarkName: String? = null,
    val bestScore: Float = 0f,
    val margin: Float = 0f,
    val isPromoted: Boolean = false
)

data class NavigationUiState(
    val isInitialized: Boolean = false,
    val screenMode: ScreenMode = ScreenMode.LANDING,
    val currentTab: NavigationTab = NavigationTab.MAP,
    val statusMessage: String? = null,
    val districtName: String = "Quezon City San Bartolome",
    val currentLocation: GeoPoint = GeoPoint(14.6630, 121.0125), // QCU San Bartolome area
    val headingDeg: Float = 0f,
    val targetBearingDeg: Float? = null,
    val lookaheadPoints: List<GeoPoint> = emptyList(),
    val locationQuality: LocationQuality = LocationQuality.MISSING,
    val locationAccuracyM: Float = 0f,
    val isSimulatingWalk: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val allPlaces: List<Place> = emptyList(),
    val savedPlaces: List<Place> = emptyList(),
    val selectedCategoryFilter: String? = null,
    val selectedPlace: Place? = null,
    val selectedEntrance: PlaceEntrance? = null,
    val activeRoute: RoutePlan? = null,
    val isNavigating: Boolean = false,
    val isArrived: Boolean = false,
    val currentManeuverIndex: Int = 0,
    val allPathsGeoJson: String = "",
    val inferenceLatencyMs: Long = 0L,
    val diagnosticsVisible: Boolean = false,
    val attributionDialogVisible: Boolean = false,
    val isSavedSheetVisible: Boolean = false,
    val isSettingsSheetVisible: Boolean = false,
    val mapStyleIndex: Int = 0,
    val recenterTrigger: Long = 0L,
    val isIndoorAnchored: Boolean = false,
    val indoorAnchorName: String? = null,
    val scanState: ScanUiState = ScanUiState()
)

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private val locationProvider = LocationProvider(application)
    private val headingProvider = org.localar.navigator.location.HeadingProvider(application)
    private val savedPlacesRepo = SavedPlacesRepository(application)
    private var districtDatabase: DistrictDatabase? = null
    private var pedestrianGraph: PedestrianGraph? = null
    private var landmarkMatcher: LandmarkMatcher? = null
    private var mediaPipeEmbedder: MediaPipeEmbedder? = null
    private var simulationJob: Job? = null

    // Tracking off-route state
    private var offRouteCount = 0
    private var firstOffRouteTimeMs = 0L
    private var lastRerouteTimeMs = 0L

    // Tracking arrival state
    private var nearEntranceCount = 0

    init {
        loadDistrictPack()
        observeLocationUpdates()
        observeHeadingUpdates()
        initMediaPipe()
    }

    private fun observeHeadingUpdates() {
        headingProvider.startListening()
        viewModelScope.launch {
            headingProvider.headingDeg.collect { h ->
                _uiState.value = _uiState.value.copy(headingDeg = h)
            }
        }
    }

    private fun initMediaPipe() {
        try {
            mediaPipeEmbedder = MediaPipeEmbedder(getApplication())
        } catch (_: Exception) {}
    }

    fun setScreenMode(mode: ScreenMode) {
        _uiState.value = _uiState.value.copy(
            screenMode = mode,
            currentTab = when (mode) {
                ScreenMode.MAP -> NavigationTab.MAP
                ScreenMode.AR_WALK, ScreenMode.CAMERA_SCANNER -> NavigationTab.AR
                else -> _uiState.value.currentTab
            }
        )
    }

    private fun loadDistrictPack() {
        viewModelScope.launch(Dispatchers.IO) {
            val packManager = DistrictPackManager(getApplication())
            val packResult = packManager.ensurePackExtracted()

            packResult.fold(
                onSuccess = { packDir ->
                    try {
                        val dbFile = File(packDir, "data/region.sqlite")
                        val db = DistrictDatabase(dbFile)
                        districtDatabase = db

                        val metadata = db.getMetadata()
                        val districtName = metadata["district_name"] ?: "Pedestrian District"
                        val graph = db.loadRoutingGraph()
                        pedestrianGraph = graph

                        val allPathsJson = db.getAllPathsGeoJson()
                        val initialPlaces = db.getAllPlaces(300).ifEmpty { db.searchPlaces("") }
                        val saved = savedPlacesRepo.getAllSavedPlaces()
                        val savedIds = saved.map { it.id }.toSet()
                        val allWithSaved = initialPlaces.map {
                            if (savedIds.contains(it.id)) it.copy(isSaved = true) else it
                        }

                        // Load AI embeddings
                        val aiDir = File(packDir, "ai")
                        landmarkMatcher = PackEmbeddingLoader.loadMatcher(aiDir)

                        _uiState.value = _uiState.value.copy(
                            isInitialized = true,
                            districtName = districtName,
                            allPathsGeoJson = allPathsJson,
                            allPlaces = allWithSaved,
                            searchResults = allWithSaved,
                            savedPlaces = saved,
                            statusMessage = "Pack loaded: $districtName"
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Error loading database: ${e.message}"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Failed to unpack district: ${err.message}"
                    )
                }
            )
        }
    }

    private fun observeLocationUpdates() {
        viewModelScope.launch {
            locationProvider.currentFix.collect { fix ->
                if (fix != null && !_uiState.value.isSimulatingWalk) {
                    handleNewLocationFix(fix)
                }
            }
        }
    }

    private fun handleNewLocationFix(fix: LocationFix) {
        _uiState.value = _uiState.value.copy(
            currentLocation = fix.point,
            locationQuality = fix.quality,
            locationAccuracyM = fix.accuracyM
        )

        if (_uiState.value.isNavigating && _uiState.value.activeRoute != null) {
            evaluateRouteProgress(fix.point, fix.accuracyM)
        }
    }

    private fun evaluateRouteProgress(currentPos: GeoPoint, accuracyM: Float) {
        val route = _uiState.value.activeRoute ?: return
        val entrance = _uiState.value.selectedEntrance

        // 1. Check Arrival Proximity (Section 11.5 of spec.md)
        if (entrance != null) {
            val entrancePoint = GeoPoint(entrance.lat, entrance.lon)
            val distToEntrance = GeoUtils.distanceMeters(currentPos, entrancePoint)
            if (distToEntrance <= 15.0 && accuracyM <= 10.0f) {
                nearEntranceCount++
                if (nearEntranceCount >= 3) {
                    _uiState.value = _uiState.value.copy(
                        isArrived = true,
                        statusMessage = "You have arrived near ${entrance.name ?: "the entrance"}!"
                    )
                    return
                }
            } else {
                nearEntranceCount = 0
            }
        }

        // 2. Check Maneuver Advance (within 8m of current maneuver)
        val maneuvers = route.maneuvers
        var activeIdx = _uiState.value.currentManeuverIndex
        if (activeIdx < maneuvers.size) {
            val targetManeuver = maneuvers[activeIdx]
            val distToManeuver = GeoUtils.distanceMeters(currentPos, targetManeuver.point)
            if (distToManeuver <= 8.0 && activeIdx + 1 < maneuvers.size) {
                activeIdx += 1
                _uiState.value = _uiState.value.copy(currentManeuverIndex = activeIdx)
            }
        }

        // Calculate Target Bearing to next waypoint
        val activeTarget = maneuvers.getOrNull(activeIdx)
        val targetBearing = activeTarget?.let {
            GeoUtils.bearingDegrees(currentPos, it.point).toFloat()
        }

        // Sample 2-6m lookahead window along route
        val lookahead = mutableListOf<GeoPoint>()
        for (pt in route.polyline) {
            val d = GeoUtils.distanceMeters(currentPos, pt)
            if (d in 1.5..8.0) {
                lookahead.add(pt)
            }
        }

        _uiState.value = _uiState.value.copy(
            targetBearingDeg = targetBearing,
            lookaheadPoints = lookahead
        )

        // 3. Sustained Off-Route Check (Section 11.4 of spec.md)
        val polyline = route.polyline
        var minCrossTrack = Double.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val p1 = polyline[i]
            val p2 = polyline[i + 1]
            val (_, dist, _) = StartSnapper.projectPointToSegment(currentPos, p1, p2)
            if (dist < minCrossTrack) {
                minCrossTrack = dist
            }
        }

        val maxAllowedDeviation = max(20.0, 1.5 * accuracyM.toDouble())
        val nowMs = SystemClock.elapsedRealtime()

        if (minCrossTrack > maxAllowedDeviation) {
            if (offRouteCount == 0) {
                firstOffRouteTimeMs = nowMs
            }
            offRouteCount++

            val spanDuration = nowMs - firstOffRouteTimeMs
            val timeSinceLastReroute = nowMs - lastRerouteTimeMs

            // 3 consecutive fixes spanning >= 5s with 10s cooldown
            if (offRouteCount >= 3 && spanDuration >= 5000L && timeSinceLastReroute >= 10000L) {
                lastRerouteTimeMs = nowMs
                offRouteCount = 0
                triggerReroute(currentPos)
            }
        } else {
            offRouteCount = 0
        }
    }

    private fun triggerReroute(currentPos: GeoPoint) {
        val entrance = _uiState.value.selectedEntrance ?: return
        val graph = pedestrianGraph ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val snapped = StartSnapper.snapToGraph(currentPos, graph)
            if (snapped != null) {
                val router = AStarRouter(snapped.modifiedGraph)
                val result = router.calculateRoute(snapped.virtualNodeId, entrance.graphNodeId)
                if (result is RouteResult.Success) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            activeRoute = result.plan,
                            currentManeuverIndex = 0,
                            statusMessage = "Rerouted: ${result.plan.totalDistanceM.toInt()}m"
                        )
                    }
                }
            }
        }
    }

    fun startListeningGps() {
        locationProvider.startListening()
    }

    fun stopListeningGps() {
        locationProvider.stopListening()
    }

    fun toggleSimulatedWalk() {
        if (_uiState.value.isSimulatingWalk) {
            simulationJob?.cancel()
            _uiState.value = _uiState.value.copy(isSimulatingWalk = false)
        } else {
            val route = _uiState.value.activeRoute ?: return
            val polyline = route.polyline
            if (polyline.size < 2) return

            _uiState.value = _uiState.value.copy(isSimulatingWalk = true, isNavigating = true)
            simulationJob = viewModelScope.launch(Dispatchers.Default) {
                // Step-by-step walk simulation along route polyline at ~1.3 m/s
                for (i in 0 until polyline.size - 1) {
                    val p1 = polyline[i]
                    val p2 = polyline[i + 1]
                    val segDist = GeoUtils.distanceMeters(p1, p2)
                    val steps = max(1, (segDist / 1.3).toInt())

                    for (step in 0..steps) {
                        val fraction = step.toDouble() / steps
                        val currentLat = p1.lat + (p2.lat - p1.lat) * fraction
                        val currentLon = p1.lon + (p2.lon - p1.lon) * fraction
                        val simulatedPos = GeoPoint(currentLat, currentLon)

                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                currentLocation = simulatedPos,
                                locationQuality = LocationQuality.GOOD,
                                locationAccuracyM = 3.0f
                            )
                            evaluateRouteProgress(simulatedPos, 3.0f)
                        }
                        delay(1000L) // 1 Hz update
                    }
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isSimulatingWalk = false,
                        isArrived = true,
                        statusMessage = "Walk simulation completed! Arrived at destination."
                    )
                }
            }
        }
    }

    fun processCameraFrame(bitmap: Bitmap) {
        val embedder = mediaPipeEmbedder ?: return
        val matcher = landmarkMatcher ?: return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val startMs = SystemClock.elapsedRealtime()
                val embedding = embedder.embed(bitmap)
                val decision = matcher.processFrame(embedding, SystemClock.elapsedRealtime())
                val latencyMs = SystemClock.elapsedRealtime() - startMs

                val landmarkName = decision.recognizedLandmarkId?.let { id ->
                    id.removePrefix("landmark-").split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        inferenceLatencyMs = latencyMs,
                        scanState = ScanUiState(
                            status = decision.status,
                            landmarkName = landmarkName,
                            bestScore = decision.bestScore,
                            margin = decision.margin,
                            isPromoted = decision.status == RecognitionStatus.IDENTIFIED
                        )
                    )

                    // Indoor Visual Relocalization: snap user position when high-confidence landmark is recognized
                    if (decision.status == RecognitionStatus.IDENTIFIED || decision.bestScore >= 0.78f) {
                        val recId = decision.recognizedLandmarkId
                        if (recId != null) {
                            val lm = districtDatabase?.getLandmarkById(recId)
                            if (lm != null) {
                                relocalizeToLandmark(lm.id, lm.displayName, GeoPoint(lm.lat, lm.lon))
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun relocalizeToLandmark(landmarkId: String, name: String, point: GeoPoint) {
        _uiState.value = _uiState.value.copy(
            currentLocation = point,
            locationQuality = LocationQuality.GOOD,
            locationAccuracyM = 0.8f,
            isIndoorAnchored = true,
            indoorAnchorName = name,
            statusMessage = "Indoor Visual Anchor: $name"
        )
        if (_uiState.value.isNavigating && _uiState.value.activeRoute != null) {
            evaluateRouteProgress(point, 0.8f)
        }
    }

    /**
     * Continuous Indoor Dead-Reckoning: ARCore visual-inertial odometry updates the
     * user's position using real-world camera translation meters without requiring GPS.
     */
    fun updateIndoorArPosition(point: GeoPoint) {
        val isGpsWeak = _uiState.value.locationQuality == LocationQuality.MISSING ||
                        _uiState.value.locationQuality == LocationQuality.POOR ||
                        _uiState.value.locationAccuracyM > 15f
        if (_uiState.value.isIndoorAnchored || isGpsWeak) {
            _uiState.value = _uiState.value.copy(
                currentLocation = point,
                locationQuality = LocationQuality.GOOD,
                locationAccuracyM = 1.0f
            )
            if (_uiState.value.isNavigating && _uiState.value.activeRoute != null) {
                evaluateRouteProgress(point, 1.0f)
            }
        }
    }

    fun toggleDiagnostics() {
        _uiState.value = _uiState.value.copy(diagnosticsVisible = !_uiState.value.diagnosticsVisible)
    }

    fun showAttributionDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(attributionDialogVisible = show)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        viewModelScope.launch(Dispatchers.IO) {
            val db = districtDatabase ?: return@launch
            val results = if (query.isBlank() && _uiState.value.selectedCategoryFilter == null) {
                _uiState.value.allPlaces
            } else {
                db.searchPlaces(query, _uiState.value.selectedCategoryFilter)
            }
            val savedIds = _uiState.value.savedPlaces.map { it.id }.toSet()
            val mapped = results.map { if (savedIds.contains(it.id)) it.copy(isSaved = true) else it }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(searchResults = mapped)
            }
        }
    }

    fun selectCategoryFilter(category: String?) {
        val nextCategory = if (_uiState.value.selectedCategoryFilter == category) null else category
        _uiState.value = _uiState.value.copy(selectedCategoryFilter = nextCategory)
        viewModelScope.launch(Dispatchers.IO) {
            val db = districtDatabase ?: return@launch
            val results = db.searchPlaces(_uiState.value.searchQuery, nextCategory)
            val savedIds = _uiState.value.savedPlaces.map { it.id }.toSet()
            val mapped = results.map { if (savedIds.contains(it.id)) it.copy(isSaved = true) else it }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(searchResults = mapped)
            }
        }
    }

    fun toggleSavePlace(place: Place) {
        viewModelScope.launch(Dispatchers.IO) {
            val isNowSaved = !savedPlacesRepo.isPlaceSaved(place.id)
            if (isNowSaved) {
                savedPlacesRepo.savePlace(place.copy(isSaved = true))
            } else {
                savedPlacesRepo.unsavePlace(place.id)
            }
            val updatedSaved = savedPlacesRepo.getAllSavedPlaces()
            val savedIds = updatedSaved.map { it.id }.toSet()
            withContext(Dispatchers.Main) {
                val updatedAll = _uiState.value.allPlaces.map {
                    if (savedIds.contains(it.id)) it.copy(isSaved = true) else it.copy(isSaved = false)
                }
                val updatedSelected = if (_uiState.value.selectedPlace?.id == place.id) {
                    _uiState.value.selectedPlace?.copy(isSaved = isNowSaved)
                } else {
                    _uiState.value.selectedPlace
                }
                val updatedSearch = _uiState.value.searchResults.map {
                    if (savedIds.contains(it.id)) it.copy(isSaved = true) else it.copy(isSaved = false)
                }
                _uiState.value = _uiState.value.copy(
                    savedPlaces = updatedSaved,
                    allPlaces = updatedAll,
                    searchResults = updatedSearch,
                    selectedPlace = updatedSelected,
                    statusMessage = if (isNowSaved) "${place.name} saved offline" else "${place.name} removed from saved"
                )
            }
        }
    }

    fun selectTab(tab: NavigationTab) {
        when (tab) {
            NavigationTab.MAP -> {
                _uiState.value = _uiState.value.copy(
                    currentTab = NavigationTab.MAP,
                    screenMode = ScreenMode.MAP,
                    isSavedSheetVisible = false,
                    isSettingsSheetVisible = false
                )
            }
            NavigationTab.AR -> {
                val hasRoute = _uiState.value.activeRoute != null
                _uiState.value = _uiState.value.copy(
                    currentTab = NavigationTab.AR,
                    screenMode = if (_uiState.value.isNavigating && hasRoute) ScreenMode.AR_WALK else ScreenMode.CAMERA_SCANNER,
                    isSavedSheetVisible = false,
                    isSettingsSheetVisible = false
                )
            }
            NavigationTab.SAVED -> {
                _uiState.value = _uiState.value.copy(
                    currentTab = NavigationTab.SAVED,
                    isSavedSheetVisible = true,
                    isSettingsSheetVisible = false
                )
            }
            NavigationTab.SETTINGS -> {
                _uiState.value = _uiState.value.copy(
                    currentTab = NavigationTab.SETTINGS,
                    isSettingsSheetVisible = true,
                    isSavedSheetVisible = false
                )
            }
        }
    }

    fun dismissSheets() {
        _uiState.value = _uiState.value.copy(
            isSavedSheetVisible = false,
            isSettingsSheetVisible = false,
            currentTab = if (_uiState.value.screenMode == ScreenMode.MAP) NavigationTab.MAP else NavigationTab.AR
        )
    }

    fun cycleMapStyle() {
        val next = (_uiState.value.mapStyleIndex + 1) % 3
        _uiState.value = _uiState.value.copy(mapStyleIndex = next)
    }

    fun recenterMap() {
        _uiState.value = _uiState.value.copy(recenterTrigger = System.currentTimeMillis())
    }

    fun startArWithSelectedPlace() {
        val hasRoute = _uiState.value.activeRoute != null
        _uiState.value = _uiState.value.copy(
            currentTab = NavigationTab.AR,
            screenMode = if (hasRoute) ScreenMode.AR_WALK else ScreenMode.CAMERA_SCANNER,
            isNavigating = hasRoute
        )
        if (hasRoute) {
            locationProvider.startListening()
        }
    }

    fun onSelectPlace(place: Place) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = districtDatabase ?: return@launch
            val graph = pedestrianGraph ?: return@launch
            val entrances = db.getEntrancesForPlace(place.id)
            val primaryEntrance = entrances.firstOrNull { it.reviewed } ?: entrances.firstOrNull() ?: run {
                // Fallback virtual entrance snapped to nearest pedestrian graph node
                val placePoint = GeoPoint(place.lat, place.lon)
                val closestNode = graph.nodes.values.minByOrNull {
                    GeoUtils.distanceMeters(placePoint, it.point)
                }
                if (closestNode != null) {
                    PlaceEntrance(
                        id = "virtual-ent-${place.id}",
                        placeId = place.id,
                        name = place.name,
                        lat = place.lat,
                        lon = place.lon,
                        graphNodeId = closestNode.id,
                        accessStatus = "public",
                        reviewed = true
                    )
                } else null
            }

            if (primaryEntrance == null) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        selectedPlace = place,
                        selectedEntrance = null,
                        activeRoute = null,
                        statusMessage = "Area outside walking routing network."
                    )
                }
                return@launch
            }

            val userLocation = _uiState.value.currentLocation
            val snapped = StartSnapper.snapToGraph(userLocation, graph)

            val routeResult = if (snapped != null) {
                val router = AStarRouter(snapped.modifiedGraph)
                router.calculateRoute(snapped.virtualNodeId, primaryEntrance.graphNodeId)
            } else {
                val nearestNode = graph.nodes.values.minByOrNull { 
                    GeoUtils.distanceMeters(userLocation, it.point)
                }
                if (nearestNode != null) {
                    val router = AStarRouter(graph)
                    router.calculateRoute(nearestNode.id, primaryEntrance.graphNodeId)
                } else {
                    RouteResult.NoPath
                }
            }

            withContext(Dispatchers.Main) {
                when (routeResult) {
                    is RouteResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            selectedPlace = place,
                            selectedEntrance = primaryEntrance,
                            activeRoute = routeResult.plan,
                            currentManeuverIndex = 0,
                            isArrived = false,
                            statusMessage = "Route computed: ${routeResult.plan.totalDistanceM.toInt()}m"
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            selectedPlace = place,
                            selectedEntrance = primaryEntrance,
                            activeRoute = null,
                            isArrived = false,
                            statusMessage = "No walkable route found."
                        )
                    }
                }
            }
        }
    }

    /**
     * User clicks any point on the map to seek shortest pedestrian route
     */
    fun onMapLocationSelected(dest: GeoPoint) {
        viewModelScope.launch(Dispatchers.IO) {
            val graph = pedestrianGraph ?: return@launch
            val userLocation = _uiState.value.currentLocation

            val destDistanceToGraph = graph.nodes.values.minOfOrNull {
                GeoUtils.distanceMeters(dest, it.point)
            } ?: Double.MAX_VALUE

            // If the destination is reasonably near the local graph (within 1.5km), route via graph
            if (destDistanceToGraph < 1500.0) {
                val startSnapped = StartSnapper.snapToGraph(userLocation, graph)
                val startNodeId = startSnapped?.virtualNodeId ?: graph.nodes.values.minByOrNull {
                    GeoUtils.distanceMeters(userLocation, it.point)
                }?.id

                val targetGraph = startSnapped?.modifiedGraph ?: graph
                val destNode = targetGraph.nodes.values.minByOrNull {
                    GeoUtils.distanceMeters(dest, it.point)
                }

                if (startNodeId != null && destNode != null) {
                    val router = AStarRouter(targetGraph)
                    val result = router.calculateRoute(startNodeId, destNode.id)

                    val customPlace = Place(
                        id = "map-pin-${System.currentTimeMillis()}",
                        osmRef = null,
                        name = "Selected Map Location",
                        normalizedName = "selected map location",
                        category = "waypoint",
                        lat = dest.lat,
                        lon = dest.lon,
                        buildingId = null,
                        recognitionSupported = false
                    )
                    val entrance = org.localar.navigator.data.PlaceEntrance(
                        id = "entrance-${destNode.id}",
                        placeId = customPlace.id,
                        name = "Map Pin",
                        lat = dest.lat,
                        lon = dest.lon,
                        graphNodeId = destNode.id,
                        accessStatus = "public",
                        reviewed = true
                    )

                    withContext(Dispatchers.Main) {
                        if (result is RouteResult.Success) {
                            _uiState.value = _uiState.value.copy(
                                selectedPlace = customPlace,
                                selectedEntrance = entrance,
                                activeRoute = result.plan,
                                currentManeuverIndex = 0,
                                isArrived = false,
                                statusMessage = "Shortest route: ${result.plan.totalDistanceM.toInt()}m"
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                selectedPlace = customPlace,
                                selectedEntrance = entrance,
                                activeRoute = null,
                                statusMessage = "No walkable path found to that pin."
                            )
                        }
                    }
                    return@launch
                }
            }

            // Outside graph pack region (anywhere in Philippines / Worldwide):
            // Generate direct walkable path between current user location and tapped destination pin
            val directDistanceM = GeoUtils.distanceMeters(userLocation, dest)
            val intermediateCount = (directDistanceM / 20.0).toInt().coerceIn(1, 40)
            val points = mutableListOf<GeoPoint>()
            points.add(userLocation)
            for (i in 1..intermediateCount) {
                val frac = i.toDouble() / (intermediateCount + 1).toDouble()
                points.add(
                    GeoPoint(
                        lat = userLocation.lat + (dest.lat - userLocation.lat) * frac,
                        lon = userLocation.lon + (dest.lon - userLocation.lon) * frac
                    )
                )
            }
            points.add(dest)

            val customPlace = Place(
                id = "map-pin-${System.currentTimeMillis()}",
                osmRef = null,
                name = "Pinned Location",
                normalizedName = "pinned location",
                category = "waypoint",
                lat = dest.lat,
                lon = dest.lon,
                buildingId = null,
                recognitionSupported = false
            )
            val entrance = org.localar.navigator.data.PlaceEntrance(
                id = "entrance-pin-${System.currentTimeMillis()}",
                placeId = customPlace.id,
                name = "Map Pin",
                lat = dest.lat,
                lon = dest.lon,
                graphNodeId = -1L,
                accessStatus = "public",
                reviewed = true
            )
            val directRoute = RoutePlan(
                routeId = "route-pin-${System.currentTimeMillis()}",
                totalDistanceM = directDistanceM,
                totalCostS = directDistanceM / 1.3,
                orderedNodes = emptyList(),
                orderedEdges = emptyList(),
                polyline = points,
                maneuvers = listOf(
                    org.localar.core.routing.Maneuver(
                        type = org.localar.core.routing.ManeuverType.DEPART,
                        instruction = "Head towards destination",
                        point = userLocation,
                        distanceToNextM = directDistanceM
                    ),
                    org.localar.core.routing.Maneuver(
                        type = org.localar.core.routing.ManeuverType.ARRIVE,
                        instruction = "Arrive at destination",
                        point = dest,
                        distanceToNextM = 0.0
                    )
                )
            )

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    selectedPlace = customPlace,
                    selectedEntrance = entrance,
                    activeRoute = directRoute,
                    currentManeuverIndex = 0,
                    isArrived = false,
                    statusMessage = "Route: ${directDistanceM.toInt()}m (~${(directDistanceM / 78.0).toInt().coerceAtLeast(1)} min)"
                )
            }
        }
    }

    fun saveRouteOffline() {
        val route = _uiState.value.activeRoute ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val offlineFile = File(getApplication<Application>().filesDir, "active_offline_route.json")
                val polylineCoords = route.polyline.map { listOf(it.lon, it.lat) }
                val jsonStr = """{"totalDistanceM": ${route.totalDistanceM}, "polyline": $polylineCoords}"""
                offlineFile.writeText(jsonStr)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Route cached offline successfully!"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Could not cache route: ${e.message}"
                    )
                }
            }
        }
    }

    fun onClearSelection() {
        simulationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedPlace = null,
            selectedEntrance = null,
            activeRoute = null,
            isNavigating = false,
            isSimulatingWalk = false,
            isArrived = false,
            currentManeuverIndex = 0
        )
    }

    fun onStartNavigation() {
        if (_uiState.value.activeRoute != null) {
            _uiState.value = _uiState.value.copy(isNavigating = true, isArrived = false)
            locationProvider.startListening()
        }
    }

    fun onStopNavigation() {
        simulationJob?.cancel()
        locationProvider.stopListening()
        _uiState.value = _uiState.value.copy(
            isNavigating = false,
            isSimulatingWalk = false,
            activeRoute = null,
            selectedPlace = null,
            selectedEntrance = null,
            isArrived = false,
            currentManeuverIndex = 0
        )
    }

    fun onNextManeuver() {
        val route = _uiState.value.activeRoute ?: return
        val nextIdx = _uiState.value.currentManeuverIndex + 1
        if (nextIdx < route.maneuvers.size) {
            _uiState.value = _uiState.value.copy(currentManeuverIndex = nextIdx)
        }
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
        locationProvider.stopListening()
        headingProvider.stopListening()
        districtDatabase?.close()
        mediaPipeEmbedder?.close()
    }
}
