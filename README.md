# NavGo

Local Google Maps turn-by-turn guidance for Zepp OS watches, targeting the Amazfit
T-Rex 3 first. A native Android notification listener forwards guidance directly
to the watch over authenticated, encrypted Bluetooth LE.

**Status: build-verified prototype, not hardware-validated navigation software.**
The Android APK and watch package build successfully. No physical phone or watch
was connected during development. In particular, authenticated direct BLE pairing
on T-Rex 3 firmware is an outstanding release gate, not a proven capability.
Do not rely on this prototype for safety-critical navigation.

## What is implemented

- Google Maps notification parsing, distance, instruction and route summary.
- Twelve maneuver symbols, arrival indication and instruction-change vibration.
- Explicit phone selection, system pairing and approval of one watch.
- Android foreground service with a stop action and watch reconnection.
- Unavailable/stale-state display instead of leaving an old turn marked live.
- No NavGo account, cloud service, analytics, tracking or route history.
- No Android internet, location, microphone or contacts permission.

This is a notification companion, not a routing engine. It does not calculate
routes, access watch GPS, show maps, provide lane guidance or supply offline
navigation. Google Maps and the Zepp app retain their own independent privacy
practices. See `PRIVACY.md`.

## Compatibility

| Component | Current requirement |
|---|---|
| Phone | Android 8.1/API 27 or newer, BLE peripheral advertising and GATT server support |
| Watch API | Zepp BLE Central APIs at API level 3.0 or newer, plus authenticated pairing support |
| Packaged display | 480 x 480 round; other sizes need manifest targets, assets and layout validation |
| Primary target | Amazfit T-Rex 3; actual firmware compatibility remains unverified |
| Other Zepp watches | Conditional on APIs, pairing, CPU/display package and physical testing; not universal |
| Notification language | English maneuver recognition; metric/imperial units; other text preserved when recognizable distance is available |

Older watches without BLE Central are not supported by this transport. This app
does not tunnel the Android companion through the Zepp side-app bridge. A phone's
existing Zepp connection is not sufficient evidence that NavGo's separate secure
GATT connection will work.

## Build

The project uses Java 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Android SDK
35/build-tools 35.0.0 and Node/npm. There are no third-party Android runtime
libraries. Node packages are development tools, not watch runtime dependencies.

From the project directory on the provisioned Windows workstation:

```powershell
$root = 'C:\Users\harv\NavGo'
$env:JAVA_HOME = (Get-ChildItem "$root\.tools\java" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$root\.tools\android-sdk"
$env:GRADLE_USER_HOME = "$root\.tools\gradle-home"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "$root\android"
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug --console=plain --no-daemon
Set-Location "$root\watch"
npm run assets
npm run check
npm test
npm run test:interop
npm run build
```

On a fresh checkout, install Java 17 and the Android SDK separately, set
`JAVA_HOME` and `ANDROID_HOME`, and run `npm ci` in `watch` once before the watch
commands. The ignored `.tools` directory is only a local convenience, not part
of the source distribution. SDK installation requires accepting Google's terms.
The interop test requires the preceding Gradle test task to compile its fixture.
Use a supported Node version that can run `node --test`.

Artifacts:

```text
android\app\build\outputs\apk\debug\app-debug.apk
watch\dist\1099901-NavGo-0.1.0-<build timestamp>.zab
android\app\build\reports\lint-results-debug.html
```

The APK is debug-signed and is not a production release. Register a unique Zepp
app ID and replace development placeholder `1099901` in `watch\app.json` before
publication. Configure Android release signing outside the repository before
distributing a production APK.

Watch build notes: CLI 1.7.1 is pinned because the newer version encountered
during development referenced an unavailable package. `tools\zeus.cjs` fixes its
bundled module alias without patching dependencies. Sharp is pinned through an
override for the Windows ARM64 build environment. `--ip false` omits intermediate
source products; the inspected package contains device bytecode, assets and
manifests, not tests, Node tools or side-app JavaScript. The packager still emits
`import.meta` warnings while scanning Node-only test/tool files and a generic
intermediate-product notice; those files are not in the inspected deliverable.
Android lint currently reports zero errors and four localization warnings.

## Install and connect

1. Install the APK on an Android phone. With USB debugging explicitly enabled:
   `adb install -r android\app\build\outputs\apk\debug\app-debug.apk`.
   Keep Bluetooth enabled.
2. Install the watch application using the Zepp development workflow. From
   `watch`, run `npm run zeus -- login`, then `npm run zeus -- preview --ip false`.
   Enable Developer Mode in the Zepp phone app and use its Mini Program QR
   scanner to install the preview on the paired watch. Account/device eligibility
   and installation have not been tested here; follow Zepp's current guidance.
3. Open NavGo on Android. Grant Nearby devices permissions and, on Android 13+,
   notifications for the persistent sharing notification.
4. Read the notification-access disclosure, open Android's notification settings
   and explicitly enable NavGo. Android may require allowing restricted settings
   for a sideloaded APK; only do so for the build you inspected and trust.
5. Tap **Start sharing**. Note the six-character **PHONE CODE**.
6. Open NavGo on the watch. Scan, use **Next phone** to match that code, then tap
   **Connect**. The code is only a discovery identifier, not a password.
7. Complete authenticated system Bluetooth pairing. On Android, use **Pair
   pending watch** if needed. Verify matching system passkeys, then tap **Approve
   paired watch** only for the device you just verified. Do not approve an
   unfamiliar device or downgrade security to make pairing work.
8. Start Google Maps navigation on the phone. Keep the watch's NavGo page open.
   Test initially while stationary, not while operating a vehicle.

Pairing/service setup can take up to two minutes. If the firmware cannot perform
authenticated pairing or encrypted reads, guidance must remain unavailable;
there is deliberately no plaintext or unauthenticated fallback.

The watch app is a **foreground page**, not an always-running background watch
service. Screen suspension, another app or an activity may interrupt updates.
**Screen: auto** preserves normal display behavior. Tapping it requests up to
one hour of bright-screen/drop-wrist hold; **Screen: ON 1h** consumes more battery.
It is not an indefinite background-execution guarantee.

Use **Stop sharing** or the foreground notification's stop action to stop
advertising and clear the route. **Forget approved watch** also removes NavGo's
approval; it does not remove Android's system Bluetooth bond. Revoke notification
access in Android settings when no longer wanted.

## Troubleshooting

| Symptom | Check |
|---|---|
| No phone in watch scan | Start Android sharing first; check BLE advertising support, permissions and Bluetooth; rescan near the phone |
| Code is wrong | Use Next phone; never select another phone automatically |
| Pairing/secure read fails | Verify the system passkey and app approval; firmware may not support the required security |
| Ready but no route | Start actual Maps navigation; allow its notifications; inspect the Android preview; unsupported notification layouts can be rejected |
| Unknown arrow | Text was retained without a confidently recognized English maneuver; check the phone |
| NOT LIVE / CHECK PHONE | Route source, connection or freshness is unavailable; old guidance is not authoritative |
| Stops after leaving watch page | Expected foreground-page limitation; keep NavGo open and test screen behavior on-device |
| Android stops in background | Inspect OS battery restrictions and foreground-service status; no boot auto-start is implemented |
| Watch was forgotten | Rescan, pair if needed, explicitly approve again; system bond and app approval are separate |

## Validation and delivery plan

Completed: protocol/design, Android implementation, watch implementation, 12 Java
unit tests, 15 watch tests, one real Java-encoder-to-watch-decoder test, JavaScript
type checking, Android debug build/lint and watch bytecode/package inspection.
Tests cover synthetic notification layouts, UTF-8 and bounds, fragmented delivery,
sequence replay/wrap, BLE lifecycle mocks, stale-display clearing and round-screen
button geometry. They do not prove live notification or firmware behavior.

Next release gate: execute the physical acceptance matrix in `DESIGN.md` on a
T-Rex 3 and an Android phone. Only after secure pairing, notification compatibility,
screen/background behavior, latency and battery measurements pass should broader
device targets, additional locales and signed store releases be considered.

Official implementation references:

```text
https://docs.zepp.com/docs/reference/device-app-api/newAPI/ble/mstStartScan/
https://docs.zepp.com/docs/reference/device-app-api/newAPI/ble/mstBuildProfile/
https://docs.zepp.com/docs/guides/tools/cli/
https://developer.android.com/reference/android/service/notification/NotificationListenerService
https://developer.android.com/reference/android/bluetooth/BluetoothGattCharacteristic
```
