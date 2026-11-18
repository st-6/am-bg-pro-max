/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.Bitmap
 *  android.opengl.GLSurfaceView
 *  android.view.Choreographer
 *  android.view.Choreographer$FrameCallback
 *  android.view.View
 */
package dev.zen.lyricbg.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.view.Choreographer;
import android.view.View;
import dev.zen.lyricbg.render.FluidGlRenderer;
import dev.zen.lyricbg.render.PulseSource;

public final class MeshBackgroundView
extends GLSurfaceView {
    private static final float MAX_FPS = 120.0f;
    private final FluidGlRenderer glRenderer;
    private final Choreographer choreographer;
    private boolean continuousRendering;
    private long lastVsyncNs;
    private float frameBudget;
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback(){

        public void doFrame(long l) {
            if (!MeshBackgroundView.this.continuousRendering) {
                return;
            }
            if (MeshBackgroundView.this.lastVsyncNs == 0L) {
                MeshBackgroundView.this.requestRender();
                MeshBackgroundView.this.frameBudget = 0.0f;
            } else {
                float f = Math.max(0.0f, (float)(l - MeshBackgroundView.this.lastVsyncNs) * 1.0E-9f);
                if (f >= 0.008333334f) {
                    MeshBackgroundView.this.requestRender();
                    MeshBackgroundView.this.frameBudget = 0.0f;
                } else {
                    MeshBackgroundView.this.frameBudget += f * 120.0f;
                    if (MeshBackgroundView.this.frameBudget >= 1.0f) {
                        MeshBackgroundView.this.frameBudget -= 1.0f;
                        MeshBackgroundView.this.requestRender();
                    }
                }
            }
            MeshBackgroundView.this.lastVsyncNs = l;
            MeshBackgroundView.this.choreographer.postFrameCallback((Choreographer.FrameCallback)this);
        }
    };

    public MeshBackgroundView(Context context) {
        super(context);
        this.choreographer = Choreographer.getInstance();
        this.setEGLContextClientVersion(2);
        this.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        this.setPreserveEGLContextOnPause(true);
        this.setClickable(false);
        this.setFocusable(false);
        this.setImportantForAccessibility(2);
        this.glRenderer = new FluidGlRenderer();
        this.setRenderer(this.glRenderer);
        this.setRenderMode(0);
    }

    public void setArtwork(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        this.queueEvent(() -> this.glRenderer.setArtwork(bitmap));
        this.requestRender();
    }

    public void setPalette(float[][] fArray) {
        this.queueEvent(() -> this.glRenderer.setPalette(fArray));
        this.requestRender();
    }

    public void setPulseSource(PulseSource pulseSource) {
        this.glRenderer.setPulseSource(pulseSource);
        this.requestRender();
    }

    public void rebuildMesh() {
        queueEvent(() -> {
            glRenderer.rebuildMesh();
        });
        requestRender();
    }

    public void setBlurEnabled(boolean bl) {
        this.queueEvent(() -> this.glRenderer.setBlurEnabled(bl));
        this.requestRender();
    }

    public void setIntensity(float f) {
        this.queueEvent(() -> this.glRenderer.setIntensity(f));
        this.requestRender();
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.updateRenderMode();
    }

    protected void onDetachedFromWindow() {
        this.setContinuous(false);
        super.onDetachedFromWindow();
    }

    protected void onWindowVisibilityChanged(int n) {
        super.onWindowVisibilityChanged(n);
        this.updateRenderMode();
    }

    protected void onVisibilityChanged(View view, int n) {
        super.onVisibilityChanged(view, n);
        this.updateRenderMode();
    }

    private void updateRenderMode() {
        this.setContinuous(this.isAttachedToWindow() && this.isShown());
    }

    private void setContinuous(boolean bl) {
        if (this.continuousRendering == bl) {
            return;
        }
        this.continuousRendering = bl;
        this.setRenderMode(0);
        if (bl) {
            this.lastVsyncNs = 0L;
            this.frameBudget = 0.0f;
            this.choreographer.postFrameCallback(this.frameCallback);
        } else {
            this.choreographer.removeFrameCallback(this.frameCallback);
        }
    }
}

