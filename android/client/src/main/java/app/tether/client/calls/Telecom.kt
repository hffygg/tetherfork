package app.tether.client.calls

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import app.tether.client.ClientApp
import app.tether.client.R
import app.tether.client.model.Call
import app.tether.client.model.Problem
import app.tether.client.model.UssdCodes
import java.util.UUID

/**
 * The calls of the Android phone as self-managed Telecom calls on this phone: Telecom routes their audio (earpiece,
 * speaker, wired and Bluetooth headsets, the car) and shows them to Bluetooth devices; the app shows its own call
 * screen and rings itself. [CallTracker] decides; this applies its decisions to Telecom connections.
 */
class TelecomBridge(private val app: ClientApp) : CallSink {
    private val tm = app.getSystemService(TelecomManager::class.java)
    val account = PhoneAccountHandle(ComponentName(app, TetherConnectionService::class.java), "tether")
    private val conns = HashMap<String, TetherConnection>()
    /** State for calls whose Telecom connection does not exist yet (Telecom creates it asynchronously). */
    private val pending = HashMap<String, Call>()
    private val pendingActive = HashSet<String>()
    private val endedEarly = HashMap<String, EndReason>()
    private val main = Handler(Looper.getMainLooper())
    var audioState: CallAudioState? = null; private set
    private val prefs = app.getSharedPreferences("calls", Context.MODE_PRIVATE)
    /** Numbers of calls that rang in the Phone app and were not answered, with when: the Phone app shows them as
     *  missed calls itself, so this app does not notify them a second time. */
    private val missedInPhoneApp = HashMap<String, Long>()
    /** The last state of each call from the Android phone, to hand a call over to the Phone app ([moveToPhoneApp]). */
    private val lastCall = HashMap<String, Call>()

    fun register() {
        runCatching {
            tm.registerPhoneAccount(PhoneAccount.builder(account, app.getString(R.string.app_name))
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL))
                .build())
        }.onFailure { app.journal.add("phone account not registered: ${it.message}") }
        syncPhoneAccounts()
    }

    // ------------------------------------------------------------------ the Phone app (managed calling accounts)

    /**
     * "Use the Phone app for calls" (Link tab), on by default. When on, calls ring and run in this phone's own Phone
     * app through a calling account per SIM of the Android phone ("Tether · Work"): its call screen, ringtone, keypad,
     * contacts and call history, as for a SIM of its own. The protocol does not change: only how the calls are shown
     * here. A call still uses this app's own call screen while no such account is turned on in the Phone app's calling
     * accounts (Android asks the user to do that once), so calls never fail because of the setting.
     */
    var usePhoneApp: Boolean
        get() = prefs.getBoolean(PREF_PHONE_APP, true)
        set(v) { prefs.edit().putBoolean(PREF_PHONE_APP, v).apply(); syncPhoneAccounts(); app.changed() }

    fun phoneHandle(sub: Int?) = handleOf(if (sub == null) PHONE_ID else "$PHONE_ID-$sub")
    private fun handleOf(id: String) = PhoneAccountHandle(ComponentName(app, TetherPhoneConnectionService::class.java), id)

    /** The SIM a Phone-app account calls with (null: the plain account, the Android phone's choice). */
    fun subOf(h: PhoneAccountHandle?): Int? = h?.id?.removePrefix("$PHONE_ID-")?.takeIf { it != h.id }?.toIntOrNull()

    /** The Phone-app accounts registered now (their ids, kept across restarts). */
    private fun registeredIds(): Set<String> = prefs.getStringSet(PREF_PHONE_IDS, emptySet()).orEmpty()

    private var syncedKey: String? = null

    /**
     * Registers the Phone-app accounts: one per SIM of a dual-SIM Android phone (so the Phone app asks which SIM), one
     * ("Tether") otherwise. An account, once registered, stays registered while the setting is on: Android forgets that
     * the user turned an account on when it is removed, and the SIM phone's status can briefly list fewer SIMs (while
     * it reconnects or restarts, after pairing again), which removed the accounts and sent calls back to this app's own
     * screen. Only the plain account is removed, once there are per-SIM ones; all go when the setting is turned off.
     * Registering an account again keeps whether it is on. Cheap when nothing changed (called on every state change).
     */
    fun syncPhoneAccounts() {
        val had = registeredIds()
        if (!usePhoneApp) {
            if (had.isEmpty()) return
            for (id in had) runCatching { tm.unregisterPhoneAccount(handleOf(id)) }
            prefs.edit().putStringSet(PREF_PHONE_IDS, emptySet()).apply()
            syncedKey = null
            app.journal.add("Phone app accounts removed (setting off)")
            return
        }
        val sims = app.state.sims
        val name = app.getString(R.string.app_name)
        val perSim = sims.size > 1 || had.any { it != PHONE_ID }
        val wanted: List<Pair<Int?, String>> = when {
            sims.isEmpty() && had.isNotEmpty() -> return   // SIMs not known (yet): leave the accounts as they are
            perSim -> sims.map { it.sub to "$name · " + it.name.ifEmpty { app.getString(R.string.sim_n, it.slot + 1) } }
            else -> listOf(null to name)
        }
        val key = wanted.joinToString("|") { "${it.first}:${it.second}" } + "/" + had.sorted().joinToString(",")
        if (key == syncedKey) return
        for ((sub, label) in wanted) runCatching {
            tm.registerPhoneAccount(PhoneAccount.builder(phoneHandle(sub), label)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setShortDescription(app.getString(R.string.phone_account_description))
                .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL))
                .setIcon(Icon.createWithResource(app, R.drawable.ic_tether).setTint(app.getColor(R.color.accent)))
                .setHighlightColor(app.getColor(R.color.accent))
                .build())
        }.onFailure { app.journal.add("Phone app account not registered: ${it.message}") }
        var ids = had + wanted.map { phoneHandle(it.first).id }
        if (perSim && PHONE_ID in ids) { runCatching { tm.unregisterPhoneAccount(handleOf(PHONE_ID)) }; ids = ids - PHONE_ID }
        prefs.edit().putStringSet(PREF_PHONE_IDS, ids).apply()
        syncedKey = wanted.joinToString("|") { "${it.first}:${it.second}" } + "/" + ids.sorted().joinToString(",")
        if (ids != had) app.journal.add("Phone app accounts: ${ids.sorted()}")
    }

    /**
     * Whether the user turned this account on in the Phone app's calling accounts. With the Phone permission, from the
     * list of accounts Android can call with (what the Phone app itself uses); without it, from the account's own flag.
     */
    @SuppressLint("MissingPermission")
    private fun isOn(h: PhoneAccountHandle): Boolean = runCatching {
        if (hasPhoneStatePermission()) tm.callCapablePhoneAccounts.contains(h) else tm.getPhoneAccount(h)?.isEnabled == true
    }.getOrDefault(false)

    /** The Phone-app accounts and whether each is on, for the Link tab (empty when the setting is off). */
    fun phoneAccounts(): List<Pair<String, Boolean>> = if (!usePhoneApp) emptyList() else registeredIds().sorted().map { id ->
        val h = handleOf(id)
        (runCatching { tm.getPhoneAccount(h)?.label?.toString() }.getOrNull() ?: app.getString(R.string.app_name)) to isOn(h)
    }

    /** The Phone-app accounts that are on, the one for [sub] first. Empty: calls use this app's own screen. */
    private fun phoneAccountsOn(sub: Int?): List<PhoneAccountHandle> {
        if (!usePhoneApp) return emptyList()
        val on = registeredIds().map(::handleOf).filter(::isOn)
        return on.sortedBy { if (subOf(it) == sub) 0 else 1 }
    }

    /** The account a call on [sub] goes to: that SIM's Phone-app account, any Phone-app account that is on, or ours. */
    private fun accountFor(sub: Int?): PhoneAccountHandle = phoneAccountsOn(sub).firstOrNull() ?: account

    /** Why a call is not in the Phone app, for the link log. */
    private fun whyNotPhoneApp(): String = when {
        !usePhoneApp -> "setting off"
        registeredIds().isEmpty() -> "no Phone app account registered"
        else -> "no Phone app account turned on"
    }

    /** Opens the Phone app's calling accounts (where the user turns the accounts on); the call settings if not there. */
    fun openCallingAccounts(from: android.app.Activity) {
        runCatching { from.startActivity(android.content.Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) }
            .recoverCatching { from.startActivity(android.content.Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)) }
            .onFailure { app.journal.add("calling accounts screen not found: ${it.message}") }
    }

    /** A missed call that already rang in the Phone app (it notifies missed calls itself), within 10 minutes. */
    fun missedInPhoneApp(number: String): Boolean {
        val now = System.currentTimeMillis()
        missedInPhoneApp.values.removeAll { now - it > 600_000 }
        return missedInPhoneApp.remove(numberKey(number)) != null
    }

    private fun numberKey(n: String) = n.filter(Char::isDigit).takeLast(9)

    // ------------------------------------------------------------------ calls

    fun connection(local: String) = conns[local]

    /** Calls being handed from this app's own screen to the Phone app (see [moveToPhoneApp]). */
    private val moving = HashSet<String>()

    /** Whether [local] can be handed to the Phone app now: it is in this app's own screen and an account is on. */
    fun canMoveToPhoneApp(local: String): Boolean {
        val old = conns[local] ?: return false
        val c = lastCall[local] ?: return false
        // A ringing call moves as a ringing call; one that runs moves as an outgoing call (no ring), which needs the
        // Phone permission.
        val ringing = old.state == Connection.STATE_RINGING
        return !old.inPhoneApp && local !in moving && old.state != Connection.STATE_HOLDING &&
            ((ringing && c.isIncoming) || hasCallPermission()) && phoneAccountsOn(c.sub).isNotEmpty()
    }

    /**
     * Hands a call shown in this app's own screen to the Phone app without touching the call itself: the same call is
     * reported again on a Phone-app account (ringing if it rings, else as a call already running), and the old
     * connection goes away quietly once the new one exists ([attach]). Nothing is sent to the Android phone: until
     * then the old connection ignores what Telecom asks of it (a hold for the new call, a disconnect). If Android
     * refuses, the call stays where it was.
     */
    @SuppressLint("MissingPermission")
    fun moveToPhoneApp(local: String): Boolean {
        if (!canMoveToPhoneApp(local)) return false
        val old = conns[local] ?: return false
        val c = lastCall[local] ?: return false
        val ringing = old.state == Connection.STATE_RINGING
        val target = phoneAccountsOn(c.sub).first()
        moving += local
        old.moving = true
        pending[local] = c
        if (old.state == Connection.STATE_ACTIVE) pendingActive += local
        try {
            if (c.isIncoming && ringing) tm.addNewIncomingCall(target, Bundle().apply {
                putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts(PhoneAccount.SCHEME_TEL, c.num, null))
                putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, Bundle().apply { putString(EXTRA_LOCAL, local) })
                putString(EXTRA_LOCAL, local)
            })
            else tm.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, c.num, null), Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, target)
                putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, Bundle().apply { putString(EXTRA_LOCAL, local) })
            })
        } catch (t: Throwable) {
            app.journal.add("call not moved to the Phone app: ${t.message}")
            moveFailed(local)
            return false
        }
        app.notifier.cancelIncoming(local)
        app.journal.add("call moved to the Phone app (${target.id})")
        return true
    }

    /** Android refused the Phone-app connection of a call being moved: it stays in this app's screen. True if it was one. */
    fun moveFailed(local: String): Boolean {
        if (!moving.remove(local)) return false
        conns[local]?.moving = false
        pending.remove(local); pendingActive.remove(local)
        return true
    }

    /** Shows the Phone app's own call screen (for a call in the Phone app). Needs the Phone permission. */
    @SuppressLint("MissingPermission")
    fun showPhoneAppCallScreen(): Boolean = hasPhoneStatePermission() &&
        runCatching { tm.showInCallScreen(false); true }.getOrDefault(false)

    /** Whether the call in progress is in the Phone app (then its call screen is the Phone app's). */
    fun inPhoneApp(local: String?) = local != null && conns[local]?.inPhoneApp == true

    /**
     * The keypad / a tel: link: Telecom creates the outgoing connection (onCreateOutgoingConnection). On a Phone-app
     * account that needs the Phone permission; without it the call goes out on this app's own account.
     */
    @SuppressLint("MissingPermission")   // MANAGE_OWN_CALLS (install time) is what a self-managed account needs
    fun placeCall(number: String, sub: Int?): Boolean {
        val target = accountFor(sub).takeIf { it == account || hasCallPermission() } ?: account
        return try {
            tm.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, target)
                putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, Bundle().apply { sub?.let { putInt(EXTRA_SUB, it) } })
            })
            true
        } catch (t: Throwable) { app.journal.add("placeCall refused: ${t.message}"); false }
    }

    fun hasCallPermission() = app.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun hasPhoneStatePermission() = app.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    /** The Phone permissions "Use the Phone app for calls" asks for (one question: they are one group). */
    val phonePermissions = arrayOf(android.Manifest.permission.CALL_PHONE, android.Manifest.permission.READ_PHONE_STATE)
    fun hasPhonePermissions() = hasCallPermission() && hasPhoneStatePermission()

    override fun reportIncoming(local: String, c: Call) {
        pending[local] = c
        lastCall[local] = c
        app.service(callMode = true)
        if (!ring(local, c, started = false)) { pending.remove(local); app.calls.localFailed(local) }
    }

    /**
     * Hands an incoming call to Telecom: on a Phone-app account that is on (that SIM's first), else on this app's own
     * account. An account Android refuses is skipped. Logs where the call went and why.
     */
    private fun ring(local: String, c: Call, started: Boolean): Boolean {
        val extras = Bundle().apply {
            // A Phone-app account shows the number before the connection exists (the ringing screen, call waiting).
            putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts(PhoneAccount.SCHEME_TEL, c.num, null))
            putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, Bundle().apply { putString(EXTRA_LOCAL, local); putBoolean(EXTRA_STARTED, started) })
            putString(EXTRA_LOCAL, local); putBoolean(EXTRA_STARTED, started)
        }
        for (h in phoneAccountsOn(c.sub)) {
            try {
                tm.addNewIncomingCall(h, extras)
                app.journal.add("incoming call: Phone app (${h.id})")
                return true
            } catch (t: Throwable) { app.journal.add("incoming call: Phone app account ${h.id} refused: ${t.message}") }
        }
        return try {
            tm.addNewIncomingCall(account, extras)
            app.journal.add("incoming call: this app's call screen (${whyNotPhoneApp()})")
            true
        } catch (t: Throwable) { app.journal.add("incoming call not reported: ${t.message}"); false }
    }

    @SuppressLint("MissingPermission")
    override fun reportStarted(local: String, c: Call) {
        pending[local] = c
        lastCall[local] = c
        if (c.state == "active") pendingActive += local
        app.service(callMode = true)
        // A call that already runs (moved here, answered on the Android phone, live at reconnect) goes to the Phone app
        // as an outgoing call: Android rings a new incoming call there even when its connection is active already, and
        // answering it then sent an answer for a call long answered. As outgoing it shows in the call screen at once.
        // Needs the Phone permission; without it, an incoming call stays in this app's own screen (no ring) as before.
        val phone = phoneAccountsOn(c.sub).firstOrNull()?.takeIf { hasCallPermission() }
        if (c.isIncoming && phone == null) {
            if (!ring(local, c, started = true)) { pending.remove(local); pendingActive.remove(local); app.calls.localFailed(local) }
            return
        }
        val target = phone ?: account
        app.journal.add("started call: " + if (target == account) "this app's call screen" else "Phone app (${target.id})")
        try {
            tm.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, c.num, null), Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, target)
                putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, Bundle().apply { putString(EXTRA_LOCAL, local) })
            })
        } catch (t: Throwable) {
            app.journal.add("started call not reported: ${t.message}")
            pending.remove(local); app.calls.localFailed(local)
        }
    }

    /** Telecom made the connection for [local]: apply what happened meanwhile. */
    fun attach(local: String, conn: TetherConnection): Connection {
        endedEarly.remove(local)?.let { moving.remove(local); return Connection.createFailedConnection(cause(it)) }
        // The call was moved to the Phone app: its old connection in this app's screen goes away without a word to
        // the Android phone (the call goes on).
        if (moving.remove(local)) conns[local]?.takeIf { it !== conn }?.let { old ->
            old.setDisconnected(DisconnectCause(DisconnectCause.OTHER, "moved to the Phone app"))
            old.destroy()
        }
        conns[local] = conn
        pending.remove(local)?.let { conn.show(it) }
        if (pendingActive.remove(local)) conn.setActive()
        refreshAudio()
        return conn
    }

    fun detach(local: String, conn: TetherConnection) { if (conns[local] === conn) conns.remove(local); refreshAudio() }

    override fun update(local: String, c: Call) { lastCall[local] = c; conns[local]?.show(c) ?: run { pending[local] = c } }

    override fun setActive(local: String) {
        val c = conns[local] ?: run { pendingActive += local; return }
        if (c.state != Connection.STATE_ACTIVE) c.setActive()
        app.notifier.cancelIncoming(local)
        refreshAudio()
    }

    override fun setHeld(local: String, on: Boolean) {
        val c = conns[local] ?: return
        if (on && c.state != Connection.STATE_HOLDING) c.setOnHold() else if (!on && c.state != Connection.STATE_ACTIVE) c.setActive()
    }

    override fun ended(local: String, reason: EndReason) {
        app.notifier.cancelIncoming(local)
        pending.remove(local); pendingActive.remove(local); lastCall.remove(local)
        // Ended while being moved to the Phone app: its new connection, when Telecom makes it, ends at once too.
        if (moving.remove(local)) { endedEarly[local] = reason; main.postDelayed({ endedEarly.remove(local) }, 10_000) }
        val c = conns.remove(local)
        if (c == null) { endedEarly[local] = reason; main.postDelayed({ endedEarly.remove(local) }, 10_000); return }
        // Rang in the Phone app and not answered: it logs and notifies the missed call itself.
        if (c.inPhoneApp && c.state == Connection.STATE_RINGING) c.address?.schemeSpecificPart?.let { missedInPhoneApp[numberKey(it)] = System.currentTimeMillis() }
        c.setDisconnected(cause(reason))
        c.destroy()
        refreshAudio()
    }

    private fun cause(r: EndReason) = DisconnectCause(when (r) {
        EndReason.REMOTE -> DisconnectCause.REMOTE
        EndReason.UNANSWERED -> DisconnectCause.MISSED
        EndReason.FAILED -> DisconnectCause.ERROR
        EndReason.ANSWERED_ELSEWHERE -> DisconnectCause.ANSWERED_ELSEWHERE
        EndReason.LOCAL -> DisconnectCause.LOCAL
        EndReason.BUSY -> DisconnectCause.BUSY
    })

    override fun ringback(on: Boolean) { app.audio.ringback = on }
    override fun uplink(open: Boolean) = app.audio.setUplink(open)

    override fun idle() {
        app.audio.stop()
        app.link.setHighPriority(false)
        app.service(callMode = false)
    }

    override fun changed() = app.changed()
    override fun log(line: String) = app.journal.add(line)

    /** Runs the call audio engine while any connection is dialing, active or held. */
    fun refreshAudio() {
        val live = conns.values.any { it.state == Connection.STATE_DIALING || it.state == Connection.STATE_ACTIVE || it.state == Connection.STATE_HOLDING }
        app.audio.engine(live)
        if (live) app.link.setHighPriority(true)
        app.changed()
    }

    fun audioStateChanged(s: CallAudioState) {
        val wasMuted = audioState?.isMuted
        audioState = s
        // Mute from a headset or the car: the uplink goes silent here and the Android phone is told.
        if (wasMuted != null && wasMuted != s.isMuted) setMuted(s.isMuted)
        app.changed()
    }

    fun setMuted(on: Boolean) {
        if (app.audio.muted == on) return
        app.audio.muted = on
        app.calls.performMute(on)
        app.changed()
    }

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) { conns.values.firstOrNull { it.state == Connection.STATE_ACTIVE || it.state == Connection.STATE_DIALING }?.setAudioRoute(route) }

    companion object {
        const val EXTRA_LOCAL = "app.tether.client.LOCAL"
        const val EXTRA_STARTED = "app.tether.client.STARTED"
        const val EXTRA_SUB = "app.tether.client.SUB"
        private const val PHONE_ID = "tether-phone"
        private const val PREF_PHONE_APP = "phone_app"
        private const val PREF_PHONE_IDS = "phone_app_accounts"
    }
}

/**
 * One call of the Android phone as Telecom sees it. Telecom calls these on the main thread. [inPhoneApp]: on a
 * Phone-app account (the Phone app rings and shows the call); else self-managed (this app rings and shows it).
 */
class TetherConnection(private val app: ClientApp, val local: String, val inPhoneApp: Boolean = false) : Connection() {
    /** Being replaced by a Phone-app connection for the same call ([TelecomBridge.moveToPhoneApp]): what Telecom asks
     *  of it meanwhile (hold it for the new call, end it) is not passed on to the Android phone. */
    @Volatile var moving = false

    init {
        if (!inPhoneApp) connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
        connectionCapabilities = CAPABILITY_MUTE
    }

    fun show(c: Call) {
        setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, c.num, null), TelecomManager.PRESENTATION_ALLOWED)
        val name = app.state.name(c.num) ?: c.name
        val sim = app.state.simName(c.sub)
        setCallerDisplayName(listOfNotNull(name ?: app.format(c.num), sim).joinToString(" · "), TelecomManager.PRESENTATION_ALLOWED)
        connectionCapabilities = CAPABILITY_MUTE or if (c.hold) CAPABILITY_HOLD or CAPABILITY_SUPPORT_HOLD else 0
    }

    override fun onShowIncomingCallUi() { app.notifier.showIncomingCall(local) }
    override fun onAnswer() { if (!moving) answer() }
    override fun onAnswer(videoState: Int) { if (!moving) answer() }

    private fun answer() {
        // A call that already runs on the Android phone (reported as started) needs no answer there.
        if (app.calls.calls.firstOrNull { it.local == local }?.state.let { it == "active" || it == "holding" }) {
            setActive(); app.notifier.cancelIncoming(local); app.telecom.refreshAudio(); return
        }
        app.calls.performAnswer(local)
        setActive()
        app.notifier.cancelIncoming(local)
        app.telecom.refreshAudio()
        if (!inPhoneApp) app.openCallScreen()   // the Phone app shows its own call screen
    }

    override fun onReject() = end(DisconnectCause.REJECTED)
    override fun onDisconnect() = end(DisconnectCause.LOCAL)
    override fun onAbort() = end(DisconnectCause.LOCAL)

    private fun end(code: Int) {
        if (moving) { setDisconnected(DisconnectCause(DisconnectCause.OTHER)); destroy(); app.telecom.detach(local, this); return }
        app.notifier.cancelIncoming(local)
        app.calls.performEnd(local)
        setDisconnected(DisconnectCause(code))
        destroy()
        app.telecom.detach(local, this)
    }

    override fun onHold() { if (!moving && app.calls.performHold(local, true)) setOnHold() }
    override fun onUnhold() { if (!moving && app.calls.performHold(local, false)) { setActive(); app.telecom.refreshAudio() } }
    override fun onPlayDtmfTone(c: Char) { if (!moving) app.calls.performDtmf(local, c.toString()) }
    override fun onSilence() { if (!inPhoneApp) app.notifier.silenceIncoming(local) }
    override fun onStateChanged(state: Int) { app.telecom.refreshAudio() }
    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(state: CallAudioState) { app.telecom.audioStateChanged(state) }
}

/** The calls on this app's own (self-managed) account. [TetherPhoneConnectionService] serves the Phone-app accounts. */
open class TetherConnectionService : ConnectionService() {
    private val app get() = application as ClientApp
    /** Whether this service's calls show in the Phone app. */
    protected open val inPhoneApp = false

    override fun onCreateIncomingConnection(account: PhoneAccountHandle?, request: ConnectionRequest): Connection {
        val local = request.extras?.getString(TelecomBridge.EXTRA_LOCAL)
            ?: request.extras?.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)?.getString(TelecomBridge.EXTRA_LOCAL)
            ?: return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        val conn = TetherConnection(app, local, inPhoneApp)
        // A started call (answered on the Android phone, moved here) is live already: no ring.
        val started = request.extras?.getBoolean(TelecomBridge.EXTRA_STARTED) == true ||
            request.extras?.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)?.getBoolean(TelecomBridge.EXTRA_STARTED) == true
        if (!started) conn.setRinging() else conn.setActive()
        conn.setInitialized()
        return app.telecom.attach(local, conn)
    }

    override fun onCreateIncomingConnectionFailed(account: PhoneAccountHandle?, request: ConnectionRequest) {
        // Telecom refused the call (too many calls, Do Not Disturb policy…): forget the binding, so that if it is
        // answered on the Android phone it shows up here as a started call with audio instead of a half-bound one.
        val local = request.extras?.getString(TelecomBridge.EXTRA_LOCAL)
            ?: request.extras?.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)?.getString(TelecomBridge.EXTRA_LOCAL) ?: return
        if (app.telecom.moveFailed(local)) { app.journal.add("Telecom refused to move a call to the Phone app"); return }
        app.journal.add("Telecom refused an incoming call"); app.calls.localFailed(local)
    }

    override fun onCreateOutgoingConnection(account: PhoneAccountHandle?, request: ConnectionRequest): Connection {
        val extras = request.extras
        val mirrored = extras?.getString(TelecomBridge.EXTRA_LOCAL)
        val local = mirrored ?: UUID.randomUUID().toString()
        // A carrier code (*123#) dialed in the Phone app with this app's account: no call (it would never connect, and
        // the Android phone's own dialog would wait there): it runs as USSD in this app's carrier-code screen.
        if (mirrored == null && UssdCodes.valid(request.address?.schemeSpecificPart.orEmpty())) {
            app.openUssd(request.address!!.schemeSpecificPart, app.telecom.subOf(account))
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED, "USSD"))
        }
        // Dialed in the Phone app with this app's account but nothing to call through: say why there.
        if (mirrored == null && !app.link.isConnected) {
            app.problem(Problem.NOT_CONNECTED)
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR, app.getString(R.string.err_not_connected),
                app.getString(R.string.err_not_connected), "link down"))
        }
        val conn = TetherConnection(app, local, inPhoneApp)
        conn.setDialing()
        conn.setInitialized()
        val number = request.address?.schemeSpecificPart.orEmpty()
        conn.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        val attached = app.telecom.attach(local, conn)
        // A call this phone dials (keypad, a tel: link, Recents): the Android phone is asked to dial it.
        if (mirrored == null) {
            // The SIM: this app's keypad says which; a call from the Phone app's keypad, its contacts or call history
            // comes with the SIM's account (dual SIM), else the SIM last used with that number.
            val sub = extras?.takeIf { it.containsKey(TelecomBridge.EXTRA_SUB) }?.getInt(TelecomBridge.EXTRA_SUB)
                ?: app.telecom.subOf(account) ?: if (inPhoneApp) app.state.preferredSim(number) else null
            app.service(callMode = true)
            app.calls.performStart(local, number, sub)
            if (!inPhoneApp) app.openCallScreen()
        }
        return attached
    }

    override fun onCreateOutgoingConnectionFailed(account: PhoneAccountHandle?, request: ConnectionRequest) {
        val local = request.extras?.getString(TelecomBridge.EXTRA_LOCAL)
        if (local != null && app.telecom.moveFailed(local)) { app.journal.add("Telecom refused to move a call to the Phone app"); return }
        app.journal.add("Telecom refused an outgoing call")
        if (local != null) app.calls.localFailed(local) else app.problem(Problem.CALL_FAILED)
    }
}

/** The calls on the Phone-app accounts ("Use the Phone app for calls"): the Phone app rings and shows them. */
class TetherPhoneConnectionService : TetherConnectionService() {
    override val inPhoneApp = true
}

/** The route after [current]: earpiece → speaker → Bluetooth / wired (whatever [supported] has) → earpiece; null: none. */
fun nextAudioRoute(supported: Int?, current: Int?): Int? {
    val mask = supported ?: (android.telecom.CallAudioState.ROUTE_EARPIECE or android.telecom.CallAudioState.ROUTE_SPEAKER)
    val order = listOf(android.telecom.CallAudioState.ROUTE_EARPIECE, android.telecom.CallAudioState.ROUTE_SPEAKER,
        android.telecom.CallAudioState.ROUTE_BLUETOOTH, android.telecom.CallAudioState.ROUTE_WIRED_HEADSET).filter { mask and it != 0 }
    if (order.isEmpty()) return null
    val cur = order.indexOf(current ?: android.telecom.CallAudioState.ROUTE_EARPIECE)
    return order[(cur + 1) % order.size]
}
