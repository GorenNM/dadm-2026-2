package co.edu.unal.softwaredirectory.ui.list

import androidx.lifecycle.SavedStateHandle
import co.edu.unal.softwaredirectory.data.Classification.CONSULTING
import co.edu.unal.softwaredirectory.data.Classification.CUSTOM_DEVELOPMENT
import co.edu.unal.softwaredirectory.data.Classification.SOFTWARE_FACTORY
import co.edu.unal.softwaredirectory.data.Company
import co.edu.unal.softwaredirectory.data.CompanyFilter
import co.edu.unal.softwaredirectory.ui.FakeCompanyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyListViewModelTest {

    private val andes = Company(1, "Andes Software Factory", classifications = setOf(SOFTWARE_FACTORY))
    private val agiles = Company(2, "Consultores Ágiles", classifications = setOf(CONSULTING))
    private val nandu = Company(3, "Ñandú Tecnología", classifications = setOf(CONSULTING, CUSTOM_DEVELOPMENT))
    private val bogota = Company(4, "Soluciones Bogotá", classifications = setOf(CONSULTING, SOFTWARE_FACTORY))

    private val repository = FakeCompanyRepository(listOf(bogota, nandu, agiles, andes))

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Un ViewModel con la pantalla abierta (alguien escucha el estado, como la Activity). */
    private fun TestScope.listViewModel(savedState: SavedStateHandle = SavedStateHandle()): CompanyListViewModel {
        val vm = CompanyListViewModel(repository, savedState)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun CompanyListViewModel.names() = uiState.value.companies.map { it.name }

    @Test
    fun startsLoading_thenListsEverythingSortedByName() = runTest {
        val vm = listViewModel()
        assertTrue(vm.uiState.value.loading)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.loading)
        assertFalse(vm.uiState.value.filtered)
        assertEquals(listOf(andes.name, agiles.name, nandu.name, bogota.name), vm.names())
    }

    @Test
    fun search_waitsUntilTypingStops_andIgnoresCaseAndAccents() = runTest {
        val vm = listViewModel()
        advanceUntilIdle()
        repository.searches.clear()

        for (text in listOf("N", "NA", "NAN", "NAND")) {
            vm.onQueryChanged(text)
            advanceTimeBy(CompanyListViewModel.SEARCH_DEBOUNCE_MS / 2)
        }
        // Lo escrito se ve al instante, pero todavía no se consultó
        assertEquals("NAND", vm.uiState.value.query)
        assertTrue(repository.searches.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(CompanyFilter(query = "NAND")), repository.searches)
        assertEquals(listOf(nandu.name), vm.names())
        assertTrue(vm.uiState.value.filtered)
    }

    @Test
    fun clearingTheSearch_showsEverythingRightAway() = runTest {
        val vm = listViewModel()
        vm.onQueryChanged("bogota")
        advanceUntilIdle()
        assertEquals(listOf(bogota.name), vm.names())

        vm.onQueryChanged("")
        runCurrent()
        assertEquals(4, vm.uiState.value.companies.size)
    }

    @Test
    fun classificationFilter_matchesAnyOfTheChosenOnes() = runTest {
        val vm = listViewModel()
        vm.onClassificationChecked(CUSTOM_DEVELOPMENT, true)
        advanceUntilIdle()
        assertEquals(listOf(nandu.name), vm.names())

        vm.onClassificationChecked(SOFTWARE_FACTORY, true)
        advanceUntilIdle()
        assertEquals(setOf(CUSTOM_DEVELOPMENT, SOFTWARE_FACTORY), vm.uiState.value.classifications)
        assertEquals(listOf(andes.name, nandu.name, bogota.name), vm.names())
    }

    @Test
    fun nameAndClassification_combine() = runTest {
        val vm = listViewModel()
        vm.onClassificationChecked(CONSULTING, true)
        vm.onQueryChanged("ó")
        advanceUntilIdle()
        // "ó" sin tilde es "o": Consultores, Soluciones (Ñandú Tecnología también, y es consultoría)
        assertEquals(listOf(agiles.name, nandu.name, bogota.name), vm.names())

        vm.onQueryChanged("sol")
        advanceUntilIdle()
        assertEquals(listOf(bogota.name), vm.names())
    }

    @Test
    fun noResults_isDifferentFromAnEmptyDirectory_andClearFiltersResets() = runTest {
        val vm = listViewModel()
        vm.onQueryChanged("zzz")
        vm.onClassificationChecked(CONSULTING, true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.companies.isEmpty())
        assertTrue(vm.uiState.value.filtered)

        vm.clearFilters()
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.query)
        assertTrue(vm.uiState.value.classifications.isEmpty())
        assertFalse(vm.uiState.value.filtered)
        assertEquals(4, vm.uiState.value.companies.size)
    }

    @Test
    fun listRefreshes_whenSomethingIsSavedOrDeleted() = runTest {
        val vm = listViewModel()
        vm.onClassificationChecked(SOFTWARE_FACTORY, true)
        advanceUntilIdle()

        repository.insert(Company(name = "Nueva Fábrica", classifications = setOf(SOFTWARE_FACTORY)))
        repository.delete(andes.id)
        advanceUntilIdle()
        assertEquals(listOf("Nueva Fábrica", bogota.name), vm.names())
    }

    @Test
    fun filterSurvivesProcessDeath() = runTest {
        val savedState = SavedStateHandle()
        val before = listViewModel(savedState)
        before.onQueryChanged("soluciones")
        before.onClassificationChecked(SOFTWARE_FACTORY, true)
        advanceUntilIdle()

        // El sistema restaura el SavedStateHandle en un ViewModel nuevo
        val after = listViewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
        assertEquals("soluciones", after.uiState.value.query)
        assertEquals(setOf(SOFTWARE_FACTORY), after.uiState.value.classifications)
        advanceUntilIdle()
        assertEquals(listOf(bogota.name), after.names())
    }
}
