package co.edu.unal.tictactoegraphics

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import co.edu.unal.tictactoegraphics.TicTacToeGame.Companion.COMPUTER_PLAYER
import co.edu.unal.tictactoegraphics.TicTacToeGame.Companion.HUMAN_PLAYER
import co.edu.unal.tictactoegraphics.databinding.ActivityMainBinding

class AndroidTicTacToeActivity : AppCompatActivity() {

    private enum class GameMode { VS_ANDROID, TWO_PLAYERS }

    companion object {
        // Pausa antes de que Android mueva, para que se alcance a ver su turno
        private const val COMPUTER_MOVE_DELAY_MS = 700L
    }

    private lateinit var binding: ActivityMainBinding

    // Represents the internal state of the game
    private lateinit var mGame: TicTacToeGame

    private lateinit var mBoardView: BoardView
    private lateinit var mSoundEffects: SoundEffects

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

        mGame = TicTacToeGame()
        mSoundEffects = SoundEffects(applicationContext)

        mBoardView = binding.board
        mBoardView.setGame(mGame)
        mBoardView.setOnCellTouchedListener { pos -> onCellTouched(pos) }

        mInfoTextView = binding.information

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
        mSoundEffects.release()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.toggle_sound)?.apply {
            setIcon(if (mSoundEffects.muted) R.drawable.ic_sound_off else R.drawable.ic_sound_on)
            setTitle(if (mSoundEffects.muted) R.string.menu_sound_off else R.string.menu_sound_on)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.new_game -> {
                startNewGame()
                true
            }
            R.id.ai_difficulty -> {
                showDifficultyDialog()
                true
            }
            R.id.toggle_sound -> {
                mSoundEffects.muted = !mSoundEffects.muted
                invalidateOptionsMenu()
                true
            }
            R.id.choose_theme -> {
                showThemeDialog()
                true
            }
            R.id.about -> {
                showAboutDialog()
                true
            }
            R.id.quit -> {
                showQuitDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Radio buttons con los tres niveles; el actual queda preseleccionado. */
    private fun showDifficultyDialog() {
        val levels = arrayOf(
            getString(R.string.difficulty_easy),
            getString(R.string.difficulty_harder),
            getString(R.string.difficulty_expert)
        )
        val selected = mGame.difficultyLevel.ordinal

        AlertDialog.Builder(this)
            .setTitle(R.string.difficulty_choose)
            .setSingleChoiceItems(levels, selected) { dialog, item ->
                dialog.dismiss()
                mGame.difficultyLevel = TicTacToeGame.DifficultyLevel.entries[item]
                Toast.makeText(applicationContext, levels[item], Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** Claro/Oscuro/Según el sistema; el actual queda preseleccionado. Cambiar recrea la Activity sola. */
    private fun showThemeDialog() {
        val options = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )
        val nightModes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        val selected = nightModes.indexOf(AppCompatDelegate.getDefaultNightMode()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_choose)
            .setSingleChoiceItems(options, selected) { dialog, item ->
                dialog.dismiss()
                val mode = nightModes[item]
                ThemePreference.saveNightMode(applicationContext, mode)
                AppCompatDelegate.setDefaultNightMode(mode)
            }
            .show()
    }

    private fun showQuitDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.quit_question)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Extra Challenge: diálogo custom con about_dialog.xml inflado a mano. */
    private fun showAboutDialog() {
        val layout = layoutInflater.inflate(R.layout.about_dialog, null)
        AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // Set up the game board.
    private fun startNewGame() {
        // Si Android estaba por mover en la partida anterior, se cancela
        mHandler.removeCallbacks(mComputerMoveRunnable)
        mComputerThinking = false

        mGame.clearBoard()
        mGameOver = false
        mBoardView.refresh()

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

    // Toque en el tablero: BoardView ya convirtió la posición (x,y) en una celda (0-8)
    private fun onCellTouched(location: Int) {
        if (mGameOver || mComputerThinking) return
        if (mGame.getBoardOccupant(location) != TicTacToeGame.OPEN_SPOT) return

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

    private fun scheduleComputerMove() {
        mComputerThinking = true
        mHandler.postDelayed(mComputerMoveRunnable, COMPUTER_MOVE_DELAY_MS)
    }

    private fun makeComputerMove() {
        mComputerThinking = false
        val move = mGame.getComputerMove()
        setMove(COMPUTER_PLAYER, move)
        if (checkGameOver()) return

        switchTurn()
        mInfoTextView.setText(R.string.turn_human)
    }

    private fun setMove(player: Char, location: Int) {
        mGame.setMove(player, location)
        mSoundEffects.play(player)
        mBoardView.refresh(animateCell = location)
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
