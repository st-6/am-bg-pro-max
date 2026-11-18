/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.graphics.Canvas
 *  android.graphics.Paint
 *  android.graphics.RuntimeShader
 *  android.graphics.Shader
 *  android.os.Build$VERSION
 */
package dev.zen.lyricbg.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import dev.zen.lyricbg.render.BackgroundRenderer;
import dev.zen.lyricbg.render.Palette;

final class AgsRenderer
implements BackgroundRenderer {
    private static final String[] UNIFORMS = new String[]{"uColor0", "uColor1", "uColor2", "uColor3", "uColor4"};
    private final Paint paint = new Paint();
    private final RuntimeShader shader;
    private int width = 1;
    private int height = 1;
    private boolean colorsDirty = true;
    private float[][] colors = Palette.fallback();

    static BackgroundRenderer create() {
        if (Build.VERSION.SDK_INT < 33) {
            return null;
        }
        try {
            return new AgsRenderer();
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private AgsRenderer() {
        this.shader = new RuntimeShader("uniform float2 uResolution;\nuniform float uTime;\nuniform float3 uColor0;\nuniform float3 uColor1;\nuniform float3 uColor2;\nuniform float3 uColor3;\nuniform float3 uColor4;\n\nfloat hash21(float2 p) {\n    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);\n}\n\nhalf4 main(float2 fragCoord) {\n    float2 res = max(uResolution, float2(1.0, 1.0));\n    float2 uv = fragCoord / res;\n    float aspect = res.x / res.y;\n    float t = uTime;\n\n    float2 c0 = float2(0.24 + 0.13 * sin(t * 0.23),       0.26 + 0.11 * cos(t * 0.19));\n    float2 c1 = float2(0.76 + 0.11 * cos(t * 0.17 + 1.7), 0.28 + 0.12 * sin(t * 0.21 + 0.6));\n    float2 c2 = float2(0.30 + 0.12 * cos(t * 0.15 + 3.1), 0.78 + 0.10 * sin(t * 0.18 + 2.2));\n    float2 c3 = float2(0.78 + 0.10 * sin(t * 0.19 + 4.4), 0.74 + 0.11 * cos(t * 0.22 + 1.1));\n    float2 c4 = float2(0.50 + 0.16 * sin(t * 0.13 + 2.0), 0.50 + 0.15 * cos(t * 0.16 + 3.7));\n\n    float2 s = float2(aspect, 1.0);\n    float2 d0 = (uv - c0) * s;\n    float2 d1 = (uv - c1) * s;\n    float2 d2 = (uv - c2) * s;\n    float2 d3 = (uv - c3) * s;\n    float2 d4 = (uv - c4) * s;\n\n    float w0 = 1.0 / (0.055 + dot(d0, d0) * 2.6);\n    float w1 = 1.0 / (0.055 + dot(d1, d1) * 2.6);\n    float w2 = 1.0 / (0.055 + dot(d2, d2) * 2.6);\n    float w3 = 1.0 / (0.055 + dot(d3, d3) * 2.6);\n    float w4 = 1.0 / (0.055 + dot(d4, d4) * 2.6);\n\n    float3 col = (uColor0 * w0 + uColor1 * w1 + uColor2 * w2 + uColor3 * w3 + uColor4 * w4)\n               / (w0 + w1 + w2 + w3 + w4);\n\n    col *= 1.0 - 0.30 * length(uv - 0.5);\n    col += (hash21(fragCoord) - 0.5) * (1.0 / 255.0);\n\n    return half4(col, 1.0);\n}\n");
        this.paint.setShader((Shader)this.shader);
        this.paint.setFilterBitmap(false);
    }

    @Override
    public void resize(int n, int n2) {
        this.width = Math.max(1, n);
        this.height = Math.max(1, n2);
    }

    @Override
    public void setColors(float[][] fArray, float f) {
        this.colors = fArray == null ? Palette.fallback() : fArray;
        this.colorsDirty = true;
    }

    @Override
    public void draw(Canvas canvas, float f) {
        this.shader.setFloatUniform("uResolution", (float)this.width, (float)this.height);
        this.shader.setFloatUniform("uTime", f);
        if (this.colorsDirty) {
            for (int i = 0; i < UNIFORMS.length; ++i) {
                float[] fArray = this.colors[i < this.colors.length ? i : this.colors.length - 1];
                this.shader.setFloatUniform(UNIFORMS[i], fArray[0], fArray[1], fArray[2]);
            }
            this.colorsDirty = false;
        }
        canvas.drawRect(0.0f, 0.0f, (float)this.width, (float)this.height, this.paint);
    }

    @Override
    public void release() {
    }
}

