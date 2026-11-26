package co.edu.unal.tourismregistry.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** El repositorio con un cliente falso: qué URL pide, qué hace con la respuesta y que sin red ni lo intenta. */
@RunWith(RobolectricTestRunner::class)
class SodaTourismRepositoryTest {

    private class FakeClient(var response: ApiResult<String> = ApiResult.Success("[]")) : SodaClient {
        val urls = mutableListOf<String>()

        override suspend fun get(url: String): ApiResult<String> {
            urls += url
            return response
        }
    }

    private val client = FakeClient()
    private var online = true
    private val repository = SodaTourismRepository(client, ConnectivityChecker { online })

    @Test
    fun offline_failsWithNoConnection_withoutCallingTheService() = runBlocking {
        online = false
        assertEquals(ApiResult.Failure(ApiError.NoConnection), repository.departments())
        assertEquals(ApiResult.Failure(ApiError.NoConnection), repository.count(ProviderFilter()))
        assertEquals(ApiResult.Failure(ApiError.NoConnection), repository.page(ProviderFilter(), 0))
        assertEquals(ApiResult.Failure(ApiError.NoConnection), repository.provider("1"))
        assertTrue(client.urls.isEmpty())
    }

    @Test
    fun page_asksForTheRightUrl_andReturnsProvidersWithTheReadableQuery() = runBlocking {
        client.response = ApiResult.Success(Fixtures.load("rnt_page.json"))
        val filter = ProviderFilter(department = "BOYACA", municipality = "VILLA DE LEYVA")

        val page = (repository.page(filter, offset = 20, limit = 20) as ApiResult.Success).value

        assertEquals(3, page.providers.size)
        val url = client.urls.single()
        assertTrue(url.startsWith("https://www.datos.gov.co/resource/thwd-ivmp.json?"))
        assertTrue(url.contains("%24where=").not()) // los nombres de parámetro no se codifican
        assertTrue(url.contains("\$where=departamento%20%3D%20%27BOYACA%27%20AND%20municipio"))
        assertTrue(url.contains("\$limit=20&\$offset=20"))
        assertTrue(page.query.contains("departamento = 'BOYACA' AND municipio = 'VILLA DE LEYVA'"))
        assertTrue(page.query.endsWith("\$limit=20&\$offset=20"))
    }

    @Test
    fun count_parsesTheTotal() = runBlocking {
        client.response = ApiResult.Success(Fixtures.load("rnt_count.json"))
        assertEquals(ApiResult.Success(1026), repository.count(ProviderFilter(department = "BOYACA")))
        assertTrue(client.urls.single().contains("\$select=count%28*%29"))
    }

    @Test
    fun departments_municipalitiesAndCategories_parseTheirField() = runBlocking {
        client.response = ApiResult.Success(Fixtures.load("rnt_departments.json"))
        val departments = (repository.departments() as ApiResult.Success).value
        assertEquals(5, departments.size)

        client.response = ApiResult.Success("""[{"municipio":"TUNJA"},{"municipio":"DUITAMA"}]""")
        assertEquals(listOf("TUNJA", "DUITAMA"), (repository.municipalities("BOYACA") as ApiResult.Success).value)
        assertTrue(client.urls.last().contains("departamento%20%3D%20%27BOYACA%27"))

        client.response = ApiResult.Success("""[{"categoria":"BARES"}]""")
        assertEquals(listOf("BARES"), (repository.categories() as ApiResult.Success).value)
    }

    @Test
    fun provider_returnsTheFirstRow_orNullWhenThereIsNone() = runBlocking {
        client.response = ApiResult.Success(Fixtures.load("rnt_page.json"))
        assertEquals("24027", (repository.provider("24027") as ApiResult.Success).value?.code)
        assertTrue(client.urls.single().contains("codigo_rnt%20%3D%20%2724027%27"))

        client.response = ApiResult.Success("[]")
        assertNull((repository.provider("0") as ApiResult.Success).value)
    }

    @Test
    fun failuresFromTheClient_passThroughUnchanged() = runBlocking {
        val error = ApiError.Server(400, "malformed")
        client.response = ApiResult.Failure(error)
        assertEquals(ApiResult.Failure(error), repository.page(ProviderFilter(), 0))
        client.response = ApiResult.Failure(ApiError.Network(NetworkReason.TIMEOUT))
        assertEquals(ApiResult.Failure(ApiError.Network(NetworkReason.TIMEOUT)), repository.count(ProviderFilter()))
    }

    @Test
    fun anUnexpectedBody_isInvalidResponse() = runBlocking {
        client.response = ApiResult.Success("<html>no es json</html>")
        assertEquals(ApiResult.Failure(ApiError.InvalidResponse), repository.page(ProviderFilter(), 0))
        assertEquals(ApiResult.Failure(ApiError.InvalidResponse), repository.count(ProviderFilter()))
        assertEquals(ApiResult.Failure(ApiError.InvalidResponse), repository.provider("1"))
    }
}
