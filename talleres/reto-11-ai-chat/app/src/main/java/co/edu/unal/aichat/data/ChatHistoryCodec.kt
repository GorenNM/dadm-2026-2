package co.edu.unal.aichat.data

/**
 * Convierte la conversación a un String para el SavedStateHandle y de vuelta. Formato propio y simple
 * (no org.json, que en los tests de la JVM no existe): un registro por mensaje,
 * `id US rol US estado US texto`, con RS entre registros. US y RS son caracteres de control que
 * nadie escribe, y se quitan del texto antes de guardar.
 */
object ChatHistoryCodec {

    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'

    /** Guarda como mucho los últimos [maxMessages], y descarta los más viejos hasta caber en [maxChars] de texto. */
    fun encode(
        messages: List<ChatMessage>,
        maxMessages: Int = ChatConfig.MAX_HISTORY_MESSAGES,
        maxChars: Int = ChatConfig.MAX_SAVED_CHARS
    ): String {
        var kept = messages.takeLast(maxMessages).map { it.copy(text = it.text.sanitized()) }
        var chars = kept.sumOf { it.text.length }
        while (chars > maxChars && kept.size > 1) {
            chars -= kept.first().text.length
            kept = kept.drop(1)
        }
        // Un único mensaje más grande que el tope: se queda con el principio
        kept = kept.map { if (it.text.length > maxChars) it.copy(text = it.text.take(maxChars)) else it }
        return kept.joinToString(RECORD.toString()) { "${it.id}$FIELD${it.role.code}$FIELD${it.status.code}$FIELD${it.text}" }
    }

    /** Lo que no se entienda se descarta: un valor viejo o dañado nunca debe tumbar la app. */
    fun decode(encoded: String?): List<ChatMessage> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return encoded.split(RECORD).mapNotNull { record ->
            val fields = record.split(FIELD, limit = 4)
            if (fields.size != 4) return@mapNotNull null
            val id = fields[0].toLongOrNull() ?: return@mapNotNull null
            val role = fields[1].singleOrNull()?.let(Role::fromCode) ?: return@mapNotNull null
            val status = fields[2].singleOrNull()?.let(MessageStatus::fromCode) ?: return@mapNotNull null
            ChatMessage(id, role, fields[3], status)
        }
    }

    private fun String.sanitized() = replace(FIELD, ' ').replace(RECORD, ' ')
}
