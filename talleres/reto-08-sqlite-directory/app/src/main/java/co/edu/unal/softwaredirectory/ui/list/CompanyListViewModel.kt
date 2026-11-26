package co.edu.unal.softwaredirectory.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.unal.softwaredirectory.data.Classification
import co.edu.unal.softwaredirectory.data.Company
import co.edu.unal.softwaredirectory.data.CompanyFilter
import co.edu.unal.softwaredirectory.data.CompanyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CompanyListUiState(
    /** Todavía no llegó la primera consulta. */
    val loading: Boolean = true,
    val companies: List<Company> = emptyList(),
    /** Lo que hay escrito en la búsqueda (al instante; la consulta va unos ms detrás). */
    val query: String = "",
    val classifications: Set<Classification> = emptySet(),
    /** [companies] salió de una consulta con filtro: si está vacía es "sin resultados", no "sin empresas". */
    val filtered: Boolean = false
)

/**
 * La lista del directorio con el filtro por nombre y/o clasificación. El filtro se aplica en SQL
 * (ver [CompanyRepository.observeCompanies]) y vive en el [SavedStateHandle].
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CompanyListViewModel(
    repository: CompanyRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    companion object {
        /** Mientras se escribe, se consulta cuando se deja de teclear este tiempo y no en cada letra. */
        const val SEARCH_DEBOUNCE_MS = 300L

        private const val KEY_QUERY = "query"
        private const val KEY_CLASSIFICATIONS = "classifications"

        fun factory(repository: CompanyRepository) = viewModelFactory {
            initializer { CompanyListViewModel(repository, createSavedStateHandle()) }
        }
    }

    private val mQuery = savedState.getStateFlow(KEY_QUERY, "")
    private val mClassifications = savedState.getStateFlow(KEY_CLASSIFICATIONS, ArrayList<String>())
        .map { codes -> codes.mapNotNull(Classification::fromCode).toSet() }

    // Borrar la búsqueda filtra al instante; escribir espera a que se deje de teclear
    private val mResults = combine(
        mQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
        mClassifications,
        ::CompanyFilter
    )
        .distinctUntilChanged()
        .flatMapLatest { filter -> repository.observeCompanies(filter).map { filter to it } }

    val uiState: StateFlow<CompanyListUiState> =
        combine(mQuery, mClassifications, mResults) { query, classifications, (filter, companies) ->
            CompanyListUiState(
                loading = false,
                companies = companies,
                query = query,
                classifications = classifications,
                filtered = !filter.isEmpty
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CompanyListUiState(
                query = mQuery.value,
                classifications = savedState.get<ArrayList<String>>(KEY_CLASSIFICATIONS).orEmpty()
                    .mapNotNull(Classification::fromCode).toSet()
            )
        )

    fun onQueryChanged(query: String) {
        savedState[KEY_QUERY] = query
    }

    fun onClassificationChecked(classification: Classification, checked: Boolean) {
        val current = savedState.get<ArrayList<String>>(KEY_CLASSIFICATIONS).orEmpty()
            .mapNotNull(Classification::fromCode).toSet()
        val updated = if (checked) current + classification else current - classification
        if (updated == current) return
        // En el orden del enum, para que el mismo filtro dé siempre la misma lista
        savedState[KEY_CLASSIFICATIONS] = ArrayList(Classification.entries.filter { it in updated }.map { it.code })
    }

    fun clearFilters() {
        savedState[KEY_QUERY] = ""
        savedState[KEY_CLASSIFICATIONS] = ArrayList<String>()
    }
}
