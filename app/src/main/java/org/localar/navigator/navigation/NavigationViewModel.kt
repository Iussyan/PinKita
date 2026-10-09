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
import org.localar.navigator.location.LocationFix
import org.localar.navigator.location.LocationProvider
import org.localar.navigator.location.LocationQuality
import org.localar.navigator.packs.DistrictPackManager
import org.localar.navigator.vision.MediaPipeEmbedder
import org.localar.navigator.vision.PackEmbeddingLoader
import java.io.File
import kotlin.math.max

enum class ScreenMode {
    MAP,
    CAMERA_SCANNER
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
    val screenMode: ScreenMode = ScreenMode.MAP,
    val statusMessage: String? = null,
    val districtName: String = "District",
    val currentLocation: GeoPoint = GeoPoint(14.5990, 120.9840), // Default: Transit Plaza West
    val locationQuality: LocationQuality = LocationQuality.MISSING,
    val locationAccuracyM: Float = 0f,
    val isSimulatingWalk: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val selectedPlace: Place? = null,
    val selectedEntrance: PlaceEntrance? = null,
    val activeRoute: RoutePlan? = null,
    val isNavigating: Boolean = false,
    val isArrived: Boolean = false,
    val currentManeuverIndex: Int = 0,
    val allPathsGeoJson: String = "",
    val scanState: ScanUiState = ScanUiState()
)

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private val locationProvider = LocationProvider(application)
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
        initMediaPipe()
    }

    private fun initMediaPipe() {
        try {
            mediaPipeEmbedder = MediaPipeEmbedder(getApplication())
        } catch (_: Exception) {}
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
                        val initialPlaces = db.searchPlaces("")

                        // Load AI embeddings
                        val aiDir = File(packDir, "ai")
                        landmarkMatcher = PackEmbeddingLoader.loadMatcher(aiDir)

                        _uiState.value = _uiState.value.copy(
                            isInitialized = true,
                            districtName = districtName,
                            allPathsGeoJson = allPathsJson,
                            searchResults = initialPlaces,
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
        val currentIdx = _uiState.value.currentManeuverIndex
        if (currentIdx < maneuvers.size) {
            val targetManeuver = maneuvers[currentIdx]
            val distToManeuver = GeoUtils.distanceMeters(currentPos, targetManeuver.point)
            if (distToManeuver <= 8.0 && currentIdx + 1 < maneuvers.size) {
                _uiState.value = _uiState.value.copy(currentManeuverIndex = currentIdx + 1)
            }
        }

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

    fun setScreenMode(mode: ScreenMode) {
        _uiState.value = _uiState.value.copy(screenMode = mode)
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
                val embedding = embedder.embed(bitmap)
                val decision = matcher.processFrame(embedding, SystemClock.elapsedRealtime())

                val landmarkName = decision.recognizedLandmarkId?.let { id ->
                    // Lookup landmark display name in places or format readable
                    id.removePrefix("landmark-").split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        scanState = ScanUiState(
                            status = decision.status,
                            landmarkName = landmarkName,
                            bestScore = decision.bestScore,
                            margin = decision.margin,
                            isPromoted = decision.status == RecognitionStatus.IDENTIFIED
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        viewModelScope.launch(Dispatchers.IO) {
            val db = districtDatabase ?: return@launch
            val results = db.searchPlaces(query)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(searchResults = results)
            }
        }
    }

    fun onSelectPlace(place: Place) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = districtDatabase ?: return@launch
            val graph = pedestrianGraph ?: return@launch
            val entrances = db.getEntrancesForPlace(place.id)
            val primaryEntrance = entrances.firstOrNull { it.reviewed } ?: entrances.firstOrNull()

            if (primaryEntrance == null) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        selectedPlace = place,
                        selectedEntrance = null,
                        activeRoute = null,
                        statusMessage = "No entrance mapped for this place."
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
        districtDatabase?.close()
        mediaPipeEmbedder?.close()
    }
}
