package co.edu.unal.nearbyplaces.data

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import co.edu.unal.nearbyplaces.R

/**
 * Categorías que se pueden buscar. [osmTags] son pares `clave:valor` de OpenStreetMap; los de una misma
 * categoría se combinan con OR en una sola consulta a Photon. [id] es lo que se guarda en el estado.
 */
enum class PoiCategory(
    val id: String,
    @param:StringRes val labelRes: Int,
    @param:ColorInt val color: Int,
    val osmTags: List<String>
) {
    HOSPITALS("hospitals", R.string.category_hospitals, 0xFFD32F2F.toInt(), listOf("amenity:hospital", "amenity:clinic")),
    PHARMACIES("pharmacies", R.string.category_pharmacies, 0xFF2E7D32.toInt(), listOf("amenity:pharmacy")),
    TOURISM(
        "tourism", R.string.category_tourism, 0xFF7B1FA2.toInt(),
        listOf("tourism:museum", "tourism:attraction", "tourism:viewpoint", "tourism:gallery")
    ),
    RESTAURANTS("restaurants", R.string.category_restaurants, 0xFFEF6C00.toInt(), listOf("amenity:restaurant")),
    HOTELS("hotels", R.string.category_hotels, 0xFF00838F.toInt(), listOf("tourism:hotel", "tourism:hostel")),
    POLICE("police", R.string.category_police, 0xFF283593.toInt(), listOf("amenity:police"));

    companion object {
        /** Lo que está marcado cuando se abre la app por primera vez. */
        val DEFAULTS: Set<PoiCategory> = setOf(HOSPITALS, TOURISM)

        fun fromId(id: String): PoiCategory? = entries.firstOrNull { it.id == id }
    }
}
