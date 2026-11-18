package com.robin.pro;

import android.graphics.Bitmap;
import android.net.Uri;

public class Clip {
    public Uri uri;
    public String name;
    public long dur, start, end;   // ms
    public Bitmap thumb;

    public Clip copy() {
        Clip c = new Clip();
        c.uri = uri; c.name = name; c.dur = dur; c.start = start; c.end = end; c.thumb = thumb;
        return c;
    }
}
