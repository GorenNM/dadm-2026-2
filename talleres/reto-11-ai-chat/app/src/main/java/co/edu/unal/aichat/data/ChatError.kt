package co.edu.unal.aichat.data

/** Por qué falló un envío. La interfaz solo conoce esto, nunca las excepciones del SDK. */
enum class ChatError {
    /** Sin conexión, o la petición tardó demasiado. */
    NETWORK,

    /** Se acabó la cuota o el límite de peticiones (HTTP 429). */
    QUOTA,

    /** Proyecto mal configurado: la API no está habilitada, la clave es inválida, falta un permiso (HTTP 403). */
    CONFIGURATION,

    /** El filtro de seguridad bloqueó la pregunta o la respuesta. */
    BLOCKED,

    /** App Check rechazó la llamada: token de depuración sin registrar, attestation fallida... (HTTP 403 de App Check). */
    APP_CHECK,

    /** El servicio no está disponible en la región del usuario. */
    LOCATION,

    /** La respuesta se cortó porque la app se cerró a mitad (no viene del servicio: lo pone el ViewModel). */
    INTERRUPTED,

    UNKNOWN
}

/** Lo único que un [ChatClient] lanza, para que quien lo use no dependa del SDK. */
class ChatException(val error: ChatError, cause: Throwable? = null) : Exception(error.name, cause)
