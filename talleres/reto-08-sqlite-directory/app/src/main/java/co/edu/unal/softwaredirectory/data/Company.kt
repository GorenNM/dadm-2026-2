package co.edu.unal.softwaredirectory.data

/** Las tres clasificaciones del enunciado; una empresa puede tener varias ("y/o"). */
enum class Classification(
    /** Lo que se guarda en la base: estable aunque se renombre la constante. */
    val code: String
) {
    CONSULTING("CONSULTING"),
    CUSTOM_DEVELOPMENT("CUSTOM_DEVELOPMENT"),
    SOFTWARE_FACTORY("SOFTWARE_FACTORY");

    companion object {
        fun fromCode(code: String): Classification? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Una empresa del directorio. Los campos opcionales vacíos son "" acá y NULL en la base.
 * [id] = [NEW_ID] mientras no se ha guardado.
 */
data class Company(
    val id: Long = NEW_ID,
    val name: String,
    val website: String = "",
    val phone: String = "",
    val email: String = "",
    val products: String = "",
    val classifications: Set<Classification> = emptySet()
) {
    companion object {
        const val NEW_ID = 0L
    }
}

/** Filtro de la lista: [query] busca dentro del nombre; [classifications] vacío = todas, si no, cualquiera de ellas. */
data class CompanyFilter(
    val query: String = "",
    val classifications: Set<Classification> = emptySet()
) {
    val isEmpty: Boolean get() = query.isBlank() && classifications.isEmpty()
}

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    /** Ya hay otra empresa con el mismo nombre (sin distinguir mayúsculas ni tildes). */
    data object DuplicateName : SaveResult
    /** Se quiso actualizar una empresa que ya no existe. */
    data object NotFound : SaveResult
}
