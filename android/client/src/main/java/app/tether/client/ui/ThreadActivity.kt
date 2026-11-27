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
 * when it was opened (it opens there); send from either SIM, call, mute, block. A long press selects texts (tap more):
 * copy them together, star them, delete them; with one selected, a long press on it again (or Select text) picks part of
 * its text, as Telegram does.
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
    /** The rows on screen now (the pinned day reads them while scrolling). */
    private var items: List<ThreadLayout.Item> = emptyList()
    private lateinit var pinned: android.widget.TextView
    private val listener: () -> Unit = { render() }
    private lateinit var header: LinearLayout
    /** The bar shown instead of [header] while texts are selected. */
    private lateinit var selBar: LinearLayout
    /** The selected texts (ids), or null when not selecting. */
    private var picked: LinkedHashSet<Long>? = null
    private var backCb: Any? = null
    /** A text to show when the conversation opens (from the Starred screen): scrolled to and briefly lit. */
    private var focus: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        address = intent.getStringExtra(EXTRA_ADDRESS) ?: run { finish(); return }
        thread = intent.getLongExtra(EXTRA_THREAD, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: app.state.threadFor(address) ?: Long.MIN_VALUE
        sub = app.state.preferredSim(address)
        focus = intent.getLongExtra(EXTRA_FOCUS, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        // A text sent into a new conversation gets its real thread id with the provider row: follow it.
        app.state.onThreadMoved = { from, to -> if (from == thread) thread = to }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.background)) }
        header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(8), dp(8)) }
        title = label(app.displayName(address), size = 19f, bold = true).apply { setSingleLine() }
        header.addView(title.weight())
        header.addView(iconButton(R.drawable.ic_call, getString(R.string.call), R.color.accent) { app.dial(address, sub) })
        header.addView(iconButton(R.drawable.ic_more_vert, getString(R.string.more_options)) { menu() })
        root.addView(header)
        selBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4)); visibility = View.GONE
        }
        root.addView(selBar)
        list = ListView(this).apply { divider = null; transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL; isStackFromBottom = true }
        // The list in a frame, with the day pinned over its top while that day's own heading is scrolled away.
        pinned = dayChip("").apply { visibility = View.GONE; elevation = dp(6).toFloat(); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(android.widget.FrameLayout(this).apply {
            addView(list, android.widget.FrameLayout.LayoutParams(-1, -1))
            addView(pinned, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(PIN_TOP) })
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        list.setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: android.widget.AbsListView, state: Int) {}
            override fun onScroll(v: android.widget.AbsListView, first: Int, visible: Int, total: Int) = updatePinned()
        })
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
        val items = ThreadLayout.build(msgs, ::dayStart, unreadFrom).also { items = it }
        val atEnd = list.lastVisiblePosition >= list.count - 2
        list.adapter = Rows(items) { item ->
            when (item) {
                is ThreadLayout.Item.Day -> dayHeading(dayName(item.day))
                ThreadLayout.Item.Unread -> unreadLine()
                is ThreadLayout.Item.Msg -> bubble(item)
            }
        }
        // Texts deleted meanwhile are no longer selected.
        picked?.retainAll(msgs.mapTo(HashSet()) { it.id })
        selectionBar(msgs)
        val unreadAt = items.indexOf(ThreadLayout.Item.Unread)
        // Opened with unread texts: at the line (the older ones above it); else, and as new texts come, at the end.
        val focusAt = focus?.let { f -> items.indexOfFirst { it is ThreadLayout.Item.Msg && it.sms.id == f } } ?: -1
        if (first && focusAt >= 0) {
            list.setSelectionFromTop(focusAt, resources.displayMetrics.heightPixels / 4)
            // Lit for a moment, then as the others.
            list.postDelayed({ focus = null; render() }, 1600)
        } else if (first && unreadAt >= 0) list.setSelectionFromTop(unreadAt, dp(8))
        else if (atEnd || msgs.size != lastCount) list.setSelection(items.size - 1)
        lastCount = msgs.size
        list.post { updatePinned() }
    }

    /** A day's name in a rounded chip (the headings and the pinned day). */
    private fun dayChip(t: String) = label(t, R.color.text, 13f, bold = true).apply {
        gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; maxLines = 1
        setPadding(dp(14), dp(6), dp(14), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat(); setColor(col(R.color.surface))
            setStroke(dp(1), android.content.res.ColorStateList.valueOf(col(R.color.muted)).withAlpha(0x40).defaultColor)
        }
    }

    /** A day's heading: its chip between two thin lines, a clear break between days. */
    private fun dayHeading(t: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(HEADING_TOP), dp(16), dp(8))
        fun rule() = View(context).apply { setBackgroundColor(col(R.color.muted)); alpha = 0.35f }
        addView(rule(), LinearLayout.LayoutParams(0, dp(1), 1f))
        addView(dayChip(t), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10); marginEnd = dp(10) })
        addView(rule(), LinearLayout.LayoutParams(0, dp(1), 1f))
    }

    /**
     * The day pinned at the top: shown only while the heading of the day at the top of the list is out of sight
     * (scrolled away above), and hidden as the next day's heading comes up under it, so two never overlap.
     */
    private fun updatePinned() {
        val first = list.firstVisiblePosition
        val top = list.getChildAt(0)
        // The heading's chip (below the row's top padding) still below the pinned day's place: no pinned day yet. It takes
        // over when the chip reaches that place, right over it, so the heading looks as if it sticks to the top.
        val headingInSight = items.getOrNull(first) is ThreadLayout.Item.Day && (top?.top ?: 0) + dp(HEADING_TOP) >= dp(PIN_TOP)
        val next = list.getChildAt(1)
        val nextHeadingClose = items.getOrNull(first + 1) is ThreadLayout.Item.Day && next != null && next.top < pinned.height + dp(PIN_TOP + 8)
        val day = ThreadLayout.dayAt(items, first)
        if (top == null || day == null || headingInSight || nextHeadingClose) { pinned.visibility = View.GONE; return }
        pinned.text = dayName(day)
        pinned.visibility = View.VISIBLE
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
        val picking = picked != null
        val isPicked = picked?.contains(m.id) == true
        val starred = app.state.isStarred(m.id)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false
            gravity = (if (mine && !picking) Gravity.END else Gravity.START) or Gravity.BOTTOM
            setPadding(dp(12), dp(if (item.startsRun) 8 else 2), dp(12), dp(2))
            // Selected: the whole row tinted, as Telegram does.
            if (isPicked) setBackgroundColor(android.content.res.ColorStateList.valueOf(col(R.color.accent)).withAlpha(0x26).defaultColor)
            else if (m.id == focus) setBackgroundColor(android.content.res.ColorStateList.valueOf(col(R.color.warn)).withAlpha(0x2E).defaultColor)
            setOnLongClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); onLong(m); true }
            setOnClickListener { if (picked != null) toggle(m.id) }
        }
        if (picking) {
            // A ring at the start of each row, filled with a check mark when selected; own texts pushed to the end.
            wrap.addView(android.widget.ImageView(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL
                    if (isPicked) setColor(col(R.color.accent)) else setStroke(dp(2), col(R.color.muted)) }
                if (isPicked) setImageDrawable(icon(R.drawable.ic_check, R.color.background, 16))
                scaleType = android.widget.ImageView.ScaleType.CENTER
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10); bottomMargin = dp(8) })
            if (mine) wrap.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
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
            // Links open with a tap, except while selecting (a tap then selects the text).
            if (!picking) movementMethod = android.text.method.LinkMovementMethod.getInstance()
            setOnLongClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); onLong(m); true }
            setOnClickListener { if (picked != null) toggle(m.id) }
            // Each line takes the direction of its first letter (Persian: right to left, on the right; English: left to
            // right, on the left); a line with no letters follows the phone's language.
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            maxWidth = (resources.displayMetrics.widthPixels * 0.72).toInt()
        }
        val state = when (m.box) { 4, 6 -> getString(R.string.sending); 5 -> getString(R.string.not_delivered); else -> null }
        val side = if (!item.showTime && state == null && !starred) null else LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = if (mine) Gravity.END else Gravity.START
            setPaddingRelative(dp(if (mine) 0 else 8), 0, dp(if (mine) 8 else 0), dp(2))   // relative: the gap faces the bubble in Persian too
            if (showSim) app.state.simName(m.sub)?.let { addView(label(it, R.color.muted, 10f).apply { setPadding(0, 0, 0, 0); maxLines = 1 }) }
            if (starred) addView(android.widget.ImageView(context).apply { setImageDrawable(icon(R.drawable.ic_star, R.color.warn, 14))
                contentDescription = getString(R.string.starred) }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { bottomMargin = dp(2) })
            // Each line its own width (the column is as wide as its widest; a star alone made the time cut off).
            if (item.showTime || state != null) addView(label(android.text.format.DateUtils.formatDateTime(context, m.date, android.text.format.DateUtils.FORMAT_SHOW_TIME), R.color.muted, 11f).apply {
                setPadding(0, 0, 0, 0); textDirection = View.TEXT_DIRECTION_LTR }, LinearLayout.LayoutParams(-2, -2))
            state?.let { addView(label(it, if (m.box == 5) R.color.bad else R.color.muted, 11f).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(-2, -2)) }
        }
        if (mine) side?.let { wrap.addView(it) }
        wrap.addView(body, LinearLayout.LayoutParams(-2, -2))
        if (!mine) side?.let { wrap.addView(it) }
        return wrap
    }

    /**
     * A long press: starts selecting with this text; on the only selected text, picks part of its text (as Telegram
     * does: holding the selected message again goes into its text); else adds or removes it.
     */
    private fun onLong(m: Sms) {
        val set = picked
        when {
            set == null -> { picked = linkedSetOf(m.id); render() }
            set.size == 1 && m.id in set -> selectText(m)
            else -> toggle(m.id)
        }
    }

    private fun toggle(id: Long) {
        val set = picked ?: return
        if (!set.remove(id)) set += id
        list.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        render()
    }

    private fun stopPicking() { picked = null; render() }

    /**
     * While selecting, the header gives way to: ✕, how many, and actions for the selected texts: Select text (one),
     * Send again (one that was not delivered), Star / Unstar, Copy (together, oldest first), Delete (asks first).
     */
    private fun selectionBar(msgs: List<Sms>) {
        val set = picked
        watchBack(set != null)
        header.visibility = if (set == null) View.VISIBLE else View.GONE
        selBar.visibility = if (set == null) View.GONE else View.VISIBLE
        selBar.removeAllViews()
        if (set == null) return
        val s = app.state
        val sel = msgs.filter { it.id in set }
        val one = sel.singleOrNull()
        val allStarred = sel.isNotEmpty() && sel.all { s.isStarred(it.id) }
        selBar.addView(iconButton(R.drawable.ic_close, getString(R.string.done_selecting)) { stopPicking() })
        selBar.addView(label(set.size.toString(), size = 19f, bold = true).apply {
            maxLines = 1; contentDescription = getString(R.string.recents_selected, set.size); setPadding(dp(6), 0, 0, 0) }.weight())
        fun act(iconId: Int, name: String, colorId: Int = R.color.text, onClick: () -> Unit) =
            selBar.addView(iconButton(iconId, name, colorId, onClick = onClick).apply { isEnabled = sel.isNotEmpty(); alpha = if (sel.isEmpty()) 0.38f else 1f })
        one?.let { m -> act(R.drawable.ic_text_fields, getString(R.string.select_text)) { selectText(m) } }
        one?.takeIf { it.box == 5 }?.let { m -> act(R.drawable.ic_send, getString(R.string.send_again)) {
            s.deleteMessages(listOf(m.id)); s.sendSms(address, m.body, sub); stopPicking() } }
        act(if (allStarred) R.drawable.ic_star else R.drawable.ic_star_border, getString(if (allStarred) R.string.unstar else R.string.star),
            if (allStarred) R.color.warn else R.color.text) { s.setStarred(sel.map { it.id }, !allStarred); stopPicking() }
        act(R.drawable.ic_copy, getString(R.string.copy)) { copy(sel.joinToString("\n\n") { it.body }); stopPicking() }
        act(R.drawable.ic_delete, getString(R.string.delete), R.color.bad) {
            AlertDialog.Builder(this).setMessage(getString(R.string.delete_texts_confirm, sel.size))
                .setPositiveButton(R.string.delete) { _, _ -> s.deleteMessages(sel.map { it.id }); stopPicking() }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("text", text))
        // Android 13+ shows its own "copied" confirmation.
        if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(this, R.string.copied, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** Part of a text: the text, selectable (hold a word, drag the handles, Copy), or Copy all. */
    private fun selectText(m: Sms) {
        val tv = label(m.body, size = 17f).apply {
            setTextIsSelectable(true); setPadding(dp(24), dp(8), dp(24), dp(8))
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG; textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(getString(R.string.select_text_hint), R.color.muted, 13f).apply { setPadding(dp(24), dp(4), dp(24), dp(4)) })
            addView(android.widget.ScrollView(context).apply { addView(tv) })
        }
        AlertDialog.Builder(this).setTitle(R.string.select_text).setView(box)
            .setPositiveButton(R.string.copy_all) { _, _ -> copy(m.body); stopPicking() }
            .setNegativeButton(R.string.done_selecting, null).show()
    }

    /** Back ends selecting (Android 13+ through the back dispatcher: an app targeting Android 16 gets no onBackPressed). */
    private fun watchBack(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val d = onBackInvokedDispatcher
        if (on && backCb == null) {
            val cb = android.window.OnBackInvokedCallback { stopPicking() }
            d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb); backCb = cb
        } else if (!on) (backCb as? android.window.OnBackInvokedCallback)?.let { d.unregisterOnBackInvokedCallback(it); backCb = null }
    }

    @Deprecated("Android 12 and older; newer ones use watchBack()")
    override fun onBackPressed() {
        if (picked != null) stopPicking() else @Suppress("DEPRECATION") super.onBackPressed()
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

    companion object { private const val PIN_TOP = 0; private const val HEADING_TOP = 18; const val EXTRA_ADDRESS = "address"; const val EXTRA_THREAD = "thread"; const val EXTRA_FOCUS = "focus" }
}
