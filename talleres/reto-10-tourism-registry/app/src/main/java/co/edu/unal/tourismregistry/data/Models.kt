package co.edu.unal.tourismregistry.data

/**
 * Un prestador del Registro Nacional de Turismo (una fila del dataset). Los campos que el servicio no trae o
 * trae como "Sin dato" quedan vacíos (o null en los números). Los correos del dataset no se leen a propósito:
 * en viviendas turísticas y guías son de personas naturales.
 */
data class Provider(
    /** Código RNT: único en el dataset, es la llave para volver a pedir el detalle. */
    val code: String,
    val name: String,
    val category: String = "",
    val subCategory: String = "",
    val providerType: String = "",
    val address: String = "",
    val municipality: String = "",
    val department: String = "",
    val phone: String = "",
    val mobile: String = "",
    val rooms: Int? = null,
    val beds: Int? = null,
    val employees: Int? = null
) {

    /** Primer número de [mobile] o [phone] que sirva para marcar (solo dígitos y +), o null. */
    val dialNumber: String? get() = PhoneNumbers.firstDialable(mobile, phone)

    /** Texto para buscar el lugar en un mapa: "dirección, municipio, departamento" sin partes vacías. */
    val mapQuery: String
        get() = listOf(address, municipality, department).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Los filtros de la búsqueda. null (o texto vacío en el nombre) = "Todos": esa condición no se envía.
 * Serializable para guardarlo tal cual en el SavedStateHandle.
 */
data class ProviderFilter(
    val name: String = "",
    val department: String? = null,
    val municipality: String? = null,
    val category: String? = null
) : java.io.Serializable {
    val isEmpty: Boolean get() = name.isBlank() && department == null && municipality == null && category == null
}

/** Una página de resultados y la consulta (URL ya decodificada) que la trajo, para mostrarla en pantalla. */
data class Page(val providers: List<Provider>, val query: String)

/** Teléfonos del dataset: vienen cosas como "3102749973", "Sin dato", "0" o "601 123 4567 / 3001112233". */
object PhoneNumbers {

    private const val MIN_DIGITS = 7
    private const val MAX_DIGITS = 15

    /** Número marcable de un texto (admite varios separados por / , ;), o null si ninguno sirve. */
    fun dialable(raw: String): String? {
        for (candidate in raw.split('/', ',', ';')) {
            val cleaned = candidate.filter { it.isDigit() || it == '+' }
            val digits = cleaned.count { it.isDigit() }
            // '+' solo vale al comienzo; los demás signos (espacios, guiones, paréntesis) ya se quitaron
            val validPlus = '+' !in cleaned || (cleaned.startsWith('+') && cleaned.count { it == '+' } == 1)
            if (validPlus && digits in MIN_DIGITS..MAX_DIGITS) return cleaned
        }
        return null
    }

    fun firstDialable(vararg raws: String): String? = raws.firstNotNullOfOrNull(::dialable)
}
