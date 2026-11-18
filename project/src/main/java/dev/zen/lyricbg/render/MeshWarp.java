/*
 * Decompiled with CFR 0.152.
 */
package dev.zen.lyricbg.render;

import dev.zen.lyricbg.render.MeshPresets;
import java.util.Random;

public final class MeshWarp {
    private static final int PORTRAIT_CONTROL = 6;
    private static final int LANDSCAPE_CONTROL = 9;
    private static final int SUBDIVISIONS = 2;
    private static final Random RANDOM = new Random();
    private static volatile int presetMask = 0x1F;

    /** Bitmask of allowed preset slots; at least one bit is always kept. */
    public static void setPresetMask(int mask) {
        presetMask = (mask & 0x1F) == 0 ? 0x1F : (mask & 0x1F);
    }

    private static int pickSlot() {
        int mask = presetMask;
        int count = Integer.bitCount(mask);
        if (count <= 1) return Integer.numberOfTrailingZeros(mask);
        int target = RANDOM.nextInt(count);
        for (int i = 0; i < 5; i++) {
            if ((mask & (1 << i)) == 0) continue;
            if (target == 0) return i;
            target--;
        }
        return Integer.numberOfTrailingZeros(mask);
    }

    private MeshWarp() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static Mesh create(boolean bl) {
        int n;
        int n2;
        float[] fArray;
        float[] fArray2;
        int n3;
        int n4;
        n4 = pickSlot();
        int n5 = bl ? n4 : (n4 < 2 ? n4 : (n4 < 4 ? 2 : 3));
        int n6 = n3 = bl ? 9 : 6;
        if (bl) {
            fArray2 = MeshWarp.identity(n3);
            fArray = MeshPresets.LANDSCAPE_TO[n5];
        } else {
            fArray2 = MeshPresets.PORTRAIT_FROM[n5];
            fArray = MeshPresets.PORTRAIT_TO[n5];
        }
        float[][] fArray3 = MeshWarp.grid(fArray2, n3);
        float[][] fArray4 = MeshWarp.grid(fArray, n3);
        for (n2 = 0; n2 < 2; ++n2) {
            fArray3 = MeshWarp.subdivide(fArray3);
            fArray4 = MeshWarp.subdivide(fArray4);
        }
        n2 = fArray3.length;
        int n7 = n2 - 1;
        float[] fArray5 = new float[n2 * n2 * 6];
        int n8 = 0;
        for (int i = 0; i < n2; ++i) {
            float f = (float)i / (float)n7;
            for (n = 0; n < n2; ++n) {
                float f2 = (float)n / (float)n7;
                fArray5[n8++] = fArray3[i][n * 2] * 2.0f - 1.0f;
                fArray5[n8++] = fArray3[i][n * 2 + 1] * 2.0f - 1.0f;
                fArray5[n8++] = fArray4[i][n * 2] * 2.0f - 1.0f;
                fArray5[n8++] = fArray4[i][n * 2 + 1] * 2.0f - 1.0f;
                fArray5[n8++] = f2;
                fArray5[n8++] = f;
            }
        }
        short[] sArray = new short[n7 * n7 * 6];
        n8 = 0;
        for (int i = 0; i < n7; ++i) {
            for (n = 0; n < n7; ++n) {
                short s = (short)(i * n2 + n);
                short s2 = (short)(s + 1);
                short s3 = (short)(s + n2);
                short s4 = (short)(s3 + 1);
                sArray[n8++] = s;
                sArray[n8++] = s3;
                sArray[n8++] = s4;
                sArray[n8++] = s4;
                sArray[n8++] = s2;
                sArray[n8++] = s;
            }
        }
        return new Mesh(fArray5, sArray, n2, bl ? 0.8f : 1.0f);
    }

    private static float[] identity(int n) {
        float[] fArray = new float[n * n * 2];
        int n2 = 0;
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < n; ++j) {
                fArray[n2++] = (float)j / (float)(n - 1);
                fArray[n2++] = (float)i / (float)(n - 1);
            }
        }
        return fArray;
    }

    private static float[][] grid(float[] fArray, int n) {
        float[][] fArray2 = new float[n][n * 2];
        for (int i = 0; i < n; ++i) {
            System.arraycopy(fArray, i * n * 2, fArray2[i], 0, n * 2);
        }
        return fArray2;
    }

    private static float[][] subdivide(float[][] fArray) {
        float f;
        float f2;
        int n;
        int n2;
        int n3;
        int n4 = fArray.length;
        int n5 = fArray[0].length / 2;
        float[][] fArray2 = new float[n4 - 1][(n5 - 1) * 2];
        for (int i = 0; i < n4 - 1; ++i) {
            for (n3 = 0; n3 < n5 - 1; ++n3) {
                for (n2 = 0; n2 < 2; ++n2) {
                    fArray2[i][n3 * 2 + n2] = (fArray[i][n3 * 2 + n2] + fArray[i][(n3 + 1) * 2 + n2] + fArray[i + 1][n3 * 2 + n2] + fArray[i + 1][(n3 + 1) * 2 + n2]) * 0.25f;
                }
            }
        }
        float[][] fArray3 = new float[n4 * 2 - 1][(n5 * 2 - 1) * 2];
        for (n3 = 0; n3 < n4; ++n3) {
            for (n2 = 0; n2 < n5; ++n2) {
                for (n = 0; n < 2; ++n) {
                    boolean bl;
                    f2 = fArray[n3][n2 * 2 + n];
                    boolean bl2 = n3 == 0 || n3 == n4 - 1;
                    boolean bl3 = bl = n2 == 0 || n2 == n5 - 1;
                    if (bl2 && bl) {
                        f = f2;
                    } else if (bl2) {
                        f = (fArray[n3][(n2 - 1) * 2 + n] + 6.0f * f2 + fArray[n3][(n2 + 1) * 2 + n]) / 8.0f;
                    } else if (bl) {
                        f = (fArray[n3 - 1][n2 * 2 + n] + 6.0f * f2 + fArray[n3 + 1][n2 * 2 + n]) / 8.0f;
                    } else {
                        float f3 = (fArray2[n3 - 1][(n2 - 1) * 2 + n] + fArray2[n3 - 1][n2 * 2 + n] + fArray2[n3][(n2 - 1) * 2 + n] + fArray2[n3][n2 * 2 + n]) * 0.25f;
                        float f4 = ((f2 + fArray[n3 - 1][n2 * 2 + n]) * 0.5f + (f2 + fArray[n3 + 1][n2 * 2 + n]) * 0.5f + (f2 + fArray[n3][(n2 - 1) * 2 + n]) * 0.5f + (f2 + fArray[n3][(n2 + 1) * 2 + n]) * 0.5f) * 0.25f;
                        f = (f3 + 2.0f * f4 + f2) * 0.25f;
                    }
                    fArray3[n3 * 2][n2 * 4 + n] = f;
                }
            }
        }
        for (n3 = 0; n3 < n4; ++n3) {
            for (n2 = 0; n2 < n5 - 1; ++n2) {
                for (n = 0; n < 2; ++n) {
                    f2 = fArray[n3][n2 * 2 + n];
                    f = fArray[n3][(n2 + 1) * 2 + n];
                    fArray3[n3 * 2][(n2 * 2 + 1) * 2 + n] = n3 == 0 || n3 == n4 - 1 ? (f2 + f) * 0.5f : (f2 + f + fArray2[n3 - 1][n2 * 2 + n] + fArray2[n3][n2 * 2 + n]) * 0.25f;
                }
            }
        }
        for (n3 = 0; n3 < n4 - 1; ++n3) {
            for (n2 = 0; n2 < n5; ++n2) {
                for (n = 0; n < 2; ++n) {
                    f2 = fArray[n3][n2 * 2 + n];
                    f = fArray[n3 + 1][n2 * 2 + n];
                    fArray3[n3 * 2 + 1][n2 * 4 + n] = n2 == 0 || n2 == n5 - 1 ? (f2 + f) * 0.5f : (f2 + f + fArray2[n3][(n2 - 1) * 2 + n] + fArray2[n3][n2 * 2 + n]) * 0.25f;
                }
            }
        }
        for (n3 = 0; n3 < n4 - 1; ++n3) {
            for (n2 = 0; n2 < n5 - 1; ++n2) {
                for (n = 0; n < 2; ++n) {
                    fArray3[n3 * 2 + 1][(n2 * 2 + 1) * 2 + n] = fArray2[n3][n2 * 2 + n];
                }
            }
        }
        return fArray3;
    }

    static final class Mesh {
        final float[] vertices;
        final short[] indices;
        final int pointCount;
        final float textureScale;

        Mesh(float[] fArray, short[] sArray, int n, float f) {
            this.vertices = fArray;
            this.indices = sArray;
            this.pointCount = n;
            this.textureScale = f;
        }
    }
}

