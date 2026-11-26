package co.edu.unal.nearbyplaces.data

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Corre con Robolectric: `org.json` en la JVM pelada es el stub de android.jar y no hace nada. */
@RunWith(RobolectricTestRunner::class)
class PhotonParserTest {

    private val origin = GeoPoint(4.6370, -74.0830)

    // Respuesta real de Photon (reverse, amenity:hospital + amenity:clinic, UNAL Bogotá), recortada,
    // con tres casos agregados a mano: sin "name", con "name": null y con nombre en blanco
    private val json = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{"osm_type":"N","osm_id":5178923446,"osm_key":"amenity","osm_value":"clinic",
            "type":"house","name":"Juan Bautista","street":"Avenida Carrera 30","locality":"Galerías",
            "district":"Localidad Teusaquillo","city":"Bogotá","state":"Bogotá, Distrito Capital","country":"Colombia",
            "postcode":"111311","countrycode":"CO"},
           "geometry":{"type":"Point","coordinates":[-74.0787898,4.6369732]}},
          {"type":"Feature","properties":{"osm_type":"W","osm_id":533884601,"osm_key":"amenity","osm_value":"clinic",
            "type":"house","name":"Corsanemos","street":"Calle 46","locality":"Ciudad Universitaria","city":"Bogotá",
            "countrycode":"CO","extent":[-74.0775302,4.6358208,-74.0773803,4.6356562]},
           "geometry":{"type":"Point","coordinates":[-74.0774552,4.6357385]}},
          {"type":"Feature","properties":{"osm_type":"N","osm_id":111,"osm_key":"amenity","osm_value":"clinic",
            "street":"Calle 1","city":"Bogotá"},
           "geometry":{"type":"Point","coordinates":[-74.0830,4.6371]}},
          {"type":"Feature","properties":{"osm_type":"N","osm_id":222,"osm_key":"amenity","osm_value":"clinic","name":null},
           "geometry":{"type":"Point","coordinates":[-74.0830,4.6372]}},
          {"type":"Feature","properties":{"osm_type":"N","osm_id":333,"osm_key":"amenity","osm_value":"clinic","name":"   "},
           "geometry":{"type":"Point","coordinates":[-74.0830,4.6373]}},
          {"type":"Feature","properties":{"osm_type":"N","osm_id":2315535486,"osm_key":"amenity","osm_value":"hospital",
            "name":"Salud Total","street":"Carrera 30","housenumber":"45-10","city":"Bogotá"},
           "geometry":{"type":"Point","coordinates":[-74.0788582,4.6359714]}}
        ]}
    """.trimIndent()

    @Test
    fun parse_keepsOnlyThePlacesWithAName() {
        val pois = PhotonParser.parse(json, PoiCategory.HOSPITALS, origin)
        assertEquals(listOf("Juan Bautista", "Corsanemos", "Salud Total"), pois.map { it.name })
        // "name": null no se convierte en el texto "null"
        assertTrue(pois.none { it.name == "null" })
    }

    @Test
    fun parse_readsCoordinatesAsLongitudeThenLatitude() {
        val first = PhotonParser.parse(json, PoiCategory.HOSPITALS, origin).first()
        assertEquals(4.6369732, first.location.latitude, 1e-9)
        assertEquals(-74.0787898, first.location.longitude, 1e-9)
    }

    @Test
    fun parse_aWay_comesWithItsPointCoordinates_andItsOwnId() {
        val way = PhotonParser.parse(json, PoiCategory.HOSPITALS, origin).first { it.name == "Corsanemos" }
        assertEquals("W533884601", way.id)
        assertEquals(4.6357385, way.location.latitude, 1e-9)
        assertEquals(-74.0774552, way.location.longitude, 1e-9)
    }

    @Test
    fun parse_idsAreTypePlusNumber_soANodeAndAWayNeverClash() {
        val ids = PhotonParser.parse(json, PoiCategory.HOSPITALS, origin).map { it.id }
        assertEquals(listOf("N5178923446", "W533884601", "N2315535486"), ids)
    }

    @Test
    fun parse_setsTheCategoryAndTheDistanceFromTheOrigin() {
        val pois = PhotonParser.parse(json, PoiCategory.HOSPITALS, origin)
        assertTrue(pois.all { it.category == PoiCategory.HOSPITALS })
        pois.forEach { assertEquals(GeoMath.distanceMeters(origin, it.location), it.distanceMeters, 1e-9) }
        // Juan Bautista queda a unos 468 m al este
        assertEquals(468.0, pois.first().distanceMeters, 5.0)
    }

    @Test
    fun parse_buildsTheAddressFromWhateverIsThere() {
        val pois = PhotonParser.parse(json, PoiCategory.HOSPITALS, origin)
        assertEquals("Avenida Carrera 30, Bogotá", pois[0].address)
        assertEquals("Carrera 30 45-10, Bogotá", pois[2].address)
    }

    @Test
    fun parse_noStreetNoCity_noAddress() {
        val bare = """{"features":[{"properties":{"osm_type":"N","osm_id":1,"name":"X"},"geometry":{"coordinates":[-74.0,4.0]}}]}"""
        assertNull(PhotonParser.parse(bare, PoiCategory.POLICE, origin).single().address)
    }

    @Test
    fun parse_skipsFeaturesWithoutUsableCoordinates() {
        val broken = """{"features":[
            {"properties":{"name":"Sin geometria"}},
            {"properties":{"name":"Una sola coordenada"},"geometry":{"coordinates":[-74.0]}},
            {"properties":{"name":"Buena","osm_type":"N","osm_id":9},"geometry":{"coordinates":[-74.0,4.0]}}]}"""
        assertEquals(listOf("Buena"), PhotonParser.parse(broken, PoiCategory.HOTELS, origin).map { it.name })
    }

    @Test
    fun parse_emptyAnswers_giveNoPlaces() {
        assertTrue(PhotonParser.parse("""{"type":"FeatureCollection","features":[]}""", PoiCategory.HOTELS, origin).isEmpty())
        assertTrue(PhotonParser.parse("{}", PoiCategory.HOTELS, origin).isEmpty())
    }

    @Test
    fun parse_notJson_throwsJSONException() {
        assertThrows(JSONException::class.java) { PhotonParser.parse("<html>502 Bad Gateway</html>", PoiCategory.HOTELS, origin) }
    }

    @Test
    fun featureCount_countsEverythingPhotonReturned_namelessIncluded() {
        assertEquals(6, PhotonParser.featureCount(json))
    }
}
