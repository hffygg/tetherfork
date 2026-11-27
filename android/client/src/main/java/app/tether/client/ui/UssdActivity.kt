package app.tether.client.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.tether.client.R
import app.tether.client.model.Problem
import app.tether.client.model.UssdCodes

/**
 * Carrier codes (USSD) such as *123#, run on the Android phone's SIM: choose the SIM, type the code, send, read the
 * carrier's answer. One answer per code (Android's USSD API); a menu is answered with a new code that carries the
 * choice (*123# then 2 sends *123*2#). Recent codes are kept on this phone for one tap.
 */
class UssdActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var code: EditText
    private lateinit var reply: EditText
    private lateinit var simRow: LinearLayout
    private lateinit var answerBox: LinearLayout
    private lateinit var recentBox: LinearLayout
    private lateinit var replySends: TextView
    private var sub: Int? = null
    /** What the answer area was built for: rebuilt only when it changes (status pushes must not take the keyboard away). */
    private var shown: Any? = null
    private val listener: () -> Unit = { render() }
    private val prefs get() = getSharedPreferences("ussd", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sub = intent.takeIf { it.hasExtra(EXTRA_SUB) }?.getIntExtra(EXTRA_SUB, 0) ?: app.state.defaultSub
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        root.addView(label(getString(R.string.ussd_title), size = 22f, bold = true))
        root.addView(label(getString(R.string.ussd_note), R.color.muted, 13f).apply { setPadding(0, dp(4), 0, dp(8)) })
        simRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(simRow)
        code = EditText(this).apply {
            hint = getString(R.string.ussd_hint); textSize = 24f; setSingleLine()
            setTextColor(col(R.color.text)); setHintTextColor(col(R.color.muted))
            inputType = InputType.TYPE_CLASS_PHONE; imeOptions = EditorInfo.IME_ACTION_SEND
            textDirection = View.TEXT_DIRECTION_LTR; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setText(intent.getStringExtra(EXTRA_CODE)?.let(UssdCodes::clean) ?: "")
            setSelection(text.length)
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
        }
        root.addView(code, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(button(getString(R.string.ussd_send), iconId = R.drawable.ic_call) { send() })
        answerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(answerBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        reply = EditText(this).apply {
            hint = getString(R.string.ussd_reply_hint); textSize = 20f; setSingleLine()
            setTextColor(col(R.color.text)); setHintTextColor(col(R.color.muted))
            inputType = InputType.TYPE_CLASS_PHONE; imeOptions = EditorInfo.IME_ACTION_SEND
            textDirection = View.TEXT_DIRECTION_LTR
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { sendReply(); true } else false }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) { updateSends() }
            })
        }
        replySends = label("", R.color.muted, 13f).apply { textDirection = View.TEXT_DIRECTION_LTR }
        recentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(recentBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val scroll = ScrollView(this).apply { setBackgroundColor(col(R.color.background)); addView(root) }
        setContentView(scroll)
        fitSystemBars(scroll)
    }

    override fun onResume() { super.onResume(); app.addListener(listener); render() }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // From a call button with a code typed: send it at once (once, not again after a rotation).
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_SEND, false)) send()
    }
    override fun onPause() { app.removeListener(listener); super.onPause() }

    private fun send(c: String = UssdCodes.clean(code.text.toString())) {
        if (!UssdCodes.valid(c)) { app.problem(Problem.OTHER, getString(R.string.ussd_invalid)); render(); return }
        if (!app.link.isConnected) { app.problem(Problem.NOT_CONNECTED); render(); return }
        code.setText(c); code.setSelection(c.length)
        reply.setText("")
        prefs.edit().putString(PREF_RECENT, UssdCodes.remember(recent(), c).joinToString(",")).apply()
        app.state.sendUssd(c, sub)
    }

    /**
     * A menu reply: typed into the carrier's menu on the Android phone when it is open there ([UssdAnswer.session]);
     * else sent as a new code that carries the choice (*123# then 2 sends *123*2#).
     */
    private fun sendReply() {
        val a = app.state.ussdAnswer ?: return
        val choice = reply.text.toString().filter { it.isDigit() || it == '*' || it == '#' || it == '+' }
        if (choice.isEmpty()) return
        if (a.session) { reply.setText(""); app.state.replyUssd(choice); return }
        UssdCodes.reply(a.code, choice)?.let { send(it) }
    }

    private fun recent(): List<String> = prefs.getString(PREF_RECENT, "").orEmpty().split(',').filter(UssdCodes::valid)

    private fun render() {
        val s = app.state
        // The SIM (dual-SIM Android phones): one choice per SIM, the chosen one filled.
        simRow.removeAllViews()
        if (s.sims.size > 1) {
            if (s.sims.none { it.sub == sub }) sub = s.defaultSub ?: s.sims.first().sub
            simRow.addView(evenRow(*s.sims.map { sim ->
                button(sim.name.ifEmpty { getString(R.string.sim_n, sim.slot + 1) }, if (sim.sub == sub) Look.FILLED else Look.TONAL) {
                    sub = sim.sub; render()
                }
            }.toTypedArray()))
        }
        // The answer, or that it is on its way (rebuilt only when that changes).
        val key = listOf(s.problem, s.ussdPending, s.ussdAnswer)
        if (key != shown) { shown = key; renderAnswer() }
        renderRecent()
    }

    /** "Sends *123*2#" under the reply field, as the choice is typed. */
    private fun updateSends() {
        // An open menu takes the choice itself: nothing to explain.
        val next = app.state.ussdAnswer?.takeIf { !it.session }?.code?.let { UssdCodes.reply(it, reply.text.toString()) }
        replySends.text = next?.let { getString(R.string.ussd_reply_sends, it) } ?: ""
    }

    private fun renderAnswer() {
        val s = app.state
        answerBox.removeAllViews()
        s.problem?.let { p ->
            answerBox.addView(label(describe(p), R.color.warn, 14f).apply { setOnClickListener { s.problem = null; render() } })
        }
        val pending = s.ussdPending
        val a = s.ussdAnswer
        when {
            pending != null -> answerBox.addView(label(getString(R.string.ussd_waiting, pending), R.color.muted, 15f))
            a != null -> {
                val box = card()
                box.addView(label(a.code, R.color.accent, 13f, bold = true).apply { textDirection = View.TEXT_DIRECTION_LTR })
                if (a.ok) box.addView(label(a.text.orEmpty(), size = 17f).apply { setTextIsSelectable(true); setPadding(0, dp(6), 0, dp(6)) })
                else box.addView(label(if (a.err == Problem.NO_ANSWER) getString(R.string.err_no_answer) else a.err ?: getString(R.string.ussd_failed), R.color.warn, 15f))
                // A menu: answer it with the choice (sent as a new code that carries it).
                // A menu: open on the Android phone (answered there), or recognised in a final answer (a new code).
                if (a.ok && (a.session || UssdCodes.isMenu(a.text))) {
                    (reply.parent as? LinearLayout)?.removeView(reply)
                    (replySends.parent as? LinearLayout)?.removeView(replySends)
                    box.addView(reply)
                    box.addView(row(*listOfNotNull(
                        button(getString(R.string.ussd_reply)) { sendReply() },
                        if (a.session) button(getString(android.R.string.cancel), Look.TEXT) { app.state.cancelUssd() } else null,
                        replySends,
                    ).toTypedArray()))
                    updateSends()
                }
                answerBox.addView(box)
            }
        }
    }

    /** Recent codes: one tap sends again. */
    private fun renderRecent() {
        recentBox.removeAllViews()
        val r = recent()
        if (r.isNotEmpty()) {
            recentBox.addView(label(getString(R.string.ussd_recent).uppercase(), R.color.accent, 12f, bold = true))
            for (c in r) recentBox.addView(button(c, Look.TEXT) { code.setText(c); send(c) }.apply {
                textDirection = View.TEXT_DIRECTION_LTR; gravity = Gravity.START or Gravity.CENTER_VERTICAL
            })
        }
    }

    companion object { const val EXTRA_CODE = "code"; const val EXTRA_SUB = "sub"; const val EXTRA_SEND = "send"; private const val PREF_RECENT = "recent" }
}
