package com.rusic.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlayerService extends Service {
    public static PlayerService inst;
    static List<Track> pendingList;
    static int pendingIdx;
    public interface Listener { void onChanged(); }
    public static Listener listener;

    public List<Track> queue = new ArrayList<>();
    public int idx = -1;
    public MediaPlayer mp;
    public boolean shuffle;
    public int repeat; // 0 off, 1 all, 2 one
    boolean prepared, hadFocus, resumeOnFocus;
    MediaSession session;
    AudioManager am;
    AudioFocusRequest focusReq;
    final Random rnd = new Random();
    static final String CH = "rusic_play";

    public static void start(Context c, List<Track> list, int i) {
        pendingList = new ArrayList<>(list);
        pendingIdx = i;
        if (inst != null) { inst.setQueue(pendingList, pendingIdx); pendingList = null; return; }
        c.startForegroundService(new Intent(c, PlayerService.class));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        session = new MediaSession(this, "Rusic");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { toggle(true); }
            @Override public void onPause() { toggle(false); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { prev(); }
            @Override public void onSeekTo(long p) { seek((int) p); }
        });
        session.setActive(true);
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        startForeground(1, buildNotif());
    }

    final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { toggle(false); }
    };

    @Override public int onStartCommand(Intent i, int f, int id) {
        startForeground(1, buildNotif());
        String a = i == null ? null : i.getAction();
        if (a != null) {
            switch (a) {
                case "rusic.toggle": toggle(!isPlaying()); break;
                case "rusic.next": next(); break;
                case "rusic.prev": prev(); break;
                case "rusic.stop": stopSelf(); break;
            }
        } else if (pendingList != null) {
            setQueue(pendingList, pendingIdx);
            pendingList = null;
        }
        return START_NOT_STICKY;
    }

    public boolean isPlaying() { try { return mp != null && prepared && mp.isPlaying(); } catch (Exception e) { return false; } }
    public Track current() { return idx >= 0 && idx < queue.size() ? queue.get(idx) : null; }

    public void setQueue(List<Track> l, int start) {
        queue = new ArrayList<>(l);
        playIndex(start);
    }

    boolean focus() {
        if (hadFocus) return true;
        if (focusReq == null) {
            focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                        @Override public void onAudioFocusChange(int c) {
                            if (c == AudioManager.AUDIOFOCUS_LOSS) { resumeOnFocus = false; hadFocus = false; toggle(false); }
                            else if (c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { resumeOnFocus = isPlaying(); toggle(false); }
                            else if (c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { if (mp != null) mp.setVolume(0.25f, 0.25f); }
                            else if (c == AudioManager.AUDIOFOCUS_GAIN) { if (mp != null) mp.setVolume(1f, 1f); if (resumeOnFocus) { resumeOnFocus = false; toggle(true); } }
                        }
                    }).build();
        }
        hadFocus = am.requestAudioFocus(focusReq) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hadFocus;
    }

    public void playIndex(int i) {
        if (queue.isEmpty()) return;
        if (i < 0) i = queue.size() - 1;
        if (i >= queue.size()) i = 0;
        idx = i;
        Track t = queue.get(i);
        try {
            if (mp != null) { mp.reset(); } else {
                mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override public void onPrepared(MediaPlayer m) { prepared = true; if (focus()) m.start(); update(); }
                });
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override public void onCompletion(MediaPlayer m) { onEnd(); }
                });
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override public boolean onError(MediaPlayer m, int w, int e) {
                        prepared = false;
                        Toast.makeText(PlayerService.this, "Can't play this song, skipping", Toast.LENGTH_SHORT).show();
                        if (queue.size() > 1) next(); else update();
                        return true;
                    }
                });
            }
            prepared = false;
            mp.setDataSource(this, Uri.parse(t.uri));
            mp.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "Can't open: " + t.title, Toast.LENGTH_SHORT).show();
        }
        update();
    }

    void onEnd() {
        if (repeat == 2) { playIndex(idx); return; }
        if (shuffle && queue.size() > 1) { playIndex(randOther()); return; }
        if (idx + 1 >= queue.size() && repeat == 0) { update(); return; }
        playIndex(idx + 1);
    }

    int randOther() { int n; do { n = rnd.nextInt(queue.size()); } while (n == idx); return n; }

    public void next() { if (shuffle && queue.size() > 1) playIndex(randOther()); else playIndex(idx + 1); }

    public void prev() {
        if (mp != null && prepared && mp.getCurrentPosition() > 4000) { mp.seekTo(0); update(); return; }
        playIndex(idx - 1);
    }

    public void toggle(boolean play) {
        if (mp == null || !prepared) return;
        try {
            if (play) { if (focus()) mp.start(); } else mp.pause();
        } catch (Exception e) { }
        update();
    }

    public void seek(int ms) { if (mp != null && prepared) { mp.seekTo(ms); update(); } }

    void update() {
        Track t = current();
        if (t != null) {
            session.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, prepared && mp != null ? mp.getDuration() : t.dur).build());
        }
        boolean pl = isPlaying();
        long pos = prepared && mp != null ? mp.getCurrentPosition() : 0;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO)
                .setState(pl ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, 1f).build());
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, buildNotif());
        if (listener != null) listener.onChanged();
    }

    PendingIntent act(String a) {
        return PendingIntent.getService(this, a.hashCode(), new Intent(this, PlayerService.class).setAction(a), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    Notification buildNotif() {
        Track t = current();
        boolean pl = isPlaying();
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(t == null ? "Rusic" : t.title)
                .setContentText(t == null ? "Ready" : t.artist)
                .setOnlyAlertOnce(true)
                .setOngoing(pl)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", act("rusic.prev")).build())
                .addAction(new Notification.Action.Builder(pl ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, pl ? "Pause" : "Play", act("rusic.toggle")).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", act("rusic.next")).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    @Override public void onTaskRemoved(Intent i) { if (!isPlaying()) stopSelf(); }

    @Override public void onDestroy() {
        inst = null;
        try { unregisterReceiver(noisy); } catch (Exception e) { }
        if (mp != null) { try { mp.release(); } catch (Exception e) { } mp = null; }
        if (hadFocus && focusReq != null) am.abandonAudioFocusRequest(focusReq);
        session.release();
        super.onDestroy();
    }
}
