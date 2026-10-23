# Architecture and acceptance plan

## Decision

Version 0.2.0 replaces independent BLE Central/GATT discovery with Zepp-managed
messaging. On the test T-Rex 3 the old watch scanner did not discover the phone,
even though a PC received the phone's advertisement. Reusing Zepp's existing
connection avoids that discovery path; it does not itself prove compatibility.

```text
Maps notification -> Maps-only listener -> parser -> RAM snapshot
  -> loopback-only HTTP server, authenticated with setup key
  -> NavGo Side Service in Zepp -> official ZML request/response
  -> watch validation/freshness state -> navigation page
```

The Nark project was architectural reference only; no unlicensed implementation
was copied. ZML 0.0.41 is the official Apache-2.0 runtime. It requires watch API
3.6+. Zepp handles device transport; NavGo no longer advertises, scans, maintains
system bonds, approves Bluetooth addresses or claims independent MITM pairing.

## Components and lifecycle

| Component | Responsibility |
|---|---|
| MapsParser | Source-package/group filtering, text cleanup, conservative maneuvers |
| PacketCodec | Bounded, code-point-safe UTF-8 body encoding |
| NavStore | RAM snapshot, source timestamp, relay status |
| NavigationService | User-started data-sync foreground service and five-second source reconciliation |
| LocalNavServer | Fixed loopback listener, bounded authenticated GET requests |
| BridgeKey | Private 32-byte SecureRandom setup key; reset/revocation |
| watch/lib/bridge.js | Fixed local fetch, key handling, response checks, single outstanding fetch |
| watch/app-side | Official Zepp messaging lifecycle; generic HTTP proxy disabled after ZML initialization |
| watch/app-settings | Validated key entry/removal; never redisplay saved key |
| watch/lib/client.js | Watch-driven request correlation, deadlines, polling, teardown |
| watch/page/index.js | Navigation, haptics, retry and optional one-hour screen hold |

Sharing starts only from an explicit user action. Stop/timeout destroys the
listener, closes sockets, stops worker threads and clears the snapshot.
The service uses START_NOT_STICKY with no boot receiver. Its dataSync foreground
type has no invented Bluetooth permission prerequisite, but Android 15+'s
six-hour-per-24-hour background allowance applies. onTimeout stops promptly.

The watch requests snapshots while its page executes, at one-second ticks with
at most one outstanding request. Close stops polling and ignores late replies.
The Side Service has no unconditional polling/background push loop.

Watch 0.2.2 calls `setWakeUpRelaunch({ relaunch: true })` when the navigation page
builds. Teardown deliberately does not disable it, because screen-off can destroy
the page. A rebuilt page creates a fresh client and displays no old guidance
until a current response arrives. This is the documented screen-off/wake
mechanism, not a notification-dismiss callback or a foreground lock. Notification
interruption recovery is still a hardware gate. Zepp-only app alert filtering
and user-controlled watch DND are documented mitigations; Android Maps
notifications remain enabled. No automatic DND changes or relaunch-timer loop.

## Trust and request limits

- Bind only to 127.0.0.1:27341, never all interfaces or a LAN address.
- GET /v1/navigation?requestId=... requires Authorization: Bearer <setup key>.
  The key is not placed in URLs, responses, logs or watch messages.
- A 256-bit key is encoded as 64 lower-case hex characters and copied manually
  into Zepp settings. Android clipboard data is marked sensitive. Reset stops
  sharing and replaces the key; removing a Zepp copy is not global revocation.
- Constant-time comparison precedes route access. Nonmatching Host, Origin,
  request bodies, transfer encoding and duplicate headers are rejected.
- Headers are capped at 4096 bytes with a two-second read deadline. Two workers
  and four queued requests bound concurrency. Connections close after one reply.
- Responses are no-store and contain only a bounded snapshot plus echoed ID.
  There is no arbitrary upstream URL; ZML's generic HTTP proxy is disabled.
- Side Service uses a 2.5-second application deadline even if native fetch
  ignores timeout. It keeps its busy gate until the native fetch settles.
  Thus a hung fetch needs Zepp restart but cannot accumulate new native fetches.
- Watch messages have a four-second request timeout and reject late results,
  mismatched request IDs, unsupported versions and malformed bodies.

This is **bearer-key client authorization, not mutually authenticated TLS**.
HTTP is plaintext inside the phone. Zepp, the OS, clipboard/input method and
devices are trusted. Other local processes can attempt denial of service; a
malicious app occupying the fixed port while NavGo is stopped could impersonate
the endpoint and capture the key or forge guidance. Rooted/compromised devices,
malicious installed apps and platform/vendor behavior are outside the protection
offered here. Do not market this as equivalent to the abandoned verified-MITM
BLE design. Do not log or publish real keys/routes during diagnosis.

## Source freshness

Only com.google.android.apps.maps extras are processed, and only while sharing
is enabled. The listener checks package before reading extras, rejects group
summaries and reconciles active notifications on events and every five seconds.
The parser is best-effort; Google Maps notifications are not a supported routing
data API. Unsupported layouts produce unavailable/no-route state rather than
invented guidance.

Android returns unavailable at 15 seconds without source verification. An
unchanged active notification remains valid: this verifies notification presence,
not GPS fix age or the correctness of Google's routing engine.

The watch accepts only a response for its current request inside four seconds,
with no backward clock movement. Errors clear its state. No cached/late reply
may refresh guidance. A 15-second display watchdog also suppresses old guidance;
the page checks once per second while executing. A stopped/suspended watch page
cannot guarantee timely redraw. Stop cannot recall an already delivered packet.

## Wire body, version 1

HTTP response: {"version":1,"requestId":"<echo>","body":"<lower-case hex>"}.
The request ID is 1-64 lower-case ASCII letters/digits/hyphens. Watch IDs combine
a session prefix and incrementing counter; correlation is not authentication.

The body retains the bounded format from the original implementation, without
BLE fragment framing:

| Field | Encoding/limit |
|---|---|
| State | Byte: IDLE=0, NAVIGATING=1, ARRIVED=2, UNAVAILABLE=3 |
| Turn | Byte: UNKNOWN=0, STRAIGHT=1, LEFT=2, RIGHT=3, SLIGHT_LEFT=4, SLIGHT_RIGHT=5, SHARP_LEFT=6, SHARP_RIGHT=7, U_TURN=8, ROUNDABOUT=9, MERGE=10, EXIT=11 |
| Distance | UTF-8, <=32 bytes, then NUL |
| Instruction | UTF-8, <=280 bytes, then NUL |
| Summary | UTF-8, <=144 bytes, then NUL |

Maximum 461 body bytes / 922 hex characters. Both phone-side JavaScript and watch
validate version, ID, enums, UTF-8, total and per-field bounds, and exactly three
terminated fields. The Side Service also caps string responses at 2048 characters.
Native fetch owns initial allocation; this limit is validation, not a streaming
memory bound against an impersonated local endpoint.

## Physical acceptance gates

Record phone/OS, watch model/firmware/API, Zepp and Maps versions, language and
sanitized observations. Building packages is not completion of these gates.

On October 23, 2026, the tester confirmed key setup and live Google Maps text on
the T-Rex 3, but reported that mirrored phone notifications displaced NavGo and
returned to the watch face. This confirms basic forwarding, not full acceptance.

| Area | Required observation |
|---|---|
| Installation | Complete 0.2.2 preview includes device, Side Service and settings; watch API >=3.6 |
| Setup | Key entry in Zepp settings, normal Zepp pairing, guidance without NavGo scanning |
| Local policy | Zepp native fetch reaches loopback on the tested phone; no LAN/remote fallback |
| Auth | Missing/wrong key yields no route; reset invalidates old key; stop closes listener |
| Notifications | Actual Maps walking/driving, start mid-route, reroute, long unchanged maneuver, arrival/removal |
| Freshness | Stop sharing, disconnect, listener loss, malformed response and delayed reply never restore old guidance |
| Recovery | Bluetooth off/on, Zepp killed/restarted, watch closed/reopened, phone reboot, key change |
| Profiles | Only intended main-profile installation active; no clone port collision |
| Background | Screen off, wrist drop, another app/workout, battery restrictions and Android foreground timeout |
| Riding interruptions | With Zepp Maps alerts disabled, Maps updates still reach NavGo; with watch DND, test unrelated alerts and turn vibration; test return-on-wake and explicit exit separately; notification-dismiss return is not assumed |
| Timing | Proposed p95 notification-to-wrist <=5 seconds; not measured |
| Expiry | Proposed source-loss-to-not-live <=20 seconds and watch-only expiry <=16 seconds while executing |
| Battery | Measure one hour screen-auto/hold; proposed incremental drain <=5 percentage points/hour per device |
| UI | Long/localized text, bezel-safe controls, no repeated vibration on distance-only heartbeat |

Timing and battery values are proposed acceptance thresholds, not measured
results. Broader device support, signed releases and store publication require
successful hardware acceptance and independent security/privacy review.
