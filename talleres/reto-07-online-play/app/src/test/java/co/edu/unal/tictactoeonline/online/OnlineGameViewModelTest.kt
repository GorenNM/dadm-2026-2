package co.edu.unal.tictactoeonline.online

import androidx.lifecycle.SavedStateHandle
import co.edu.unal.tictactoeonline.GameStore
import co.edu.unal.tictactoeonline.Move
import co.edu.unal.tictactoeonline.StoredGame
import co.edu.unal.tictactoeonline.TicTacToeGame.Companion.OPEN_SPOT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineGameViewModelTest {

    private class FakeStore(var stored: StoredGame = StoredGame()) : GameStore {
        override fun load() = stored
        override fun save(stored: StoredGame) {
            this.stored = stored
        }
    }

    private val server = FakeServer()
    private val alice = FakeGameRepository(server, "alice")
    private val bob = FakeGameRepository(server, "bob")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: GameRepository,
        gameId: String,
        store: GameStore = FakeStore(),
        handle: SavedStateHandle = SavedStateHandle(mapOf(OnlineGameViewModel.KEY_GAME_ID to gameId))
    ) = OnlineGameViewModel(handle, repository, store)

    /** Junta todo lo que emite [flow] mientras dure el test. */
    private fun <T> TestScope.collected(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(items) }
        return items
    }

    /** Alice crea, Bob se une; los dos con la partida abierta. */
    private suspend fun TestScope.startedGame(): Triple<String, OnlineGameViewModel, OnlineGameViewModel> {
        val id = alice.createGame("Alice")
        bob.joinGame(id, "Bob")
        val aliceVm = viewModel(alice, id)
        val bobVm = viewModel(bob, id)
        advanceUntilIdle()
        return Triple(id, aliceVm, bobVm)
    }

    private val OnlineGameViewModel.state get() = uiState.value

    @Test
    fun creatorWaitsForRival_andCannotMove() = runTest {
        val id = alice.createGame("Alice")
        val vm = viewModel(alice, id)
        advanceUntilIdle()

        assertEquals(GameStatus.WAITING, vm.state.status)
        assertEquals(PLAYER_X, vm.state.mySymbol)
        assertFalse(vm.state.isMyTurn)
        vm.onCellTouched(4)
        advanceUntilIdle()
        assertTrue(vm.state.board.all { it == OPEN_SPOT })
    }

    @Test
    fun rivalJoins_creatorStarts_bothSeeTheMove() = runTest {
        val (_, aliceVm, bobVm) = startedGame()
        val bobMoves = collected(bobVm.moves)

        assertEquals(GameStatus.PLAYING, aliceVm.state.status)
        assertEquals("Bob", aliceVm.state.oName)
        assertTrue(aliceVm.state.isMyTurn)
        assertFalse(bobVm.state.isMyTurn)

        aliceVm.onCellTouched(4)
        advanceUntilIdle()

        assertEquals(PLAYER_X, bobVm.state.board[4])
        assertTrue(bobVm.state.isMyTurn)
        // Sonido y animación de la jugada del rival
        assertEquals(listOf(Move(PLAYER_X, 4)), bobMoves)
    }

    @Test
    fun touchOutOfTurn_orOnOccupiedCell_isIgnored() = runTest {
        val (_, aliceVm, bobVm) = startedGame()
        bobVm.onCellTouched(0)
        advanceUntilIdle()
        assertTrue(aliceVm.state.board.all { it == OPEN_SPOT })

        aliceVm.onCellTouched(4)
        advanceUntilIdle()
        bobVm.onCellTouched(4)
        advanceUntilIdle()
        assertEquals(PLAYER_X, bobVm.state.board[4])
        assertTrue(bobVm.state.isMyTurn)
    }

    @Test
    fun doubleTap_onlyOneMove() = runTest {
        val (_, aliceVm, _) = startedGame()
        aliceVm.onCellTouched(0)
        aliceVm.onCellTouched(1)
        advanceUntilIdle()
        assertEquals(1, aliceVm.state.board.count { it != OPEN_SPOT })
    }

    @Test
    fun win_thenRematch_otherPlayerStarts_scoreKept() = runTest {
        val (_, aliceVm, bobVm) = startedGame()
        for ((vm, cell) in listOf(aliceVm to 0, bobVm to 3, aliceVm to 1, bobVm to 4, aliceVm to 2)) {
            vm.onCellTouched(cell)
            advanceUntilIdle()
        }
        assertEquals(GameStatus.FINISHED, bobVm.state.status)
        assertEquals(2, bobVm.state.winner)
        assertEquals(1, bobVm.state.xWins)

        // Los dos piden revancha a la vez: se reinicia una sola vez
        aliceVm.rematch()
        bobVm.rematch()
        advanceUntilIdle()
        assertEquals(GameStatus.PLAYING, aliceVm.state.status)
        assertTrue(aliceVm.state.board.all { it == OPEN_SPOT })
        assertEquals(PLAYER_O, aliceVm.state.turn)
        assertTrue(bobVm.state.isMyTurn)
        assertEquals(1, aliceVm.state.xWins)
    }

    @Test
    fun rivalLeaves_gameAbandoned_lastOneOutDeletesIt() = runTest {
        val (id, aliceVm, bobVm) = startedGame()
        val bobEvents = collected(bobVm.events)

        bobVm.leave()
        advanceUntilIdle()
        assertEquals(listOf(OnlineEvent.Left), bobEvents)
        assertEquals(GameStatus.ABANDONED, aliceVm.state.status)
        assertFalse(aliceVm.state.isMyTurn)

        aliceVm.leave()
        advanceUntilIdle()
        assertFalse(id in server.games.value)
    }

    @Test
    fun creatorLeavesWhileWaiting_gameDeleted() = runTest {
        val id = alice.createGame("Alice")
        val vm = viewModel(alice, id)
        advanceUntilIdle()
        vm.leave()
        advanceUntilIdle()
        assertTrue(server.games.value.isEmpty())
        assertFalse(vm.state.gone)
    }

    @Test
    fun gameDeleted_isGone() = runTest {
        val (_, _, bobVm) = startedGame()
        server.games.value = emptyMap()
        advanceUntilIdle()
        assertTrue(bobVm.state.gone)
    }

    @Test
    fun rivalDisconnects_isShown_andComesBack() = runTest {
        val (id, aliceVm, _) = startedGame()
        assertTrue(aliceVm.state.rivalOnline)

        server.setOnline(id, PLAYER_O, false)
        advanceUntilIdle()
        assertFalse(aliceVm.state.rivalOnline)

        server.setOnline(id, PLAYER_O, true)
        advanceUntilIdle()
        assertTrue(aliceVm.state.rivalOnline)
    }

    @Test
    fun processDeath_sameGameRestored_noMoveReplayed() = runTest {
        val (id, aliceVm, _) = startedGame()
        aliceVm.onCellTouched(4)
        advanceUntilIdle()

        // Lo que Android le devuelve a un ViewModel nuevo: solo el SavedStateHandle (el id viene del Intent)
        val restored = viewModel(alice, id, handle = SavedStateHandle(mapOf(OnlineGameViewModel.KEY_GAME_ID to id)))
        val moves = collected(restored.moves)
        advanceUntilIdle()

        assertEquals(PLAYER_X, restored.state.mySymbol)
        assertEquals(PLAYER_X, restored.state.board[4])
        assertFalse(restored.state.isMyTurn)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun toggleMuted_keepsLocalScore() = runTest {
        val store = FakeStore(StoredGame(xWins = 5))
        val id = alice.createGame("Alice")
        val vm = viewModel(alice, id, store)
        advanceUntilIdle()

        vm.toggleMuted()
        assertTrue(vm.state.muted)
        assertTrue(store.stored.muted)
        assertEquals(5, store.stored.xWins)
    }
}
