package com.robin.cord;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Output file: MediaStore (internal) or a SAF document in a user-picked SD-card folder. */
final class Out {
    Uri uri;
    ParcelFileDescriptor pfd;
    String name;
    String note;   // set when we had to fall back
    boolean media;
    long size;
    private final Context ctx;

    private Out(Context c) { ctx = c; }

    static Out open(Context c, Cfg cfg) throws IOException {
        String name = "Robincord_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
        Out o = new Out(c);
        o.name = name;
        if (cfg.store == 1) {
            try {
                if (cfg.sdTree == null) throw new IOException("no SD folder chosen");
                Uri tree = Uri.parse(cfg.sdTree);
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                Uri doc = DocumentsContract.createDocument(c.getContentResolver(), parent, "video/mp4", name);
                if (doc == null) throw new IOException("cannot create file on SD card");
                o.uri = doc;
                o.pfd = c.getContentResolver().openFileDescriptor(doc, "rw");
                if (o.pfd == null) throw new IOException("cannot open SD file");
                return o;
            } catch (Throwable t) {
                o.note = "SD card not available (" + t.getMessage() + ") - saved to internal storage instead";
            }
        }
        ContentResolver r = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        v.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Robincord");
        v.put(MediaStore.Video.Media.IS_PENDING, 1);
        o.uri = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
        if (o.uri == null) throw new IOException("MediaStore insert failed");
        o.media = true;
        o.pfd = r.openFileDescriptor(o.uri, "rw");
        if (o.pfd == null) throw new IOException("cannot open output");
        return o;
    }

    /** ok=false deletes the half-written file. */
    void finish(boolean ok) {
        try { size = pfd.getStatSize(); } catch (Throwable ignore) { }
        try { pfd.close(); } catch (Throwable ignore) { }
        ContentResolver r = ctx.getContentResolver();
        try {
            if (!ok) {
                if (media) r.delete(uri, null, null); else DocumentsContract.deleteDocument(r, uri);
            } else if (media) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Video.Media.IS_PENDING, 0);
                r.update(uri, v, null, null);
            }
        } catch (Throwable ignore) { }
    }
}
