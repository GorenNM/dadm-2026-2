package co.edu.unal.tictactoeonline.online

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unal.tictactoeonline.BoardView
import co.edu.unal.tictactoeonline.GamePreferences
import co.edu.unal.tictactoeonline.R
import co.edu.unal.tictactoeonline.SoundEffects
import co.edu.unal.tictactoeonline.databinding.ActivityOnlineGameBinding
import kotlinx.coroutines.launch

/**
 * Partida en línea. Solo UI: dibuja el [OnlineUiState] y le pasa los toques al [OnlineGameViewModel].
 * Back, la flecha y "Salir" hacen lo mismo: avisan a la nube que este jugador se fue y vuelven a la lista.
 */
class OnlineGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnlineGameBinding

    private val mViewModel: OnlineGameViewModel by viewModels {
        OnlineGameViewModel.factory(FirebaseGameRepository(), GamePreferences(applicationContext))
    }

    private lateinit var mBoardView: BoardView
    private lateinit var mSoundEffects: SoundEffects

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityOnlineGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mSoundEffects = SoundEffects(applicationContext)

        mBoardView = binding.board
        mBoardView.setOnCellTouchedListener { pos -> mViewModel.onCellTouched(pos) }

        binding.rematchButton.setOnClickListener { mViewModel.rematch() }
        binding.leaveButton.setOnClickListener { confirmLeave() }
        onBackPressedDispatcher.addCallback(this) { confirmLeave() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { mViewModel.uiState.collect { render(it) } }
                launch {
                    mViewModel.moves.collect { move ->
                        mSoundEffects.play(move.player)
                        mBoardView.animateMove(move.location)
                    }
                }
                launch { mViewModel.events.collect { onEvent(it) } }
            }
        }
    }

    override fun onDestroy() {
        mSoundEffects.release()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        confirmLeave()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_online, menu)
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
            R.id.leave -> {
                confirmLeave()
                true
            }
            R.id.toggle_sound -> {
                mViewModel.toggleMuted()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** A mitad de partida se pregunta; esperando rival o con la partida terminada se sale directo. */
    private fun confirmLeave() {
        if (mViewModel.uiState.value.status != GameStatus.PLAYING) {
            mViewModel.leave()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.online_leave_title)
            .setMessage(R.string.online_leave_message)
            .setPositiveButton(R.string.online_leave) { _, _ -> mViewModel.leave() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onEvent(event: OnlineEvent) {
        when (event) {
            OnlineEvent.Left -> finish()
            OnlineEvent.Failed -> Toast.makeText(this, R.string.online_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun render(state: OnlineUiState) {
        if (state.gone) {
            // El rival borró el juego, o nunca existió: no hay nada que mostrar
            Toast.makeText(this, R.string.online_game_gone, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        mBoardView.setBoard(state.board)
        // Fuera de turno el tablero se ve apagado (el ViewModel igual ignora los toques)
        mBoardView.alpha = if (state.isMyTurn || state.status != GameStatus.PLAYING) 1f else 0.6f

        if (mSoundEffects.muted != state.muted) {
            mSoundEffects.muted = state.muted
            invalidateOptionsMenu()
        }

        supportActionBar?.title =
            if (state.xName.isBlank()) getString(R.string.app_name) else getString(R.string.lobby_game_title, state.xName)

        binding.progress.isVisible = state.loading || state.status == GameStatus.WAITING
        binding.information.text = statusMessage(state)
        binding.rivalOffline.isVisible = !state.rivalOnline &&
            (state.status == GameStatus.PLAYING || state.status == GameStatus.FINISHED)
        binding.rematchButton.isVisible = state.status == GameStatus.FINISHED
        // Durante la partida se sale desde la barra o con Back (con confirmación)
        binding.leaveButton.isVisible = !state.loading && state.status != GameStatus.PLAYING

        updateTurnIndicator(state)
        updateScores(state)
    }

    private fun statusMessage(state: OnlineUiState): String {
        val rival = rivalName(state)
        return when {
            state.loading -> getString(R.string.online_connecting)
            state.status == GameStatus.WAITING -> getString(R.string.online_waiting)
            state.status == GameStatus.ABANDONED -> getString(R.string.online_rival_left, rival)
            state.status == GameStatus.FINISHED -> when {
                state.winner == 1 -> getString(R.string.result_tie)
                winnerSymbol(state.winner) == state.mySymbol -> getString(R.string.online_you_won)
                else -> getString(R.string.online_rival_won, rival)
            }
            state.isMyTurn -> getString(R.string.online_your_turn)
            else -> getString(R.string.online_rival_turn, rival)
        }
    }

    private fun winnerSymbol(winner: Int) = if (winner == 2) PLAYER_X else PLAYER_O

    private fun rivalName(state: OnlineUiState) =
        playerName(state, OnlineRules.other(state.mySymbol), markYou = false)

    /** El apodo de [symbol]; el propio con "(tú)". O sin rival todavía: "Rival". */
    private fun playerName(state: OnlineUiState, symbol: Char, markYou: Boolean = true): String {
        val name = (if (symbol == PLAYER_X) state.xName else state.oName)
            .ifBlank { getString(R.string.online_rival_placeholder) }
        return if (markYou && symbol == state.mySymbol) getString(R.string.online_player_you, name) else name
    }

    private fun updateTurnIndicator(state: OnlineUiState) {
        binding.turnX.text = getString(R.string.player_label, PLAYER_X.toString(), playerName(state, PLAYER_X))
        binding.turnO.text = getString(R.string.player_label, PLAYER_O.toString(), playerName(state, PLAYER_O))
        val playing = state.status == GameStatus.PLAYING
        styleTurnLabel(binding.turnX, playing && state.turn == PLAYER_X)
        styleTurnLabel(binding.turnO, playing && state.turn == PLAYER_O)
    }

    private fun styleTurnLabel(label: TextView, active: Boolean) {
        label.setBackgroundResource(if (active) R.drawable.bg_turn_active else R.drawable.bg_turn_inactive)
        label.alpha = if (active) 1f else 0.4f
    }

    private fun updateScores(state: OnlineUiState) {
        binding.xScore.text = getString(R.string.score_player, playerName(state, PLAYER_X, markYou = false), state.xWins)
        binding.tieScore.text = getString(R.string.score_ties, state.ties)
        binding.oScore.text = getString(R.string.score_player, playerName(state, PLAYER_O, markYou = false), state.oWins)
    }
}
