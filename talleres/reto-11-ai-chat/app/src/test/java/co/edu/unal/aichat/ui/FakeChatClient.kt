package co.edu.unal.aichat.ui

import co.edu.unal.aichat.data.ChatClient
import co.edu.unal.aichat.data.ChatError
import co.edu.unal.aichat.data.ChatException
import co.edu.unal.aichat.data.ChatMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow

/**
 * Cliente falso programable. Cada llamada a [stream] guarda el historial que recibió en [calls] y
 * devuelve lo que diga [next]: por defecto, responde de un tirón; con [hold] el test controla cuándo
 * llega cada fragmento y cuándo termina (o falla).
 */
class FakeChatClient : ChatClient {

    val calls = mutableListOf<List<ChatMessage>>()

    /** Cómo responde la próxima llamada. */
    var next: () -> Flow<String> = { flowOf("ok") }

    override fun stream(history: List<ChatMessage>): Flow<String> {
        calls += history
        return next()
    }

    fun replyWith(vararg chunks: String) {
        next = { flowOf(*chunks) }
    }

    /** Emite [chunks] y después falla con [error]. */
    fun failWith(error: ChatError, vararg chunks: String) {
        next = {
            flow {
                chunks.forEach { emit(it) }
                throw ChatException(error)
            }
        }
    }

    /** Un canal para que el test mande los fragmentos a mano: `send` los emite, `close()` termina, `close(e)` falla. */
    fun hold(): Channel<String> {
        val channel = Channel<String>(Channel.UNLIMITED)
        next = { channel.consumeAsFlow() }
        return channel
    }

    private fun flowOf(vararg chunks: String): Flow<String> = flow { chunks.forEach { emit(it) } }
}
