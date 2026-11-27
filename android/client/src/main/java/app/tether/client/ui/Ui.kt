package app.tether.client.ui

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.tether.client.ClientApp
import app.tether.client.R
import app.tether.client.model.Problem

/** Extra padding for the window's edges: system bars and display cutout, and at the bottom the keyboard when it is
 *  taller than the navigation bar (both sit at the bottom, so they never add up). Left, top, right, bottom. */
fun barPadding(bars: IntArray, imeBottom: Int): IntArray =
    intArrayOf(bars[0], bars[1], bars[2], maxOf(bars[3], imeBottom))

/**
 * Draws [root] edge to edge and pads it by the system bars, the cutout and the keyboard (#12). Android 15+ draws apps
 * with targetSdk 35+ behind the bars and ignores adjustResize, so text went behind the navigation bar and the
 * keyboard covered the text field. The same from Android 11 on, so every version behaves alike; Android 10 keeps
 * its own fitting (and adjustResize).
 */
fun Activity.fitSystemBars(root: View) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    window.setDecorFitsSystemWindows(false)
    val base = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
    root.setOnApplyWindowInsetsListener { v, insets ->
        val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val p = barPadding(intArrayOf(b.left, b.top, b.right, b.bottom), insets.getInsets(WindowInsets.Type.ime()).bottom)
        v.setPadding(base[0] + p[0], base[1] + p[1], base[2] + p[2], base[3] + p[3])
        WindowInsets.CONSUMED
    }
    root.requestApplyInsets()
}

/** Small helpers for the app's plain framework views (the Android phone's app is built the same way). */
fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.col(id: Int) = getColor(id)

/**
 * A text in the app's style. It is aligned to the start of the view's own direction (right in Persian, also for a
 * Latin name), which overrides `gravity`: a centred label also sets `textAlignment = TEXT_ALIGNMENT_CENTER`, else it
 * is drawn at the start of its box (keypad digits sat left of their letters).
 */
fun Context.label(t: CharSequence, colorId: Int = R.color.text, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
    text = t; textSize = size; setTextColor(col(colorId)); setPadding(0, dp(2), 0, dp(2))
    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

fun Context.round(colorId: Int, sizeDp: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col(colorId)); setSize(dp(sizeDp), dp(sizeDp)) }

fun Context.card() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(12), dp(16), dp(12))
    background = GradientDrawable().apply { setColor(col(R.color.surface)); cornerRadius = dp(16).toFloat() }
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
}

/**
 * A horizontal row. A centred LinearLayout moves a child with uneven top and bottom margins down by half their
 * difference, past the row's bottom edge, where it is cut off: a button's 6dp top margin (its spacing in a column) lost
 * the bottom of the button. In a row the margin is split evenly above and below. No baseline alignment either.
 */
fun Context.row(vararg views: View, gravity: Int = Gravity.CENTER_VERTICAL) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; this.gravity = gravity
    views.forEach { v ->
        (v.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            val m = (lp.topMargin + lp.bottomMargin) / 2; lp.topMargin = m; lp.bottomMargin = m
        }
        addView(v)
    }
}

fun View.weight(w: Float = 1f): View { layoutParams = LinearLayout.LayoutParams(0, -2, w); return this }

/** A row of buttons sharing the width equally (one line each), so long labels (Persian, "Bluetooth") never push a
 *  button off the screen (#12). */
fun Context.evenRow(vararg buttons: TextView) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
    buttons.forEachIndexed { i, b ->
        // One line, but not setSingleLine(): that also scrolls the text sideways, which with centred text drew a SIM's
        // name outside its button.
        b.maxLines = 1; b.ellipsize = android.text.TextUtils.TruncateAt.END; b.setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply {
            topMargin = dp(3); bottomMargin = dp(3); marginStart = if (i == 0) 0 else dp(4); marginEnd = if (i == buttons.lastIndex) 0 else dp(4)
        })
    }
}

/** A contact's picture (base64 JPEG from the Android phone) or a circle with the initial. */
fun Context.avatar(name: String, photo: String?, sizeDp: Int = 40): View {
    photo?.let { p ->
        runCatching { Base64.decode(p, Base64.DEFAULT) }.getOrNull()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let { bmp ->
            return ImageView(this).apply {
                setImageBitmap(bmp); clipToOutline = true
                background = round(R.color.surface, sizeDp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { marginEnd = dp(12) }
            }
        }
    }
    return TextView(this).apply {
        text = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"
        gravity = Gravity.CENTER; textSize = sizeDp / 2.6f; setTextColor(col(R.color.background))
        background = round(R.color.accent, sizeDp)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { marginEnd = dp(12) }
    }
}

fun Context.time(ms: Long): String = if (DateUtils.isToday(ms)) DateUtils.formatDateTime(this, ms, DateUtils.FORMAT_SHOW_TIME)
    else DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

/** The start of [ms]'s local day (groups calls and texts by day). */
fun dayStart(ms: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = ms; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

/** "Today", "Yesterday", else the weekday and date ("Thursday, November 19"; with the year when not this year). */
fun Context.dayName(day: Long): String {
    val today = dayStart(System.currentTimeMillis())
    val yesterday = dayStart(today - 12 * 3600_000L)
    if (day == today) return getString(R.string.recents_today)
    if (day == yesterday) return getString(R.string.recents_yesterday)
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) ==
        java.util.Calendar.getInstance().apply { timeInMillis = day }.get(java.util.Calendar.YEAR)
    return DateUtils.formatDateTime(this, day, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
        if (thisYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR)
}

/** A conversation's date: the time today, "Yesterday", the weekday this week, else the date (the year when not this one). */
fun Context.listDate(ms: Long): String {
    val day = dayStart(ms); val today = dayStart(System.currentTimeMillis())
    return when {
        day == today -> android.text.format.DateUtils.formatDateTime(this, ms, android.text.format.DateUtils.FORMAT_SHOW_TIME)
        today - day < 6 * 86_400_000L + 3_600_000L && day != dayStart(today - 12 * 3600_000L) ->
            android.text.format.DateUtils.formatDateTime(this, ms, android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY or android.text.format.DateUtils.FORMAT_ABBREV_WEEKDAY)
        day == dayStart(today - 12 * 3600_000L) -> getString(R.string.recents_yesterday)
        java.util.Calendar.getInstance().apply { timeInMillis = ms }.get(java.util.Calendar.YEAR) == java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) ->
            android.text.format.DateUtils.formatDateTime(this, ms, android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_NO_YEAR or android.text.format.DateUtils.FORMAT_ABBREV_MONTH)
        else -> android.text.format.DateUtils.formatDateTime(this, ms, android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_NUMERIC_DATE)
    }
}

/** The words for a [Problem] (its detail comes from the Android phone as is). */
fun Context.describe(p: Problem): String {
    val base = when (p.kind) {
        Problem.NOT_CONNECTED -> getString(R.string.err_not_connected)
        Problem.NOT_DELETED -> getString(R.string.err_not_deleted)
        Problem.NOT_SENT -> getString(R.string.err_not_sent)
        Problem.CONTACT_NOT_SAVED -> getString(R.string.err_contact_not_saved)
        Problem.SEND_LATER -> getString(R.string.err_send_later)
        Problem.TOO_LONG -> getString(R.string.err_too_long)
        Problem.CALL_FAILED -> getString(R.string.err_call_failed)
        Problem.NO_ANSWER -> getString(R.string.err_no_answer)
        Problem.PAIRING_EXPIRED -> getString(R.string.err_pairing_expired)
        Problem.UNKNOWN_DEVICE -> getString(R.string.err_unknown_device)
        else -> p.detail ?: ""
    }
    return if (p.detail != null && p.kind != Problem.OTHER) "$base: ${p.detail}" else base
}

/** The line for a microphone warning (call screen and main screen). */
fun micText(w: app.tether.client.calls.MicWarning): Int = when (w) {
    app.tether.client.calls.MicWarning.DENIED -> R.string.mic_denied
    app.tether.client.calls.MicWarning.UNAVAILABLE -> R.string.mic_unavailable
    app.tether.client.calls.MicWarning.SILENT -> R.string.mic_silent
}

/** A list adapter from a list and a row builder. */
class Rows<T>(private val items: List<T>, private val build: (T) -> View) : BaseAdapter() {
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?) = build(items[position])
}

val Activity.app get() = application as ClientApp
