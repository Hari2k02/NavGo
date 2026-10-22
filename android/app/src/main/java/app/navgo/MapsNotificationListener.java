package app.navgo;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import app.navgo.core.MapsParser;
import app.navgo.core.Navigation;

public final class MapsNotificationListener extends NotificationListenerService {
    private static MapsNotificationListener instance;

    @Override public void onListenerConnected() {
        instance = this;
        refreshNow();
    }

    @Override public void onListenerDisconnected() {
        instance = null;
        NavStore.clear();
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        NavStore.clear();
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (MapsParser.MAPS_PACKAGE.equals(notification.getPackageName())) refreshNow();
    }

    @Override public void onNotificationRemoved(StatusBarNotification notification) {
        if (MapsParser.MAPS_PACKAGE.equals(notification.getPackageName())) refreshNow();
    }

    static void refreshNow() {
        if (!NavStore.enabled || instance == null) return;
        try {
            StatusBarNotification[] notifications = instance.getActiveNotifications();
            if (notifications == null) {
                NavStore.clear();
                return;
            }
            Navigation latest = null;
            long latestTime = Long.MIN_VALUE;
            for (StatusBarNotification sbn : notifications) {
                if (!MapsParser.MAPS_PACKAGE.equals(sbn.getPackageName())) continue;
                Notification notification = sbn.getNotification();
                Bundle extras = notification.extras;
                Navigation parsed = MapsParser.parse(sbn.getPackageName(), sbn.isOngoing(),
                    (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0,
                    value(extras, Notification.EXTRA_TITLE),
                    value(extras, Notification.EXTRA_TEXT),
                    value(extras, Notification.EXTRA_BIG_TEXT),
                    value(extras, Notification.EXTRA_SUB_TEXT));
                if (parsed != null && sbn.getPostTime() > latestTime) {
                    latest = parsed;
                    latestTime = sbn.getPostTime();
                }
            }
            NavStore.update(latest == null ? Navigation.idle() : latest);
        } catch (SecurityException exception) {
            NavStore.clear();
            NavStore.status = "Notification access was revoked. Stop and grant access again.";
        }
    }

    private static String value(Bundle extras, String key) {
        CharSequence value = extras == null ? null : extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }
}
