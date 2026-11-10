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

    private void setupAudio() {
        boolean wantMic = cfg.mic, wantInt = cfg.internal != 0;
        if (!wantMic && !wantInt) { hasAudio = false; return; }
        int minMono = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int minStereo = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (wantMic) {
            int[] srcs = {MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.UNPROCESSED,
                    MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.VOICE_COMMUNICATION};
            int[] tryOrder = {srcs[cfg.micSrc], MediaRecorder.AudioSource.MIC};
            for (int s : tryOrder) {
                try {
                    AudioRecord r = new AudioRecord(s, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(minMono * 4, SR / 2));
                    if (r.getState() == AudioRecord.STATE_INITIALIZED) { micRec = r; break; }
                    r.release();
                } catch (Throwable t) { Log.w(TAG, "mic src " + s, t); }
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
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        try {
            while (!stopping) {
                try { Thread.sleep(8); } catch (InterruptedException ignore) { }
                pull(micRec, micQ, rd);
                pull(gameRec, gameQ, rd);
                long now = System.nanoTime();
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

    private void pull(AudioRecord r, Fifo q, short[] buf) {
        if (r == null) return;
        for (int k = 0; k < 6; k++) {
            int n = r.read(buf, 0, buf.length, AudioRecord.READ_NON_BLOCKING);
            if (n <= 0) break;
            q.push(buf, n);
        }
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
