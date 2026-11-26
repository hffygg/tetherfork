package app.tether.client

import app.tether.client.model.RecentsLayout
import app.tether.client.model.RecentsLayout.Item
import app.tether.client.model.RecentsLayout.Pos
import app.tether.core.CallEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Recents grouped by day, with calls in a row from the same number merged (the Recents tab's layout). */
class RecentsLayoutTest {
    private val day = 86_400_000L
    private val d0 = 100 * day
    private fun c(id: Long, at: Long, num: String = "+10000000001", type: Int = 1) = CallEntry(id, num, null, at, 30, type, null)
    private fun layout(vararg calls: CallEntry) = RecentsLayout.build(calls.toList(), { it / day * day })

    @Test fun callsAreGroupedByDayUnderAHeading() {
        val items = layout(c(1, d0 + 5000, "+10000000001"), c(2, d0 + 4000, "+10000000002"), c(3, d0 - day + 9000, "+10000000003"))
        assertEquals(listOf(Item.Day(d0), Item.Line(listOf(c(1, d0 + 5000, "+10000000001")), Pos.FIRST, d0),
            Item.Line(listOf(c(2, d0 + 4000, "+10000000002")), Pos.LAST, d0),
            Item.Day(d0 - day), Item.Line(listOf(c(3, d0 - day + 9000, "+10000000003")), Pos.ONLY, d0 - day)), items)
    }

    @Test fun callsInARowFromTheSameNumberAndKindAreOneLine() {
        val items = layout(c(1, d0 + 3000, "+98 912 000 0001", 3), c(2, d0 + 2000, "09120000001", 3), c(3, d0 + 1000, "+989120000001", 1))
        val lines = items.filterIsInstance<Item.Line>()
        assertEquals("two missed calls merged (formatting aside), the answered one apart", listOf(2, 1), lines.map { it.calls.size })
        assertEquals(1L, lines[0].newest.id)
        assertEquals(listOf(Pos.FIRST, Pos.LAST), lines.map { it.pos })
    }

    @Test fun theSameNumberOnAnotherDayOrAfterAnotherCallIsNotMerged() {
        val lines = layout(c(1, d0 + 3000), c(2, d0 + 2000, "+10000000002"), c(3, d0 + 1000), c(4, d0 - day + 1000))
            .filterIsInstance<Item.Line>()
        assertEquals(listOf(1, 1, 1, 1), lines.map { it.calls.size })
        assertEquals(listOf(Pos.FIRST, Pos.MIDDLE, Pos.LAST, Pos.ONLY), lines.map { it.pos })
    }

    @Test fun theDateBubbleNamesTheDayAtThePosition() {
        val items = layout(c(1, d0 + 1), c(2, d0 - day + 1))
        assertEquals(d0, RecentsLayout.dayAt(items, 0)); assertEquals(d0, RecentsLayout.dayAt(items, 1))
        assertEquals(d0 - day, RecentsLayout.dayAt(items, 3)); assertNull(RecentsLayout.dayAt(items, 9))
        assertEquals(emptyList<Item>(), layout())
    }
}
