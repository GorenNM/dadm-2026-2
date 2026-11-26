package co.edu.unal.nearbyplaces.ui.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.unal.nearbyplaces.NearbyPlacesApp
import co.edu.unal.nearbyplaces.data.AddressRepository
import co.edu.unal.nearbyplaces.data.GeoMath
import co.edu.unal.nearbyplaces.data.GeoPoint
import co.edu.unal.nearbyplaces.data.LocationOutcome
import co.edu.unal.nearbyplaces.data.LocationProvider
import co.edu.unal.nearbyplaces.data.Poi
import co.edu.unal.nearbyplaces.data.PoiCategory
import co.edu.unal.nearbyplaces.data.PoiRepository
import co.edu.unal.nearbyplaces.data.RadiusSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** En qué punto está la pantalla. Cada uno tiene su mensaje en la vista. */
sealed interface MapStatus {
    /** Todavía no se llamó a [MapViewModel.start]. */
    data object Idle : MapStatus

    /** Se tocó "mi ubicación" sin permiso: se pide, y el mapa sigue donde estaba. */
    data object PermissionNeeded : MapStatus

    data object Locating : MapStatus
    data object LocationOff : MapStatus
    data object LocationFailed : MapStatus
    data object LoadingPois : MapStatus
    data object NoCategories : MapStatus
    data class Found(val count: Int) : MapStatus
    data object Empty : MapStatus
    data object NetworkError : MapStatus
}

/** De dónde sale el centro de la búsqueda. */
enum class CenterSource {
    /** La UNAL Bogotá: dónde busca la app al abrirse, sin esperar al GPS. */
    DEFAULT,

    /** La posición del usuario (botón "mi ubicación"). */
    USER,

    /** Un punto elegido con pulsación larga en el mapa. */
    PICKED
}

data class MapUiState(
    val status: MapStatus = MapStatus.Idle,
    /** Centro de la última búsqueda (o de la que está en curso). */
    val center: GeoPoint,
    val centerSource: CenterSource,
    /** Dirección del [center], si el Geocoder la dio; la vista cae en las coordenadas. */
    val address: String? = null,
    val radiusKm: Int,
    val categories: Set<PoiCategory>,
    /** Del más cercano al más lejano, ya filtrados por el radio. */
    val pois: List<Poi> = emptyList(),
    /** Categorías cuya consulta llegó al tope de la fuente: puede haber más lugares de los que se ven. */
    val truncated: List<PoiCategory> = emptyList(),
    /** La vista debe pedir el permiso (o llevar a los ajustes) y avisar con [MapViewModel.onPermissionPromptHandled]. */
    val shouldRequestPermission: Boolean = false,
    /** Ya se le mostró el diálogo del sistema alguna vez: si sigue sin permiso y no hay rationale, hay que ir a ajustes. */
    val permissionAsked: Boolean = false
) {
    /** Con el centro por defecto la vista agrega la pista de tocar "mi ubicación". */
    val showDefaultHint: Boolean get() = centerSource == CenterSource.DEFAULT
}

/**
 * Orquesta la pantalla del mapa: centro (UNAL, GPS o punto elegido) → consulta de lugares por categoría → resultado. Todo el
 * trabajo de red y GPS vive en los repositorios (en hilos de IO); acá solo se decide qué sigue. Lo que se
 * pierde si Android mata el proceso (centro por pulsación larga, categorías, si ya se pidió el permiso)
 * va en el [SavedStateHandle]; los resultados no: se vuelven a buscar.
 */
class MapViewModel(
    private val locationProvider: LocationProvider,
    private val poiRepository: PoiRepository,
    private val addressRepository: AddressRepository,
    private val radiusSource: RadiusSource,
    private val savedState: SavedStateHandle
) : ViewModel() {

    companion object {
        /** Universidad Nacional de Colombia, sede Bogotá: el centro de la búsqueda al abrir la app. */
        val UNAL_BOGOTA = GeoPoint(4.6370, -74.0830)

        private const val KEY_SEARCH_CENTER = "search_center"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_PERMISSION_ASKED = "permission_asked"

        fun factory(app: NearbyPlacesApp) = viewModelFactory {
            initializer {
                MapViewModel(
                    app.locationProvider, app.poiRepository, app.addressRepository, app.radiusSource,
                    createSavedStateHandle()
                )
            }
        }
    }

    private val mState = MutableStateFlow(initialState())
    val uiState: StateFlow<MapUiState> = mState.asStateFlow()

    /** Búsqueda en curso (ubicarse + consultar). Una nueva cancela la anterior. */
    private var mSearchJob: Job? = null
    private var mAddressJob: Job? = null
    private var mAddressCenter: GeoPoint? = null

    // No va en el SavedStateHandle: tras la muerte del proceso hay que arrancar de nuevo. Rotar conserva el ViewModel
    private var mStarted = false

    private fun initialState(): MapUiState {
        val searchCenter = savedState.get<DoubleArray>(KEY_SEARCH_CENTER)?.let { GeoPoint(it[0], it[1]) }
        return MapUiState(
            center = searchCenter ?: UNAL_BOGOTA,
            centerSource = if (searchCenter == null) CenterSource.DEFAULT else CenterSource.PICKED,
            radiusKm = radiusSource.radiusKm(),
            categories = savedCategories(),
            permissionAsked = savedState.get<Boolean>(KEY_PERMISSION_ASKED) == true
        )
    }

    private fun savedCategories(): Set<PoiCategory> {
        val ids = savedState.get<ArrayList<String>>(KEY_CATEGORIES) ?: return PoiCategory.DEFAULTS
        return ids.mapNotNull(PoiCategory::fromId).toSet()
    }

    /**
     * Primera búsqueda al abrir la pantalla. Solo se hace una vez por ViewModel, así rotar no repite la consulta.
     * Busca en el punto elegido a mano si Android mató el proceso y lo había; si no, en la UNAL Bogotá. No usa el
     * GPS ni pide el permiso: eso solo pasa con "mi ubicación".
     */
    fun start() {
        if (mStarted) return
        mStarted = true
        val state = mState.value
        search(state.center, state.centerSource)
    }

    /** La vista ya abrió el diálogo del sistema (o los ajustes): no hay que volver a pedir. */
    fun onPermissionPromptHandled() {
        savedState[KEY_PERMISSION_ASKED] = true
        mState.update { it.copy(shouldRequestPermission = false, permissionAsked = true) }
    }

    /**
     * El permiso cambió o se confirmó (resultado del diálogo, o la Activity volvió a primer plano, quizá desde ajustes).
     * Con permiso y esperándolo (solo se llega a `PermissionNeeded` tras tocar "mi ubicación") o con la ubicación del
     * sistema apagada, se busca la ubicación. Sin permiso no hace nada: el aviso ya está.
     */
    fun onPermissionState(granted: Boolean) {
        if (!mStarted) return
        val state = mState.value
        // LocationOff: el usuario pudo encender la ubicación en ajustes y volver; si sigue apagada, vuelve a Disabled.
        // LocationFailed no se relanza solo: ya tiene "Reintentar"
        if (granted && (state.status == MapStatus.PermissionNeeded || state.status == MapStatus.LocationOff)) {
            locateAndSearch()
        }
    }

    /** El botón "mi ubicación": busca en la posición del usuario (pidiendo el permiso si falta), descartando el punto elegido a mano. */
    fun onMyLocationClicked(permissionGranted: Boolean) {
        savedState.remove<DoubleArray>(KEY_SEARCH_CENTER)
        if (permissionGranted) locateAndSearch() else requirePermission(prompt = true)
    }

    /** Pulsación larga en el mapa: busca alrededor de ese punto. Sirve con o sin permiso. */
    fun onMapLongPress(point: GeoPoint) {
        savedState[KEY_SEARCH_CENTER] = doubleArrayOf(point.latitude, point.longitude)
        search(point, CenterSource.PICKED)
    }

    fun onCategoryToggled(category: PoiCategory, checked: Boolean) {
        val current = mState.value.categories
        val updated = if (checked) current + category else current - category
        if (updated == current) return
        // En el orden del enum, para que el mismo conjunto se guarde siempre igual
        savedState[KEY_CATEGORIES] = ArrayList(PoiCategory.entries.filter { it in updated }.map { it.id })
        mState.update { it.copy(categories = updated) }
        searchAgain()
    }

    /** Se vuelve a leer el radio (p. ej. al volver de los ajustes); si cambió, se busca de nuevo. */
    fun onRadiusMaybeChanged() {
        val radius = radiusSource.radiusKm()
        if (radius == mState.value.radiusKm) return
        mState.update { it.copy(radiusKm = radius) }
        searchAgain()
    }

    /** "Reintentar" tras un error de red o de ubicación. */
    fun onRetry(permissionGranted: Boolean) {
        val state = mState.value
        if (state.status == MapStatus.NetworkError) search(state.center, state.centerSource)
        else onMyLocationClicked(permissionGranted)
    }

    /**
     * Repite la búsqueda en el centro actual con la selección y el radio nuevos. Mientras se busca la ubicación no
     * hace nada: esa búsqueda en curso ya va a leer los valores nuevos.
     */
    private fun searchAgain() {
        val state = mState.value
        if (state.status == MapStatus.Locating) return
        search(state.center, state.centerSource)
    }

    private fun requirePermission(prompt: Boolean) {
        mSearchJob?.cancel()
        mState.update { it.copy(status = MapStatus.PermissionNeeded, shouldRequestPermission = prompt) }
    }

    private fun locateAndSearch() {
        mSearchJob?.cancel()
        mSearchJob = viewModelScope.launch {
            mState.update { it.copy(status = MapStatus.Locating, shouldRequestPermission = false) }
            when (val outcome = locationProvider.currentLocation()) {
                is LocationOutcome.Found -> searchAround(outcome.point, CenterSource.USER)
                LocationOutcome.Disabled -> mState.update { it.copy(status = MapStatus.LocationOff) }
                LocationOutcome.NoFix -> mState.update { it.copy(status = MapStatus.LocationFailed) }
            }
        }
    }

    private fun search(center: GeoPoint, source: CenterSource) {
        mSearchJob?.cancel()
        mSearchJob = viewModelScope.launch { searchAround(center, source) }
    }

    private suspend fun searchAround(center: GeoPoint, source: CenterSource) {
        val radiusKm = radiusSource.radiusKm()
        val categories = mState.value.categories
        val addressStale = mAddressCenter != center

        mState.update {
            it.copy(
                center = center, centerSource = source, radiusKm = radiusKm,
                address = if (addressStale) null else it.address,
                shouldRequestPermission = false
            )
        }
        if (addressStale) loadAddress(center)

        if (categories.isEmpty()) {
            mState.update { it.copy(status = MapStatus.NoCategories, pois = emptyList(), truncated = emptyList()) }
            return
        }
        mState.update { it.copy(status = MapStatus.LoadingPois) }

        try {
            // Una consulta por categoría, en paralelo; si una falla, se cancelan las demás
            val results = coroutineScope {
                categories.map { category -> async { poiRepository.nearby(center, radiusKm, category) } }.awaitAll()
            }
            val pois = GeoMath.nearest(results.flatMap { it.pois }.distinctBy { it.id }, radiusKm * 1000.0)
            val truncated = results.filter { it.truncated }.map { it.category }.sortedBy { it.ordinal }
            mState.update {
                it.copy(
                    status = if (pois.isEmpty()) MapStatus.Empty else MapStatus.Found(pois.size),
                    pois = pois,
                    truncated = truncated
                )
            }
        } catch (e: IOException) {
            mState.update { it.copy(status = MapStatus.NetworkError, pois = emptyList(), truncated = emptyList()) }
        }
    }

    /** La dirección va aparte: si tarda o falla, los lugares no esperan. */
    private fun loadAddress(center: GeoPoint) {
        mAddressJob?.cancel()
        mAddressCenter = center
        mAddressJob = viewModelScope.launch {
            val address = addressRepository.addressOf(center)
            // Si mientras tanto el centro cambió, esta dirección ya no corresponde
            mState.update { if (it.center == center) it.copy(address = address) else it }
        }
    }
}
