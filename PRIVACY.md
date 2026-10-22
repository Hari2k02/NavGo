# NavGo privacy behavior

This describes the implemented prototype, not Google Maps, Android, the Zepp
phone app, watch firmware or development/distribution services.

## Access and data flow

Android notification access is a broad system capability: the OS can deliver
notifications from other apps to the listener. NavGo checks the source package
before reading notification extras and processes only Google Maps while sharing
is enabled. It uses notification title, text, expanded text and subtext to obtain
the current maneuver, distance and summary.

The current route snapshot lives in application memory. It is not intentionally
written to files, preferences, a database, analytics, route logs or an endpoint.
It is displayed on the phone/watch and forwarded only over the approved watch's
authenticated encrypted BLE connection. Memory is cleared on stop/listener loss
and the watch suppresses unavailable or expired guidance. Platform memory dumps
and behavior on a compromised device are outside this guarantee.

## Persistent data and network

Private Android preferences contain only the approved watch's Bluetooth address
and a random three-byte phone discovery code. The address is a device identifier;
it is not sent to a server. The discovery code is advertised publicly and is not
a secret. Android manages Bluetooth pairing keys separately from NavGo.

NavGo's Android manifest does not request internet or location access. The watch
app uses BLE, screen and vibration APIs, with no HTTP calls, side-app JavaScript,
analytics SDK or tracking service. The application has no NavGo cloud backend,
account, subscription or route history.

Android backup is disabled and explicit extraction exclusions are configured.
Device-vendor behavior is not under NavGo's control.

## User controls

- Stop sharing to stop the service/advertising and clear current guidance.
- Forget the watch to remove its NavGo approval. Remove the system Bluetooth bond
  separately in Android Bluetooth settings if desired.
- Revoke notification access and Nearby devices permissions in Android settings.
- Uninstall Android NavGo to remove its private preferences; system/vendor bond
  handling is separate. Close/uninstall the watch app to stop its page execution.

## Separate ecosystem behavior

Google Maps may use location, accounts and network services under its own terms.
The Zepp app and firmware may have their own communications and data collection.
NavGo does not claim to disable those independent services.

Downloading build dependencies requires network access. The pinned third-party
Zeus CLI has its own update checks and analytics calls; those are development-tool
behavior, not code shipped inside NavGo. Its optional login/QR-preview workflow
uses Zepp development/distribution services and may upload application packages,
not NavGo route data. Do not run that workflow if those terms are unacceptable.
No application source should contain real routes, credentials or signing keys.
