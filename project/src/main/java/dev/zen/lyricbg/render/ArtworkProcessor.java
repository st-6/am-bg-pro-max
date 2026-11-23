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

    /*
     * Neutral whites above the limit and near-black pixels are erased and
     * refilled from the nearest surviving colours. Filling spreads inward from
     * the mask border so the result keeps the cover's own hue and gradients.
     */
    public static Bitmap cleanExtremes(Bitmap source, float strength) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        if (width < 2 || height < 2) {
            return null;
        }
        int total = width * height;
        int[] pixels = new int[total];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        float amount = Math.max(0.25f, Math.min(0.75f, strength));
        float whiteLimit = 0.86f;
        float blackLimit = 0.12f;
        // type: 0 keep, 1 neutral white, 2 near black.
        byte[] type = new byte[total];
        // state: 0 valid, 1/2 pending, 3/4 queued (type + 2).
        byte[] state = new byte[total];
        int valid = 0;
        int white = 0;
        for (int i = 0; i < total; i++) {
            int color = pixels[i];
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            float max = Math.max(r, Math.max(g, b));
            float min = Math.min(r, Math.min(g, b));
            if (luma >= whiteLimit && max - min <= 0.10f) {
                type[i] = 1;
                state[i] = 1;
                white++;
            } else if (luma <= blackLimit) {
                type[i] = 2;
                state[i] = 2;
            } else {
                valid++;
            }
        }
        if (valid == total) {
            return null;
        }
        if (valid == 0) {
            int fill = white * 2 >= total ? 0xFFB2ACA6 : 0xFF2A2624;
            for (int i = 0; i < total; i++) {
                pixels[i] = blend(pixels[i], fill, amount);
            }
            return toBitmap(pixels, width, height);
        }

        int[] fill = new int[total];
        int[] queue = new int[total];
        int head = 0;
        int tail = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                byte st = state[i];
                if ((st == 1 || st == 2) && hasValidNeighbor(state, width, height, x, y)) {
                    state[i] = (byte)(st + 2);
                    queue[tail++] = i;
                }
            }
        }
        while (head < tail) {
            int i = queue[head++];
            byte st = state[i];
            int kind = st == 3 ? 1 : 2;
            int x = i % width;
            int y = i / width;
            int rSum = 0;
            int gSum = 0;
            int bSum = 0;
            int count = 0;
            for (int dy = -1; dy <= 1; dy++) {
                int yy = y + dy;
                if (yy < 0 || yy >= height) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int xx = x + dx;
                    if (xx < 0 || xx >= width) continue;
                    int j = yy * width + xx;
                    int c;
                    if (state[j] == 0) {
                        c = fill[j] != 0 ? fill[j] : pixels[j];
                    } else {
                        continue;
                    }
                    rSum += (c >> 16) & 0xFF;
                    gSum += (c >> 8) & 0xFF;
                    bSum += c & 0xFF;
                    count++;
                }
            }
            int rgb;
            if (count == 0) {
                rgb = kind == 1 ? 0xFFB2ACA6 : 0xFF2A2624;
            } else {
                float r = rSum / (float)count / 255f;
                float g = gSum / (float)count / 255f;
                float b = bSum / (float)count / 255f;
                float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                if (kind == 1 && luma > 0.78f) {
                    float k = 0.78f / luma;
                    r *= k;
                    g *= k;
                    b *= k;
                } else if (kind == 2 && luma < 0.14f) {
                    float k = 0.14f / Math.max(luma, 1.0E-4f);
                    r *= k;
                    g *= k;
                    b *= k;
                }
                rgb = 0xFF000000
                        | (Math.round(clamp01(r) * 255.0f) << 16)
                        | (Math.round(clamp01(g) * 255.0f) << 8)
                        | Math.round(clamp01(b) * 255.0f);
            }
            fill[i] = rgb;
            state[i] = 0;
            for (int dy = -1; dy <= 1; dy++) {
                int yy = y + dy;
                if (yy < 0 || yy >= height) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx;
                    if (xx < 0 || xx >= width) continue;
                    int j = yy * width + xx;
                    byte next = state[j];
                    if (next == 1 || next == 2) {
                        state[j] = (byte)(next + 2);
                        queue[tail++] = j;
                    }
                }
            }
        }
        for (int i = 0; i < total; i++) {
            if (type[i] != 0 && fill[i] != 0) {
                pixels[i] = blend(pixels[i], fill[i], amount);
            }
        }
        return toBitmap(pixels, width, height);
    }

    private static int blend(int original, int replacement, float amount) {
        int r = Math.round(((original >> 16) & 0xFF) * (1.0f - amount)
                + ((replacement >> 16) & 0xFF) * amount);
        int g = Math.round(((original >> 8) & 0xFF) * (1.0f - amount)
                + ((replacement >> 8) & 0xFF) * amount);
        int b = Math.round((original & 0xFF) * (1.0f - amount)
                + (replacement & 0xFF) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static boolean hasValidNeighbor(byte[] state, int width, int height, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            int yy = y + dy;
            if (yy < 0 || yy >= height) continue;
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int xx = x + dx;
                if (xx < 0 || xx >= width) continue;
                if (state[yy * width + xx] == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static Bitmap toBitmap(int[] pixels, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}

