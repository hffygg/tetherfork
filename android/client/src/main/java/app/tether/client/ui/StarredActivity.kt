package app.tether.client.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.tether.client.R
import app.tether.client.model.Search
import app.tether.client.model.Starred
import app.tether.core.Sms

/**
 * Starred texts (stars are kept on this phone). Without a chat: the chats that have starred texts, each with how many
 * and its newest one; tap one for its starred texts. With a chat: its starred texts, newest first; tap one to open it
 * in the conversation, hold it to unstar or copy. A search field filters by the texts (and, in the chat list, names).
 */
class StarredActivity : Activity() {
    private var thread: Long? = null
    private var address: String? = null
    private lateinit var list: ListView
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private var query = ""
    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thread = intent.getLongExtra(EXTRA_THREAD, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        address = intent.getStringExtra(EXTRA_ADDRESS)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.background)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(10), dp(16), dp(4)) }
        header.addView(iconButton(R.drawable.ic_arrow_back, getString(R.string.back)) { finish() })
        header.addView(label(address?.let { app.displayName(it) } ?: getString(R.string.starred_messages), size = 19f, bold = true).apply {
            setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(8), 0, dp(8), 0) }.weight())
        count = label("", R.color.warn, 14f, bold = true).apply {
            setCompoundDrawablesRelative(icon(R.drawable.ic_star, R.color.warn, 16), null, null, null); compoundDrawablePadding = dp(4) }
        header.addView(count)
        root.addView(header)
        root.addView(EditText(this).apply {
            hint = getString(R.string.search_starred); setSingleLine(); setTextColor(col(R.color.text)); setHintTextColor(col(R.color.muted))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: Editable?) { query = e?.toString() ?: ""; render() }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), 0, dp(16), dp(4)) })
        list = ListView(this).apply { divider = null; selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT) }
        empty = label(getString(R.string.starred_empty), R.color.muted, 14f).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(dp(32), dp(48), dp(32), 0); visibility = View.GONE }
        root.addView(android.widget.FrameLayout(this).apply { addView(list); addView(empty) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        fitSystemBars(root)
    }

    override fun onResume() { super.onResume(); app.addListener(listener); render() }
    override fun onPause() { app.removeListener(listener); super.onPause() }

    private fun matches(m: Sms, withName: Boolean): Boolean = query.isBlank() ||
        Search.fold(m.body).contains(Search.fold(query)) ||
        (withName && Search.matches(query, listOfNotNull(app.state.name(m.addr)), listOf(m.addr)))

    private fun render() {
        val s = app.state
        val first = list.firstVisiblePosition; val top = list.getChildAt(0)?.top ?: 0
        val t = thread
        if (t == null) {
            val chats = Starred.chats(s.sms, s.starred) { matches(it, withName = true) }
            count.text = chats.sumOf { it.count }.toString()
            empty.visibility = if (chats.isEmpty() && query.isBlank()) View.VISIBLE else View.GONE
            list.adapter = Rows(chats) { c -> chatRow(c, last = c === chats.lastOrNull()) }
            list.setOnItemClickListener { _, _, pos, _ -> chats.getOrNull(pos)?.let { c ->
                startActivity(Intent(this, StarredActivity::class.java).putExtra(EXTRA_THREAD, c.thread).putExtra(EXTRA_ADDRESS, c.address)) } }
            list.setOnItemLongClickListener(null)
        } else {
            val texts = Starred.texts(s.sms, s.starred, t) { matches(it, withName = false) }
            count.text = texts.size.toString()
            // The last one unstarred: back to the list of chats.
            if (texts.isEmpty() && query.isBlank()) { finish(); return }
            list.adapter = Rows(texts) { m -> textRow(m) }
            list.setOnItemClickListener { _, _, pos, _ -> texts.getOrNull(pos)?.let(::open) }
            list.setOnItemLongClickListener { _, _, pos, _ -> texts.getOrNull(pos)?.let(::menu); true }
        }
        list.setSelectionFromTop(first, top)
    }

    /** A chat with starred texts: picture, name, its newest starred text, the date and a gold count. */
    private fun chatRow(c: Starred.Chat, last: Boolean): View {
        val s = app.state
        val name = s.name(c.address)
        val who = name ?: app.format(c.address)
        val photo = s.photo(c.address)
        val pic = if (name == null && photo == null) android.widget.ImageView(this).apply {
            setImageDrawable(icon(R.drawable.ic_person, R.color.muted, 26)); scaleType = android.widget.ImageView.ScaleType.CENTER
            background = round(R.color.surface, 48)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) }
        } else avatar(who, photo, 48).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(14) }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12)); minimumHeight = dp(76)
            addView(pic)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(who, R.color.text, 16f, bold = true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, 0, 0, dp(2)) })
                addView(label(c.newest.body.replace('\n', ' '), R.color.muted, 14f).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, 0, 0, 0)
                    textDirection = View.TEXT_DIRECTION_FIRST_STRONG; textAlignment = View.TEXT_ALIGNMENT_TEXT_START })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.END; setPaddingRelative(dp(10), 0, 0, 0)
                addView(label(listDate(c.newest.date), R.color.muted, 12f).apply { maxLines = 1; setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(-2, -2))
                addView(label(c.count.toString(), R.color.warn, 14f, bold = true).apply {
                    setPadding(0, dp(6), 0, 0)
                    setCompoundDrawablesRelative(icon(R.drawable.ic_star, R.color.warn, 14), null, null, null); compoundDrawablePadding = dp(3)
                }, LinearLayout.LayoutParams(-2, -2))
            }, LinearLayout.LayoutParams(-2, -2))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.RippleDrawable(rippleColor(R.color.text), null, null)
            addView(line)
            if (!last) addView(View(context).apply { setBackgroundColor(android.content.res.ColorStateList.valueOf(col(R.color.muted)).withAlpha(0x55).defaultColor) },
                LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(16 + 48 + 14) })
            contentDescription = listOf(who, getString(R.string.starred) + " " + c.count, c.newest.body, listDate(c.newest.date)).joinToString(", ")
        }
    }

    /** One starred text: its bubble (own texts in teal) and, beside it, the date. */
    private fun textRow(m: Sms): View {
        val mine = m.box != 1
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false
            gravity = (if (mine) Gravity.END else Gravity.START) or Gravity.BOTTOM
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = android.graphics.drawable.RippleDrawable(rippleColor(R.color.text), null, null)
            val date = label(listDate(m.date), R.color.muted, 11f).apply { setPaddingRelative(dp(8), 0, dp(8), dp(2)); maxLines = 1 }
            val body = label(m.body, if (mine) R.color.background else R.color.text, 16f).apply {
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(col(if (mine) R.color.accent else R.color.surface)) }
                textDirection = View.TEXT_DIRECTION_FIRST_STRONG; textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                maxWidth = (resources.displayMetrics.widthPixels * 0.72).toInt()
            }
            if (mine) addView(date)
            addView(body, LinearLayout.LayoutParams(-2, -2))
            if (!mine) addView(date)
        }
    }

    /** Opens the text in its conversation, scrolled to it. */
    private fun open(m: Sms) {
        startActivity(Intent(this, ThreadActivity::class.java).putExtra(ThreadActivity.EXTRA_THREAD, m.thread)
            .putExtra(ThreadActivity.EXTRA_ADDRESS, m.addr).putExtra(ThreadActivity.EXTRA_FOCUS, m.id))
    }

    private fun menu(m: Sms) {
        AlertDialog.Builder(this).setItems(arrayOf(getString(R.string.unstar), getString(R.string.copy))) { _, w ->
            when (w) {
                0 -> app.state.setStarred(listOf(m.id), false)
                1 -> {
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("text", m.body))
                    if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(this, R.string.copied, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.show()
    }

    companion object { const val EXTRA_THREAD = "thread"; const val EXTRA_ADDRESS = "address" }
}
