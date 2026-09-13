package co.edu.unal.tictactoe

import co.edu.unal.tictactoe.TicTacToeGame.Companion.COMPUTER_PLAYER
import co.edu.unal.tictactoe.TicTacToeGame.Companion.HUMAN_PLAYER
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Random

class TicTacToeGameTest {

    private lateinit var game: TicTacToeGame

    @Before
    fun setUp() {
        game = TicTacToeGame(Random(42))
    }

    private fun place(player: Char, vararg locations: Int) {
        locations.forEach { game.setMove(player, it) }
    }

    @Test
    fun newBoard_hasNoWinner() {
        assertEquals(0, game.checkForWinner())
    }

    @Test
    fun humanRow_returns2() {
        place(HUMAN_PLAYER, 3, 4, 5)
        assertEquals(2, game.checkForWinner())
    }

    @Test
    fun computerDiagonal_returns3() {
        place(COMPUTER_PLAYER, 2, 4, 6)
        assertEquals(3, game.checkForWinner())
    }

    @Test
    fun fullBoardWithoutLine_isTie() {
        // X O X
        // X O O
        // O X X
        place(HUMAN_PLAYER, 0, 2, 3, 7, 8)
        place(COMPUTER_PLAYER, 1, 4, 5, 6)
        assertEquals(1, game.checkForWinner())
    }

    @Test
    fun setMove_onOccupiedSpot_isIgnored() {
        place(HUMAN_PLAYER, 0)
        place(COMPUTER_PLAYER, 0)
        place(HUMAN_PLAYER, 1, 2)
        assertEquals(2, game.checkForWinner())
    }

    @Test
    fun clearBoard_removesAllMoves() {
        place(HUMAN_PLAYER, 0, 1, 2)
        game.clearBoard()
        assertEquals(0, game.checkForWinner())
    }

    @Test
    fun computer_prefersWinningOverBlocking() {
        place(COMPUTER_PLAYER, 0, 1)
        place(HUMAN_PLAYER, 3, 4)
        assertEquals(2, game.getComputerMove())
    }

    @Test
    fun computer_blocksHumanWin() {
        place(HUMAN_PLAYER, 0, 1)
        place(COMPUTER_PLAYER, 4)
        assertEquals(2, game.getComputerMove())
    }

    @Test
    fun getComputerMove_doesNotChangeBoard() {
        place(HUMAN_PLAYER, 0, 1)
        place(COMPUTER_PLAYER, 4)
        val move = game.getComputerMove()
        // Si la casilla sigue libre, el humano puede ocuparla y gana
        place(HUMAN_PLAYER, move)
        assertEquals(2, game.checkForWinner())
    }

    @Test
    fun randomMove_isAlwaysAnOpenSpot() {
        // X O X
        // X O O
        // O X _   -> la única casilla libre es 8 y nadie gana ni bloquea con ella
        place(HUMAN_PLAYER, 0, 2, 3, 7)
        place(COMPUTER_PLAYER, 1, 4, 5, 6)
        repeat(50) {
            assertEquals(8, game.getComputerMove())
        }
    }
}
