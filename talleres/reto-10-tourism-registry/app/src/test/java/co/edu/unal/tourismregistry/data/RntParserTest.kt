package co.edu.unal.tourismregistry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Con Robolectric: en la JVM normal org.json es el stub del android.jar (todos los métodos devuelven vacío o lanzan
 * "not mocked"). Robolectric trae el org.json real del teléfono, el mismo que usa la app.
 */
@RunWith(RobolectricTestRunner::class)
class RntParserTest {

    private fun providers(body: String): List<Provider> =
        (RntParser.parseProviders(body) as ApiResult.Success).value

    private fun failure(result: ApiResult<*>): ApiError = (result as ApiResult.Failure).error

    // ---- Prestadores ----

    @Test
    fun parsesARealResponse() {
        val list = providers(Fixtures.load("rnt_page.json"))
        assertEquals(3, list.size)

        val first = list[0]
        assertEquals("24027", first.code)
        assertEquals("1572 EL ORIGEN", first.name)
        assertEquals("VIVIENDAS TURÍSTICAS", first.category)
        assertEquals("FINCA TURISTICA (ALOJAMIENTO RURAL)", first.subCategory)
        assertEquals("Comerciante", first.providerType)
        assertEquals("VILLA DE LEYVA VEREDA EL ROBLE", first.address)
        assertEquals("VILLA DE LEYVA", first.municipality)
        assertEquals("BOYACA", first.department)
        assertEquals("3102749973", first.phone)
        assertEquals("3102749973", first.mobile)
        assertEquals(11, first.rooms)
        assertEquals(32, first.beds)
        assertEquals(3, first.employees)
    }

    @Test
    fun noDataPlaceholder_becomesEmpty_andZeroStaysZero() {
        val second = providers(Fixtures.load("rnt_page.json"))[1]
        assertEquals("1% ADRENALINA VILLA DE LEYVA", second.name)
        assertEquals("", second.phone) // "Sin dato"
        assertEquals("3202950881", second.mobile)
        assertEquals("3202950881", second.dialNumber)
        assertEquals(0, second.rooms)
        assertEquals(0, second.beds)
    }

    @Test
    fun missingFields_areEmptyOrNull() {
        val list = providers("""[{"codigo_rnt":"7","razon_social_establecimiento":"SOLO NOMBRE"}]""")
        assertEquals(Provider(code = "7", name = "SOLO NOMBRE"), list.single())
        assertNull(list.single().rooms)
        assertNull(list.single().dialNumber)
    }

    @Test
    fun nullValues_doNotBecomeTheTextNull() {
        val body = """[{"codigo_rnt":"7","razon_social_establecimiento":null,"municipio":null,"categoria":null,
            |"habitaciones":null,"n_m_tel_fono":null}]""".trimMargin()
        val provider = providers(body).single()
        // optString("x") devolvería "null" para un valor JSON null: el parser mira isNull antes
        assertEquals("", provider.name)
        assertEquals("", provider.municipality)
        assertEquals("", provider.category)
        assertEquals("", provider.phone)
        assertNull(provider.rooms)
    }

    @Test
    fun numbersAsJsonNumbers_orGarbage_areHandled() {
        val body = """[{"codigo_rnt":"7","razon_social_establecimiento":"A","habitaciones":5,"camas":"abc","num_emp1":" 4 "}]"""
        val provider = providers(body).single()
        assertEquals(5, provider.rooms)
        assertNull(provider.beds)
        assertEquals(4, provider.employees)
    }

    @Test
    fun emailsAreNeverRead() {
        // El JSON real trae correo_establecimiento y correo_prestador; el modelo no los tiene
        val fields = Provider::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.none { "mail" in it || "correo" in it })
    }

    @Test
    fun rowsWithoutCode_andNonObjects_areSkipped() {
        val body = """[{"razon_social_establecimiento":"SIN CODIGO"}, 5, null, {"codigo_rnt":"","razon_social_establecimiento":"VACIO"},
            |{"codigo_rnt":"9","razon_social_establecimiento":"OK"}]""".trimMargin()
        assertEquals(listOf("9"), providers(body).map { it.code })
    }

    @Test
    fun emptyList_isSuccessWithNoProviders() {
        assertEquals(emptyList<Provider>(), providers("[]"))
        assertEquals(emptyList<Provider>(), providers("  [ ]  "))
    }

    @Test
    fun socrataErrorObject_isNotAProviderList() {
        assertEquals(ApiError.InvalidResponse, failure(RntParser.parseProviders(Fixtures.load("rnt_error_400.json"))))
    }

    @Test
    fun invalidJson_isInvalidResponse() {
        assertEquals(ApiError.InvalidResponse, failure(RntParser.parseProviders("<html>502 Bad Gateway</html>")))
        assertEquals(ApiError.InvalidResponse, failure(RntParser.parseProviders("")))
        assertEquals(ApiError.InvalidResponse, failure(RntParser.parseProviders("[{\"codigo_rnt\":")))
    }

    // ---- Mensaje de error ----

    @Test
    fun errorMessage_isTheMessageOfTheSocrataErrorJson() {
        val message = RntParser.errorMessage(Fixtures.load("rnt_error_400.json"))
        assertNotNull(message)
        assertTrue(message!!.startsWith("Could not parse SoQL query"))
    }

    @Test
    fun errorMessage_isNullWhenThereIsNone() {
        assertNull(RntParser.errorMessage(""))
        assertNull(RntParser.errorMessage("<html>Forbidden</html>"))
        assertNull(RntParser.errorMessage("""{"code":"x","error":true}"""))
        assertNull(RntParser.errorMessage("""{"message":null}"""))
        assertNull(RntParser.errorMessage("[1,2]"))
    }

    // ---- Total ----

    @Test
    fun count_readsTheStringNumber() {
        assertEquals(ApiResult.Success(1026), RntParser.parseCount(Fixtures.load("rnt_count.json")))
        assertEquals(ApiResult.Success(0), RntParser.parseCount("""[{"count":"0"}]"""))
    }

    @Test
    fun count_withoutANumber_isInvalidResponse() {
        assertEquals(ApiError.InvalidResponse, failure(RntParser.parseCount("[]")))
        assertEquals(ApiError.InvalidResponse, failure(RntParser.parseCount("""[{"count":"abc"}]""")))
        assertEquals(ApiError.InvalidResponse, failure(RntParser.parseCount("""[{"total":"3"}]""")))
        assertEquals(ApiError.InvalidResponse, failure(RntParser.parseCount("no es json")))
    }

    // ---- Valores de los desplegables ----

    @Test
    fun values_listTheGroupedField() {
        val result = RntParser.parseValues(Fixtures.load("rnt_departments.json"), RntFields.DEPARTMENT)
        assertEquals(
            listOf("AMAZONAS", "ANTIOQUIA", "ARAUCA", "NARIÑO", "NO DETERMINADO"),
            (result as ApiResult.Success).value
        )
    }

    @Test
    fun values_skipEmptyNullAndMissing() {
        val body = """[{"municipio":"TUNJA"},{"municipio":null},{"municipio":"  "},{"otro":"x"},{"municipio":"SOGAMOSO"}]"""
        assertEquals(
            listOf("TUNJA", "SOGAMOSO"),
            (RntParser.parseValues(body, RntFields.MUNICIPALITY) as ApiResult.Success).value
        )
    }

    @Test
    fun values_ofAnInvalidBody_isInvalidResponse() {
        assertEquals(ApiError.InvalidResponse, failure(RntParser.parseValues("{}", RntFields.CATEGORY)))
    }
}
