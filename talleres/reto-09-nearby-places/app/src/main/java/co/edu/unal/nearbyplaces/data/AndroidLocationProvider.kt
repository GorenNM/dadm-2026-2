package co.edu.unal.nearbyplaces.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [LocationProvider] con `LocationManager` (sin Play Services). Pide una posición nueva con
 * [LocationManagerCompat.getCurrentLocation] al GPS y, si no contesta a tiempo, a la red; si ninguno
 * responde, cae en la última posición conocida.
 */
@SuppressLint("MissingPermission") // El permiso se pide en la Activity antes de llamar; acá se revisa y se atrapa SecurityException
class AndroidLocationProvider(
    context: Context,
    private val timeoutMs: Long = TIMEOUT_MS
) : LocationProvider {

    companion object {
        /** Tiempo total para conseguir una posición nueva, repartido entre los proveedores disponibles. */
        const val TIMEOUT_MS = 15_000L
    }

    private val mContext = context.applicationContext
    private val mManager = mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override suspend fun currentLocation(): LocationOutcome {
        if (!LocationManagerCompat.isLocationEnabled(mManager)) return LocationOutcome.Disabled

        // Con solo el permiso aproximado el GPS está vedado: solo la red
        val providers = buildList {
            if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { isEnabled(it) }
        if (providers.isEmpty()) return LocationOutcome.Disabled

        for (provider in providers) {
            val fresh = withTimeoutOrNull(timeoutMs / providers.size) { current(provider) }
            if (fresh != null) return LocationOutcome.Found(fresh.toGeoPoint())
        }

        val last = providers.mapNotNull { lastKnown(it) }.maxByOrNull { it.time }
        return if (last != null) LocationOutcome.Found(last.toGeoPoint()) else LocationOutcome.NoFix
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(mContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun isEnabled(provider: String) = try {
        mManager.isProviderEnabled(provider)
    } catch (e: IllegalArgumentException) {
        false
    }

    /** Una posición nueva de [provider]; null si el proveedor no la pudo dar o no hay permiso. Cancelable. */
    private suspend fun current(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(
                mManager, provider, signal, ContextCompat.getMainExecutor(mContext),
                Consumer<Location?> { location -> if (continuation.isActive) continuation.resume(location) }
            )
        } catch (e: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun lastKnown(provider: String): Location? = try {
        mManager.getLastKnownLocation(provider)
    } catch (e: SecurityException) {
        null
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)
}
