package co.edu.unal.nearbyplaces.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Cuentas sobre la esfera terrestre. Kotlin puro: se testea en la JVM sin Android. */
object GeoMath {

    const val EARTH_RADIUS_METERS = 6_371_000.0

    private const val METERS_PER_DEGREE = EARTH_RADIUS_METERS * Math.PI / 180.0

    /** Distancia en línea recta sobre la superficie (fórmula del haversine), en metros. */
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        // min(1.0): por redondeo "a" puede pasar de 1 en puntos opuestos y asin daría NaN
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /** Los lugares a [radiusMeters] o menos (el borde cuenta), del más cercano al más lejano. */
    fun nearest(pois: List<Poi>, radiusMeters: Double): List<Poi> =
        pois.filter { it.distanceMeters <= radiusMeters }.sortedBy { it.distanceMeters }

    /** Rectángulo que contiene el círculo de [radiusMeters] alrededor de [center]. */
    fun bounds(center: GeoPoint, radiusMeters: Double): GeoBounds {
        val dLat = radiusMeters / METERS_PER_DEGREE
        // Cerca de los polos los meridianos se juntan: se evita dividir por casi cero
        val dLon = radiusMeters / (METERS_PER_DEGREE * max(cos(Math.toRadians(center.latitude)), 0.01))
        return GeoBounds(
            north = min(center.latitude + dLat, 85.0),
            south = max(center.latitude - dLat, -85.0),
            east = min(center.longitude + dLon, 180.0),
            west = max(center.longitude - dLon, -180.0)
        )
    }
}
