package app.navgo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
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
    private BridgeKey bridgeKey;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            status.setText((NavStore.enabled ? "SHARING ON\n" : "SHARING OFF\n") + NavStore.status
                + "\nNotification access: " + (listenerGranted() ? "granted" : "not granted"));
            Navigation nav = NavStore.snapshot();
            preview.setText(NavStore.enabled ? nav.instruction + "\n" + nav.distance + "  " + nav.summary
                : "Your next turn will appear here.\nNo route history is saved.");
            candidate.setText(NavStore.enabled && NavStore.bridgeReadAt >= 0
                && SystemClock.elapsedRealtime() - NavStore.bridgeReadAt < 15_000
                ? "Zepp relay: receiving requests" : "Zepp relay: waiting for watch");
            handler.postDelayed(this, 1_000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        bridgeKey = new BridgeKey(this);
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
        label(content, "ZEPP LOCAL BRIDGE  /  0.2.0", 16, 0xffc9fa73);
        status = label(content, "", 15, 0xffb8c7bb);
        preview = label(content, "", 23, Color.WHITE);
        button(content, "1  Allow navigation notification access", this::notificationConsent);
        button(content, "2  Copy Zepp setup key", this::copyKey);
        label(content, "Paste this private key into NavGo's settings inside the Zepp app. "
            + "Pair your watch in Zepp as usual; no separate NavGo Bluetooth pairing is needed.",
            14, 0xffb8c7bb);
        button(content, "3  Start sharing", this::startSharing);
        candidate = label(content, "", 13, 0xffb8c7bb);
        button(content, "Allow sharing status notification", this::permissions);
        button(content, "Stop sharing", () -> {
            stopSharing();
            NavStore.status = "Sharing is off";
        });
        button(content, "Reset / revoke Zepp setup key", () ->
            new AlertDialog.Builder(this).setTitle("Revoke the current setup key?")
                .setMessage("Sharing will stop. Copy the new key into Zepp before starting again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset key", (dialog, which) -> {
                    stopSharing();
                    bridgeKey.reset();
                    NavStore.status = "Key revoked. Copy the new setup key into Zepp.";
                }).show());
        label(content, "LOCAL ONLY\nNo account. No cloud. No analytics. No location permission.\n\n"
            + "Android notification access is broad. NavGo filters for Google Maps before reading text, "
            + "and only processes it while sharing is on. Disable access in Android settings at any time.\n\n"
            + "The loopback-only endpoint requires your setup key. Zepp handles the watch link and "
            + "can access forwarded navigation. Anyone with the key and access to this phone can "
            + "read navigation while sharing is on. Never post the key or share screenshots of it.\n\n"
            + "Android 15+ limits background data-sync sharing to six hours per 24 hours. "
            + "Reopen NavGo if Android stops sharing. No automatic restart after reboot.\n\n"
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
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS);
        needed.removeIf(permission -> checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
        if (needed.isEmpty()) message("Permissions already granted");
        else requestPermissions(needed.toArray(new String[0]), 1);
    }

    private void notificationConsent() {
        new AlertDialog.Builder(this).setTitle("Allow access to navigation notifications?")
            .setMessage("Android grants access to all notifications. NavGo reads only Google Maps while "
                + "sharing is on, keeps route data in memory, and makes it available to the Zepp "
                + "companion using a secret-key-protected loopback endpoint. Zepp forwards it over "
                + "its existing watch connection. NavGo has no cloud endpoint.")
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
        NotificationListenerService.requestRebind(new ComponentName(this, MapsNotificationListener.class));
        try {
            startForegroundService(new Intent(this, NavigationService.class));
        } catch (IllegalStateException | SecurityException exception) {
            NavStore.status = "Android blocked sharing. Keep NavGo visible and retry.";
            message(NavStore.status);
        }
    }

    private void stopSharing() {
        NavStore.enabled = false;
        NavStore.clear();
        stopService(new Intent(this, NavigationService.class));
    }

    private void copyKey() {
        ClipData clip = ClipData.newPlainText("NavGo private setup key", bridgeKey.get());
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        clip.getDescription().setExtras(extras);
        getSystemService(ClipboardManager.class).setPrimaryClip(clip);
        message("Private key copied. Paste only into NavGo settings in Zepp.");
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
