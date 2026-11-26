package app.tether.client.ui

import app.tether.client.report.ClientReport
import app.tether.client.report.ReportUi
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import app.tether.client.R
import app.tether.client.link.BleCentral
import app.tether.client.link.LinkService
import app.tether.client.model.RecentsLayout
import app.tether.client.model.Search
import app.tether.client.model.UssdCodes
import app.tether.client.model.dialable
import app.tether.core.CallEntry
import app.tether.core.Contact
import app.tether.core.Phone

/** Recents, Contacts (Favourites), Keypad, Messages and Link: the iPhone app's five tabs. */
class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var banner: LinearLayout
    private lateinit var status: TextView
    private val tabs = ArrayList<TabView>()
    private var tab = TAB_KEYPAD
    private val listener: () -> Unit = { render() }
    private var query = ""
    private var number = StringBuilder()
    private var list: ListView? = null
    /** The frame around [list], and Recents' floating date bubble in it (null until Recents shows). */
    private var listFrame: FrameLayout? = null
    private var dayBubble: TextView? = null
    private var listTab = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = savedInstanceState?.getInt("tab") ?: if (app.identity.isPaired) TAB_RECENTS else TAB_LINK
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.background)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(6)) }
        header.addView(label(getString(R.string.app_name), size = 22f, bold = true).weight())
        status = label("", R.color.muted, 13f)
        header.addView(status)
        root.addView(header)
        banner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        root.addView(banner)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; setBackgroundColor(col(R.color.surface)); setPadding(0, dp(6), 0, dp(6)) }
        TAB_NAMES.forEachIndexed { i, name ->
            val t = TabView(this, TAB_ICONS[i], getString(name)) { select(i) }
            tabs += t
            bar.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bar)
        setContentView(root)
        fitSystemBars(root)
        handleIntent(intent)
        askPermissions()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent?) {
        i?.getIntExtra(EXTRA_TAB, -1)?.takeIf { it >= 0 }?.let { tab = it }
        i?.getStringExtra(EXTRA_NUMBER)?.let { number = StringBuilder(dialable(it)); tab = TAB_KEYPAD }
        listTab = -1
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putInt("tab", tab) }

    override fun onResume() {
        super.onResume()
        app.addListener(listener)
        app.appVisible = true
        if (app.calls.hasCalls) app.service(callMode = true)
        render()
    }

    override fun onPause() {
        app.removeListener(listener)
        app.appVisible = false
        super.onPause()
    }

    private fun askPermissions() {
        val want = app.linkPermissions() + listOfNotNull(Manifest.permission.RECORD_AUDIO,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null)
        val missing = want.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
        else if (app.identity.isPaired) LinkService.start(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (app.identity.isPaired) LinkService.start(this)
        render()
    }

    private fun select(i: Int) {
        if (tab == TAB_RECENTS && i != TAB_RECENTS) app.state.markRecentsSeen()
        tab = i; query = ""; listTab = -1
        if (picked != null) { picked = null; watchBack(false) }
        render()
    }

    // ------------------------------------------------------------------ render

    private fun render() {
        val s = app.state
        status.text = when (app.linkState) {
            BleCentral.State.CONNECTED -> "● " + (app.identity.peerName ?: getString(R.string.android_phone))
            BleCentral.State.OFF -> getString(R.string.state_bt_off)
            BleCentral.State.UNPAIRED -> getString(R.string.state_unpaired)
            BleCentral.State.SEARCHING -> getString(R.string.state_searching)
            else -> getString(R.string.state_connecting)
        }
        status.setTextColor(col(if (app.linkState == BleCentral.State.CONNECTED) R.color.ok else R.color.muted))
        tabs.forEachIndexed { i, t ->
            t.update(selected = i == tab, badge = when (i) { TAB_RECENTS -> s.missedCount; TAB_MESSAGES -> s.unreadTotal; else -> 0 })
        }
        renderBanner()
        when (tab) {
            TAB_RECENTS -> recents()
            TAB_CONTACTS -> contacts()
            TAB_KEYPAD -> keypad()
            TAB_MESSAGES -> messages()
            else -> linkTab()
        }
    }

    private fun renderBanner() {
        banner.removeAllViews()
        app.state.problem?.let { p ->
            // Tap to dismiss.
            banner.addView(label(describe(p), R.color.warn, 14f).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10)); minHeight = dp(48)
                pressableCard(R.color.surface) { app.state.problem = null; render() }
            }, bannerParams())
        }
        app.micWarning?.takeIf { app.identity.isPaired }?.let { w ->
            banner.addView(label(getString(micText(w)), R.color.warn, 14f).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { setColor(col(R.color.surface)); cornerRadius = dp(10).toFloat() }
            }, bannerParams())
        }
        app.calls.calls.firstOrNull()?.let { c ->
            // The call in progress: tap to go back to the call screen.
            banner.addView(label((c.name ?: app.format(c.number)) + " · " + stateWord(c.state), R.color.background, 14f, bold = true).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10)); minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
                setCompoundDrawablesRelative(icon(R.drawable.ic_call, R.color.background, 18), null, null, null); compoundDrawablePadding = dp(10)
                pressableCard(R.color.ok) { app.openCallScreen() }
            }, bannerParams())
        }
        for (r in app.calls.remote.values) {
            banner.addView(row(
                label(getString(R.string.call_elsewhere, app.displayName(r.num), r.where ?: getString(R.string.another_device)), R.color.text, 14f).weight(),
                button(getString(R.string.move_here), iconId = R.drawable.ic_call) { app.calls.take(r.cid) }.apply {
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(8), dp(4), dp(4), dp(4)) }
                },
            ).apply { setPadding(dp(12), dp(2), dp(4), dp(2)); background = GradientDrawable().apply { setColor(col(R.color.surface)); cornerRadius = dp(10).toFloat() } }, bannerParams())
        }
    }

    private fun bannerParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }

    private fun stateWord(s: String) = getString(when (s) {
        "ringing" -> R.string.call_ringing; "dialing", "connecting" -> R.string.call_dialing
        "holding" -> R.string.call_on_hold; else -> R.string.call_active
    })

    /** A search field above a list; the list survives redraws (its scroll position too). */
    private fun listScreen(hint: String?, extra: View? = null, rows: Rows<*>, onLong: ((Int) -> Unit)? = null, onClick: ((Int) -> Unit)? = null) {
        val lv = list.takeIf { listTab == tab } ?: run {
            content.removeAllViews()
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
            if (hint != null || extra != null) {
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL }
                if (hint != null) top.addView(EditText(this).apply {
                    this.hint = hint; setText(query); setSingleLine(); setTextColor(col(R.color.text)); setHintTextColor(col(R.color.muted))
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(e: Editable?) { query = e?.toString() ?: ""; render() }
                    })
                }.weight())
                extra?.let { top.addView(it) }
                box.addView(top)
            }
            val l = ListView(this).apply { divider = null; isFocusable = false }
            // The list in a frame, so a screen can float something over it (Recents' date bubble).
            val frame = FrameLayout(this).apply { addView(l, FrameLayout.LayoutParams(-1, -1)) }
            box.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(box)
            list = l; listTab = tab; listFrame = frame; dayBubble = null
            l
        }
        val first = lv.firstVisiblePosition; val top = lv.getChildAt(0)?.top ?: 0
        lv.adapter = rows   // rows are built as they scroll into view
        lv.setSelectionFromTop(first, top)
        lv.setOnItemClickListener { _, _, pos, _ -> onClick?.invoke(pos) }
        lv.setOnItemLongClickListener { _, _, pos, _ -> onLong?.invoke(pos); onLong != null }
    }

    private fun rowView(avatar: View?, title: CharSequence, subtitle: CharSequence?, right: CharSequence?, titleColor: Int = R.color.text, bold: Boolean = false) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(10), dp(4), dp(10))
            avatar?.let { addView(it) }
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(title, titleColor, 16f, bold).apply { setSingleLine() })
            subtitle?.let { texts.addView(label(it, R.color.muted, 13f).apply { maxLines = 2 }) }
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            right?.let { addView(label(it, R.color.muted, 12f).apply { setPadding(dp(8), 0, 0, 0) }) }
        }

    // ------------------------------------------------------------------ Recents

    /**
     * Recents grouped by day, as phones' own call logs show them: a day heading ("Today", "Yesterday", the date) over a
     * card of that day's calls; per line the direction (arrow), the name (red when missed, a count for calls in a row
     * from the same number), the SIM and how long the call took, and the time. While the list scrolls, a bubble at the
     * top names the day being scrolled through, and fades a moment after it stops.
     */
    private fun recents() {
        val s = app.state
        val calls = s.recents.filter { Search.matches(query, listOfNotNull(it.name, s.name(it.num)), listOf(it.num)) }
        val items = RecentsLayout.build(calls, ::dayStart)
        val clear = iconButton(R.drawable.ic_delete_sweep, getString(R.string.clear_all), R.color.accent) {
            AlertDialog.Builder(this).setMessage(R.string.clear_recents_confirm)
                .setPositiveButton(R.string.clear_all) { _, _ -> s.clearRecents() }.setNegativeButton(android.R.string.cancel, null).show()
        }
        // Calls deleted meanwhile (here or on the Android phone) are no longer picked.
        if (picked != null) { val have = s.recents.mapTo(HashSet()) { it.id }; picked!!.retainAll(have) }
        listScreen(getString(R.string.search), clear, Rows(items) { item ->
            when (item) {
                is RecentsLayout.Item.Day -> label(dayName(item.day).uppercase(), R.color.muted, 13f, bold = true).apply {
                    letterSpacing = 0.06f; setPadding(dp(8), dp(16), dp(8), dp(8))
                }
                is RecentsLayout.Item.Line -> recentLine(item)
            }
        }, onLong = { pos ->
            // A long press starts picking calls (as phones' own call logs do); the one-call menu is on the bar (⋮).
            val line = items[pos] as? RecentsLayout.Item.Line
            if (picked == null && line != null) { picked = LinkedHashSet(); togglePick(items, pos) } else togglePick(items, pos)
        }) { pos ->
            if (picked != null) togglePick(items, pos)
            else (items[pos] as? RecentsLayout.Item.Line)?.newest?.let { app.dial(it.num, it.sub ?: s.defaultSub) }
        }
        list?.selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)   // the lines ripple themselves
        dateBubble(items)
        selectionBar(items)
    }

    /**
     * The calls picked for deleting together (their ids; a line of calls in a row picks all of them), or null when
     * Recents is not picking. Picking ends on Back, on ✕, on another tab and after deleting; unpicking everything keeps
     * it on (a day's heading tapped twice).
     */
    private var picked: LinkedHashSet<Long>? = null
    private var selectBar: View? = null
    private var backWhilePicking: Any? = null

    private fun idsOf(item: RecentsLayout.Item, items: List<RecentsLayout.Item>): List<Long> = when (item) {
        is RecentsLayout.Item.Line -> item.calls.map { it.id }
        // A day's heading picks (or unpicks) every call of that day.
        is RecentsLayout.Item.Day -> items.filterIsInstance<RecentsLayout.Item.Line>().filter { it.day == item.day }.flatMap { l -> l.calls.map { it.id } }
    }

    private fun togglePick(items: List<RecentsLayout.Item>, pos: Int) {
        val set = picked ?: return
        val ids = idsOf(items.getOrNull(pos) ?: return, items)
        if (ids.isNotEmpty() && set.containsAll(ids)) set.removeAll(ids.toSet()) else set.addAll(ids)
        list?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        render()   // stays selecting with nothing picked (a day picked and unpicked again); ✕ or Back ends it
    }

    private fun stopPicking() { picked = null; render() }

    /** The bar floating at the bottom of Recents while picking: ✕, how many, ⋮ (one call's menu), select all, delete. */
    private fun selectionBar(items: List<RecentsLayout.Item>) {
        val frame = listFrame ?: return
        selectBar?.let { if (it.parent === frame) frame.removeView(it) }; selectBar = null
        val set = picked
        watchBack(set != null)
        if (set == null) return
        val all = items.filterIsInstance<RecentsLayout.Item.Line>().flatMap { l -> l.calls.map { it.id } }
        val allPicked = all.isNotEmpty() && set.containsAll(all)
        val one = items.filterIsInstance<RecentsLayout.Item.Line>().singleOrNull { l -> l.calls.any { it.id in set } }
            ?.takeIf { l -> l.calls.map { it.id }.containsAll(set) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(8), dp(4)); elevation = dp(8).toFloat()
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(col(R.color.surface)); setStroke(dp(1), col(R.color.background)) }
            isClickable = true   // taps on the bar never reach the list under it
            addView(iconButton(R.drawable.ic_close, getString(R.string.done_selecting)) { stopPicking() })
            addView(label(getString(R.string.recents_selected, set.size), size = 15f, bold = true).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }.weight())
            one?.let { l -> addView(iconButton(R.drawable.ic_more_vert, getString(R.string.more_options)) { recentMenu(l.newest) }) }
            // An icon, so "3 selected" keeps its room (in words it cut the count to "1 selec"); lit when all are picked.
            addView(iconButton(R.drawable.ic_select_all, getString(if (allPicked) R.string.select_none else R.string.select_all),
                if (allPicked) R.color.accent else R.color.text) {
                if (allPicked) set.clear() else set.addAll(all); render()
            })
            addView(button(getString(R.string.delete), Look.FILLED, R.color.bad, iconId = R.drawable.ic_delete_sweep) {
                if (set.isEmpty()) return@button
                val ids = set.toList()
                AlertDialog.Builder(this@MainActivity).setMessage(getString(R.string.delete_calls_confirm, ids.size))
                    .setPositiveButton(R.string.delete) { _, _ -> app.state.deleteRecents(ids); stopPicking() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) }; isEnabled = set.isNotEmpty() })
        }
        frame.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(4), 0, dp(4), dp(12)) })
        selectBar = bar
        // Room under the last call, so the bar never covers it.
        list?.apply { clipToPadding = false; setPadding(0, 0, 0, dp(84)) }
    }

    /** Back ends picking (Android 13+ through the back dispatcher: an app targeting Android 16 gets no onBackPressed). */
    private fun watchBack(on: Boolean) {
        if (!on) list?.setPadding(0, 0, 0, 0)
        if (Build.VERSION.SDK_INT >= 33) {
            val d = onBackInvokedDispatcher
            if (on && backWhilePicking == null) {
                val cb = android.window.OnBackInvokedCallback { stopPicking() }
                d.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb); backWhilePicking = cb
            } else if (!on) (backWhilePicking as? android.window.OnBackInvokedCallback)?.let { d.unregisterOnBackInvokedCallback(it); backWhilePicking = null }
        }
    }

    @Deprecated("Android 12 and older; newer ones use watchBack()")
    override fun onBackPressed() {
        if (picked != null) stopPicking() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    /** [a] with [f] of [b] mixed in (opaque: the list's rows sit on the page). */
    private fun mix(a: Int, b: Int, f: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - f) + ((b shr s) and 0xFF) * f).toInt() shl s
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }

    /** One line of a day's card: direction, name (and count), SIM and duration, time. Rounded where its card is. */
    private fun recentLine(item: RecentsLayout.Item.Line): View {
        val s = app.state
        val c = item.newest
        val who = s.name(c.num) ?: c.name ?: app.format(c.num)
        val missed = c.type == 3
        val (iconId, iconColor) = when (c.type) {
            1 -> R.drawable.ic_call_received to R.color.muted
            2 -> R.drawable.ic_call_made to R.color.ok
            3 -> R.drawable.ic_call_missed to R.color.bad
            5 -> R.drawable.ic_call_declined to R.color.bad
            6 -> R.drawable.ic_block to R.color.muted
            else -> R.drawable.ic_call_received to R.color.muted
        }
        val kind = getString(when (c.type) { 1 -> R.string.call_in; 2 -> R.string.call_out; 3 -> R.string.call_missed; 5 -> R.string.call_declined; 6 -> R.string.call_blocked; else -> R.string.call_other })
        val duration = if (c.dur > 0) "%d:%02d".format(c.dur / 60, c.dur % 60) else null
        val time = android.text.format.DateUtils.formatDateTime(this, c.date, android.text.format.DateUtils.FORMAT_SHOW_TIME)
        val sim = s.simName(c.sub)
        val r = dp(18).toFloat()
        val radii = when (item.pos) {
            RecentsLayout.Pos.ONLY -> floatArrayOf(r, r, r, r, r, r, r, r)
            RecentsLayout.Pos.FIRST -> floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            RecentsLayout.Pos.LAST -> floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            RecentsLayout.Pos.MIDDLE -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        val picking = picked != null
        val isPicked = picking && item.calls.all { it.id in picked!! }
        val body = GradientDrawable().apply {
            cornerRadii = radii
            setColor(if (isPicked) mix(col(R.color.surface), col(R.color.accent), 0.2f) else col(R.color.surface))
        }
        val mask = GradientDrawable().apply { cornerRadii = radii; setColor(col(R.color.text)) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.RippleDrawable(rippleColor(R.color.text), body, mask)
            // A thin line between the lines of a card (not above its first).
            if (item.pos == RecentsLayout.Pos.MIDDLE || item.pos == RecentsLayout.Pos.LAST)
                addView(View(context).apply { setBackgroundColor(col(R.color.background)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(48) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(16), dp(10)); minimumHeight = dp(62)
                if (picking) addView(android.widget.ImageView(context).apply {
                    // A check mark in a filled circle when picked, an empty ring when not.
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (isPicked) setColor(col(R.color.accent)) else setStroke(dp(2), col(R.color.muted))
                    }
                    if (isPicked) setImageDrawable(icon(R.drawable.ic_check, R.color.background, 16))
                    scaleType = android.widget.ImageView.ScaleType.CENTER
                }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(12) })
                else addView(android.widget.ImageView(context).apply { setImageDrawable(icon(iconId, iconColor, 20)) }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(14) })
                val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(label(android.text.SpannableStringBuilder(who).apply {
                    if (item.calls.size > 1) {
                        val at = length; append(" (${item.calls.size})")
                        setSpan(android.text.style.ForegroundColorSpan(col(R.color.muted)), at, length, 0)
                    }
                }, if (missed) R.color.bad else R.color.text, 16f).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, 0, 0, 0) })
                texts.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(3), 0, 0)
                    sim?.let { addView(label(it, R.color.muted, 11f).apply {
                        setPadding(dp(5), 0, dp(5), 0); background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setStroke(dp(1), col(R.color.muted)); alpha = 120 }
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) }) }
                    addView(label(duration ?: kind, R.color.muted, 13f).apply { setPadding(0, 0, 0, 0); if (duration != null) textDirection = View.TEXT_DIRECTION_LTR })
                })
                addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label(time, if (missed) R.color.bad else R.color.muted, 14f).apply { setPadding(dp(8), 0, 0, 0); textDirection = View.TEXT_DIRECTION_LTR })
            })
            // TalkBack: one sentence for the line.
            isSelected = isPicked
            contentDescription = listOfNotNull(if (isPicked) getString(R.string.recents_selected, item.calls.size) else null, who, kind, item.calls.size.takeIf { it > 1 }?.toString(), sim, duration, time).joinToString(", ")
        }
    }

    /** Whether the user is scrolling Recents (a finger on it, or a fling): kept here, as each render adds a new listener. */
    private var recentsScrolling = false
    private var bubbleShown = false
    /** One fade for every render's listener, so a later render can still cancel it. */
    private val bubbleFade = Runnable { bubbleShown = false; dayBubble?.animate()?.alpha(0f)?.setDuration(250)?.start() }

    /**
     * The bubble that names the day being scrolled through (as the Samsung Phone app does), "Today" at the top too.
     * Shown when a scroll starts (not when the list only moves because it was rebuilt), faded a moment after it stops.
     * It is shown once per scroll: starting the fade-in again on every scrolled frame kept it invisible until the
     * list stopped.
     */
    private fun dateBubble(items: List<RecentsLayout.Item>) {
        val frame = listFrame ?: return
        val bubble = dayBubble ?: label("", size = 15f).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; maxLines = 1
            setPadding(dp(18), dp(8), dp(18), dp(8)); alpha = 0f; elevation = dp(6).toFloat()
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(col(R.color.surface)); setStroke(dp(1), col(R.color.background)) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            frame.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(10) })
            dayBubble = this; recentsScrolling = false; bubbleShown = false
        }
        fun show() {
            bubble.removeCallbacks(bubbleFade)
            if (bubbleShown) return
            bubbleShown = true
            bubble.animate().cancel(); bubble.animate().alpha(1f).setDuration(120).start()
        }
        list?.setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: android.widget.AbsListView, state: Int) {
                recentsScrolling = state != android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                if (recentsScrolling) show() else { bubble.removeCallbacks(bubbleFade); bubble.postDelayed(bubbleFade, 900) }
            }
            override fun onScroll(v: android.widget.AbsListView, first: Int, visible: Int, total: Int) {
                RecentsLayout.dayAt(items, first)?.let { bubble.text = dayName(it) }
                if (recentsScrolling) show()
            }
        })
    }

    private fun recentMenu(c: CallEntry) {
        val s = app.state
        val blocked = s.isBlocked(c.num)
        val opts = listOf(getString(R.string.call), getString(R.string.message), getString(R.string.copy_number), getString(R.string.delete),
            getString(if (blocked) R.string.unblock else R.string.block))
        AlertDialog.Builder(this).setTitle(s.name(c.num) ?: app.format(c.num)).setItems(opts.toTypedArray()) { _, w ->
            when (w) {
                0 -> app.dial(c.num, c.sub ?: s.defaultSub)
                1 -> openThread(c.num)
                2 -> copy(c.num)
                3 -> s.deleteRecents(listOf(c.id))
                4 -> s.setBlocked(c.num, !blocked)
            }
        }.show()
    }

    // ------------------------------------------------------------------ Contacts

    private fun contacts() {
        val s = app.state
        val all = s.contacts.filter { Search.matches(query, listOf(it.name), it.phones.map { p -> p.n }) }
        val favs = if (query.isBlank()) all.filter { it.starred } else emptyList()
        val items = ArrayList<Any>()
        if (favs.isNotEmpty()) { items += getString(R.string.favourites); items.addAll(favs); items += getString(R.string.all_contacts) }
        items.addAll(all)
        val add = iconButton(R.drawable.ic_person_add, getString(R.string.new_contact), R.color.accent) { editContact(null) }
        listScreen(getString(R.string.search), add, Rows(items) { it ->
            if (it is String) label(it.uppercase(), R.color.accent, 12f, bold = true).apply { setPadding(dp(4), dp(12), 0, dp(4)) }
            else (it as Contact).let { c -> rowView(avatar(c.name, c.photo), (if (c.starred) "★ " else "") + c.name, c.phones.firstOrNull()?.let { p -> app.format(p.n) }, null) }
        }) { pos -> (items[pos] as? Contact)?.let { contactMenu(it) } }
    }

    private fun contactMenu(c: Contact) {
        val s = app.state
        val opts = ArrayList<Pair<String, () -> Unit>>()
        for (p in c.phones) {
            opts += getString(R.string.call_number, app.format(p.n)) to { app.dial(p.n, s.defaultSub) }
            opts += getString(R.string.message_number, app.format(p.n)) to { openThread(p.n) }
        }
        opts += getString(if (c.starred) R.string.unfavourite else R.string.favourite) to { s.setStarred(c.id, !c.starred) }
        opts += getString(R.string.edit) to { editContact(c) }
        c.phones.firstOrNull()?.let { p -> val b = s.isBlocked(p.n); opts += getString(if (b) R.string.unblock else R.string.block) to { s.setBlocked(p.n, !b) } }
        AlertDialog.Builder(this).setTitle(c.name).setItems(opts.map { it.first }.toTypedArray()) { _, w -> opts[w].second() }.show()
    }

    private fun editContact(c: Contact?, prefill: String? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = EditText(this).apply { hint = getString(R.string.name); setText(c?.name ?: "") }
        val phone = EditText(this).apply { hint = getString(R.string.phone); inputType = InputType.TYPE_CLASS_PHONE; setText(c?.phones?.firstOrNull()?.n ?: prefill ?: "") }
        box.addView(name); box.addView(phone)
        AlertDialog.Builder(this).setTitle(if (c == null) R.string.new_contact else R.string.edit).setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val n = name.text.toString().trim(); val p = phone.text.toString().trim()
                if (n.isEmpty()) return@setPositiveButton
                val phones = listOfNotNull(p.takeIf { it.isNotEmpty() }?.let { Phone(it, c?.phones?.firstOrNull()?.type ?: "mobile") }) + (c?.phones?.drop(1) ?: emptyList())
                if (c == null) app.state.addContact(n, phones, emptyList()) else app.state.updateContact(c.id, n, phones, c.emails)
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    // ------------------------------------------------------------------ Keypad

    private var keypadRoot: View? = null
    private lateinit var keypadNumber: TextView
    private lateinit var keypadWho: TextView
    private lateinit var keypadBack: View
    private lateinit var keypadCalls: LinearLayout
    private lateinit var keypadAdd: View
    private lateinit var keypadUssd: View

    /** The keypad is built once while it is shown; a key press or a state change only updates its texts and buttons. */
    private fun keypad() {
        list = null; listTab = -1
        if (keypadRoot?.parent == null) {
            content.removeAllViews()
            content.addView(buildKeypad().also { keypadRoot = it })
        }
        updateKeypad()
    }

    private fun buildKeypad(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(8), dp(24), dp(16)) }
        // The number, centred, with backspace at its end (a spacer of the same width keeps it centred).
        keypadNumber = label("", size = 32f).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.START
            textDirection = View.TEXT_DIRECTION_LTR }
        keypadBack = iconButton(R.drawable.ic_backspace, getString(R.string.delete_digit), R.color.muted,
            onLong = { number.clear(); updateKeypad() }) { if (number.isNotEmpty()) number.deleteCharAt(number.length - 1); updateKeypad() }
        box.addView(row(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)) }, keypadNumber.weight(), keypadBack),
            LinearLayout.LayoutParams(-1, dp(64)))
        keypadWho = label("", R.color.muted, 14f).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; maxLines = 1 }
        box.addView(keypadWho, LinearLayout.LayoutParams(-1, -2))
        val grid = GridLayout(this).apply { columnCount = 3; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
        for ((k, sub) in keys) {
            grid.addView(dialKey(k, sub, onLong = if (k == "0") ({ number.append("+"); updateKeypad() }) else null) { number.append(k); updateKeypad() },
                GridLayout.LayoutParams().apply { width = dp(76); height = dp(76); setMargins(dp(10), dp(6), dp(10), dp(6)) })
        }
        box.addView(grid, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        keypadCalls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER }
        box.addView(keypadCalls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        // Under the call buttons: carrier codes (USSD) on the Android phone's SIM, and adding the number to contacts.
        keypadUssd = button(getString(R.string.ussd_button), Look.TONAL, iconId = R.drawable.ic_dialpad) { openUssd(number.toString(), null) }
        keypadAdd = button(getString(R.string.add_contact), Look.TEXT, iconId = R.drawable.ic_person_add) { editContact(null, number.toString()) }
        box.addView(keypadUssd, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        box.addView(keypadAdd, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        return ScrollView(this).apply { addView(box) }
    }

    /** The carrier-code screen, with [code] typed in (sent at once when it is a code) on SIM [sub]. */
    private fun openUssd(code: String, sub: Int?) {
        startActivity(Intent(this, UssdActivity::class.java).putExtra(UssdActivity.EXTRA_CODE, code).apply { sub?.let { putExtra(UssdActivity.EXTRA_SUB, it) } }
            .putExtra(UssdActivity.EXTRA_SEND, UssdCodes.valid(code)))
    }

    private fun updateKeypad() {
        val n = number.toString()
        keypadNumber.text = if (n.isEmpty()) "" else app.format(n)
        val name = app.state.name(n)
        keypadWho.text = name ?: ""
        keypadBack.visibility = if (n.isEmpty()) View.INVISIBLE else View.VISIBLE
        val code = UssdCodes.valid(n)
        keypadAdd.visibility = if (n.isNotEmpty() && name == null && !code) View.VISIBLE else View.INVISIBLE
        // Shown when the Android phone runs carrier codes (its builds with the phone tools say so in their status).
        keypadUssd.visibility = if (app.state.canUssd) View.VISIBLE else View.GONE
        // One green call button, or one per SIM labelled with its name.
        keypadCalls.removeAllViews()
        val sims = app.state.sims
        val targets = if (sims.size > 1) sims.map { it.name.ifEmpty { getString(R.string.sim_n, it.slot + 1) } to it.sub }
            else listOf(getString(R.string.call) to app.state.defaultSub)
        for ((label, sub) in targets) keypadCalls.addView(roundAction(R.drawable.ic_call, label, R.color.ok) {
            if (number.isEmpty()) return@roundAction
            // A carrier code (*123#) on a call button: sent as USSD on that SIM, like a phone's own dialer does.
            if (UssdCodes.valid(number.toString()) && app.state.canUssd) { openUssd(number.toString(), sub); number.clear(); updateKeypad(); return@roundAction }
            app.dial(number.toString(), sub); number.clear(); updateKeypad()
        }.apply { getChildAt(0).isEnabled = n.isNotEmpty() }, LinearLayout.LayoutParams(dp(112), -2))
    }

    // ------------------------------------------------------------------ Messages

    private fun messages() {
        val s = app.state
        val convs = s.conversations.filter { c -> Search.matches(query, listOfNotNull(s.name(c.address)), listOf(c.address)) ||
            (query.length > 1 && s.messages(c.thread).any { Search.fold(it.body).contains(Search.fold(query)) }) }
        val compose = iconButton(R.drawable.ic_edit, getString(R.string.new_message), R.color.accent) {
            val f = EditText(this).apply { hint = getString(R.string.phone); inputType = InputType.TYPE_CLASS_PHONE }
            AlertDialog.Builder(this).setTitle(R.string.new_message).setView(FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(f) })
                .setPositiveButton(R.string.next) { _, _ -> dialable(f.text.toString()).takeIf { it.isNotEmpty() }?.let { openThread(it) } }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        listScreen(getString(R.string.search), compose, Rows(convs) { c ->
            val who = s.name(c.address) ?: app.format(c.address)
            val muted = if (s.isMuted(c.thread)) "🔕 " else ""
            val last = (if (!c.last.let { it.box == 1 }) getString(R.string.you) + ": " else "") + c.last.body.replace('\n', ' ')
            rowView(avatar(who, s.photo(c.address)), muted + who + if (c.unread > 0) "  (${c.unread})" else "", last.take(120), time(c.last.date), bold = c.unread > 0)
        }, onLong = { pos -> threadMenu(convs[pos].thread, convs[pos].address) }) { pos -> openThread(convs[pos].address, convs[pos].thread) }
    }

    private fun threadMenu(thread: Long, address: String) {
        val s = app.state
        val muted = s.isMuted(thread); val blocked = s.isBlocked(address)
        val opts = listOf(getString(R.string.call), getString(if (muted) R.string.unmute else R.string.mute),
            getString(if (blocked) R.string.unblock else R.string.block), getString(R.string.mark_read), getString(R.string.delete_conversation))
        AlertDialog.Builder(this).setTitle(s.name(address) ?: app.format(address)).setItems(opts.toTypedArray()) { _, w ->
            when (w) {
                0 -> app.dial(address, s.preferredSim(address))
                1 -> s.setMuted(thread, !muted)
                2 -> s.setBlocked(address, !blocked)
                3 -> s.markRead(thread)
                4 -> AlertDialog.Builder(this).setMessage(R.string.delete_conversation_confirm)
                    .setPositiveButton(R.string.delete) { _, _ -> s.deleteConversation(thread) }.setNegativeButton(android.R.string.cancel, null).show()
            }
        }.show()
    }

    private fun openThread(address: String, thread: Long? = null) {
        startActivity(Intent(this, ThreadActivity::class.java).putExtra(ThreadActivity.EXTRA_ADDRESS, address)
            .putExtra(ThreadActivity.EXTRA_THREAD, thread ?: app.state.threadFor(address) ?: Long.MIN_VALUE))
    }

    private fun copy(t: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("number", t))
    }

    // ------------------------------------------------------------------ Link

    private fun linkTab() {
        val lv = list
        val keepScroll = (content.getChildAt(0) as? ScrollView)?.takeIf { listTab == TAB_LINK && lv == null }?.scrollY ?: 0
        list = null; listTab = TAB_LINK
        content.removeAllViews()
        val s = app.state
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(24)) }
        val connected = app.link.isConnected

        // The Android phone
        val phone = card()
        val st = s.status
        phone.addView(label(app.identity.peerName ?: getString(R.string.android_phone), size = 18f, bold = true))
        phone.addView(label(when (app.linkState) {
            BleCentral.State.CONNECTED -> getString(R.string.state_connected_via, app.transport.uppercase())
            BleCentral.State.OFF -> getString(R.string.state_bt_off)
            BleCentral.State.UNPAIRED -> getString(R.string.state_unpaired)
            BleCentral.State.SEARCHING -> getString(R.string.state_searching)
            BleCentral.State.HANDSHAKING, BleCentral.State.CONNECTING -> getString(R.string.state_connecting)
        }, if (connected) R.color.ok else R.color.muted))
        if (st != null) phone.addView(label(getString(R.string.battery, st.optInt("battery"), if (st.optBoolean("charging")) " ⚡" else ""), R.color.muted, 14f))
        // The Android phone cannot bridge call audio (Android 8-10, or its software gives none): calls stay on it.
        when (st?.optJSONObject("helper")?.optString("audio")) {
            "unavailable" -> phone.addView(label(getString(R.string.audio_unavailable), R.color.warn, 13f))
            "blocked" -> phone.addView(label(getString(R.string.audio_blocked), R.color.warn, 13f))
        }
        for (sim in s.sims) {
            val bars = "▂▄▆█".take(sim.signal.coerceIn(0, 4)).padEnd(4, '·')
            val line = listOfNotNull(sim.name.ifEmpty { getString(R.string.sim_n, sim.slot + 1) }, sim.carrier.takeIf { it.isNotEmpty() && it != sim.name },
                if (sim.hasService) "$bars ${sim.net ?: ""}".trim() else getString(R.string.no_service), if (sim.roaming) "R" else null).joinToString(" · ")
            phone.addView(label(line, size = 14f))
        }
        st?.optJSONArray("clients")?.let { a ->
            val names = (0 until a.length()).map { a.getJSONObject(it) }.map { (if (it.optBoolean("self")) "● " else "○ ") + it.optString("name") + if (it.optBoolean("audio")) " ♪" else "" }
            if (names.size > 1) phone.addView(label(getString(R.string.clients, names.joinToString("  ")), R.color.muted, 13f))
        }
        // Not paired yet: how to start, in plain words.
        if (!app.identity.isPaired) phone.addView(label(getString(R.string.pair_guide), R.color.muted, 13f))
        phone.addView(row(
            button(getString(if (app.identity.isPaired) R.string.pair_again else R.string.pair), if (app.identity.isPaired) Look.TONAL else Look.FILLED) {
                startActivity(Intent(this, PairActivity::class.java))
            },
            *(if (app.identity.isPaired) arrayOf(button(getString(R.string.unpair), Look.TEXT, R.color.bad) {
                AlertDialog.Builder(this).setMessage(R.string.unpair_confirm).setPositiveButton(R.string.unpair) { _, _ -> app.unpair() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }) else emptyArray()),
        ))
        box.addView(phone)

        // Switches
        if (connected) {
            if (s.net == null && s.netPending == null) s.refreshNet()
            val net = s.net
            val sw = card()
            sw.addView(label(getString(R.string.switches).uppercase(), R.color.accent, 12f, bold = true))
            val can = net?.optJSONObject("can")
            fun toggle(title: Int, what: String, on: Boolean?, shown: Boolean = true) {
                if (!shown) return
                sw.addView(Switch(this).apply {
                    text = getString(title); textSize = 16f; setTextColor(col(R.color.text)); isChecked = on == true
                    isEnabled = on != null && s.netPending == null
                    setPadding(0, dp(8), 0, dp(8))
                    setOnCheckedChangeListener { _, v -> if (v != on) s.setNet(what, v) }
                })
            }
            toggle(R.string.mobile_data, "data", net?.optBoolean("data"))
            toggle(R.string.hotspot, "hotspot", net?.optBoolean("hotspot"), can?.optBoolean("hotspot", true) ?: true)
            toggle(R.string.bt_tethering, "bt", net?.optBoolean("bt"), can?.optBoolean("bt", true) ?: true)
            toggle(R.string.airplane, "airplane", net?.optBoolean("airplane"))
            toggle(R.string.share_vpn, "share", net?.optBoolean("share"))
            if (net?.optBoolean("vpn") == true) sw.addView(label(getString(R.string.vpn_up), R.color.ok, 13f))
            net?.optJSONObject("proxy")?.optJSONArray("addrs")?.let { a ->
                if (net.optBoolean("share")) for (i in 0 until a.length()) a.getJSONObject(i).let { ad ->
                    sw.addView(label("${ad.optString("kind")}: ${ad.optString("ip")}:${net.optJSONObject("proxy")?.optInt("port")}  ·  ${ad.optString("pac")}", R.color.muted, 12f).apply { setTextIsSelectable(true) })
                }
            }
            val sims = s.sims
            if (sims.size > 1) {
                val dataSub = net?.optInt("dataSub", -1) ?: -1
                // The data SIM: one choice per SIM, the chosen one filled.
                sw.addView(label(getString(R.string.data_sim), size = 16f).apply { setPadding(0, dp(10), 0, 0) })
                sw.addView(evenRow(*sims.map { sim ->
                    button(sim.name.ifEmpty { getString(R.string.sim_n, sim.slot + 1) }, if (sim.sub == dataSub) Look.FILLED else Look.TONAL) {
                        if (sim.sub != dataSub) s.setDataSim(sim.sub)
                    }.apply { isEnabled = s.netPending == null; isSelected = sim.sub == dataSub }
                }.toTypedArray()))
            }
            for (sim in sims) {
                val mode = net?.optJSONArray("rat")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optInt("slot") == sim.slot }?.optString("mode") }
                sw.addView(row(label(getString(R.string.network_mode, sim.name.ifEmpty { getString(R.string.sim_n, sim.slot + 1) }), size = 16f).weight(),
                    button(mode ?: "–", Look.TONAL) {
                        val modes = arrayOf("5G", "4G", "3G", "2G")
                        AlertDialog.Builder(this).setItems(modes) { _, w -> s.setNetworkMode(sim.slot, modes[w]) }.show()
                    }))
            }
            s.netPending?.let { sw.addView(label(getString(R.string.waiting_for_phone), R.color.muted, 13f)) }
            s.netError?.let { sw.addView(label(if (it == "no_answer") getString(R.string.err_no_answer) else it, R.color.warn, 13f)) }
            box.addView(sw)
        }

        // Calls: in the Phone app (a calling account per SIM) or in this app's own call screen.
        box.addView(callsCard())

        // This phone
        val me = card()
        me.addView(label(getString(R.string.this_phone).uppercase(), R.color.accent, 12f, bold = true))
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) me.addView(button(getString(R.string.allow_background), Look.OUTLINED) {
            runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
        })
        if (!app.hasLinkPermissions()) me.addView(button(getString(R.string.grant_permissions)) { askPermissions() })
        app.link.roundTripMs?.let { me.addView(label(getString(R.string.round_trip, it), R.color.muted, 13f)) }
        me.addView(row(
            button(getString(R.string.report_title), Look.TONAL) { ReportUi.send(this) { ClientReport.build(app) } },
        ))
        me.addView(label(getString(R.string.report_hint), R.color.muted, 13f))
        me.addView(row(
            button(getString(R.string.link_log), Look.TEXT) { showLog() },
            *(if (connected) arrayOf(button(getString(R.string.selftest), Look.TEXT) { s.runSelftest() }) else emptyArray()),
        ))
        s.selftest?.let { t ->
            val m = t.optJSONArray("methods")
            me.addView(label((0 until (m?.length() ?: 0)).joinToString("\n") { i -> m!!.getJSONObject(i).let { "${if (it.optBoolean("ok")) "✓" else "✗"} ${it.optString("name")} ${it.optString("note")}" } }, R.color.muted, 12f))
        }
        box.addView(me)
        val sv = ScrollView(this).apply { addView(box) }
        content.addView(sv)
        sv.post { sv.scrollTo(0, keepScroll) }
    }

    /**
     * "Use the Phone app for calls" and what it still needs: the calling accounts turned on in the Phone app, and the
     * Phone permission (to see which accounts are on, to dial from this app's keypad through the Phone app and to open
     * its call screen). Until an account is on, calls use this app's own call screen, which the card says. Also, for
     * that own screen: whether Android lets it wake the screen for an incoming call (full-screen notifications).
     */
    private fun callsCard(): View {
        val c = card()
        c.addView(label(getString(R.string.calls_title).uppercase(), R.color.accent, 12f, bold = true))
        val t = app.telecom
        c.addView(Switch(this).apply {
            text = getString(R.string.use_phone_app); textSize = 16f; setTextColor(col(R.color.text)); isChecked = t.usePhoneApp
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, v ->
                t.usePhoneApp = v
                if (v && !t.hasPhonePermissions()) requestPermissions(t.phonePermissions, 2)
            }
        })
        if (!t.usePhoneApp) c.addView(label(getString(R.string.phone_app_off), R.color.muted, 13f))
        else {
            val accounts = t.phoneAccounts()
            val off = accounts.filter { !it.second }
            when {
                accounts.isEmpty() -> c.addView(label(getString(R.string.phone_app_turn_on), R.color.warn, 13f))
                off.isEmpty() -> c.addView(label(getString(R.string.phone_app_ready), R.color.ok, 13f))
                off.size == accounts.size -> c.addView(label(getString(R.string.phone_app_turn_on), R.color.warn, 13f))
                else -> c.addView(label(getString(R.string.phone_app_turn_on_some, off.joinToString { it.first }), R.color.warn, 13f))
            }
            if (off.isNotEmpty() || accounts.isEmpty())
                c.addView(button(getString(R.string.phone_app_open_accounts), iconId = R.drawable.ic_call) { t.openCallingAccounts(this) })
            if (!t.hasPhonePermissions()) {
                c.addView(label(getString(R.string.phone_app_permission), R.color.muted, 13f))
                c.addView(button(getString(R.string.allow_phone_calls), Look.OUTLINED) { requestPermissions(t.phonePermissions, 2) })
            }
        }
        // Calls in this app's own screen ring through a full-screen notification: from Android 14 that wakes the screen
        // only with "Full screen notifications" allowed for the app.
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            c.addView(label(getString(R.string.full_screen_off), R.color.warn, 13f).apply { setPadding(0, dp(10), 0, dp(2)) })
            c.addView(button(getString(R.string.full_screen_allow), Look.OUTLINED) {
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) }
                    .recoverCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            })
        }
        return c
    }

    private fun showLog() {
        val t = TextView(this).apply { text = app.journal.text; textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(16), dp(8), dp(16), dp(8)) }
        AlertDialog.Builder(this).setTitle(R.string.link_log).setView(ScrollView(this).apply { addView(t) })
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.copy) { _, _ -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("log", app.journal.text)) }
            .show()
    }

    companion object {
        const val EXTRA_TAB = "tab"; const val EXTRA_NUMBER = "number"
        const val TAB_RECENTS = 0; const val TAB_CONTACTS = 1; const val TAB_KEYPAD = 2; const val TAB_MESSAGES = 3; const val TAB_LINK = 4
        private val TAB_NAMES = listOf(R.string.tab_recents, R.string.tab_contacts, R.string.tab_keypad, R.string.tab_messages, R.string.tab_link)
        private val TAB_ICONS = listOf(R.drawable.ic_history, R.drawable.ic_person, R.drawable.ic_dialpad, R.drawable.ic_chat, R.drawable.ic_link)
    }
}
