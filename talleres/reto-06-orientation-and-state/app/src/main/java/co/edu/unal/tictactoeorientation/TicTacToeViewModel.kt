package co.edu.unal.tictactoeorientation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.unal.tictactoeorientation.TicTacToeGame.Companion.COMPUTER_PLAYER
import co.edu.unal.tictactoeorientation.TicTacToeGame.Companion.HUMAN_PLAYER
import co.edu.unal.tictactoeorientation.TicTacToeGame.DifficultyLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Todo lo que la Activity necesita para dibujar la pantalla. */
data class GameUiState(
    val board: List<Char>,
    val mode: GameMode,
    val currentPlayer: Char,
    val gameOver: Boolean,
    /** Resultado de [TicTacToeGame.checkForWinner]: 0 sigue, 1 empate, 2 ganó X, 3 ganó O. */
    val winner: Int,
    val xWins: Int,
    val oWins: Int,
    val ties: Int,
    val difficulty: DifficultyLevel,
    val muted: Boolean
)

/** Una ficha recién puesta: la Activity la usa para el sonido y la animación (evento de una sola vez). */
data class Move(val player: Char, val location: Int)

/**
 * Estado y reglas de la partida, fuera de la Activity.
 *
 * Tres niveles de "memoria":
 * - El ViewModel mismo sobrevive a rotar la pantalla: la Activity se recrea, el ViewModel no.
 *   Hasta la jugada pendiente de Android (la corrutina con el delay) sigue corriendo.
 * - [SavedStateHandle] (el Bundle de onSaveInstanceState por dentro) sobrevive a que Android
 *   mate el proceso en segundo plano: tablero, turno y quién empieza.
 * - [GameStore] (SharedPreferences) sobrevive a cerrar la app: marcador, dificultad, modo, sonido.
 */
class TicTacToeViewModel(
    private val savedState: SavedStateHandle,
    private val store: GameStore,
    private val game: TicTacToeGame = TicTacToeGame()
) : ViewModel() {

    companion object {
        // Pausa antes de que Android mueva, para que se alcance a ver su turno
        const val COMPUTER_MOVE_DELAY_MS = 700L

        private const val KEY_BOARD = "board"
        private const val KEY_GAME_OVER = "mGameOver"
        private const val KEY_CURRENT_PLAYER = "mCurrentPlayer"
        private const val KEY_X_GOES_FIRST = "mGoFirst"

        fun factory(store: GameStore) = viewModelFactory {
            initializer { TicTacToeViewModel(createSavedStateHandle(), store) }
        }
    }

    private var mStored = store.load()

    private var mGameOver = false

    // Jugador que tiene que mover ahora. En vs Android, X es el humano y O es Android.
    // Se guarda: si no, al restaurar Android movía dos veces seguidas (bug del paso 5 del enunciado).
    private var mCurrentPlayer = HUMAN_PLAYER

    // Extra Challenge del reto 03: alternar quién empieza
    private var mXGoesFirst = true

    // Jugada pendiente de Android; mientras está activa el tablero no acepta toques
    private var mComputerJob: Job? = null

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _moves = Channel<Move>(Channel.BUFFERED)
    val moves: Flow<Move> = _moves.receiveAsFlow()

    init {
        game.difficultyLevel = mStored.difficulty

        val board = savedState.get<CharArray>(KEY_BOARD)
        if (board == null) {
            startNewGame()
        } else {
            // Restore the game's state (el proceso murió con la app en segundo plano)
            game.setBoardState(board)
            mGameOver = savedState[KEY_GAME_OVER] ?: false
            mCurrentPlayer = savedState[KEY_CURRENT_PLAYER] ?: HUMAN_PLAYER
            mXGoesFirst = savedState[KEY_X_GOES_FIRST] ?: true
            publish()
            // Extra Challenge 2: la jugada pendiente de Android murió con el proceso; se vuelve a programar
            if (isComputersTurn()) scheduleComputerMove()
        }
    }

    fun newGame() = startNewGame()

    fun onCellTouched(location: Int) {
        if (mGameOver || mComputerJob?.isActive == true) return
        if (game.getBoardOccupant(location) != TicTacToeGame.OPEN_SPOT) return

        makeMove(mCurrentPlayer, location)
        if (!mGameOver) {
            switchTurn()
            if (isComputersTurn()) scheduleComputerMove()
        }
        publish()
    }

    /** Cambiar de modo reinicia el marcador: no tiene sentido mezclar partidas. Mismo modo: no hace nada. */
    fun setMode(mode: GameMode) {
        if (mode == mStored.mode) return
        updateStored(mStored.copy(mode = mode, xWins = 0, oWins = 0, ties = 0))
        mXGoesFirst = true
        startNewGame()
    }

    fun setDifficulty(level: DifficultyLevel) {
        game.difficultyLevel = level
        updateStored(mStored.copy(difficulty = level))
        publish()
    }

    fun toggleMuted() {
        updateStored(mStored.copy(muted = !mStored.muted))
        publish()
    }

    fun resetScores() {
        updateStored(mStored.copy(xWins = 0, oWins = 0, ties = 0))
        publish()
    }

    private fun startNewGame() {
        // Si Android estaba por mover en la partida anterior, se cancela
        mComputerJob?.cancel()
        mComputerJob = null

        game.clearBoard()
        mGameOver = false
        mCurrentPlayer = if (mXGoesFirst) HUMAN_PLAYER else COMPUTER_PLAYER
        mXGoesFirst = !mXGoesFirst

        if (isComputersTurn()) scheduleComputerMove()
        publish()
    }

    private fun isComputersTurn() =
        mStored.mode == GameMode.VS_ANDROID && !mGameOver && mCurrentPlayer == COMPUTER_PLAYER

    private fun scheduleComputerMove() {
        mComputerJob = viewModelScope.launch {
            delay(COMPUTER_MOVE_DELAY_MS)
            mComputerJob = null
            makeMove(COMPUTER_PLAYER, game.getComputerMove())
            if (!mGameOver) switchTurn()
            publish()
        }
    }

    private fun makeMove(player: Char, location: Int) {
        game.setMove(player, location)
        _moves.trySend(Move(player, location))

        when (game.checkForWinner()) {
            0 -> return
            1 -> updateStored(mStored.copy(ties = mStored.ties + 1))
            2 -> updateStored(mStored.copy(xWins = mStored.xWins + 1))
            else -> updateStored(mStored.copy(oWins = mStored.oWins + 1))
        }
        mGameOver = true
    }

    private fun switchTurn() {
        mCurrentPlayer = if (mCurrentPlayer == HUMAN_PLAYER) COMPUTER_PLAYER else HUMAN_PLAYER
    }

    /** Se guarda apenas cambia (no en onStop como el enunciado): el ViewModel no se entera de onStop. */
    private fun updateStored(stored: StoredGame) {
        mStored = stored
        store.save(stored)
    }

    /** Guarda el estado de la partida en el SavedStateHandle y avisa a la UI. */
    private fun publish() {
        savedState[KEY_BOARD] = game.getBoardState()
        savedState[KEY_GAME_OVER] = mGameOver
        savedState[KEY_CURRENT_PLAYER] = mCurrentPlayer
        savedState[KEY_X_GOES_FIRST] = mXGoesFirst
        _uiState.value = buildUiState()
    }

    private fun buildUiState() = GameUiState(
        board = game.getBoardState().toList(),
        mode = mStored.mode,
        currentPlayer = mCurrentPlayer,
        gameOver = mGameOver,
        winner = game.checkForWinner(),
        xWins = mStored.xWins,
        oWins = mStored.oWins,
        ties = mStored.ties,
        difficulty = mStored.difficulty,
        muted = mStored.muted
    )
}
