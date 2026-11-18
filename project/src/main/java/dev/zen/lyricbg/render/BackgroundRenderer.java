/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.graphics.Canvas
 */
package dev.zen.lyricbg.render;

import android.graphics.Canvas;

interface BackgroundRenderer {
    public void resize(int var1, int var2);

    public void setColors(float[][] var1, float var2);

    public void draw(Canvas var1, float var2);

    public void release();
}

