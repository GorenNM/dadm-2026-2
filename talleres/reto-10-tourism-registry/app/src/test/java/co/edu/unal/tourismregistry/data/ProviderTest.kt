package co.edu.unal.tourismregistry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTest {

    private fun provider(phone: String = "", mobile: String = "", address: String = "", municipality: String = "", department: String = "") =
        Provider("1", "X", address = address, municipality = municipality, department = department, phone = phone, mobile = mobile)

    // ---- Teléfono marcable ----

    @Test
    fun dialable_acceptsPlainAndFormattedNumbers() {
        assertEquals("3102749973", PhoneNumbers.dialable("3102749973"))
        assertEquals("6011234567", PhoneNumbers.dialable("(601) 123-4567"))
        assertEquals("+573001112233", PhoneNumbers.dialable("+57 300 111 2233"))
    }

    @Test
    fun dialable_rejectsPlaceholdersAndShortOrLongValues() {
        assertNull(PhoneNumbers.dialable("Sin dato"))
        assertNull(PhoneNumbers.dialable("0"))
        assertNull(PhoneNumbers.dialable(""))
        assertNull(PhoneNumbers.dialable("123456"))
        assertNull(PhoneNumbers.dialable("1234567890123456"))
        assertNull(PhoneNumbers.dialable("57+3001112233"))
    }

    @Test
    fun dialable_takesTheFirstUsefulOfSeveralNumbers() {
        assertEquals("3001112233", PhoneNumbers.dialable("0 / 300 111 2233"))
        assertEquals("6011234567", PhoneNumbers.dialable("601 123 4567, 3001112233"))
    }

    @Test
    fun dialNumber_prefersTheMobile_andFallsBackToThePhone() {
        assertEquals("3001112233", provider(phone = "6011234567", mobile = "3001112233").dialNumber)
        assertEquals("6011234567", provider(phone = "6011234567", mobile = "0").dialNumber)
        assertNull(provider(phone = "", mobile = "0").dialNumber)
    }

    // ---- Búsqueda en el mapa ----

    @Test
    fun mapQuery_joinsTheNonEmptyParts() {
        assertEquals(
            "KILOMETRO 2 FINCA SAN RAFAEL, VILLA DE LEYVA, BOYACA",
            provider(address = "KILOMETRO 2 FINCA SAN RAFAEL", municipality = "VILLA DE LEYVA", department = "BOYACA").mapQuery
        )
        assertEquals("VILLA DE LEYVA, BOYACA", provider(municipality = "VILLA DE LEYVA", department = "BOYACA").mapQuery)
        assertEquals("", provider().mapQuery)
    }

    // ---- Filtro ----

    @Test
    fun filter_isEmptyOnlyWithoutAnyCondition() {
        assertTrue(ProviderFilter().isEmpty)
        assertTrue(ProviderFilter(name = "  ").isEmpty)
        assertFalse(ProviderFilter(name = "a").isEmpty)
        assertFalse(ProviderFilter(department = "META").isEmpty)
        assertFalse(ProviderFilter(category = "BARES").isEmpty)
    }
}
