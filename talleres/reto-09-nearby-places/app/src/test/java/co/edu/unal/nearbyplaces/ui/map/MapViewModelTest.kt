package co.edu.unal.nearbyplaces.ui.map

import androidx.lifecycle.SavedStateHandle
import co.edu.unal.nearbyplaces.data.CategoryResult
import co.edu.unal.nearbyplaces.data.GeoPoint
import co.edu.unal.nearbyplaces.data.LocationOutcome
import co.edu.unal.nearbyplaces.data.PoiCategory.HOSPITALS
import co.edu.unal.nearbyplaces.data.PoiCategory.HOTELS
import co.edu.unal.nearbyplaces.data.PoiCategory.PHARMACIES
import co.edu.unal.nearbyplaces.data.PoiCategory.TOURISM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val unal = MapViewModel.UNAL_BOGOTA
    private val user = GeoPoint(4.7110, -74.0721)
    private val tapped = GeoPoint(6.2442, -75.5812)

    private val location = FakeLocationProvider(LocationOutcome.Found(user))
    private val repository = FakePoiRepository()
    private val addresses = FakeAddressRepository()
    private val radius = FakeRadius(2)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        MapViewModel(location, repository, addresses, radius, savedState)

    /** Un ViewModel ya abierto: buscó en la UNAL y terminó. */
    private fun TestScope.openedViewModel(savedState: SavedStateHandle = SavedStateHandle()): MapViewModel {
        val vm = viewModel(savedState)
        vm.start()
        advanceUntilIdle()
        return vm
    }

    private fun setResults(vararg results: CategoryResult) {
        results.forEach { repository.results[it.category] = it }
    }

    // --- Al abrir: siempre la UNAL, sin GPS ni permiso ---

    @Test
    fun opening_searchesTheUnal_nearestFirst_withoutGpsOrPermission() = runTest {
        setResults(
            CategoryResult(HOSPITALS, listOf(poi("h1", HOSPITALS, 800.0), poi("h2", HOSPITALS, 300.0)), false),
            CategoryResult(TOURISM, listOf(poi("t1", TOURISM, 500.0)), false)
        )
        repository.delayByCenter[unal] = 1_000
        val vm = viewModel()
        // Antes de arrancar ya se sabe dónde se va a buscar
        assertEquals(unal, vm.uiState.value.center)
        assertEquals(CenterSource.DEFAULT, vm.uiState.value.centerSource)

        vm.start()
        runCurrent()
        assertEquals(MapStatus.LoadingPois, vm.uiState.value.status)

        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(MapStatus.Found(3), state.status)
        assertEquals(unal, state.center)
        assertEquals(CenterSource.DEFAULT, state.centerSource)
        assertEquals(listOf("h2", "t1", "h1"), state.pois.map { it.id })
        // Las categorías de la primera vez: hospitales y turismo
        assertEquals(setOf(HOSPITALS, TOURISM), repository.calls.map { it.category }.toSet())
        assertTrue(repository.calls.all { it.center == unal && it.radiusKm == 2 })
        // Ni GPS ni permiso
        assertEquals(0, location.calls)
        assertFalse(state.shouldRequestPermission)
        assertFalse(state.permissionAsked)
        assertTrue(state.status != MapStatus.PermissionNeeded)
    }

    @Test
    fun opening_neverLeavesThePermissionNoticeUp_whateverThePermissionState() = runTest {
        // La Activity llama a onPermissionState en onStart, con permiso o sin él
        val denied = openedViewModel()
        denied.onPermissionState(granted = false)
        advanceUntilIdle()
        assertEquals(MapStatus.Empty, denied.uiState.value.status)
        assertFalse(denied.uiState.value.shouldRequestPermission)

        val granted = openedViewModel()
        granted.onPermissionState(granted = true)
        advanceUntilIdle()
        assertEquals(MapStatus.Empty, granted.uiState.value.status)
        assertEquals(CenterSource.DEFAULT, granted.uiState.value.centerSource)
        assertEquals(0, location.calls)
    }

    @Test
    fun theDefaultHint_showsOnlyForTheDefaultCenter() = runTest {
        val vm = openedViewModel()
        assertTrue(vm.uiState.value.showDefaultHint)

        vm.onMapLongPress(tapped)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showDefaultHint)

        vm.onMyLocationClicked(permissionGranted = true)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showDefaultHint)
    }

    // --- "Mi ubicación" y permiso ---

    @Test
    fun myLocation_withPermission_usesTheGps_andSearchesThere() = runTest {
        setResults(CategoryResult(HOSPITALS, listOf(poi("h1", HOSPITALS, 100.0)), false))
        val vm = openedViewModel()
        repository.calls.clear()
        location.delayMs = 1_000

        vm.onMyLocationClicked(permissionGranted = true)
        runCurrent()
        assertEquals(MapStatus.Locating, vm.uiState.value.status)

        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(MapStatus.Found(1), state.status)
        assertEquals(user, state.center)
        assertEquals(CenterSource.USER, state.centerSource)
        assertEquals(1, location.calls)
        assertTrue(repository.calls.all { it.center == user })
    }

    @Test
    fun myLocation_withoutPermission_asks_andOnceGranted_locates() = runTest {
        val vm = openedViewModel()
        repository.calls.clear()

        vm.onMyLocationClicked(permissionGranted = false)
        advanceUntilIdle()

        var state = vm.uiState.value
        assertEquals(MapStatus.PermissionNeeded, state.status)
        assertTrue(state.shouldRequestPermission)
        // El mapa sigue donde estaba y no se tocó el GPS
        assertEquals(unal, state.center)
        assertEquals(0, location.calls)

        vm.onPermissionPromptHandled()
        assertFalse(vm.uiState.value.shouldRequestPermission)
        assertTrue(vm.uiState.value.permissionAsked)

        // El usuario aceptó
        vm.onPermissionState(granted = true)
        advanceUntilIdle()
        state = vm.uiState.value
        assertEquals(1, location.calls)
        assertEquals(user, state.center)
        assertEquals(CenterSource.USER, state.centerSource)
    }

    @Test
    fun deniedAnswer_keepsTheNotice_withoutAskingAgain_andWithoutTheGps() = runTest {
        val vm = openedViewModel()
        vm.onMyLocationClicked(permissionGranted = false)
        vm.onPermissionPromptHandled()

        vm.onPermissionState(granted = false)
        advanceUntilIdle()

        assertEquals(MapStatus.PermissionNeeded, vm.uiState.value.status)
        assertFalse(vm.uiState.value.shouldRequestPermission)
        assertEquals(0, location.calls)
    }

    @Test
    fun alreadyAsked_isRememberedAfterProcessDeath() = runTest {
        val saved = SavedStateHandle()
        val first = openedViewModel(saved)
        first.onMyLocationClicked(permissionGranted = false)
        first.onPermissionPromptHandled()

        val second = viewModel(saved)
        assertTrue(second.uiState.value.permissionAsked)
        second.start()
        advanceUntilIdle()
        // Reabrir tampoco pide el permiso
        assertFalse(second.uiState.value.shouldRequestPermission)
        assertEquals(MapStatus.Empty, second.uiState.value.status)
    }

    @Test
    fun longPress_searchesAroundThatPoint_withoutGpsOrPermission() = runTest {
        setResults(CategoryResult(HOSPITALS, listOf(poi("h1", HOSPITALS, 100.0)), false))
        val vm = openedViewModel()
        repository.calls.clear()

        vm.onMapLongPress(tapped)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(MapStatus.Found(1), state.status)
        assertEquals(tapped, state.center)
        assertEquals(CenterSource.PICKED, state.centerSource)
        assertEquals(0, location.calls)
        assertTrue(repository.calls.all { it.center == tapped })
    }

    // --- Ubicación ---

    @Test
    fun locationOff_isReported_andTheMapStaysOnTheUnal() = runTest {
        location.outcome = LocationOutcome.Disabled
        val vm = openedViewModel()
        repository.calls.clear()

        vm.onMyLocationClicked(permissionGranted = true)
        advanceUntilIdle()

        assertEquals(MapStatus.LocationOff, vm.uiState.value.status)
        assertEquals(unal, vm.uiState.value.center)
        assertEquals(CenterSource.DEFAULT, vm.uiState.value.centerSource)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun locationOff_thenTurnedOnAndBack_relaunchesTheSearch() = runTest {
        location.outcome = LocationOutcome.Disabled
        val vm = openedViewModel()
        vm.onMyLocationClicked(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(MapStatus.LocationOff, vm.uiState.value.status)

        // Sigue apagada al volver: queda igual
        vm.onPermissionState(granted = true)
        advanceUntilIdle()
        assertEquals(MapStatus.LocationOff, vm.uiState.value.status)
        assertEquals(2, location.calls)

        // La encendió en ajustes y volvió
        location.outcome = LocationOutcome.Found(user)
        setResults(CategoryResult(HOSPITALS, listOf(poi("h1", HOSPITALS, 100.0)), false))
        vm.onPermissionState(granted = true)
        advanceUntilIdle()
        assertEquals(MapStatus.Found(1), vm.uiState.value.status)
        assertEquals(user, vm.uiState.value.center)
        assertEquals(CenterSource.USER, vm.uiState.value.centerSource)
    }

    @Test
    fun onPermissionState_doesNotRelaunch_fromAnyOtherStatus() = runTest {
        // Sin señal: ya tiene "Reintentar"
        location.outcome = LocationOutcome.NoFix
        val failed = openedViewModel()
        failed.onMyLocationClicked(permissionGranted = true)
        advanceUntilIdle()
        failed.onPermissionState(granted = true)
        advanceUntilIdle()
        assertEquals(MapStatus.LocationFailed, failed.uiState.value.status)
        assertEquals(1, location.calls)

        // Punto de pulsación larga: no se ubica
        location.calls = 0
        val picked = openedViewModel()
        picked.onMapLongPress(tapped)
        advanceUntilIdle()
        picked.onPermissionState(granted = true)
        advanceUntilIdle()
        assertEquals(0, location.calls)
        assertEquals(tapped, picked.uiState.value.center)

        // Buscando ubicación: no se lanza otra
        location.calls = 0
        location.delayMs = 5_000
        val locating = openedViewModel()
        locating.onMyLocationClicked(permissionGranted = true)
        advanceTimeBy(1_000)
        locating.onPermissionState(granted = true)
        advanceUntilIdle()
        assertEquals(1, location.calls)
    }

    @Test
    fun noFix_isReported_andRetryTriesAgain() = runTest {
        location.outcome = LocationOutcome.NoFix
        val vm = openedViewModel()
        vm.onMyLocationClicked(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(MapStatus.LocationFailed, vm.uiState.value.status)
        assertEquals(unal, vm.uiState.value.center)

        location.outcome = LocationOutcome.Found(user)
        vm.onRetry(permissionGranted = true)
        advanceUntilIdle()

        assertEquals(2, location.calls)
        assertEquals(user, vm.uiState.value.center)
        assertEquals(MapStatus.Empty, vm.uiState.value.status)
    }

    @Test
    fun whileTheGpsIsSlow_itShowsLocating_andTheOldPlacesStay() = runTest {
        setResults(CategoryResult(HOSPITALS, listOf(poi("h1", HOSPITALS, 100.0)), false))
        val vm = openedViewModel()
        location.delayMs = 10_000
        repository.calls.clear()

        vm.onMyLocationClicked(permissionGranted = true)
        advanceTimeBy(5_000)
        assertEquals(MapStatus.Locating, vm.uiState.value.status)
        assertEquals(1, vm.uiState.value.pois.size)
        assertTrue(repository.calls.isEmpty())

        advanceUntilIdle()
        assertEquals(user, vm.uiState.value.center)
    }

    // --- Consulta de lugares ---

    @Test
    fun noNetwork_onOpening_showsTheError_andRetryRepeatsTheSameSearch() = runTest {
        repository.failure = IOException("sin red")
        val vm = openedViewModel()

        assertEquals(MapStatus.NetworkError, vm.uiState.value.status)
        assertTrue(vm.uiState.value.pois.isEmpty())

        repository.failure = null
        setResults(CategoryResult(HOSPITALS, listOf(poi("h1", HOSPITALS, 100.0)), false))
        repository.calls.clear()
        vm.onRetry(permissionGranted = false)
        advanceUntilIdle()

        assertEquals(MapStatus.Found(1), vm.uiState.value.status)
        assertEquals(CenterSource.DEFAULT, vm.uiState.value.centerSource)
        assertTrue(repository.calls.all { it.center == unal })
        // Reintentar no pide ni GPS ni permiso
        assertEquals(0, location.calls)
        assertFalse(vm.uiState.value.shouldRequestPermission)
    }

    @Test
    fun noNetwork_afterMyLocation_retryKeepsTheUserCenter_andDoesNotLocateAgain() = runTest {
        val vm = openedViewModel()
        vm.onMyLocationClicked(permissionGranted = true)
        advanceUntilIdle()
        repository.failure = IOException("sin red")
        vm.onCategoryToggled(HOTELS, checked = true)
        advanceUntilIdle()
        assertEquals(MapStatus.NetworkError, vm.uiState.value.status)
        assertEquals(user, vm.uiState.value.center)
        assertEquals(1, location.calls)

        repository.failure = null
        vm.onRetry(permissionGranted = true)
        advanceUntilIdle()

        assertEquals(1, location.calls)
        assertEquals(user, vm.uiState.value.center)
        assertEquals(CenterSource.USER, vm.uiState.value.centerSource)
        assertEquals(MapStatus.Empty, vm.uiState.value.status)
    }

    @Test
    fun noResults_isEmpty_notAnError() = runTest {
        val vm = openedViewModel()
        assertEquals(MapStatus.Empty, vm.uiState.value.status)
        assertTrue(vm.uiState.value.pois.isEmpty())
    }

    @Test
    fun aFullCategory_isReportedAsTruncated() = runTest {
        setResults(
            CategoryResult(HOSPITALS, (1..50).map { poi("h$it", HOSPITALS, it * 10.0) }, truncated = true),
            CategoryResult(TOURISM, listOf(poi("t1", TOURISM, 5.0)), truncated = false)
        )
        val vm = openedViewModel()

        assertEquals(listOf(HOSPITALS), vm.uiState.value.truncated)
        assertEquals(MapStatus.Found(51), vm.uiState.value.status)
    }

    @Test
    fun placesBeyondTheRadius_areDropped_theBorderStays() = runTest {
        setResults(
            CategoryResult(
                HOSPITALS,
                listOf(poi("far", HOSPITALS, 2_000.1), poi("border", HOSPITALS, 2_000.0), poi("near", HOSPITALS, 10.0)),
                false
            )
        )
        val vm = openedViewModel()

        assertEquals(listOf("near", "border"), vm.uiState.value.pois.map { it.id })
    }

    @Test
    fun theSamePlace_inTwoCategories_appearsOnce() = runTest {
        setResults(
            CategoryResult(HOSPITALS, listOf(poi("N1", HOSPITALS, 100.0)), false),
            CategoryResult(TOURISM, listOf(poi("N1", TOURISM, 100.0)), false)
        )
        val vm = openedViewModel()

        assertEquals(1, vm.uiState.value.pois.size)
    }

    @Test
    fun oneFailingCategory_failsTheWholeSearch() = runTest {
        repository.failure = IOException("HTTP 502")
        val vm = openedViewModel()
        assertEquals(MapStatus.NetworkError, vm.uiState.value.status)
    }

    // --- Radio y categorías ---

    @Test
    fun changingTheRadius_searchesAgain_withTheNewRadius_inTheSameCenter() = runTest {
        val vm = openedViewModel()
        repository.calls.clear()

        radius.km = 7
        vm.onRadiusMaybeChanged()
        advanceUntilIdle()

        assertEquals(7, vm.uiState.value.radiusKm)
        assertTrue(repository.calls.isNotEmpty())
        assertTrue(repository.calls.all { it.radiusKm == 7 && it.center == unal })
        assertEquals(0, location.calls)
    }

    @Test
    fun theSameRadius_doesNotSearchAgain() = runTest {
        val vm = openedViewModel()
        val calls = repository.calls.size

        vm.onRadiusMaybeChanged()
        advanceUntilIdle()

        assertEquals(calls, repository.calls.size)
    }

    @Test
    fun togglingACategory_searchesAgain_withTheNewSet() = runTest {
        val vm = openedViewModel()
        repository.calls.clear()

        vm.onCategoryToggled(PHARMACIES, checked = true)
        advanceUntilIdle()

        assertEquals(setOf(HOSPITALS, TOURISM, PHARMACIES), vm.uiState.value.categories)
        assertEquals(setOf(HOSPITALS, TOURISM, PHARMACIES), repository.calls.map { it.category }.toSet())

        repository.calls.clear()
        vm.onCategoryToggled(HOSPITALS, checked = false)
        advanceUntilIdle()
        assertEquals(setOf(TOURISM, PHARMACIES), repository.calls.map { it.category }.toSet())
    }

    @Test
    fun uncheckingEverything_clearsTheMap_withoutCallingTheNetwork() = runTest {
        setResults(CategoryResult(HOSPITALS, listOf(poi("h1", HOSPITALS, 100.0)), false))
        val vm = openedViewModel()
        repository.calls.clear()

        vm.onCategoryToggled(HOSPITALS, checked = false)
        vm.onCategoryToggled(TOURISM, checked = false)
        advanceUntilIdle()

        assertEquals(MapStatus.NoCategories, vm.uiState.value.status)
        assertTrue(vm.uiState.value.pois.isEmpty())
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun togglingWhileLocating_doesNotCancelTheLocation_andTheSearchUsesTheNewSet() = runTest {
        val vm = openedViewModel()
        location.delayMs = 5_000
        vm.onMyLocationClicked(permissionGranted = true)
        advanceTimeBy(1_000)
        repository.calls.clear()

        vm.onCategoryToggled(HOTELS, checked = true)
        advanceUntilIdle()

        assertEquals(1, location.calls)
        assertEquals(user, vm.uiState.value.center)
        assertTrue(HOTELS in repository.calls.map { it.category })
    }

    // --- Pulsación larga y "mi ubicación" ---

    @Test
    fun aNewLongPress_cancelsTheSearchStillRunning() = runTest {
        setResults(CategoryResult(HOSPITALS, listOf(poi("h1", HOSPITALS, 100.0)), false))
        repository.delayByCenter[unal] = 10_000
        val vm = viewModel()
        vm.start()
        advanceTimeBy(1_000) // la consulta de la UNAL sigue en vuelo

        vm.onMapLongPress(tapped)
        advanceUntilIdle()

        assertEquals(tapped, vm.uiState.value.center)
        assertEquals(MapStatus.Found(1), vm.uiState.value.status)
    }

    @Test
    fun myLocation_goesToTheUser_andForgetsTheTappedPoint() = runTest {
        val saved = SavedStateHandle()
        val vm = openedViewModel(saved)
        vm.onMapLongPress(tapped)
        advanceUntilIdle()
        assertEquals(CenterSource.PICKED, vm.uiState.value.centerSource)

        vm.onMyLocationClicked(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(user, vm.uiState.value.center)
        assertEquals(CenterSource.USER, vm.uiState.value.centerSource)

        // Tras la muerte del proceso ya no vuelve al punto tocado: abre en la UNAL
        val reborn = viewModel(saved)
        assertEquals(unal, reborn.uiState.value.center)
        assertEquals(CenterSource.DEFAULT, reborn.uiState.value.centerSource)
    }

    // --- Rotación y muerte del proceso ---

    @Test
    fun rotating_doesNotRepeatTheQueries() = runTest {
        setResults(CategoryResult(HOSPITALS, listOf(poi("h1", HOSPITALS, 100.0)), false))
        val vm = openedViewModel()
        val queries = repository.calls.size
        val state = vm.uiState.value

        // La Activity nueva repite lo mismo que la anterior en onCreate/onStart
        vm.start()
        vm.onPermissionState(granted = true)
        vm.onRadiusMaybeChanged()
        advanceUntilIdle()

        assertEquals(queries, repository.calls.size)
        assertEquals(0, location.calls)
        assertEquals(state, vm.uiState.value)
    }

    @Test
    fun processDeath_withATappedPoint_restoresItAndTheCategories_andSearchesThere() = runTest {
        setResults(CategoryResult(PHARMACIES, listOf(poi("p1", PHARMACIES, 100.0)), false))
        val saved = SavedStateHandle()
        val before = openedViewModel(saved)
        before.onCategoryToggled(HOSPITALS, checked = false)
        before.onCategoryToggled(TOURISM, checked = false)
        before.onCategoryToggled(PHARMACIES, checked = true)
        before.onMapLongPress(tapped)
        advanceUntilIdle()
        repository.calls.clear()

        val after = viewModel(saved)
        assertEquals(setOf(PHARMACIES), after.uiState.value.categories)
        assertEquals(tapped, after.uiState.value.center)
        assertEquals(CenterSource.PICKED, after.uiState.value.centerSource)

        after.start()
        advanceUntilIdle()

        assertEquals(MapStatus.Found(1), after.uiState.value.status)
        assertEquals(0, location.calls)
        assertTrue(repository.calls.all { it.center == tapped && it.category == PHARMACIES })
    }

    @Test
    fun processDeath_withoutATappedPoint_opensOnTheUnal_evenIfTheGpsWasUsedBefore() = runTest {
        val saved = SavedStateHandle()
        val before = openedViewModel(saved)
        before.onMyLocationClicked(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(user, before.uiState.value.center)
        location.calls = 0
        repository.calls.clear()

        val after = viewModel(saved)
        after.start()
        advanceUntilIdle()

        assertEquals(unal, after.uiState.value.center)
        assertEquals(CenterSource.DEFAULT, after.uiState.value.centerSource)
        assertEquals(0, location.calls)
        assertTrue(repository.calls.all { it.center == unal })
    }

    // --- Dirección ---

    @Test
    fun theAddressOfTheCenter_arrivesApart_andFailingLeavesItNull() = runTest {
        addresses.address = "Carrera 30 # 45-03, Bogotá"
        val vm = openedViewModel()
        assertEquals("Carrera 30 # 45-03, Bogotá", vm.uiState.value.address)
        assertEquals(listOf(unal), addresses.requested)

        addresses.address = null
        vm.onMapLongPress(tapped)
        advanceUntilIdle()
        assertNull(vm.uiState.value.address)
        assertEquals(tapped, vm.uiState.value.center)
    }

    @Test
    fun theAddress_isNotRequestedAgain_forTheSameCenter() = runTest {
        val vm = openedViewModel()

        vm.onCategoryToggled(HOTELS, checked = true)
        advanceUntilIdle()

        assertEquals(1, addresses.requested.size)
    }
}
