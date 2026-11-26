package co.edu.unal.tourismregistry.ui

import android.content.Context
import co.edu.unal.tourismregistry.R
import co.edu.unal.tourismregistry.data.ApiError
import co.edu.unal.tourismregistry.data.NetworkReason

/** El texto (en el idioma del teléfono) para cada fallo; compartido por la búsqueda y el detalle. */
fun ApiError.describe(context: Context): String = when (this) {
    ApiError.NoConnection -> context.getString(R.string.error_no_connection)
    is ApiError.Network -> context.getString(
        when (reason) {
            NetworkReason.TIMEOUT -> R.string.error_timeout
            NetworkReason.UNKNOWN_HOST -> R.string.error_unknown_host
            NetworkReason.OTHER -> R.string.error_network
        }
    )
    is ApiError.Server ->
        if (message.isNullOrBlank()) context.getString(R.string.error_server, code)
        else context.getString(R.string.error_server_with_message, code, message)
    ApiError.InvalidResponse -> context.getString(R.string.error_invalid_response)
}
