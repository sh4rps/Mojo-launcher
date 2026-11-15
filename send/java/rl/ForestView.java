package rl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Static pixel-art forest backdrop (bundled picture, centre-cropped, crisp pixels). Draws once, never animates. */
public class ForestView extends View {
    private Bitmap bmp;
    private final Paint paint = new Paint();
    private final Matrix mx = new Matrix();
    private boolean tried;

    public ForestView(Context c) { super(c); paint.setFilterBitmap(false); }
    public ForestView(Context c, AttributeSet a) { super(c, a); paint.setFilterBitmap(false); }

    private void load() {
        if (tried) return;
        tried = true;
        try {
            Context c = getContext();
            int id = c.getResources().getIdentifier("send_forest", "drawable", c.getPackageName());
            if (id != 0) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inScaled = false; o.inPreferredConfig = Bitmap.Config.RGB_565;
                bmp = BitmapFactory.decodeResource(c.getResources(), id, o);
            }
        } catch (Throwable t) { bmp = null; }
    }

    @Override protected void onDraw(Canvas c) {
        load();
        if (bmp == null) { c.drawColor(0xff150d33); return; }
        float w = getWidth(), h = getHeight();
        float s = Math.max(w / bmp.getWidth(), h / bmp.getHeight());
        mx.reset(); mx.postScale(s, s); mx.postTranslate((w - bmp.getWidth() * s) / 2f, (h - bmp.getHeight() * s) / 2f);
        c.drawBitmap(bmp, mx, paint);
    }
}
