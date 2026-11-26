package co.edu.unal.softwaredirectory.data

import java.text.Normalizer
import java.util.Locale

/**
 * Búsqueda sin distinguir mayúsculas ni tildes. El LIKE de SQLite solo ignora mayúsculas en ASCII
 * ("Á" ≠ "á"), así que se guarda una versión normalizada del nombre y se busca sobre ella.
 */
object SearchText {

    /** Carácter de escape para [containsPattern]: la consulta debe llevar `ESCAPE '\'`. */
    const val LIKE_ESCAPE = '\\'

    private val COMBINING_MARKS = "\\p{Mn}+".toRegex()
    private val SPACES = "\\s+".toRegex()

    /** "  Ñandú   Tecnología " → "nandu tecnologia". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .trim()
            .replace(SPACES, " ")

    /** Patrón LIKE para "contiene [query]". `%` y `_` del usuario se buscan literales, no como comodines. */
    fun containsPattern(query: String): String = "%" + escapeLike(normalize(query)) + "%"

    private fun escapeLike(text: String): String = buildString {
        for (c in text) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') append(LIKE_ESCAPE)
            append(c)
        }
    }
}
