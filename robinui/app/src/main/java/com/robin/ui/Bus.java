package com.robin.ui;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;

/** In-process event hand-off between the notification listener and the island / control center. */
public final class Bus {
    public static final class Note {
        public String app, title, text;
        public Drawable icon;
        public PendingIntent open;
        public boolean call;
    }

    public interface Listener {
        void onNote(Note n);
        void onMedia();
    }

    public static Listener listener;
    public static volatile MediaController media;

    static void note(Note n) { if (listener != null) listener.onNote(n); }
    static void media() { if (listener != null) listener.onMedia(); }
}
