package co.edu.unal.softwaredirectory.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {

    @Test
    fun normalize_removesAccentsCaseAndExtraSpaces() {
        assertEquals("nandu tecnologia", SearchText.normalize("  Ñandú   Tecnología "))
        assertEquals("soluciones bogota", SearchText.normalize("SOLUCIONES\tBOGOTÁ"))
        assertEquals("pixel & byte", SearchText.normalize("Pixel & Byte"))
    }

    @Test
    fun containsPattern_wrapsInWildcards() {
        assertEquals("%bogota%", SearchText.containsPattern(" Bogotá "))
    }

    @Test
    fun containsPattern_escapesLikeWildcardsFromTheUser() {
        assertEquals("%100\\%%", SearchText.containsPattern("100%"))
        assertEquals("%a\\_b%", SearchText.containsPattern("a_b"))
        assertEquals("%c:\\\\x%", SearchText.containsPattern("C:\\x"))
    }
}
