package co.edu.unal.tictactoeonline.online

import co.edu.unal.tictactoeonline.TicTacToeGame.Companion.OPEN_SPOT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRulesTest {

    private fun GameChange.game() = (this as GameChange.Update).game

    private val waiting = OnlineRules.newGame("alice", "Alice")
    private val playing = OnlineRules.join(waiting, "bob", "Bob").game()

    /** Juega las casillas en orden, alternando desde quien tiene el turno. */
    private fun OnlineGame.play(vararg cells: Int): OnlineGame = cells.fold(this) { game, cell ->
        val uid = if (game.turn == PLAYER_X) game.xUid else checkNotNull(game.oUid)
        OnlineRules.applyMove(game, uid, cell).game()
    }

    @Test
    fun newGame_waitingWithCreatorAsX() {
        assertEquals(GameStatus.WAITING, waiting.status)
        assertEquals(PLAYER_X, waiting.symbolOf("alice"))
        assertNull(waiting.oUid)
        assertTrue(waiting.board.all { it == OPEN_SPOT })
    }

    @Test
    fun join_secondPlayerIsO_andGameStarts() {
        assertEquals(GameStatus.PLAYING, playing.status)
        assertEquals(PLAYER_O, playing.symbolOf("bob"))
        assertEquals("Bob", playing.oName)
        assertEquals(PLAYER_X, playing.turn)
    }

    @Test
    fun join_rejectedForOwnGame_takenGame_orNotWaiting() {
        assertEquals(GameChange.Reject, OnlineRules.join(waiting, "alice", "Alice"))
        assertEquals(GameChange.Reject, OnlineRules.join(playing, "carol", "Carol"))
        assertEquals(GameChange.Reject, OnlineRules.join(waiting.copy(status = GameStatus.ABANDONED), "bob", "Bob"))
    }

    @Test
    fun move_placesSymbolAndSwitchesTurn() {
        val game = OnlineRules.applyMove(playing, "alice", 4).game()
        assertEquals(PLAYER_X, game.board[4])
        assertEquals(PLAYER_O, game.turn)
        assertEquals(GameStatus.PLAYING, game.status)
    }

    @Test
    fun move_rejectedOutOfTurn_occupied_outOfRange_strangerOrNotPlaying() {
        assertEquals(GameChange.Reject, OnlineRules.applyMove(playing, "bob", 0))
        val afterX = playing.play(4)
        assertEquals(GameChange.Reject, OnlineRules.applyMove(afterX, "bob", 4))
        assertEquals(GameChange.Reject, OnlineRules.applyMove(afterX, "bob", 9))
        assertEquals(GameChange.Reject, OnlineRules.applyMove(afterX, "carol", 0))
        assertEquals(GameChange.Reject, OnlineRules.applyMove(waiting, "alice", 0))
    }

    @Test
    fun win_finishesAndCountsForTheWinner() {
        // X: 0 1 2 (fila de arriba); O: 3 4
        val game = playing.play(0, 3, 1, 4, 2)
        assertEquals(GameStatus.FINISHED, game.status)
        assertEquals(2, game.winner)
        assertEquals(1, game.xWins)
        assertEquals(0, game.oWins)
        assertEquals(GameChange.Reject, OnlineRules.applyMove(game, "bob", 5))
    }

    @Test
    fun tie_finishesAndCountsTie() {
        // X O X / X O O / O X X
        val game = playing.play(0, 1, 2, 4, 3, 5, 7, 6, 8)
        assertEquals(GameStatus.FINISHED, game.status)
        assertEquals(1, game.winner)
        assertEquals(1, game.ties)
    }

    @Test
    fun rematch_clearsBoard_keepsScore_otherPlayerStarts() {
        val finished = playing.play(0, 3, 1, 4, 2)
        val rematch = OnlineRules.rematch(finished, "bob").game()
        assertEquals(GameStatus.PLAYING, rematch.status)
        assertTrue(rematch.board.all { it == OPEN_SPOT })
        assertEquals(0, rematch.winner)
        assertEquals(1, rematch.xWins)
        assertEquals(PLAYER_O, rematch.starter)
        assertEquals(PLAYER_O, rematch.turn)
        // Una segunda revancha simultánea ya ve la partida en curso: no reinicia de nuevo
        assertEquals(GameChange.Reject, OnlineRules.rematch(rematch, "alice"))
    }

    @Test
    fun rematch_onlyWhenFinished_andOnlyPlayers() {
        assertEquals(GameChange.Reject, OnlineRules.rematch(playing, "alice"))
        assertEquals(GameChange.Reject, OnlineRules.rematch(playing.play(0, 3, 1, 4, 2), "carol"))
    }

    @Test
    fun leave_deletesWaiting_abandonsPlayingOrFinished_lastOneOutDeletes() {
        assertEquals(GameChange.Delete, OnlineRules.leave(waiting, "alice"))
        val abandoned = OnlineRules.leave(playing, "bob").game()
        assertEquals(GameStatus.ABANDONED, abandoned.status)
        assertEquals(GameStatus.ABANDONED, OnlineRules.leave(playing.play(0, 3, 1, 4, 2), "alice").game().status)
        assertEquals(GameChange.Delete, OnlineRules.leave(abandoned, "alice"))
        assertEquals(GameChange.Reject, OnlineRules.leave(playing, "carol"))
    }

    @Test
    fun map_roundTrip() {
        val game = playing.play(4, 0).copy(id = "g1", createdAt = 1234L, xOnline = true, oOnline = false)
        assertEquals(game, OnlineGame.fromMap("g1", game.toMap()))
    }

    @Test
    fun map_boardStoredWithDashes_andNumbersAsLong() {
        val map = playing.play(4).toMap()
        assertEquals("----X----", map[OnlineGame.KEY_BOARD])
        assertEquals("playing", map[OnlineGame.KEY_STATUS])

        // Firebase devuelve los números como Long
        val fromFirebase = map + (OnlineGame.KEY_X_WINS to 2L) + (OnlineGame.KEY_CREATED_AT to 99L)
        val game = checkNotNull(OnlineGame.fromMap("g1", fromFirebase))
        assertEquals(2, game.xWins)
        assertEquals(99L, game.createdAt)
    }

    @Test
    fun map_notAGame_returnsNull() {
        // Lo que dejaría un onDisconnect sobre un juego ya borrado
        assertNull(OnlineGame.fromMap("g1", mapOf(OnlineGame.KEY_X_ONLINE to false)))
        assertNull(OnlineGame.fromMap("g1", waiting.toMap() + (OnlineGame.KEY_BOARD to "XO")))
        assertNull(OnlineGame.fromMap("g1", waiting.toMap() + (OnlineGame.KEY_STATUS to "paused")))
    }
}
