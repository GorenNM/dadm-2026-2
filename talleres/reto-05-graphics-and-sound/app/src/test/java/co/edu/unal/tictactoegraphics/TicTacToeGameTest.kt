package co.edu.unal.tictactoegraphics

import co.edu.unal.tictactoegraphics.TicTacToeGame.Companion.COMPUTER_PLAYER
import co.edu.unal.tictactoegraphics.TicTacToeGame.Companion.HUMAN_PLAYER
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

    @Test
    fun difficultyLevel_defaultsToExpert() {
        assertEquals(TicTacToeGame.DifficultyLevel.Expert, game.difficultyLevel)
    }

    @Test
    fun getBoardOccupant_reflectsMoves() {
        place(HUMAN_PLAYER, 4)
        assertEquals(HUMAN_PLAYER, game.getBoardOccupant(4))
        assertEquals(TicTacToeGame.OPEN_SPOT, game.getBoardOccupant(0))
    }

    /** Random que siempre devuelve el mismo índice, para hacer determinista getRandomMove(). */
    private class FixedIndexRandom(private val index: Int) : Random() {
        override fun nextInt(bound: Int): Int = index
    }

    @Test
    fun easy_ignoresWinningMove_playsFixedRandomSpot() {
        // O ganaría en 2, pero en Easy debe ignorarlo y jugar al azar
        val g = TicTacToeGame(FixedIndexRandom(3))
        g.difficultyLevel = TicTacToeGame.DifficultyLevel.Easy
        g.setMove(COMPUTER_PLAYER, 0)
        g.setMove(COMPUTER_PLAYER, 1)
        // Casillas libres en orden: 2,3,4,5,6,7,8 -> índice 3 es la casilla 5
        assertEquals(5, g.getComputerMove())
    }

    @Test
    fun harder_prefersWinningMove() {
        place(COMPUTER_PLAYER, 0, 1)
        place(HUMAN_PLAYER, 3, 4)
        game.difficultyLevel = TicTacToeGame.DifficultyLevel.Harder
        assertEquals(2, game.getComputerMove())
    }

    @Test
    fun harder_doesNotBlockHumanWin() {
        // X gana en 2; O no tiene jugada ganadora, así que en Harder debe ignorar el bloqueo
        val g = TicTacToeGame(FixedIndexRandom(1))
        g.difficultyLevel = TicTacToeGame.DifficultyLevel.Harder
        g.setMove(HUMAN_PLAYER, 0)
        g.setMove(HUMAN_PLAYER, 1)
        // Casillas libres en orden: 2,3,4,5,6,7,8 -> índice 1 es la casilla 3 (no la de bloqueo, que es 2)
        assertEquals(3, g.getComputerMove())
    }
}
