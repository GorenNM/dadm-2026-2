package co.edu.unal.tourismregistry.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.unal.tourismregistry.data.ApiError
import co.edu.unal.tourismregistry.data.ApiResult
import co.edu.unal.tourismregistry.data.Provider
import co.edu.unal.tourismregistry.data.TourismRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Loaded(val provider: Provider) : DetailUiState

    /** El código ya no está en el dataset (o la pantalla se abrió sin código). */
    data object NotFound : DetailUiState
    data class Failed(val error: ApiError) : DetailUiState
}

/**
 * El detalle de un prestador. Solo recibe el código RNT (que llega como extra del Intent y el SavedStateHandle
 * conserva) y vuelve a consultar el servicio: así la pantalla se reconstruye sola si el proceso murió.
 */
class DetailViewModel(
    private val repository: TourismRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    companion object {
        /** Extra del Intent con el código RNT; createSavedStateHandle() lo copia al SavedStateHandle. */
        const val KEY_CODE = "code"

        fun factory(repository: TourismRepository) = viewModelFactory {
            initializer { DetailViewModel(repository, createSavedStateHandle()) }
        }
    }

    private val mCode: String? = savedState.get<String>(KEY_CODE)?.takeIf { it.isNotBlank() }

    private val mState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = mState.asStateFlow()

    private var mJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        val code = mCode
        if (code == null) {
            mState.value = DetailUiState.NotFound
            return
        }
        mJob?.cancel()
        mState.value = DetailUiState.Loading
        mJob = viewModelScope.launch {
            mState.value = when (val result = repository.provider(code)) {
                is ApiResult.Success -> result.value?.let { DetailUiState.Loaded(it) } ?: DetailUiState.NotFound
                is ApiResult.Failure -> DetailUiState.Failed(result.error)
            }
        }
    }
}
