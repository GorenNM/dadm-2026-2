package co.edu.unal.tourismregistry.data

import java.net.URLEncoder
import java.util.Locale

/** Nombres de las columnas del dataset (constantes: nunca vienen del usuario). */
object RntFields {
    const val CODE = "codigo_rnt"
    const val DEPARTMENT = "departamento"
    const val MUNICIPALITY = "municipio"
    const val CATEGORY = "categoria"
    const val SUB_CATEGORY = "sub_categoria"
    const val PROVIDER_TYPE = "tipo_prestador"
    const val NAME = "razon_social_establecimiento"
    const val ADDRESS = "direcci_n_comercial_establecimiento"
    const val PHONE = "n_m_tel_fono"
    const val MOBILE = "num_celular"
    const val ROOMS = "habitaciones"
    const val BEDS = "camas"
    const val EMPLOYEES = "num_emp1"
}

/** Una consulta SoQL lista: parámetros ($where, $limit...) más la URL base del dataset. */
class SodaQuery(private val baseUrl: String, val params: List<Pair<String, String>>) {

    /** La URL real: los valores codificados (espacios como %20, no +). Los nombres de parámetro son constantes. */
    val url: String
        get() = baseUrl + "?" + params.joinToString("&") { (key, value) -> key + "=" + encode(value) }

    /** La misma consulta sin codificar, para leerla en pantalla. */
    val readableUrl: String
        get() = baseUrl + "?" + params.joinToString("&") { (key, value) -> "$key=$value" }

    companion object {
        /** URLEncoder deja los espacios como "+", que en un query de Socrata sería un "+" literal: se pasan a %20. */
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}

/**
 * Arma las consultas SoQL (Socrata) al dataset. Kotlin puro, sin Android, para poder probarlo en la JVM.
 *
 * Escape: todo valor que viene del usuario o de un desplegable va entre comillas simples y con `'` doblada (`''`).
 * En el filtro por nombre (`contains`) el servicio trata `%` y `_` como comodines de LIKE, así que además se
 * escapan con `\` (verificado contra el servicio: `contains(..., '1\%')` busca un % literal).
 */
class SodaQueryBuilder(private val baseUrl: String = DEFAULT_BASE_URL) {

    companion object {
        const val DEFAULT_BASE_URL = "https://www.datos.gov.co/resource/thwd-ivmp.json"
        const val PAGE_SIZE = 20

        /** Orden estable para paginar: el nombre no es único, el código RNT desempata. */
        private const val PAGE_ORDER = "${RntFields.NAME},${RntFields.CODE}"

        /** `'` -> `''`: lo único que hace falta para meter texto libre en un literal SoQL entre comillas simples. */
        fun escapeLiteral(value: String): String = value.replace("'", "''")

        /** Para patrones LIKE (y `contains`): la barra y los comodines `%` y `_` pasan a ser literales. */
        fun escapeLike(value: String): String =
            value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        fun literal(value: String): String = "'" + escapeLiteral(value) + "'"
    }

    /** El `$where` del filtro, o null si no hay ninguna condición ("Todos" en todo). */
    fun where(filter: ProviderFilter): String? {
        val conditions = buildList {
            filter.department?.let { add("${RntFields.DEPARTMENT} = ${literal(it)}") }
            filter.municipality?.let { add("${RntFields.MUNICIPALITY} = ${literal(it)}") }
            filter.category?.let { add("${RntFields.CATEGORY} = ${literal(it)}") }
            // Los valores del dataset están en mayúsculas: se compara contra upper(campo) con el texto en mayúsculas
            val name = filter.name.trim()
            if (name.isNotEmpty()) {
                val pattern = literal(escapeLike(name.uppercase(Locale.ROOT)))
                add("contains(upper(${RntFields.NAME}), $pattern)")
            }
        }
        return conditions.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
    }

    /** Una página de resultados: `$limit`/`$offset` con orden estable. */
    fun page(filter: ProviderFilter, offset: Int, limit: Int = PAGE_SIZE): SodaQuery {
        require(offset >= 0) { "offset must not be negative" }
        require(limit > 0) { "limit must be positive" }
        return SodaQuery(
            baseUrl,
            buildList {
                where(filter)?.let { add("\$where" to it) }
                add("\$order" to PAGE_ORDER)
                add("\$limit" to limit.toString())
                add("\$offset" to offset.toString())
            }
        )
    }

    /** El total de filas que cumplen el filtro (la respuesta es `[{"count":"123"}]`). */
    fun count(filter: ProviderFilter): SodaQuery = SodaQuery(
        baseUrl,
        buildList {
            add("\$select" to "count(*)")
            where(filter)?.let { add("\$where" to it) }
        }
    )

    /** Un solo prestador por su código RNT. */
    fun byCode(code: String): SodaQuery = SodaQuery(
        baseUrl,
        listOf("\$where" to "${RntFields.CODE} = ${literal(code)}", "\$limit" to "1")
    )

    fun departments(): SodaQuery = distinct(RntFields.DEPARTMENT, where = null)

    /** Los municipios que tienen prestadores en ese departamento. */
    fun municipalities(department: String): SodaQuery =
        distinct(RntFields.MUNICIPALITY, where = "${RntFields.DEPARTMENT} = ${literal(department)}")

    fun categories(): SodaQuery = distinct(RntFields.CATEGORY, where = null)

    /** Valores distintos de una columna: `$select=campo&$group=campo&$order=campo`. */
    private fun distinct(field: String, where: String?): SodaQuery = SodaQuery(
        baseUrl,
        buildList {
            add("\$select" to field)
            where?.let { add("\$where" to it) }
            add("\$group" to field)
            add("\$order" to field)
            // Sin $limit el servicio corta en 1000; ningún desplegable se acerca, pero así queda explícito
            add("\$limit" to "2000")
        }
    )
}
