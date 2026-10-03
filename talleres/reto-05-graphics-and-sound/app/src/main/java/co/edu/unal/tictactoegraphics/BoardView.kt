package co.edu.unal.tictactoegraphics

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation

/**
 * Tablero del triqui dibujado a mano sobre un Canvas: grilla + fichas X/O.
 * Reemplaza los botones de los retos anteriores (enunciado: "Creating a Custom View").
 * No conoce la UI de la Activity: solo pinta [TicTacToeGame] y avisa qué celda se tocó.
 */
class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val GRID_WIDTH = 6
        private const val MOVE_ANIMATION_MS = 220L
    }

    private val mGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = GRID_WIDTH.toFloat()
    }

    private val mHumanDrawable = ContextCompat.getDrawable(context, R.drawable.ic_piece_x)!!.mutate()
    private val mComputerDrawable = ContextCompat.getDrawable(context, R.drawable.ic_piece_o)!!.mutate()

    private val mPiecePadding = (10 * resources.displayMetrics.density).toInt()

    private var mGame: TicTacToeGame? = null
    private var mOnCellTouched: ((Int) -> Unit)? = null

    // Ficha recién puesta: "aparece" agrandándose en vez de dibujarse de golpe (reto 04 lo hacía con los botones)
    private var mAnimatingCell = -1
    private var mAnimationScale = 1f
    private var mAnimator: ValueAnimator? = null

    fun setGame(game: TicTacToeGame) {
        mGame = game
    }

    fun setOnCellTouchedListener(listener: (Int) -> Unit) {
        mOnCellTouched = listener
    }

    fun getBoardCellWidth(): Int = (width - paddingLeft - paddingRight) / 3
    fun getBoardCellHeight(): Int = (height - paddingTop - paddingBottom) / 3

    /** Redibuja el tablero. Si [animateCell] es una celda válida (0-8), su ficha aparece animada. */
    fun refresh(animateCell: Int = -1) {
        mAnimator?.cancel()
        if (animateCell in 0 until TicTacToeGame.BOARD_SIZE) {
            mAnimatingCell = animateCell
            mAnimator = ValueAnimator.ofFloat(0.25f, 1f).apply {
                duration = MOVE_ANIMATION_MS
                interpolator = OvershootInterpolator(2.5f)
                addUpdateListener {
                    mAnimationScale = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            mAnimatingCell = -1
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // El fondo (bg_board) deja padding: la grilla se dibuja adentro, no pisa el borde redondeado
        val boardWidth = width - paddingLeft - paddingRight
        val boardHeight = height - paddingTop - paddingBottom
        val cellWidth = boardWidth / 3
        val cellHeight = boardHeight / 3

        canvas.withTranslation(paddingLeft.toFloat(), paddingTop.toFloat()) {
            // Las dos líneas verticales y las dos horizontales de la grilla
            drawLine(cellWidth.toFloat(), 0f, cellWidth.toFloat(), boardHeight.toFloat(), mGridPaint)
            drawLine((cellWidth * 2).toFloat(), 0f, (cellWidth * 2).toFloat(), boardHeight.toFloat(), mGridPaint)
            drawLine(0f, cellHeight.toFloat(), boardWidth.toFloat(), cellHeight.toFloat(), mGridPaint)
            drawLine(0f, (cellHeight * 2).toFloat(), boardWidth.toFloat(), (cellHeight * 2).toFloat(), mGridPaint)

            val game = mGame ?: return@withTranslation
            for (i in 0 until TicTacToeGame.BOARD_SIZE) {
                val drawable = when (game.getBoardOccupant(i)) {
                    TicTacToeGame.HUMAN_PLAYER -> mHumanDrawable
                    TicTacToeGame.COMPUTER_PLAYER -> mComputerDrawable
                    else -> null
                } ?: continue

                val bounds = BoardGeometry.cellBounds(i, boardWidth, boardHeight, mPiecePadding)
                if (i == mAnimatingCell && mAnimationScale != 1f) {
                    val cx = (bounds[0] + bounds[2]) / 2f
                    val cy = (bounds[1] + bounds[3]) / 2f
                    val halfWidth = (bounds[2] - bounds[0]) / 2f * mAnimationScale
                    val halfHeight = (bounds[3] - bounds[1]) / 2f * mAnimationScale
                    drawable.setBounds((cx - halfWidth).toInt(), (cy - halfHeight).toInt(), (cx + halfWidth).toInt(), (cy + halfHeight).toInt())
                } else {
                    drawable.setBounds(bounds[0], bounds[1], bounds[2], bounds[3])
                }
                drawable.draw(this)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            performClick()
            val boardWidth = width - paddingLeft - paddingRight
            val boardHeight = height - paddingTop - paddingBottom
            val x = event.x.toInt() - paddingLeft
            val y = event.y.toInt() - paddingTop
            val pos = BoardGeometry.cellIndex(x, y, boardWidth, boardHeight)
            mOnCellTouched?.invoke(pos)
        }
        // No nos interesan los eventos de move/up: no hace falta seguir reportando el touch
        return false
    }

    // performClick() avisa a los servicios de accesibilidad (TalkBack) que se hizo click
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
