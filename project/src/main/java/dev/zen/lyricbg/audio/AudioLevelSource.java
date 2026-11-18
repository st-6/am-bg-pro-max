/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.media.AudioTrack
 *  android.os.Build$VERSION
 *  android.os.SystemClock
 *  dev.amenhancer.plugin.api.PluginCall
 *  dev.amenhancer.plugin.api.PluginContext
 *  dev.amenhancer.plugin.api.PluginHook
 *  dev.amenhancer.plugin.api.PluginRegistration
 */
package dev.zen.lyricbg.audio;

import android.content.Context;
import android.media.AudioTrack;
import android.os.Build;
import android.os.SystemClock;
import dev.amenhancer.plugin.api.PluginCall;
import dev.amenhancer.plugin.api.PluginContext;
import dev.amenhancer.plugin.api.PluginHook;
import dev.amenhancer.plugin.api.PluginRegistration;
import dev.zen.lyricbg.audio.AudioPlaybackBridge;
import dev.zen.lyricbg.render.PulseSource;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class AudioLevelSource
implements PulseSource {
    private static final long AUDIO_TIMEOUT_MS = 6000L;
    private static final int ENCODING_PCM_16BIT = 2;
    private static final int ENCODING_PCM_FLOAT = 4;
    private static final int WINDOW_SAMPLES = 2048;
    private static final int KIND_BYTE_OFFSET = 0;
    private static final int KIND_BYTE_MODE = 1;
    private static final int KIND_SHORT_OFFSET = 2;
    private static final int KIND_SHORT_MODE = 3;
    private static final int KIND_FLOAT_MODE = 4;
    private static final int KIND_BUFFER = 5;
    private final PluginContext context;
    private final AudioPlaybackBridge playbackBridge;
    private final List<PluginRegistration> registrations = new ArrayList<PluginRegistration>();
    private final Map<Object, Integer> encodings = Collections.synchronizedMap(new WeakHashMap());
    private final Map<Object, Integer> sampleRates = Collections.synchronizedMap(new WeakHashMap());
    private final AtomicLong writeCalls = new AtomicLong();
    private volatile float level;
    private volatile long lastAudioAt;
    private volatile boolean sawAudio;
    private volatile float externalLevel;
    private volatile long externalAt;
    private volatile long externalReports;
    private volatile int bridgeState;
    private volatile float bpm = 100.0f;
    private volatile boolean metronomeEnabled;
    private volatile boolean enabled = true;
    private boolean installed;
    private int hooksRegistered;
    private int hooksFailed;
    private String firstHookError;
    private int unsupportedEncoding = Integer.MIN_VALUE;
    private final Biquad bassFilter = new Biquad();
    private final Biquad midFilter = new Biquad();
    private int filterRate;
    private float bassEnvelope;
    private float midEnvelope;
    private float envelopeAttack;
    private float envelopeRelease;
    private float slowBassDb = -72.0f;
    private float slowMidDb = -72.0f;
    private final float[] history = new float[3];
    private int historyWrite;
    private int historyCount;
    private float output;
    private float peak = 0.45f;

    public AudioLevelSource(PluginContext pluginContext) {
        this.context = pluginContext;
        this.playbackBridge = new AudioPlaybackBridge((Context)pluginContext.getApplication(), this);
    }

    public synchronized void install() {
        if (this.installed) {
            this.playbackBridge.bind();
            return;
        }
        this.installed = true;
        this.hook(new Class[]{byte[].class, Integer.TYPE, Integer.TYPE}, 0);
        this.hook(new Class[]{byte[].class, Integer.TYPE, Integer.TYPE, Integer.TYPE}, 1);
        this.hook(new Class[]{short[].class, Integer.TYPE, Integer.TYPE}, 2);
        this.hook(new Class[]{short[].class, Integer.TYPE, Integer.TYPE, Integer.TYPE}, 3);
        this.hook(new Class[]{float[].class, Integer.TYPE, Integer.TYPE, Integer.TYPE}, 4);
        this.hook(new Class[]{ByteBuffer.class, Integer.TYPE}, 5);
        this.hook(new Class[]{ByteBuffer.class, Integer.TYPE, Integer.TYPE}, 5);
        this.playbackBridge.bind();
    }

    private void hook(Class<?>[] classArray, int n) {
        block2: {
            try {
                Method method = AudioTrack.class.getDeclaredMethod("write", classArray);
                this.registrations.add(this.context.getHooks().hook((Executable)method, false, (PluginHook)new Tap(n, classArray.length)));
                ++this.hooksRegistered;
            }
            catch (Throwable throwable) {
                ++this.hooksFailed;
                if (this.firstHookError != null) break block2;
                this.firstHookError = throwable.getClass().getSimpleName() + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
            }
        }
    }

    private void logEncoding(int n) {
        if (this.unsupportedEncoding == n) {
            return;
        }
        this.unsupportedEncoding = n;
        this.context.log("跳过不支持的音频编码 " + n + "（PCM16=2, FLOAT=4）", null);
    }

    private void count() {
        if (this.writeCalls.incrementAndGet() == 1L) {
            this.context.log("已捕获 AudioTrack.write，开始分析音频电平", null);
        }
    }

    private void analyzeBytes(byte[] byArray, int n, int n2, Object object) {
        int n3 = Math.min(n2 / 2, (byArray.length - n) / 2);
        if (n3 <= 0) {
            return;
        }
        this.beginWindow(object);
        int n4 = Math.min(n3, 2048);
        for (int i = 0; i < n4; ++i) {
            int n5 = n + i * 2;
            this.pushSample((float)((short)(byArray[n5] & 0xFF | byArray[n5 + 1] << 8)) / 32768.0f);
        }
        this.report();
    }

    private void analyzeShorts(short[] sArray, int n, int n2, Object object) {
        int n3 = Math.min(n2, sArray.length - n);
        if (n3 <= 0) {
            return;
        }
        this.beginWindow(object);
        int n4 = Math.min(n3, 2048);
        for (int i = 0; i < n4; ++i) {
            this.pushSample((float)sArray[n + i] / 32768.0f);
        }
        this.report();
    }

    private void analyzeFloats(float[] fArray, int n, int n2, Object object) {
        int n3 = Math.min(n2, fArray.length - n);
        if (n3 <= 0) {
            return;
        }
        this.beginWindow(object);
        int n4 = Math.min(n3, 2048);
        for (int i = 0; i < n4; ++i) {
            this.pushSample(fArray[n + i]);
        }
        this.report();
    }

    private void analyzeBuffer(ByteBuffer byteBuffer, int n, Object object) {
        if (n <= 0) {
            return;
        }
        ByteBuffer byteBuffer2 = byteBuffer.duplicate();
        int n2 = Math.min(n, byteBuffer2.remaining());
        if (n2 <= 0) {
            return;
        }
        int n3 = this.encodingOf(object);
        if (n3 != 2 && n3 != 4) {
            return;
        }
        this.beginWindow(object);
        int n4 = n3 == 2 ? 2 : 4;
        int n5 = n2 / n4;
        int n6 = Math.min(n5, 2048);
        for (int i = 0; i < n6; ++i) {
            this.pushSample(n3 == 2 ? (float)byteBuffer2.getShort() / 32768.0f : byteBuffer2.getFloat());
        }
        this.report();
    }

    private void beginWindow(Object object) {
        int n = this.rateOf(object);
        if (n == this.filterRate) {
            return;
        }
        this.filterRate = n;
        this.bassFilter.configure(120.0f, n);
        this.midFilter.configure(600.0f, n);
        this.envelopeAttack = (float)Math.exp(-1.0 / ((double)n * 0.006));
        this.envelopeRelease = (float)Math.exp(-1.0 / ((double)n * 0.045));
        this.bassEnvelope = 0.0f;
        this.midEnvelope = 0.0f;
    }

    private void pushSample(float f) {
        float f2 = f > 4.0f ? 4.0f : Math.max(f, -4.0f);
        float f3 = this.bassFilter.process(f2);
        float f4 = this.midFilter.process(f2);
        this.bassEnvelope = this.envelope(this.bassEnvelope, f3 * f3);
        this.midEnvelope = this.envelope(this.midEnvelope, f4 * f4);
    }

    private float envelope(float f, float f2) {
        float f3 = f2 > f ? this.envelopeAttack : this.envelopeRelease;
        return f * f3 + f2 * (1.0f - f3);
    }

    private void report() {
        float f;
        float f2 = AudioLevelSource.decibels(this.bassEnvelope);
        float f3 = AudioLevelSource.decibels(this.midEnvelope);
        float f4 = AudioLevelSource.smoothRange(f2, -50.0f, -18.0f);
        float f5 = AudioLevelSource.smoothRange(f2 - f3, 0.0f, 8.0f);
        float f6 = Math.max(0.0f, f2 - this.slowBassDb);
        float f7 = AudioLevelSource.smoothRange(f6, 1.2f, 7.0f);
        this.history[this.historyWrite] = f = f4 * f5 * (0.1f + 0.9f * f7);
        this.historyWrite = (this.historyWrite + 1) % this.history.length;
        this.historyCount = Math.min(this.historyCount + 1, this.history.length);
        float f8 = this.historyCount >= 3 ? this.medianOfThree() : (this.historyCount == 2 ? Math.min(this.history[0], this.history[1]) : f);
        this.slowBassDb = AudioLevelSource.smoothBaseline(this.slowBassDb, f2);
        this.slowMidDb = AudioLevelSource.smoothBaseline(this.slowMidDb, f3);
        this.peak = Math.max(f8, this.peak * 0.995f);
        this.peak = Math.max(this.peak, 0.35f);
        float f9 = AudioLevelSource.clamp(f8 / this.peak);
        f9 *= f9;
        float f10 = f9 > this.output ? 0.55f : 0.1f;
        this.output += (f9 - this.output) * f10;
        this.level = this.output;
        this.lastAudioAt = SystemClock.elapsedRealtime();
        this.sawAudio = true;
    }

    private float medianOfThree() {
        float f = this.history[0];
        float f2 = this.history[1];
        float f3 = this.history[2];
        return f + f2 + f3 - Math.min(f, Math.min(f2, f3)) - Math.max(f, Math.max(f2, f3));
    }

    private static float smoothBaseline(float f, float f2) {
        float f3 = 0.011627907f;
        float f4 = f2 > f ? 1.1f : 0.16f;
        float f5 = 1.0f - (float)Math.exp(-f3 / f4);
        return Math.max(-72.0f, f + (f2 - f) * f5);
    }

    private static float decibels(float f) {
        return 10.0f * (float)Math.log10(Math.max(f, 1.0E-12f));
    }

    private static float smoothRange(float f, float f2, float f3) {
        float f4 = Math.max(0.0f, Math.min(1.0f, (f - f2) / (f3 - f2)));
        return f4 * f4 * (3.0f - 2.0f * f4);
    }

    @Override
    public float value(float f) {
        if (!this.enabled) {
            return 0.0f;
        }
        long l = SystemClock.elapsedRealtime();
        long l2 = l - this.externalAt;
        if (this.externalReports > 0L && l2 <= 1800L) {
            if (l2 < 80L) {
                return this.externalLevel;
            }
            return this.externalLevel * (float)Math.exp((float)(-(l2 - 80L)) / 240.0f);
        }
        long l3 = l - this.lastAudioAt;
        if (this.sawAudio && l3 <= 6000L) {
            if (l3 < 40L) {
                return this.level;
            }
            return this.level * (float)Math.exp((float)(-(l3 - 40L)) / 300.0f);
        }
        if (!this.metronomeEnabled) {
            return 0.0f;
        }
        double d = (double)(f * this.bpm) / 60.0;
        float f2 = (float)(d - Math.floor(d));
        float f3 = 1.0f - f2;
        return f3 * f3 * f3;
    }

    public boolean usingRealAudio() {
        long l = SystemClock.elapsedRealtime();
        return this.externalReports > 0L && l - this.externalAt <= 6000L || this.sawAudio && l - this.lastAudioAt <= 6000L;
    }

    public boolean usingPlaybackCapture() {
        return this.externalReports > 0L && SystemClock.elapsedRealtime() - this.externalAt <= 6000L;
    }

    public long externalReportCount() {
        return this.externalReports;
    }

    public int externalPulsePercent() {
        return Math.round(this.externalLevel * 100.0f);
    }

    public int bridgeState() {
        return this.bridgeState;
    }

    public void setBridgeState(int n) {
        this.bridgeState = n;
    }

    public void acceptExternalPulse(float f) {
        this.externalLevel = AudioLevelSource.clamp(f);
        this.externalAt = SystemClock.elapsedRealtime();
        ++this.externalReports;
    }

    public String openCaptureHelper() {
        this.playbackBridge.bind();
        return this.playbackBridge.openHelper();
    }

    public String captureBridgeStatus() {
        if (Build.VERSION.SDK_INT < 29) {
            return "系统内录需要 Android 10 或更高版本";
        }
        this.playbackBridge.bind();
        if (this.bridgeState == 3) {
            return "应用和插件已连接，但系统返回静音；确认 Apple Music 正在播放，源应用策略或 DRM 可能禁止共享";
        }
        if (this.bridgeState == -4) {
            return "设备拦截了 Visualizer 输出混音；请在应用中同意备用系统播放捕获授权";
        }
        if (this.bridgeState == 2 && !this.usingPlaybackCapture()) {
            return "应用已启动，插件连接正常，但还未收到音频数据";
        }
        if (this.usingPlaybackCapture()) {
            return "已收到系统音频；低频脉冲=" + Math.round(this.externalLevel * 100.0f) + "%，脉冲更新=" + this.externalReports;
        }
        if (this.bridgeState == 2) {
            return "系统内录正在传送音频；低频脉冲=" + Math.round(this.externalLevel * 100.0f) + "%";
        }
        if (this.bridgeState == -3) {
            return "正在连接音频捕获；再检查一次状态";
        }
        if (this.bridgeState == 1) {
            return "应用已连接插件；请在应用里启动内录并同意系统共享";
        }
        if (this.bridgeState == -2) {
            return "应用已启动，但 AM++ 插件进程身份校验失败；插件需要运行在 Apple Music 进程中";
        }
        if (this.bridgeState < 0) {
            return "未连接音频捕获；请打开AM背景插件并启动音频分析，插件会自动连接本机音频通道";
        }
        return "音频捕获尚未连接；从插件设置打开捕获，再启动系统内录授权";
    }

    public long writeCount() {
        return this.writeCalls.get();
    }

    public int hooksRegistered() {
        return this.hooksRegistered;
    }

    public int hooksFailed() {
        return this.hooksFailed;
    }

    public String firstHookError() {
        return this.firstHookError;
    }

    public void setBpm(float f) {
        this.bpm = Math.max(40.0f, Math.min(220.0f, Float.isNaN(f) ? 100.0f : f));
    }

    public void setMetronomeEnabled(boolean bl) {
        this.metronomeEnabled = bl;
    }

    public void setEnabled(boolean bl) {
        this.enabled = bl;
        if (!bl) {
            this.level = 0.0f;
        }
    }

    public synchronized void uninstall() {
        for (PluginRegistration pluginRegistration : this.registrations) {
            try {
                pluginRegistration.close();
            }
            catch (Throwable throwable) {}
        }
        this.registrations.clear();
        this.playbackBridge.unbind();
        this.installed = false;
        this.level = 0.0f;
        this.externalLevel = 0.0f;
        this.externalAt = 0L;
    }

    private int encodingOf(Object object) {
        Integer n = this.encodings.get(object);
        if (n != null) {
            return n;
        }
        int n2 = 2;
        try {
            n2 = ((AudioTrack)object).getAudioFormat();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        this.encodings.put(object, n2);
        return n2;
    }

    private int rateOf(Object object) {
        Integer n = this.sampleRates.get(object);
        if (n != null) {
            return n;
        }
        int n2 = 48000;
        try {
            n2 = ((AudioTrack)object).getSampleRate();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (n2 <= 0) {
            n2 = 48000;
        }
        this.sampleRates.put(object, n2);
        return n2;
    }

    private static float clamp(float f) {
        if (Float.isNaN(f)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, f));
    }

    private static final class Biquad {
        private float b0;
        private float b1;
        private float b2;
        private float a1;
        private float a2;
        private float z1;
        private float z2;

        private Biquad() {
        }

        void configure(float f, int n) {
            float f2 = (float)n * 0.49f;
            float f3 = (float)(Math.PI * 2 * (double)Math.min(f, f2) / (double)n);
            float f4 = (float)Math.sin(f3);
            float f5 = (float)Math.cos(f3);
            float f6 = f4 / 1.4142135f;
            float f7 = 1.0f / (1.0f + f6);
            this.b0 = (1.0f - f5) * 0.5f * f7;
            this.b1 = (1.0f - f5) * f7;
            this.b2 = this.b0;
            this.a1 = -2.0f * f5 * f7;
            this.a2 = (1.0f - f6) * f7;
            this.z1 = 0.0f;
            this.z2 = 0.0f;
        }

        float process(float f) {
            float f2 = this.b0 * f + this.z1;
            this.z1 = this.b1 * f - this.a1 * f2 + this.z2;
            this.z2 = this.b2 * f - this.a2 * f2;
            return Float.isFinite(f2) ? f2 : 0.0f;
        }
    }

    private final class Tap
    implements PluginHook {
        private final int kind;
        private final int arity;

        Tap(int n, int n2) {
            this.kind = n;
            this.arity = n2;
        }

        public void before(PluginCall pluginCall) {
            if (!AudioLevelSource.this.enabled) {
                return;
            }
            AudioLevelSource.this.count();
            Object[] objectArray = pluginCall.getArguments();
            try {
                int n;
                Object object = objectArray[0];
                int n2 = 0;
                if (this.kind == 5) {
                    if (!(object instanceof ByteBuffer)) {
                        return;
                    }
                    int n3 = (Integer)objectArray[1];
                    AudioLevelSource.this.count();
                    AudioLevelSource.this.analyzeBuffer((ByteBuffer)object, n3, pluginCall.getReceiver());
                    return;
                }
                if (this.kind == 0 || this.kind == 2) {
                    n2 = (Integer)objectArray[1];
                    n = (Integer)objectArray[2];
                } else {
                    n2 = (Integer)objectArray[1];
                    n = (Integer)objectArray[2];
                }
                if (this.kind <= 1) {
                    if (!(object instanceof byte[])) {
                        return;
                    }
                    if (AudioLevelSource.this.encodingOf(pluginCall.getReceiver()) != 2) {
                        return;
                    }
                    AudioLevelSource.this.count();
                    AudioLevelSource.this.analyzeBytes((byte[])object, n2, n, pluginCall.getReceiver());
                } else if (this.kind <= 3) {
                    if (!(object instanceof short[])) {
                        return;
                    }
                    AudioLevelSource.this.analyzeShorts((short[])object, n2, n, pluginCall.getReceiver());
                } else {
                    if (!(object instanceof float[])) {
                        return;
                    }
                    if (AudioLevelSource.this.encodingOf(pluginCall.getReceiver()) != 4) {
                        return;
                    }
                    AudioLevelSource.this.count();
                    AudioLevelSource.this.analyzeFloats((float[])object, n2, n, pluginCall.getReceiver());
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }
}

