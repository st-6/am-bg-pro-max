/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.graphics.Bitmap
 *  android.graphics.Bitmap$Config
 *  android.graphics.Canvas
 *  android.graphics.Paint
 *  android.graphics.Rect
 */
package dev.zen.lyricbg.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

public final class ArtworkProcessor {
    private static final int MAX_EDGE = 512;

    private ArtworkProcessor() {
    }

    public static Bitmap capture(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        int n = bitmap.getWidth();
        int n2 = bitmap.getHeight();
        if (n < 2 || n2 < 2) {
            return null;
        }
        float f = Math.min(1.0f, 512.0f / (float)Math.max(n, n2));
        int n3 = Math.max(2, Math.round((float)n * f));
        int n4 = Math.max(2, Math.round((float)n2 * f));
        Bitmap bitmap2 = null;
        try {
            bitmap2 = Bitmap.createBitmap((int)n3, (int)n4, (Bitmap.Config)Bitmap.Config.ARGB_8888);
            Paint paint = new Paint(6);
            new Canvas(bitmap2).drawBitmap(bitmap, null, new Rect(0, 0, n3, n4), paint);
            return bitmap2;
        }
        catch (Throwable throwable) {
            if (bitmap2 != null && !bitmap2.isRecycled()) {
                bitmap2.recycle();
            }
            return null;
        }
    }
}

