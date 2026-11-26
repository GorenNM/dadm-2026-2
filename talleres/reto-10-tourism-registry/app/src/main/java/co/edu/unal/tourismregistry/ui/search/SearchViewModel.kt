package co.edu.unal.tourismregistry.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.unal.tourismregistry.data.ApiError
import co.edu.unal.tourismregistry.data.ApiResult
import co.edu.unal.tourismregistry.data.Provider
import co.edu.unal.tourismregistry.data.ProviderFilter
import co.edu.unal.tourismregistry.data.SodaQueryBuilder
import co.edu.unal.tourismregistry.data.TourismRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchPhase {
    /** Todavía no se ha buscado: se muestra la instrucción. */
    IDLE,
    LOADING,

    /** Hay respuesta (puede ser una lista vacía: "ningún prestador coincide"). */
    LOADED,
    ERROR
}

data class SearchUiState(
    /** Lo que hay en los campos (puede haber cambiado desde la última búsqueda). */
    val filter: ProviderFilter = ProviderFilter(),
    val departments: List<String> = emptyList(),
    val municipalities: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    /** No se pudieron cargar los desplegables desde el servicio. */
    val optionsError: ApiError? = null,
    val phase: SearchPhase = SearchPhase.IDLE,
    val providers: List<Provider> = emptyList(),
    /** Cuántos prestadores cumplen la última búsqueda (todas las páginas). */
    val total: Int = 0,
    /** La consulta (URL decodificada) de la última página pedida, para el panel "consulta enviada". */
    val query: String? = null,
    /** Por qué falló la búsqueda ([SearchPhase.ERROR]). */
    val error: ApiError? = null,
    val loadingMore: Boolean = false,
    /** "Cargar más" falló: la lista ya cargada se conserva y se ofrece reintentar. */
    val loadMoreError: ApiError? = null
) {
    val canLoadMore: Boolean get() = phase == SearchPhase.LOADED && providers.size < total
}

/**
 * La pantalla de búsqueda: filtros (con los desplegables llenados desde el servicio), resultados paginados y los
 * estados de carga/vacío/error. Los filtros y cuántos resultados van cargados viven en el [SavedStateHandle]: al
 * rotar el ViewModel sigue vivo y no se repite nada; si el proceso muere, se repite la consulta una sola vez,
 * con tantos resultados como había.
 */
class SearchViewModel(
    private val repository: TourismRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_FILTER = "filter"
        private const val KEY_SEARCHED = "searched"
        private const val KEY_LOADED = "loaded"

        fun factory(repository: TourismRepository) = viewModelFactory {
            initializer { SearchViewModel(repository, createSavedStateHandle()) }
        }
    }

    private val mState = MutableStateFlow(SearchUiState(filter = savedState.get<ProviderFilter>(KEY_FILTER) ?: ProviderFilter()))
    val uiState: StateFlow<SearchUiState> = mState.asStateFlow()

    private var mSearchJob: Job? = null
    private var mMoreJob: Job? = null
    private var mOptionsJob: Job? = null
    private var mMunicipalitiesJob: Job? = null

    init {
        loadOptions()
        mState.value.filter.department?.let(::loadMunicipalities)
        // El proceso murió con resultados en pantalla: se vuelven a pedir (todas las páginas que había, de una vez)
        savedState.get<ProviderFilter>(KEY_SEARCHED)?.let { searched ->
            val loaded = savedState.get<Int>(KEY_LOADED) ?: 0
            startSearch(searched, limit = maxOf(SodaQueryBuilder.PAGE_SIZE, loaded))
        }
    }

    // ---- Filtros ----

    fun onNameChanged(name: String) = updateFilter { it.copy(name = name) }

    fun onDepartmentSelected(department: String?) {
        if (department == mState.value.filter.department) return
        // El municipio depende del departamento: al cambiarlo se limpia y se vuelven a pedir los municipios
        mMunicipalitiesJob?.cancel()
        updateFilter { it.copy(department = department, municipality = null) }
        mState.update { it.copy(municipalities = emptyList()) }
        department?.let(::loadMunicipalities)
    }

    fun onMunicipalitySelected(municipality: String?) = updateFilter { it.copy(municipality = municipality) }

    fun onCategorySelected(category: String?) = updateFilter { it.copy(category = category) }

    fun clearFilters() {
        mMunicipalitiesJob?.cancel()
        updateFilter { ProviderFilter() }
        mState.update { it.copy(municipalities = emptyList()) }
        search()
    }

    private fun updateFilter(change: (ProviderFilter) -> ProviderFilter) {
        val updated = change(mState.value.filter)
        if (updated == mState.value.filter) return
        savedState[KEY_FILTER] = updated
        mState.update { it.copy(filter = updated) }
    }

    // ---- Desplegables (vienen del servicio) ----

    /** Vuelve a pedir lo que no se pudo cargar (los botones "Reintentar" de los desplegables). */
    fun retryOptions() {
        val state = mState.value
        if (state.departments.isEmpty() || state.categories.isEmpty()) loadOptions()
        state.filter.department?.let { if (state.municipalities.isEmpty()) loadMunicipalities(it) }
    }

    private fun loadOptions() {
        mOptionsJob?.cancel()
        mState.update { it.copy(optionsError = null) }
        mOptionsJob = viewModelScope.launch {
            val (departments, categories) = coroutineScope {
                val departments = async { repository.departments() }
                val categories = async { repository.categories() }
                departments.await() to categories.await()
            }
            if (departments is ApiResult.Success) mState.update { it.copy(departments = departments.value) }
            if (categories is ApiResult.Success) mState.update { it.copy(categories = categories.value) }
            val failure = listOf(departments, categories).filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) mState.update { it.copy(optionsError = failure.error) }
        }
    }

    private fun loadMunicipalities(department: String) {
        mMunicipalitiesJob?.cancel()
        mState.update { it.copy(optionsError = null) }
        mMunicipalitiesJob = viewModelScope.launch {
            when (val result = repository.municipalities(department)) {
                is ApiResult.Success -> mState.update {
                    // Si mientras tanto se eligió otro departamento, esta respuesta ya no sirve
                    if (it.filter.department == department) it.copy(municipalities = result.value) else it
                }
                is ApiResult.Failure -> mState.update { it.copy(optionsError = result.error) }
            }
        }
    }

    // ---- Búsqueda y paginación ----

    /** Busca con los filtros de los campos. Si había una búsqueda en curso, se cancela. */
    fun search() = startSearch(mState.value.filter, SodaQueryBuilder.PAGE_SIZE)

    /** "Reintentar" tras un error: repite la última búsqueda (con sus filtros, no con lo que se haya editado). */
    fun retry() = startSearch(savedState[KEY_SEARCHED] ?: mState.value.filter, SodaQueryBuilder.PAGE_SIZE)

    private fun startSearch(filter: ProviderFilter, limit: Int) {
        mSearchJob?.cancel()
        mMoreJob?.cancel()
        savedState[KEY_SEARCHED] = filter
        savedState[KEY_LOADED] = 0
        mState.update {
            it.copy(
                phase = SearchPhase.LOADING,
                providers = emptyList(),
                total = 0,
                query = null,
                error = null,
                loadingMore = false,
                loadMoreError = null
            )
        }
        mSearchJob = viewModelScope.launch {
            // El total y la primera página son dos pedidos independientes: van en paralelo
            val (count, page) = coroutineScope {
                val count = async { repository.count(filter) }
                val page = async { repository.page(filter, offset = 0, limit = limit) }
                count.await() to page.await()
            }
            when {
                count is ApiResult.Failure -> failSearch(count.error)
                page is ApiResult.Failure -> failSearch(page.error)
                count is ApiResult.Success && page is ApiResult.Success -> {
                    savedState[KEY_LOADED] = page.value.providers.size
                    mState.update {
                        it.copy(
                            phase = SearchPhase.LOADED,
                            providers = page.value.providers,
                            total = count.value,
                            query = page.value.query
                        )
                    }
                }
            }
        }
    }

    private fun failSearch(error: ApiError) {
        mState.update { it.copy(phase = SearchPhase.ERROR, error = error) }
    }

    /** Pide la siguiente página y la agrega al final. Si falla, lo ya cargado se queda. También sirve de "reintentar". */
    fun loadMore() {
        val state = mState.value
        val filter = savedState.get<ProviderFilter>(KEY_SEARCHED) ?: return
        if (!state.canLoadMore || state.loadingMore) return
        mState.update { it.copy(loadingMore = true, loadMoreError = null) }
        mMoreJob = viewModelScope.launch {
            when (val result = repository.page(filter, offset = state.providers.size, limit = SodaQueryBuilder.PAGE_SIZE)) {
                is ApiResult.Success -> {
                    val merged = (state.providers + result.value.providers).distinctBy { it.code }
                    savedState[KEY_LOADED] = merged.size
                    mState.update {
                        it.copy(
                            providers = merged,
                            // Una página vacía con filas por llegar: los datos cambiaron; no se ofrece más
                            total = if (result.value.providers.isEmpty()) merged.size else it.total,
                            query = result.value.query,
                            loadingMore = false
                        )
                    }
                }
                is ApiResult.Failure -> mState.update { it.copy(loadingMore = false, loadMoreError = result.error) }
            }
        }
    }
}
