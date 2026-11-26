package co.edu.unal.nearbyplaces.data

import java.io.IOException

/**
 * Lo que devolvió la búsqueda de una categoría. [truncated]: la fuente llenó su tope de resultados, así
 * que puede haber más lugares de esta categoría que no llegaron.
 */
data class CategoryResult(val category: PoiCategory, val pois: List<Poi>, val truncated: Boolean)

interface PoiRepository {

    /** Lugares de [category] cerca de [center]. Lanza [IOException] si no hay red o la respuesta no sirve. */
    @Throws(IOException::class)
    suspend fun nearby(center: GeoPoint, radiusKm: Int, category: PoiCategory): CategoryResult
}
