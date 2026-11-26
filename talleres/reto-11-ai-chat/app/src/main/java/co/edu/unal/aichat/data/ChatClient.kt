package co.edu.unal.aichat.data

import kotlinx.coroutines.flow.Flow

/** Habla con un modelo de IA generativa. Sin estado: cada llamada recibe la conversación completa. */
interface ChatClient {

    /**
     * Respuesta del modelo a [history] (termina con un mensaje del usuario), por fragmentos de texto
     * que se van agregando. Si algo falla, el flujo termina con una [ChatException].
     */
    fun stream(history: List<ChatMessage>): Flow<String>
}
