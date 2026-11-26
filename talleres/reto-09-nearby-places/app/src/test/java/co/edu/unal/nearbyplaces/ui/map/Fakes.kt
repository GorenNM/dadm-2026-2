package co.edu.unal.nearbyplaces.ui.map

import co.edu.unal.nearbyplaces.data.AddressRepository
import co.edu.unal.nearbyplaces.data.CategoryResult
import co.edu.unal.nearbyplaces.data.GeoPoint
import co.edu.unal.nearbyplaces.data.LocationOutcome
import co.edu.unal.nearbyplaces.data.LocationProvider
import co.edu.unal.nearbyplaces.data.Poi
import co.edu.unal.nearbyplaces.data.PoiCategory
import co.edu.unal.nearbyplaces.data.PoiRepository
import co.edu.unal.nearbyplaces.data.RadiusSource
import kotlinx.coroutines.delay
import java.io.IOException

class FakeLocationProvider(var outcome: LocationOutcome) : LocationProvider {
    var calls = 0
    var delayMs = 0L

    override suspend fun currentLocation(): LocationOutcome {
        calls++
        if (delayMs > 0) delay(delayMs)
        return outcome
    }
}

class FakePoiRepository : PoiRepository {

    data class Call(val center: GeoPoint, val radiusKm: Int, val category: PoiCategory)

    val calls = mutableListOf<Call>()

    /** Lo que contesta por categoría; sin entrada, una lista vacía. */
    val results = mutableMapOf<PoiCategory, CategoryResult>()

    /** Si no es null, toda consulta falla con esto. */
    var failure: IOException? = null

    /** Demora por centro: sirve para tener una consulta "en vuelo" mientras llega otra. */
    val delayByCenter = mutableMapOf<GeoPoint, Long>()

    override suspend fun nearby(center: GeoPoint, radiusKm: Int, category: PoiCategory): CategoryResult {
        calls += Call(center, radiusKm, category)
        delayByCenter[center]?.let { delay(it) }
        failure?.let { throw it }
        return results[category] ?: CategoryResult(category, emptyList(), truncated = false)
    }
}

class FakeAddressRepository(var address: String? = null) : AddressRepository {
    val requested = mutableListOf<GeoPoint>()

    override suspend fun addressOf(point: GeoPoint): String? {
        requested += point
        return address
    }
}

class FakeRadius(var km: Int = 2) : RadiusSource {
    override fun radiusKm() = km
}

fun poi(id: String, category: PoiCategory, distanceMeters: Double, location: GeoPoint = GeoPoint(4.64, -74.08)) =
    Poi(id = id, name = "Lugar $id", category = category, location = location, distanceMeters = distanceMeters)
