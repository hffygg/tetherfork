package app.tether.client.model

/**
 * The Android phone's low-battery alert on this phone: at the chosen level and again at every 5 % below it, while the
 * Android phone is not charging. Pure, so the steps are unit-tested.
 */
object BatteryAlert {
    /** The levels offered (0 = off). */
    val CHOICES = listOf(0, 10, 15, 20, 25, 30, 40, 50)
    const val DEFAULT = 20

    /**
     * The alert step [level] is in: [threshold], [threshold] - 5, - 10 … (the highest step at or above [level]), or
     * null when there is no alert (off, charging, unknown level, or above the threshold).
     */
    fun step(level: Int, charging: Boolean, threshold: Int): Int? {
        if (threshold <= 0 || charging || level < 0 || level > threshold) return null
        return threshold - (threshold - level) / 5 * 5
    }

    /** Whether to alert now: a step lower than the last one alerted ([last] null: none since the last charge or reset). */
    fun shouldAlert(step: Int?, last: Int?): Boolean = step != null && (last == null || step < last)
}
