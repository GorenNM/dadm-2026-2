package co.edu.unal.nearbyplaces.data

import org.json.JSONException
import org.json.JSONObject

/** Lee el GeoJSON (`FeatureCollection`) que devuelve Photon y lo convierte en [Poi]. */
object PhotonParser {

    /** Cuántos lugares trae la respuesta, sin descartar ninguno (para saber si llegó al tope de Photon). */
    @Throws(JSONException::class)
    fun featureCount(json: String): Int = JSONObject(json).optJSONArray("features")?.length() ?: 0

    /**
     * Los lugares de la respuesta, asignados a [category] y con su distancia a [origin].
     * Se descarta lo que no tiene nombre (no habría qué mostrar) o no tiene coordenadas.
     * Lanza [JSONException] si [json] no es JSON.
     */
    @Throws(JSONException::class)
    fun parse(json: String, category: PoiCategory, origin: GeoPoint): List<Poi> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        val pois = ArrayList<Poi>(features.length())
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val properties = feature.optJSONObject("properties") ?: continue

            val name = properties.text("name") ?: continue
            // GeoJSON va en orden [longitud, latitud]
            val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coordinates.length() < 2) continue
            val latitude = coordinates.optDouble(1, Double.NaN)
            val longitude = coordinates.optDouble(0, Double.NaN)
            if (latitude.isNaN() || longitude.isNaN()) continue
            val location = GeoPoint(latitude, longitude)

            pois += Poi(
                id = idOf(properties, category, location),
                name = name,
                category = category,
                location = location,
                distanceMeters = GeoMath.distanceMeters(origin, location),
                address = address(properties)
            )
        }
        return pois
    }

    /** "N5178923446" (tipo + número de OpenStreetMap); si falta, algo estable sacado de las coordenadas. */
    private fun idOf(properties: JSONObject, category: PoiCategory, location: GeoPoint): String {
        if (properties.isNull("osm_id")) return "${category.id}:${location.latitude},${location.longitude}"
        return properties.text("osm_type").orEmpty() + properties.opt("osm_id")
    }

    /** "Calle 46 12, Bogotá", con lo que haya. */
    private fun address(properties: JSONObject): String? {
        val street = listOfNotNull(properties.text("street"), properties.text("housenumber")).joinToString(" ")
        val parts = listOf(street, properties.text("city").orEmpty()).filter { it.isNotEmpty() }
        return parts.joinToString(", ").ifEmpty { null }
    }

    /** El texto de [key], o null si falta, es `null` de JSON o está en blanco. (`optString` devolvería "null".) */
    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).trim().ifEmpty { null }
}
