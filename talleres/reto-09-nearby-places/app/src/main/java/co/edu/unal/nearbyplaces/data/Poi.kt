package co.edu.unal.nearbyplaces.data

/**
 * Un punto de interés. [id] sale de OpenStreetMap (tipo + número, p. ej. "N5178923446") y sirve para no
 * repetir un lugar. [distanceMeters] es la distancia en línea recta desde el centro de la búsqueda.
 */
data class Poi(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val location: GeoPoint,
    val distanceMeters: Double,
    /** Calle y ciudad, si OpenStreetMap las trae. */
    val address: String? = null
)
