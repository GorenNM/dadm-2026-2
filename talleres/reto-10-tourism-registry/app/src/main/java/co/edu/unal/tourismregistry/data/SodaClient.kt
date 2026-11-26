package co.edu.unal.tourismregistry.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Hace un GET y devuelve el cuerpo. Una interfaz para que el repositorio se pruebe sin red. */
interface SodaClient {
    suspend fun get(url: String): ApiResult<String>
}

/**
 * [SodaClient] con `HttpURLConnection` (sin librerías), siempre fuera del hilo principal. Con tiempos máximos,
 * `Accept: application/json`, lectura del `errorStream` cuando el servicio responde un error, y cierre de los
 * streams y de la conexión pase lo que pase.
 *
 * Cancelación: HttpURLConnection es bloqueante y no escucha a las corrutinas. Si la búsqueda se cancela (el usuario
 * busca otra cosa), withContext descarta la respuesta apenas llega (o vence el timeout) y lanza
 * CancellationException: la UI nunca ve el resultado viejo.
 */
class HttpSodaClient(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS
) : SodaClient {

    companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }

    override suspend fun get(url: String): ApiResult<String> = withContext(dispatcher) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            if (status in 200..299) {
                ApiResult.Success(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } else {
                val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                ApiResult.Failure(ApiError.Server(status, RntParser.errorMessage(body)))
            }
        } catch (e: IOException) {
            ApiResult.Failure(ApiError.Network(e.toReason()))
        } finally {
            connection?.disconnect()
        }
    }

    private fun IOException.toReason() = when (this) {
        is SocketTimeoutException -> NetworkReason.TIMEOUT
        is UnknownHostException -> NetworkReason.UNKNOWN_HOST
        else -> NetworkReason.OTHER
    }
}
