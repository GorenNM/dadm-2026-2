package co.edu.unal.aichat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryCodecTest {

    private fun msg(id: Long, role: Role = Role.USER, text: String = "m$id", status: MessageStatus = MessageStatus.COMPLETE) =
        ChatMessage(id, role, text, status)

    @Test
    fun roundTrip_keepsEverything_includingNewlinesAccentsAndEmoji() {
        val messages = listOf(
            msg(1, Role.USER, "Hola\n¿cómo estás?\n\n- uno\n- dos"),
            msg(2, Role.MODEL, "Bien 😀 — ñandú", MessageStatus.STOPPED),
            msg(3, Role.MODEL, "", MessageStatus.STREAMING),
            msg(4, Role.MODEL, "corte", MessageStatus.INTERRUPTED)
        )
        assertEquals(messages, ChatHistoryCodec.decode(ChatHistoryCodec.encode(messages)))
    }

    @Test
    fun emptyList_roundTrips() {
        assertEquals("", ChatHistoryCodec.encode(emptyList()))
        assertTrue(ChatHistoryCodec.decode("").isEmpty())
        assertTrue(ChatHistoryCodec.decode(null).isEmpty())
    }

    @Test
    fun encode_keepsTheLastMessages() {
        val messages = (1L..10L).map { msg(it) }
        val decoded = ChatHistoryCodec.decode(ChatHistoryCodec.encode(messages, maxMessages = 4))
        assertEquals(listOf(7L, 8L, 9L, 10L), decoded.map { it.id })
    }

    @Test
    fun encode_dropsTheOldestUntilTheTextFitsTheCap() {
        val messages = (1L..5L).map { msg(it, text = "x".repeat(100)) }
        val decoded = ChatHistoryCodec.decode(ChatHistoryCodec.encode(messages, maxChars = 250))
        assertEquals(listOf(4L, 5L), decoded.map { it.id })
    }

    @Test
    fun encode_truncatesASingleMessageBiggerThanTheCap() {
        val decoded = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(msg(1, text = "y".repeat(500))), maxChars = 100))
        assertEquals(1, decoded.size)
        assertEquals(100, decoded.single().text.length)
    }

    @Test
    fun separatorsInTheText_cannotBreakTheFormat() {
        val tricky = "a\u001Fb\u001Ec"
        val decoded = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(msg(1, text = tricky), msg(2))))
        assertEquals(listOf(1L, 2L), decoded.map { it.id })
        assertEquals("a b c", decoded.first().text)
    }

    @Test
    fun decode_skipsWhatItCannotUnderstand() {
        val good = ChatHistoryCodec.encode(listOf(msg(7)))
        val broken = listOf("no es un registro", "x\u001Fu\u001Fc\u001Ftexto", "1\u001Fz\u001Fc\u001Ftexto", "1\u001Fu\u001Fz\u001Ftexto")
        val decoded = ChatHistoryCodec.decode((broken + good).joinToString("\u001E"))
        assertEquals(listOf(7L), decoded.map { it.id })
    }

    @Test
    fun forModel_dropsEmptyMessages_trimsToTheLast_andStartsWithTheUser() {
        val messages = (1L..60L).map { msg(it, if (it % 2 == 1L) Role.USER else Role.MODEL) } +
            msg(61, Role.MODEL, "", MessageStatus.STREAMING)
        val sent = messages.forModel()
        // El último mensaje con texto es el 60 (modelo); de los últimos 40 (21..60) el primero ya es del usuario
        assertEquals(40, sent.size)
        assertEquals(21L, sent.first().id)
        assertEquals(Role.USER, sent.first().role)
        assertEquals(60L, sent.last().id)
    }

    @Test
    fun forModel_dropsALeadingModelMessage() {
        val messages = listOf(msg(1, Role.MODEL), msg(2, Role.USER), msg(3, Role.MODEL))
        assertEquals(listOf(2L, 3L), messages.forModel().map { it.id })
    }
}
