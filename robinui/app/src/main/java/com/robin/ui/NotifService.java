package com.robin.ui;

import android.app.Notification;
import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;

public class NotifService extends NotificationListenerService {
    MediaSessionManager msm;
    MediaController cur;
    final Handler h = new Handler(Looper.getMainLooper());

    final MediaController.Callback cb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { Bus.media(); }
        @Override public void onMetadataChanged(MediaMetadata m) { Bus.media(); }
        @Override public void onSessionDestroyed() { pick(); }
    };

    final MediaSessionManager.OnActiveSessionsChangedListener sl = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override public void onActiveSessionsChanged(List<MediaController> l) { pick(l); }
    };

    @Override public void onListenerConnected() {
        try {
            msm = getSystemService(MediaSessionManager.class);
            ComponentName me = new ComponentName(this, NotifService.class);
            msm.addOnActiveSessionsChangedListener(sl, me, h);
            pick();
        } catch (Throwable t) { }
    }

    @Override public void onListenerDisconnected() {
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sl); } catch (Throwable t) { }
        setCur(null);
    }

    void pick() {
        try { pick(msm.getActiveSessions(new ComponentName(this, NotifService.class))); } catch (Throwable t) { }
    }

    void pick(List<MediaController> l) {
        MediaController best = null;
        if (l != null) {
            for (MediaController c : l) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
                if (best == null) best = c;
            }
        }
        setCur(best);
    }

    void setCur(MediaController c) {
        if (cur != null) { try { cur.unregisterCallback(cb); } catch (Throwable t) { } }
        cur = c;
        if (c != null) { try { c.registerCallback(cb, h); } catch (Throwable t) { } }
        Bus.media = c;
        Bus.media();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Notification n = sbn.getNotification();
            if (sbn.getPackageName().equals(getPackageName())) return;
            boolean call = Notification.CATEGORY_CALL.equals(n.category);
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0 && !call) return;
            if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0 && !call) return;
            if (n.extras.getString(Notification.EXTRA_TEMPLATE, "").contains("MediaStyle")) return;
            CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence x = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (t == null && x == null) return;
            Bus.Note b = new Bus.Note();
            b.title = t == null ? "" : t.toString();
            b.text = x == null ? "" : x.toString();
            b.call = call;
            b.open = n.contentIntent;
            try { b.app = String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(sbn.getPackageName(), 0))); } catch (Throwable e) { b.app = ""; }
            try { b.icon = getPackageManager().getApplicationIcon(sbn.getPackageName()); } catch (Throwable e) { }
            Bus.note(b);
        } catch (Throwable e) { }
    }
}
