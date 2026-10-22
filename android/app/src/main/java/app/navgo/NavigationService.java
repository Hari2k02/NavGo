package app.navgo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public final class NavigationService extends Service {
    static final String STOP = "app.navgo.STOP";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BleServer ble;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            MapsNotificationListener.refreshNow();
            handler.postDelayed(this, 5_000);
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            NavStore.status = "Sharing is off";
            stopSelf();
            return START_NOT_STICKY;
        }
        if (NavStore.enabled) return START_NOT_STICKY;
        if (Build.VERSION.SDK_INT >= 31
            && (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)) {
            fail("Nearby devices permission is required");
            return START_NOT_STICKY;
        }
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
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(1, notification);
        }
        NavStore.clear();
        NavStore.enabled = true;
        NavStore.status = "Starting private Bluetooth link...";
        ble = new BleServer(this, handler, this::fail);
        try {
            ble.start();
            if (NavStore.enabled) handler.post(heartbeat);
        } catch (SecurityException exception) {
            fail("Bluetooth permission is missing. Grant Nearby devices access and restart.");
        } catch (IllegalStateException exception) {
            fail("Bluetooth is unavailable. Enable Bluetooth and restart.");
        }
        return START_NOT_STICKY;
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
        try {
            if (ble != null) ble.close();
        } catch (SecurityException exception) {
            NavStore.status = "Sharing stopped; Bluetooth permission was revoked.";
        } finally {
            NavStore.clear();
            stopForeground(STOP_FOREGROUND_REMOVE);
            super.onDestroy();
        }
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
