package co.edu.unal.tictactoeorientation

import androidx.lifecycle.SavedStateHandle
import co.edu.unal.tictactoeorientation.TicTacToeGame.Companion.COMPUTER_PLAYER
import co.edu.unal.tictactoeorientation.TicTacToeGame.Companion.HUMAN_PLAYER
import co.edu.unal.tictactoeorientation.TicTacToeGame.Companion.OPEN_SPOT
import co.edu.unal.tictactoeorientation.TicTacToeGame.DifficultyLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random

@OptIn(ExperimentalCoroutinesApi::class)
class TicTacToeViewModelTest {

    /** SharedPreferences en memoria. */
    private class FakeStore(var stored: StoredGame = StoredGame()) : GameStore {
        override fun load() = stored
        override fun save(stored: StoredGame) {
            this.stored = stored
        }
    }

    @Before
    fun setUp() {
        // viewModelScope corre en Main: en tests se reemplaza por un dispatcher con reloj virtual
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(store: FakeStore, handle: SavedStateHandle = SavedStateHandle()) =
        TicTacToeViewModel(handle, store, TicTacToeGame(Random(1)))

    /** Lo que Android le devuelve a un ViewModel nuevo después de matar el proceso: solo lo guardado. */
    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private val TicTacToeViewModel.pieces get() = uiState.value.board.count { it != OPEN_SPOT }

    @Test
    fun freshStart_emptyBoard_humanFirst() {
        val vm = viewModel(FakeStore())
        assertEquals(0, vm.pieces)
        assertEquals(HUMAN_PLAYER, vm.uiState.value.currentPlayer)
        assertFalse(vm.uiState.value.gameOver)
    }

    @Test
    fun vsAndroid_computerMovesAfterDelay() = runTest {
        val vm = viewModel(FakeStore())
        vm.onCellTouched(0)
        assertEquals(COMPUTER_PLAYER, vm.uiState.value.currentPlayer)
        advanceUntilIdle()
        assertEquals(2, vm.pieces)
        assertEquals(HUMAN_PLAYER, vm.uiState.value.currentPlayer)
    }

    @Test
    fun touchWhileComputerThinking_isIgnored() = runTest {
        val vm = viewModel(FakeStore())
        vm.onCellTouched(0)
        vm.onCellTouched(1)
        assertEquals(1, vm.pieces)
    }

    @Test
    fun touchOnOccupiedCell_isIgnored() {
        val vm = viewModel(FakeStore(StoredGame(mode = GameMode.TWO_PLAYERS)))
        vm.onCellTouched(4)
        vm.onCellTouched(4)
        assertEquals(1, vm.pieces)
        assertEquals(COMPUTER_PLAYER, vm.uiState.value.currentPlayer)
    }

    @Test
    fun processDeath_restoresBoardTurnAndWhoGoesFirst() {
        val store = FakeStore(StoredGame(mode = GameMode.TWO_PLAYERS))
        val handle = SavedStateHandle()
        val vm = viewModel(store, handle)
        vm.onCellTouched(0)
        vm.onCellTouched(4)
        vm.onCellTouched(8)

        val restored = viewModel(store, handle.afterProcessDeath())
        assertEquals(vm.uiState.value.board, restored.uiState.value.board)
        assertEquals(COMPUTER_PLAYER, restored.uiState.value.currentPlayer)

        // La primera partida la empezó X: la siguiente la empieza O, también después de restaurar
        restored.newGame()
        assertEquals(COMPUTER_PLAYER, restored.uiState.value.currentPlayer)
    }

    @Test
    fun processDeath_onHumansTurn_computerDoesNotMoveTwice() = runTest {
        // Bug del paso 5 del enunciado: si no se guarda de quién es el turno, Android mueve de nuevo
        val store = FakeStore()
        val handle = SavedStateHandle()
        val vm = viewModel(store, handle)
        vm.onCellTouched(0)
        advanceUntilIdle()

        val restored = viewModel(store, handle.afterProcessDeath())
        advanceUntilIdle()
        assertEquals(2, restored.pieces)
        assertEquals(HUMAN_PLAYER, restored.uiState.value.currentPlayer)
    }

    @Test
    fun processDeath_whileComputerThinking_computerStillMoves() = runTest {
        // Extra Challenge 2: la jugada pendiente de Android no se pierde
        val store = FakeStore()
        val handle = SavedStateHandle()
        viewModel(store, handle).onCellTouched(0)

        val restored = viewModel(store, handle.afterProcessDeath())
        assertEquals(COMPUTER_PLAYER, restored.uiState.value.currentPlayer)
        advanceUntilIdle()
        assertEquals(2, restored.pieces)
        assertEquals(HUMAN_PLAYER, restored.uiState.value.currentPlayer)
    }

    @Test
    fun win_updatesScore_andPersistsIt() {
        val store = FakeStore(StoredGame(mode = GameMode.TWO_PLAYERS))
        val vm = viewModel(store)
        // X: 0,1,2 (fila de arriba)   O: 3,4
        listOf(0, 3, 1, 4, 2).forEach { vm.onCellTouched(it) }

        assertTrue(vm.uiState.value.gameOver)
        assertEquals(2, vm.uiState.value.winner)
        assertEquals(1, vm.uiState.value.xWins)
        assertEquals(1, store.stored.xWins)

        // App cerrada y vuelta a abrir: sin SavedStateHandle, solo SharedPreferences
        assertEquals(1, viewModel(store).uiState.value.xWins)
    }

    @Test
    fun resetScores_zerosAndPersists() {
        val store = FakeStore(StoredGame(xWins = 3, oWins = 2, ties = 1))
        val vm = viewModel(store)
        vm.resetScores()
        assertEquals(StoredGame(), store.stored)
        assertEquals(0, vm.uiState.value.xWins + vm.uiState.value.oWins + vm.uiState.value.ties)
    }

    @Test
    fun setMode_sameMode_keepsScoresAndBoard() {
        val store = FakeStore(StoredGame(xWins = 3))
        val vm = viewModel(store)
        vm.onCellTouched(0)
        vm.setMode(GameMode.VS_ANDROID)
        assertEquals(3, vm.uiState.value.xWins)
        assertEquals(1, vm.pieces)
    }

    @Test
    fun setMode_otherMode_resetsScoresAndPersistsMode() {
        val store = FakeStore(StoredGame(xWins = 3))
        val vm = viewModel(store)
        vm.setMode(GameMode.TWO_PLAYERS)
        assertEquals(0, vm.uiState.value.xWins)
        assertEquals(GameMode.TWO_PLAYERS, store.stored.mode)
        assertEquals(GameMode.TWO_PLAYERS, viewModel(store).uiState.value.mode)
    }

    @Test
    fun difficulty_persistsAcrossRestarts() {
        // Extra Challenge 1
        val store = FakeStore()
        viewModel(store).setDifficulty(DifficultyLevel.Easy)
        assertEquals(DifficultyLevel.Easy, viewModel(store).uiState.value.difficulty)
    }

    @Test
    fun muted_persistsAcrossRestarts() {
        val store = FakeStore()
        viewModel(store).toggleMuted()
        assertTrue(viewModel(store).uiState.value.muted)
    }

    @Test
    fun move_emitsEventForSoundAndAnimation() = runTest {
        val vm = viewModel(FakeStore())
        vm.onCellTouched(4)
        assertEquals(Move(HUMAN_PLAYER, 4), vm.moves.first())
    }
}
