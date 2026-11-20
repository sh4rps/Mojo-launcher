package com.robin.mods;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.OutputStream;

/** Minimal Storage Access Framework file helper (no AndroidX). */
public class Saf {
    private static String find(ContentResolver cr, Uri tree, String parentId, String name) {
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        try (Cursor c = cr.query(kids, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (c != null && c.moveToNext()) if (name.equals(c.getString(1))) return c.getString(0);
        } catch (Exception ignored) { }
        return null;
    }

    private static String mkdir(ContentResolver cr, Uri tree, String parentId, String name) throws Exception {
        String ex = find(cr, tree, parentId, name);
        if (ex != null) return ex;
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId);
        Uri d = DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, name);
        if (d == null) throw new Exception("Cannot create folder " + name);
        return DocumentsContract.getDocumentId(d);
    }

    static String safeName(String rel) throws Exception {
        for (String p : rel.split("/")) if (p.equals("..")) throw new Exception("Unsafe path " + rel);
        return rel;
    }

    /** Opens (overwriting) a file at relative path under the picked tree, creating folders. */
    public static OutputStream open(ContentResolver cr, Uri tree, String rel) throws Exception {
        safeName(rel);
        String[] parts = rel.split("/");
        String id = DocumentsContract.getTreeDocumentId(tree);
        for (int i = 0; i < parts.length - 1; i++) if (!parts[i].isEmpty()) id = mkdir(cr, tree, id, parts[i]);
        String name = parts[parts.length - 1];
        String ex = find(cr, tree, id, name);
        if (ex != null) {
            try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, ex)); } catch (Exception ignored) { }
        }
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, id);
        Uri f = DocumentsContract.createDocument(cr, parent, "application/octet-stream", name);
        if (f == null) throw new Exception("Cannot create " + name);
        return cr.openOutputStream(f, "wt");
    }
}
