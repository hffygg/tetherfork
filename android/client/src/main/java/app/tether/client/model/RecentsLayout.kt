package app.tether.client.model

import app.tether.core.CallEntry

/**
 * Recents as the list shows them: grouped by day under a day heading ("Today", "Yesterday", the date), each day's
 * calls in one card, and calls in a row from the same number with the same direction merged into one line with a
 * count ("Kian (2)"), as phones' own call logs do. Pure: [dayOf] gives the start of a call's local day.
 */
object RecentsLayout {
    /** Where a line sits in its day's card (for its rounded corners and divider). */
    enum class Pos { ONLY, FIRST, MIDDLE, LAST }

    sealed class Item {
        /** The heading of a day: [day] is its start (from [dayOf]). */
        data class Day(val day: Long) : Item()
        /** One line: [calls] newest first, all the same number and kind; the line shows the newest. */
        data class Line(val calls: List<CallEntry>, val pos: Pos, val day: Long) : Item() { val newest get() = calls.first() }
    }

    /** [calls] newest first (as synced). [same]: whether two numbers are the same person's (formatting aside). */
    fun build(calls: List<CallEntry>, dayOf: (Long) -> Long, same: (String, String) -> Boolean = { a, b -> key(a) == key(b) }): List<Item> {
        val out = ArrayList<Item>()
        var day = Long.MIN_VALUE
        var lines = ArrayList<List<CallEntry>>()
        fun flush() {
            lines.forEachIndexed { i, l ->
                val pos = when { lines.size == 1 -> Pos.ONLY; i == 0 -> Pos.FIRST; i == lines.lastIndex -> Pos.LAST; else -> Pos.MIDDLE }
                out += Item.Line(l, pos, day)
            }
            lines = ArrayList()
        }
        for (c in calls) {
            val d = dayOf(c.date)
            if (d != day) { flush(); day = d; out += Item.Day(d) }
            val last = lines.lastOrNull()
            if (last != null && last.first().type == c.type && same(last.first().num, c.num)) lines[lines.lastIndex] = last + c
            else lines += listOf(c)
        }
        flush()
        return out
    }

    /** The day heading to show while position [pos] is at the top of the list (the date bubble). */
    fun dayAt(items: List<Item>, pos: Int): Long? = when (val i = items.getOrNull(pos)) { is Item.Day -> i.day; is Item.Line -> i.day; null -> null }

    private fun key(n: String) = n.filter(Char::isDigit).takeLast(9).ifEmpty { n }
}
