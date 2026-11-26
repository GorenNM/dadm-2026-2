package co.edu.unal.aichat.ui

import androidx.lifecycle.SavedStateHandle
import co.edu.unal.aichat.data.ChatConfig
import co.edu.unal.aichat.data.ChatError
import co.edu.unal.aichat.data.ChatHistoryCodec
import co.edu.unal.aichat.data.MessageStatus
import co.edu.unal.aichat.data.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val client = FakeChatClient()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        chatClient: FakeChatClient? = client
    ) = ChatViewModel(chatClient, savedState)

    private fun ChatViewModel.texts() = uiState.value.messages.map { it.role to it.text }

    /** El sistema restaura el SavedStateHandle en un ViewModel nuevo (muerte del proceso). */
    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any>(it) })

    @Test
    fun send_addsUserMessage_andStreamsTheReplyInChunks() = runTest {
        val channel = client.hold()
        val vm = viewModel()

        vm.send("Hola")
        runCurrent()
        // Antes de la primera palabra: burbuja del modelo vacía, "cargando"
        assertTrue(vm.uiState.value.generating)
        assertEquals(listOf(Role.USER to "Hola", Role.MODEL to ""), vm.texts())
        assertEquals(MessageStatus.STREAMING, vm.uiState.value.messages.last().status)

        channel.send("Hola, ")
        runCurrent()
        assertEquals("Hola, ", vm.uiState.value.messages.last().text)

        channel.send("¿cómo estás?")
        runCurrent()
        assertEquals("Hola, ¿cómo estás?", vm.uiState.value.messages.last().text)
        assertTrue(vm.uiState.value.generating)

        channel.close()
        runCurrent()
        assertFalse(vm.uiState.value.generating)
        assertEquals(MessageStatus.COMPLETE, vm.uiState.value.messages.last().status)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun secondSend_deliversTheWholeHistoryInOrder() = runTest {
        val vm = viewModel()
        client.replyWith("Uno")
        vm.send("Pregunta 1")
        advanceUntilIdle()

        client.replyWith("Dos")
        vm.send("Pregunta 2")
        advanceUntilIdle()

        assertEquals(2, client.calls.size)
        assertEquals(listOf("Pregunta 1"), client.calls[0].map { it.text })
        assertEquals(
            listOf(Role.USER to "Pregunta 1", Role.MODEL to "Uno", Role.USER to "Pregunta 2"),
            client.calls[1].map { it.role to it.text }
        )
        // La burbuja vacía de la respuesta que se está pidiendo no viaja
        assertTrue(client.calls[1].none { it.text.isEmpty() })
    }

    @Test
    fun blankInput_isIgnored_andTextIsTrimmed() = runTest {
        val vm = viewModel()
        vm.send("")
        vm.send("   \n  ")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertTrue(client.calls.isEmpty())

        vm.send("  hola  ")
        advanceUntilIdle()
        assertEquals("hola", vm.uiState.value.messages.first().text)
    }

    @Test
    fun cannotSend_whileTheModelIsAnswering() = runTest {
        val channel = client.hold()
        val vm = viewModel()
        vm.send("Primera")
        runCurrent()

        vm.send("Segunda")
        runCurrent()
        assertEquals(1, client.calls.size)
        assertEquals(listOf(Role.USER to "Primera", Role.MODEL to ""), vm.texts())

        channel.close()
        runCurrent()
        client.replyWith("ok")
        vm.send("Segunda")
        advanceUntilIdle()
        assertEquals(2, client.calls.size)
    }

    @Test
    fun stop_cancelsTheRequest_andKeepsWhatArrived() = runTest {
        val channel = client.hold()
        val vm = viewModel()
        vm.send("Cuéntame algo largo")
        runCurrent()
        channel.send("Había una vez")
        runCurrent()

        vm.stop()
        runCurrent()
        assertFalse(vm.uiState.value.generating)
        assertNull(vm.uiState.value.error)
        val reply = vm.uiState.value.messages.last()
        assertEquals("Había una vez", reply.text)
        assertEquals(MessageStatus.STOPPED, reply.status)

        // Lo que llegue después ya no se agrega: la petición se canceló
        // El canal ya se canceló con la petición: lo que se mande ahora no llega a nadie
        channel.trySend(" y vivieron felices")
        runCurrent()
        assertEquals("Había una vez", vm.uiState.value.messages.last().text)
    }

    @Test
    fun stop_beforeAnyText_removesTheEmptyBubble() = runTest {
        client.hold()
        val vm = viewModel()
        vm.send("Hola")
        runCurrent()

        vm.stop()
        runCurrent()
        assertEquals(listOf(Role.USER to "Hola"), vm.texts())
        assertFalse(vm.uiState.value.generating)
    }

    @Test
    fun afterStopping_theUserCanSendAgain_andThePartialReplyIsPartOfTheHistory() = runTest {
        val channel = client.hold()
        val vm = viewModel()
        vm.send("Uno")
        runCurrent()
        channel.send("Parcial")
        runCurrent()
        vm.stop()

        client.replyWith("Final")
        vm.send("Dos")
        advanceUntilIdle()
        assertEquals(
            listOf(Role.USER to "Uno", Role.MODEL to "Parcial", Role.USER to "Dos"),
            client.calls.last().map { it.role to it.text }
        )
    }

    @Test
    fun everyChatError_producesItsState_andLeavesTheUserMessageAndPartialText() = runTest {
        for (error in ChatError.entries) {
            val vm = viewModel()
            client.failWith(error, "Algo de ")
            vm.send("Hola")
            advanceUntilIdle()

            assertEquals(error, vm.uiState.value.error)
            assertFalse(vm.uiState.value.generating)
            val last = vm.uiState.value.messages.last()
            assertEquals("Algo de ", last.text)
            assertEquals(MessageStatus.INTERRUPTED, last.status)
        }
    }

    @Test
    fun appCheckRejection_showsItsOwnError_andRetryWorksOnceTheTokenIsRegistered() = runTest {
        val vm = viewModel()
        client.failWith(ChatError.APP_CHECK)
        vm.send("Hola")
        advanceUntilIdle()
        assertEquals(ChatError.APP_CHECK, vm.uiState.value.error)
        assertEquals(listOf(Role.USER to "Hola"), vm.texts())

        // El usuario registra el token en la consola y reintenta
        client.replyWith("Ya funciona")
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertEquals(listOf(Role.USER to "Hola", Role.MODEL to "Ya funciona"), vm.texts())
    }

    @Test
    fun errorBeforeAnyText_leavesNoEmptyBubble() = runTest {
        val vm = viewModel()
        client.failWith(ChatError.NETWORK)
        vm.send("Hola")
        advanceUntilIdle()
        assertEquals(ChatError.NETWORK, vm.uiState.value.error)
        assertEquals(listOf(Role.USER to "Hola"), vm.texts())
    }

    @Test
    fun anUnexpectedExceptionBecomesUnknown() = runTest {
        val vm = viewModel()
        client.next = { kotlinx.coroutines.flow.flow { throw IllegalStateException("boom") } }
        vm.send("Hola")
        advanceUntilIdle()
        assertEquals(ChatError.UNKNOWN, vm.uiState.value.error)
        assertFalse(vm.uiState.value.generating)
    }

    @Test
    fun emptyReply_isAnErrorAndLeavesNoEmptyBubble() = runTest {
        val vm = viewModel()
        client.replyWith()
        vm.send("Hola")
        advanceUntilIdle()
        assertEquals(ChatError.UNKNOWN, vm.uiState.value.error)
        assertEquals(listOf(Role.USER to "Hola"), vm.texts())
    }

    @Test
    fun retry_resendsTheLastMessage_withoutDuplicatingIt_andDropsThePartialReply() = runTest {
        val vm = viewModel()
        client.failWith(ChatError.NETWORK, "Medio")
        vm.send("Hola")
        advanceUntilIdle()

        client.replyWith("Ahora sí")
        vm.retry()
        advanceUntilIdle()

        assertEquals(2, client.calls.size)
        assertEquals(listOf("Hola"), client.calls[1].map { it.text })
        assertEquals(listOf(Role.USER to "Hola", Role.MODEL to "Ahora sí"), vm.texts())
        assertNull(vm.uiState.value.error)
        assertEquals(MessageStatus.COMPLETE, vm.uiState.value.messages.last().status)
    }

    @Test
    fun retry_afterAnEarlierSuccessfulTurn_resendsTheWholeHistory() = runTest {
        val vm = viewModel()
        client.replyWith("A1")
        vm.send("Q1")
        advanceUntilIdle()
        client.failWith(ChatError.QUOTA)
        vm.send("Q2")
        advanceUntilIdle()

        client.replyWith("A2")
        vm.retry()
        advanceUntilIdle()
        assertEquals(
            listOf(Role.USER to "Q1", Role.MODEL to "A1", Role.USER to "Q2"),
            client.calls.last().map { it.role to it.text }
        )
        assertEquals(listOf("Q1", "A1", "Q2", "A2"), vm.uiState.value.messages.map { it.text })
    }

    @Test
    fun retry_withoutAnError_doesNothing() = runTest {
        val vm = viewModel()
        client.replyWith("ok")
        vm.send("Hola")
        advanceUntilIdle()
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, client.calls.size)
    }

    @Test
    fun sendingAgain_clearsTheError() = runTest {
        val vm = viewModel()
        client.failWith(ChatError.BLOCKED)
        vm.send("Hola")
        advanceUntilIdle()
        assertEquals(ChatError.BLOCKED, vm.uiState.value.error)

        client.replyWith("ok")
        vm.send("Otra cosa")
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun newConversation_clearsEverything_andCancelsTheAnswerInProgress() = runTest {
        val channel = client.hold()
        val vm = viewModel()
        vm.send("Hola")
        runCurrent()
        channel.send("Parcial")
        runCurrent()

        vm.newConversation()
        runCurrent()
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertFalse(vm.uiState.value.generating)
        assertNull(vm.uiState.value.error)

        // La respuesta cancelada no reaparece
        channel.trySend("Más")
        runCurrent()
        assertTrue(vm.uiState.value.messages.isEmpty())

        // Y la conversación nueva arranca sin el historial viejo
        client.replyWith("ok")
        vm.send("Nueva")
        advanceUntilIdle()
        assertEquals(listOf("Nueva"), client.calls.last().map { it.text })
    }

    @Test
    fun newConversation_clearsTheSavedHistoryToo() = runTest {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState)
        client.replyWith("ok")
        vm.send("Hola")
        advanceUntilIdle()
        vm.newConversation()

        val restored = viewModel(savedState.afterProcessDeath())
        assertTrue(restored.uiState.value.messages.isEmpty())
    }

    @Test
    fun conversationSurvivesProcessDeath() = runTest {
        val savedState = SavedStateHandle()
        val before = viewModel(savedState)
        client.replyWith("Uno")
        before.send("Pregunta 1")
        advanceUntilIdle()

        val after = viewModel(savedState.afterProcessDeath())
        assertEquals(before.uiState.value.messages, after.uiState.value.messages)
        assertNull(after.uiState.value.error)
        assertFalse(after.uiState.value.generating)

        // Y la conversación sigue: los ids no se repiten y el historial viaja completo
        client.replyWith("Dos")
        after.send("Pregunta 2")
        advanceUntilIdle()
        assertEquals(listOf("Pregunta 1", "Uno", "Pregunta 2"), client.calls.last().map { it.text })
        val ids = after.uiState.value.messages.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun replyCutMidwayByProcessDeath_isNotLeftLoading_andOffersRetry() = runTest {
        val savedState = SavedStateHandle()
        val channel = client.hold()
        val before = viewModel(savedState)
        before.send("Hola")
        runCurrent()
        channel.send("Respuesta a medi")
        runCurrent()
        assertEquals(MessageStatus.STREAMING, before.uiState.value.messages.last().status)

        val after = viewModel(savedState.afterProcessDeath())
        val last = after.uiState.value.messages.last()
        assertEquals("Respuesta a medi", last.text)
        assertEquals(MessageStatus.INTERRUPTED, last.status)
        assertFalse(after.uiState.value.generating)
        assertEquals(ChatError.INTERRUPTED, after.uiState.value.error)

        client.replyWith("Completa")
        after.retry()
        advanceUntilIdle()
        assertEquals(listOf("Hola", "Completa"), after.uiState.value.messages.map { it.text })
    }

    @Test
    fun processDeathBeforeTheFirstWord_dropsTheEmptyBubble_andOffersRetry() = runTest {
        val savedState = SavedStateHandle()
        client.hold()
        val before = viewModel(savedState)
        before.send("Hola")
        runCurrent()

        val after = viewModel(savedState.afterProcessDeath())
        assertEquals(listOf(Role.USER to "Hola"), after.texts())
        assertEquals(ChatError.INTERRUPTED, after.uiState.value.error)
    }

    @Test
    fun restoringGarbage_givesAnEmptyConversation() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf("history" to "esto no es un historial")))
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun historySentToTheModel_isTrimmedToTheLastMessages_startingWithTheUser() = runTest {
        val vm = viewModel()
        client.replyWith("r")
        // 25 turnos = 50 mensajes
        repeat(25) {
            vm.send("pregunta $it")
            advanceUntilIdle()
        }
        val sent = client.calls.last()
        assertTrue(sent.size <= ChatConfig.MAX_HISTORY_MESSAGES)
        assertEquals(Role.USER, sent.first().role)
        assertEquals("pregunta 24", sent.last().text)
        // Lo que se ve en pantalla no se recorta
        assertEquals(50, vm.uiState.value.messages.size)
    }

    @Test
    fun savedHistory_isTrimmed() = runTest {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState)
        client.replyWith("r")
        repeat(25) {
            vm.send("pregunta $it")
            advanceUntilIdle()
        }
        val saved = ChatHistoryCodec.decode(savedState.get<String>("history"))
        assertEquals(ChatConfig.MAX_HISTORY_MESSAGES, saved.size)
        assertEquals(vm.uiState.value.messages.takeLast(ChatConfig.MAX_HISTORY_MESSAGES), saved)
    }

    @Test
    fun withoutAClient_itIsNotConfigured_andNothingCanBeSent() = runTest {
        val vm = viewModel(chatClient = null)
        assertTrue(vm.uiState.value.notConfigured)

        vm.send("Hola")
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.generating)

        // Nueva conversación no lo cambia
        vm.newConversation()
        assertTrue(vm.uiState.value.notConfigured)
    }

    @Test
    fun withAClient_itIsConfigured() = runTest {
        assertFalse(viewModel().uiState.value.notConfigured)
    }
}
