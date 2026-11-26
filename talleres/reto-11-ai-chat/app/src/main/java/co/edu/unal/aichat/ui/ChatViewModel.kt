package co.edu.unal.aichat.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.unal.aichat.data.ChatClient
import co.edu.unal.aichat.data.ChatError
import co.edu.unal.aichat.data.ChatException
import co.edu.unal.aichat.data.ChatHistoryCodec
import co.edu.unal.aichat.data.ChatMessage
import co.edu.unal.aichat.data.MessageStatus
import co.edu.unal.aichat.data.Role
import co.edu.unal.aichat.data.forModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    /** El modelo está respondiendo: el botón es "Detener" y no se puede enviar. */
    val generating: Boolean = false,
    /** El último envío falló (o quedó cortado): se muestra el aviso con "Reintentar". */
    val error: ChatError? = null,
    /** No hay cliente: Firebase no está configurado. La pantalla lo explica y no deja escribir. */
    val notConfigured: Boolean = false
)

/**
 * La conversación. El historial vive acá (sobrevive a la rotación) y en el [SavedStateHandle]
 * (sobrevive a la muerte del proceso). Cada envío manda el historial completo al [ChatClient],
 * que no tiene estado. [client] es null si Firebase no está configurado.
 */
class ChatViewModel(
    private val client: ChatClient?,
    private val savedState: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_HISTORY = "history"

        fun factory(client: ChatClient?) = viewModelFactory {
            initializer { ChatViewModel(client, createSavedStateHandle()) }
        }
    }

    private var mJob: Job? = null
    private var mNextId: Long

    private val mState: MutableStateFlow<ChatUiState>
    val uiState: StateFlow<ChatUiState>

    init {
        val restored = ChatHistoryCodec.decode(savedState.get<String>(KEY_HISTORY)).mapNotNull(::settle)
        mNextId = (restored.maxOfOrNull { it.id } ?: 0L) + 1
        // Si el proceso murió a mitad de una respuesta (o antes de la primera palabra), no queda nada
        // "cargando": se ofrece reintentar
        val last = restored.lastOrNull()
        val cut = last != null && (last.role == Role.USER || last.status == MessageStatus.INTERRUPTED)
        mState = MutableStateFlow(
            ChatUiState(
                messages = restored,
                error = if (cut && client != null) ChatError.INTERRUPTED else null,
                notConfigured = client == null
            )
        )
        uiState = mState.asStateFlow()
    }

    /** Una respuesta que estaba llegando cuando murió el proceso: sin texto desaparece, con texto queda "interrumpida". */
    private fun settle(message: ChatMessage): ChatMessage? = when {
        message.status != MessageStatus.STREAMING -> message
        message.text.isEmpty() -> null
        else -> message.copy(status = MessageStatus.INTERRUPTED)
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || client == null || mState.value.generating) return
        val message = ChatMessage(nextId(), Role.USER, clean)
        update { it.copy(messages = it.messages + message, error = null) }
        generate(client)
    }

    /** Corta la respuesta que está llegando y deja lo recibido, marcado como detenido. */
    fun stop() {
        if (!mState.value.generating) return
        mJob?.cancel()
        mJob = null
        update { state ->
            state.copy(messages = state.messages.closeStreaming(MessageStatus.STOPPED), generating = false)
        }
    }

    /** Reenvía el último mensaje del usuario; lo que haya llegado de la respuesta fallida se descarta. */
    fun retry() {
        val state = mState.value
        if (client == null || state.generating || state.error == null) return
        val messages = state.messages.dropLastWhile { it.role == Role.MODEL }
        if (messages.lastOrNull()?.role != Role.USER) {
            update { it.copy(error = null) }
            return
        }
        update { it.copy(messages = messages, error = null) }
        generate(client)
    }

    fun newConversation() {
        mJob?.cancel()
        mJob = null
        update { ChatUiState(notConfigured = it.notConfigured) }
    }

    private fun generate(client: ChatClient) {
        // El historial se toma antes de agregar la burbuja vacía de la respuesta
        val history = mState.value.messages.forModel()
        val reply = ChatMessage(nextId(), Role.MODEL, "", MessageStatus.STREAMING)
        update { it.copy(messages = it.messages + reply, generating = true, error = null) }
        mJob = viewModelScope.launch {
            try {
                client.stream(history).collect { chunk -> appendToReply(reply.id, chunk) }
                finishReply(reply.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failReply((e as? ChatException)?.error ?: ChatError.UNKNOWN)
            }
        }
    }

    private fun appendToReply(id: Long, chunk: String) = update { state ->
        state.copy(messages = state.messages.map { if (it.id == id) it.copy(text = it.text + chunk) else it })
    }

    private fun finishReply(id: Long) {
        val reply = mState.value.messages.firstOrNull { it.id == id }
        if (reply == null || reply.text.isBlank()) {
            // Terminó sin decir nada: no queda una burbuja vacía
            update { it.copy(messages = it.messages.filterNot { m -> m.id == id }, generating = false, error = ChatError.UNKNOWN) }
        } else {
            update { it.copy(messages = it.messages.closeStreaming(MessageStatus.COMPLETE), generating = false) }
        }
    }

    private fun failReply(error: ChatError) = update { state ->
        state.copy(
            messages = state.messages.closeStreaming(MessageStatus.INTERRUPTED),
            generating = false,
            error = error
        )
    }

    /** La burbuja que está llegando pasa a [status]; si todavía no tenía texto, se quita. */
    private fun List<ChatMessage>.closeStreaming(status: MessageStatus): List<ChatMessage> =
        mapNotNull { m ->
            when {
                m.status != MessageStatus.STREAMING -> m
                m.text.isEmpty() -> null
                else -> m.copy(status = status)
            }
        }

    private fun nextId() = mNextId++

    /** Cambia el estado y guarda la conversación en el SavedStateHandle (es chica: ≤ 40 mensajes y un tope de texto). */
    private fun update(transform: (ChatUiState) -> ChatUiState) {
        mState.update(transform)
        savedState[KEY_HISTORY] = ChatHistoryCodec.encode(mState.value.messages)
    }
}
