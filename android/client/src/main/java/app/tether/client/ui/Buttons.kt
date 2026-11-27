package app.tether.client.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.tether.client.R

/**
 * The client's buttons. They are drawn here and do not use the platform `Button` style, which brings its own minimum
 * width, shadow and capitals. Every button:
 *  - ripples when pressed (bounded to its shape), and looks disabled when disabled,
 *  - is at least 48dp tall (Android's touch target) and wraps its text, so large font sizes and Persian never clip,
 *  - says "button" (and its label, or a description for icon-only ones) to TalkBack.
 * Colours come from the palette only (colors.xml); pressed and selected tints are palette colours with alpha.
 */
enum class Look { FILLED, TONAL, OUTLINED, TEXT }

/** A TextView that TalkBack announces as a button and that fades when disabled. */
class ButtonView(ctx: Context) : TextView(ctx) {
    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name
    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); alpha = if (enabled) 1f else 0.38f }
}

/** A frame that TalkBack announces as a button (icon buttons, keypad keys, call actions) and that fades when disabled. */
class ButtonFrame(ctx: Context) : FrameLayout(ctx) {
    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name
    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); alpha = if (enabled) 1f else 0.38f }
}

/** The pressed-state colour: [colorId] at about 20 % (on a filled button its text colour, so the ripple shows). */
fun Context.rippleColor(colorId: Int): ColorStateList = ColorStateList.valueOf(col(colorId)).withAlpha(0x33)

/** A shape with a ripple confined to it. [fill] null = transparent; [stroke] draws an outline. */
fun Context.pressable(fill: Int?, rippleId: Int, radiusDp: Float = 100f, oval: Boolean = false, stroke: Int? = null): Drawable {
    fun shape(color: Int?) = GradientDrawable().apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(1) * radiusDp
        color?.let { setColor(it) }
        stroke?.let { setStroke(dp(1), it) }
    }
    val mask = GradientDrawable().apply { if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(1) * radiusDp; setColor(col(R.color.text)) }
    return RippleDrawable(rippleColor(rippleId), shape(fill), mask)
}

/** A palette icon tinted to [colorId], [sizeDp] square (for compound drawables, which need bounds). */
fun Context.icon(iconId: Int, colorId: Int, sizeDp: Int = 24): Drawable =
    getDrawable(iconId)!!.mutate().apply { setTint(col(colorId)); setBounds(0, 0, dp(sizeDp), dp(sizeDp)) }

/**
 * A text button, optionally with a leading icon.
 *  FILLED: [colorId] background, dark text (the main action of a screen or card).
 *  TONAL: a faint [colorId] tint, [colorId] text (a choice that is not selected, a quieter action); it shows on
 *         both the page and a card.
 *  OUTLINED: an outline in [colorId] (a secondary action).
 *  TEXT: no outline (a low-emphasis action in a row of others).
 */
fun Context.button(t: CharSequence, look: Look = Look.FILLED, colorId: Int = R.color.accent, iconId: Int? = null,
                   onClick: () -> Unit) = ButtonView(this).apply {
    text = t; textSize = 15f; isAllCaps = false; gravity = Gravity.CENTER
    setTypeface(typeface, android.graphics.Typeface.BOLD)
    maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    val fg = if (look == Look.FILLED) R.color.background else colorId
    setTextColor(col(fg))
    background = when (look) {
        Look.FILLED -> pressable(col(colorId), R.color.background)
        Look.TONAL -> pressable(ColorStateList.valueOf(col(colorId)).withAlpha(0x2E).defaultColor, colorId)
        Look.OUTLINED -> pressable(null, colorId, stroke = col(colorId))
        Look.TEXT -> pressable(null, colorId)
    }
    iconId?.let { setCompoundDrawablesRelative(icon(it, fg, 18), null, null, null); compoundDrawablePadding = dp(8) }
    minHeight = dp(48); minimumHeight = dp(48)
    setPadding(dp(if (look == Look.TEXT) 12 else 20), dp(8), dp(if (look == Look.TEXT) 12 else 20), dp(8))
    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6); marginEnd = dp(8) }
    isClickable = true; isFocusable = true
    setOnClickListener { onClick() }
}

/** The older call shape used across the screens: filled, or outlined when [secondary]. */
fun Context.pill(t: String, secondary: Boolean = false, colorId: Int = R.color.accent, onClick: () -> Unit) =
    button(t, if (secondary) Look.OUTLINED else Look.FILLED, colorId, onClick = onClick)

/**
 * An icon-only button: a 48dp round touch target with a borderless ripple. [description] is read by TalkBack and shown
 * as a tooltip on a long press (unless [onLong] takes the long press).
 */
fun Context.iconButton(iconId: Int, description: String, colorId: Int = R.color.text, sizeDp: Int = 48,
                       onLong: (() -> Unit)? = null, onClick: () -> Unit) = ButtonFrame(this).apply {
    contentDescription = description
    background = RippleDrawable(rippleColor(colorId), null, null)   // no mask: a round ripple, borderless
    addView(ImageView(context).apply { setImageDrawable(icon(iconId, colorId)) },
        FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    isClickable = true; isFocusable = true
    setOnClickListener { onClick() }
    if (onLong != null) setOnLongClickListener { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onLong(); true }
    else tooltipText = description
}

/**
 * A round action with its label underneath, as on a phone's call screen.
 * [checked] null = a plain action in [colorId] (answer, decline, end call: filled, white-on-colour);
 * true / false = a switch (mute, speaker, keypad, hold): on is filled with the accent, off is the dark grey surface.
 */
fun Context.roundAction(iconId: Int, text: String, colorId: Int = R.color.accent, checked: Boolean? = null,
                        sizeDp: Int = 64, onClick: () -> Unit): LinearLayout {
    val fill = when (checked) { null -> colorId; true -> R.color.accent; false -> R.color.surface }
    val fg = if (checked == false) R.color.text else R.color.background
    val circle = ButtonFrame(this).apply {
        background = pressable(col(fill), fg, oval = true)
        addView(ImageView(context).apply { setImageDrawable(icon(iconId, fg)) },
            FrameLayout.LayoutParams(dp(sizeDp * 2 / 5), dp(sizeDp * 2 / 5), Gravity.CENTER))
        contentDescription = text
        isClickable = true; isFocusable = true
        if (checked != null) {
            // TalkBack reads "Mute, switch, on".
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.isCheckable = true; info.isChecked = checked
                }
            }
        }
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        addView(circle, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
        addView(label(text, R.color.muted, 13f).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO   // the circle says it already
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
    }
}

/** Round actions sharing the width equally, centred, on one line. */
fun Context.actionRow(vararg actions: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER
    // Even margins above and below: a centred row moves a child with only a top margin down past its own bottom edge.
    actions.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(6); bottomMargin = dp(6) }) }
}

/** Makes a whole card or banner pressable: a ripple over its [fill] colour with [radiusDp] corners. */
fun View.pressableCard(fill: Int, radiusDp: Float = 10f, onClick: () -> Unit) {
    background = context.pressable(context.col(fill), R.color.text, radiusDp)
    isClickable = true; isFocusable = true
    setOnClickListener { onClick() }
}

/**
 * A keypad key: the digit, its letters underneath (an empty line for keys without letters, so the digits line up; no
 * line at all when [letters] is null, as on the call screen's pad), a round ripple on the dark grey surface and a
 * key-press haptic.
 * [onLong] (optional) takes a long press, e.g. 0 → "+".
 */
fun Context.dialKey(digit: String, letters: String?, sizeDp: Int = 76, onLong: (() -> Unit)? = null, onClick: () -> Unit) = ButtonFrame(this).apply {
    background = pressable(col(R.color.surface), R.color.text, oval = true)
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(label(digit, size = sizeDp / 2.8f).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, 0, 0, 0) })
        // The letters' line is kept (empty, invisible) on keys without letters (1, *, #) when the pad has letters at
        // all ([letters] not null), so every digit sits at the same height, as on a phone's own dialer.
        if (letters != null) addView(label(letters.ifEmpty { " " }, R.color.muted, 10f).apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, 0, 0, 0); letterSpacing = 0.1f
            if (letters.isEmpty()) visibility = View.INVISIBLE
        })
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    contentDescription = if (letters.isNullOrEmpty()) digit else "$digit, $letters"
    isClickable = true; isFocusable = true
    setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
    if (onLong != null) setOnLongClickListener { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onLong(); true }
}

/**
 * One tab of the bottom bar: the icon in a pill that lights up when selected, the label underneath, and a count badge
 * (missed calls, unread texts) on the icon. TalkBack reads "Messages, 3 new, tab, selected".
 */
class TabView(ctx: Context, iconId: Int, private val name: String, onClick: () -> Unit) : LinearLayout(ctx) {
    private val pill = FrameLayout(ctx)
    private val iconView = ImageView(ctx)
    private val text = ctx.label(name, R.color.muted, 12f)
    private val badge = ctx.label("", R.color.background, 10f, bold = true)
    private val iconRes = iconId
    private var selectedNow = false

    init {
        orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, ctx.dp(2), 0, ctx.dp(2)); minimumHeight = ctx.dp(56)
        background = RippleDrawable(ctx.rippleColor(R.color.text), null, null)
        pill.addView(iconView, FrameLayout.LayoutParams(ctx.dp(24), ctx.dp(24), Gravity.CENTER))
        badge.apply {
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(ctx.dp(4), 0, ctx.dp(4), 0); minWidth = ctx.dp(16)
            background = GradientDrawable().apply { cornerRadius = ctx.dp(8).toFloat(); setColor(ctx.col(R.color.bad)) }
        }
        pill.addView(badge, FrameLayout.LayoutParams(-2, ctx.dp(16), Gravity.TOP or Gravity.END).apply { topMargin = ctx.dp(0); marginEnd = ctx.dp(10) })
        addView(pill, LayoutParams(ctx.dp(56), ctx.dp(30)))
        text.apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        addView(text, LayoutParams(-2, -2).apply { topMargin = ctx.dp(2) })
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
        update(false, 0)
    }

    fun update(selected: Boolean, badge: Int) {
        selectedNow = selected
        val c = if (selected) R.color.accent else R.color.muted
        iconView.setImageDrawable(context.icon(iconRes, c))
        text.setTextColor(context.col(if (selected) R.color.text else R.color.muted))
        text.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        pill.background = if (selected) GradientDrawable().apply {
            cornerRadius = context.dp(15).toFloat(); setColor(context.col(R.color.accent)); alpha = 0x38
        } else null
        this.badge.visibility = if (badge > 0) View.VISIBLE else View.GONE
        this.badge.text = if (badge > 99) "99+" else badge.toString()
        contentDescription = if (badge > 0) context.getString(R.string.tab_badge, name, badge) else name
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.TabWidget"
    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info); info.isSelected = selectedNow
    }
}

/** A filled round icon button (send): [colorId] circle, dark icon. */
fun Context.fab(iconId: Int, description: String, colorId: Int = R.color.accent, sizeDp: Int = 48, onClick: () -> Unit) = ButtonFrame(this).apply {
    background = pressable(col(colorId), R.color.background, oval = true)
    addView(ImageView(context).apply { setImageDrawable(icon(iconId, R.color.background, 22)) }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
    contentDescription = description; tooltipText = description
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    isClickable = true; isFocusable = true
    setOnClickListener { onClick() }
}
