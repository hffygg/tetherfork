package app.tether.client.ui

import app.tether.client.model.NumberShape
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import app.tether.client.R
import app.tether.client.model.ThreadLayout
import app.tether.core.Sms

/**
 * One conversation: bubbles under a heading per day, the time beside them, a line above the texts that were unread
 * when it was opened (it opens there); send from either SIM, copy / delete, call, mute, block.
 */
class ThreadActivity : Activity() {
    private var thread = Long.MIN_VALUE
    private lateinit var address: String
    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var title: android.widget.TextView
    private lateinit var simButton: android.widget.TextView
    private lateinit var sendButton: View
    private var sub: Int? = null
    private var lastCount = -1
    /** The first text that was unread when the conversation opened (the "Unread messages" line goes above it). */
    private var unreadFrom: Long? = null
    private var unreadChecked = false
    /** The SIM beside the time: only when this conversation's texts went over more than one SIM. */
    private var showSim = false
    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        address = intent.getStringExtra(EXTRA_ADDRESS) ?: run { finish(); return }
        thread = intent.getLongExtra(EXTRA_THREAD, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: app.state.threadFor(address) ?: Long.MIN_VALUE
        sub = app.state.preferredSim(address)
        // A text sent into a new conversation gets its real thread id with the provider row: follow it.
        app.state.onThreadMoved = { from, to -> if (from == thread) thread = to }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.background)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(8), dp(8)) }
        title = label(app.displayName(address), size = 19f, bold = true).apply { setSingleLine() }
        header.addView(title.weight())
        header.addView(iconButton(R.drawable.ic_call, getString(R.string.call), R.color.accent) { app.dial(address, sub) })
        header.addView(iconButton(R.drawable.ic_more_vert, getString(R.string.more_options)) { menu() })
        root.addView(header)
        list = ListView(this).apply { divider = null; transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL; isStackFromBottom = true }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(8)) }
        // The SIM this text goes out on (dual-SIM phones only): tap to choose.
        simButton = button("", Look.TONAL) { pickSim() }.apply {
            textSize = 13f; setPadding(dp(14), dp(4), dp(14), dp(4))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
        }
        bar.addView(simButton)
        input = EditText(this).apply {
            hint = getString(R.string.text_message); setTextColor(col(R.color.text)); setHintTextColor(col(R.color.muted)); maxLines = 5
            setText(app.state.draft(address))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) { sendButton.isEnabled = !e.isNullOrBlank() }
            })
        }
        bar.addView(input.weight())
        sendButton = fab(R.drawable.ic_send, getString(R.string.send)) { send() }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8)
            isEnabled = input.text.isNotBlank()
        }
        bar.addView(sendButton)
        root.addView(bar)
        setContentView(root)
        fitSystemBars(root)
    }

    override fun onResume() {
        super.onResume()
        app.addListener(listener)
        app.appVisible = true
        render()
    }

    override fun onPause() {
        app.state.setDraft(input.text.toString(), address)
        app.removeListener(listener)
        app.appVisible = false
        if (app.state.openThread == thread) app.state.openThread = null
        super.onPause()
    }

    override fun onDestroy() { if (app.state.onThreadMoved != null) app.state.onThreadMoved = null; super.onDestroy() }

    private fun render() {
        val s = app.state
        s.openThread = thread
        title.text = app.displayName(address)
        val sims = s.sims
        simButton.visibility = if (sims.size > 1) View.VISIBLE else View.GONE
        simButton.text = sims.firstOrNull { it.sub == sub }?.let { it.name.ifEmpty { getString(R.string.sim_n, it.slot + 1) } } ?: getString(R.string.sim)
        val msgs = if (thread == Long.MIN_VALUE) emptyList() else s.messages(thread)
        // Read before marking it read: once, when the texts are first there.
        val first = !unreadChecked && msgs.isNotEmpty()
        if (first) { unreadFrom = ThreadLayout.firstUnread(msgs, s.readUpTo[thread] ?: 0); unreadChecked = true }
        s.markRead(thread)
        showSim = s.sims.size > 1 && msgs.mapTo(HashSet()) { it.sub }.size > 1
        val items = ThreadLayout.build(msgs, ::dayStart, unreadFrom)
        val atEnd = list.lastVisiblePosition >= list.count - 2
        list.adapter = Rows(items) { item ->
            when (item) {
                is ThreadLayout.Item.Day -> label(dayName(item.day), R.color.muted, 13f).apply {
                    gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(dp(16), dp(20), dp(16), dp(8))
                }
                ThreadLayout.Item.Unread -> unreadLine()
                is ThreadLayout.Item.Msg -> bubble(item)
            }
        }
        list.setOnItemLongClickListener { _, _, pos, _ -> (items[pos] as? ThreadLayout.Item.Msg)?.let { bubbleMenu(it.sms) }; true }
        val unreadAt = items.indexOf(ThreadLayout.Item.Unread)
        // Opened with unread texts: at the line (the older ones above it); else, and as new texts come, at the end.
        if (first && unreadAt >= 0) list.setSelectionFromTop(unreadAt, dp(8))
        else if (atEnd || msgs.size != lastCount) list.setSelection(items.size - 1)
        lastCount = msgs.size
    }

    /** The line above the texts that were unread when the conversation opened. */
    private fun unreadLine(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(6))
        fun rule() = View(context).apply { setBackgroundColor(col(R.color.muted)); alpha = 0.4f }
        addView(rule(), LinearLayout.LayoutParams(0, dp(1), 1f))
        addView(label(getString(R.string.unread_messages), R.color.accent, 13f, bold = true).apply {
            setPadding(dp(12), 0, dp(12), 0)
            setCompoundDrawablesRelative(icon(R.drawable.ic_chat, R.color.accent, 16), null, null, null); compoundDrawablePadding = dp(6)
        })
        addView(rule(), LinearLayout.LayoutParams(0, dp(1), 1f))
    }

    /**
     * A text's bubble, and beside it (towards the middle, at its bottom) the time, the SIM on a dual-SIM phone and
     * "Sending" / "Not delivered"; a run of texts in one minute shows the time once, on the last.
     */
    private fun bubble(item: ThreadLayout.Item.Msg): View {
        val m = item.sms
        val mine = ThreadLayout.mine(m)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = (if (mine) Gravity.END else Gravity.START) or Gravity.BOTTOM
            setPadding(dp(12), dp(if (item.startsRun) 8 else 2), dp(12), dp(2))
        }
        val r = dp(18).toFloat(); val s = dp(6).toFloat()
        // The corner on the sender's side is tighter where the next bubble of the run follows (as phones draw runs).
        val tail = if (item.showTime) r else s
        val radii = if (mine) floatArrayOf(r, r, r, r, tail, tail, r, r) else floatArrayOf(r, r, r, r, r, r, tail, tail)
        val body = label(m.body, if (mine) R.color.background else R.color.text, 16f).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply { cornerRadii = radii; setColor(col(if (mine) R.color.accent else R.color.surface)) }
            // Links, e-mail addresses and phone numbers, except "numbers" that are dates, versions or amounts.
            text = android.text.SpannableString(m.body).also { sp ->
                Linkify.addLinks(sp, Linkify.WEB_URLS or Linkify.PHONE_NUMBERS or Linkify.EMAIL_ADDRESSES)
                for (link in sp.getSpans(0, sp.length, android.text.style.URLSpan::class.java)) {
                    if (link.url.startsWith("tel:") && NumberShape.isNotAPhoneNumber(sp.substring(sp.getSpanStart(link), sp.getSpanEnd(link)))) sp.removeSpan(link)
                }
            }
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            maxWidth = (resources.displayMetrics.widthPixels * 0.72).toInt()
        }
        val state = when (m.box) { 4, 6 -> getString(R.string.sending); 5 -> getString(R.string.not_delivered); else -> null }
        val side = if (!item.showTime && state == null) null else LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = if (mine) Gravity.END else Gravity.START
            setPaddingRelative(dp(if (mine) 0 else 8), 0, dp(if (mine) 8 else 0), dp(2))   // relative: the gap faces the bubble in Persian too
            if (showSim) app.state.simName(m.sub)?.let { addView(label(it, R.color.muted, 10f).apply { setPadding(0, 0, 0, 0); maxLines = 1 }) }
            addView(label(android.text.format.DateUtils.formatDateTime(context, m.date, android.text.format.DateUtils.FORMAT_SHOW_TIME), R.color.muted, 11f).apply {
                setPadding(0, 0, 0, 0); textDirection = View.TEXT_DIRECTION_LTR })
            state?.let { addView(label(it, if (m.box == 5) R.color.bad else R.color.muted, 11f).apply { setPadding(0, 0, 0, 0) }) }
        }
        if (mine) side?.let { wrap.addView(it) }
        wrap.addView(body, LinearLayout.LayoutParams(-2, -2))
        if (!mine) side?.let { wrap.addView(it) }
        return wrap
    }

    private fun bubbleMenu(m: Sms) {
        val opts = mutableListOf(getString(R.string.copy), getString(R.string.delete))
        if (m.box == 5) opts += getString(R.string.send_again)
        AlertDialog.Builder(this).setItems(opts.toTypedArray()) { _, w ->
            when (w) {
                0 -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("text", m.body))
                1 -> app.state.deleteMessages(listOf(m.id))
                2 -> { app.state.deleteMessages(listOf(m.id)); app.state.sendSms(address, m.body, sub) }
            }
        }.show()
    }

    private fun pickSim() {
        val sims = app.state.sims
        AlertDialog.Builder(this).setItems(sims.map { it.name.ifEmpty { getString(R.string.sim_n, it.slot + 1) } }.toTypedArray()) { _, w ->
            sub = sims[w].sub; app.state.setPreferredSim(sub, address); render()
        }.show()
    }

    private fun send() {
        val text = input.text.toString()
        if (text.isBlank()) return
        app.state.sendSms(address, text, sub)
        if (thread == Long.MIN_VALUE) thread = app.state.threadFor(address) ?: thread
        input.setText("")
        render()
    }

    private fun menu() {
        val s = app.state
        val muted = thread != Long.MIN_VALUE && s.isMuted(thread); val blocked = s.isBlocked(address)
        val opts = listOf(getString(if (muted) R.string.unmute else R.string.mute), getString(if (blocked) R.string.unblock else R.string.block),
            getString(R.string.delete_conversation))
        AlertDialog.Builder(this).setItems(opts.toTypedArray()) { _, w ->
            when (w) {
                0 -> if (thread != Long.MIN_VALUE) s.setMuted(thread, !muted)
                1 -> s.setBlocked(address, !blocked)
                2 -> AlertDialog.Builder(this).setMessage(R.string.delete_conversation_confirm)
                    .setPositiveButton(R.string.delete) { _, _ -> if (thread != Long.MIN_VALUE) s.deleteConversation(thread); finish() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        }.show()
    }

    companion object { const val EXTRA_ADDRESS = "address"; const val EXTRA_THREAD = "thread" }
}
