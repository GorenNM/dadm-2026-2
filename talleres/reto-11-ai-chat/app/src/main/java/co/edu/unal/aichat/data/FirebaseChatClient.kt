package co.edu.unal.aichat.data

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.InvalidLocationException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.UnsupportedUserLocationException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/**
 * Gemini vía Firebase AI Logic (backend Gemini Developer API). Es la única clase que conoce `firebase-ai`.
 * Sin estado: no usa el `Chat` del SDK, cada llamada manda la conversación completa.
 * La API key de Gemini no está en la app: la guarda Firebase y la app solo lleva google-services.json.
 */
class FirebaseChatClient(private val modelName: String = ChatConfig.MODEL_NAME) : ChatClient {

    // Perezoso: no se toca Firebase hasta la primera pregunta
    private val model: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName,
            systemInstruction = Content(role = null, parts = listOf(TextPart(ChatConfig.SYSTEM_INSTRUCTION)))
        )
    }

    override fun stream(history: List<ChatMessage>): Flow<String> = flow {
        model.generateContentStream(history.map { it.toContent() }).collect { response ->
            response.text?.takeIf { it.isNotEmpty() }?.let { emit(it) }
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        // Una respuesta que se acabó por longitud no es un error: ya se mostró lo que había
        if (e is ResponseStoppedException && e.response.candidates.firstOrNull()?.finishReason == FinishReason.MAX_TOKENS) {
            return@catch
        }
        throw ChatException(e.toChatError(), e)
    }

    private fun ChatMessage.toContent() =
        Content(if (role == Role.USER) "user" else "model", listOf(TextPart(text)))
}

/** De las excepciones del SDK (y de red) al [ChatError] de la app. */
internal fun Throwable.toChatError(): ChatError = if (mentionsAppCheck()) ChatError.APP_CHECK else when (this) {
    is QuotaExceededException -> ChatError.QUOTA
    is InvalidAPIKeyException, is APINotConfiguredException, is ServiceDisabledException,
    is PermissionMissingException -> ChatError.CONFIGURATION
    is UnsupportedUserLocationException, is InvalidLocationException -> ChatError.LOCATION
    is ContentBlockedException, is PromptBlockedException, is ResponseStoppedException -> ChatError.BLOCKED
    is RequestTimeoutException -> ChatError.NETWORK
    else -> fromCausesAndMessage()
}

/**
 * firebase-ai adjunta el token de App Check sola (AppCheckHeaderProvider), pero no tiene una excepción propia
 * para el rechazo: llega como ServerException con el mensaje del servidor ("Firebase App Check token is invalid"),
 * así que se reconoce por el texto, antes que el 403 genérico.
 */
private fun Throwable.mentionsAppCheck(): Boolean =
    generateSequence(this) { it.cause }.take(8).any { e ->
        e.message.orEmpty().lowercase().let { "app check" in it || "appcheck" in it || "app_check" in it }
    }

/** Errores genéricos del SDK (ServerException, UnknownException...): se mira la causa (red) y el texto (HTTP 429/403). */
private fun Throwable.fromCausesAndMessage(): ChatError {
    val chain = generateSequence(this) { it.cause }.take(8).toList()
    if (chain.any { it is IOException }) return ChatError.NETWORK
    val text = chain.joinToString(" ") { it.message.orEmpty() }.lowercase()
    return when {
        "429" in text || "resource_exhausted" in text || "quota" in text -> ChatError.QUOTA
        "403" in text || "permission_denied" in text || "api key" in text ||
            "has not been used" in text || "not been enabled" in text || "firebaseapp" in text -> ChatError.CONFIGURATION
        "unable to resolve host" in text || "timeout" in text || "timed out" in text -> ChatError.NETWORK
        else -> ChatError.UNKNOWN
    }
}
