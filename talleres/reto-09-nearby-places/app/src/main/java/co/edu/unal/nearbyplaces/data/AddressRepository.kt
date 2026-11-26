package co.edu.unal.nearbyplaces.data

interface AddressRepository {

    /** La dirección de [point] en texto, o null si no se pudo averiguar (sin Geocoder, sin red, sin resultado). */
    suspend fun addressOf(point: GeoPoint): String?
}
