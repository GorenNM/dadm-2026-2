package co.edu.unal.tourismregistry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URLDecoder

/** Kotlin puro: el armado de la consulta no necesita Android ni red. */
class SodaQueryBuilderTest {

    private val builder = SodaQueryBuilder()

    private fun SodaQuery.param(name: String) = params.firstOrNull { it.first == name }?.second

    // ---- Escape ----

    @Test
    fun escapeLiteral_doublesSingleQuotes() {
        assertEquals("D''COLOMBIA", SodaQueryBuilder.escapeLiteral("D'COLOMBIA"))
        assertEquals("''''", SodaQueryBuilder.escapeLiteral("''"))
        assertEquals("sin comillas", SodaQueryBuilder.escapeLiteral("sin comillas"))
    }

    @Test
    fun escapeLike_makesWildcardsAndBackslashLiteral() {
        assertEquals("100\\% \\_X", SodaQueryBuilder.escapeLike("100% _X"))
        assertEquals("A\\\\B", SodaQueryBuilder.escapeLike("A\\B"))
    }

    @Test
    fun name_withApostrophe_isEscapedInsideTheLiteral() {
        val where = builder.where(ProviderFilter(name = "d'colombia"))
        assertEquals("contains(upper(razon_social_establecimiento), 'D''COLOMBIA')", where)
    }

    @Test
    fun name_lookingLikeAnInjection_staysInsideOneLiteral() {
        val where = builder.where(ProviderFilter(name = "x' OR '1'='1"))
        // Cada comilla del usuario quedó doblada: el servicio ve un solo literal, nunca una condición extra
        assertEquals("contains(upper(razon_social_establecimiento), 'X'' OR ''1''=''1')", where)
    }

    @Test
    fun dropdownValues_areEscapedToo() {
        val where = builder.where(ProviderFilter(department = "BOYACA' OR '1'='1", municipality = "D'ABC", category = "A'B"))
        assertEquals(
            "departamento = 'BOYACA'' OR ''1''=''1' AND municipio = 'D''ABC' AND categoria = 'A''B'",
            where
        )
    }

    @Test
    fun name_percentAndUnderscore_areSearchedAsLiterals() {
        val where = builder.where(ProviderFilter(name = "1%_a"))
        assertEquals("contains(upper(razon_social_establecimiento), '1\\%\\_A')", where)
    }

    @Test
    fun name_isTrimmedAndUppercased_includingAccents() {
        val where = builder.where(ProviderFilter(name = "  ñandú  "))
        assertEquals("contains(upper(razon_social_establecimiento), 'ÑANDÚ')", where)
    }

    // ---- Combinación de filtros ----

    @Test
    fun noFilters_meansNoWhere() {
        assertNull(builder.where(ProviderFilter()))
        // Solo espacios en el nombre tampoco filtra
        assertNull(builder.where(ProviderFilter(name = "   ")))
        assertFalse(builder.page(ProviderFilter(), 0).params.any { it.first == "\$where" })
    }

    @Test
    fun allFilters_areCombinedWithAnd_inAFixedOrder() {
        val where = builder.where(
            ProviderFilter(name = "hotel", department = "BOYACA", municipality = "VILLA DE LEYVA", category = "BARES")
        )
        assertEquals(
            "departamento = 'BOYACA' AND municipio = 'VILLA DE LEYVA' AND categoria = 'BARES' AND " +
                "contains(upper(razon_social_establecimiento), 'HOTEL')",
            where
        )
    }

    @Test
    fun onlyTheChosenFilters_appear() {
        assertEquals("categoria = 'BARES'", builder.where(ProviderFilter(category = "BARES")))
        assertEquals("departamento = 'META'", builder.where(ProviderFilter(department = "META")))
    }

    // ---- Consultas ----

    @Test
    fun page_hasStableOrder_limitAndOffset() {
        val query = builder.page(ProviderFilter(department = "META"), offset = 40, limit = 20)
        assertEquals("departamento = 'META'", query.param("\$where"))
        assertEquals("razon_social_establecimiento,codigo_rnt", query.param("\$order"))
        assertEquals("20", query.param("\$limit"))
        assertEquals("40", query.param("\$offset"))
    }

    @Test
    fun page_defaultsToTwentyPerPage() {
        assertEquals("20", builder.page(ProviderFilter(), 0).param("\$limit"))
        assertEquals(20, SodaQueryBuilder.PAGE_SIZE)
    }

    @Test
    fun page_rejectsNegativeOffsetAndEmptyLimit() {
        try {
            builder.page(ProviderFilter(), offset = -1)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            builder.page(ProviderFilter(), offset = 0, limit = 0)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun count_usesTheSameWhereAsThePage() {
        val filter = ProviderFilter(name = "d'a", department = "BOYACA")
        val count = builder.count(filter)
        assertEquals("count(*)", count.param("\$select"))
        assertEquals(builder.page(filter, 0).param("\$where"), count.param("\$where"))
        assertNull(builder.count(ProviderFilter()).param("\$where"))
    }

    @Test
    fun byCode_looksUpOneRow_andEscapes() {
        val query = builder.byCode("24027")
        assertEquals("codigo_rnt = '24027'", query.param("\$where"))
        assertEquals("1", query.param("\$limit"))
        assertEquals("codigo_rnt = '1'' OR ''1''=''1'", builder.byCode("1' OR '1'='1").param("\$where"))
    }

    @Test
    fun dropdownQueries_selectGroupAndOrderTheField() {
        val departments = builder.departments()
        assertEquals("departamento", departments.param("\$select"))
        assertEquals("departamento", departments.param("\$group"))
        assertEquals("departamento", departments.param("\$order"))
        assertNull(departments.param("\$where"))

        val categories = builder.categories()
        assertEquals("categoria", categories.param("\$select"))
        assertEquals("categoria", categories.param("\$group"))
    }

    @Test
    fun municipalities_areFilteredByTheChosenDepartment() {
        val query = builder.municipalities("SAN ANDRES Y PROVIDENCIA")
        assertEquals("municipio", query.param("\$select"))
        assertEquals("municipio", query.param("\$group"))
        assertEquals("departamento = 'SAN ANDRES Y PROVIDENCIA'", query.param("\$where"))
        assertEquals("departamento = 'D''X'", builder.municipalities("D'X").param("\$where"))
    }

    // ---- Codificación de la URL ----

    @Test
    fun url_encodesSpacesAsPercent20_notPlus() {
        val url = builder.page(ProviderFilter(municipality = "VILLA DE LEYVA"), 0).url
        assertFalse("la URL no debe tener '+': $url", url.contains('+'))
        assertTrue(url.contains("VILLA%20DE%20LEYVA"))
    }

    @Test
    fun url_encodesQuotesAccentsAndSpecials() {
        val url = builder.page(ProviderFilter(department = "NARIÑO", name = "d'a&b=c"), 0).url
        assertTrue(url.contains("NARI%C3%91O"))
        assertTrue(url.contains("%27"))
        // '&' y '=' del usuario no pueden partir los parámetros de la URL
        assertEquals(4, url.substringAfter('?').split('&').size)
        assertFalse(url.contains("'"))
    }

    @Test
    fun url_startsWithTheDatasetEndpoint_andKeepsParameterNames() {
        val url = builder.count(ProviderFilter()).url
        assertTrue(url.startsWith("https://www.datos.gov.co/resource/thwd-ivmp.json?"))
        assertEquals("https://www.datos.gov.co/resource/thwd-ivmp.json?\$select=count%28%2A%29", url.replace("*", "%2A"))
    }

    @Test
    fun url_decodesBackToTheReadableQuery() {
        val query = builder.page(
            ProviderFilter(name = "a+b%c d'e", department = "NARIÑO", municipality = "VILLA DE LEYVA"), 20
        )
        val decoded = query.url.substringAfter('?').split('&').joinToString("&") { part ->
            val (key, value) = part.split('=', limit = 2)
            key + "=" + URLDecoder.decode(value, "UTF-8")
        }
        assertEquals(query.readableUrl.substringAfter('?'), decoded)
    }

    @Test
    fun readableUrl_isTheQueryWithoutEncoding() {
        val query = builder.page(ProviderFilter(department = "BOYACA"), 0, 20)
        assertEquals(
            "https://www.datos.gov.co/resource/thwd-ivmp.json?\$where=departamento = 'BOYACA'" +
                "&\$order=razon_social_establecimiento,codigo_rnt&\$limit=20&\$offset=0",
            query.readableUrl
        )
    }
}
