package co.edu.unal.nearbyplaces.data

import java.net.URLEncoder
import java.util.Locale

/** Arma la URL de `reverse` de Photon (https://github.com/komoot/photon). */
object PhotonUrlBuilder {

    const val BASE_URL = "https://photon.komoot.io/reverse"

    /** Photon devuelve como máximo 50 resultados por consulta, aunque se pida más. */
    const val MAX_RESULTS = 50

    /**
     * Lugares de [category] a [radiusKm] de [center]. Cada `osm_tag` va repetido (Photon los combina con
     * OR) y codificado: los `:` salen como `%3A`.
     */
    fun build(center: GeoPoint, radiusKm: Int, category: PoiCategory, limit: Int = MAX_RESULTS): String {
        val tags = category.osmTags.joinToString("") { "&osm_tag=" + URLEncoder.encode(it, "UTF-8") }
        // Locale.ROOT: con la regional en español el decimal saldría con coma
        val lat = String.format(Locale.ROOT, "%.6f", center.latitude)
        val lon = String.format(Locale.ROOT, "%.6f", center.longitude)
        return "$BASE_URL?lat=$lat&lon=$lon&radius=$radiusKm&limit=$limit$tags"
    }
}
