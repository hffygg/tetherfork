package app.tether.client.model

import app.tether.core.Sms

/** The Starred screen's data: chats that have starred texts, and one chat's starred texts. Pure, so it is unit-tested. */
object Starred {
    /** A chat with starred texts: how many, and the newest of them (its preview and date). */
    data class Chat(val thread: Long, val address: String, val count: Int, val newest: Sms)

    /** Chats with starred texts, the one with the newest star first; [match] filters the texts (search). */
    fun chats(sms: List<Sms>, starred: Set<Long>, match: (Sms) -> Boolean = { true }): List<Chat> =
        sms.filter { it.id in starred && match(it) }.groupBy { it.thread }
            .map { (thread, l) -> val n = l.maxWith(compareBy({ it.date }, { it.id })); Chat(thread, n.addr, l.size, n) }
            .sortedByDescending { it.newest.date }

    /** One chat's starred texts, newest first. */
    fun texts(sms: List<Sms>, starred: Set<Long>, thread: Long, match: (Sms) -> Boolean = { true }): List<Sms> =
        sms.filter { it.thread == thread && it.id in starred && match(it) }.sortedWith(compareByDescending<Sms> { it.date }.thenByDescending { it.id })
}
