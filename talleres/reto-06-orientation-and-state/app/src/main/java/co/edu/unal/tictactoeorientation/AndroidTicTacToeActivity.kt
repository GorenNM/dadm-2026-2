package co.edu.unal.tictactoeorientation

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unal.tictactoeorientation.TicTacToeGame.Companion.COMPUTER_PLAYER
import co.edu.unal.tictactoeorientation.TicTacToeGame.Companion.HUMAN_PLAYER
import co.edu.unal.tictactoeorientation.databinding.AboutDialogBinding
import co.edu.unal.tictactoeorientation.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Solo UI: dibuja el [GameUiState] del ViewModel y le pasa los toques.
 * Al rotar, esta Activity se destruye y se crea otra; el [TicTacToeViewModel] es el mismo.
 */
class AndroidTicTacToeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val mViewModel: TicTacToeViewModel by viewModels {
        TicTacToeViewModel.factory(GamePreferences(applicationContext))
    }

    private lateinit var mBoardView: BoardView
    private lateinit var mSoundEffects: SoundEffects

    // Various text displayed
    private lateinit var mInfoTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // En landscape el notch queda de costado: displayCutout evita que tape el tablero
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        mSoundEffects = SoundEffects(applicationContext)

        mBoardView = binding.board
        mBoardView.setOnCellTouchedListener { pos -> mViewModel.onCellTouched(pos) }

        mInfoTextView = binding.information

        binding.newGameButton.setOnClickListener { mViewModel.newGame() }

        // También salta cuando el toggle se restaura solo o lo marca render(): ahí el modo es el mismo y no pasa nada
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mViewModel.setMode(if (checkedId == R.id.mode_two_players) GameMode.TWO_PLAYERS else GameMode.VS_ANDROID)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { mViewModel.uiState.collect { render(it) } }
                launch {
                    mViewModel.moves.collect { move ->
                        mSoundEffects.play(move.player)
                        mBoardView.animateMove(move.location)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mSoundEffects.release()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val muted = mViewModel.uiState.value.muted
        menu.findItem(R.id.toggle_sound)?.apply {
            setIcon(if (muted) R.drawable.ic_sound_off else R.drawable.ic_sound_on)
            setTitle(if (muted) R.string.menu_sound_off else R.string.menu_sound_on)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.new_game -> {
                mViewModel.newGame()
                true
            }
            R.id.ai_difficulty -> {
                showDifficultyDialog()
                true
            }
            R.id.reset_scores -> {
                mViewModel.resetScores()
                true
            }
            R.id.toggle_sound -> {
                mViewModel.toggleMuted()
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
        val selected = mViewModel.uiState.value.difficulty.ordinal

        AlertDialog.Builder(this)
            .setTitle(R.string.difficulty_choose)
            .setSingleChoiceItems(levels, selected) { dialog, item ->
                dialog.dismiss()
                mViewModel.setDifficulty(TicTacToeGame.DifficultyLevel.entries[item])
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

    /** Extra Challenge del reto 04: diálogo custom con about_dialog.xml inflado a mano. */
    private fun showAboutDialog() {
        val about = AboutDialogBinding.inflate(layoutInflater)
        about.aboutVersion.text = getString(R.string.about_version, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
        AlertDialog.Builder(this)
            .setView(about.root)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun render(state: GameUiState) {
        mBoardView.setBoard(state.board)

        val modeButton = if (state.mode == GameMode.TWO_PLAYERS) R.id.mode_two_players else R.id.mode_vs_android
        if (binding.modeToggle.checkedButtonId != modeButton) binding.modeToggle.check(modeButton)

        if (mSoundEffects.muted != state.muted) {
            mSoundEffects.muted = state.muted
            invalidateOptionsMenu()
        }

        mInfoTextView.text = statusMessage(state)
        updateTurnIndicator(state)
        updateScores(state)
    }

    /** El mensaje se arma del estado (no se guarda el texto como en el enunciado): así sigue el idioma actual. */
    private fun statusMessage(state: GameUiState): String {
        val twoPlayers = state.mode == GameMode.TWO_PLAYERS
        val current = playerName(state.mode, state.currentPlayer)
        return when {
            state.gameOver -> when (state.winner) {
                1 -> getString(R.string.result_tie)
                2 -> winnerMessage(state.mode, HUMAN_PLAYER)
                else -> winnerMessage(state.mode, COMPUTER_PLAYER)
            }
            // Tablero vacío: todavía nadie movió
            state.board.all { it == TicTacToeGame.OPEN_SPOT } -> when {
                twoPlayers -> getString(R.string.first_player, current)
                state.currentPlayer == HUMAN_PLAYER -> getString(R.string.first_human)
                else -> getString(R.string.first_computer)
            }
            twoPlayers -> getString(R.string.turn_player, current)
            state.currentPlayer == HUMAN_PLAYER -> getString(R.string.turn_human)
            else -> getString(R.string.turn_computer)
        }
    }

    private fun winnerMessage(mode: GameMode, player: Char): String = when {
        mode == GameMode.TWO_PLAYERS -> getString(R.string.result_player_wins, playerName(mode, player))
        player == HUMAN_PLAYER -> getString(R.string.result_human_wins)
        else -> getString(R.string.result_computer_wins)
    }

    private fun playerName(mode: GameMode, player: Char): String = getString(
        when {
            mode == GameMode.VS_ANDROID && player == HUMAN_PLAYER -> R.string.player_you
            mode == GameMode.VS_ANDROID -> R.string.player_android
            player == HUMAN_PLAYER -> R.string.player_1
            else -> R.string.player_2
        }
    )

    /** Resalta al jugador que tiene el turno; al terminar la partida ninguno queda resaltado. */
    private fun updateTurnIndicator(state: GameUiState) {
        binding.turnX.text = getString(R.string.player_label, HUMAN_PLAYER.toString(), playerName(state.mode, HUMAN_PLAYER))
        binding.turnO.text = getString(R.string.player_label, COMPUTER_PLAYER.toString(), playerName(state.mode, COMPUTER_PLAYER))
        styleTurnLabel(binding.turnX, !state.gameOver && state.currentPlayer == HUMAN_PLAYER)
        styleTurnLabel(binding.turnO, !state.gameOver && state.currentPlayer == COMPUTER_PLAYER)
    }

    private fun styleTurnLabel(label: TextView, active: Boolean) {
        label.setBackgroundResource(if (active) R.drawable.bg_turn_active else R.drawable.bg_turn_inactive)
        label.alpha = if (active) 1f else 0.4f
    }

    private fun updateScores(state: GameUiState) {
        binding.humanScore.text = getString(R.string.score_player, playerName(state.mode, HUMAN_PLAYER), state.xWins)
        binding.tieScore.text = getString(R.string.score_ties, state.ties)
        binding.computerScore.text = getString(R.string.score_player, playerName(state.mode, COMPUTER_PLAYER), state.oWins)
    }
}
