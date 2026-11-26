# The Android client

`android/client` is an app for a **second Android phone** that does what the iPhone app does: it links to the
Android phone that holds the SIM cards (the one running Tether, `android/app`) and shows its calls, texts, contacts
and call history. The SIM phone stays in a bag; you use the other phone.

| | |
| --- | --- |
| Application id | `app.tether.client` (name "Tether Client"); installs next to `app.tether.android` without touching it |
| Android | 10 (API 29) or later: Bluetooth LE central with L2CAP channels (`createInsecureL2capChannel`); GATT pipe as fallback |
| Client kind | `"android"` in `sync` (protocol §5, §5.1) |
| Languages | English and Persian |

## What it does

- **Pairing**: scans the QR code the SIM phone shows (Tether › Pair) with the camera (ZXing), or takes the code
  pasted by hand (`tether1:…`; the QR code holds `tether://pair?c=…` since 1.2, both are read). Same handshake, keys and token as the iPhone (protocol §3).
- **Link**: scans for the service UUID and also tries the address it last reached the phone at, connects, reads the
  PSM, opens an L2CAP channel (6 s, then the GATT pipe), runs the session, and reconnects for ever with the iPhone's
  rules: at once after a failure, a session restarted on a Bluetooth connection that is still up (twice in a row at
  most), a 12 s setup watchdog, a 10 s connect watchdog, a stall check (a record half received for 10 s), back-off only
  when setup keeps failing (at most 30 s), REJECT 2 → retry after a minute, REJECT 4 → after 5 s, REJECT 3 while
  pairing → "code expired". It pings every 25 s; unlike an iPhone app it is never suspended, so 80 s of silence
  (plus a ping and 10 s of grace) ends a dead session. A foreground service (connected device) keeps it running.
- **Calls** as self-managed Telecom calls: Telecom routes the audio (earpiece, speaker, wired and Bluetooth headsets,
  the car) and shows the calls to Bluetooth devices; the app rings through its own full-screen notification and has
  its own call screen (answer, decline, mute, audio route, hold, keypad, end; screen off at the ear). The rules of the
  iPhone's CallController are ported unchanged (`CallTracker`): ring first, the hang-up guard against late states,
  a dial hung up before the phone reported it is ended as soon as it shows up, a dial the phone never reports fails
  after 30 s, mirrored answers and holds done on the SIM phone, `elsewhere` ends the local call ("answered
  elsewhere") with a **Move here** banner (`call.take`), and local calls end when the link goes.
- **Calls in the Phone app** (Link › Calls, "Use the Phone app for calls", on by default): the SIM phone's SIMs become
  calling accounts of this phone (`CAPABILITY_CALL_PROVIDER`, one per SIM on a dual-SIM phone, served by
  `TetherPhoneConnectionService`). The Phone app rings, shows the call, dials out from its keypad, contacts and history,
  and logs the calls. The user turns the accounts on once in the Phone app's calling accounts. Until then, or with the
  switch off, calls use the self-managed account and this app's call screen (`TelecomBridge.accountFor`). Only the
  local Telecom side changes: the messages on the link are the same. Accounts stay registered while the switch is on
  (removing one makes Android forget it was turned on). The call screen can move a call shown there to the Phone app
  (`moveToPhoneApp`: the same call reported again on a Phone-app account, the old connection dropped without a word to
  the SIM phone), and the link log says where each incoming call went.
- **Call audio** both ways: `AudioRecord` (VOICE_COMMUNICATION, with the platform's echo canceller, noise suppressor
  and gain control) → Opus (Concentus, the codec code shared with the SIM phone) → link; link → Opus → the playout
  buffer ported from TetherCore (same numbers, same tests) → `AudioTrack` (voice communication). A local ringback
  plays while dialing until the far end is audible. The microphone is read but not sent until a call is connected (the iPhone's
  rule); a refused microphone permission, a recorder that does not open, or 6 s of exact silence in a connected call (as on the iPhone) show a warning on the
  call screen and the main screen, with a `microphone:` line in the Link log, and the permission is asked for again when
  a call starts. Call statistics go to the Link log at the end of each call.
  The playout matches the iPhone's tuning of 2026-11-23: arrivals stamped off the link, depth from the arrival
  spread around the far clock's drift (first 2 s left out), frames dropped for lateness filled by Opus concealment.
- **Keypad** with DTMF during calls; **Recents** grouped by day (day cards, a count for calls in a row from one number,
  a date bubble while scrolling; `model/RecentsLayout.kt`), with search, delete (one, picked calls or a whole day), clear and a missed-call badge; **Contacts** with
  Favourites (search, add, edit, star, block); **Messages** (a list with unread counts and dividers; conversations under a heading per day, pinned while
  scrolling, each line in its own direction, the time beside each text, an "Unread messages" line where it opens; `model/ThreadLayout.kt`; send from either SIM, outbox while out of
  reach, "Not delivered" for a text whose answer was lost, read state both ways, delete, mute, block).
- **USSD codes** (Keypad › USSD codes, or a code such as *123# sent with a call button): choose the SIM, run the code on
  the SIM phone (`ussd.send` / `ussd.result`), answer a menu (open on the SIM phone: `ussd.reply` / `ussd.cancel`;
  else a new code that carries the choice, *123# then 2 sends *123*2#), and send recent codes again
  (`ui/UssdActivity.kt`, `model/Ussd.kt`). A code dialed in the Phone app with Tether's account opens this screen.
- **Notifications** for texts (with reply and mark-read) and missed calls.
- **Link screen**: the SIM phone's battery and signal per SIM, the connected devices, the switches (mobile data,
  data SIM, hotspot, Bluetooth tethering, airplane mode, network mode per SIM, Share VPN), the audio self-test, the
  Link log, **Send a problem report** (one `.txt` file for an issue, numbers and names removed), pairing and unpairing.
- **tel: links**: `ACTION_DIAL`, `ACTION_VIEW` and `ACTION_CALL` for `tel:` are registered, so tapping a number in
  another app offers "Call with Tether Client" (the keypad, or a call at once for `ACTION_CALL` while linked).
- Numbers are shown with `PhoneNumberUtils.formatNumber` and this phone's country, left-to-right in Persian too.

## Not done (yet)

- No one-time-code detection or rich text in messages, no contact photos larger than the synced thumbnails, no
  contact e-mail editing (e-mails are kept as synced).
- No video, MMS, RCS or group messages (as on the iPhone).
- A call answered on the SIM phone itself whose audio comes to this client while it is in the background may have a
  silent microphone: Android lets a background app start a microphone service only in some cases. Calls answered on
  this phone (its call screen or notification) are not affected.
- The UI is plain Android views built in code, not Material components. Its buttons come from `ui/Buttons.kt`
  (ripples, a 48dp minimum, TalkBack labels; icons in `res/drawable/ic_*.xml`, tinted from the palette).

## Building

```bash
cd android
./gradlew :client:testDebugUnitTest :client:assembleRelease
adb -s <the client phone> install -r client/build/outputs/apk/release/client-release.apk
```

Same JDK, SDK, Maven mirror and optional signing key as the SIM phone's app (see [BUILD.md](BUILD.md)).

## First run

1. Install and open it; allow Nearby devices (Bluetooth), the microphone and notifications.
2. Link › Pair: on the SIM phone open Tether and tap **Pair**, then point the camera at the code.
3. Link › **Allow running in the background** (battery optimisation off), so the link survives Doze.
4. Calls from Recents, Contacts or the Keypad go out through the SIM phone; incoming calls ring here.

The SIM phone accepts the iPhone and this client at the same time (protocol §5.1). Call audio goes to the device that
answered or dialed; the other one shows the call as "on …" with **Move here**.

## How it is built

| Part | Where |
| --- | --- |
| Session (handshake, sealed records, ping, stall) | `link/CentralSession.kt`, on the shared `android/core` (`CentralHandshake`, `SecureSession`, `RecordReader`) |
| Reconnect rules | `link/LinkPolicy.kt` (`ClientLinkPolicy`, pure, unit-tested) |
| Bluetooth (scan, GATT, L2CAP, GATT pipe) | `link/BleCentral.kt` (one handler thread) |
| Identity (static key, pairing) | `link/Identity.kt` (Android Keystore key on Android 12+, else a software key), `LinkJournal` |
| Synced state (port of the iPhone's AppModel) | `model/ClientState.kt`, routing in `model/ClientCore.kt` |
| Calls | `calls/CallTracker.kt` (port of CallController, pure), `calls/Telecom.kt` (ConnectionService), `calls/CallAudio.kt`, `calls/Playout.kt` |
| UI | `ui/` (tabs, conversation, call screen, pairing scanner, tel: links) |

## Tests

There is no second Android phone or Bluetooth on the build machine, so the client's stack is tested on the JVM
against a fake SIM phone built from the shared core's peripheral handshake (`src/test/.../Harness.kt`): the
protocol vectors, pairing and every REJECT, a full sync in GATT-sized chunks, sending a text (placeholder → real row),
the outbox on reconnect, an incremental sync with deletions from `sync.ids`, ping round trip, a stalled stream, an
incoming call answered here with Opus audio both ways and hung up, an outgoing call, a call answered elsewhere and
moved back, a dropped link ending local calls and `call.list` bringing them back; and the ported rules (playout
buffer, hang-up guard, reconnect policy, search).

What needs a real second phone: Bluetooth itself (scan, L2CAP, the GATT pipe, reconnects through address changes),
Telecom (ringing, Bluetooth headset and car routing), audio quality and echo cancellation, notifications, the camera
scanner, and battery life with the link up.
