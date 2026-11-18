/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.graphics.Bitmap
 *  android.opengl.GLES20
 *  android.opengl.GLSurfaceView$Renderer
 *  android.opengl.GLUtils
 *  javax.microedition.khronos.egl.EGLConfig
 *  javax.microedition.khronos.opengles.GL10
 */
package dev.zen.lyricbg.render;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import dev.zen.lyricbg.render.BlurKernel;
import dev.zen.lyricbg.render.MeshWarp;
import dev.zen.lyricbg.render.Palette;
import dev.zen.lyricbg.render.PulseSource;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class FluidGlRenderer
implements GLSurfaceView.Renderer {
    private static final int ART_W = 0;
    private static final int ART_H = 1;
    private static final int FBO_W = 2;
    private static final int FBO_H = 3;
    private static final int FBO = 4;
    private static final int FBO_TEXTURE = 5;
    private static final float OUTPUT_SIGMA = 42.5f;
    private static final float SURFACE_DOWNSAMPLE = 10.0f;
    private static final String PRECISION = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n";
    private static final float[] QUAD_ART = new float[]{-1.0f, -1.0f, 0.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};
    private static final float[] QUAD_FBO = new float[]{-1.0f, -1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 1.0f, 0.0f, -1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
    private static final short[] QUAD_INDICES = new short[]{0, 1, 2, 2, 1, 3};
    private static final String VS_QUAD = "attribute vec2 aPos;attribute vec2 aUv;varying vec2 vUv;void main(){gl_Position=vec4(aPos,0.0,1.0);vUv=aUv;}";
    private static final String VS_PINCH = "attribute vec2 aFrom;attribute vec2 aTo;attribute vec2 aUv;uniform float uMorph;uniform vec4 uPinchTransform;varying vec2 vUv;void main(){vec2 p=mix(aFrom,aTo,uMorph);gl_Position=vec4(p,0.0,1.0);vUv=aUv*uPinchTransform.xy+uPinchTransform.zw;}";
    private static final String FS_TEXTURE = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\nvarying vec2 vUv;uniform sampler2D uTex;uniform sampler2D uPrev;uniform float uMix;void main(){vec3 c=mix(texture2D(uPrev,vUv).rgb,texture2D(uTex,vUv).rgb,uMix);gl_FragColor=vec4(c,1.0);}";
    private static final String FS_MATERIAL = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\nvarying vec2 vUv;uniform sampler2D uTex;uniform float uScrim;vec3 saturation(vec3 c,float s){vec3 r=vec3(0.2126+0.7873*s,0.2126-0.2126*s,0.2126-0.2126*s);vec3 g=vec3(0.7152-0.7152*s,0.7152+0.2848*s,0.7152-0.7152*s);vec3 b=vec3(0.0722-0.0722*s,0.0722-0.0722*s,0.0722+0.9278*s);return r*c.r+g*c.g+b*c.b;}void main(){vec3 c=texture2D(uTex,vUv).rgb;c=saturation(c,1.4);c=clamp(c,vec3(-0.752941),vec3(1.25098));c=saturation(c,0.70);c=mix(c,vec3(0.0),uScrim);float d=fract(52.9829189*fract(dot(gl_FragCoord.xy,vec2(0.06711056,0.00583715))))-0.5;c+=d/255.0;gl_FragColor=vec4(clamp(c,0.07,0.97),1.0);} ";
    private final Object artworkLock = new Object();
    private Bitmap pendingArtwork;
    private Bitmap currentArtwork;
    private Bitmap previousArtwork;
    private float[][] palette = Palette.fallback();
    private volatile boolean blurEnabled = true;
    private float intensity = 0.5f;
    private volatile PulseSource pulseSource;
    private int currentTexture;
    private int previousTexture;
    private int rotationTexture;
    private int horizontalTexture;
    private int blurredTexture;
    private int rotationFbo;
    private int horizontalFbo;
    private int blurredFbo;
    private int rotationProgram;
    private int blurProgram;
    private int materialProgram;
    private int pinchProgram;
    private int quadArtVbo;
    private int quadFboVbo;
    private int quadEbo;
    private int meshVbo;
    private int meshEbo;
    private int width;
    private int height;
    private int smallWidth;
    private int smallHeight;
    private MeshWarp.Mesh mesh;
    private FloatBuffer quadScratch;
    private long animationStartNs;
    private long transitionStartNs;
    private long lastPulseUpdateNs;
    private float smoothedPulse;
    private int rPos;
    private int rUv;
    private int rTex;
    private int rPrev;
    private int rMix;
    private int bPos;
    private int bUv;
    private int bTex;
    private int bTexel;
    private int bDirection;
    private int bScale;
    private int bNormalize;
    private int mPos;
    private int mUv;
    private int mTex;
    private int mScrim;
    private int pFrom;
    private int pTo;
    private int pUv;
    private int pMorph;
    private int pTransform;
    private int pTex;
    private int pScrim;

    FluidGlRenderer() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void setArtwork(Bitmap bitmap) {
        Object object = this.artworkLock;
        synchronized (object) {
            if (this.pendingArtwork != null && this.pendingArtwork != bitmap && !this.pendingArtwork.isRecycled()) {
                this.pendingArtwork.recycle();
            }
            this.pendingArtwork = bitmap;
        }
    }

    void setPalette(float[][] fArray) {
        if (fArray == null || fArray.length == 0) {
            return;
        }
        this.palette = FluidGlRenderer.copyPalette(fArray);
        int n = this.paletteTexture(this.palette);
        this.beginTransition(n, null);
    }

    void setPulseSource(PulseSource pulseSource) {
        this.pulseSource = pulseSource;
    }

    void setBlurEnabled(boolean bl) {
        this.blurEnabled = bl;
    }

    void setIntensity(float f) {
        this.intensity = Math.max(0.0f, Math.min(1.0f, f));
    }

    public void onSurfaceCreated(GL10 gL10, EGLConfig eGLConfig) {
        this.blurredTexture = 0;
        this.horizontalTexture = 0;
        this.rotationTexture = 0;
        this.previousTexture = 0;
        this.currentTexture = 0;
        this.blurredFbo = 0;
        this.horizontalFbo = 0;
        this.rotationFbo = 0;
        this.meshEbo = 0;
        this.meshVbo = 0;
        this.quadEbo = 0;
        this.quadFboVbo = 0;
        this.quadArtVbo = 0;
        GLES20.glDisable((int)2929);
        GLES20.glDisable((int)3042);
        GLES20.glDisable((int)2884);
        GLES20.glClearColor((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f);
        this.rotationProgram = FluidGlRenderer.program(VS_QUAD, FS_TEXTURE);
        this.blurProgram = FluidGlRenderer.program(VS_QUAD, FluidGlRenderer.buildBlurShader());
        this.materialProgram = FluidGlRenderer.program(VS_QUAD, FS_MATERIAL);
        this.pinchProgram = FluidGlRenderer.program(VS_PINCH, FS_MATERIAL);
        this.cacheLocations();
        this.quadArtVbo = FluidGlRenderer.arrayBuffer(QUAD_ART);
        this.quadFboVbo = FluidGlRenderer.arrayBuffer(QUAD_FBO);
        this.quadEbo = FluidGlRenderer.elementBuffer(QUAD_INDICES);
        this.quadScratch = FluidGlRenderer.direct(QUAD_ART.length);
        this.animationStartNs = System.nanoTime();
        this.currentTexture = this.currentArtwork != null && !this.currentArtwork.isRecycled() ? this.textureFromBitmap(this.currentArtwork) : this.paletteTexture(this.palette);
        this.previousTexture = this.previousArtwork != null && !this.previousArtwork.isRecycled() ? this.textureFromBitmap(this.previousArtwork) : this.currentTexture;
    }

    public void onSurfaceChanged(GL10 gL10, int n, int n2) {
        this.width = Math.max(1, n);
        this.height = Math.max(1, n2);
        this.smallWidth = Math.max(1, (int)Math.ceil((float)this.width / 10.0f));
        this.smallHeight = Math.max(1, (int)Math.ceil((float)this.height / 10.0f));
        FluidGlRenderer.deleteTarget(this.rotationTexture, this.rotationFbo);
        FluidGlRenderer.deleteTarget(this.horizontalTexture, this.horizontalFbo);
        FluidGlRenderer.deleteTarget(this.blurredTexture, this.blurredFbo);
        int[] nArray = FluidGlRenderer.createTarget(this.smallWidth, this.smallHeight);
        this.rotationTexture = nArray[5];
        this.rotationFbo = nArray[4];
        nArray = FluidGlRenderer.createTarget(this.smallWidth, this.smallHeight);
        this.horizontalTexture = nArray[5];
        this.horizontalFbo = nArray[4];
        nArray = FluidGlRenderer.createTarget(this.smallWidth, this.smallHeight);
        this.blurredTexture = nArray[5];
        this.blurredFbo = nArray[4];
        if (this.meshVbo != 0) {
            GLES20.glDeleteBuffers((int)1, (int[])new int[]{this.meshVbo}, (int)0);
        }
        if (this.meshEbo != 0) {
            GLES20.glDeleteBuffers((int)1, (int[])new int[]{this.meshEbo}, (int)0);
        }
        this.mesh = MeshWarp.create(this.width >= this.height);
        this.meshVbo = FluidGlRenderer.arrayBuffer(this.mesh.vertices);
        this.meshEbo = FluidGlRenderer.elementBuffer(this.mesh.indices);
        GLES20.glBindFramebuffer((int)36160, (int)0);
    }

    /** Rebuilds only the mesh, so a preset change applies without a new surface. */
    void rebuildMesh() {
        if (this.meshVbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{this.meshVbo}, 0);
        }
        this.meshVbo = 0;
        if (this.meshEbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{this.meshEbo}, 0);
        }
        this.meshEbo = 0;
        if (this.width <= 0 || this.height <= 0) {
            this.mesh = null;
            return;
        }
        this.mesh = MeshWarp.create(this.width >= this.height);
        this.meshVbo = FluidGlRenderer.arrayBuffer(this.mesh.vertices);
        this.meshEbo = FluidGlRenderer.elementBuffer(this.mesh.indices);
    }

    public void onDrawFrame(GL10 gL10) {
        long l = System.nanoTime();
        this.uploadPendingArtwork();
        if (this.width <= 0 || this.height <= 0 || this.currentTexture == 0) {
            return;
        }
        float f = 0.7f + 0.6f * this.intensity;
        float f2 = (float)(l - this.animationStartNs) * 1.0E-9f * f;
        float f3 = 0.0f;
        PulseSource pulseSource = this.pulseSource;
        if (pulseSource != null) {
            try {
                f3 = Math.max(0.0f, Math.min(1.0f, pulseSource.value(f2)));
            }
            catch (Throwable throwable) {
                f3 = 0.0f;
            }
        }
        float f4 = this.lastPulseUpdateNs == 0L ? 0.016666668f : Math.max(0.0f, Math.min(0.1f, (float)(l - this.lastPulseUpdateNs) * 1.0E-9f));
        this.lastPulseUpdateNs = l;
        float f5 = f3 > this.smoothedPulse ? 0.045f : 0.18f;
        float f6 = 1.0f - (float)Math.exp(-f4 / f5);
        this.smoothedPulse += (f3 - this.smoothedPulse) * f6;
        float f7 = this.smoothedPulse;
        float f8 = this.previousTexture == this.currentTexture ? 1.0f : Math.min(1.0f, (float)(l - this.transitionStartNs) / 5.0E8f);
        this.renderRotatingArtwork(f2, f8, f7);
        int n = this.rotationTexture;
        if (this.blurEnabled) {
            this.renderBlur(this.rotationTexture, this.horizontalFbo, 1.0f, 0.0f, false);
            this.renderBlur(this.horizontalTexture, this.blurredFbo, 0.0f, 1.0f, true);
            n = this.blurredTexture;
        }
        GLES20.glBindFramebuffer((int)36160, (int)0);
        GLES20.glViewport((int)0, (int)0, (int)this.width, (int)this.height);
        GLES20.glClear((int)16384);
        this.drawMaterialQuad(n);
        this.drawPinchMesh(n, f2);
        if (f8 >= 1.0f && this.previousTexture != this.currentTexture) {
            GLES20.glDeleteTextures((int)1, (int[])new int[]{this.previousTexture}, (int)0);
            this.previousTexture = this.currentTexture;
            this.recyclePreviousArtwork();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void uploadPendingArtwork() {
        Bitmap bitmap;
        Object object = this.artworkLock;
        synchronized (object) {
            bitmap = this.pendingArtwork;
            this.pendingArtwork = null;
        }
        if (bitmap == null) {
            return;
        }
        if (bitmap.isRecycled()) {
            return;
        }
        int n = this.textureFromBitmap(bitmap);
        if (n == 0) {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return;
        }
        this.beginTransition(n, bitmap);
    }

    private void beginTransition(int n, Bitmap bitmap) {
        if (n == 0) {
            return;
        }
        if (this.previousTexture != 0 && this.previousTexture != this.currentTexture) {
            GLES20.glDeleteTextures((int)1, (int[])new int[]{this.previousTexture}, (int)0);
        }
        this.recyclePreviousArtwork();
        this.previousTexture = this.currentTexture == 0 ? n : this.currentTexture;
        this.previousArtwork = this.currentArtwork;
        this.currentTexture = n;
        this.currentArtwork = bitmap;
        this.transitionStartNs = System.nanoTime();
        if (this.previousTexture == this.currentTexture) {
            this.transitionStartNs = 0L;
        }
    }

    private void recyclePreviousArtwork() {
        if (this.previousArtwork != null && this.previousArtwork != this.currentArtwork && !this.previousArtwork.isRecycled()) {
            this.previousArtwork.recycle();
        }
        this.previousArtwork = null;
    }

    private void renderRotatingArtwork(float f, float f2, float f3) {
        GLES20.glBindFramebuffer((int)36160, (int)this.rotationFbo);
        GLES20.glViewport((int)0, (int)0, (int)this.smallWidth, (int)this.smallHeight);
        GLES20.glClear((int)16384);
        this.drawFillQuad(this.viewScaleX(), this.viewScaleY(), f2);
        this.drawRotationLayer(0, f, f2, f3);
        this.drawRotationLayer(1, f, f2, f3);
        this.drawRotationLayer(2, f, f2, f3);
    }

    private void drawFillQuad(float f, float f2, float f3) {
        this.quadScratch.clear();
        for (int i = 0; i < 4; ++i) {
            int n = i * 4;
            this.quadScratch.put(QUAD_ART[n]).put(QUAD_ART[n + 1]);
            this.quadScratch.put((QUAD_ART[n + 2] - 0.5f) / f + 0.5f);
            this.quadScratch.put((QUAD_ART[n + 3] - 0.5f) / f2 + 0.5f);
        }
        this.quadScratch.flip();
        this.drawRotationQuad(this.quadScratch, f3);
    }

    private void drawRotationLayer(int n, float f, float f2, float f3) {
        float f4 = n == 2 ? 0.7f : 0.0f;
        float f5;
        float f6 = n == 1 ? 70.0f : (n == 2 ? 90.0f : 120.0f);
        float f7 = f * ((float)Math.PI * 2) / f6;
        float f8 = (float)Math.cos(f7);
        float f9 = (float)Math.sin(f7);
        float f10 = f5 = n == 0 ? 1.4f : 0.7f;
        float f11 = n == 1 ? -0.25f : f4;
        float f12 = n == 1 ? 0.15f : (n == 2 ? 0.7f : 0.0f);
        float f13 = this.viewScaleX();
        float f14 = this.viewScaleY();
        float f15 = 1.0f + 0.4f * f3 * f3;
        float f16 = f * ((float)Math.PI * 2) / 120.0f;
        float f17 = (float)Math.cos(f16);
        float f18 = (float)Math.sin(f16);
        this.quadScratch.clear();
        for (int i = 0; i < 4; ++i) {
            int n2 = i * 4;
            float f19 = QUAD_ART[n2];
            float f20 = QUAD_ART[n2 + 1];
            float f21 = (f19 * f8 - f20 * f9) * f5 + f4;
            float f22 = (f19 * f9 + f20 * f8) * f5 + f12;
            f21 *= f13;
            f22 *= f14;
            if (n == 2) {
                float f23 = f21 * f17 - f22 * f18;
                f22 = f21 * f18 + f22 * f17;
                f21 = f23;
            }
            this.quadScratch.put(f21 *= f15).put(f22 *= f15).put(QUAD_ART[n2 + 2]).put(QUAD_ART[n2 + 3]);
        }
        this.quadScratch.flip();
        this.drawRotationQuad(this.quadScratch, f2);
    }

    private void drawRotationQuad(FloatBuffer floatBuffer, float f) {
        GLES20.glUseProgram((int)this.rotationProgram);
        this.bindClientQuad(floatBuffer, this.rPos, this.rUv);
        this.setTransitionTextures(f);
        GLES20.glDrawElements((int)4, (int)6, (int)5123, (int)0);
    }

    private void setTransitionTextures(float f) {
        GLES20.glActiveTexture((int)33984);
        GLES20.glBindTexture((int)3553, (int)this.currentTexture);
        GLES20.glUniform1i((int)this.rTex, (int)0);
        GLES20.glActiveTexture((int)33985);
        GLES20.glBindTexture((int)3553, (int)this.previousTexture);
        GLES20.glUniform1i((int)this.rPrev, (int)1);
        GLES20.glUniform1f((int)this.rMix, (float)f);
    }

    private void renderBlur(int n, int n2, float f, float f2, boolean bl) {
        GLES20.glBindFramebuffer((int)36160, (int)n2);
        GLES20.glViewport((int)0, (int)0, (int)this.smallWidth, (int)this.smallHeight);
        GLES20.glUseProgram((int)this.blurProgram);
        this.bindQuadVbo(this.blurProgram, this.quadFboVbo, this.bPos, this.bUv);
        GLES20.glActiveTexture((int)33984);
        GLES20.glBindTexture((int)3553, (int)n);
        GLES20.glUniform1i((int)this.bTex, (int)0);
        GLES20.glUniform2f((int)this.bTexel, (float)(1.0f / (float)this.smallWidth), (float)(1.0f / (float)this.smallHeight));
        GLES20.glUniform2f((int)this.bDirection, (float)f, (float)f2);
        GLES20.glUniform2f((int)this.bScale, (float)(4.0f * (float)this.smallWidth / (float)this.width), (float)(4.0f * (float)this.smallHeight / (float)this.height));
        GLES20.glUniform1f((int)this.bNormalize, (float)(bl ? 1.0f : 0.0f));
        GLES20.glBindBuffer((int)34963, (int)this.quadEbo);
        GLES20.glDrawElements((int)4, (int)6, (int)5123, (int)0);
    }

    private void drawMaterialQuad(int n) {
        GLES20.glUseProgram((int)this.materialProgram);
        this.bindQuadVbo(this.materialProgram, this.quadFboVbo, this.mPos, this.mUv);
        GLES20.glActiveTexture((int)33984);
        GLES20.glBindTexture((int)3553, (int)n);
        GLES20.glUniform1i((int)this.mTex, (int)0);
        GLES20.glUniform1f((int)this.mScrim, (float)0.4f);
        GLES20.glBindBuffer((int)34963, (int)this.quadEbo);
        GLES20.glDrawElements((int)4, (int)6, (int)5123, (int)0);
    }

    private void drawPinchMesh(int n, float f) {
        if (this.mesh == null || this.meshVbo == 0 || this.meshEbo == 0) {
            return;
        }
        float f2 = (float)(Math.acos(Math.sin((double)f * Math.PI / 5.0)) / Math.PI);
        float f3 = f2 * f2 * (3.0f - 2.0f * f2);
        f3 = 0.5f + (f3 - 0.5f) * (0.65f + 0.7f * this.intensity);
        float f4 = (1.0f - this.mesh.textureScale) * 0.5f;
        GLES20.glUseProgram((int)this.pinchProgram);
        GLES20.glBindBuffer((int)34962, (int)this.meshVbo);
        GLES20.glBindBuffer((int)34963, (int)this.meshEbo);
        GLES20.glEnableVertexAttribArray((int)this.pFrom);
        GLES20.glEnableVertexAttribArray((int)this.pTo);
        GLES20.glEnableVertexAttribArray((int)this.pUv);
        GLES20.glVertexAttribPointer((int)this.pFrom, (int)2, (int)5126, (boolean)false, (int)24, (int)0);
        GLES20.glVertexAttribPointer((int)this.pTo, (int)2, (int)5126, (boolean)false, (int)24, (int)8);
        GLES20.glVertexAttribPointer((int)this.pUv, (int)2, (int)5126, (boolean)false, (int)24, (int)16);
        GLES20.glUniform1f((int)this.pMorph, (float)f3);
        GLES20.glUniform4f((int)this.pTransform, (float)this.mesh.textureScale, (float)this.mesh.textureScale, (float)f4, (float)f4);
        GLES20.glActiveTexture((int)33984);
        GLES20.glBindTexture((int)3553, (int)n);
        GLES20.glUniform1i((int)this.pTex, (int)0);
        GLES20.glUniform1f((int)this.pScrim, (float)0.4f);
        GLES20.glDrawElements((int)4, (int)this.mesh.indices.length, (int)5123, (int)0);
    }

    private void bindClientQuad(FloatBuffer floatBuffer, int n, int n2) {
        GLES20.glBindBuffer((int)34962, (int)0);
        GLES20.glBindBuffer((int)34963, (int)0);
        floatBuffer.position(0);
        GLES20.glVertexAttribPointer((int)n, (int)2, (int)5126, (boolean)false, (int)16, (Buffer)floatBuffer);
        GLES20.glEnableVertexAttribArray((int)n);
        floatBuffer.position(2);
        GLES20.glVertexAttribPointer((int)n2, (int)2, (int)5126, (boolean)false, (int)16, (Buffer)floatBuffer);
        GLES20.glEnableVertexAttribArray((int)n2);
        GLES20.glBindBuffer((int)34963, (int)this.quadEbo);
    }

    private void bindQuadVbo(int n, int n2, int n3, int n4) {
        GLES20.glBindBuffer((int)34962, (int)n2);
        GLES20.glEnableVertexAttribArray((int)n3);
        GLES20.glEnableVertexAttribArray((int)n4);
        GLES20.glVertexAttribPointer((int)n3, (int)2, (int)5126, (boolean)false, (int)16, (int)0);
        GLES20.glVertexAttribPointer((int)n4, (int)2, (int)5126, (boolean)false, (int)16, (int)8);
    }

    private float viewScaleX() {
        return this.width >= this.height ? 1.0f : (float)this.height / (float)this.width;
    }

    private float viewScaleY() {
        return this.width >= this.height ? (float)this.width / (float)this.height : 1.0f;
    }

    private int textureFromBitmap(Bitmap bitmap) {
        int n = FluidGlRenderer.newTexture();
        try {
            GLES20.glBindTexture((int)3553, (int)n);
            GLUtils.texImage2D((int)3553, (int)0, (Bitmap)bitmap, (int)0);
            FluidGlRenderer.textureParameters();
            return n;
        }
        catch (Throwable throwable) {
            GLES20.glDeleteTextures((int)1, (int[])new int[]{n}, (int)0);
            return 0;
        }
    }

    private int paletteTexture(float[][] fArray) {
        int n;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(16384).order(ByteOrder.nativeOrder());
        for (n = 0; n < 64; ++n) {
            float f = (float)n / 63.0f * (float)(fArray.length - 1);
            int n2 = Math.min(fArray.length - 1, (int)f);
            int n3 = Math.min(fArray.length - 1, n2 + 1);
            float f2 = f - (float)n2;
            for (int i = 0; i < 64; ++i) {
                float f3 = ((float)i / 63.0f - 0.5f) * 0.06f;
                for (int j = 0; j < 3; ++j) {
                    float f4 = fArray[n2][j] * (1.0f - f2) + fArray[n3][j] * f2 + f3;
                    byteBuffer.put((byte)Math.round(Math.max(0.0f, Math.min(1.0f, f4)) * 255.0f));
                }
                byteBuffer.put((byte)-1);
            }
        }
        byteBuffer.position(0);
        n = FluidGlRenderer.newTexture();
        GLES20.glBindTexture((int)3553, (int)n);
        GLES20.glTexImage2D((int)3553, (int)0, (int)6408, (int)64, (int)64, (int)0, (int)6408, (int)5121, (Buffer)byteBuffer);
        FluidGlRenderer.textureParameters();
        return n;
    }

    private static float[][] copyPalette(float[][] fArray) {
        float[][] fArray2 = new float[fArray.length][3];
        for (int i = 0; i < fArray.length; ++i) {
            for (int j = 0; j < 3; ++j) {
                fArray2[i][j] = fArray[i].length > j ? fArray[i][j] : 0.0f;
            }
        }
        return fArray2;
    }

    private static int[] createTarget(int n, int n2) {
        int n3 = FluidGlRenderer.newTexture();
        GLES20.glBindTexture((int)3553, (int)n3);
        FluidGlRenderer.textureParameters();
        GLES20.glTexImage2D((int)3553, (int)0, (int)6408, (int)n, (int)n2, (int)0, (int)6408, (int)5121, null);
        int[] nArray = new int[1];
        GLES20.glGenFramebuffers((int)1, (int[])nArray, (int)0);
        GLES20.glBindFramebuffer((int)36160, (int)nArray[0]);
        GLES20.glFramebufferTexture2D((int)36160, (int)36064, (int)3553, (int)n3, (int)0);
        int n4 = GLES20.glCheckFramebufferStatus((int)36160);
        if (n4 != 36053) {
            GLES20.glDeleteTextures((int)1, (int[])new int[]{n3}, (int)0);
            GLES20.glDeleteFramebuffers((int)1, (int[])nArray, (int)0);
            throw new IllegalStateException("Fluid render target unavailable: 0x" + Integer.toHexString(n4));
        }
        return new int[]{n, n2, n, n2, nArray[0], n3};
    }

    private static int newTexture() {
        int[] nArray = new int[1];
        GLES20.glGenTextures((int)1, (int[])nArray, (int)0);
        return nArray[0];
    }

    private static void textureParameters() {
        GLES20.glTexParameteri((int)3553, (int)10241, (int)9729);
        GLES20.glTexParameteri((int)3553, (int)10240, (int)9729);
        GLES20.glTexParameteri((int)3553, (int)10242, (int)33071);
        GLES20.glTexParameteri((int)3553, (int)10243, (int)33071);
    }

    private static void deleteTarget(int n, int n2) {
        if (n != 0) {
            GLES20.glDeleteTextures((int)1, (int[])new int[]{n}, (int)0);
        }
        if (n2 != 0) {
            GLES20.glDeleteFramebuffers((int)1, (int[])new int[]{n2}, (int)0);
        }
    }

    private static int arrayBuffer(float[] fArray) {
        int[] nArray = new int[1];
        GLES20.glGenBuffers((int)1, (int[])nArray, (int)0);
        FloatBuffer floatBuffer = FluidGlRenderer.floats(fArray);
        GLES20.glBindBuffer((int)34962, (int)nArray[0]);
        GLES20.glBufferData((int)34962, (int)(fArray.length * 4), (Buffer)floatBuffer, (int)35044);
        GLES20.glBindBuffer((int)34962, (int)0);
        return nArray[0];
    }

    private static int elementBuffer(short[] sArray) {
        int[] nArray = new int[1];
        GLES20.glGenBuffers((int)1, (int[])nArray, (int)0);
        ShortBuffer shortBuffer = FluidGlRenderer.shorts(sArray);
        GLES20.glBindBuffer((int)34963, (int)nArray[0]);
        GLES20.glBufferData((int)34963, (int)(sArray.length * 2), (Buffer)shortBuffer, (int)35044);
        GLES20.glBindBuffer((int)34963, (int)0);
        return nArray[0];
    }

    private static FloatBuffer floats(float[] fArray) {
        FloatBuffer floatBuffer = FluidGlRenderer.direct(fArray.length);
        floatBuffer.put(fArray).position(0);
        return floatBuffer;
    }

    private static FloatBuffer direct(int n) {
        return ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static ShortBuffer shorts(short[] sArray) {
        ShortBuffer shortBuffer = ByteBuffer.allocateDirect(sArray.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        shortBuffer.put(sArray).position(0);
        return shortBuffer;
    }

    private void cacheLocations() {
        this.rPos = GLES20.glGetAttribLocation((int)this.rotationProgram, (String)"aPos");
        this.rUv = GLES20.glGetAttribLocation((int)this.rotationProgram, (String)"aUv");
        this.rTex = GLES20.glGetUniformLocation((int)this.rotationProgram, (String)"uTex");
        this.rPrev = GLES20.glGetUniformLocation((int)this.rotationProgram, (String)"uPrev");
        this.rMix = GLES20.glGetUniformLocation((int)this.rotationProgram, (String)"uMix");
        this.bPos = GLES20.glGetAttribLocation((int)this.blurProgram, (String)"aPos");
        this.bUv = GLES20.glGetAttribLocation((int)this.blurProgram, (String)"aUv");
        this.bTex = GLES20.glGetUniformLocation((int)this.blurProgram, (String)"uTex");
        this.bTexel = GLES20.glGetUniformLocation((int)this.blurProgram, (String)"uTexel");
        this.bDirection = GLES20.glGetUniformLocation((int)this.blurProgram, (String)"uDirection");
        this.bScale = GLES20.glGetUniformLocation((int)this.blurProgram, (String)"uScale");
        this.bNormalize = GLES20.glGetUniformLocation((int)this.blurProgram, (String)"uNormalize");
        this.mPos = GLES20.glGetAttribLocation((int)this.materialProgram, (String)"aPos");
        this.mUv = GLES20.glGetAttribLocation((int)this.materialProgram, (String)"aUv");
        this.mTex = GLES20.glGetUniformLocation((int)this.materialProgram, (String)"uTex");
        this.mScrim = GLES20.glGetUniformLocation((int)this.materialProgram, (String)"uScrim");
        this.pFrom = GLES20.glGetAttribLocation((int)this.pinchProgram, (String)"aFrom");
        this.pTo = GLES20.glGetAttribLocation((int)this.pinchProgram, (String)"aTo");
        this.pUv = GLES20.glGetAttribLocation((int)this.pinchProgram, (String)"aUv");
        this.pMorph = GLES20.glGetUniformLocation((int)this.pinchProgram, (String)"uMorph");
        this.pTransform = GLES20.glGetUniformLocation((int)this.pinchProgram, (String)"uPinchTransform");
        this.pTex = GLES20.glGetUniformLocation((int)this.pinchProgram, (String)"uTex");
        this.pScrim = GLES20.glGetUniformLocation((int)this.pinchProgram, (String)"uScrim");
    }

    private static String buildBlurShader() {
        StringBuilder stringBuilder = new StringBuilder(24000);
        stringBuilder.append(PRECISION).append("varying vec2 vUv;uniform sampler2D uTex;").append("uniform vec2 uTexel;uniform vec2 uDirection;uniform vec2 uScale;").append("uniform float uNormalize;").append("vec4 sampleZero(vec2 p){vec2 c=clamp(p/uTexel+0.5,0.0,1.0)*clamp((1.0-p)/uTexel+0.5,0.0,1.0);if(p.x< -0.5*uTexel.x||p.y< -0.5*uTexel.y||p.x>1.0+0.5*uTexel.x||p.y>1.0+0.5*uTexel.y)return vec4(0.0);return texture2D(uTex,clamp(p,0.0,1.0))*c.x*c.y;}").append("void main(){vec4 c=sampleZero(vUv)*").append(0.009389732f).append(";");
        for (int i = 0; i < BlurKernel.OFFSETS.length; ++i) {
            stringBuilder.append("c+=(sampleZero(vUv+uDirection*uTexel*uScale*").append(BlurKernel.OFFSETS[i]).append(")+").append("sampleZero(vUv-uDirection*uTexel*uScale*").append(BlurKernel.OFFSETS[i]).append("))*").append(BlurKernel.WEIGHTS[i]).append(";");
        }
        stringBuilder.append("if(uNormalize>0.5){c.rgb/=max(c.a,1.0/65535.0);c.a=1.0;}").append("gl_FragColor=c;}");
        return stringBuilder.toString();
    }

    private static int program(String string, String string2) {
        int n = FluidGlRenderer.shader(35633, string);
        int n2 = FluidGlRenderer.shader(35632, string2);
        int n3 = GLES20.glCreateProgram();
        GLES20.glAttachShader((int)n3, (int)n);
        GLES20.glAttachShader((int)n3, (int)n2);
        GLES20.glLinkProgram((int)n3);
        int[] nArray = new int[1];
        GLES20.glGetProgramiv((int)n3, (int)35714, (int[])nArray, (int)0);
        GLES20.glDeleteShader((int)n);
        GLES20.glDeleteShader((int)n2);
        if (nArray[0] == 0) {
            throw new IllegalStateException("Fluid shader link failed: " + GLES20.glGetProgramInfoLog((int)n3));
        }
        return n3;
    }

    private static int shader(int n, String string) {
        int n2 = GLES20.glCreateShader((int)n);
        GLES20.glShaderSource((int)n2, (String)string);
        GLES20.glCompileShader((int)n2);
        int[] nArray = new int[1];
        GLES20.glGetShaderiv((int)n2, (int)35713, (int[])nArray, (int)0);
        if (nArray[0] == 0) {
            throw new IllegalStateException("Fluid shader compile failed: " + GLES20.glGetShaderInfoLog((int)n2));
        }
        return n2;
    }
}

