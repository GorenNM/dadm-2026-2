package co.edu.unal.tictactoegraphics

import java.util.Random

/**
 * Lógica del triqui, portada de TicTacToeConsole.java (Frank McCown).
 * No sabe nada de Android: la Activity solo la usa a través de los métodos públicos.
 */
class TicTacToeGame(private val mRand: Random = Random()) {

    /** Nivel de dificultad de la IA. Easy: al azar. Harder: gana si puede, si no al azar (no bloquea). */
    enum class DifficultyLevel { Easy, Harder, Expert }

    companion object {
        // Characters used to represent the human, computer, and open spots
        const val HUMAN_PLAYER = 'X'
        const val COMPUTER_PLAYER = 'O'
        const val OPEN_SPOT = ' '

        const val BOARD_SIZE = 9

        private val WINNING_LINES = arrayOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8), // horizontales
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8), // verticales
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)                       // diagonales
        )
    }

    // Current difficulty level. Expert por defecto, igual que el comportamiento original del reto 03.
    var difficultyLevel: DifficultyLevel = DifficultyLevel.Expert

    private val mBoard = CharArray(BOARD_SIZE) { OPEN_SPOT }

    /** Clear the board of all X's and O's by setting all spots to OPEN_SPOT. */
    fun clearBoard() {
        mBoard.fill(OPEN_SPOT)
    }

    /** What's on the board at [location] (0-8): HUMAN_PLAYER, COMPUTER_PLAYER, or OPEN_SPOT. */
    fun getBoardOccupant(location: Int): Char = mBoard[location]

    /**
     * Set the given player at the given location on the game board.
     * The location must be available, or the board will not be changed.
     *
     * @param player - The HUMAN_PLAYER or COMPUTER_PLAYER
     * @param location - The location (0-8) to place the move
     */
    fun setMove(player: Char, location: Int) {
        if (location in 0 until BOARD_SIZE && mBoard[location] == OPEN_SPOT) {
            mBoard[location] = player
        }
    }

    /**
     * Return the best move for the computer to make, according to the current
     * [difficultyLevel]. You must call setMove() to actually make the computer
     * move to that location.
     * @return The best move for the computer to make (0-8).
     */
    fun getComputerMove(): Int {
        var move = -1
        if (difficultyLevel == DifficultyLevel.Easy) {
            move = getRandomMove()
        } else if (difficultyLevel == DifficultyLevel.Harder) {
            move = getWinningMove()
            if (move == -1) move = getRandomMove()
        } else if (difficultyLevel == DifficultyLevel.Expert) {
            // Try to win, but if that's not possible, block. If that's not possible, move anywhere.
            move = getWinningMove()
            if (move == -1) move = getBlockingMove()
            if (move == -1) move = getRandomMove()
        }
        return move
    }

    /** A move O can make to win, or -1 if there isn't one. Leaves the board unchanged. */
    private fun getWinningMove(): Int = findWinningSpot(COMPUTER_PLAYER) ?: -1

    /** A move O can make to block X from winning, or -1 if there isn't one. Leaves the board unchanged. */
    private fun getBlockingMove(): Int = findWinningSpot(HUMAN_PLAYER) ?: -1

    /** A random open spot on the board. */
    private fun getRandomMove(): Int {
        val openSpots = (0 until BOARD_SIZE).filter { mBoard[it] == OPEN_SPOT }
        check(openSpots.isNotEmpty()) { "No moves left on a full board" }
        return openSpots[mRand.nextInt(openSpots.size)]
    }

    /**
     * Check for a winner and return a status value indicating who has won.
     * @return Return 0 if no winner or tie yet, 1 if it's a tie, 2 if X won,
     * or 3 if O won.
     */
    fun checkForWinner(): Int {
        for ((a, b, c) in WINNING_LINES) {
            if (mBoard[a] != OPEN_SPOT && mBoard[a] == mBoard[b] && mBoard[b] == mBoard[c]) {
                return if (mBoard[a] == HUMAN_PLAYER) 2 else 3
            }
        }

        // If there is an open spot left, no one has won yet
        if (mBoard.any { it == OPEN_SPOT }) return 0

        // All places are taken, so it's a tie
        return 1
    }

    /** Devuelve la casilla libre con la que [player] ganaría, o null si no hay. El tablero queda igual. */
    private fun findWinningSpot(player: Char): Int? {
        val winValue = if (player == HUMAN_PLAYER) 2 else 3
        for (i in 0 until BOARD_SIZE) {
            if (mBoard[i] == OPEN_SPOT) {
                mBoard[i] = player
                val wins = checkForWinner() == winValue
                mBoard[i] = OPEN_SPOT
                if (wins) return i
            }
        }
        return null
    }
}
