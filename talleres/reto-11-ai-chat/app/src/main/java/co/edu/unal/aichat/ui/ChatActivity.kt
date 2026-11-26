package co.edu.unal.aichat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
import co.edu.unal.aichat.ChatApp
import co.edu.unal.aichat.R
import co.edu.unal.aichat.data.ChatMessage
import co.edu.unal.aichat.databinding.ActivityChatBinding
import co.edu.unal.aichat.showAboutDialog
import co.edu.unal.aichat.showThemeDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Única pantalla: la conversación con el modelo, el campo de texto y los prompts sugeridos. */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding

    private val mViewModel: ChatViewModel by viewModels {
        ChatViewModel.factory((application as ChatApp).chatClient)
    }

    private val mAdapter = MessagesAdapter(::copyToClipboard)

    private var mConfirmDialog: AlertDialog? = null
    private var mHasMessages = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ime: con el teclado abierto, el campo de texto sube y la conversación se achica
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        binding.messages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messages.adapter = mAdapter

        val suggestions = mapOf(
            binding.chipSuggestion1 to R.string.suggestion_1,
            binding.chipSuggestion2 to R.string.suggestion_2,
            binding.chipSuggestion3 to R.string.suggestion_3,
            binding.chipSuggestion4 to R.string.suggestion_4
        )
        for ((chip, prompt) in suggestions) {
            chip.setOnClickListener { mViewModel.send(getString(prompt)) }
        }

        binding.sendButton.setOnClickListener {
            if (mViewModel.uiState.value.generating) {
                mViewModel.stop()
            } else {
                val text = binding.input.text?.toString().orEmpty()
                if (text.isNotBlank()) {
                    mViewModel.send(text)
                    binding.input.text?.clear()
                }
            }
        }
        binding.retryButton.setOnClickListener { mViewModel.retry() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onDestroy() {
        // Sin esto, rotar con el diálogo abierto deja la ventana "filtrada"
        mConfirmDialog?.dismiss()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.new_conversation).isEnabled = mHasMessages
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.new_conversation -> {
                confirmNewConversation()
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

    private fun render(state: ChatUiState) {
        // ¿Estaba al final? Entonces se queda al final mientras la respuesta crece
        val atBottom = !binding.messages.canScrollVertically(1)
        val grew = state.messages.size > mAdapter.itemCount
        mAdapter.submitList(state.messages) {
            if (state.messages.isNotEmpty() && (grew || atBottom)) {
                binding.messages.scrollToPosition(state.messages.lastIndex)
            }
        }

        val empty = state.messages.isEmpty()
        binding.emptyGroup.isVisible = empty
        binding.suggestions.isVisible = !state.notConfigured
        binding.notConfiguredText.isVisible = state.notConfigured

        binding.errorBanner.isVisible = state.error != null
        state.error?.let { binding.errorText.setText(it.messageRes) }

        binding.input.isEnabled = !state.notConfigured
        binding.inputLayout.isEnabled = !state.notConfigured
        binding.sendButton.isEnabled = !state.notConfigured
        // Mientras el modelo responde, el botón corta la respuesta
        binding.sendButton.setIconResource(if (state.generating) R.drawable.ic_stop else R.drawable.ic_send)
        binding.sendButton.contentDescription = getString(if (state.generating) R.string.action_stop else R.string.action_send)

        if (mHasMessages == empty) {
            mHasMessages = !empty
            invalidateOptionsMenu()
        }
    }

    private fun confirmNewConversation() {
        if (!mHasMessages) return
        mConfirmDialog = AlertDialog.Builder(this)
            .setTitle(R.string.new_conversation_title)
            .setMessage(R.string.new_conversation_message)
            .setPositiveButton(R.string.new_conversation_confirm) { _, _ -> mViewModel.newConversation() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun copyToClipboard(message: ChatMessage) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), message.text))
        Snackbar.make(binding.main, R.string.message_copied, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.inputRow)
            .show()
    }
}
