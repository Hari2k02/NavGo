package app.navgo;

import android.os.SystemClock;
import app.navgo.core.Navigation;

final class NavStore {
    static volatile boolean enabled;
    static volatile String status = "Sharing is off";
    static volatile String pendingAddress;
    static volatile String connectedAddress;
    private static Navigation navigation = Navigation.idle();
    private static long verifiedAt = -1;

    private NavStore() {}

    static synchronized void update(Navigation value) {
        navigation = value;
        verifiedAt = SystemClock.elapsedRealtime();
    }

    static synchronized Navigation snapshot() {
        if (!enabled || verifiedAt < 0 || SystemClock.elapsedRealtime() - verifiedAt > 15_000) {
            return Navigation.unavailable();
        }
        return navigation;
    }

    static synchronized void clear() {
        navigation = Navigation.idle();
        verifiedAt = -1;
    }
}
