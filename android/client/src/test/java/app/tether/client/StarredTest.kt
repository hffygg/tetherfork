package app.tether.client

import app.tether.client.model.Starred
import app.tether.core.Sms
import org.junit.Assert.assertEquals
import org.junit.Test

class StarredTest {
    private fun sms(id: Long, thread: Long, date: Long, body: String = "t$id") = Sms(id, thread, "+$thread", body, date, 1, true, 1)
    private val all = listOf(sms(1, 7, 100), sms(2, 7, 300), sms(3, 7, 200), sms(4, 8, 250), sms(5, 9, 400))

    @Test fun chatsWithStarsNewestFirst() {
        val chats = Starred.chats(all, setOf(1, 2, 3, 4))
        assertEquals(listOf(7L, 8L), chats.map { it.thread })
        assertEquals(listOf(3, 1), chats.map { it.count })
        assertEquals(2L, chats[0].newest.id)
    }

    @Test fun oneChatsTextsNewestFirst() {
        assertEquals(listOf(2L, 3L, 1L), Starred.texts(all, setOf(1, 2, 3, 4), 7).map { it.id })
    }

    @Test fun searchFilters() {
        assertEquals(listOf(8L), Starred.chats(all, setOf(1, 4), { it.body == "t4" }).map { it.thread })
    }
}
