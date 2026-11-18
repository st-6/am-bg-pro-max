/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.graphics.Canvas
 *  android.graphics.Color
 *  android.graphics.Paint
 *  android.graphics.Paint$Style
 *  android.graphics.RadialGradient
 *  android.graphics.Shader
 *  android.graphics.Shader$TileMode
 */
package dev.zen.lyricbg.render;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import dev.zen.lyricbg.render.BackgroundRenderer;
import dev.zen.lyricbg.render.Palette;

final class CanvasRenderer
implements BackgroundRenderer {
    private static final int BLOBS = 5;
    private final Paint[] blobPaints = new Paint[5];
    private final RadialGradient[] blobGradients = new RadialGradient[5];
    private final Paint basePaint = new Paint();
    private final Paint vignettePaint = new Paint();
    private RadialGradient vignette;
    private float[][] colors = Palette.fallback();
    private boolean colorsDirty = true;
    private int width = 1;
    private int height = 1;

    CanvasRenderer() {
        for (int i = 0; i < 5; ++i) {
            Paint paint = new Paint(5);
            paint.setStyle(Paint.Style.FILL);
            this.blobPaints[i] = paint;
        }
        this.basePaint.setStyle(Paint.Style.FILL);
        this.basePaint.setColor(-16777216);
        this.vignettePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void resize(int n, int n2) {
        this.width = Math.max(1, n);
        this.height = Math.max(1, n2);
        this.vignette = null;
    }

    @Override
    public void setColors(float[][] fArray, float f) {
        this.colors = fArray == null ? Palette.fallback() : fArray;
        this.colorsDirty = true;
    }

    @Override
    public void draw(Canvas canvas, float f) {
        if (this.colorsDirty) {
            this.rebuildGradients();
            this.colorsDirty = false;
        }
        canvas.drawRect(0.0f, 0.0f, (float)this.width, (float)this.height, this.basePaint);
        int n = Math.max(this.width, this.height);
        for (int i = 0; i < 5; ++i) {
            float f2 = 0.17f + 0.043f * (float)i;
            float f3 = (0.5f + 0.34f * (float)Math.sin(f * f2 + (float)i * 1.7f)) * (float)this.width;
            float f4 = (0.5f + 0.31f * (float)Math.cos(f * f2 * 0.87f + (float)i * 2.3f)) * (float)this.height;
            float f5 = (0.52f + 0.07f * (float)Math.sin(f * 0.29f + (float)i * 1.1f)) * (float)n;
            if (f5 <= 0.0f) continue;
            canvas.save();
            canvas.translate(f3, f4);
            canvas.scale(f5, f5);
            canvas.drawCircle(0.0f, 0.0f, 1.0f, this.blobPaints[i]);
            canvas.restore();
        }
        if (this.vignette == null) {
            this.vignette = new RadialGradient((float)this.width * 0.5f, (float)this.height * 0.5f, (float)n * 0.78f, new int[]{0, 0x59000000}, new float[]{0.35f, 1.0f}, Shader.TileMode.CLAMP);
            this.vignettePaint.setShader((Shader)this.vignette);
        }
        canvas.drawRect(0.0f, 0.0f, (float)this.width, (float)this.height, this.vignettePaint);
    }

    private void rebuildGradients() {
        int n = 0;
        for (int i = 0; i < 5; ++i) {
            float[] fArray = this.colors[i < this.colors.length ? i : this.colors.length - 1];
            int n2 = Color.rgb((int)CanvasRenderer.channel(fArray[0]), (int)CanvasRenderer.channel(fArray[1]), (int)CanvasRenderer.channel(fArray[2]));
            int n3 = n2 & 0xFFFFFF;
            this.blobGradients[i] = new RadialGradient(0.0f, 0.0f, 1.0f, new int[]{n2, n2, n3}, new float[]{0.0f, 0.45f, 1.0f}, Shader.TileMode.CLAMP);
            this.blobPaints[i].setShader((Shader)this.blobGradients[i]);
            n += (Color.red((int)n2) + Color.green((int)n2) + Color.blue((int)n2)) / 3;
        }
        this.basePaint.setColor(Color.rgb((int)(n / 5 / 3), (int)(n / 5 / 3), (int)(n / 5 / 2)));
    }

    private static int channel(float f) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, f)) * 255.0f);
    }

    @Override
    public void release() {
        for (int i = 0; i < 5; ++i) {
            this.blobGradients[i] = null;
            this.blobPaints[i].setShader(null);
        }
        this.vignette = null;
        this.vignettePaint.setShader(null);
    }
}

