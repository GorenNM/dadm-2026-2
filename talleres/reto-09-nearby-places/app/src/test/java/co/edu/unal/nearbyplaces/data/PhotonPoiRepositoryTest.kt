package co.edu.unal.nearbyplaces.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** Corre con Robolectric por el `org.json` real (ver [PhotonParserTest]). */
@RunWith(RobolectricTestRunner::class)
class PhotonPoiRepositoryTest {

    private val center = GeoPoint(4.6370, -74.0830)

    /** Una respuesta con [count] lugares, el primero [firstName] (null = sin nombre). */
    private fun response(count: Int, firstName: String? = "Lugar 0"): String {
        val features = (0 until count).joinToString(",") { i ->
            val name = if (i == 0) firstName else "Lugar $i"
            val nameField = if (name == null) "" else """"name":"$name","""
            """{"properties":{$nameField"osm_type":"N","osm_id":$i},"geometry":{"coordinates":[-74.083,${4.637 + i * 0.0001}]}}"""
        }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    @Test
    fun nearby_asksPhotonWithTheBuiltUrl_andReturnsTheParsedPlaces() = runTest {
        val urls = mutableListOf<String>()
        val repository = PhotonPoiRepository { url -> urls += url; response(3) }

        val result = repository.nearby(center, 2, PoiCategory.PHARMACIES)

        assertEquals(listOf(PhotonUrlBuilder.build(center, 2, PoiCategory.PHARMACIES)), urls)
        assertEquals(PoiCategory.PHARMACIES, result.category)
        assertEquals(listOf("Lugar 0", "Lugar 1", "Lugar 2"), result.pois.map { it.name })
        assertFalse(result.truncated)
    }

    @Test
    fun nearby_with50Results_isTruncated() = runTest {
        val result = PhotonPoiRepository { response(50) }.nearby(center, 2, PoiCategory.RESTAURANTS)
        assertEquals(50, result.pois.size)
        assertTrue(result.truncated)
    }

    @Test
    fun nearby_with49Results_isNotTruncated() = runTest {
        assertFalse(PhotonPoiRepository { response(49) }.nearby(center, 2, PoiCategory.RESTAURANTS).truncated)
    }

    @Test
    fun nearby_truncationCountsWhatPhotonSent_notWhatHadAName() = runTest {
        // 50 resultados, uno sin nombre: se muestran 49, pero igual Photon llenó el tope
        val result = PhotonPoiRepository { response(50, firstName = null) }.nearby(center, 2, PoiCategory.RESTAURANTS)
        assertEquals(49, result.pois.size)
        assertTrue(result.truncated)
    }

    @Test
    fun nearby_httpFailure_isAnIOException() {
        val repository = PhotonPoiRepository { throw IOException("HTTP 502") }
        assertThrows(IOException::class.java) { runBlocking { repository.nearby(center, 2, PoiCategory.POLICE) } }
    }

    @Test
    fun nearby_garbageBody_isAnIOException_notAJSONException() {
        val repository = PhotonPoiRepository { "<html>Bad Gateway</html>" }
        assertThrows(IOException::class.java) { runBlocking { repository.nearby(center, 2, PoiCategory.POLICE) } }
    }
}
