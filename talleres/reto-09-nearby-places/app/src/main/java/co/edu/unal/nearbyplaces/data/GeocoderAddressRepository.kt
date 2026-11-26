package co.edu.unal.nearbyplaces.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/** [AddressRepository] con el `Geocoder` del sistema (reverse). Cualquier fallo se vuelve null. */
class GeocoderAddressRepository(context: Context) : AddressRepository {

    private val mContext = context.applicationContext

    override suspend fun addressOf(point: GeoPoint): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(mContext, Locale.getDefault())
        val addresses = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) fetchAsync(geocoder, point) else fetchBlocking(geocoder, point)
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        return addresses?.firstOrNull()?.getAddressLine(0)?.takeIf { it.isNotBlank() }
    }

    /** API 33+: el sistema avisa por un listener, sin bloquear. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun fetchAsync(geocoder: Geocoder, point: GeoPoint): List<Address>? =
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(point.latitude, point.longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) continuation.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }

    /** Antes de API 33 la llamada bloquea: va al hilo de IO. */
    @Suppress("DEPRECATION")
    private suspend fun fetchBlocking(geocoder: Geocoder, point: GeoPoint): List<Address>? =
        withContext(Dispatchers.IO) { geocoder.getFromLocation(point.latitude, point.longitude, 1) }
}
