package co.edu.unal.tourismregistry.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** ¿Hay red? Se pregunta antes de cada pedido para poder avisar "sin conexión" en vez de esperar un timeout. */
fun interface ConnectivityChecker {
    fun isOnline(): Boolean
}

/** Red activa con capacidad de internet (wifi, datos o ethernet). Requiere ACCESS_NETWORK_STATE. */
class AndroidConnectivityChecker(context: Context) : ConnectivityChecker {

    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val network = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
