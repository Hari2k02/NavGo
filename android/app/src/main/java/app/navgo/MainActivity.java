package app.navgo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import app.navgo.core.Navigation;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView preview;
    private TextView candidate;
    private TrustStore trust;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            status.setText((NavStore.enabled ? "SHARING ON\n" : "SHARING OFF\n") + NavStore.status
                + "\nNotification access: " + (listenerGranted() ? "granted" : "not granted"));
            Navigation nav = NavStore.snapshot();
            preview.setText(NavStore.enabled ? nav.instruction + "\n" + nav.distance + "  " + nav.summary
                : "Your next turn will appear here.\nNo route history is saved.");
            candidate.setText("Approved watch: " + (trust.approved().isEmpty() ? "none" : trust.approved())
                + "\nPending watch: " + (NavStore.pendingAddress == null ? "none" : NavStore.pendingAddress));
            handler.postDelayed(this, 1_000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        trust = new TrustStore(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(18), dp(24), dp(24));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        label(content, "NAVGO  /  WRIST NAVIGATION", 13, 0xffc9fa73);
        label(content, "Eyes ahead.\nNext turn, on wrist.", 34, Color.WHITE);
        label(content, "PHONE CODE  " + trust.phoneLabel(), 16, 0xffc9fa73);
        status = label(content, "", 15, 0xffb8c7bb);
        preview = label(content, "", 23, Color.WHITE);
        button(content, "1  Grant Nearby devices access", this::permissions);
        button(content, "2  Allow notification access", this::notificationConsent);
        button(content, "3  Start sharing", this::startSharing);
        candidate = label(content, "", 13, 0xffb8c7bb);
        button(content, "Pair pending watch", this::pair);
        button(content, "Approve paired watch", this::approve);
        button(content, "Stop sharing", () -> {
            stopService(new Intent(this, NavigationService.class));
            NavStore.status = "Sharing is off";
        });
        button(content, "Forget approved watch", () -> {
            stopService(new Intent(this, NavigationService.class));
            trust.forget();
            NavStore.status = "Watch approval removed. System Bluetooth bond is unchanged.";
        });
        label(content, "LOCAL ONLY\nNo account. No cloud. No analytics. No location permission.\n\n"
            + "Android notification access is broad. NavGo filters for Google Maps before reading text, "
            + "and only processes it while sharing is on. Disable access in Android settings at any time.\n\n"
            + "Keep NavGo open on the watch. English maneuver recognition is best-effort; "
            + "unrecognized directions retain their text, never a guessed arrow. "
            + "Always follow road signs and check the phone when guidance is unavailable.", 14, 0xffb8c7bb);
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    @Override public void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override public void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private TextView label(LinearLayout parent, String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(10), 0, dp(14));
        if (size >= 23) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(view);
        return view;
    }

    private void button(LinearLayout parent, String title, Runnable action) {
        Button view = new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setOnClickListener(ignored -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56));
        params.bottomMargin = dp(8);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void permissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            needed.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS);
        needed.removeIf(permission -> checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
        if (needed.isEmpty()) message("Permissions already granted");
        else requestPermissions(needed.toArray(new String[0]), 1);
    }

    private void notificationConsent() {
        new AlertDialog.Builder(this).setTitle("Allow access to navigation notifications?")
            .setMessage("Android grants access to all notifications. NavGo reads only Google Maps while "
                + "sharing is on, keeps route data in memory, and sends it only to your approved watch "
                + "over an authenticated encrypted Bluetooth link. Nothing is uploaded.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open Android settings", (dialog, which) ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).show();
    }

    private boolean listenerGranted() {
        return getSystemService(android.app.NotificationManager.class)
            .isNotificationListenerAccessGranted(new ComponentName(this, MapsNotificationListener.class));
    }

    private void startSharing() {
        if (!listenerGranted()) {
            message("Grant notification access first");
            return;
        }
        if (Build.VERSION.SDK_INT >= 31
            && (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)) {
            permissions();
            return;
        }
        NotificationListenerService.requestRebind(new ComponentName(this, MapsNotificationListener.class));
        startForegroundService(new Intent(this, NavigationService.class));
    }

    private BluetoothDevice pending() {
        if (NavStore.pendingAddress == null) {
            message("Connect from NavGo on your watch first");
            return null;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            message("Bluetooth is unavailable");
            return null;
        }
        return adapter.getRemoteDevice(NavStore.pendingAddress);
    }

    private void pair() {
        try {
            BluetoothDevice device = pending();
            if (device == null) return;
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                message("Already paired. Verify it is your watch, then approve.");
            } else if (!device.createBond()) {
                message("Pairing could not start. Reconnect your watch.");
            } else {
                message("Confirm the matching system passkey on both devices.");
            }
        } catch (SecurityException exception) {
            message("Grant Nearby devices access first");
        }
    }

    private void approve() {
        try {
            BluetoothDevice device = pending();
            if (device == null) return;
            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                message("Finish system Bluetooth pairing first");
                return;
            }
            final String address = device.getAddress();
            new AlertDialog.Builder(this).setTitle("Trust this watch?")
                .setMessage("Approve " + address + " only if you just verified its system pairing passkey. "
                    + "This allows it to read your live navigation. Do not approve an unfamiliar device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Approve watch", (dialog, which) -> {
                    if (!address.equals(NavStore.pendingAddress) || !NavStore.enabled) {
                        message("Watch disconnected. Reconnect before approving.");
                        return;
                    }
                    trust.approve(address);
                    message("Watch approved. Guidance will appear after encrypted reads succeed.");
                }).show();
        } catch (SecurityException exception) {
            message("Grant Nearby devices access first");
        }
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
