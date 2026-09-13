package co.edu.unal.tictactoe

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import co.edu.unal.tictactoe.TicTacToeGame.Companion.COMPUTER_PLAYER
import co.edu.unal.tictactoe.TicTacToeGame.Companion.HUMAN_PLAYER
import co.edu.unal.tictactoe.databinding.ActivityMainBinding

class AndroidTicTacToeActivity : AppCompatActivity() {

    private enum class GameMode { VS_ANDROID, TWO_PLAYERS }

    companion object {
        // Pausa antes de que Android mueva, para que se alcance a ver su turno
        private const val COMPUTER_MOVE_DELAY_MS = 700L
        private const val MOVE_ANIMATION_MS = 350L
    }

    private lateinit var binding: ActivityMainBinding

    // Represents the internal state of the game
    private lateinit var mGame: TicTacToeGame

    // Buttons making up the board
    private lateinit var mBoardButtons: Array<Button>

    // Various text displayed
    private lateinit var mInfoTextView: TextView

    private var mGameOver = false

    private var mMode = GameMode.VS_ANDROID

    // Jugador que tiene que mover ahora. En vs Android, X es el humano y O es Android.
    private var mCurrentPlayer = HUMAN_PLAYER

    // True mientras Android "piensa": el tablero no acepta toques
    private var mComputerThinking = false

    private val mHandler = Handler(Looper.getMainLooper())
    private val mComputerMoveRunnable = Runnable { makeComputerMove() }

    // Extra Challenge: alternar quién empieza y llevar el marcador (X = humano / jugador 1)
    private var mXGoesFirst = true
    private var mXWins = 0
    private var mOWins = 0
    private var mTies = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        mBoardButtons = arrayOf(
            binding.one, binding.two, binding.three,
            binding.four, binding.five, binding.six,
            binding.seven, binding.eight, binding.nine
        )
        mInfoTextView = binding.information

        mGame = TicTacToeGame()

        binding.newGameButton.setOnClickListener { startNewGame() }

        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = if (checkedId == R.id.mode_two_players) GameMode.TWO_PLAYERS else GameMode.VS_ANDROID
            if (newMode != mMode) {
                // Cambiar de modo reinicia el marcador: no tiene sentido mezclar partidas
                mMode = newMode
                mXGoesFirst = true
                mXWins = 0
                mOWins = 0
                mTies = 0
                startNewGame()
            }
        }

        startNewGame()
    }

    override fun onDestroy() {
        mHandler.removeCallbacks(mComputerMoveRunnable)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.new_game -> {
                startNewGame()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Set up the game board.
    private fun startNewGame() {
        // Si Android estaba por mover en la partida anterior, se cancela
        mHandler.removeCallbacks(mComputerMoveRunnable)
        mComputerThinking = false

        mGame.clearBoard()
        mGameOver = false

        // Reset all buttons
        for (i in mBoardButtons.indices) {
            mBoardButtons[i].animate().cancel()
            mBoardButtons[i].scaleX = 1f
            mBoardButtons[i].scaleY = 1f
            mBoardButtons[i].text = ""
            mBoardButtons[i].isEnabled = true
            mBoardButtons[i].setOnClickListener(ButtonClickListener(i))
        }

        mCurrentPlayer = if (mXGoesFirst) HUMAN_PLAYER else COMPUTER_PLAYER
        mXGoesFirst = !mXGoesFirst

        when {
            mMode == GameMode.TWO_PLAYERS ->
                mInfoTextView.text = getString(R.string.first_player, playerName(mCurrentPlayer))
            mCurrentPlayer == HUMAN_PLAYER ->
                mInfoTextView.setText(R.string.first_human)
            else -> {
                mInfoTextView.setText(R.string.first_computer)
                scheduleComputerMove()
            }
        }

        updateTurnIndicator()
        updateScores()
    }

    // Handles clicks on the game board buttons
    private inner class ButtonClickListener(private val location: Int) : View.OnClickListener {
        override fun onClick(view: View) {
            if (mGameOver || mComputerThinking || !mBoardButtons[location].isEnabled) return

            setMove(mCurrentPlayer, location)
            if (checkGameOver()) return

            switchTurn()
            if (mMode == GameMode.VS_ANDROID) {
                mInfoTextView.setText(R.string.turn_computer)
                scheduleComputerMove()
            } else {
                mInfoTextView.text = getString(R.string.turn_player, playerName(mCurrentPlayer))
            }
        }
    }

    private fun scheduleComputerMove() {
        mComputerThinking = true
        mHandler.postDelayed(mComputerMoveRunnable, COMPUTER_MOVE_DELAY_MS)
    }

    private fun makeComputerMove() {
        mComputerThinking = false
        val move = mGame.getComputerMove()
        setMove(COMPUTER_PLAYER, move)
        animateMove(mBoardButtons[move])
        if (checkGameOver()) return

        switchTurn()
        mInfoTextView.setText(R.string.turn_human)
    }

    private fun setMove(player: Char, location: Int) {
        mGame.setMove(player, location)
        mBoardButtons[location].isEnabled = false
        mBoardButtons[location].text = player.toString()
        val color = if (player == HUMAN_PLAYER) R.color.human_player else R.color.computer_player
        // setTextColor(int) aplica el color a todos los estados, así el botón deshabilitado no lo vuelve gris
        mBoardButtons[location].setTextColor(ContextCompat.getColor(this, color))
    }

    /** La casilla aparece desde pequeña y "rebota" hasta su tamaño normal. */
    private fun animateMove(button: Button) {
        button.scaleX = 0.3f
        button.scaleY = 0.3f
        button.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(MOVE_ANIMATION_MS)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
    }

    /** Revisa si alguien ganó o hubo empate; si la partida terminó, actualiza marcador y mensaje. */
    private fun checkGameOver(): Boolean {
        when (mGame.checkForWinner()) {
            0 -> return false
            1 -> {
                mTies++
                endGame(getString(R.string.result_tie))
            }
            2 -> {
                mXWins++
                endGame(winnerMessage(HUMAN_PLAYER))
            }
            else -> {
                mOWins++
                endGame(winnerMessage(COMPUTER_PLAYER))
            }
        }
        return true
    }

    private fun winnerMessage(player: Char): String = when {
        mMode == GameMode.TWO_PLAYERS -> getString(R.string.result_player_wins, playerName(player))
        player == HUMAN_PLAYER -> getString(R.string.result_human_wins)
        else -> getString(R.string.result_computer_wins)
    }

    private fun endGame(message: String) {
        mGameOver = true
        mInfoTextView.text = message
        updateTurnIndicator()
        updateScores()
    }

    private fun switchTurn() {
        mCurrentPlayer = if (mCurrentPlayer == HUMAN_PLAYER) COMPUTER_PLAYER else HUMAN_PLAYER
        updateTurnIndicator()
    }

    private fun playerName(player: Char): String = getString(
        when {
            mMode == GameMode.VS_ANDROID && player == HUMAN_PLAYER -> R.string.player_you
            mMode == GameMode.VS_ANDROID -> R.string.player_android
            player == HUMAN_PLAYER -> R.string.player_1
            else -> R.string.player_2
        }
    )

    /** Resalta al jugador que tiene el turno; al terminar la partida ninguno queda resaltado. */
    private fun updateTurnIndicator() {
        binding.turnX.text = getString(R.string.player_label, HUMAN_PLAYER.toString(), playerName(HUMAN_PLAYER))
        binding.turnO.text = getString(R.string.player_label, COMPUTER_PLAYER.toString(), playerName(COMPUTER_PLAYER))
        styleTurnLabel(binding.turnX, !mGameOver && mCurrentPlayer == HUMAN_PLAYER)
        styleTurnLabel(binding.turnO, !mGameOver && mCurrentPlayer == COMPUTER_PLAYER)
    }

    private fun styleTurnLabel(label: TextView, active: Boolean) {
        label.setBackgroundResource(if (active) R.drawable.bg_turn_active else R.drawable.bg_turn_inactive)
        label.alpha = if (active) 1f else 0.4f
    }

    private fun updateScores() {
        binding.humanScore.text = getString(R.string.score_player, playerName(HUMAN_PLAYER), mXWins)
        binding.tieScore.text = getString(R.string.score_ties, mTies)
        binding.computerScore.text = getString(R.string.score_player, playerName(COMPUTER_PLAYER), mOWins)
    }
}
