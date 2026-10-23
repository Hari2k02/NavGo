package app.navgo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import app.navgo.core.LocalNavServer;
import java.io.IOException;

public final class NavigationService extends Service {
    static final String STOP = "app.navgo.STOP";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocalNavServer bridge;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            MapsNotificationListener.refreshNow();
            handler.postDelayed(this, 5_000);
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            NavStore.status = "Sharing is off";
            NavStore.enabled = false;
            NavStore.clear();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (NavStore.enabled) return START_NOT_STICKY;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("connection",
            getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
            new Intent(this, NavigationService.class).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "connection")
            .setSmallIcon(R.drawable.ic_navgo)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_message))
            .setContentIntent(open).setOngoing(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .addAction(new Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1, notification);
            }
        } catch (IllegalStateException | SecurityException exception) {
            fail("Android blocked background sharing. Reopen NavGo and retry.");
            return START_NOT_STICKY;
        }
        NavStore.clear();
        NavStore.enabled = true;
        NavStore.status = "Starting local Zepp relay...";
        try {
            bridge = new LocalNavServer(new BridgeKey(this).get(), NavStore::snapshot,
                () -> NavStore.bridgeReadAt = SystemClock.elapsedRealtime(),
                error -> handler.post(() -> fail(error)),
                warning -> Log.w("NavGo", warning));
            bridge.start(LocalNavServer.PORT);
            NavStore.status = "Ready. Open NavGo on your Zepp-connected watch.";
            handler.post(heartbeat);
        } catch (IOException | SecurityException exception) {
            fail("Local relay could not start. Stop other NavGo profiles, then retry.");
        }
        return START_NOT_STICKY;
    }

    @Override public void onTimeout(int startId, int foregroundServiceType) {
        fail("Android ended the background sharing time allowance. Reopen NavGo to restart.");
    }

    private void fail(String message) {
        NavStore.status = message;
        NavStore.enabled = false;
        NavStore.clear();
        handler.removeCallbacks(heartbeat);
        stopSelf();
    }

    @Override public void onDestroy() {
        NavStore.enabled = false;
        handler.removeCallbacksAndMessages(null);
        if (bridge != null) bridge.close();
        NavStore.clear();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
