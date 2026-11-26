package co.edu.unal.tourismregistry.ui.detail

import androidx.lifecycle.SavedStateHandle
import co.edu.unal.tourismregistry.data.ApiError
import co.edu.unal.tourismregistry.data.NetworkReason
import co.edu.unal.tourismregistry.ui.FakeTourismRepository
import co.edu.unal.tourismregistry.ui.sampleProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val repository = FakeTourismRepository(sampleProviders(5))

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(code: String?) =
        DetailViewModel(repository, SavedStateHandle(if (code == null) emptyMap() else mapOf(DetailViewModel.KEY_CODE to code)))

    @Test
    fun loadsTheProviderByItsCode() = runTest {
        val vm = viewModel("3")
        assertEquals(DetailUiState.Loading, vm.uiState.value)

        advanceUntilIdle()
        val state = vm.uiState.value as DetailUiState.Loaded
        assertEquals("PRESTADOR 03", state.provider.name)
        assertEquals(listOf("3"), repository.providerCalls)
    }

    @Test
    fun aCodeNoLongerInTheDataset_isNotFound() = runTest {
        val vm = viewModel("999")
        advanceUntilIdle()
        assertEquals(DetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun withoutACode_isNotFound_andNothingIsRequested() = runTest {
        val vm = viewModel(null)
        advanceUntilIdle()
        assertEquals(DetailUiState.NotFound, vm.uiState.value)
        assertTrue(repository.providerCalls.isEmpty())

        assertEquals(DetailUiState.NotFound, viewModel("  ").uiState.value)
    }

    @Test
    fun withoutConnection_fails_andRetryLoads() = runTest {
        repository.providerFailure = ApiError.NoConnection
        val vm = viewModel("2")
        advanceUntilIdle()
        assertEquals(DetailUiState.Failed(ApiError.NoConnection), vm.uiState.value)

        repository.providerFailure = null
        vm.retry()
        assertEquals(DetailUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals("PRESTADOR 02", (vm.uiState.value as DetailUiState.Loaded).provider.name)
        assertEquals(listOf("2", "2"), repository.providerCalls)
    }

    @Test
    fun serverAndNetworkErrors_areKeptAsTheyCame() = runTest {
        repository.providerFailure = ApiError.Server(503, "Service Unavailable")
        val vm = viewModel("1")
        advanceUntilIdle()
        assertEquals(DetailUiState.Failed(ApiError.Server(503, "Service Unavailable")), vm.uiState.value)

        repository.providerFailure = ApiError.Network(NetworkReason.TIMEOUT)
        vm.retry()
        advanceUntilIdle()
        assertEquals(DetailUiState.Failed(ApiError.Network(NetworkReason.TIMEOUT)), vm.uiState.value)
    }

    @Test
    fun theCodeComesFromTheSavedStateHandle_soAProcessRestartRequestsItAgain() = runTest {
        val handle = SavedStateHandle(mapOf(DetailViewModel.KEY_CODE to "4"))
        DetailViewModel(repository, handle)
        advanceUntilIdle()
        // "Proceso nuevo" con el mismo handle: vuelve a consultar el mismo código
        val restored = DetailViewModel(repository, handle)
        advanceUntilIdle()

        assertEquals(listOf("4", "4"), repository.providerCalls)
        assertEquals("PRESTADOR 04", (restored.uiState.value as DetailUiState.Loaded).provider.name)
    }
}
