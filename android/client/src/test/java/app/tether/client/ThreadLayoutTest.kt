package app.tether.client

import app.tether.client.model.ThreadLayout
import app.tether.client.model.ThreadLayout.Item
import app.tether.core.Sms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadLayoutTest {
    private val day = 86_400_000L
    private fun sms(id: Long, date: Long, box: Int = 1, read: Boolean = true, sub: Int? = 1) = Sms(id, 7, "+1", "t$id", date, box, read, sub)
    private fun dayOf(ms: Long) = ms / day * day

    @Test fun headingPerDay() {
        val items = ThreadLayout.build(listOf(sms(1, 1000), sms(2, 2000), sms(3, day + 5)), ::dayOf, null)
        assertEquals(listOf(Item.Day::class, Item.Msg::class, Item.Msg::class, Item.Day::class, Item.Msg::class), items.map { it::class })
    }

    @Test fun timeOnlyAtTheEndOfASameMinuteRun() {
        val items = ThreadLayout.build(listOf(sms(1, 1000), sms(2, 2000), sms(3, 70_000), sms(4, 71_000, box = 2)), ::dayOf, null)
            .filterIsInstance<Item.Msg>()
        assertEquals(listOf(false, true, true, true), items.map { it.showTime })
        assertEquals(listOf(true, false, false, true), items.map { it.startsRun })
    }

    @Test fun unreadLineAboveTheFirstUnread() {
        val msgs = listOf(sms(1, 1000), sms(2, 2000, read = false), sms(3, 3000, read = false))
        val from = ThreadLayout.firstUnread(msgs, readUpTo = 0)
        assertEquals(2L, from)
        val items = ThreadLayout.build(msgs, ::dayOf, from)
        assertEquals(Item.Unread, items[2])
        // The text before the line keeps its time: the line splits the run.
        assertEquals(true, (items[1] as Item.Msg).showTime)
    }

    @Test fun dayOfAnyRow() {
        val items = ThreadLayout.build(listOf(sms(1, 1000), sms(2, day + 5, read = false)), ::dayOf, 2)
        // Day, msg 1, Day, Unread, msg 2
        assertEquals(listOf(0L, 0L, day, day, day), items.indices.map { ThreadLayout.dayAt(items, it) })
        assertNull(ThreadLayout.dayAt(emptyList(), 0))
    }

    @Test fun noUnreadLineForOwnOrAlreadyReadTexts() {
        assertNull(ThreadLayout.firstUnread(listOf(sms(1, 1000, box = 2, read = false), sms(2, 2000, read = false)), readUpTo = 2))
    }
}
