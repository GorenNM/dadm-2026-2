package co.edu.unal.tictactoeonline.online

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
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
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.unal.tictactoeonline.AndroidTicTacToeActivity
import co.edu.unal.tictactoeonline.GamePreferences
import co.edu.unal.tictactoeonline.R
import co.edu.unal.tictactoeonline.databinding.ActivityLobbyBinding
import co.edu.unal.tictactoeonline.databinding.NicknameDialogBinding
import co.edu.unal.tictactoeonline.showAboutDialog
import co.edu.unal.tictactoeonline.showThemeDialog
import kotlinx.coroutines.launch

/**
 * Pantalla inicial (enunciado): la lista de juegos disponibles, crear uno o unirse.
 * "Jugar sin conexión" abre el triqui del reto 06 tal cual.
 */
class LobbyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLobbyBinding

    private val mViewModel: LobbyViewModel by viewModels {
        LobbyViewModel.factory(FirebaseGameRepository(), GamePreferences(applicationContext))
    }

    private val mAdapter = GamesAdapter { game -> mViewModel.joinGame(game.id) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        binding.games.layoutManager = LinearLayoutManager(this)
        binding.games.adapter = mAdapter

        binding.nicknameButton.setOnClickListener { showNicknameDialog(continuePending = false) }
        binding.createGameButton.setOnClickListener { mViewModel.createGame() }
        binding.retryButton.setOnClickListener { mViewModel.retry() }
        binding.offlineButton.setOnClickListener {
            startActivity(Intent(this, AndroidTicTacToeActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { mViewModel.uiState.collect { render(it) } }
                launch { mViewModel.events.collect { onEvent(it) } }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_lobby, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.change_nickname -> {
                showNicknameDialog(continuePending = false)
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

    private fun render(state: LobbyUiState) {
        mAdapter.submitList(state.games)

        binding.progress.isVisible = state.loading
        binding.errorGroup.isVisible = state.error
        binding.empty.isVisible = !state.loading && !state.error && state.games.isEmpty()
        binding.games.isVisible = !state.loading && !state.error

        // Sin sesión no se puede crear; mientras se crea/une, tampoco otra vez (el ViewModel igual lo ignora)
        binding.createGameButton.isEnabled = !state.loading && !state.error && !state.busy

        binding.nicknameButton.text = if (state.nickname.isBlank()) {
            getString(R.string.lobby_no_nickname)
        } else {
            getString(R.string.lobby_nickname, state.nickname)
        }
    }

    private fun onEvent(event: LobbyEvent) {
        when (event) {
            is LobbyEvent.OpenGame -> startActivity(
                Intent(this, OnlineGameActivity::class.java).putExtra(OnlineGameViewModel.KEY_GAME_ID, event.gameId)
            )
            LobbyEvent.AskNickname -> showNicknameDialog(continuePending = true)
            LobbyEvent.GameTaken -> Toast.makeText(this, R.string.lobby_game_taken, Toast.LENGTH_SHORT).show()
            LobbyEvent.Failed -> Toast.makeText(this, R.string.online_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * [continuePending]: se abrió porque el jugador quiso crear o unirse sin apodo; al aceptar
     * se sigue con eso, al cancelar se descarta.
     */
    private fun showNicknameDialog(continuePending: Boolean) {
        // Desde el menú no hay nada que continuar (p. ej. si un diálogo anterior se perdió al rotar)
        if (!continuePending) mViewModel.cancelPendingAction()
        val dialogBinding = NicknameDialogBinding.inflate(layoutInflater)
        dialogBinding.nicknameInput.setText(mViewModel.uiState.value.nickname)

        AlertDialog.Builder(this)
            .setTitle(R.string.nickname_title)
            .setMessage(R.string.nickname_message)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                mViewModel.setNickname(dialogBinding.nicknameInput.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            // Cancelar, Back o aceptar vacío: lo pendiente se descarta. Aceptar con apodo ya lo ejecutó setNickname
            .setOnDismissListener { if (continuePending && mViewModel.uiState.value.nickname.isBlank()) mViewModel.cancelPendingAction() }
            .show()
    }
}
