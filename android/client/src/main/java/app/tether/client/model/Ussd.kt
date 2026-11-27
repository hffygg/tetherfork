package app.tether.client.model

/**
 * Carrier (USSD) codes such as *123#, sent through the Android phone (`ussd.send` / `ussd.result`, protocol §5): one
 * answer per code, as Android's USSD API gives. A carrier's menu is answered by sending a new code with the choice
 * in it (*123# then 2 → *123*2#), which most carriers accept. Pure.
 */
object UssdCodes {
    /** The codes the Android phone accepts (its `UssdCode`): * or #, then digits, * and #, ending in #. */
    private val code = Regex("^[*#][0-9*#]{1,30}#$")
    private val menuLine = Regex("^\\s*\\d{1,2}\\s*[.):\\-]")

    fun valid(c: String) = code.matches(c)

    /** What was typed, as a code: spaces and other characters out. */
    fun clean(c: String) = c.filter { it.isDigit() || it == '*' || it == '#' }

    /** A menu choice as a new code: *123# + "2" → *123*2#; null when that is no code (empty choice, too long). */
    fun reply(c: String, choice: String): String? {
        val pick = choice.filter { it.isDigit() || it == '*' }.trim('*')
        if (pick.isEmpty() || !valid(c)) return null
        return (c.removeSuffix("#") + "*" + pick + "#").takeIf(::valid)
    }

    /** Whether an answer looks like a menu (two or more lines that start with a number: "1. Balance", "2) Packages"). */
    fun isMenu(text: String?) = text != null && text.lines().count { menuLine.containsMatchIn(it) } >= 2

    /** The recent codes with [c] first, without repeats, at most [max]. */
    fun remember(recent: List<String>, c: String, max: Int = 6) = (listOf(c) + recent.filter { it != c }).take(max)
}
