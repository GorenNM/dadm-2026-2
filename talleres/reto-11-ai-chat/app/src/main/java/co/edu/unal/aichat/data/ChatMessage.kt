package co.edu.unal.aichat.data

enum class Role(val code: Char) {
    USER('u'),
    MODEL('m');

    companion object {
        fun fromCode(code: Char): Role? = entries.firstOrNull { it.code == code }
    }
}

enum class MessageStatus(val code: Char) {
    COMPLETE('c'),

    /** La respuesta del modelo todavía está llegando. */
    STREAMING('s'),

    /** El usuario tocó Detener: se conserva lo recibido. */
    STOPPED('x'),

    /** Se cortó a mitad (error o el proceso murió): se conserva lo recibido. */
    INTERRUPTED('i');

    companion object {
        fun fromCode(code: Char): MessageStatus? = entries.firstOrNull { it.code == code }
    }
}

/** Un mensaje de la conversación. [id] es único dentro de la conversación y estable: DiffUtil lo usa para animar. */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val status: MessageStatus = MessageStatus.COMPLETE
)

/**
 * Lo que se le manda al modelo: sin mensajes vacíos (la burbuja que está por llenarse), los últimos
 * [ChatConfig.MAX_HISTORY_MESSAGES], y empezando por un mensaje del usuario.
 */
fun List<ChatMessage>.forModel(): List<ChatMessage> =
    filter { it.text.isNotBlank() }
        .takeLast(ChatConfig.MAX_HISTORY_MESSAGES)
        .dropWhile { it.role != Role.USER }
