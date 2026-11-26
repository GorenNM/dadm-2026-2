package co.edu.unal.tourismregistry.ui

import co.edu.unal.tourismregistry.data.ApiError
import co.edu.unal.tourismregistry.data.ApiResult
import co.edu.unal.tourismregistry.data.Page
import co.edu.unal.tourismregistry.data.Provider
import co.edu.unal.tourismregistry.data.ProviderFilter
import co.edu.unal.tourismregistry.data.TourismRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException

data class PageCall(val filter: ProviderFilter, val offset: Int, val limit: Int)

/**
 * Un repositorio en memoria para los tests de los ViewModels: filtra una lista como lo haría el servicio, registra
 * cada pedido y deja fabricar fallos o dejar un pedido colgado ([gate]) para probar cancelaciones.
 */
class FakeTourismRepository(var data: List<Provider> = emptyList()) : TourismRepository {

    var departments: ApiResult<List<String>> = ApiResult.Success(listOf("ANTIOQUIA", "BOYACA"))
    var categories: ApiResult<List<String>> = ApiResult.Success(listOf("AGENCIAS DE VIAJES", "BARES"))
    var municipalitiesByDepartment: Map<String, List<String>> =
        mapOf("BOYACA" to listOf("TUNJA", "VILLA DE LEYVA"), "ANTIOQUIA" to listOf("MEDELLIN"))
    var municipalitiesFailure: ApiError? = null

    /** Si no es null, [count] devuelve esto en vez del conteo real. */
    var countOverride: ApiResult<Int>? = null

    /** Un fallo por pedido de página, en orden; cuando se acaban, las páginas salen bien. */
    val pageFailures = ArrayDeque<ApiError>()

    /** Mientras esté puesto, [page] se queda esperando a que se complete. */
    var gate: CompletableDeferred<Unit>? = null

    var providerFailure: ApiError? = null

    val pageCalls = mutableListOf<PageCall>()
    val countCalls = mutableListOf<ProviderFilter>()
    val municipalityCalls = mutableListOf<String>()
    val providerCalls = mutableListOf<String>()
    var departmentCalls = 0
    var cancelledPageCalls = 0

    private fun matches(filter: ProviderFilter) = data.filter { provider ->
        (filter.name.isBlank() || provider.name.contains(filter.name.trim(), ignoreCase = true)) &&
            (filter.department == null || provider.department == filter.department) &&
            (filter.municipality == null || provider.municipality == filter.municipality) &&
            (filter.category == null || provider.category == filter.category)
    }

    override suspend fun departments(): ApiResult<List<String>> {
        departmentCalls++
        return departments
    }

    override suspend fun categories() = categories

    override suspend fun municipalities(department: String): ApiResult<List<String>> {
        municipalityCalls += department
        municipalitiesFailure?.let { return ApiResult.Failure(it) }
        return ApiResult.Success(municipalitiesByDepartment[department].orEmpty())
    }

    override suspend fun count(filter: ProviderFilter): ApiResult<Int> {
        countCalls += filter
        return countOverride ?: ApiResult.Success(matches(filter).size)
    }

    override suspend fun page(filter: ProviderFilter, offset: Int, limit: Int): ApiResult<Page> {
        pageCalls += PageCall(filter, offset, limit)
        try {
            gate?.await()
        } catch (e: CancellationException) {
            cancelledPageCalls++
            throw e
        }
        pageFailures.removeFirstOrNull()?.let { return ApiResult.Failure(it) }
        return ApiResult.Success(Page(matches(filter).drop(offset).take(limit), "query offset=$offset limit=$limit"))
    }

    override suspend fun provider(code: String): ApiResult<Provider?> {
        providerCalls += code
        providerFailure?.let { return ApiResult.Failure(it) }
        return ApiResult.Success(data.firstOrNull { it.code == code })
    }
}

/** [count] prestadores "PRESTADOR 01..": los primeros [inBoyaca] en Tunja (BOYACA), el resto en Medellín (ANTIOQUIA). */
fun sampleProviders(count: Int, inBoyaca: Int = count): List<Provider> = (1..count).map { i ->
    val boyaca = i <= inBoyaca
    Provider(
        code = "$i",
        name = "PRESTADOR %02d".format(i),
        category = if (i % 2 == 0) "BARES" else "AGENCIAS DE VIAJES",
        municipality = if (boyaca) "TUNJA" else "MEDELLIN",
        department = if (boyaca) "BOYACA" else "ANTIOQUIA"
    )
}
