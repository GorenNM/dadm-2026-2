package co.edu.unal.tictactoeonline.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.unal.tictactoeonline.GameStore
import co.edu.unal.tictactoeonline.Move
import co.edu.unal.tictactoeonline.TicTacToeGame.Companion.BOARD_SIZE
import co.edu.unal.tictactoeonline.TicTacToeGame.Companion.OPEN_SPOT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Todo lo que [OnlineGameActivity] necesita para dibujar la partida. */
data class OnlineUiState(
    val loading: Boolean = true,
    /** El juego ya no existe (el rival lo borró al salir): hay que volver a la lista. */
    val gone: Boolean = false,
    val status: GameStatus = GameStatus.WAITING,
    val board: List<Char> = List(BOARD_SIZE) { OPEN_SPOT },
    val mySymbol: Char = PLAYER_X,
    val turn: Char = PLAYER_X,
    /** Mismo código que [co.edu.unal.tictactoeonline.TicTacToeGame.checkForWinner]. */
    val winner: Int = 0,
    val xName: String = "",
    val oName: String = "",
    val rivalOnline: Boolean = true,
    val xWins: Int = 0,
    val oWins: Int = 0,
    val ties: Int = 0,
    val muted: Boolean = false
) {
    val isMyTurn: Boolean get() = status == GameStatus.PLAYING && turn == mySymbol
}

sealed interface OnlineEvent {
    /** Ya se avisó a la nube que este jugador se fue: la Activity se cierra. */
    data object Left : OnlineEvent
    data object Failed : OnlineEvent
}

/**
 * Una partida en línea. La verdad está en la nube: este ViewModel escucha el juego, lo traduce a
 * [OnlineUiState] y manda las jugadas; las reglas las aplica [OnlineRules] dentro de la transacción.
 *
 * - Rotar: el ViewModel y la escucha siguen vivos.
 * - Matar el proceso: el id del juego queda en el [SavedStateHandle] (viene en el Intent). Al volver
 *   se escucha otra vez el mismo juego y el uid anónimo es el mismo, así que se sigue jugando.
 */
class OnlineGameViewModel(
    savedState: SavedStateHandle,
    private val repository: GameRepository,
    private val store: GameStore
) : ViewModel() {

    companion object {
        /** Extra del Intent: el SavedStateHandle lo recibe solo y lo guarda si muere el proceso. */
        const val KEY_GAME_ID = "gameId"

        // Sin red, salir no espera para siempre: el servidor igual marca al jugador como desconectado
        const val LEAVE_TIMEOUT_MS = 3000L

        fun factory(repository: GameRepository, store: GameStore) = viewModelFactory {
            initializer { OnlineGameViewModel(createSavedStateHandle(), repository, store) }
        }
    }

    private val gameId: String = checkNotNull(savedState[KEY_GAME_ID]) { "Missing $KEY_GAME_ID" }

    private var mUid = ""
    private var mGame: OnlineGame? = null
    private var mPresenceJob: Job? = null
    private var mLeaving = false
    private var mMoveInFlight = false

    private val _uiState = MutableStateFlow(OnlineUiState(muted = store.load().muted))
    val uiState: StateFlow<OnlineUiState> = _uiState.asStateFlow()

    private val _moves = Channel<Move>(Channel.BUFFERED)
    val moves: Flow<Move> = _moves.receiveAsFlow()

    private val _events = Channel<OnlineEvent>(Channel.BUFFERED)
    val events: Flow<OnlineEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                mUid = repository.signIn()
                repository.observeGame(gameId).collect { onGameChanged(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, gone = true) }
            }
        }
    }

    fun onCellTouched(cell: Int) {
        val state = _uiState.value
        if (!state.isMyTurn || mMoveInFlight || mLeaving || state.board.getOrNull(cell) != OPEN_SPOT) return

        // Hasta que la nube conteste no se aceptan más toques (doble toque rápido)
        mMoveInFlight = true
        launchCloud { repository.makeMove(gameId, cell) }.invokeOnCompletion { mMoveInFlight = false }
    }

    /** Si los dos la piden a la vez, la transacción reinicia la partida una sola vez. */
    fun rematch() {
        if (_uiState.value.status != GameStatus.FINISHED || mLeaving) return
        launchCloud { repository.rematch(gameId) }
    }

    fun leave() {
        if (mLeaving) return
        mLeaving = true
        mPresenceJob?.cancel()
        viewModelScope.launch {
            try {
                withTimeout(LEAVE_TIMEOUT_MS) { repository.leaveGame(gameId) }
            } catch (_: TimeoutCancellationException) {
                // Sin red: se sale igual
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Lo mismo: el jugador quiere irse, no tiene sentido retenerlo
            }
            _events.send(OnlineEvent.Left)
        }
    }

    /** El sonido es la misma preferencia del modo local. Se relee antes de guardar: no pisa el marcador local. */
    fun toggleMuted() {
        val muted = !_uiState.value.muted
        store.save(store.load().copy(muted = muted))
        _uiState.update { it.copy(muted = muted) }
    }

    private fun onGameChanged(game: OnlineGame?) {
        // Al salir, el juego se borra o se abandona: ya no interesa
        if (mLeaving) return

        val symbol = game?.symbolOf(mUid)
        if (game == null || symbol == null) {
            mPresenceJob?.cancel()
            mGame = null
            _uiState.update { it.copy(loading = false, gone = true) }
            return
        }

        // Sonido y animación de las fichas nuevas, propias o del rival.
        // En la primera carga no (p. ej. al volver después de que Android mató el proceso).
        val previous = mGame
        if (previous != null) {
            for (i in game.board.indices) {
                if (game.board[i] != OPEN_SPOT && game.board[i] != previous.board[i]) _moves.trySend(Move(game.board[i], i))
            }
        }
        mGame = game

        // Presencia desde que hay rival; mientras se espera, el juego se borra solo si el creador se desconecta
        if (game.status != GameStatus.WAITING && mPresenceJob == null) {
            mPresenceJob = launchCloud { repository.stayOnline(gameId, symbol) }
        }

        _uiState.update {
            it.copy(
                loading = false,
                gone = false,
                status = game.status,
                board = game.board,
                mySymbol = symbol,
                turn = game.turn,
                winner = game.winner,
                xName = game.xName,
                oName = game.oName ?: "",
                rivalOnline = game.isOnline(OnlineRules.other(symbol)),
                xWins = game.xWins,
                oWins = game.oWins,
                ties = game.ties
            )
        }
    }

    /** Corre [block] contra la nube; si falla (p. ej. sin permisos) avisa a la UI en vez de reventar. */
    private fun launchCloud(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _events.send(OnlineEvent.Failed)
        }
    }
}
