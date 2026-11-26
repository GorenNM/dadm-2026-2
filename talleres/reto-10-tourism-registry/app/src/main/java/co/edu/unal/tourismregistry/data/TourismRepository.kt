package co.edu.unal.tourismregistry.data

/** Lo que las pantallas saben del Registro Nacional de Turismo: sin URLs, sin JSON, sin excepciones. */
interface TourismRepository {
    suspend fun departments(): ApiResult<List<String>>

    /** Municipios con prestadores en [department]. */
    suspend fun municipalities(department: String): ApiResult<List<String>>

    suspend fun categories(): ApiResult<List<String>>

    /** Cuántos prestadores cumplen [filter] (todas las páginas). */
    suspend fun count(filter: ProviderFilter): ApiResult<Int>

    /** Una página de [limit] prestadores a partir de [offset], siempre en el mismo orden. */
    suspend fun page(filter: ProviderFilter, offset: Int, limit: Int = SodaQueryBuilder.PAGE_SIZE): ApiResult<Page>

    /** El prestador con ese código RNT, o null (éxito) si ya no existe. */
    suspend fun provider(code: String): ApiResult<Provider?>
}

/** Implementación contra el servicio SODA: arma la consulta, la envía con [SodaClient] y parsea la respuesta. */
class SodaTourismRepository(
    private val client: SodaClient,
    private val connectivity: ConnectivityChecker,
    private val queries: SodaQueryBuilder = SodaQueryBuilder()
) : TourismRepository {

    override suspend fun departments() =
        fetch(queries.departments()) { RntParser.parseValues(it, RntFields.DEPARTMENT) }

    override suspend fun municipalities(department: String) =
        fetch(queries.municipalities(department)) { RntParser.parseValues(it, RntFields.MUNICIPALITY) }

    override suspend fun categories() =
        fetch(queries.categories()) { RntParser.parseValues(it, RntFields.CATEGORY) }

    override suspend fun count(filter: ProviderFilter) = fetch(queries.count(filter), RntParser::parseCount)

    override suspend fun page(filter: ProviderFilter, offset: Int, limit: Int): ApiResult<Page> {
        val query = queries.page(filter, offset, limit)
        return fetch(query) { body -> RntParser.parseProviders(body).map { Page(it, query.readableUrl) } }
    }

    override suspend fun provider(code: String) =
        fetch(queries.byCode(code)) { body -> RntParser.parseProviders(body).map { it.firstOrNull() } }

    private suspend fun <T> fetch(query: SodaQuery, parse: (String) -> ApiResult<T>): ApiResult<T> {
        if (!connectivity.isOnline()) return ApiResult.Failure(ApiError.NoConnection)
        return client.get(query.url).flatMap(parse)
    }
}
