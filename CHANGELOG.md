# Changelog

## Unreleased

- Android client: the "full-screen notifications are off" warning shows only when incoming calls would ring in
  Tether's own call screen. Calls in the Phone app ring in its own screen and never need that permission, which Android
  may switch off again with every update installed outside the Play Store.

- Android client: **see whether a text you sent arrived.** Under a text sent from this phone: "✓ Sent" when the
  network took it, then "✓✓ Delivered" (in teal) when the recipient's phone confirmed it. The SIM phone already
  forwarded both reports; the client kept only "sent", and lost track of the text once its real copy came from the SIM
  phone, which happens before the delivery report. Networks that send no delivery reports leave it at "Sent".

## 1.3.2 — 2026-11-27

- Android client: **smoother call sound when the link hiccups.** A frame from the SIM phone that is not there in time
  (Bluetooth sometimes delivers one late) is now filled by the Opus decoder's own concealment, which continues the
  voice, at full level for 40 ms and then faded, instead of repeating the last 10 ms while fading out (a short, audible
  dropout). The problem report says how much of each call the decoder filled.

## 1.3.1 — 2026-11-27

- Android client: **call sounds from the start of an outgoing call.** Until the other side answered, nothing played:
  not the network's ringback or announcements ("the number you dialed is switched off…") coming from the SIM phone,
  nor the client's own ringback. The call was reset from "dialing" to "new" as it was created, and the call audio
  runs only while a call is dialing, active or on hold. It now starts as the call starts dialing.

## 1.3.0 — 2026-11-27

This fork ships the Android apps only (the SIM phone app and Tether Client; no iPhone app). It includes everything
in the original project's 1.2.5, below, plus these changes:

- Android client: **select texts in a conversation**. A long press selects a text (tap more to add them); the header
  then shows how many, with Star / Unstar, Copy (the selected texts together, oldest first) and Delete (asks first).
  With one selected: Select text, to copy part of it (also by holding the selected text again, as Telegram does),
  and Send again for a text that was not delivered. Starred texts show a star by their time; stars are kept on this
  phone. Back or ✕ stops selecting. This replaces the Copy / Delete menu.
- Android client: **Starred messages**. A gold star beside the search field on the Messages tab opens the chats that
  have starred texts (how many, and the newest one); tap a chat for its starred texts, newest first; tap a text to open
  it in its conversation (scrolled to it and lit for a moment), hold it to unstar or copy. Both lists can be searched.

- Android client: **low-battery alert for the SIM phone**. Link › Low battery alert (off, or 10 % to 50 %; 20 % by
  default): when the SIM phone is not charging and its battery falls to that level, a notification says so, and it
  alerts again at every 5 % below it (nothing changes in between). It goes away when the phone charges or rises above
  the level. It has its own notification category, "Android phone battery", and its own group in the notification
  shade, apart from texts. Texts are now conversation notifications (a shortcut per conversation), so Android 11+
  lists them in the shade's Conversations section, apart from the app's other notifications such as this alert.

- Android client: **the Messages list looks like a phone's messaging app**: a thin line between conversations; the
  date on the right (the time today, "Yesterday", the weekday this week, else the date) and under it a round count
  of unread texts instead of "(3)" after the name; unread conversations in bold with a brighter preview and a teal
  date; two lines of the last text, in its own direction; a person icon for numbers that are not contacts.
- Android client: **act on several conversations at once**. A long press starts selecting (tap more, or select all);
  the search row becomes a bar with how many are selected and a row of actions: Read, Mute / Unmute, Block / Unblock,
  Delete (asks first), and Call when one is selected. They stay above the list while it scrolls. Back or ✕ stops
  selecting. This replaces the long-press menu, whose actions are all there.

- Android client: **conversations laid out like a phone's messaging app**:
  - a heading for each day (Today, Yesterday, then the weekday and date) in a chip between two lines, instead of a
    date under every text; while you scroll, the day stays pinned at the top once its heading has gone off screen;
  - each line of a text runs in the direction of its first letter: Persian right to left, English left to right;
  - the time beside each bubble, once for texts sent in a row in the same minute, with "Sending" or "Not delivered"
    under it; the SIM only in conversations that used more than one SIM;
  - an "Unread messages" line above the texts that were unread when you opened the conversation, which opens there;
  - bubbles in a run from one side sit closer together.

- Android client: **Recents grouped by day**, like a phone's own call log:
  - a heading for each day (Today, Yesterday, then the date) over a card of that day's calls;
  - on each line: the direction as an arrow (missed and declined in red, blocked crossed out), the name (red when
    missed), the SIM, how long the call took, and the time;
  - calls in a row from the same number with the same direction share one line with a count, for example "(2)";
  - while you scroll, a bubble at the top names the day you are in, and fades a moment after you stop;
  - tap still calls back.
- Android client: **delete several calls at once** in Recents. A long press starts selecting; tap more calls, or a
  day's heading for the whole day (tap it again to unpick the day). A bar at the bottom shows how many are selected,
  with Select all (an icon, lit when everything is picked; tap again to unpick all) and Delete (asks first); ⋮ there
  opens the old one-call menu (call, message, copy, block) when one line is selected. Back or ✕ stops selecting.

- SIM phone, USSD: a carrier menu can be answered while the SIM phone is locked. Its screen went off while the menu
  waited for your reply, so the reply found "no menu". Now:
  - every read of the menu wakes the screen first;
  - a reply waits up to about 3 s for the menu to show again;
  - the answer reaches the main phone sooner: the SIM phone checks for it every 0.3 s instead of every second.

- Android client: the keypad's digits line up. Each digit is centred over its letters and in its key, and keys
  without letters (1, *, #) keep an empty letters line, so every digit sits at the same height, as on a phone's own
  dialer. The same fix centres the number typed above the keypad, the name under it, and the call screen's texts.

- SIM phone: **USSD codes as the phone's own dialer runs them.** Some carriers refuse codes sent through Android's
  background USSD API ("The carrier refused the code") but answer a dialed code. Where Tether's Wireless debugging is
  paired, the SIM phone now:
  - dials the code on the chosen SIM;
  - reads the answer from the phone's own USSD dialog;
  - closes final answers, so no dialog is left on the SIM phone's screen;
  - keeps a carrier menu open for a reply from the client: new `ussd.reply` / `ussd.cancel` messages and an optional
    `session` field on `ussd.result`.

  Without Wireless debugging it uses the background API as before. A code is never sent both ways.
- Android client: a carrier menu open on the SIM phone is answered directly (Reply, Cancel); a menu in a final answer
  still goes out as a new code. A code dialed in the Phone app with Tether's calling account (or a `tel:` link) now
  opens the USSD screen instead of starting a call that never connected.

- Android client: **USSD codes** (carrier codes such as *123#). A new button under the call buttons on the keypad
  opens a screen where you:
  - choose the SIM, type a code and see the carrier's answer;
  - answer a menu by typing your choice: it is sent as a new code that carries it (*123# then 2 sends *123*2#), which
    most carriers accept;
  - send a recent code again with one tap.

  A code typed on the keypad and sent with a call button runs as USSD on that SIM, as on a phone's own dialer. It
  uses the SIM phone's existing carrier-code support (`ussd.send`): no protocol change, and the SIM phone's app needs
  no update. Like Android's USSD API, it gets one answer per code.

- SIM phone: no notification sound before the ringtone. The incoming-call notification's channel played the default
  notification sound just before Android's ringtone. It is now a silent channel; the ringtone and its vibration are
  Android's, unchanged.

- Android client: a call on another device (the SIM phone took it, or it was answered there) now stays in the
  notification bar, "Call on another device" with a **Move here** button. A tap on it opens Tether. Before, the way
  back was only the banner inside the app. It is quiet (no sound, no pop-up) and goes away when the call ends or comes
  back here.

- Android client: **Move here** puts the call straight into the Phone app's call screen again. Before, a call taken
  back from the SIM phone rang in the Phone app like a new call, and answering it then barely worked. Since the
  Phone-app calling accounts came in, a call that already runs (moved here, answered on the SIM phone, live after a
  reconnect) was reported to Android as a new incoming call. It is now handed over as an outgoing call, which Android
  shows in the call screen without ringing, and "Move to the Phone app" does the same for a running call. Both need
  the Phone permission; without it the call shows in Tether's own call screen, as before. The Phone app's call history
  may list such a call as outgoing.

- SIM phone: **one silent call no longer stops call audio for good.** The "This Android phone's software gives
  Tether no call audio" notice could appear after a single call whose first 6 seconds were digital silence (a far
  end that says nothing on VoLTE). From then on every answered call went back to the SIM phone, so the client's call
  screen closed as soon as you answered.
  - The check now waits 15 seconds of silence.
  - Only that call goes back to the SIM phone.
  - The phone is remembered as giving no call audio only after two such calls in a row.
  - Every Tether install tries again; test builds share a version number, so a new install did not clear it before.

- SIM phone: once Tether's call screen is up, the incoming-call notification no longer pops up over it and keeps the
  screen awake. It stays in the notification bar with Answer and Decline, on a quiet channel ("Call in progress
  (quiet)"). The ringtone is unchanged: Android plays it. The next call starts with the usual pop-up again.

- SIM phone: **Take the call here.** The call screen on the phone with the SIMs has a new button while the call's
  audio is on the phone in your hand. It moves the call to the SIM phone's own earpiece and microphone, as for a call
  answered there. The client shows the call "on <SIM phone>" and can take it back with **Move here**. No protocol
  change: the client is told through the call updates it already gets.
- SIM phone: **a full call screen.** It now has the same round buttons as the client: Mute, the audio route
  (earpiece, speaker, Bluetooth, headset), Keypad (DTMF), Hold and End, and Decline and Answer while ringing.
  Mute and the route show only while the call is on the SIM phone itself. The phone maker's own call screen can't
  show these calls while Tether is the SIM phone's phone app, which Tether needs to control calls.

- Android client, calls in the Phone app:
  - **An incoming call could ring in Tether's own screen instead of the Phone app.** The calling accounts were
    removed and added again whenever the SIM phone's status briefly listed fewer SIMs (while reconnecting, after a
    restart or after pairing again). Android then forgot they were turned on. An account now stays registered while
    the setting is on.
  - **Which accounts are on** is read from the accounts Android can call with (as the Phone app does) once the Phone
    permission is allowed. An account Android refuses is skipped.
  - **The link log** says where each incoming call went, and why if not to the Phone app.
  - **Tether's call screen** has **Move to the Phone app** (the call goes on, nothing is sent to the SIM phone) for a
    call shown there, and **Phone app call screen** for a call already in the Phone app. Tapping the call banner opens
    the Phone app's call screen for such a call.
  - **Link › Calls** says when an incoming call in Tether's own screen can't wake the screen: since Android 14 that
    needs full-screen notifications allowed for the app. A button opens that setting.

- Test builds (CI artifacts, and builds without the release key on any computer) are all signed with one shared test
  key, `android/test-signing.keystore`, so each installs over the previous one. Before, every CI run and every computer
  signed with its own random debug key and Android refused the update. Coming from such a build, uninstall once.

- Android client: **calls in the Phone app**. A new switch, Link › Calls › "Use the Phone app for calls" (on by
  default), makes the Android phone's SIMs calling accounts of this phone, one per SIM ("Tether · Work"). Calls then
  ring in the Phone app with its call screen and ringtone, can be dialed from its keypad, contacts and call history,
  and are logged there. Android asks you to turn the accounts on once (Open calling accounts). Until you do, or with
  the switch off, calls use Tether Client's own call screen as before. The protocol and the SIM phone's app do not
  change. Calling from Tether Client's own keypad through the Phone app needs the Phone permission. A missed call
  that rang in the Phone app is not notified a second time.

- This fork is Android only: the iPhone app, the Swift SDK and their tooling are removed. The Android apps, the Kotlin
  library and the protocol are unchanged.
- Android client: new buttons throughout. Every button now shows a ripple when pressed, looks disabled when it is
  disabled, is at least 48dp tall and wraps its text, so large font sizes and Persian no longer clip. TalkBack
  announces each one as a button, with a description for icon-only ones.
  - Tabs have icons, a highlight behind the selected one, and a red count badge for missed calls and unread texts.
  - Keypad: round keys with a ripple and a key-press haptic. Backspace sits next to the number (hold it to clear).
    A green call button per SIM, disabled while the number is empty. The keypad is no longer rebuilt on every key.
  - Call screen: round actions with a label under each (Mute, Speaker / Bluetooth / Headset, Keypad, Hold). They
    fill with the accent colour when on. Answer, Decline and End call are large round buttons.
  - Conversation: call and menu icons in the header, a round Send button (disabled while the text is empty), and a
    SIM chip.
  - Recents, Contacts and Messages: Clear all, New contact and New message are icon buttons beside the search field.
    The call banner and the error banner show a ripple when tapped.
  - Link: the data SIM is a row of choices with the chosen one filled; the quieter actions use quieter styles.

## 1.2.5 — 2026-11-25

- iPhone: Tether sets its CallKit provider up again when iOS restarted its call service under the running app. Until
  now every call then failed until the app was force-quit: calls dialed on the iPhone ended at once ("unknown call
  provider") and incoming calls rang but CallKit never activated their audio (no microphone, no sound). Tether checks
  this whenever it comes to the front, after an audio-system reset and on a refused request, then tries once more;
  the link log says `CallKit: … provider set up again`. The Android client uses Android's Telecom and is not
  affected (#18, thanks @arashfarahani).
- Android phone, Qualcomm phones whose audio configuration wires `incall_music_uplink` without an `afe-proxy-playback`
  mixer path (seen on a Redmi Note 8 with a custom Android 16): the iPhone's voice is now sent into the call through
  the in-call music route (`telephony_tx`). The interception API's uplink went nowhere there (the HAL logged "unable
  to find path 'afe-proxy-playback afe-proxy'"), so the other side heard only what the Android phone's own microphone
  picked up, and only when it lay close. The self-test and the in-call check read the same proxy and reported the
  voice as reaching the call. Tether reads the vendor's mixer configuration once, never injects through the
  interception API there (not even when an earlier self-test chose it), and says why in the link log (#18, thanks
  @arashfarahani).
- Android phone, Qualcomm without a maker's microphone switch (Xiaomi, Redmi, Poco, OnePlus, Samsung with a Qualcomm
  chip and others): **Muted** no longer uses Android's microphone mute. Its audio system mutes the whole uplink with
  it, the iPhone's voice included, while the in-call check still heard that voice and remembered the mute as safe: the
  other side heard nothing (Redmi Note 8; perhaps also #14 on a Poco X3 Pro). The phone's microphone stays on there,
  the Android app says so in orange under **Muted**, the link log and problem report give the reason, and the
  self-test no longer tests that mute. The iPhone's Link tab already shows **In this call: Microphone on** (#18,
  thanks @arashfarahani).
- Android phone: switching between **Off during calls**, **Muted** and **On** keeps what this phone showed of the
  redirect modes. Before, every switch forgot it, so the next calls tried a mode known to fail again, each with a few
  seconds of silence and a late uplink for the rest of that call. Choosing **Off during calls** again while it is
  chosen, or the self-test, still tries them anew (#18, thanks @arashfarahani).

## 1.2.4 — 2026-11-25

- Coming from 1.1.0: uninstall the 1.1.0 Android app first (1.2 APKs are signed with the project's release key), then
  pair again.
- Link: an iPhone app suspended in the background now finds its way back when the Android phone closed the session or
  Tether restarted there (an update): it answers every wake at once and sets a new session up when the phone's
  service changed or a wake finds none, and the Android phone wakes a paired iPhone every 3 minutes while there is
  no session. Before, with Bluetooth tethering on, the app could stay unlinked until it was opened (21 minutes seen).
  Every wake is now in the link log, with why one could not be sent.
- Android phone, Samsung with a Qualcomm chip (Galaxy A73 and others): Tether no longer writes Samsung's call keys or
  switches calls into a redirect mode there. On a Galaxy A73 (Android 16) they left the phone's voice channel busy,
  so call audio and even calls on the phone itself stayed silent until a reboot (#13). Redirect modes are no longer
  tried automatically on any Samsung; vendor keys are written back at helper start only if Tether set them; a phone
  remembered as giving no call audio is tried again after a Tether update; the notice says to restart the phone once
  if its own calls have no sound.
- Android phone: real calls and texts always reach the iPhone and other clients, even while a debug test filter is on,
  and that filter switches itself off after 30 minutes (a forgotten one let a call dialed from the iPhone run silent).
- Android phone: **Help › Send a problem report** shares one `.txt` file (or copies it) with what is needed to find
  any problem: device and Android / One UI version, setup steps, link, Bluetooth and tethering state, the SIMs' network
  and signal, call-audio settings and self-test, the link log and the app's log. Phone numbers, names, message text,
  e-mail, addresses and keys are removed. It also holds call-audio diagnostics from the helper (audio mode, the
  interception API, each active recording with Android's `isClientSilenced`, routes, selected `dumpsys audio` /
  `media.audio_policy` lines), taken now and automatically when a call's path looks dead and at the end of every
  bridged call (the last three are kept), to tell a silenced recording from a phone that gives no call audio (#13).
- Tether Client: **Link › Send a problem report** with the same format (device, setup, link, the phone's status
  without numbers or names, playout and microphone state, link log, app log).
- Issue forms and docs ask for the problem reports instead of pasted logs.

- Design rules for both apps (dark first, the teal accent, palette colours only, English and Persian) in CONTRIBUTING.md,
  checked by `tools/check-guardrails.py` in CI: raw or off-palette colours fail the build, and a pull request may not
  change the palette, icons, app names, ids, signing, workflows or the checks. The new pairing screens use the app's
  own teal and dark cards.
- iPhone: a redesigned first-run Link screen (connect card, numbered steps), a scanner that explains a missing or
  refused camera, and a clearer manual code entry (#17, thanks @ashkan0033).
- iPhone: Link › **Send a problem report** builds one text (device, setup and permissions, link, call audio, the Android
  phone's last status, the link log, the app's events) and shares it as a .txt file or copies it. Phone numbers, e-mail,
  names, Bluetooth and IP addresses, IDs and keys are removed before it leaves the app.
- Contributing: pull request rules (one topic, keep the project's name and ids, never break installed apps, CI changes
  on their own); `main` now requires all CI jobs to pass and a maintainer's approval before a pull request is merged.

## 1.2.2 — 2026-11-25

- Android phone: a call answered or dialed on the Android phone itself (its screen, a headset or a car connected to it)
  stays on that phone. Before, with the iPhone linked, its audio went to the iPhone and the phone's own earpiece and
  microphone were muted, so the call had no audio where it was taken (#13). The iPhone shows such a call as on the
  Android phone, with **Move here**.
- Android phone: when its software gives Tether no call audio (the call's sound reads as silence, as on a Galaxy A73
  with Android 16, #13), the call goes back to the phone itself within seconds instead of staying silent on both ends,
  and later calls are not bridged on that firmware. The iPhone, the Android client and the phone app say so
  (`helper.audio` "blocked"); a system update or a self-test that hears the call turns call audio back on.
- iPhone: the renewal note says to refresh the app in SideStore, AltStore or Sideloadly (it named a developer script).
- Android phone: Call audio › **Link log** shows the link log with Share and Copy (headed by the app version, phone
  model and Android version), so a bug report no longer needs a computer and adb.
- Coming from 1.1.0: uninstall the 1.1.0 Android app first (1.2 APKs are signed with the project's release key), then
  pair again.

## 1.2.1 — 2026-11-25

- Android phone: a redirect audio mode whose effect on the uplink cannot be checked (the phone gives no uplink capture,
  common on custom ROMs) is no longer kept for the call, and no other untested mode is tried on later calls. Before, the
  first one or two calls on such a phone could leave the other side hearing nothing of the iPhone (Poco F3 and
  Poco X3 Pro reports, #14).
- Coming from 1.1.0: uninstall the 1.1.0 Android app first (1.2 APKs are signed with the project's release key), then
  pair again. Everything new in 1.2: see 1.2.0.

## 1.2.0 — 2026-11-25

### Call audio on the Android phone

- The phone's own microphone is kept out of bridged calls fully automatically: the default setting tries a redirect
  audio mode, then the phone maker's own microphone switch (Samsung's tx key, MediaTek's UL mute; never Android's
  general mic mute), then leaves the microphone on, each step checked during the call and remembered per phone model.
  A redirect mode whose effect on the uplink could not be seen is no longer used on later calls (#15); where the
  uplink cannot be checked at all, only Samsung's key (verified on real calls) stays on.
- The call's audio paths open only once the phone is in a call's audio mode (on outgoing calls that can take seconds of
  dialing): no fall-back to older methods for that, and no restart loop (at most one restart, never over the
  self-test's choice) (#15).
- The iPhone's voice goes into the call only once it is connected: nothing piles up while dialing.
- With Bluetooth tethering on: frequent hold-ups get a larger uplink cushion (60 ms instead of 45), and what a long
  stall delivers late is dropped at once instead of delaying the call for a minute.
- Samsung phones with a Qualcomm chip (Galaxy A71, A73, Fold) also mute the voice-call stream, so the earpiece is silent
  even where Samsung's keys do nothing (#15). The `telephony_tx` path is checked to be routed to the call, and built
  without a low-latency track some Qualcomm phones refuse (#15).
- Hang-up no longer waits for blocked audio reads; start, stop, the mic setting and a new helper session take one lock
  (#15).
- Diagnostics for "no audio" reports (#13, #14): the Link log says when the downlink capture delivers nothing or only
  zeros, when the phone takes nothing from the uplink injection, when the injection is not routed to the call, and how
  long the paths waited; the self-test's rows say the same.

### iPhone

- iPhone: the microphone permission is asked for once the app is paired and again when a call starts; a refused or
  silent microphone is shown on the call screen, in the Link tab and the Link log, and "Your voice in the call" no
  longer says it reaches the other side then (#14).
- iPhone call audio: the microphone is sent only once a call is connected (not while an outgoing call dials); a
  stall's backlog (Bluetooth tethering) plays late and drains in the pauses instead of losing speech; interruptions
  and media-services resets bring the audio back; CallKit callbacks run in order; a speaker choice made while
  connecting is kept; the render-thread flags use a heap-allocated lock (#15).
- iPhone link: renewing a pending connect no longer leaves the link waiting on the cancelled one (#15).

### Android client

- Android client: a malformed `sync.ids` page no longer deletes local rows (#11, thanks @Shabdin-A).
- Android client: the same playout stall handling as the iPhone (a stall's backlog plays late and drains in the
  pauses, cut only beyond 0.8 s; a stall's burst no longer holds the buffer deep).
- Android client: the microphone is sent only once a call is connected (not while dialing), and a refused permission, a recorder that does not open or one that delivers only silence for 6 s is shown on the call screen and the main screen and in the Link log; the permission is asked for again when a call starts (#14).
- Android client: content no longer goes behind the navigation bar, text fields stay above the keyboard, call-screen buttons share the width evenly (#12).

### Platforms

- The phone app installs on Android 8.0 and 9 (was 10): the link runs over GATT there (no L2CAP channels; the phone
  answers the PSM read empty, so the iPhone and the Android client go straight to GATT), texts, contacts, call log and
  call control work, the default phone app is requested through Telecom, a call's direction comes from its first
  state. No call audio before Android 11: the status field `helper.audio` ("unavailable") is now shown on the
  iPhone in Link › Call audio. Hotspot and Bluetooth tethering switches are reported as unavailable before Android 11.

### Developers

- SDK guides: a "Writing a client" checklist for the 1.2 rules (two pairing-code forms, an empty PSM means GATT, the
  microphone only once a call is active, `status.helper.audio`, audio stalls); the examples show the QR link form.
- CONTRIBUTING.md: what "done" means (every doc, translation, SDK page, example and the other client app updated with
  a change) and why no personal Apple or signing account may appear in the repository; the pull request checklist
  asks for both.

### Install, pairing and first run

- Release APKs are signed with the project's own release key (fingerprint in docs/RELEASING.md). The 1.1.0 APKs were
  debug-signed: uninstall the 1.1.0 app before installing 1.2.0, then pair again.
- Install without building: an AltStore / SideStore source for the iPhone app (`releases/latest/download/altstore.json`),
  a step-by-step install guide (docs/INSTALL.md) with SideStore, AltStore, Sideloadly and TrollStore, an FAQ, a
  complete Persian guide (docs/fa/), and release notes that say which download goes on which phone.
- Pairing QR code is now a `tether://pair?c=…` link: the iPhone's Camera app offers to open it in Cellular, which asks before pairing. Old `tether1:` codes still work everywhere; the Android app can show the old code for 1.1 iPhone apps.
- First-run guides: numbered Setup steps with plain instructions on the Android phone (incl. the one-time wireless-debugging pairing), a Set up section on the iPhone's Link tab, and pairing steps in the Android client (English and Persian).

## 1.1.0 — 2026-11-23

### New features

- iPhone Link tab › Phone tools: send carrier codes (balance etc.) on a chosen SIM and read the answer, "Find phone"
  (rings the Android phone on the alarm stream for up to 30 s) and a ringer mode picker. New optional messages
  `ussd.send` / `ussd.result`, `phone.find` / `phone.finding`, `ringer.set` / `ringer.result`, and `tools`, `ringer`,
  `dnd` in `status`; the section is hidden when the Android phone does not report `tools`.
- Phone numbers are shown grouped like phones show them ("+98 912 345 6789", "+1 (555) 010-0001"), on the
  iPhone's keypad as you type, in Recents, Contacts, Messages, the call screen, CallKit and notifications, and on
  the Android phone's call screen. National numbers follow the SIM's country (new optional `country` per SIM in
  `status`). Short codes, USSD codes and names are left as they are; what is dialled, stored and searched is unchanged.
- **Android client** (`android/client`, "Tether Client"): a second Android phone (Android 10+) as a Tether client,
  like the iPhone app: QR pairing, the link with the iPhone's reconnect rules, calls as self-managed Telecom calls
  (Bluetooth headsets and car kits) with Opus audio both ways, keypad, Recents, Contacts and Favourites, Messages,
  notifications, the Link screen and switches, `tel:` links. Tested against a simulated Android phone only so far.
- iPhone: call numbers from other apps. `tel:` links (as the iOS 18.2+ default calling app, paid accounts only),
  the `tether://dial?number=` scheme, a "Call a Number" Shortcuts / Siri action that also finds a number in text,
  and optional call-backs from the Phone app's Recents. Links always ask before dialling; service codes and
  extensions are kept.
- Calls on Bluetooth headsets and car kits: an **Audio** menu on the call screen (iPhone, Speaker, each accessory)
  moves the call there and back, routes chosen on the system screen, Control Center or in the car are followed,
  the speaker button follows the real route, and the call audio rebuilds itself when a hands-free microphone
  changes the sample rate instead of going silent. Route changes are listed in the Link log.
- A "Calls paused" notification when the app is closed while running; docs on what happens when it is swiped away.

### Developer platform

- Swift SDK: TetherCore can be added to any package with `.package(url: "https://github.com/amirwmr/tether",
  from: "1.1.0")` (root `Package.swift`). Everything except the Bluetooth client builds and passes its tests on
  Linux (swift-crypto in place of CryptoKit, a portable lock, a regular-expression fallback where NSDataDetector does
  not exist); the package builds for iOS, macOS, tvOS, watchOS and visionOS. DocC documentation with four articles,
  public initialisers for every message body, `TetherPrivateKey`, `SyncBody.codecs` / `client`, and two examples:
  `examples/swift/Loopback` (a whole session in memory, macOS and Linux) and `examples/swift/MacCentral` (a Mac
  Bluetooth client). See docs/SDK-swift.md. The iPhone app's behaviour is unchanged.
- Kotlin/JVM library `com.github.amirwmr.tether:tether-core` (JitPack or `publishToMavenLocal`): the protocol
  core now builds as a plain JVM 17 library for desktops, servers and Android, with Dokka API docs, a command-line
  example (`examples/kotlin-jvm`) and docs/SDK-kotlin.md; MediaCodec Opus moved to `android/core-android`.
- Extensions: developers can add features on top of the link without forking the apps (protocol §7,
  docs/EXTENDING.md). Extension messages use the reserved `x.<reverse-dns>` namespace; each side announces what it
  speaks in `caps` (in `sync` and `status`), the highest common major version is used, and older apps neither send nor
  receive anything new. Registries in TetherCore and the shared Android core route only extension types (never a core
  one), cap sizes (16 KiB, 2 KiB during a call) and rates (20/s), answer an unknown extension request with
  `error {t, code}` and switch off an extension that keeps failing. Built in on every side: `x.tether.echo` (round
  trip, clock offset, transport); `tools/dbg.sh ext` / `ext_echo`, and a Developer row in Debug iPhone builds.
- Releases: one version in `VERSION` for the Android apps (versionName / versionCode) and the iPhone app;
  `tools/release.sh <version>` updates it, the CHANGELOG and tags; a tag builds a GitHub Release with the A54 and
  client APKs, an unsigned iPhone IPA, SHA256SUMS and build provenance attestations. CI keeps debug-signed APKs of
  every push to main. See docs/RELEASING.md.
- The Android phone's protocol and codec code moved to a shared module (`android/core`) used by both Android apps.

### Calls and audio

- Call audio, uplink (the iPhone's voice to the far end): no voice frames are thrown away any more. The Android
  phone's helper kept 8 frames and dropped the oldest when full, plus a frame whenever 2 stayed queued for a second:
  5-7 % of the iPhone's voice was lost on real-iPhone test calls (42-54 dropped and 13-17 trimmed of ~975 frames). It
  now keeps up to 1 s, steers the delay ahead of each frame (queue plus inject track) to what the recent Bluetooth
  hold-ups and the track's mixer need (5-60 ms), and corrects it where it cannot be heard: quiet frames are cut or
  repeated, and only without a pause for 0.5 s is speech sped up or slowed down by at most 3 %. Clock drift of
  ±2 % is absorbed the same way. Real-iPhone test calls: 0 frames lost; the call statistics show what was cut,
  added, sped up or slowed, the delay ahead and its target, and the capture's and track's sample clocks.
- The uplink starts when the Android phone's audio path is open: frames the iPhone sent before (0.3-1 s while the
  interception API waited for the call's audio mode; it is now polled every 30 ms instead of 150 ms) only delayed the
  first words; they are no longer forwarded.
- Call audio: the Android phone no longer throws away far-end voice when its capture delivers in bursts (its delay
  guard trims only standing delay and only audio already buffered: 1.3 s lost per 6.5 s test call before, 0.07 s
  after). The iPhone's playout depth follows the spread of frame arrival times (periodic Bluetooth hold-ups are
  absorbed instead of concealed: 1 s → 0.2 s concealed per minute on a simulated 30 ms link with 60-77 ms gaps), and
  frames lost for lateness are filled with Opus concealment (libopus decoding).
- iPhone playout: the statistics show the playout delay the listener really had (the buffer's level: 44-46 ms on
  the reference link, 0.3-1.1 % concealed) instead of the target (105-110 ms, which overstated it). The arrival
  spread is measured around the far clock's own rate (a drift of 2 % used to hold the target at its maximum, or run
  the buffer dry every few seconds), arrival times are taken when a frame comes off the link rather than when the
  audio queue gets to it, and the first 2 s of a call no longer count (a ~260 ms hold-up while the connection
  interval is shortened kept the target at its maximum for 10 s); a slow far clock is made up for in quiet frames.
- Android client: the iPhone's playout tuning ported (arrival-spread depth around a drift line, first 2 s left out,
  quiet-frame fill for a slow far clock, concealment of frames dropped for lateness, arrivals stamped off the link,
  the same call statistics); its trace tests give the same numbers as TetherCore's.
- During a bridged call the Android phone is silenced on more phones: Samsung (as before), Qualcomm (voice-call
  stream mute plus microphone mute) and MediaTek (its speech mute controls; never Android's microphone mute, which
  there also mutes the iPhone's voice); other phones get the earpiece muted. The Link tab shows "Android phone
  silent".
- Call statistics from the Android phone (frame spacing from the helper, link queue delay, uplink arrival, PHY,
  helper figures) arrive in the iPhone's Link log after each call (`audio.stats`). The call-audio self-test measures
  each uplink path's delay.
- TetherCore carries libopus 1.5.2 (`COpus` target) with `LibOpusDecoder` / `LibOpusEncoder`: Opus with packet-loss
  concealment and complexity control on the iPhone, checked against AudioToolbox's Opus both ways.
- Calls: the fast Bluetooth connection interval is requested at the first ring, and the iPhone's playout buffer
  adapts to bursty arrival (about 1 % of a call concealed instead of 4-6 %).

### Platforms

- Android 10 and later are supported (was 12): call audio is attempted on Android 11 and 12 (voice-call capture and
  the incall-music inject path) and the app says clearly when the phone cannot bridge call audio (Android 10). Version
  guards for the newer Bluetooth, telephony, notification and Telecom APIs, legacy Bluetooth permissions below 12, and
  a lint check that fails on unguarded newer APIs. Feature table: [docs/devices/android-versions.md](docs/devices/android-versions.md).
- Opt-in TCP debugging fallback for the audio helper: where Wireless debugging is missing (Android 10) or fails,
  Tether connects to the phone's adbd in `adb tcpip` mode on `127.0.0.1:5555` (port settable, key approval). Setup in
  [docs/SETUP.md](docs/SETUP.md); the security trade-off is in [SECURITY.md](SECURITY.md).
- The iPhone app runs on iOS 16 (it needed 17): Observation comes from swift-perception, a few iOS 17 views have
  iOS 16 stand-ins; iOS 17 and later behave as before. The Swift package needs network access at build time.

### Link

- Stable link while the phones are also paired for Bluetooth tethering (the Android phone holds an LE link during a
  session, so the session runs over LE / L2CAP and does not move between links).
- Reconnects after the app is closed and reopened (within ~1-3 s), after a link drop while the app is in the
  background (under 1 s), and after the Android app restarts.
- A suspended iPhone is woken after 15 minutes of silence instead of having its session dropped (it relinked,
  handshook and synced every 15 minutes); a session is ended only if the woken iPhone still does not answer.
- The Android phone kept failing to advertise after many link changes ("too many advertisers"): fixed.
- The Android phone knows the client kind "android" and holds / pulses the LE link only for an iPhone (a new
  client's kind is known at its first sync).

### Fixes

- After an update the Android phone's link service starts as soon as the app's process starts, not only when Samsung
  delivers the delayed update broadcast; until then the iPhone could not link and calls were not bridged.
- The other side could not hear the iPhone (#2, Galaxy Z Fold 6): during a bridged call the Android phone also muted
  its own microphone (through Android's mic mute, and on Samsung its tx control key), and on several audio systems
  that mute silences the injected iPhone voice too. The phone's microphone now stays on by default on every phone
  (its earpiece is still silenced); a new setting "Mute this phone's microphone during bridged calls" (Android app ›
  Call audio, and a switch in the iPhone's Link tab) turns it on with one mute per vendor, and then a check during
  the call compares the iPhone's speech with what the call's uplink carries, undoes the mute when the uplink stays
  silent, and remembers that for the phone model. The self-test runs the working inject method once more with the
  mic muted, its first row and the link log say whether the uplink injection opened, with which method and with
  which mute, and the link log flags an iPhone that sent only silence. New optional `micMute`, `uplink` and `capture`
  in `status.helper`, the `audio.set` message, and the helper's LOCAL frame (protocol §5, §6); docs/TROUBLESHOOTING.md
  "The other side cannot hear me".
- The Android phone's own microphone and earpiece now leave a bridged call instead of being muted (#2): Tether
  puts the call in Android's redirect audio mode (`MODE_CALL_REDIRECT`, Android 13+; set by the audio helper, which
  Android lets own the mode over Telecom's), in which the platform stops routing the call to the phone's mic and
  earpiece while the uplink injection and downlink capture go on. So the other side hears only the iPhone (no doubled
  voice, no bag noise, no echo of their own voice from the iPhone's speaker), without the mutes that silenced the
  injected voice on some phones. Each call checks the mode: it is set once the downlink has audio, set again when
  Telecom takes it back (3 times at most), undone when the downlink goes silent in it or when the iPhone's speech stops
  reaching the uplink, and what it found is remembered per phone model; a phone that breaks it falls back to the call
  screening mode (where the phone declares it) and then to an open microphone. The setting becomes "This phone's
  microphone during bridged calls: Off during calls (default) / Muted / On" on both phones; the self-test adds a
  `mic:<mode>` row per mode (mic level, tone and downlink without and with the mode). New optional `mic` and `micPath`
  in `status.helper`, `mic` in `audio.set` (`micMute` still read), the helper's `mic` report and `redirect` memory
  (protocol §5, §6); the helper's `mode` command; docs/TROUBLESHOOTING.md, docs/devices.
- Wireless debugging pairing from the notification no longer closes the app (#1): the work runs in a short foreground
  service instead of the notification's broadcast, a wrong or expired code is reported instead of being taken as
  success, and each failure (no Wi-Fi, pairing window closed, wrong code, connected later) says what to do, in English
  and Persian.
- Dates (also Persian calendar ones), version numbers, IP addresses and amounts in texts are no longer shown as
  phone numbers on the iPhone or the Android client, or picked by "Call with"; numbers written with dots
  (555.123.4567) now are.
- Contacts: a number's custom kind ("Satellite") is shown on the iPhone and kept when a contact is edited there; the
  Android phone now stores labels sent with `contact.add` / `contact.update`, and an empty label clears one.
- Fixed by those tests: a damaged iPhone data file is now kept aside instead of being overwritten; the Android
  client no longer deletes all local texts or calls on a malformed `sync.ids` page; a `tel:` link holding only an
  extension no longer dials the extension; the debug hooks no longer accept `+10000000000`.

### Tests and tooling

- Tests for future changes: protocol fixtures read by both implementations (old and newer app variants), an iPhone
  app test target (saved-data format, message handling, links), security and framing edge cases, the A54's safety
  guards and helper login, the Android client's synced state and call rules; about 380 automatic tests in all.
  `tools/test-all.sh` runs every check, `tools/check-private.sh` scans changes for private values,
  `tools/device-test.sh` runs the test-line checks on the phone, and CI runs on every push. See docs/TESTING.md.
- The test line behaves like a real call: nothing read its injected uplink, and an unread remote submix plays it ~10 %
  slower than real time (14.4 kHz for 16 kHz), which no network does; the helper now reads it as the far end would.
- Debug: `only none` no longer stops `loopback_call`; `loopback_call selftest`, `inject_ms`.

## 1.0.0 — first public release

- Calls on the iPhone through CallKit with two-way call audio (Opus 16 kHz) on Android phones that allow call-audio
  interception; ringback while dialling, speaker, hold, mute, keypad, call waiting, moving a call between devices.
- Messages: full sync, live texts, send from either SIM, shared read state, delete, mute, block, one-time codes,
  rich text, notifications with reply.
- Contacts with favourites and editing; Recents with search, delete and a missed-call badge.
- Link tab: battery and signal per SIM, remote switches for mobile data, data SIM, hotspot, Bluetooth tethering,
  airplane mode, network mode, and Share VPN.
- End-to-end encrypted link (P-256 handshake, AES-256-GCM) over Bluetooth LE L2CAP with a GATT fallback; no
  dependence on Bluetooth pairing, so it works alongside Bluetooth tethering.
- iPhone and iPad; English and Persian; light and dark.
- Local configuration for team id, bundle id and display name; scripts that find the connected devices.
