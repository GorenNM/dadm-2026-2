package co.edu.unal.nearbyplaces.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    private val origin = GeoPoint(4.6370, -74.0830)

    private fun poi(id: String, distanceMeters: Double) = Poi(
        id = id, name = id, category = PoiCategory.HOSPITALS, location = origin, distanceMeters = distanceMeters
    )

    @Test
    fun distance_samePoint_isZero() {
        assertEquals(0.0, GeoMath.distanceMeters(origin, origin), 1e-9)
    }

    @Test
    fun distance_oneDegreeOfLongitudeAtTheEquator() {
        // R * pi / 180 = 111194.93 m
        assertEquals(111_194.93, GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 0.5)
    }

    @Test
    fun distance_poleToEquator_isAQuarterOfTheMeridian() {
        assertEquals(10_007_543.4, GeoMath.distanceMeters(GeoPoint(90.0, 0.0), GeoPoint(0.0, 0.0)), 1.0)
    }

    @Test
    fun distance_antipodes_isHalfTheCircumference_andNotNaN() {
        val half = GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0))
        assertEquals(20_015_086.8, half, 1.0)
    }

    @Test
    fun distance_isSymmetric() {
        val medellin = GeoPoint(6.2442, -75.5812)
        assertEquals(GeoMath.distanceMeters(origin, medellin), GeoMath.distanceMeters(medellin, origin), 1e-6)
        // Bogotá - Medellín en línea recta: unos 240 km
        assertEquals(240_000.0, GeoMath.distanceMeters(origin, medellin), 5_000.0)
    }

    @Test
    fun nearest_keepsTheBorder_dropsWhatIsBeyond() {
        val pois = listOf(poi("inside", 1_999.9), poi("border", 2_000.0), poi("beyond", 2_000.1))
        assertEquals(listOf("inside", "border"), GeoMath.nearest(pois, 2_000.0).map { it.id })
    }

    @Test
    fun nearest_sortsByDistance() {
        val pois = listOf(poi("c", 900.0), poi("a", 100.0), poi("b", 500.0))
        assertEquals(listOf("a", "b", "c"), GeoMath.nearest(pois, 2_000.0).map { it.id })
    }

    @Test
    fun nearest_ofNothing_isEmpty() {
        assertTrue(GeoMath.nearest(emptyList(), 2_000.0).isEmpty())
    }

    @Test
    fun bounds_surroundTheCenter_andMatchTheRadius() {
        val bounds = GeoMath.bounds(origin, 2_000.0)
        assertTrue(bounds.south < origin.latitude && origin.latitude < bounds.north)
        assertTrue(bounds.west < origin.longitude && origin.longitude < bounds.east)
        // Los lados norte y este están a (casi) el radio del centro
        val north = GeoMath.distanceMeters(origin, GeoPoint(bounds.north, origin.longitude))
        val east = GeoMath.distanceMeters(origin, GeoPoint(origin.latitude, bounds.east))
        assertEquals(2_000.0, north, 1.0)
        assertEquals(2_000.0, east, 1.0)
    }
}
