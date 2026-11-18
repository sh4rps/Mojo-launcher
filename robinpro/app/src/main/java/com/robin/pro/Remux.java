package com.robin.pro;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;

import java.nio.ByteBuffer;
import java.util.List;

/** Lossless cut + join: copies the compressed streams, no re-encoding (fast, no quality loss). */
public class Remux {
    public interface Progress { void on(int pct); }

    public static int rotation(Context c, Clip clip) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, clip.uri);
            String s = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            return s == null ? 0 : Integer.parseInt(s);
        } catch (Exception e) { return 0; }
        finally { try { r.release(); } catch (Exception ignored) { } }
    }

    private static int track(MediaExtractor ex, String prefix) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            String m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith(prefix)) return i;
        }
        return -1;
    }

    /** returns number of clips skipped because their video format differs from the first clip */
    public static int run(Context ctx, List<Clip> clips, String outPath, boolean mute, Progress prog) throws Exception {
        MediaMuxer mux = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        boolean started = false;
        int skipped = 0;
        MediaFormat vf0 = null, af0 = null;
        int vOut = -1, aOut = -1;
        long offsetUs = 0;
        long total = 0;
        for (Clip c : clips) total += c.end - c.start;
        long done = 0;
        ByteBuffer buf = ByteBuffer.allocate(4 * 1024 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            for (int ci = 0; ci < clips.size(); ci++) {
                Clip c = clips.get(ci);
                MediaExtractor ex = new MediaExtractor();
                try {
                    ex.setDataSource(ctx, c.uri, null);
                    int vt = track(ex, "video/"), at = mute ? -1 : track(ex, "audio/");
                    if (vt < 0) { skipped++; continue; }
                    MediaFormat vf = ex.getTrackFormat(vt);
                    if (vf0 == null) {
                        vf0 = vf;
                        mux.setOrientationHint(rotation(ctx, c));
                        vOut = mux.addTrack(vf);
                        if (at >= 0) { af0 = ex.getTrackFormat(at); aOut = mux.addTrack(af0); }
                        mux.start(); started = true;
                    } else {
                        boolean same = vf.getString(MediaFormat.KEY_MIME).equals(vf0.getString(MediaFormat.KEY_MIME))
                                && vf.getInteger(MediaFormat.KEY_WIDTH) == vf0.getInteger(MediaFormat.KEY_WIDTH)
                                && vf.getInteger(MediaFormat.KEY_HEIGHT) == vf0.getInteger(MediaFormat.KEY_HEIGHT);
                        if (!same) { skipped++; continue; }
                    }
                    boolean useAudio = at >= 0 && aOut >= 0;
                    if (!useAudio) at = -1;
                    ex.selectTrack(vt);
                    if (useAudio) ex.selectTrack(at);
                    long startUs = c.start * 1000L, endUs = c.end * 1000L;
                    ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    long base = -1, maxPts = 0;
                    boolean vDone = false, aDone = !useAudio;
                    while (!(vDone && aDone)) {
                        int idx = ex.getSampleTrackIndex();
                        if (idx < 0) break;
                        long t = ex.getSampleTime();
                        if (idx == vt) {
                            if (t > endUs) { vDone = true; ex.unselectTrack(vt); continue; }
                            if (base < 0) base = t;
                        } else {
                            if (t > endUs) { aDone = true; ex.unselectTrack(at); continue; }
                            if (base < 0 || t < base) { ex.advance(); continue; }
                        }
                        buf.clear();
                        int n = ex.readSampleData(buf, 0);
                        if (n < 0) break;
                        info.set(0, n, offsetUs + t - base,
                                (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                        mux.writeSampleData(idx == vt ? vOut : aOut, buf, info);
                        if (info.presentationTimeUs - offsetUs > maxPts) maxPts = info.presentationTimeUs - offsetUs;
                        ex.advance();
                    }
                    offsetUs += maxPts + 33000;
                    done += c.end - c.start;
                    if (prog != null) prog.on((int) (done * 100 / Math.max(1, total)));
                } finally { ex.release(); }
            }
        } finally {
            try { if (started) mux.stop(); } catch (Exception ignored) { }
            mux.release();
        }
        if (!started) throw new Exception("No usable video in the timeline");
        return skipped;
    }
}
