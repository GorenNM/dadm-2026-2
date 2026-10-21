package co.edu.unal.tictactoeonline.online

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
class LobbyViewModelTest {

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

    private fun lobby(repository: GameRepository, nickname: String = "") =
        LobbyViewModel(repository, FakePlayerStore(nickname)) { server.now }

    private fun <T> TestScope.collected(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(items) }
        return items
    }

    @Test
    fun listsOthersWaitingGames_notOwn_notStarted_notStale() = runTest {
        val carol = FakeGameRepository(server, "carol")
        val open = bob.createGame("Bob")
        alice.createGame("Alice")
        val started = carol.createGame("Carol")
        FakeGameRepository(server, "dave").joinGame(started, "Dave")
        server.now -= LobbyViewModel.STALE_GAME_MS + 1
        carol.createGame("Carol old")
        server.now += LobbyViewModel.STALE_GAME_MS + 1

        val vm = lobby(alice)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals(listOf(open), vm.uiState.value.games.map { it.id })
    }

    @Test
    fun listUpdatesWhenSomeoneCreatesAGame() = runTest {
        val vm = lobby(alice)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.games.isEmpty())

        bob.createGame("Bob")
        advanceUntilIdle()
        assertEquals(listOf("Bob"), vm.uiState.value.games.map { it.xName })
    }

    @Test
    fun createWithoutNickname_asksFirst_thenCreates() = runTest {
        val vm = lobby(alice)
        val events = collected(vm.events)
        advanceUntilIdle()

        vm.createGame()
        advanceUntilIdle()
        assertEquals(listOf(LobbyEvent.AskNickname), events)
        assertTrue(server.games.value.isEmpty())

        vm.setNickname("  Alice  ")
        advanceUntilIdle()
        val game = server.games.value.values.single()
        assertEquals("Alice", game.xName)
        assertEquals(LobbyEvent.OpenGame(game.id), events.last())
    }

    @Test
    fun nicknameDialogCancelled_nothingCreated() = runTest {
        val vm = lobby(alice)
        advanceUntilIdle()
        vm.createGame()
        vm.cancelPendingAction()
        vm.setNickname("Alice")
        advanceUntilIdle()
        assertTrue(server.games.value.isEmpty())
    }

    @Test
    fun setNickname_trimmedTruncatedAndSaved_blankIgnored() = runTest {
        val store = FakePlayerStore("Alice")
        val vm = LobbyViewModel(alice, store) { server.now }
        vm.setNickname("   ")
        assertEquals("Alice", vm.uiState.value.nickname)

        vm.setNickname("  " + "x".repeat(30))
        assertEquals("x".repeat(LobbyViewModel.MAX_NICKNAME_LENGTH), store.nickname)
        assertEquals(store.nickname, vm.uiState.value.nickname)
    }

    @Test
    fun join_opensGame() = runTest {
        val id = bob.createGame("Bob")
        val vm = lobby(alice, "Alice")
        val events = collected(vm.events)
        advanceUntilIdle()

        vm.joinGame(id)
        advanceUntilIdle()
        assertEquals(listOf(LobbyEvent.OpenGame(id)), events)
        assertEquals("alice", server.games.value.getValue(id).oUid)
    }

    @Test
    fun twoJoinAtOnce_oneGetsIt_otherIsToldItWasTaken() = runTest {
        val id = bob.createGame("Bob")
        val aliceLobby = lobby(alice, "Alice")
        val carolLobby = lobby(FakeGameRepository(server, "carol"), "Carol")
        val aliceEvents = collected(aliceLobby.events)
        val carolEvents = collected(carolLobby.events)
        advanceUntilIdle()

        aliceLobby.joinGame(id)
        carolLobby.joinGame(id)
        advanceUntilIdle()

        assertEquals(listOf(LobbyEvent.OpenGame(id)), aliceEvents)
        assertEquals(listOf(LobbyEvent.GameTaken), carolEvents)
        assertEquals("alice", server.games.value.getValue(id).oUid)
    }

    @Test
    fun signInFails_showsError_retryRecovers() = runTest {
        alice.failSignIn = true
        val vm = lobby(alice, "Alice")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error)

        // Sin sesión, crear no hace nada
        vm.createGame()
        advanceUntilIdle()
        assertTrue(server.games.value.isEmpty())

        alice.failSignIn = false
        vm.retry()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
    }
}
