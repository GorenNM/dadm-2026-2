package co.edu.unal.tictactoe

import java.util.Random

/**
 * Lógica del triqui, portada de TicTacToeConsole.java (Frank McCown).
 * No sabe nada de Android: la Activity solo la usa a través de los métodos públicos.
 */
class TicTacToeGame(private val mRand: Random = Random()) {

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

    private val mBoard = CharArray(BOARD_SIZE) { OPEN_SPOT }

    /** Clear the board of all X's and O's by setting all spots to OPEN_SPOT. */
    fun clearBoard() {
        mBoard.fill(OPEN_SPOT)
    }

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
     * Return the best move for the computer to make. You must call setMove()
     * to actually make the computer move to that location.
     * @return The best move for the computer to make (0-8).
     */
    fun getComputerMove(): Int {
        // First see if there's a move O can make to win
        findWinningSpot(COMPUTER_PLAYER)?.let { return it }

        // See if there's a move O can make to block X from winning
        findWinningSpot(HUMAN_PLAYER)?.let { return it }

        // Generate random move
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
