package com.robinui.launcher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotifListener extends NotificationListenerService {
    @Override public void onListenerConnected() { IslandService.mediaAttach(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { IslandService.notif(sbn); }
}
