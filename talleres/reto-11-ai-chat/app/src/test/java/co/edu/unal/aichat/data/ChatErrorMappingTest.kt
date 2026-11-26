package co.edu.unal.aichat.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Del mapeo de las excepciones reales de firebase-ai (se instancian en la JVM, sin red) al [ChatError] propio.
 * Los constructores del SDK son `internal` en Kotlin pero públicos en el bytecode: se llaman por reflexión.
 */
class ChatErrorMappingTest {

    private fun sdk(name: String, message: String = "mensaje", cause: Throwable? = null): Throwable =
        Class.forName("com.google.firebase.ai.type.$name")
            .getConstructor(String::class.java, Throwable::class.java)
            .newInstance(message, cause) as Throwable

    @Test
    fun quota() {
        assertEquals(ChatError.QUOTA, sdk("QuotaExceededException").toChatError())
    }

    @Test
    fun configuration() {
        for (name in listOf("InvalidAPIKeyException", "APINotConfiguredException", "ServiceDisabledException", "PermissionMissingException")) {
            assertEquals(name, ChatError.CONFIGURATION, sdk(name).toChatError())
        }
    }

    @Test
    fun blocked() {
        assertEquals(ChatError.BLOCKED, sdk("ContentBlockedException").toChatError())
    }

    @Test
    fun location() {
        assertEquals(ChatError.LOCATION, sdk("InvalidLocationException", "CO").toChatError())
        val unsupported = Class.forName("com.google.firebase.ai.type.UnsupportedUserLocationException")
            .getConstructor(Throwable::class.java).newInstance(null) as Throwable
        assertEquals(ChatError.LOCATION, unsupported.toChatError())
    }

    @Test
    fun timeoutIsNetwork() {
        val timeout = Class.forName("com.google.firebase.ai.type.RequestTimeoutException")
            .getConstructor(String::class.java, Throwable::class.java, List::class.java)
            .newInstance("lento", null, emptyList<Any>()) as Throwable
        assertEquals(ChatError.NETWORK, timeout.toChatError())
    }

    @Test
    fun noConnection_isNetwork_evenWrappedInAGenericSdkException() {
        assertEquals(ChatError.NETWORK, sdk("UnknownException", cause = UnknownHostException("generativelanguage.googleapis.com")).toChatError())
        assertEquals(ChatError.NETWORK, sdk("ServerException", cause = SocketTimeoutException("timeout")).toChatError())
        assertEquals(ChatError.NETWORK, IOException("Unable to resolve host").toChatError())
    }

    @Test
    fun genericServerErrors_areClassifiedByTheirHttpStatus() {
        assertEquals(ChatError.QUOTA, sdk("ServerException", "HTTP 429 RESOURCE_EXHAUSTED").toChatError())
        assertEquals(ChatError.CONFIGURATION, sdk("ServerException", "HTTP 403 PERMISSION_DENIED").toChatError())
        assertEquals(
            ChatError.CONFIGURATION,
            sdk("ServerException", "Firebase AI Logic API has not been used in project 123 before or it is disabled").toChatError()
        )
        assertEquals(ChatError.CONFIGURATION, IllegalStateException("Default FirebaseApp is not initialized").toChatError())
    }

    @Test
    fun appCheckRejection_isItsOwnError_evenWhenItLooksLikeAGeneric403() {
        assertEquals(ChatError.APP_CHECK, sdk("ServerException", "Firebase App Check token is invalid.").toChatError())
        assertEquals(ChatError.APP_CHECK, sdk("ServerException", "HTTP 403 PERMISSION_DENIED: App Check token missing").toChatError())
        assertEquals(ChatError.APP_CHECK, sdk("UnknownException", "fallo", RuntimeException("AppCheck attestation failed")).toChatError())
    }

    @Test
    fun anythingElse_isUnknown() {
        assertEquals(ChatError.UNKNOWN, sdk("ServerException", "HTTP 500 INTERNAL").toChatError())
        assertEquals(ChatError.UNKNOWN, RuntimeException("algo raro").toChatError())
    }
}
