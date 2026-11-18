package dev.zen.lyricbg.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.audiofx.Visualizer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.PowerManager;
import android.content.pm.PackageManager;


public final class AudioCaptureService extends Service {
    static final String ACTION_START = "dev.zen.lyricbg.capture.START";
    static final String ACTION_STOP = "dev.zen.lyricbg.capture.STOP";
    static final String ACTION_START_VISUALIZER = "dev.zen.lyricbg.capture.START_VISUALIZER";
    static final String ACTION_START_PROJECTION = "dev.zen.lyricbg.capture.START_PROJECTION";
    static final String EXTRA_RESULT = "projection_result";
    static final String EXTRA_UID = "target_uid";
    static final String EXTRA_DATA = "projection_data";
    private static final int NOTIFICATION_ID = 4101;
    private static final String CHANNEL = "am_bg_pro_max_capture";
    private static final String PREFS = "capture_service";
    private static final String PREF_VISUALIZER_RESTART = "visualizer_restart";
    private static final String PREF_VISUALIZER_CALLBACK_SEEN = "visualizer_callback_seen";
    static final int STATE_IDLE = 0;
    static final int STATE_STARTING = 4;
    static final int STATE_RUNNING = 2;
    static final int STATE_SILENT = 3;
    static final int STATE_VISUALIZER_FAILED = -4;
    static final int STATE_WAITING_FOR_AUDIO = 5;
    private static final int STATE_ERROR = -1;
    private static final int SAMPLE_RATE = 48000;

    private static volatile AudioCaptureService liveService;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile AudioRecord recorder;
    private volatile Visualizer visualizer;
    private volatile long lastSignalAt;
    private volatile boolean visualizerCallbackReceived;
    private volatile long visualizerFftCallbacks;
    private volatile float latestVisualizerPulse;
    private volatile MediaProjection projection;
    private PowerManager.WakeLock captureWakeLock;
    private Thread reader;
    private int targetUid = -1;
    private volatile int state = STATE_IDLE;
    private long frames;
    private final Runnable visualizerStartWatchdog = () -> {
        if (visualizer != null && running && !visualizerCallbackReceived) {
            setState(STATE_WAITING_FOR_AUDIO);
            updateNotification("Visualizer 已启动，等待 Apple Music 播放音频");
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        liveService = this;
        createChannel();
        LocalAudioSocketHub.start();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            if (!shouldRestartVisualizer()) return START_NOT_STICKY;
            try {
                setState(STATE_STARTING);
                startVisualizerForeground();
                startVisualizer();
                return START_STICKY;
            } catch (Throwable error) {
                setVisualizerRestart(false);
                setState(STATE_VISUALIZER_FAILED);
                stopCapture();
                stopForegroundCompat();
                stopSelf(startId);
                return START_NOT_STICKY;
            }
        }
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            setVisualizerRestart(false);
            stopCapture();
            setState(STATE_IDLE);
            stopForegroundCompat();
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_START_VISUALIZER.equals(action)) {
            setVisualizerRestart(true);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_VISUALIZER_CALLBACK_SEEN, false).apply();
            stopCapture();
            setState(STATE_STARTING);
            try {
                startVisualizerForeground();
                startVisualizer();
            } catch (Throwable error) {
                setState(STATE_VISUALIZER_FAILED);
                setVisualizerRestart(false);
                stopCapture();
                updateNotification("Visualizer 被设备拦截；应用将请求备用系统授权");
                stopForegroundCompat();
                stopSelf(startId);
            }
            return START_STICKY;
        }
        if (!ACTION_START_PROJECTION.equals(action) && !ACTION_START.equals(action)) return START_NOT_STICKY;
        setVisualizerRestart(false);
        int resultCode = intent.getIntExtra(EXTRA_RESULT, 0);
        targetUid = intent.getIntExtra(EXTRA_UID, -1);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        if (resultCode == 0 || data == null || targetUid < 0) {
            setState(STATE_ERROR);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        try {
            Notification notification = buildNotification("等待 Apple Music 音频…");
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            startCapture(resultCode, data);
        } catch (Throwable error) {
            setState(STATE_ERROR);
            stopCapture();
            updateNotification("系统播放捕获启动失败，请重试授权");
            stopForegroundCompat();
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    private boolean shouldRestartVisualizer() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_VISUALIZER_RESTART, false);
    }

    static boolean visualizerCallbackSeen(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_VISUALIZER_CALLBACK_SEEN, false);
    }

    private void setVisualizerRestart(boolean enabled) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_VISUALIZER_RESTART, enabled).commit();
    }

    private void acquireCaptureWakeLock() {
        try {
            if (captureWakeLock != null && captureWakeLock.isHeld()) return;
            PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
            if (power == null) return;
            captureWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AMBackgroundProMax:Capture");
            captureWakeLock.setReferenceCounted(false);
            captureWakeLock.acquire();
        } catch (Throwable ignored) {}
    }

    private void releaseCaptureWakeLock() {
        try {
            if (captureWakeLock != null && captureWakeLock.isHeld()) captureWakeLock.release();
        } catch (Throwable ignored) {}
        captureWakeLock = null;
    }

    private void startVisualizerForeground() {
        Notification notification = buildNotification("尝试读取系统输出混音");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
    }
    private void startVisualizer() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("RECORD_AUDIO permission missing");
        }
        // Keep the CPU awake for the entire user-started monitoring session,
        // including silent periods before the next track begins.
        acquireCaptureWakeLock();
        int[] range = Visualizer.getCaptureSizeRange();
        if (range == null || range.length < 2 || range[1] < range[0]) throw new IllegalStateException("Visualizer capture size unavailable");
        Visualizer candidate = new Visualizer(0);
        try {
            int captureSize = range[1];
            candidate.setCaptureSize(captureSize);
            candidate.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED);
            SpectrumDetector detector = new SpectrumDetector(captureSize, candidate.getSamplingRate() / 1000f);
            int result = candidate.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer effect, byte[] waveform, int samplingRate) {}
                @Override public void onFftDataCapture(Visualizer effect, byte[] fft, int samplingRate) {
                    if (!running || fft == null) return;
                    try {
                        float pulse = detector.process(fft, samplingRate / 1000f);
                        visualizerFftCallbacks++;
                        latestVisualizerPulse = pulse;
                        sendPulse(pulse);
                        long now = android.os.SystemClock.elapsedRealtime();
                        if (!visualizerCallbackReceived) {
                            visualizerCallbackReceived = true;
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putBoolean(PREF_VISUALIZER_CALLBACK_SEEN, true).apply();
                        }
                        boolean active = detector.signalActive() || pulse >= 0.02f;
                        if (active) {
                            acquireCaptureWakeLock();
                            lastSignalAt = now;
                            if (state == STATE_SILENT || state == STATE_STARTING || state == STATE_WAITING_FOR_AUDIO) {
                                setState(STATE_RUNNING);
                                updateNotification("Visualizer 正在分析系统混音低频");
                            }
                        } else if (state == STATE_STARTING || state == STATE_WAITING_FOR_AUDIO) {
                            setState(STATE_SILENT);
                            updateNotification("Visualizer 已收到回调，当前混音静音");
                        } else if (state == STATE_RUNNING && now - lastSignalAt > 2800L) {
                            setState(STATE_SILENT);
                            updateNotification("Visualizer 已启用，当前系统混音静音");
                        }
                    } catch (Throwable ignored) {
                        // Keep effect callbacks isolated from audio playback.
                    }
                }
            }, Visualizer.getMaxCaptureRate(), false, true);
            if (result != Visualizer.SUCCESS) throw new IllegalStateException("Visualizer listener rejected: " + result);
            visualizer = candidate;
            running = true;
            visualizerCallbackReceived = false;
            lastSignalAt = android.os.SystemClock.elapsedRealtime();
            result = candidate.setEnabled(true);
            if (result != Visualizer.SUCCESS) throw new IllegalStateException("Visualizer enable failed: " + result);
            updateNotification("Visualizer 已启用，等待首个音频回调");
            main.postDelayed(visualizerStartWatchdog, 3000L);
        } catch (Throwable error) {
            try { candidate.setDataCaptureListener(null, 0, false, false); } catch (Throwable ignored) {}
            try { candidate.setEnabled(false); } catch (Throwable ignored) {}
            try { candidate.release(); } catch (Throwable ignored) {}
            throw error;
        }
    }

    static String debugStatus() {
        AudioCaptureService service = liveService;
        if (service == null) return LocalAudioSocketHub.diagnostics();
        return "FFT callbacks=" + service.visualizerFftCallbacks + ", latest pulse=" + service.latestVisualizerPulse
                + "; " + LocalAudioSocketHub.diagnostics();
    }
    private boolean isMusicActive() {
        try {
            android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            return audio != null && audio.isMusicActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void failVisualizerForSilentMix() {
        if (visualizer == null || !running || state == STATE_VISUALIZER_FAILED) return;
        setVisualizerRestart(false);
        setState(STATE_VISUALIZER_FAILED);
        updateNotification("系统正在播放，但 Visualizer 混音静音；改用备用系统授权");
        stopCapture();
        stopForegroundCompat();
        stopSelf();
    }

    private void startCapture(int resultCode, Intent data) {
        stopCapture();
        if (Build.VERSION.SDK_INT < 29) throw new IllegalStateException("Android 10+ required");
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("RECORD_AUDIO permission missing");
        }
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) throw new IllegalStateException("MediaProjection unavailable");
        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) throw new SecurityException("MediaProjection token invalid");
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                stopCapture();
                setState(STATE_IDLE);
                if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
                else stopForeground(true);
                stopSelf();
            }
        }, main);
        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUid(targetUid)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
        int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) minimum = 8192;
        int bytes = Math.max(minimum * 2, 8192);
        recorder = new AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bytes)
                .setAudioPlaybackCaptureConfig(config)
                .build();
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release(); recorder = null;
            throw new IllegalStateException("AudioRecord initialization failed");
        }
        recorder.startRecording();
        acquireCaptureWakeLock();
        running = true;
        frames = 0;
        setState(STATE_RUNNING);
        updateNotification("正在分析 Apple Music 低频");
        reader = new Thread(this::readLoop, "am-bg-pro-max-capture");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        short[] samples = new short[1024];
        Detector detector = new Detector(SAMPLE_RATE);
        long silentSince = android.os.SystemClock.elapsedRealtime();
        boolean silentState = false;
        while (running) {
            AudioRecord source = recorder;
            if (source == null) break;
            int count;
            try { count = source.read(samples, 0, samples.length, AudioRecord.READ_BLOCKING); }
            catch (Throwable error) { count = AudioRecord.ERROR_INVALID_OPERATION; }
            if (count < 0) {
                if (running) failCapture();
                return;
            }
            if (count == 0) continue;
            frames += count;
            float rms = detector.process(samples, count);
            float pulse = detector.pulse();
            sendPulse(pulse);
            long now = android.os.SystemClock.elapsedRealtime();
            if (rms > 0.000012f) {
                silentSince = now;
                if (silentState) { silentState = false; setState(STATE_RUNNING); updateNotification("正在分析 Apple Music 低频"); }
            } else if (!silentState && now - silentSince > 2800L) {
                silentState = true;
                setState(STATE_SILENT);
                updateNotification("已授权，但没有收到 Apple Music 音频");
            }
        }
    }

    private void failCapture() {
        running = false;
        setState(STATE_ERROR);
        updateNotification("捕获已中断，请重新授权");
        main.post(this::stopForegroundCompat);
        main.post(() -> stopSelf());
    }

    private void stopCapture() {
        running = false;
        Thread oldReader = reader;
        reader = null;
        AudioRecord oldRecorder = recorder;
        recorder = null;
        if (oldRecorder != null) {
            try { oldRecorder.stop(); } catch (Throwable ignored) {}
            try { oldRecorder.release(); } catch (Throwable ignored) {}
        }
        if (oldReader != null && oldReader != Thread.currentThread()) {
            try { oldReader.join(250L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        releaseCaptureWakeLock();
        Visualizer oldVisualizer = visualizer;
        visualizer = null;
        if (oldVisualizer != null) {
            try { oldVisualizer.setDataCaptureListener(null, 0, false, false); } catch (Throwable ignored) {}
            try { oldVisualizer.setEnabled(false); } catch (Throwable ignored) {}
            try { oldVisualizer.release(); } catch (Throwable ignored) {}
        }
        MediaProjection oldProjection = projection;
        projection = null;
        if (oldProjection != null) {
            try { oldProjection.stop(); } catch (Throwable ignored) {}
        }
    }

    private void sendPulse(float value) {
        LocalAudioSocketHub.publishPulse(value);
    }

    private void setState(int value) {
        state = value;
        AudioBridgeService.publishState(value);
    }

    static void requestStopFromBridge() {
        AudioCaptureService service = liveService;
        if (service != null) service.stopCaptureFromBridge();
    }

    private void stopCaptureFromBridge() {
        setVisualizerRestart(false);
        stopCapture();
        setState(STATE_IDLE);
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, CaptureActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, AudioCaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("音频捕获 for AM背景 Pro Max").setContentText(text)
                .setContentIntent(openPending).setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "停止", stopPending)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "音频捕获 for AM背景 Pro Max", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示系统播放音频捕获的状态和停止入口");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override public void onDestroy() {
        stopCapture();
        releaseCaptureWakeLock();
        if (state != STATE_VISUALIZER_FAILED) setState(STATE_IDLE);
        LocalAudioSocketHub.stop();
        stopForegroundCompat();
        if (liveService == this) liveService = null;
        super.onDestroy();
    }

    private static final class SpectrumDetector {
        private final int captureSize;
        private float baseBassDb = -72f, baseReferenceDb = -72f;
        private float previousBassDb = -72f, sharpAttack, output;
        private long lastFrameAt;
        private boolean active;
        private final float[] recent = new float[3];
        private int recentCount, recentIndex;
        SpectrumDetector(int captureSize, float rateMilliHz) {
            this.captureSize = captureSize;
        }
        float process(byte[] fft, float rateMilliHz) {
            float rateHz = rateMilliHz > 0f ? rateMilliHz : 48000f;
            float binHz = rateHz / captureSize;
            int half = captureSize / 2;
            double lowBassSum = 0d, bassNoteSum = 0d, upperBassSum = 0d, lowMidSum = 0d, midSum = 0d;
            int lowBassCount = 0, bassNoteCount = 0, upperBassCount = 0, lowMidCount = 0, midCount = 0;
            float maximum = 0f;
            for (int k = 1; k < half; k++) {
                int i = k * 2;
                float magnitude = (float)Math.hypot(fft[i], fft[i + 1]);
                maximum = Math.max(maximum, magnitude);
                float hz = k * binHz;
                float power = magnitude * magnitude;
                if (hz >= 30f && hz <= 105f) { lowBassSum += power; lowBassCount++; }
                if (hz >= 75f && hz <= 155f) { bassNoteSum += power; bassNoteCount++; }
                if (hz >= 145f && hz <= 210f) { upperBassSum += power; upperBassCount++; }
                if (hz >= 155f && hz <= 380f) { lowMidSum += power; lowMidCount++; }
                if (hz >= 380f && hz <= 760f) { midSum += power; midCount++; }
            }
            double lowBassPower = lowBassCount == 0 ? 0d : lowBassSum / lowBassCount;
            double bassNotePower = bassNoteCount == 0 ? 0d : bassNoteSum / bassNoteCount;
            double coreBassPower = Math.max(lowBassPower, bassNotePower * 0.9d);
            double upperBassPower = upperBassCount == 0 ? 0d : upperBassSum / upperBassCount;
            double bassPower = coreBassPower + Math.min(upperBassPower, coreBassPower * 1.35d) * 0.2d;
            double lowMidPower = lowMidCount == 0 ? 0d : lowMidSum / lowMidCount;
            double midPower = midCount == 0 ? 0d : midSum / midCount;
            double referencePower = Math.max(lowMidPower * 2.3d, midPower * 1.6d);
            float bassDb = powerDb(bassPower), referenceDb = powerDb(referencePower);
            if (recentCount == 0) {
                baseBassDb = Math.max(-72f, bassDb - 7f);
                baseReferenceDb = referenceDb;
                previousBassDb = bassDb;
            }
            float frameRise = Math.max(0f, bassDb - previousBassDb);
            previousBassDb = bassDb;
            float bassRise = Math.max(0f, bassDb - baseBassDb);
            float referenceRise = Math.max(0f, referenceDb - baseReferenceDb);
            float dominance = range(bassDb - referenceDb, 0f, 8f);
            float sharp = bassDb >= -45f ? range(frameRise, 7f, 14f) : 0f;
            sharpAttack = Math.max(sharp, sharpAttack * 0.80f);
            float harmonicConfidence = range(dominance, 0.12f, 0.30f) * sharpAttack * 0.9f;
            float bassConfidence = Math.max(dominance, harmonicConfidence);
            float bassOnlyRise = bassRise - referenceRise * (0.7f - dominance * 0.35f);
            baseBassDb += (bassDb - baseBassDb) * (bassDb > baseBassDb ? 0.022f : 0.15f);
            baseReferenceDb += (referenceDb - baseReferenceDb) * (referenceDb > baseReferenceDb ? 0.022f : 0.15f);
            float level = range(bassDb, -50f, -18f);
            float transientAmount = range(bassOnlyRise, 1.2f, 7f);
            float response = level * bassConfidence * (0.10f + 0.90f * transientAmount);
            recent[recentIndex] = response;
            recentIndex = (recentIndex + 1) % recent.length;
            recentCount = Math.min(recentCount + 1, recent.length);
            boolean immediate = (sharpAttack >= 0.72f && response >= 0.32f);
            float confirmed = immediate ? response : recentCount >= 2
                    ? (recentCount == 2 ? Math.min(recent[0], recent[1]) : median(recent[0], recent[1], recent[2]))
                    : 0f;
            float target = confirmed;
            long now = android.os.SystemClock.elapsedRealtime();
            float dt = lastFrameAt == 0L ? 0.02f : Math.max(0.005f, Math.min(0.10f, (now - lastFrameAt) / 1000f));
            lastFrameAt = now;
            float tau = target > output ? 0.04f : 0.18f;
            float blend = 1f - (float)Math.exp(-dt / tau);
            output += (target - output) * blend;
            active = maximum > 1.5f;
            return Math.max(0f, Math.min(1f, output));
        }
        boolean signalActive() { return active; }
        private static float powerDb(double power) { return 10f * (float)Math.log10(Math.max(1e-12d, power)); }
        private static float median(float a, float b, float c) { return a + b + c - Math.min(a, Math.min(b, c)) - Math.max(a, Math.max(b, c)); }
        private static float range(float value, float min, float max) {
            float x = Math.min(1f, Math.max(0f, (value - min) / (max - min)));
            return x * x * (3f - 2f * x);
        }
    }

    private static final class Detector {
        private final Biquad low30 = new Biquad(30f, SAMPLE_RATE);
        private final Biquad low75 = new Biquad(75f, SAMPLE_RATE);
        private final Biquad low105 = new Biquad(105f, SAMPLE_RATE);
        private final Biquad low145 = new Biquad(145f, SAMPLE_RATE);
        private final Biquad low155 = new Biquad(155f, SAMPLE_RATE);
        private final Biquad low210 = new Biquad(210f, SAMPLE_RATE);
        private final Biquad low380 = new Biquad(380f, SAMPLE_RATE);
        private final Biquad low760 = new Biquad(760f, SAMPLE_RATE);
        private float baseBassDb = -72f, baseReferenceDb = -72f, previousBassDb = -72f, sharpAttack, output;
        private long lastFrameAt;
        private final float[] recent = new float[3];
        private int recentCount, recentIndex;
        Detector(int rate) {}
        float process(short[] data, int count) {
            double squares = 0d, p30 = 0d, p75 = 0d, p105 = 0d, p145 = 0d, p155 = 0d, p210 = 0d, p380 = 0d, p760 = 0d;
            for (int i = 0; i < count; i++) {
                float x = data[i] / 32768f;
                float a = low30.process(x), b = low75.process(x), c = low105.process(x), d = low145.process(x), e = low155.process(x), f = low210.process(x), g = low380.process(x), h = low760.process(x);
                squares += x * x; p30 += a*a; p75 += b*b; p105 += c*c; p145 += d*d; p155 += e*e; p210 += f*f; p380 += g*g; p760 += h*h;
            }
            float rms = (float)Math.sqrt(squares / count);
            double lowBass = Math.max(0d, p105 - p30) / count;
            double bassNote = Math.max(0d, p155 - p75) / count;
            double coreBass = Math.max(lowBass, bassNote * 0.9d);
            double upperBass = Math.max(0d, p210 - p145) / count;
            double bassPower = coreBass + Math.min(upperBass, coreBass * 1.35d) * 0.2d;
            double lowMid = Math.max(0d, p380 - p155) / count;
            double mid = Math.max(0d, p760 - p380) / count;
            double referencePower = Math.max(lowMid * 2.3d, mid * 1.6d);
            float bassDb = db(bassPower), referenceDb = db(referencePower);
            if (recentCount == 0) { baseBassDb = Math.max(-72f, bassDb - 7f); baseReferenceDb = referenceDb; previousBassDb = bassDb; }
            float frameRise = Math.max(0f, bassDb - previousBassDb); previousBassDb = bassDb;
            float dominance = range(bassDb - referenceDb, 0f, 8f);
            float sharp = bassDb >= -45f ? range(frameRise, 7f, 14f) : 0f;
            sharpAttack = Math.max(sharp, sharpAttack * 0.80f);
            float bassConfidence = Math.max(dominance, range(dominance, 0.12f, 0.30f) * sharpAttack * 0.9f);
            float bassRise = Math.max(0f, bassDb - baseBassDb), referenceRise = Math.max(0f, referenceDb - baseReferenceDb);
            float bassOnlyRise = bassRise - referenceRise * (0.7f - dominance * 0.35f);
            baseBassDb += (bassDb - baseBassDb) * (bassDb > baseBassDb ? 0.022f : 0.15f);
            baseReferenceDb += (referenceDb - baseReferenceDb) * (referenceDb > baseReferenceDb ? 0.022f : 0.15f);
            float response = range(bassDb, -50f, -18f) * bassConfidence * (0.10f + 0.90f * range(bassOnlyRise, 1.2f, 7f));
            recent[recentIndex] = response; recentIndex = (recentIndex + 1) % recent.length; recentCount = Math.min(recentCount + 1, recent.length);
            float target = sharpAttack >= 0.72f && response >= 0.32f ? response : recentCount >= 2
                    ? (recentCount == 2 ? Math.min(recent[0], recent[1]) : median(recent[0], recent[1], recent[2])) : 0f;
            long now = android.os.SystemClock.elapsedRealtime();
            float dt = lastFrameAt == 0L ? 0.02f : Math.max(0.005f, Math.min(0.10f, (now - lastFrameAt) / 1000f)); lastFrameAt = now;
            float blend = 1f - (float)Math.exp(-dt / (target > output ? 0.04f : 0.18f));
            output += (target - output) * blend;
            return rms;
        }
        float pulse() { return Math.min(1f, Math.max(0f, output)); }
        private static float db(double power) { return 10f * (float)Math.log10(Math.max(1e-12d, power)); }
        private static float median(float a, float b, float c) { return a + b + c - Math.min(a, Math.min(b, c)) - Math.max(a, Math.max(b, c)); }
        private static float range(float value, float min, float max) {
            float x = Math.min(1f, Math.max(0f, (value - min) / (max - min)));
            return x * x * (3f - 2f * x);
        }
    }

    private static final class Biquad {
        private final float b0, b1, b2, a1, a2;
        private float z1, z2;
        Biquad(float cutoff, int rate) {
            float w = (float)(2d * Math.PI * cutoff / rate), s = (float)Math.sin(w), c = (float)Math.cos(w);
            float alpha = s / (2f * 0.70710678f), inv = 1f / (1f + alpha);
            b0 = (1f - c) * 0.5f * inv; b1 = (1f - c) * inv; b2 = b0;
            a1 = -2f * c * inv; a2 = (1f - alpha) * inv;
        }
        float process(float x) {
            float y = b0*x + z1; z1 = b1*x - a1*y + z2; z2 = b2*x - a2*y;
            return Float.isFinite(y) ? y : 0f;
        }
    }
}


