package com.robin.island;

import android.app.Notification;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotifService extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn.getPackageName().equals(getPackageName())) return;
            Notification n = sbn.getNotification();
            int skip = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_FOREGROUND_SERVICE;
            if ((n.flags & skip) != 0) return;
            Bundle e = n.extras;
            CharSequence t = e.getCharSequence(Notification.EXTRA_TITLE), b = e.getCharSequence(Notification.EXTRA_TEXT);
            if (t == null && b == null) return;
            Drawable ic = getPackageManager().getApplicationIcon(sbn.getPackageName());
            IslandService.show(ic, t == null ? "" : t.toString(), b == null ? "" : b.toString(), n.contentIntent);
        } catch (Exception ignored) { }
    }
}
