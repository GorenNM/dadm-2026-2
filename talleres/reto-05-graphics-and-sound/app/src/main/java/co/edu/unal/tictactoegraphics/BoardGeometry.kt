package co.edu.unal.tictactoegraphics

/**
 * Cálculo de coordenadas del tablero: sin nada de Android, para poder testear con JUnit
 * sin emulador. [BoardView] es la única que la usa, pasándole sus dimensiones en píxeles.
 */
object BoardGeometry {

    /** Rectángulo (left, top, right, bottom) donde dibujar la ficha de la celda [index] (0-8). */
    fun cellBounds(
        index: Int,
        boardWidth: Int,
        boardHeight: Int,
        piecePadding: Int
    ): IntArray {
        val col = index % 3
        val row = index / 3
        val cellWidth = boardWidth / 3
        val cellHeight = boardHeight / 3
        return intArrayOf(
            col * cellWidth + piecePadding,
            row * cellHeight + piecePadding,
            (col + 1) * cellWidth - piecePadding,
            (row + 1) * cellHeight - piecePadding
        )
    }

    /** Celda (0-8) que corresponde al punto tocado ([x], [y]) dentro de un tablero de [boardWidth]x[boardHeight]. */
    fun cellIndex(x: Int, y: Int, boardWidth: Int, boardHeight: Int): Int {
        val col = (x * 3 / boardWidth).coerceIn(0, 2)
        val row = (y * 3 / boardHeight).coerceIn(0, 2)
        return row * 3 + col
    }
}
