package org.localar.navigator.data

data class Place(
    val id: String,
    val osmRef: String?,
    val name: String,
    val normalizedName: String,
    val category: String,
    val lat: Double,
    val lon: Double,
    val buildingId: String?,
    val recognitionSupported: Boolean,
    val address: String? = null,
    val openingHours: String? = null,
    val city: String? = null,
    val tagFeature: String? = null,
    val isSaved: Boolean = false
)

data class PlaceEntrance(
    val id: String,
    val placeId: String,
    val name: String?,
    val lat: Double,
    val lon: Double,
    val graphNodeId: Long,
    val accessStatus: String,
    val reviewed: Boolean
)

data class LandmarkRecord(
    val id: String,
    val placeId: String,
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val recognitionRadiusM: Double,
    val modelId: String,
    val referenceCount: Int
)
