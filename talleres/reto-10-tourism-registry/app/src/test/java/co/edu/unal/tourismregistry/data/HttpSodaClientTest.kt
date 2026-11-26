package co.edu.unal.tourismregistry.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * El cliente HTTP contra un servidor local de verdad (com.sun.net.httpserver, solo para tests): sin Internet y sin
 * depender de datos.gov.co. Robolectric porque el mensaje de error pasa por org.json.
 */
@RunWith(RobolectricTestRunner::class)
class HttpSodaClientTest {

    private lateinit var server: HttpServer
    private val acceptHeaders = CopyOnWriteArrayList<String?>()

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun reply(path: String, status: Int, body: String, delayMs: Long = 0) {
        server.createContext(path) { exchange ->
            acceptHeaders += exchange.requestHeaders.getFirst("Accept")
            if (delayMs > 0) Thread.sleep(delayMs)
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        reply("/ok", 200, Fixtures.load("rnt_page.json"))
        reply("/bad", 400, Fixtures.load("rnt_error_400.json"))
        reply("/html", 502, "<html>Bad Gateway</html>")
        reply("/forbidden", 403, "")
        reply("/slow", 200, "[]", delayMs = 1_500)
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun get(path: String, client: HttpSodaClient = HttpSodaClient(Dispatchers.IO)) =
        runBlocking { client.get(url(path)) }

    @Test
    fun ok_returnsTheBody_inUtf8() {
        val result = get("/ok") as ApiResult.Success
        assertTrue(result.value.contains("VIVIENDAS TURÍSTICAS"))
    }

    @Test
    fun sendsAcceptJson() {
        get("/ok")
        assertEquals("application/json", acceptHeaders.single())
    }

    @Test
    fun serverError_readsTheErrorStream_andExtractsTheMessage() {
        val error = (get("/bad") as ApiResult.Failure).error as ApiError.Server
        assertEquals(400, error.code)
        assertTrue(error.message!!.startsWith("Could not parse SoQL query"))
    }

    @Test
    fun serverError_withoutJson_hasCodeAndNoMessage() {
        assertEquals(ApiResult.Failure(ApiError.Server(502, null)), get("/html"))
        assertEquals(ApiResult.Failure(ApiError.Server(403, null)), get("/forbidden"))
    }

    @Test
    fun slowServer_isATimeout() {
        val client = HttpSodaClient(Dispatchers.IO, connectTimeoutMs = 500, readTimeoutMs = 300)
        assertEquals(ApiResult.Failure(ApiError.Network(NetworkReason.TIMEOUT)), get("/slow", client))
    }

    @Test
    fun unreachableServer_isANetworkError() {
        val deadUrl = url("/ok")
        server.stop(0)
        val result = runBlocking { HttpSodaClient(Dispatchers.IO).get(deadUrl) }
        assertEquals(ApiResult.Failure(ApiError.Network(NetworkReason.OTHER)), result)
    }

    @Test
    fun unknownHost_isReportedAsSuch() {
        // El dominio .invalid está reservado (RFC 2606): nunca resuelve
        val result = runBlocking { HttpSodaClient(Dispatchers.IO).get("http://sin-servidor.invalid/x") }
        val error = (result as ApiResult.Failure).error as ApiError.Network
        assertTrue(error.reason == NetworkReason.UNKNOWN_HOST || error.reason == NetworkReason.OTHER)
    }
}
