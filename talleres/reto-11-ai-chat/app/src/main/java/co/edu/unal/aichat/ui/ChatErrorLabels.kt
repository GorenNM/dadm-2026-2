package co.edu.unal.aichat.ui

import androidx.annotation.StringRes
import co.edu.unal.aichat.R
import co.edu.unal.aichat.data.ChatError

/** El mensaje de cada error: la capa de datos no sabe de textos ni de idiomas. */
@get:StringRes
val ChatError.messageRes: Int
    get() = when (this) {
        ChatError.NETWORK -> R.string.error_network
        ChatError.QUOTA -> R.string.error_quota
        ChatError.CONFIGURATION -> R.string.error_configuration
        ChatError.BLOCKED -> R.string.error_blocked
        ChatError.APP_CHECK -> R.string.error_app_check
        ChatError.LOCATION -> R.string.error_location
        ChatError.INTERRUPTED -> R.string.error_interrupted
        ChatError.UNKNOWN -> R.string.error_unknown
    }
