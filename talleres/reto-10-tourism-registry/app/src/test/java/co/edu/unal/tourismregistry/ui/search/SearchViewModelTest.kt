package co.edu.unal.tourismregistry.ui.search

import androidx.lifecycle.SavedStateHandle
import co.edu.unal.tourismregistry.data.ApiError
import co.edu.unal.tourismregistry.data.ApiResult
import co.edu.unal.tourismregistry.data.NetworkReason
import co.edu.unal.tourismregistry.data.ProviderFilter
import co.edu.unal.tourismregistry.ui.FakeTourismRepository
import co.edu.unal.tourismregistry.ui.sampleProviders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    /** 45 prestadores: 30 en Tunja (BOYACA) y 15 en Medellín (ANTIOQUIA). */
    private val repository = FakeTourismRepository(sampleProviders(45, inBoyaca = 30))

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = SearchViewModel(repository, savedState)

    // ---- Estado inicial y desplegables ----

    @Test
    fun startsIdle_andFillsTheDropdownsFromTheService_withoutSearching() = runTest {
        val vm = viewModel()
        assertEquals(SearchPhase.IDLE, vm.uiState.value.phase)

        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(SearchPhase.IDLE, state.phase)
        assertEquals(listOf("ANTIOQUIA", "BOYACA"), state.departments)
        assertEquals(listOf("AGENCIAS DE VIAJES", "BARES"), state.categories)
        assertTrue(state.municipalities.isEmpty())
        assertNull(state.optionsError)
        assertTrue(repository.pageCalls.isEmpty())
        assertTrue(repository.countCalls.isEmpty())
    }

    @Test
    fun choosingADepartment_loadsItsMunicipalities() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDepartmentSelected("BOYACA")
        advanceUntilIdle()

        assertEquals("BOYACA", vm.uiState.value.filter.department)
        assertEquals(listOf("TUNJA", "VILLA DE LEYVA"), vm.uiState.value.municipalities)
        assertEquals(listOf("BOYACA"), repository.municipalityCalls)
    }

    @Test
    fun changingTheDepartment_clearsTheMunicipality_andLoadsTheNewOnes() = runTest {
        val vm = viewModel()
        vm.onDepartmentSelected("BOYACA")
        advanceUntilIdle()
        vm.onMunicipalitySelected("TUNJA")
        assertEquals("TUNJA", vm.uiState.value.filter.municipality)

        vm.onDepartmentSelected("ANTIOQUIA")
        // Al instante: sin municipio elegido y sin la lista vieja
        assertNull(vm.uiState.value.filter.municipality)
        assertTrue(vm.uiState.value.municipalities.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("MEDELLIN"), vm.uiState.value.municipalities)
    }

    @Test
    fun choosingAll_inDepartment_clearsMunicipalityAndItsOptions() = runTest {
        val vm = viewModel()
        vm.onDepartmentSelected("BOYACA")
        advanceUntilIdle()
        vm.onMunicipalitySelected("TUNJA")

        vm.onDepartmentSelected(null)
        advanceUntilIdle()

        assertNull(vm.uiState.value.filter.department)
        assertNull(vm.uiState.value.filter.municipality)
        assertTrue(vm.uiState.value.municipalities.isEmpty())
        assertEquals(1, repository.municipalityCalls.size) // no se pidieron municipios de "Todos"
    }

    @Test
    fun aStaleMunicipalitiesAnswer_isDiscarded() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDepartmentSelected("BOYACA")
        vm.onDepartmentSelected("ANTIOQUIA") // antes de que llegue la respuesta de BOYACA
        advanceUntilIdle()

        assertEquals(listOf("MEDELLIN"), vm.uiState.value.municipalities)
    }

    @Test
    fun optionsFailure_isReported_andRetryLoadsThem() = runTest {
        repository.departments = ApiResult.Failure(ApiError.NoConnection)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ApiError.NoConnection, vm.uiState.value.optionsError)
        assertTrue(vm.uiState.value.departments.isEmpty())
        assertEquals(listOf("AGENCIAS DE VIAJES", "BARES"), vm.uiState.value.categories) // lo demás sí llegó

        repository.departments = ApiResult.Success(listOf("BOYACA"))
        vm.retryOptions()
        advanceUntilIdle()

        assertNull(vm.uiState.value.optionsError)
        assertEquals(listOf("BOYACA"), vm.uiState.value.departments)
    }

    @Test
    fun municipalitiesFailure_isReported() = runTest {
        repository.municipalitiesFailure = ApiError.Network(NetworkReason.TIMEOUT)
        val vm = viewModel()
        vm.onDepartmentSelected("BOYACA")
        advanceUntilIdle()
        assertEquals(ApiError.Network(NetworkReason.TIMEOUT), vm.uiState.value.optionsError)

        repository.municipalitiesFailure = null
        vm.retryOptions()
        advanceUntilIdle()
        assertNull(vm.uiState.value.optionsError)
        assertEquals(listOf("TUNJA", "VILLA DE LEYVA"), vm.uiState.value.municipalities)
    }

    @Test
    fun editingFilters_doesNotQueryUntilSearch() = runTest {
        val vm = viewModel()
        vm.onNameChanged("PRESTADOR")
        vm.onDepartmentSelected("BOYACA")
        vm.onCategorySelected("BARES")
        advanceUntilIdle()

        assertTrue(repository.pageCalls.isEmpty())
        assertTrue(repository.countCalls.isEmpty())
        assertEquals(SearchPhase.IDLE, vm.uiState.value.phase)
    }

    // ---- Búsqueda ----

    @Test
    fun search_showsLoading_thenTheFirstPageAndTheTotal() = runTest {
        val vm = viewModel()
        vm.onDepartmentSelected("BOYACA")
        advanceUntilIdle()

        vm.search()
        assertEquals(SearchPhase.LOADING, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.providers.isEmpty())

        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(SearchPhase.LOADED, state.phase)
        assertEquals(20, state.providers.size)
        assertEquals("PRESTADOR 01", state.providers.first().name)
        assertEquals(30, state.total)
        assertEquals("query offset=0 limit=20", state.query)
        assertTrue(state.canLoadMore)
        assertEquals(ProviderFilter(department = "BOYACA"), repository.countCalls.single())
        assertEquals(listOf(ProviderFilter(department = "BOYACA")), repository.pageCalls.map { it.filter })
        assertEquals(0, repository.pageCalls.single().offset)
        assertEquals(20, repository.pageCalls.single().limit)
    }

    @Test
    fun search_withNoMatches_isLoadedAndEmpty() = runTest {
        val vm = viewModel()
        vm.onNameChanged("NO EXISTE")
        vm.search()
        advanceUntilIdle()

        assertEquals(SearchPhase.LOADED, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.providers.isEmpty())
        assertEquals(0, vm.uiState.value.total)
        assertFalse(vm.uiState.value.canLoadMore)
    }

    @Test
    fun search_combinesAllTheFilters() = runTest {
        val vm = viewModel()
        vm.onNameChanged("prestador 0")
        vm.onDepartmentSelected("BOYACA")
        advanceUntilIdle()
        vm.onMunicipalitySelected("TUNJA")
        vm.onCategorySelected("BARES")
        vm.search()
        advanceUntilIdle()

        // PRESTADOR 02, 04, 06, 08 (pares, en Tunja, nombre que empieza con "PRESTADOR 0")
        assertEquals(listOf("PRESTADOR 02", "PRESTADOR 04", "PRESTADOR 06", "PRESTADOR 08"), vm.uiState.value.providers.map { it.name })
        assertEquals(4, vm.uiState.value.total)
        assertEquals(
            ProviderFilter("prestador 0", "BOYACA", "TUNJA", "BARES"),
            repository.pageCalls.single().filter
        )
    }

    @Test
    fun searchingAgain_replacesTheOldResultsWithLoading() = runTest {
        val vm = viewModel()
        vm.search()
        advanceUntilIdle()
        assertEquals(20, vm.uiState.value.providers.size)

        vm.onNameChanged("PRESTADOR 4")
        vm.search()
        assertEquals(SearchPhase.LOADING, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.providers.isEmpty())
        advanceUntilIdle()
        assertEquals(6, vm.uiState.value.providers.size) // 40 a 45 (los nombres llevan dos dígitos: 04 no contiene "4" tras el espacio)
    }

    // ---- Cargar más ----

    @Test
    fun loadMore_appendsEachPage_untilEverythingIsLoaded() = runTest {
        val vm = viewModel()
        vm.search()
        advanceUntilIdle()
        assertEquals(20, vm.uiState.value.providers.size)

        vm.loadMore()
        assertTrue(vm.uiState.value.loadingMore)
        advanceUntilIdle()
        assertEquals(40, vm.uiState.value.providers.size)
        assertEquals("PRESTADOR 21", vm.uiState.value.providers[20].name)
        assertFalse(vm.uiState.value.loadingMore)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(45, vm.uiState.value.providers.size)
        assertFalse(vm.uiState.value.canLoadMore)
        assertEquals(listOf(0, 20, 40), repository.pageCalls.map { it.offset })

        vm.loadMore() // ya no hay más: no pide nada
        advanceUntilIdle()
        assertEquals(3, repository.pageCalls.size)
    }

    @Test
    fun loadMore_usesTheSearchedFilter_notWhatWasEditedAfterwards() = runTest {
        val vm = viewModel()
        vm.search()
        advanceUntilIdle()

        vm.onNameChanged("OTRA COSA") // se editó pero no se buscó
        vm.loadMore()
        advanceUntilIdle()

        assertEquals(ProviderFilter(), repository.pageCalls.last().filter)
        assertEquals(40, vm.uiState.value.providers.size)
    }

    @Test
    fun loadMore_failure_keepsWhatWasLoaded_andRetryAppends() = runTest {
        val vm = viewModel()
        vm.search()
        advanceUntilIdle()

        repository.pageFailures.add(ApiError.Network(NetworkReason.TIMEOUT))
        vm.loadMore()
        advanceUntilIdle()
        var state = vm.uiState.value
        assertEquals(SearchPhase.LOADED, state.phase)
        assertEquals(20, state.providers.size)
        assertEquals(ApiError.Network(NetworkReason.TIMEOUT), state.loadMoreError)
        assertFalse(state.loadingMore)
        assertTrue(state.canLoadMore)

        vm.loadMore() // el botón "Reintentar" del pie
        advanceUntilIdle()
        state = vm.uiState.value
        assertEquals(40, state.providers.size)
        assertNull(state.loadMoreError)
    }

    @Test
    fun loadMore_isIgnoredWhileAnotherPageIsLoading() = runTest {
        val vm = viewModel()
        vm.search()
        advanceUntilIdle()

        repository.gate = CompletableDeferred()
        vm.loadMore()
        vm.loadMore()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, repository.pageCalls.size) // la búsqueda y un solo "cargar más"

        repository.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(40, vm.uiState.value.providers.size)
    }

    @Test
    fun loadMore_withAnEmptyPage_stopsOfferingMore() = runTest {
        val vm = viewModel()
        vm.search()
        advanceUntilIdle()
        repository.data = repository.data.take(20) // el servicio ya no tiene las otras filas

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(20, vm.uiState.value.providers.size)
        assertFalse(vm.uiState.value.canLoadMore)
    }

    // ---- Errores de la búsqueda ----

    @Test
    fun search_withoutConnection_isAnError_andRetryWorksOnceBack() = runTest {
        val vm = viewModel()
        repository.pageFailures.add(ApiError.NoConnection)
        vm.search()
        advanceUntilIdle()

        assertEquals(SearchPhase.ERROR, vm.uiState.value.phase)
        assertEquals(ApiError.NoConnection, vm.uiState.value.error)
        assertTrue(vm.uiState.value.providers.isEmpty())

        vm.retry()
        assertEquals(SearchPhase.LOADING, vm.uiState.value.phase)
        assertNull(vm.uiState.value.error)
        advanceUntilIdle()
        assertEquals(SearchPhase.LOADED, vm.uiState.value.phase)
        assertEquals(20, vm.uiState.value.providers.size)
    }

    @Test
    fun search_whenCountFails_isAnError_withTheServerMessage() = runTest {
        val error = ApiError.Server(400, "Could not parse SoQL query")
        repository.countOverride = ApiResult.Failure(error)
        val vm = viewModel()
        vm.search()
        advanceUntilIdle()

        assertEquals(SearchPhase.ERROR, vm.uiState.value.phase)
        assertEquals(error, vm.uiState.value.error)
    }

    @Test
    fun retry_repeatsTheSearchedFilter_notTheEditedFields() = runTest {
        val vm = viewModel()
        vm.onNameChanged("PRESTADOR 01")
        repository.pageFailures.add(ApiError.Server(500, null))
        vm.search()
        advanceUntilIdle()
        assertEquals(SearchPhase.ERROR, vm.uiState.value.phase)

        vm.onNameChanged("OTRA COSA")
        vm.retry()
        advanceUntilIdle()

        assertEquals("PRESTADOR 01", repository.pageCalls.last().filter.name)
        assertEquals(1, vm.uiState.value.providers.size)
    }

    // ---- Cancelación ----

    @Test
    fun aNewSearch_cancelsThePreviousOne_andOnlyItsResultsShow() = runTest {
        val vm = viewModel()
        repository.gate = CompletableDeferred() // la primera búsqueda se queda colgada
        vm.onNameChanged("PRESTADOR 1")
        vm.search()
        advanceUntilIdle()
        assertEquals(SearchPhase.LOADING, vm.uiState.value.phase)
        val firstGate = repository.gate!!

        repository.gate = null
        vm.onNameChanged("PRESTADOR 4")
        vm.search()
        advanceUntilIdle()
        assertEquals(1, repository.cancelledPageCalls)

        firstGate.complete(Unit) // aunque la vieja "respondiera" tarde, no cuenta
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(SearchPhase.LOADED, state.phase)
        assertTrue(state.providers.all { it.name.startsWith("PRESTADOR 4") })
        assertEquals(6, state.total)
    }

    @Test
    fun searchingWhileLoadingMore_cancelsThePendingPage() = runTest {
        val vm = viewModel()
        vm.search()
        advanceUntilIdle()

        repository.gate = CompletableDeferred()
        vm.loadMore()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadingMore)

        repository.gate = null
        vm.onNameChanged("PRESTADOR 0")
        vm.search()
        advanceUntilIdle()

        assertEquals(1, repository.cancelledPageCalls)
        assertFalse(vm.uiState.value.loadingMore)
        assertEquals(9, vm.uiState.value.providers.size)
    }

    // ---- Limpiar filtros ----

    @Test
    fun clearFilters_resetsTheFields_andSearchesEverything() = runTest {
        val vm = viewModel()
        vm.onNameChanged("ZZZ")
        vm.onDepartmentSelected("BOYACA")
        advanceUntilIdle()
        vm.onMunicipalitySelected("TUNJA")
        vm.onCategorySelected("BARES")
        vm.search()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.providers.isEmpty())

        vm.clearFilters()
        assertEquals(ProviderFilter(), vm.uiState.value.filter)
        assertTrue(vm.uiState.value.municipalities.isEmpty())
        advanceUntilIdle()

        assertEquals(SearchPhase.LOADED, vm.uiState.value.phase)
        assertEquals(45, vm.uiState.value.total)
        assertEquals(20, vm.uiState.value.providers.size)
        assertEquals(ProviderFilter(), repository.pageCalls.last().filter)
    }

    // ---- SavedStateHandle ----

    @Test
    fun filters_liveInTheSavedStateHandle() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.onNameChanged("hotel")
        vm.onDepartmentSelected("BOYACA")
        vm.onCategorySelected("BARES")
        advanceUntilIdle()

        assertEquals(ProviderFilter("hotel", "BOYACA", null, "BARES"), handle.get<ProviderFilter>("filter"))
    }

    @Test
    fun afterProcessDeath_theFiltersComeBack_andTheLoadedResultsAreRequestedAgain() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.onDepartmentSelected("BOYACA")
        advanceUntilIdle()
        first.onMunicipalitySelected("TUNJA")
        first.search()
        advanceUntilIdle()
        first.loadMore()
        advanceUntilIdle()
        assertEquals(30, first.uiState.value.providers.size) // 20 + 10
        val callsBefore = repository.pageCalls.size

        // Proceso nuevo, mismo SavedStateHandle (lo que Android restaura)
        val restored = viewModel(handle)
        assertEquals(ProviderFilter(department = "BOYACA", municipality = "TUNJA"), restored.uiState.value.filter)
        assertEquals(SearchPhase.LOADING, restored.uiState.value.phase)
        advanceUntilIdle()

        val state = restored.uiState.value
        assertEquals(SearchPhase.LOADED, state.phase)
        assertEquals(30, state.providers.size)
        assertEquals(30, state.total)
        assertEquals(listOf("TUNJA", "VILLA DE LEYVA"), state.municipalities)
        // Una sola consulta, con todo lo que había: limit = 30 desde el offset 0
        assertEquals(callsBefore + 1, repository.pageCalls.size)
        assertEquals(0, repository.pageCalls.last().offset)
        assertEquals(30, repository.pageCalls.last().limit)
    }

    @Test
    fun afterProcessDeath_withoutAnySearch_staysIdle() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).onNameChanged("hotel")
        advanceUntilIdle()

        val restored = viewModel(handle)
        advanceUntilIdle()

        assertEquals("hotel", restored.uiState.value.filter.name)
        assertEquals(SearchPhase.IDLE, restored.uiState.value.phase)
        assertTrue(repository.pageCalls.isEmpty())
    }

    @Test
    fun theQuery_isKeptForThePanel() = runTest {
        val vm = viewModel()
        assertNull(vm.uiState.value.query)
        vm.search()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.query)
    }
}
