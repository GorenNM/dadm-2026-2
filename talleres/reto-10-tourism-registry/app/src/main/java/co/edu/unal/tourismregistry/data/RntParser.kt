package co.edu.unal.tourismregistry.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Del JSON de Socrata a los modelos, con el `org.json` del SDK de Android (lo que pide el enunciado).
 *
 * Ojo con ese org.json: `optString` devuelve el texto "null" para un valor null, así que se mira
 * `has`/`isNull` antes de leer. Y los números del dataset llegan como string ("11", "0").
 */
object RntParser {

    /** El dataset usa este texto cuando no tiene el dato. */
    private const val NO_DATA = "sin dato"

    fun parseProviders(body: String): ApiResult<List<Provider>> = parseArray(body) { array ->
        buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                toProvider(row)?.let(::add)
            }
        }
    }

    /** `[{"count":"1026"}]` -> 1026. */
    fun parseCount(body: String): ApiResult<Int> = parseArray(body) { array ->
        array.optJSONObject(0)?.text("count")?.toIntOrNull()
    }.flatMap { count ->
        if (count == null) ApiResult.Failure(ApiError.InvalidResponse) else ApiResult.Success(count)
    }

    /** Los valores distintos de una columna (`$select=campo&$group=campo`), sin vacíos. */
    fun parseValues(body: String, field: String): ApiResult<List<String>> = parseArray(body) { array ->
        buildList {
            for (i in 0 until array.length()) {
                val value = array.optJSONObject(i)?.text(field).orEmpty()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    /** El `message` del JSON de error de Socrata (`{"code":..., "error":true, "message":...}`), o null. */
    fun errorMessage(body: String): String? = try {
        JSONObject(body.trim()).text("message").takeIf { it.isNotEmpty() }
    } catch (_: JSONException) {
        null
    }

    private fun toProvider(row: JSONObject): Provider? {
        val code = row.text(RntFields.CODE)
        if (code.isEmpty()) return null
        return Provider(
            code = code,
            name = row.text(RntFields.NAME),
            category = row.text(RntFields.CATEGORY),
            subCategory = row.text(RntFields.SUB_CATEGORY),
            providerType = row.text(RntFields.PROVIDER_TYPE),
            address = row.text(RntFields.ADDRESS),
            municipality = row.text(RntFields.MUNICIPALITY),
            department = row.text(RntFields.DEPARTMENT),
            phone = row.text(RntFields.PHONE),
            mobile = row.text(RntFields.MOBILE),
            rooms = row.text(RntFields.ROOMS).toIntOrNull(),
            beds = row.text(RntFields.BEDS).toIntOrNull(),
            employees = row.text(RntFields.EMPLOYEES).toIntOrNull()
        )
    }

    /** Texto de un campo: "" si falta, es null o dice "Sin dato"; sin espacios sobrantes. */
    private fun JSONObject.text(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val value = optString(key, "").trim()
        return if (value.equals(NO_DATA, ignoreCase = true)) "" else value
    }

    /** Un cuerpo que no es un arreglo JSON (inválido, vacío o un objeto de error) es [ApiError.InvalidResponse]. */
    private fun <T> parseArray(body: String, transform: (JSONArray) -> T): ApiResult<T> = try {
        ApiResult.Success(transform(JSONArray(body.trim())))
    } catch (_: JSONException) {
        ApiResult.Failure(ApiError.InvalidResponse)
    }
}
