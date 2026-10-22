# Architecture and acceptance plan

## Goals and boundaries

Expose a useful next maneuver on the wrist while keeping route data local and
transient. Require explicit consent and authenticated transport rather than
making an insecure connection merely to maximize watch compatibility.

```text
Google Maps navigation notification
  -> Android NotificationListenerService (Maps-only filter)
  -> conservative parser -> in-memory current snapshot
  -> foreground BLE GATT server (bond + explicit watch approval)
  -> authenticated, encrypted characteristic reads
  -> watch BLE Central -> fragment validation -> freshness state
  -> distance / arrow / instruction / summary / haptic
```

There is no web server, cloud endpoint, GPS permission, routing API key, phone
side-app transport or runtime analytics SDK. Google Maps itself still calculates
the route and controls the notification contents.

## Component responsibilities

| Component | Responsibility |
|---|---|
| `android\core\MapsParser` | Package/group filtering, Unicode cleanup, distance and conservative maneuver extraction |
| `android\core\PacketCodec` | Bounded UTF-8 encoding and MTU-safe fragments |
| `MapsNotificationListener` | Read only Maps extras while enabled; reconcile all active Maps notifications |
| `NavStore` | RAM-only route and last source-verification time |
| `NavigationService` | User-started connected-device foreground service, five-second source reconciliation, teardown |
| `BleServer` | Advertising, approval/bond enforcement, authenticated reads and fixed transfer snapshots |
| `TrustStore` | Private persistent watch approval and random phone discovery code, not route data |
| `watch\lib\client.js` | Discovery, explicit selection, paired profile, reads, deadlines, retry and cleanup |
| `watch\lib\protocol.js` | Strict framing/UTF-8/body validation, replay handling and stale-state suppression |
| `watch\page\index.js` | Large glanceable guidance, availability status, optional display hold and haptics |

The Android UI uses a dark green background, lime emphasis, large route preview
and explicit permission/trust actions. The round watch UI prioritizes direction
and distance, then instruction and summary. Controls have tested bezel-safe
geometry for the 480-pixel design; real text legibility still requires a watch.
Long or localized text is bounded and may not fit entirely on the screen.

## Parsing and source freshness

Only `com.google.android.apps.maps` notifications are considered. The app checks
the package before accessing extras, rejects group summaries, and generally
requires an ongoing notification with distance or recognized navigation cues.
Arrival has its own title-based recognition. This is deliberately best-effort:
Google Maps notifications are not a supported routing-data API.

English maneuver cues are distinguished from incidental street-name words.
Metric/imperial distances are extracted from titles. Other-language instruction
text can survive when a recognizable distance identifies the notification, but
the arrow remains unknown unless the maneuver is recognized. Unsupported locales
or layouts may produce no route at all rather than fabricated guidance.

Source notifications are rechecked every five seconds and on notification
events. Android marks its snapshot unavailable after more than 15 seconds
without verification. An unchanged but still active Maps notification remains
valid; this heartbeat verifies notification presence, **not GPS fix age or the
correctness/freshness of Google's routing engine**.

Each BLE transfer captures one immutable encoded snapshot. A server batch older
than five seconds is replaced. The watch publishes only complete valid packets,
clears guidance immediately on disconnect and suppresses a snapshot at 15 seconds
without a complete packet. Clock regression also invalidates watch display state.
The page checks display freshness once per second while it is executing.

## Trust and transport

The phone advertises the NavGo service UUID and a scan-response service-data
value containing a random three-byte code. The watch scans for at most 15 seconds,
keeps up to eight matching phones and requires explicit selection by code.
The code is public, can collide and can be spoofed; it is not authentication.

The watch requests a paired profile. Android's data characteristic requires
`PERMISSION_READ_ENCRYPTED_MITM`; each application-level read also requires the
currently approved address, an existing system bond and sharing to be enabled.
Approval is distinct from bonding. Only the explicitly approved watch is served.
Addresses are identifiers, not secrets. Transport authentication comes from the
system's authenticated BLE bond and enforced link encryption.

The first-pairing ceremony depends on firmware supporting a real authenticated
passkey exchange. A `pair: true` profile alone does not prove MITM protection.
The Android stack must enforce the characteristic permission. Unsupported
security must fail closed; do not change it to an unencrypted characteristic or
accept Just Works pairing as an equivalent.

The watch uses the selected address only in RAM, reconnects to that selection
with exponential backoff capped at 30 seconds, and never silently switches phone.
Connection has a 20-second watchdog; pairing/approval errors have a two-minute
window; individual reads have a four-second watchdog. Closing the page unregisters
callbacks and destroys/disconnects its BLE resources.

No application-layer password, bespoke cryptography or key-storage scheme is
invented. The design does not protect against a compromised phone/watch, an
incorrectly approved device, shoulder surfing or the platform vendor's behavior.

## Wire protocol, version 1

Service UUID: `a8e70001-8d9b-4c2f-a51b-2e52d72f6300`.
Read-only characteristic UUID: `a8e70002-8d9b-4c2f-a51b-2e52d72f6300`.
No notifications, writes or long reads are used.

Each read returns the next frame, at most 20 bytes, compatible with ATT MTU 23.

| Frame offset | Bytes | Meaning |
|---|---|---|
| 0 | 1 | Magic `0x4e` |
| 1 | 1 | Protocol version `1` |
| 2 | 2 | Unsigned sequence, big-endian, modulo 65536 |
| 4 | 1 | Zero-based fragment index |
| 5 | 1 | Fragment count, 1 through 33 |
| 6 | 1-14 | Body fragment; all nonfinal fragments use 14 bytes |

Reassembled body:

| Order | Encoding / encoder limit |
|---|---|
| State | One byte: IDLE=0, NAVIGATING=1, ARRIVED=2, UNAVAILABLE=3 |
| Turn | One byte, values below |
| Distance | UTF-8, at most 32 bytes, followed by NUL |
| Instruction | UTF-8, at most 280 bytes, followed by NUL |
| Summary | UTF-8, at most 144 bytes, followed by NUL |

Turn values: UNKNOWN=0, STRAIGHT=1, LEFT=2, RIGHT=3, SLIGHT_LEFT=4,
SLIGHT_RIGHT=5, SHARP_LEFT=6, SHARP_RIGHT=7, U_TURN=8, ROUNDABOUT=9,
MERGE=10, EXIT=11.

The body limit is 461 bytes, including state, turn and terminators. The encoder
truncates at Unicode code-point boundaries. The decoder enforces the total body
limit, valid UTF-8, enums and exactly three terminated fields. It rejects malformed
headers/counts and never publishes partial, duplicate or out-of-order fragments.
A sequence is newer than the completed sequence only when its modulo-65536 delta
is between 1 and 32767; a fresh connection resets sequence history.

Reads are spaced about 80 ms during transfer and one second after a completed
snapshot. A maximal packet takes multiple seconds. This is responsive glanceable
guidance, not hard real-time navigation; physical latency must be measured.
Replay checks prevent accidental stale frame reuse within a connection and do
not replace BLE authentication.

## Physical acceptance matrix - not yet executed

Record phone/OS, watch model, firmware/API, Maps version/language, connection
conditions and observed results. Do not collect or publish real route text in
debug logs; use a consented test route and sanitized fixtures.

| Area | Required observation before release |
|---|---|
| Authenticated pairing | Correct passkey ceremony, usable encrypted reads, repeated connection while the normal Zepp connection is present |
| Negative access | Unbonded, unauthenticated and bonded-but-unapproved clients cannot read route bytes; incorrect passkey fails |
| Approval/revocation | Unknown watch not served, second watch rejected, Forget/Stop stops subsequent reads and clears phone route |
| Real notifications | Actual Maps walking/driving layouts, distance changes, long unchanged highway maneuver, Unicode, units and unsupported locales |
| Route lifecycle | Starting mid-route, route replacement, reroute, notification removal, ending route and arrival never leave misleading old guidance |
| Source unavailable | Revoked listener access, stopped listener or killed phone process removes live indication within the freshness budget |
| Connection recovery | Bluetooth off, range loss, disconnect mid-fragment, phone restart and watch-page close clear guidance; retry never switches phones |
| Permission lifecycle | Denied/revoked Nearby devices and notification permissions produce explicit recovery guidance, not crashes |
| Display lifecycle | Screen-off, wrist-drop, one-hour hold expiry, other watch apps and workout coexistence; document actual suspension limits |
| Timing | Measure notification-post-to-wrist latency; proposed acceptance p95 <=5 seconds in normal connected operation; test worst-case body separately |
| Freshness | Disconnect clears on callback; phone-source timeout plus next transfer does not exceed 20 seconds; watch packet loss clears within 16 seconds while page is running |
| Battery/resources | Measure at least one hour with screen auto and hold, compare phone/watch baselines; proposed screen-auto incremental drain <=5 percentage points/hour on each device |
| UI | Readable stationary and walking; long text/Unicode; controls inside bezel; no haptic repeat on distance-only heartbeat |

The timing and battery figures are **proposed acceptance thresholds, not measured
results**. Adjust product requirements explicitly if the hardware cannot meet
them; never weaken encryption to meet a performance target.

## Subsequent phases

1. Prove the direct authenticated BLE link on T-Rex 3 or reject this transport for
   that firmware. A different transport would be a separate design decision.
2. Capture consented notification layouts and add narrowly targeted parser tests
   for demonstrated failures and additional locales.
3. Measure latency/battery, validate screen lifecycle and optimize only measured
   bottlenecks while preserving freshness and explicit error handling.
4. Add display targets and validate each advertised watch model; do not infer
   compatibility solely from a successful package build.
5. Register app identities, configure release signing, review current distribution
   policies and complete independent security/privacy review before publication.
