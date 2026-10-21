package co.edu.unal.tictactoeonline.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.unal.tictactoeonline.PlayerStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LobbyUiState(
    /** Iniciando sesión o esperando la primera lista. */
    val loading: Boolean = true,
    /** No se pudo conectar: la pantalla ofrece reintentar. */
    val error: Boolean = false,
    val games: List<OnlineGame> = emptyList(),
    val nickname: String = "",
    /** Creando un juego o uniéndose a uno: los botones no responden. */
    val busy: Boolean = false
)

/** Cosas que pasan una sola vez (navegar, avisos). */
sealed interface LobbyEvent {
    data class OpenGame(val gameId: String) : LobbyEvent
    /** Para crear o unirse hace falta apodo: la Activity lo pide y llama a [LobbyViewModel.setNickname]. */
    data object AskNickname : LobbyEvent
    /** Otro se unió primero. */
    data object GameTaken : LobbyEvent
    data object Failed : LobbyEvent
}

/**
 * Pantalla inicial: la lista de juegos que esperan rival (enunciado), crear uno o unirse.
 * El modo sin conexión del reto 06 se abre desde la Activity, no pasa por acá.
 */
class LobbyViewModel(
    private val repository: GameRepository,
    private val players: PlayerStore,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    companion object {
        const val MAX_NICKNAME_LENGTH = 20

        // Un juego que lleva más de una hora esperando seguramente quedó colgado (sin Cloud Functions nadie lo borra)
        const val STALE_GAME_MS = 60 * 60 * 1000L

        fun factory(repository: GameRepository, players: PlayerStore) = viewModelFactory {
            initializer { LobbyViewModel(repository, players) }
        }
    }

    private val _uiState = MutableStateFlow(LobbyUiState(nickname = players.loadNickname()))
    val uiState: StateFlow<LobbyUiState> = _uiState.asStateFlow()

    private val _events = Channel<LobbyEvent>(Channel.BUFFERED)
    val events: Flow<LobbyEvent> = _events.receiveAsFlow()

    private var mUid: String? = null
    private var mGamesJob: Job? = null

    // Crear/unirse que quedó esperando a que el jugador elija apodo
    private var mPendingAction: ((String) -> Unit)? = null

    init {
        connect()
    }

    fun retry() = connect()

    fun createGame() = withNickname { nickname ->
        runBusy { _events.send(LobbyEvent.OpenGame(repository.createGame(nickname))) }
    }

    fun joinGame(gameId: String) = withNickname { nickname ->
        runBusy {
            _events.send(if (repository.joinGame(gameId, nickname)) LobbyEvent.OpenGame(gameId) else LobbyEvent.GameTaken)
        }
    }

    /** Guarda el apodo (sin espacios de más, hasta [MAX_NICKNAME_LENGTH]) y sigue con lo que estaba pendiente. */
    fun setNickname(nickname: String) {
        val clean = nickname.trim().take(MAX_NICKNAME_LENGTH)
        if (clean.isEmpty()) return
        players.saveNickname(clean)
        _uiState.update { it.copy(nickname = clean) }
        mPendingAction?.let { action ->
            mPendingAction = null
            action(clean)
        }
    }

    /** El jugador cerró el diálogo del apodo sin elegir uno. */
    fun cancelPendingAction() {
        mPendingAction = null
    }

    private fun connect() {
        mGamesJob?.cancel()
        _uiState.update { it.copy(loading = true, error = false) }
        mGamesJob = viewModelScope.launch {
            try {
                val uid = repository.signIn()
                mUid = uid
                repository.observeOpenGames().collect { games ->
                    val oldest = clock() - STALE_GAME_MS
                    // Los juegos propios no se listan: uno no se puede unir a su propio juego
                    val available = games.filter { it.xUid != uid && it.createdAt >= oldest }
                    _uiState.update { it.copy(loading = false, games = available) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    private fun withNickname(action: (String) -> Unit) {
        val nickname = _uiState.value.nickname
        if (nickname.isNotBlank()) {
            action(nickname)
        } else {
            mPendingAction = action
            _events.trySend(LobbyEvent.AskNickname)
        }
    }

    /** Una acción contra la nube a la vez; sin sesión iniciada no se hace nada. */
    private fun runBusy(block: suspend () -> Unit) {
        if (mUid == null || _uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(LobbyEvent.Failed)
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
}
