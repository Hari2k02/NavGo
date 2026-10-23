# NavGo privacy behavior

This describes NavGo 0.2.0, not Google Maps, Android, Zepp, watch firmware or
development/distribution services.

## Data access and flow

Android notification access is broad: the OS can deliver other apps'
notifications to the listener. NavGo checks the source package before reading
extras and processes only Google Maps while sharing is enabled. Title, text,
expanded text and subtext provide the current maneuver, distance and summary.

Current navigation lives in RAM, is displayed on phone/watch, and is served
through an authenticated loopback endpoint to NavGo's Side Service inside Zepp.
The Side Service forwards it through Zepp's existing watch connection. Zepp is
therefore inside the navigation trust boundary, not just an installer.

NavGo does not intentionally persist route history, write routes/keys to logs,
or send navigation to any NavGo cloud/server outside the phone. Stop/listener
loss clears phone data; watch errors and freshness deadlines suppress guidance
while its page executes. Previously delivered data cannot be recalled
instantaneously. Platform caches, memory dumps and vendor behavior are outside
this guarantee.

## Stored settings and permissions

Android private preferences contain a 32-byte random setup key, encoded as
64 hex characters. The user copies it into NavGo's Zepp settings storage.
The key is not sent to the watch. Reset replaces it and stops sharing.
Obsolete BLE address/discovery-code preferences are cleared on migration.
Android backup and extraction are disabled for NavGo; Zepp manages its own
settings storage, backups and deletion behavior.

Copying the key intentionally places it on the Android clipboard, marked
sensitive. Android, the input method and authorized clipboard consumers may
access it. Keep it private and use Reset if exposed. Saved keys are not
redisplayed in the settings page; the editor may show text while pasting.

Android INTERNET permission is required for the local listening socket.
NavGo binds only 127.0.0.1:27341; it makes no outbound Android network calls.
The Side Service fetches only that fixed loopback endpoint and has no generic
HTTP proxy. No analytics/tracking SDK is added. Android requests no location,
contacts, microphone or direct-Bluetooth permissions.

The watch reads screen dimensions under data:os.device.info, uses screen and
vibration APIs, and communicates through official ZML/Zepp messaging. It does
not retain/transmit device information or BLE scan diagnostics.

## Limits and controls

Loopback HTTP is plaintext inside the phone, not TLS. The bearer key restricts
clients of the real NavGo server, but does not authenticate the server process
to Zepp. Malicious local apps can interfere or impersonate a stopped endpoint.
The OS, Zepp, phone/watch and clipboard are trusted; compromised/rooted devices
or malicious installed applications are not protected against by this design.

- **Stop sharing** closes the endpoint and clears the route.
- **Reset / revoke Zepp setup key** stops sharing and invalidates the old key.
  Copy the new key into Zepp to reconnect.
- **Remove saved key** in Zepp removes that copy only; reset on Android to revoke.
- Revoke notification access in Android settings when no longer wanted.
- Uninstall Android NavGo to remove its private preferences; separately remove
  the Zepp-side key/watch app. No system Bluetooth bond needs removal for NavGo.

## Ecosystem and build tooling

Google Maps may use location/accounts/network services under its own terms.
Zepp and firmware may have independent communications, diagnostics or collection.
NavGo does not claim to prevent these or to control Zepp's networking policy.

Downloading dependencies requires network access. The pinned Zeus CLI has its
own update checks/analytics as development-tool behavior, not NavGo runtime
analytics. Login/QR preview uses Zepp distribution services and can upload
application packages. It must not contain real routes, setup keys or signing
credentials. The official Apache-2.0 ZML runtime is bundled with its license and
notice; no implementation was copied from the unlicensed Nark repository.
