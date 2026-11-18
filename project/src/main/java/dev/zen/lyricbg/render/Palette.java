/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.graphics.Bitmap
 *  android.graphics.Color
 */
package dev.zen.lyricbg.render;

import android.graphics.Bitmap;
import android.graphics.Color;

public final class Palette {
    public static final int SIZE = 5;
    private static final float[][] FALLBACK = new float[][]{{0.09f, 0.1f, 0.21f}, {0.22f, 0.15f, 0.4f}, {0.4f, 0.17f, 0.33f}, {0.11f, 0.21f, 0.35f}, {0.15f, 0.15f, 0.25f}};
    private static final int SAMPLE = 24;
    private static final int BINS = 6;

    private Palette() {
    }

    public static float[][] fallback() {
        return Palette.copy(FALLBACK);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static float[][] fromBitmap(Bitmap bitmap, float f) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() < 2 || bitmap.getHeight() < 2) {
            return Palette.shaded(FALLBACK, f);
        }
        Bitmap bitmap2 = null;
        try {
            bitmap2 = Bitmap.createScaledBitmap((Bitmap)bitmap, (int)24, (int)24, (boolean)false);
            int[] nArray = new int[576];
            bitmap2.getPixels(nArray, 0, 24, 0, 0, 24, 24);
            float[][] fArray = Palette.cluster(nArray);
            float[][] fArray2 = fArray == null ? Palette.shaded(FALLBACK, f) : Palette.shaded(fArray, f);
            return fArray2;
        }
        catch (Throwable throwable) {
            float[][] fArray = Palette.shaded(FALLBACK, f);
            return fArray;
        }
        finally {
            if (bitmap2 != null && bitmap2 != bitmap) {
                bitmap2.recycle();
            }
        }
    }

    private static float[][] cluster(int[] nArray) {
        int n;
        int n22;
        float[][] fArray = new float[6][3];
        float[] fArray2 = new float[6];
        float[] fArray3 = new float[3];
        for (int pixel : nArray) {
            if ((pixel >>> 24 & 0xFF) < 200) continue;
            Color.colorToHSV(pixel, (float[])fArray3);
            if (fArray3[2] < 0.05f) continue;
            n = (int)(fArray3[0] / 60.0f);
            if (n >= 6) {
                n = 5;
            }
            float f = 0.25f + fArray3[1] * fArray3[2];
            float[] fArray4 = fArray[n];
            fArray4[0] = fArray4[0] + (float)(pixel >> 16 & 0xFF) / 255.0f * f;
            float[] fArray5 = fArray[n];
            fArray5[1] = fArray5[1] + (float)(pixel >> 8 & 0xFF) / 255.0f * f;
            float[] fArray6 = fArray[n];
            fArray6[2] = fArray6[2] + (float)(pixel & 0xFF) / 255.0f * f;
            int n3 = n;
            fArray2[n3] = fArray2[n3] + f;
        }
        float[][] fArray7 = new float[6][3];
        int n4 = 0;
        for (int fArray72 = 0; fArray72 < 6; ++fArray72) {
            if (fArray2[fArray72] <= 0.0f) continue;
            fArray7[fArray72][0] = fArray[fArray72][0] / fArray2[fArray72];
            fArray7[fArray72][1] = fArray[fArray72][1] / fArray2[fArray72];
            fArray7[fArray72][2] = fArray[fArray72][2] / fArray2[fArray72];
            ++n4;
        }
        if (n4 < 2) {
            return null;
        }
        float[][] fArray8 = new float[5][3];
        n22 = 0;
        for (n = 0; n < 6 && n22 < 5; ++n) {
            if (fArray2[n] <= 0.0f) continue;
            fArray8[n22++] = fArray7[n];
        }
        for (n = n22; n < 5; ++n) {
            fArray8[n] = (float[])FALLBACK[n].clone();
        }
        return fArray8;
    }

    private static float[][] shaded(float[][] fArray, float f) {
        float f2 = 0.55f + 0.45f * Palette.clamp(f);
        float[][] fArray2 = new float[5][3];
        float[] fArray3 = new float[3];
        for (int i = 0; i < 5; ++i) {
            float[] fArray4 = i < fArray.length ? fArray[i] : FALLBACK[i];
            int n = Color.rgb((int)Palette.to255(fArray4[0]), (int)Palette.to255(fArray4[1]), (int)Palette.to255(fArray4[2]));
            Color.colorToHSV((int)n, (float[])fArray3);
            float f3 = Math.max(0.3f, Math.min(0.85f, fArray3[1] * f2 + 0.1f));
            float f4 = Math.max(0.13f, Math.min(0.42f, fArray3[2] * 0.55f));
            int n2 = Color.HSVToColor((float[])new float[]{fArray3[0], f3, f4});
            fArray2[i][0] = (float)(n2 >> 16 & 0xFF) / 255.0f;
            fArray2[i][1] = (float)(n2 >> 8 & 0xFF) / 255.0f;
            fArray2[i][2] = (float)(n2 & 0xFF) / 255.0f;
        }
        return fArray2;
    }

    private static float[][] copy(float[][] fArray) {
        float[][] fArray2 = new float[fArray.length][3];
        for (int i = 0; i < fArray.length; ++i) {
            fArray2[i] = (float[])fArray[i].clone();
        }
        return fArray2;
    }

    private static int to255(float f) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, f)) * 255.0f);
    }

    private static float clamp(float f) {
        if (Float.isNaN(f)) {
            return 0.5f;
        }
        return Math.max(0.0f, Math.min(1.0f, f));
    }
}

