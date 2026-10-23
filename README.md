# NavGo

Local Google Maps turn-by-turn guidance for Zepp OS watches, targeting the Amazfit
T-Rex 3 first. Version **0.2.0** replaces independent BLE discovery/pairing with
the watch's existing Zepp connection:

```text
Google Maps notification -> Android NavGo -> authenticated loopback HTTP
  -> NavGo Side Service inside Zepp -> Zepp messaging -> watch NavGo
```

**Status: prototype with basic live forwarding confirmed by the tester.** On
October 23, 2026, Google Maps instructions appeared on the test T-Rex 3 through
the Zepp bridge. Notification interruptions still displaced the navigation page;
uninterrupted riding, recovery, timing and battery acceptance are not complete.
Do not rely on it for safety-critical navigation. No Nark source code was copied.

## Features and requirements

- Maps notification parsing, distance, instruction, summary and twelve maneuver icons.
- Instruction-change vibration, arrival indication and stale-guidance clearing.
- Explicit sharing controls and a revocable 256-bit setup key.
- No NavGo cloud endpoint, analytics, tracking, account or route history.
- Android 8.1/API 27 or newer; Android INTERNET permission for the local socket,
  not external requests. No location or Bluetooth-advertising permissions.
- Zepp app on the same Android phone/profile, with the watch already paired.
- Watch API **3.6+**, required by the pinned official ZML runtime. Currently
  packaged for a 480 x 480 round display; other watches require their own targets
  and physical validation. This is not universal Zepp compatibility.
- English maneuver recognition is best-effort. Other-language text can survive
  when a recognizable distance is present, but an unknown arrow is not guessed.

NavGo is not a routing engine. It does not calculate routes, access watch GPS,
show maps, provide lane guidance or supply offline navigation. Zepp is now part
of the navigation data path. See `PRIVACY.md` for the trust boundaries.

## Install and connect

Use Android **0.2.0+** and watch preview **0.2.2+**; the old scan/pair builds
cannot communicate with this version.

1. Install the APK on the main Android profile with USB debugging enabled:

   ```powershell
   $root = 'C:\Users\harv\NavGo'
   $adb = "$root\.tools\android-sdk\platform-tools\adb.exe"
   & $adb devices
   & $adb install --user 0 -r "$root\android\app\build\outputs\apk\debug\app-debug.apk"
   ```

2. Update the watch through the Zepp development workflow:

   ```powershell
   Set-Location 'C:\Users\harv\NavGo\watch'
   npm run zeus -- preview --ip false
   ```

   If required, log in first with `npm run zeus -- login`. Scan the preview QR
   using Zepp's Developer Mode Mini Program scanner. This distribution step uses
   Zepp's development service; it is not part of runtime navigation forwarding.

3. Open Android NavGo and grant **navigation notification access** after reading
   the disclosure. Allow the sharing-status notification as well. Sideloaded
   builds may require Android's restricted-settings approval; only grant access
   to a build you trust.
4. Tap **Copy Zepp setup key**. In Zepp, open the installed NavGo Mini Program's
   **settings**. On the **NavGo setup / 0.2.2** page, tap the green
   **Tap to paste setup key** control to open its editor, paste the full key, and
   save/confirm the text input. The page should show **Key saved (hidden)**.
   Settings placement varies with Zepp version; use the installed preview's
   settings entry if NavGo is under Developer Mode. Do not send anyone the key.
5. Tap **Start sharing** in Android NavGo. Keep Zepp allowed to run in the
   background and Bluetooth on. Open watch NavGo; it connects automatically.
   There is no phone scan, discovery code, separate pairing or approval step.
6. Start real Google Maps navigation. First test while stationary. The Android
   preview should show the next instruction and the watch should show guidance.
   **Retry** starts a request when no previous request is outstanding.

Keep NavGo open on the watch. It is a foreground page, not an always-running
watch service. Starting in watch 0.2.2, NavGo requests Zepp OS's return-on-wake
behavior after screen-off instead of the default return to the watch face.
Reopening starts a new connection and waits for fresh guidance, not a saved turn.
**Screen: auto** allows normal display sleep; tapping it requests up to one hour
of bright-screen/drop-wrist hold, at a battery cost. It does not block notifications.

### Avoid notification interruptions while riding

Configure and test this while stationary:

1. In **Zepp > your T-Rex 3 > Notifications and Reminders > App Notifications**,
   disable **Google Maps** in the app alert list (menu names vary by Zepp version).
   This avoids receiving the same instruction as both NavGo guidance and a
   separate watch notification. Disable NavGo's own mirrored sharing-status
   alerts here too if selected.
2. **Keep Google Maps notifications enabled in Android**, and keep NavGo's
   notification access enabled. Those are the source of the guidance; the Zepp
   app-alert selection is a separate forwarding path.
3. For other interruptions, try **Do Not Disturb** in the watch's quick settings
   during the ride. This silences ordinary call/message alerts, not just Maps.
   Restore your preferred mode afterward. Check NavGo's turn vibration in DND
   on your firmware before relying on it. Alternatively, temporarily switch off
   Zepp's app-notification forwarding; restore it afterward.
4. Reopen NavGo and optionally enable **Screen: ON 1h**. Trigger an unrelated
   phone notification and a screen-off/wake cycle while stationary. Confirm the
   route stays visible or reopens with fresh guidance.

The documented `setWakeUpRelaunch` API covers screen-off recovery, **not automatic
return after dismissing a system notification**. That behavior remains
firmware-dependent and unverified. NavGo does not change DND, cancel phone
notifications, or force itself above calls/alerts using repeated relaunch timers.

**Stop sharing** closes the local listener and clears phone guidance. The watch
clears guidance on the next error/deadline while executing; an already delivered
snapshot cannot be recalled instantaneously. **Reset / revoke Zepp setup key**
stops sharing and invalidates the old key. Copy the replacement into Zepp before
restarting. Removing a saved key in Zepp alone does not revoke other copies.

Android 15+ applies a six-hour-per-24-hour background allowance to data-sync
foreground services. NavGo handles the timeout by stopping, not by bypassing
the limit. Reopening NavGo allows the user to start again. It does not restart
automatically at boot. Long journeys and background restrictions need testing.

## Troubleshooting

| Watch/phone symptom | Action |
|---|---|
| Still shows Scan / No phone / BLE counters | Install the new 0.2.2 watch preview; Android alone is insufficient |
| Paste key in Zepp settings | Save the full 64-character setup key in the installed NavGo settings |
| Copy new key into Zepp | Key was revoked or belongs to another NavGo install/profile |
| Start sharing on phone | Open Android NavGo and start sharing; use the main profile, not a clone |
| Phone relay timed out / Relay busy | Check Android sharing, then reopen Zepp and watch NavGo; native fetch may be stalled |
| Check Zepp link / retry | Confirm Zepp shows the watch connected; keep Zepp running and reopen NavGo |
| Local relay could not start | Another profile/process may occupy the fixed port; stop other NavGo copies, do not expose a LAN server |
| No settings entry | Reinstall the complete new preview, including its phone-side/settings modules |
| Settings shows crowded text / no obvious input | Tap the old "Paste private setup key" label, or update to watch preview 0.2.1 for separated rows and a green editor control |
| Ready but no route | Check Android preview, Maps notifications and NavGo notification access |
| Unknown arrow | Unsupported maneuver wording; read text and check the phone |
| NOT LIVE / CHECK PHONE | Source, connection, response validation or freshness failed; old guidance is not authoritative |
| Maps/other notification replaces NavGo | Disable duplicate Maps alerts in Zepp, not Android; use the riding notification setup above |
| Watch face appears after display sleep | Update to watch 0.2.2 for return-on-wake; test separately from notification dismissal |
| Stops when screen/app changes | Foreground-page and phone background limitations; validate on hardware |

Zepp must permit local HTTP to `127.0.0.1:27341`. Its network policy is independent
of Android NavGo's manifest. If your Zepp version blocks this, the bridge remains
unavailable; do not disable authentication or bind to a LAN address as a workaround.

## Build and validation

Java 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, SDK 35/build-tools 35.0.0,
Node/npm. No third-party Android runtime libraries. Watch messaging uses
`@zeppos/zml` 0.0.41 (Apache-2.0); its license and notice ship in watch assets.

```powershell
$root = 'C:\Users\harv\NavGo'
$env:JAVA_HOME = (Get-ChildItem "$root\.tools\java" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$root\.tools\android-sdk"
$env:GRADLE_USER_HOME = "$root\.tools\gradle-home"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location "$root\android"
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug --console=plain --no-daemon
Set-Location "$root\watch"
npm run check
npm test
npm run test:interop
npm run build
```

On a fresh checkout, provision Java/Android SDK and run `npm ci` in `watch`.
The ignored `.tools` folder is a workstation convenience. The interop test needs
the compiled Java test fixture; it exercises real authenticated loopback HTTP
through the JavaScript bridge and watch client, using synthetic route data.

Artifacts:

```text
android\app\build\outputs\apk\debug\app-debug.apk
watch\dist\1099901-NavGo-0.2.2-<timestamp>.zab
android\app\build\reports\lint-results-debug.html
```

Tests cover parsing, Unicode/packet bounds, HTTP authentication/rejection,
loopback binding, request deadlines, replay/late-response rejection, setup key
changes, settings storage, lifecycle and stale UI clearing. They cannot prove
Zepp network policy, real Maps notifications, watch firmware, battery or latency.
Use the hardware acceptance matrix in `DESIGN.md` before release.

CLI 1.7.1, the alias workaround in `tools\zeus.cjs`, and the ARM64 Sharp override
remain pinned. `--ip false` excludes intermediate sources. The CLI scans
Node-only tests/tools and can emit `import.meta` warnings; inspect nested ZAB/ZPK
contents rather than assuming those files ship. Device bytecode, Side Service
JavaScript, settings JavaScript, assets and manifests are intentional payloads.

The APK is debug-signed. Register a unique Zepp app ID instead of development
placeholder 1099901, configure release signing outside source, and complete
hardware, security/privacy and distribution review before publishing a release.

Official references:

```text
https://github.com/zepp-health/zml
https://docs.zepp.com/docs/guides/architecture/arc/
https://docs.zepp.com/docs/reference/side-service-api/fetch/
https://docs.zepp.com/docs/reference/app-json/
https://docs.zepp.com/docs/reference/device-app-api/newAPI/display/setWakeUpRelaunch/
https://support.amazfit.com/en/amazfit_t-rex_3/docs/PW5cdEhxfo9R1rxN5xFcYfeSnde
https://in.amazfit.com/pages/faq/how-to-enable-dnd-do-not-disturb-mode-in-amazfit-t-rex-3
https://developer.android.com/develop/background-work/services/fgs/timeout
```
