package co.edu.unal.nearbyplaces.data

/** Cómo terminó el intento de saber dónde está el usuario. */
sealed interface LocationOutcome {
    data class Found(val point: GeoPoint) : LocationOutcome

    /** La ubicación del sistema está apagada (o no hay ningún proveedor que se pueda usar). */
    data object Disabled : LocationOutcome

    /** Había proveedor pero no dio posición a tiempo (sin señal de GPS, por ejemplo). */
    data object NoFix : LocationOutcome
}

interface LocationProvider {

    /** Una sola posición, no un flujo: la app no se mueve sola cada vez que cambia el GPS. Requiere el permiso. */
    suspend fun currentLocation(): LocationOutcome
}
