package co.edu.unal.tictactoegraphics

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardGeometryTest {

    @Test
    fun cellBounds_topLeftCell_startsAtOrigin() {
        val bounds = BoardGeometry.cellBounds(0, boardWidth = 300, boardHeight = 300, piecePadding = 10)
        assertEquals(intArrayOf(10, 10, 90, 90).toList(), bounds.toList())
    }

    @Test
    fun cellBounds_centerCell_isOffsetByOneCell() {
        val bounds = BoardGeometry.cellBounds(4, boardWidth = 300, boardHeight = 300, piecePadding = 10)
        assertEquals(intArrayOf(110, 110, 190, 190).toList(), bounds.toList())
    }

    @Test
    fun cellBounds_bottomRightCell_endsAtBoardEdge() {
        val bounds = BoardGeometry.cellBounds(8, boardWidth = 300, boardHeight = 300, piecePadding = 10)
        assertEquals(intArrayOf(210, 210, 290, 290).toList(), bounds.toList())
    }

    @Test
    fun cellIndex_mapsEachThirdOfTheBoardToItsRowAndColumn() {
        assertEquals(0, BoardGeometry.cellIndex(x = 10, y = 10, boardWidth = 300, boardHeight = 300))
        assertEquals(4, BoardGeometry.cellIndex(x = 150, y = 150, boardWidth = 300, boardHeight = 300))
        assertEquals(8, BoardGeometry.cellIndex(x = 299, y = 299, boardWidth = 300, boardHeight = 300))
    }

    @Test
    fun cellIndex_clampsTouchesRightAtTheEdge() {
        // El enunciado divide x,y / cellWidth: en el borde exacto (300) sin clamp se saldría del tablero
        assertEquals(8, BoardGeometry.cellIndex(x = 300, y = 300, boardWidth = 300, boardHeight = 300))
    }
}
