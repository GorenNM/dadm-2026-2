package co.edu.unal.nearbyplaces.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Un GET que devuelve el cuerpo como texto. Bloquea: se llama desde un hilo de IO. Lanza [IOException] si falla. */
fun interface HttpGet {
    @Throws(IOException::class)
    fun get(url: String): String
}

/** [HttpGet] con `HttpURLConnection`: sin dependencias extra, con `User-Agent` propio y tiempos límite. */
class UrlConnectionHttpGet(
    private val userAgent: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000
) : HttpGet {

    override fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
