package dev.zen.lyricbg.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.app.NotificationManager;

import java.util.List;

public final class CaptureActivity extends Activity {
    private static final int REQUEST_AUDIO = 77;
    private static final int REQUEST_PROJECTION = 78;
    private static final int REQUEST_NOTIFICATIONS = 79;
    private static final int REQUEST_NOTIFICATION_ACCESS = 80;
    private static final String APPLE_MUSIC = "com.apple.android.music";
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private int appleMusicUid = -1;
    private boolean startupStarted;
    private boolean listenerSettingsOpened;
    private boolean projectionRequested;
    private boolean appleMusicLaunched;
    private long visualizerStartedAt;
    private boolean hasPlayingSession;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        try { appleMusicUid = getPackageManager().getApplicationInfo(APPLE_MUSIC, 0).uid; }
        catch (Throwable ignored) {}
        main.post(this::continueStartup);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_VERTICAL);
        status = new TextView(this);
        status.setTextSize(16f);
        status.setText("正在准备自动音频律动…");
        root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button listener = new Button(this);
        listener.setText("通知访问权限");
        listener.setOnClickListener(v -> openNotificationListenerSettings());
        root.addView(listener, new LinearLayout.LayoutParams(-1, -2));
        Button battery = new Button(this);
        battery.setText("后台运行设置（关闭电池优化）");
        battery.setOnClickListener(v -> openBackgroundRunSettings());
        root.addView(battery, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void continueStartup() {
        if (isFinishing() || startupStarted) return;
        if (Build.VERSION.SDK_INT < 29) {
            status.setText("音频捕获应用要求 Android 10 或更高版本。");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("需要录音权限来读取系统 Visualizer 音频；不会录制或保存声音。");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            status.setText("需要通知权限以显示并保持音频应用运行。");
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        if (!hasNotificationListenerAccess()) {
            status.setText("请在系统页面允许 音频捕获 for AM背景 Pro Max读取媒体通知，以判断 Apple Music 是否正在播放；返回后会自动继续。");
            if (!listenerSettingsOpened) openNotificationListenerSettings();
            return;
        }
        startupStarted = true;
        inspectPlaybackAndStart();
    }

    private boolean hasNotificationListenerAccess() {
        try {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            return nm != null && nm.isNotificationListenerAccessGranted(
                    new ComponentName(this, MusicNotificationListener.class));
        } catch (Throwable ignored) { return false; }
    }

    private void openNotificationListenerSettings() {
        listenerSettingsOpened = true;
        try { startActivityForResult(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), REQUEST_NOTIFICATION_ACCESS); }
        catch (Throwable error) { status.setText("无法打开通知访问设置，请在系统设置中手动允许 音频捕获 for AM背景 Pro Max读取通知。"); }
    }

    private void inspectPlaybackAndStart() {
        hasPlayingSession = isAppleMusicPlaying();
        AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        boolean muted = audio == null || audio.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0
                || (Build.VERSION.SDK_INT >= 23 && audio.isStreamMute(AudioManager.STREAM_MUSIC));
        startVisualizer();
        if (muted) {
            new AlertDialog.Builder(this)
                    .setTitle("媒体音量已静音")
                    .setMessage("当前媒体音量为 0，Visualizer 将收不到有效音频。请调高媒体音量后播放 Apple Music。")
                    .setPositiveButton("知道了", (dialog, which) -> {})
                    .show();
        }
        if (!hasPlayingSession) {
            status.setText("Visualizer 已自动启动。通知访问正常，但当前没有检测到 Apple Music 正在播放；即将打开 Apple Music。\n\n" + AudioCaptureService.debugStatus());
        }
        main.postDelayed(this::launchAppleMusic, 500L);
    }

    private boolean isAppleMusicPlaying() {
        try {
            MediaSessionManager manager = (MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
            if (manager == null) return false;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(this, MusicNotificationListener.class));
            for (MediaController controller : sessions) {
                if (!APPLE_MUSIC.equals(controller.getPackageName())) continue;
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) return true;
                MediaMetadata metadata = controller.getMetadata();
                if (metadata != null && state != null && state.getState() == PlaybackState.STATE_BUFFERING) return true;
            }
        } catch (SecurityException ignored) {
        } catch (Throwable ignored) {}
        return false;
    }

    private void launchAppleMusic() {
        if (appleMusicLaunched || isFinishing()) return;
        appleMusicLaunched = true;
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(APPLE_MUSIC);
            if (launch == null) {
                status.setText("Visualizer 已启动，但设备上未找到 Apple Music。\n\n" + AudioCaptureService.debugStatus());
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        } catch (Throwable error) {
            status.setText("Visualizer 已启动，但无法自动打开 Apple Music：" + error.getClass().getSimpleName());
        }
    }

    private void startVisualizer() {
        visualizerStartedAt = android.os.SystemClock.elapsedRealtime();
        status.setText("正在自动启动 Visualizer，并打开 Apple Music…");
        Intent service = new Intent(this, AudioCaptureService.class).setAction(AudioCaptureService.ACTION_START_VISUALIZER);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            pollVisualizer();
        } catch (Throwable error) {
            status.setText("Visualizer 服务启动失败：" + error.getClass().getSimpleName());
        }
    }

    private void pollVisualizer() {
        main.postDelayed(() -> {
            if (isFinishing()) return;
            int captureState = AudioBridgeService.currentCaptureState();
            boolean callbackSeen = AudioCaptureService.visualizerCallbackSeen(this);
            hasPlayingSession = isAppleMusicPlaying();
            if (callbackSeen) {
                String message = captureState == AudioCaptureService.STATE_RUNNING
                        ? "Visualizer 正在接收音频；已锁定 Visualizer 路径，不会请求屏幕共享。通知栏可停止。"
                        : "Visualizer 已收到过音频回调，当前等待有效低频或播放；不会再调用屏幕共享。";
                status.setText(message + "\n\n" + AudioCaptureService.debugStatus());
                pollVisualizer();
                return;
            }
            if (captureState == AudioCaptureService.STATE_RUNNING) {
                status.setText("Visualizer 正在运行，等待首个音频回调；不会读取屏幕。\n\n" + AudioCaptureService.debugStatus());
                pollVisualizer();
                return;
            }
            long elapsed = android.os.SystemClock.elapsedRealtime() - visualizerStartedAt;
            if (elapsed > 12000L && hasPlayingSession && !projectionRequested) {
                status.setText("检测到 Apple Music 正在播放，但 Visualizer 尚未收到音频。尝试系统音频捕获授权。\n\n" + AudioCaptureService.debugStatus());
                requestProjectionConsent();
                return;
            }
            String wait = hasPlayingSession ? "已检测到 Apple Music 播放，等待 Visualizer 音频回调…"
                    : "等待 Apple Music 开始播放；Visualizer 已保持运行，不会因暂时无音频而弹出屏幕共享。";
            status.setText(wait + "\n\n" + AudioCaptureService.debugStatus());
            pollVisualizer();
        }, 700L);
    }

    private void requestProjectionConsent() {
        if (AudioCaptureService.visualizerCallbackSeen(this) || projectionRequested) return;
        if (appleMusicUid < 0) {
            status.setText("Visualizer 尚未收到音频，且未找到 Apple Music，无法启动备用音频捕获。");
            return;
        }
        MediaProjectionManager manager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            status.setText("系统没有提供备用音频捕获服务；Visualizer 仍在等待音频。");
            return;
        }
        projectionRequested = true;
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_PROJECTION);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQUEST_AUDIO || request == REQUEST_NOTIFICATIONS) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) { startupStarted = false; continueStartup(); }
            else status.setText("未授予必要权限，应用无法自动启动 Visualizer。");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_NOTIFICATION_ACCESS) {
            if (hasNotificationListenerAccess()) { startupStarted = false; continueStartup(); }
            else status.setText("通知访问尚未开启。开启后重新打开应用即可自动继续。\n\n" + AudioCaptureService.debugStatus());
            return;
        }
        if (request != REQUEST_PROJECTION) return;
        if (AudioCaptureService.visualizerCallbackSeen(this)) {
            status.setText("Visualizer 已收到音频，继续使用 Visualizer，不启动屏幕共享。\n\n" + AudioCaptureService.debugStatus());
            return;
        }
        if (result != RESULT_OK || data == null) {
            status.setText("未授予备用音频捕获；Visualizer 继续等待音频回调。\n\n" + AudioCaptureService.debugStatus());
            return;
        }
        Intent service = new Intent(this, AudioCaptureService.class)
                .setAction(AudioCaptureService.ACTION_START_PROJECTION)
                .putExtra(AudioCaptureService.EXTRA_RESULT, result)
                .putExtra(AudioCaptureService.EXTRA_UID, appleMusicUid)
                .putExtra(AudioCaptureService.EXTRA_DATA, data);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            status.setText("备用系统音频捕获已启动。\n\n" + AudioCaptureService.debugStatus());
        } catch (Throwable error) { status.setText("备用音频捕获启动失败：" + error.getClass().getSimpleName()); }
    }

    private void openBackgroundRunSettings() {
        if (Build.VERSION.SDK_INT < 23) { status.setText("此 Android 版本没有单独的电池优化开关。"); return; }
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        if (power != null && power.isIgnoringBatteryOptimizations(getPackageName())) {
            status.setText("应用已不受电池优化限制。若后台仍被停止，请在系统自启动/后台管理中允许 音频捕获 for AM背景 Pro Max。");
            return;
        }
        try {
            Intent request = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", Uri.parse("package:" + getPackageName()));
            startActivity(request);
        } catch (Throwable unavailable) {
            try { startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")); }
            catch (Throwable ignored) { status.setText("请在系统设置中将 音频捕获 for AM背景 Pro Max设为不受限制。"); }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (status == null) return;
        if (!startupStarted) main.post(this::continueStartup);
        else if (AudioBridgeService.currentCaptureState() != AudioCaptureService.STATE_IDLE) {
            status.setText("音频应用正在运行。\n\n" + AudioCaptureService.debugStatus());
        }
    }
}
