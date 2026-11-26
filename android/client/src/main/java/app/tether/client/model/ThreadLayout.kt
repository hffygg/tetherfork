package app.tether.client.model

import app.tether.core.Sms

/**
 * A conversation laid out as phones' own messaging apps show it: a heading per day, a line above the first text that
 * was unread when the conversation was opened, and the time beside a bubble only where it says something new (not on
 * a text followed by another one from the same side in the same minute). Pure, so it is unit-tested.
 */
object ThreadLayout {
    sealed class Item {
        data class Day(val day: Long) : Item()
        object Unread : Item()
        /** [showTime]: the time (and SIM, state) beside the bubble; [startsRun]: first of a run from one side (more space above). */
        data class Msg(val sms: Sms, val showTime: Boolean, val startsRun: Boolean) : Item()
    }

    /** Sent by this side (anything but the inbox). */
    fun mine(m: Sms) = m.box != 1

    fun build(msgs: List<Sms>, dayOf: (Long) -> Long, unreadFrom: Long?): List<Item> {
        val out = ArrayList<Item>()
        var day: Long? = null
        var prev: Sms? = null
        for ((i, m) in msgs.withIndex()) {
            val d = dayOf(m.date)
            var broke = false
            if (d != day) { out += Item.Day(d); day = d; broke = true }
            if (m.id == unreadFrom) { out += Item.Unread; broke = true }
            val next = msgs.getOrNull(i + 1)
            val joinsNext = next != null && next.id != unreadFrom && dayOf(next.date) == d && mine(next) == mine(m) &&
                next.date / 60_000 == m.date / 60_000 && next.sub == m.sub && next.box == m.box
            out += Item.Msg(m, showTime = !joinsNext, startsRun = broke || prev == null || mine(prev) != mine(m))
            prev = m
        }
        return out
    }

    /** The day the item at [pos] belongs to (the pinned day while scrolling), or null. */
    fun dayAt(items: List<Item>, pos: Int): Long? {
        for (i in minOf(pos, items.lastIndex) downTo 0) (items[i] as? Item.Day)?.let { return it.day }
        return null
    }

    /** The first text still unread (incoming, not read, newer than what this side marked read), or null. */
    fun firstUnread(msgs: List<Sms>, readUpTo: Long): Long? = msgs.firstOrNull { !mine(it) && !it.read && it.id > readUpTo }?.id
}
