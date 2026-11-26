package co.edu.unal.nearbyplaces.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PhotonUrlBuilderTest {

    private val center = GeoPoint(4.6370, -74.0830)
    private val defaultLocale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun build_hasCenterRadiusLimitAndEncodedTags() {
        assertEquals(
            "https://photon.komoot.io/reverse?lat=4.637000&lon=-74.083000&radius=2&limit=50" +
                "&osm_tag=amenity%3Ahospital&osm_tag=amenity%3Aclinic",
            PhotonUrlBuilder.build(center, 2, PoiCategory.HOSPITALS)
        )
    }

    @Test
    fun build_repeatsOneOsmTagPerTag() {
        val url = PhotonUrlBuilder.build(center, 5, PoiCategory.TOURISM)
        val tags = url.split("&").filter { it.startsWith("osm_tag=") }
        assertEquals(PoiCategory.TOURISM.osmTags.size, tags.size)
        assertEquals(PoiCategory.TOURISM.osmTags.map { "osm_tag=" + it.replace(":", "%3A") }, tags)
    }

    @Test
    fun build_usesTheRadiusAndLimitGiven() {
        val url = PhotonUrlBuilder.build(center, 20, PoiCategory.POLICE, limit = 10)
        assertTrue("radius=20" in url)
        assertTrue("limit=10" in url)
    }

    @Test
    fun build_neverAsksForALanguage() {
        // lang=es hace que Photon conteste 400
        assertFalse("lang=" in PhotonUrlBuilder.build(center, 2, PoiCategory.PHARMACIES))
    }

    @Test
    fun build_usesDotsAsDecimalSeparator_inAnyLocale() {
        Locale.setDefault(Locale.forLanguageTag("es-CO"))
        val url = PhotonUrlBuilder.build(GeoPoint(-12.5, 130.25), 1, PoiCategory.HOTELS)
        assertTrue("lat=-12.500000&lon=130.250000" in url)
    }

    @Test
    fun everyCategory_hasAtLeastOneWellFormedTag() {
        for (category in PoiCategory.entries) {
            assertTrue(category.osmTags.isNotEmpty())
            assertTrue(category.osmTags.all { Regex("[a-z_]+:[a-z_]+").matches(it) })
        }
    }
}
