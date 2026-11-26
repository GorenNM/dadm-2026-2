package co.edu.unal.nearbyplaces

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import co.edu.unal.nearbyplaces.data.AddressRepository
import co.edu.unal.nearbyplaces.data.AndroidLocationProvider
import co.edu.unal.nearbyplaces.data.GeocoderAddressRepository
import co.edu.unal.nearbyplaces.data.LocationProvider
import co.edu.unal.nearbyplaces.data.PhotonPoiRepository
import co.edu.unal.nearbyplaces.data.PoiRepository
import co.edu.unal.nearbyplaces.data.PreferencesRadiusSource
import co.edu.unal.nearbyplaces.data.RadiusSource
import co.edu.unal.nearbyplaces.data.UrlConnectionHttpGet

/** Arma las dependencias de la app (sin framework de inyección: son cuatro) y aplica el tema guardado. */
class NearbyPlacesApp : Application() {

    val locationProvider: LocationProvider by lazy { AndroidLocationProvider(this) }

    // Photon pide un User-Agent que identifique la app
    val poiRepository: PoiRepository by lazy {
        PhotonPoiRepository(UrlConnectionHttpGet("NearbyPlacesR9/${BuildConfig.VERSION_NAME} (Android; course project)"))
    }

    val addressRepository: AddressRepository by lazy { GeocoderAddressRepository(this) }

    val radiusSource: RadiusSource by lazy { PreferencesRadiusSource(this) }

    /** Aplica el tema guardado antes de crear cualquier Activity: evita el parpadeo claro/oscuro al abrir la app. */
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(ThemePreference.loadNightMode(this))
    }
}
