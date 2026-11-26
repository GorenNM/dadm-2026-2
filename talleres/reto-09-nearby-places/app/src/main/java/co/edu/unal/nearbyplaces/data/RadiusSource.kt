package co.edu.unal.nearbyplaces.data

import android.content.Context
import androidx.preference.PreferenceManager

/** De dónde sale el radio de búsqueda (en km). Una interfaz pequeña para poder testear el ViewModel sin Android. */
fun interface RadiusSource {
    fun radiusKm(): Int
}

/** Las preferencias de la app: la pantalla de ajustes guarda el radio con [KEY] y esto lo lee. */
class PreferencesRadiusSource(context: Context) : RadiusSource {

    companion object {
        /** Mismo valor que `android:key` del `SeekBarPreference` en `res/xml/preferences.xml`. */
        const val KEY = "radius_km"
        const val MIN_KM = 1
        const val MAX_KM = 20
        const val DEFAULT_KM = 2
    }

    private val mPrefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    override fun radiusKm(): Int = mPrefs.getInt(KEY, DEFAULT_KM).coerceIn(MIN_KM, MAX_KM)
}
