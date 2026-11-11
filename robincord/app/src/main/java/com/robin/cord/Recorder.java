package com.robin.cord;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.WindowManager;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Screen recorder: MediaProjection -> hardware encoder (H.264/H.265), audio = mic and/or captured internal
 * audio mixed into one AAC track, muxed to MP4. Pause/resume drops video frames and stops the audio clock.
 */
final class Recorder {
    private static final String TAG = "Robincord";
    private static final int SR = 48000;
    private static final int CHUNK = 1024; // audio frames per AAC input

    private final Context ctx;
    private final Cfg cfg;
    private final MediaProjection mp;

    private MediaCodec venc, aenc;
    private Surface surf;
    private VirtualDisplay vd;
    private MediaMuxer muxer;
    private Out out;
    private AudioRecord micRec, gameRec;
    private Thread vt, at;

    private volatile boolean stopping;
    private volatile boolean paused;
    private boolean hasAudio;
    private long startUs;
    private final ArrayList<long[]> pauses = new ArrayList<>(); // {fromUs, toUs (0 = open)}

    private int vTrack = -1, aTrack = -1;
    private boolean muxStarted;
    private final Object ml = new Object();
    private final ArrayList<Pend> pend = new ArrayList<>();

    /** Live meters for the UI: peak level 0..32767 (decaying) and a status line. */
    static volatile int micLevel, gameLevel;
    static volatile String micMsg = "";
    private int micSrcNow = -1;
    private int micAttempt = 0;
    private boolean everSound;          // once the mic has produced real sound we never switch source again
    private int switches;
    private long micLoops, micActive;
    private int gamePeakMax;
    private boolean micWanted, gameWanted;
    volatile String report = "";

    String summary = "";
    final ArrayList<String> warnings = new ArrayList<>();

    private static final class Pend {
        boolean video; byte[] data; long pts; int flags;
    }

    Recorder(Context c, Cfg cfg, MediaProjection mp) { this.ctx = c; this.cfg = cfg; this.mp = mp; }

    // ------------------------------------------------------------------ start
    void start() throws Exception {
        try {
            doStart();
        } catch (Throwable t) {
            cleanup(false);
            if (t instanceof Exception) throw (Exception) t;
            throw new Exception(t);
        }
    }

    private void doStart() throws Exception {
        // --- screen size
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        int pw, ph;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Rect b = wm.getMaximumWindowMetrics().getBounds();
            pw = b.width(); ph = b.height();
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            pw = dm.widthPixels; ph = dm.heightPixels;
        }
        int dpi = ctx.getResources().getDisplayMetrics().densityDpi;
        int sMin = Math.min(pw, ph), sMax = Math.max(pw, ph);
        boolean land;
        if (cfg.orient == 0) land = true;
        else if (cfg.orient == 1) land = false;
        else land = pw > ph;
        int tShort = Math.min(Cfg.RES[cfg.res], sMin);
        int tLong = Math.round(tShort * (sMax / (float) sMin));
        int w = land ? tLong : tShort, h = land ? tShort : tLong;

        // --- encoder choice
        String mime = cfg.codec == 1 ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
        MediaCodecInfo info = pickEncoder(mime);
        if (info == null && cfg.codec == 1) {
            mime = MediaFormat.MIMETYPE_VIDEO_AVC;
            info = pickEncoder(mime);
            warnings.add("H.265 encoder not found, using H.264");
        }
        if (info == null) throw new Exception("No video encoder found");
        MediaCodecInfo.VideoCapabilities vc = info.getCapabilitiesForType(mime).getVideoCapabilities();
        int wa = Math.max(2, vc.getWidthAlignment()), ha = Math.max(2, vc.getHeightAlignment());
        w = Math.max(wa, (w / wa) * wa);
        h = Math.max(ha, (h / ha) * ha);
        int guard = 0;
        while (!vc.isSizeSupported(w, h) && guard++ < 20) {
            w = Math.max(wa, ((int) (w * 0.9f) / wa) * wa);
            h = Math.max(ha, ((int) (h * 0.9f) / ha) * ha);
        }
        int fps = Cfg.FPS[cfg.fps];
        try {
            Range<Double> fr = vc.getSupportedFrameRatesFor(w, h);
            if (fps > fr.getUpper()) { fps = (int) Math.floor(fr.getUpper()); warnings.add("Encoder max at this size: " + fps + " fps"); }
        } catch (Throwable ignore) { }
        int br = Cfg.BR[cfg.br] * 1000000;
        try { br = vc.getBitrateRange().clamp(br); } catch (Throwable ignore) { }

        MediaFormat vf = MediaFormat.createVideoFormat(mime, w, h);
        vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        vf.setInteger(MediaFormat.KEY_BIT_RATE, br);
        vf.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        vf.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1500000L / fps);
        try { vf.setInteger("max-fps-to-encoder", fps); } catch (Throwable ignore) { }
        if (mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            vf.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        }
        venc = MediaCodec.createByCodecName(info.getName());
        try {
            MediaFormat cbr = new MediaFormat(vf);
            cbr.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            venc.configure(cbr, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Throwable t) {
            venc.release();
            venc = MediaCodec.createByCodecName(info.getName());
            MediaFormat plain = new MediaFormat(vf);
            plain.removeKey(MediaFormat.KEY_PROFILE);
            venc.configure(plain, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }
        surf = venc.createInputSurface();
        venc.start();
        summary = w + "x" + h + " " + fps + "fps " + (br / 1000000) + "Mbps " + (mime.contains("hevc") ? "H.265" : "H.264");

        // --- audio sources
        setupAudio();

        // --- output
        out = Out.open(ctx, cfg);
        if (out.note != null) warnings.add(out.note);
        muxer = new MediaMuxer(out.pfd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // --- go
        startUs = System.nanoTime() / 1000L;
        if (hasAudio) {
            if (micRec != null) micRec.startRecording();
            if (gameRec != null) gameRec.startRecording();
            at = new Thread(this::audioLoop, "rc-audio");
            at.start();
        }
        vt = new Thread(this::videoLoop, "rc-video");
        vt.start();
        vd = mp.createVirtualDisplay("Robincord", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surf, null, null);
        if (vd == null) throw new Exception("Could not create virtual display");
    }

    private MediaCodecInfo pickEncoder(String mime) {
        MediaCodecInfo soft = null;
        for (MediaCodecInfo i : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!i.isEncoder()) continue;
            boolean ok = false;
            for (String t : i.getSupportedTypes()) if (t.equalsIgnoreCase(mime)) ok = true;
            if (!ok) continue;
            if (i.isHardwareAccelerated() && !i.isSoftwareOnly()) return i;
            if (soft == null) soft = i;
        }
        return soft;
    }

    private static final int[] SRC = {MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION};

    /** Selected source first, then the ones that usually survive a call. */
    private int[] micOrder() {
        int[] pref = {SRC[Math.min(cfg.micSrc, SRC.length - 1)], MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.UNPROCESSED};
        java.util.LinkedHashSet<Integer> set = new java.util.LinkedHashSet<>();
        for (int p : pref) set.add(p);
        int[] o = new int[set.size()];
        int i = 0;
        for (int v : set) o[i++] = v;
        return o;
    }

    private AudioRecord openMic(int src) {
        try {
            int minMono = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord r = new AudioRecord(src, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minMono * 4, SR / 2));
            if (r.getState() == AudioRecord.STATE_INITIALIZED) return r;
            r.release();
        } catch (Throwable t) { Log.w(TAG, "mic src " + src, t); }
        return null;
    }

    private static String srcName(int s) {
        switch (s) {
            case MediaRecorder.AudioSource.MIC: return "Mic";
            case MediaRecorder.AudioSource.UNPROCESSED: return "Unprocessed";
            case MediaRecorder.AudioSource.CAMCORDER: return "Camcorder";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "Voice call";
            case MediaRecorder.AudioSource.VOICE_RECOGNITION: return "Voice recognition";
        }
        return "src" + s;
    }

    private void setupAudio() {
        boolean wantMic = cfg.mic, wantInt = cfg.internal != 0;
        if (!wantMic && !wantInt) { hasAudio = false; return; }
        int minStereo = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        micLevel = 0; gameLevel = 0; micMsg = "";
        micWanted = wantMic; gameWanted = wantInt;
        if (wantMic) {
            int[] order = micOrder();
            for (int k = 0; k < order.length && micRec == null; k++) {
                micRec = openMic(order[k]);
                if (micRec != null) { micSrcNow = order[k]; micAttempt = k; }
            }
            if (micRec == null) warnings.add("Microphone could not be opened");
        }
        if (wantInt) {
            for (int attempt = 0; attempt < 2 && gameRec == null; attempt++) {
                try {
                    AudioPlaybackCaptureConfiguration.Builder cb = new AudioPlaybackCaptureConfiguration.Builder(mp);
                    cb.addMatchingUsage(AudioAttributes.USAGE_GAME);
                    if (cfg.internal == 1) {
                        cb.addMatchingUsage(AudioAttributes.USAGE_MEDIA);
                        cb.addMatchingUsage(AudioAttributes.USAGE_UNKNOWN);
                    }
                    if (cfg.expVoice && attempt == 0) cb.addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION);
                    AudioRecord r = new AudioRecord.Builder()
                            .setAudioPlaybackCaptureConfig(cb.build())
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR)
                                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build())
                            .setBufferSizeInBytes(Math.max(minStereo * 4, SR))
                            .build();
                    if (r.getState() == AudioRecord.STATE_INITIALIZED) gameRec = r; else r.release();
                } catch (Throwable t) { Log.w(TAG, "playback capture attempt " + attempt, t); }
                if (!cfg.expVoice) break;
            }
            if (gameRec == null) warnings.add("Internal sound capture could not start");
        }
        hasAudio = micRec != null || gameRec != null;
        if (!hasAudio) return;
        MediaFormat af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SR, 2);
        af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        af.setInteger(MediaFormat.KEY_BIT_RATE, 192000);
        af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        try {
            aenc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            aenc.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            aenc.start();
        } catch (Throwable t) {
            warnings.add("Audio encoder failed: " + t.getMessage());
            hasAudio = false;
            if (aenc != null) { try { aenc.release(); } catch (Throwable ignore) { } aenc = null; }
            if (micRec != null) { micRec.release(); micRec = null; }
            if (gameRec != null) { gameRec.release(); gameRec = null; }
        }
    }

    // ------------------------------------------------------------------ muxer helpers
    private void addTrack(boolean video, MediaFormat f) {
        synchronized (ml) {
            if (video) vTrack = muxer.addTrack(f); else aTrack = muxer.addTrack(f);
            if (vTrack >= 0 && (!hasAudio || aTrack >= 0)) {
                muxer.start();
                muxStarted = true;
                for (Pend p : pend) {
                    MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                    bi.set(0, p.data.length, p.pts, p.flags);
                    muxer.writeSampleData(p.video ? vTrack : aTrack, ByteBuffer.wrap(p.data), bi);
                }
                pend.clear();
            }
        }
    }

    private void write(boolean video, ByteBuffer buf, MediaCodec.BufferInfo bi, long pts) {
        synchronized (ml) {
            if (muxStarted) {
                MediaCodec.BufferInfo o = new MediaCodec.BufferInfo();
                o.set(bi.offset, bi.size, pts, bi.flags);
                try { muxer.writeSampleData(video ? vTrack : aTrack, buf, o); } catch (Throwable t) { Log.w(TAG, "write", t); }
            } else {
                Pend p = new Pend();
                p.video = video; p.pts = pts; p.flags = bi.flags;
                p.data = new byte[bi.size];
                buf.position(bi.offset);
                buf.get(p.data, 0, bi.size);
                pend.add(p);
            }
        }
    }

    // ------------------------------------------------------------------ video thread
    /** Returns the output timestamp, or -1 if the frame lies in a pause. */
    private long mapVideoTime(long p) {
        long sub = 0;
        synchronized (pauses) {
            for (long[] iv : pauses) {
                if (p >= iv[0] && (iv[1] == 0 || p < iv[1])) return -1;
                if (iv[1] != 0 && iv[1] <= p) sub += iv[1] - iv[0];
            }
        }
        long t = p - startUs - sub;
        return Math.max(0, t);
    }

    private void videoLoop() {
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        long lastPts = -1;
        try {
            while (true) {
                int i = venc.dequeueOutputBuffer(bi, 10000);
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    addTrack(true, venc.getOutputFormat());
                } else if (i >= 0) {
                    ByteBuffer b = venc.getOutputBuffer(i);
                    boolean eos = (bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
                    if (bi.size > 0 && b != null) {
                        long t = mapVideoTime(bi.presentationTimeUs);
                        if (t >= 0) {
                            if (t <= lastPts) t = lastPts + 1;
                            lastPts = t;
                            b.position(bi.offset);
                            b.limit(bi.offset + bi.size);
                            write(true, b, bi, t);
                        }
                    }
                    venc.releaseOutputBuffer(i, false);
                    if (eos) break;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "video loop", t);
        }
    }

    // ------------------------------------------------------------------ audio thread
    private static final class Fifo {
        final short[] a; int r, n;
        Fifo(int cap) { a = new short[cap]; }
        void push(short[] s, int len) {
            if (len >= a.length) { System.arraycopy(s, len - a.length, a, 0, a.length); r = 0; n = a.length; return; }
            if (n + len > a.length) { int drop = n + len - a.length; r = (r + drop) % a.length; n -= drop; }
            int w = (r + n) % a.length;
            for (int i = 0; i < len; i++) a[(w + i) % a.length] = s[i];
            n += len;
        }
        int pop(short[] d, int len) {
            int g = Math.min(len, n);
            for (int i = 0; i < g; i++) d[i] = a[(r + i) % a.length];
            r = (r + g) % a.length; n -= g;
            for (int i = g; i < len; i++) d[i] = 0;
            return g;
        }
        void clear() { r = 0; n = 0; }
    }

    private void audioLoop() {
        Fifo micQ = new Fifo(SR);          // mono, 1 s
        Fifo gameQ = new Fifo(SR * 2);     // stereo, 1 s
        short[] rd = new short[4096];
        short[] mic = new short[CHUNK];
        short[] game = new short[CHUNK * 2];
        byte[] pcm = new byte[CHUNK * 4];
        float gm = cfg.gainMic / 100f, gg = cfg.gainInt / 100f;
        long t0 = startUs * 1000L, pausedNs = 0, pauseFrom = 0;
        long frames = 0;
        boolean wasPaused = false;
        long lastMicSound = System.nanoTime();
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        try {
            while (!stopping) {
                try { Thread.sleep(8); } catch (InterruptedException ignore) { }
                int mp = pull(micRec, micQ, rd);
                int gp = pull(gameRec, gameQ, rd);
                micLevel = Math.max((int) (micLevel * 0.9f), mp);
                gameLevel = Math.max((int) (gameLevel * 0.9f), gp);
                long now = System.nanoTime();
                if (gp > gamePeakMax) gamePeakMax = gp;
                if (micRec != null && !paused) {
                    micLoops++;
                    if (mp > 0) {
                        micActive++;
                        lastMicSound = now;
                        if (!everSound) { everSound = true; micMsg = ""; }
                    } else if (!everSound && now - lastMicSound > 3_000_000_000L) {
                        switchMic();
                        lastMicSound = now;
                    }
                }
                if (paused) {
                    if (!wasPaused) { wasPaused = true; pauseFrom = now; }
                    micQ.clear(); gameQ.clear();
                    drainAudio(bi, false);
                    continue;
                }
                if (wasPaused) { wasPaused = false; pausedNs += now - pauseFrom; }
                long target = (long) ((now - t0 - pausedNs) / 1e9 * SR);
                while (target - frames >= CHUNK && !stopping) {
                    micQ.pop(mic, CHUNK);
                    gameQ.pop(game, CHUNK * 2);
                    for (int i = 0; i < CHUNK; i++) {
                        float m = mic[i] * gm;
                        float l = m + game[2 * i] * gg, r = m + game[2 * i + 1] * gg;
                        int li = clip(l), ri = clip(r);
                        pcm[4 * i] = (byte) li; pcm[4 * i + 1] = (byte) (li >> 8);
                        pcm[4 * i + 2] = (byte) ri; pcm[4 * i + 3] = (byte) (ri >> 8);
                    }
                    queueAudio(pcm, CHUNK * 4, frames * 1000000L / SR, bi);
                    frames += CHUNK;
                }
                drainAudio(bi, false);
            }
            // EOS
            long pts = frames * 1000000L / SR;
            for (int k = 0; k < 50; k++) {
                int ix = aenc.dequeueInputBuffer(10000);
                if (ix >= 0) { aenc.queueInputBuffer(ix, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); break; }
                drainAudio(bi, false);
            }
            for (int k = 0; k < 300; k++) { if (drainAudio(bi, true)) break; }
        } catch (Throwable t) {
            Log.e(TAG, "audio loop", t);
        }
    }

    private static int clip(float v) { return v > 32767 ? 32767 : (v < -32768 ? -32768 : (int) v); }

    /** @return the peak absolute sample read this call (0 if none / digital silence). */
    private int pull(AudioRecord r, Fifo q, short[] buf) {
        if (r == null) return 0;
        int peak = 0;
        for (int k = 0; k < 6; k++) {
            int n = r.read(buf, 0, buf.length, AudioRecord.READ_NON_BLOCKING);
            if (n <= 0) break;
            for (int i = 0; i < n; i++) { int v = buf[i] < 0 ? -buf[i] : buf[i]; if (v > peak) peak = v; }
            q.push(buf, n);
        }
        return peak;
    }

    /** Called when the mic only delivers digital zeros (Android silences it during calls): try the next source. */
    private void switchMic() {
        int[] order = micOrder();
        if (micAttempt + 1 >= order.length) {
            micMsg = "Mic is silent (Android blocks it). Enable Robincord in Accessibility.";
            micAttempt = -1; // try the whole list again in 3 s (the call may connect later)
            return;
        }
        switches++;
        try { micRec.stop(); micRec.release(); } catch (Throwable ignore) { }
        micRec = null;
        for (int k = micAttempt + 1; k < order.length && micRec == null; k++) {
            micAttempt = k;
            AudioRecord r = openMic(order[k]);
            if (r != null) {
                try { r.startRecording(); micRec = r; micSrcNow = order[k]; } catch (Throwable t) { r.release(); }
            }
        }
        micMsg = micRec != null ? "Mic silent - trying source: " + srcName(micSrcNow)
                : "Mic is silent (Android blocks it). Enable Robincord in Accessibility.";
    }

    private void queueAudio(byte[] pcm, int len, long pts, MediaCodec.BufferInfo bi) {
        for (int k = 0; k < 20; k++) {
            int ix = aenc.dequeueInputBuffer(5000);
            if (ix >= 0) {
                ByteBuffer ib = aenc.getInputBuffer(ix);
                ib.clear();
                ib.put(pcm, 0, len);
                aenc.queueInputBuffer(ix, 0, len, pts, 0);
                return;
            }
            drainAudio(bi, false);
        }
    }

    /** @return true when EOS was seen */
    private boolean drainAudio(MediaCodec.BufferInfo bi, boolean wait) {
        while (true) {
            int i = aenc.dequeueOutputBuffer(bi, wait ? 10000 : 0);
            if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                addTrack(false, aenc.getOutputFormat());
            } else if (i >= 0) {
                ByteBuffer b = aenc.getOutputBuffer(i);
                boolean eos = (bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
                if (bi.size > 0 && b != null) {
                    b.position(bi.offset);
                    b.limit(bi.offset + bi.size);
                    write(false, b, bi, bi.presentationTimeUs);
                }
                aenc.releaseOutputBuffer(i, false);
                if (eos) return true;
            } else {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ control
    boolean isPaused() { return paused; }

    void pause() {
        if (paused) return;
        synchronized (pauses) { pauses.add(new long[]{System.nanoTime() / 1000L, 0}); }
        paused = true;
    }

    void resume() {
        if (!paused) return;
        synchronized (pauses) {
            if (!pauses.isEmpty()) pauses.get(pauses.size() - 1)[1] = System.nanoTime() / 1000L;
        }
        paused = false;
        try {
            Bundle b = new Bundle();
            b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            venc.setParameters(b);
        } catch (Throwable ignore) { }
    }

    /** Stops everything and returns the finished output (or null if nothing usable was recorded). */
    Out stop() {
        report = "Mic " + (!micWanted ? "off" : micSrcNow < 0 ? "could not open"
                : srcName(micSrcNow) + ", " + (micLoops > 0 ? (100 * micActive / micLoops) : 0) + "% active"
                + (everSound ? "" : " (SILENT)")) + "  |  Internal "
                + (!gameWanted ? "off" : (gamePeakMax > 0 ? "ok" : "no sound")) + "  |  source switches: " + switches;
        if (paused) resume();
        stopping = true;
        try { if (vd != null) vd.release(); } catch (Throwable ignore) { }
        vd = null;
        try { if (venc != null) venc.signalEndOfInputStream(); } catch (Throwable ignore) { }
        try { if (at != null) at.join(8000); } catch (InterruptedException ignore) { }
        try { if (vt != null) vt.join(8000); } catch (InterruptedException ignore) { }
        boolean ok = false;
        synchronized (ml) {
            if (muxStarted) {
                try { muxer.stop(); ok = true; } catch (Throwable t) { Log.e(TAG, "muxer stop", t); }
            }
        }
        cleanup(ok);
        return ok ? out : null;
    }

    private void cleanup(boolean ok) {
        stopping = true;
        try { if (micRec != null) { micRec.stop(); micRec.release(); } } catch (Throwable ignore) { }
        try { if (gameRec != null) { gameRec.stop(); gameRec.release(); } } catch (Throwable ignore) { }
        micRec = null; gameRec = null;
        try { if (venc != null) { venc.stop(); venc.release(); } } catch (Throwable ignore) { }
        try { if (aenc != null) { aenc.stop(); aenc.release(); } } catch (Throwable ignore) { }
        venc = null; aenc = null;
        try { if (surf != null) surf.release(); } catch (Throwable ignore) { }
        try { if (muxer != null) muxer.release(); } catch (Throwable ignore) { }
        muxer = null;
        if (out != null) out.finish(ok);
    }
}
