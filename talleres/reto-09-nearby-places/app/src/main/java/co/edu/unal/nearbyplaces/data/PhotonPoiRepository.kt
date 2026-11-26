package co.edu.unal.nearbyplaces.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

/** [PoiRepository] sobre Photon (datos de OpenStreetMap). Una consulta por categoría, en el hilo de IO. */
class PhotonPoiRepository(private val http: HttpGet) : PoiRepository {

    override suspend fun nearby(center: GeoPoint, radiusKm: Int, category: PoiCategory): CategoryResult =
        withContext(Dispatchers.IO) {
            val body = http.get(PhotonUrlBuilder.build(center, radiusKm, category))
            try {
                CategoryResult(
                    category = category,
                    pois = PhotonParser.parse(body, category, center),
                    // Se cuenta lo que Photon devolvió, no lo que quedó tras descartar los lugares sin nombre
                    truncated = PhotonParser.featureCount(body) >= PhotonUrlBuilder.MAX_RESULTS
                )
            } catch (e: JSONException) {
                throw IOException("Respuesta de Photon ilegible", e)
            }
        }
}
