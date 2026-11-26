package co.edu.unal.tourismregistry.data

/** Por qué falló un pedido. La UI decide el texto; ninguna excepción cruza hacia los ViewModels. */
sealed interface ApiError {
    /** El teléfono no tiene red (ConnectivityManager); ni siquiera se intentó el pedido. */
    data object NoConnection : ApiError

    /** El pedido salió pero no hubo respuesta (tiempo agotado, DNS, conexión cortada). */
    data class Network(val reason: NetworkReason) : ApiError

    /** El servicio respondió con HTTP >= 400. [message] es el "message" del JSON de error de Socrata, si vino. */
    data class Server(val code: Int, val message: String?) : ApiError

    /** Llegó un 200 pero el cuerpo no es el JSON esperado. */
    data object InvalidResponse : ApiError
}

enum class NetworkReason { TIMEOUT, UNKNOWN_HOST, OTHER }

/** Éxito con valor, o fallo tipado. */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(value)
    is ApiResult.Failure -> this
}
