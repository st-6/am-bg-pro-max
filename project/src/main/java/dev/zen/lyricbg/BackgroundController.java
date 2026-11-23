/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.Activity
 *  android.app.Application$ActivityLifecycleCallbacks
 *  android.content.Context
 *  android.graphics.Bitmap
 *  android.graphics.Canvas
 *  android.graphics.drawable.BitmapDrawable
 *  android.graphics.drawable.Drawable
 *  android.os.Bundle
 *  android.os.Handler
 *  android.os.Looper
 *  android.view.View
 *  android.view.View$OnAttachStateChangeListener
 *  android.view.ViewGroup
 *  android.view.ViewGroup$LayoutParams
 *  android.view.ViewGroup$MarginLayoutParams
 *  android.view.ViewParent
 *  android.widget.ImageView
 *  android.widget.Toast
 *  dev.amenhancer.plugin.api.PluginCall
 *  dev.amenhancer.plugin.api.PluginContext
 *  dev.amenhancer.plugin.api.PluginHook
 *  dev.amenhancer.plugin.api.PluginRegistration
 *  dev.amenhancer.plugin.api.PluginUnsupportedException
 */
package dev.zen.lyricbg;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;
import dev.amenhancer.plugin.api.PluginCall;
import dev.amenhancer.plugin.api.PluginContext;
import dev.amenhancer.plugin.api.PluginHook;
import dev.amenhancer.plugin.api.PluginRegistration;
import dev.amenhancer.plugin.api.PluginUnsupportedException;
import dev.zen.lyricbg.SettingsStore;
import dev.zen.lyricbg.audio.AudioLevelSource;
import dev.zen.lyricbg.render.ArtworkProcessor;
import dev.zen.lyricbg.render.MeshBackgroundView;
import dev.zen.lyricbg.render.Palette;
import java.lang.reflect.Executable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BackgroundController {
    private static final String LAYER_CLASS = "com.apple.android.music.player.LyricsBackgroundLayerView";
    private static final String TAG = "ampp-fluid-background";
    private static final long[] VERIFIED_VERSION_CODES = new long[]{1606L, 1607L, 1609L, 1613L, 1614L, 1622L, 1624L, 1626L};
    private final PluginContext context;
    private final SettingsStore settings;
    private final AudioLevelSource audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ampp-fluid-palette");
        thread.setPriority(1);
        thread.setDaemon(true);
        return thread;
    });
    private final Map<View, Session> sessions = new WeakHashMap<View, Session>();
    private final Map<View, Boolean> waiting = new WeakHashMap<View, Boolean>();
    private final Map<View, Boolean> layoutChecks = new WeakHashMap<View, Boolean>();
    private final List<PluginRegistration> registrations = new ArrayList<PluginRegistration>();
    private Class<?> layerClass;
    private Class<?> lyricsFragmentClass;
    private volatile boolean lyricsPageActive;
    private WeakReference<View> lyricsAnchor = new WeakReference<View>(null);
    private boolean prepared;
    private int lastPresetMask = -1;
    private Bitmap lastArtwork;
    private Bitmap lastRawArtwork;
    private boolean appliedCleanEnabled;
    private float appliedCleanStrength = -1.0f;
    private int attachAttempts;
    private int attachFailures;
    private boolean audioCheckScheduled;
    private static boolean noAudioToasted;
    private boolean lifecycleRegistered;
    private final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks(){

        public void onActivityResumed(Activity activity) {
            BackgroundController.this.runOnMain(() -> BackgroundController.this.scanForLayer(activity));
        }

        public void onActivityCreated(Activity activity, Bundle bundle) {
        }

        public void onActivityStarted(Activity activity) {
        }

        public void onActivityPaused(Activity activity) {
        }

        public void onActivityStopped(Activity activity) {
        }

        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        }

        public void onActivityDestroyed(Activity activity) {
        }
    };
    private final PluginHook artworkHook = new PluginHook(){

        public void before(PluginCall pluginCall) {
            Object object = pluginCall.getReceiver();
            if (!(object instanceof View)) {
                return;
            }
            View view = (View)object;
            Object[] objectArray = pluginCall.getArguments();
            Bitmap bitmap = objectArray.length > 0 && objectArray[0] instanceof Bitmap ? (Bitmap)objectArray[0] : null;
            Bitmap bitmap2 = null;
            if (bitmap != null && !bitmap.isRecycled()) {
                try {
                    bitmap2 = ArtworkProcessor.capture(bitmap);
                }
                catch (Throwable throwable) {
                    BackgroundController.this.reportOnce("artwork", throwable);
                }
            }
            if (bitmap2 == null) {
                BackgroundController.this.reportOnce("artwork-null", new IllegalStateException("封面无法处理，回退网格"));
            }
            Bitmap bitmap3 = bitmap2;
            BackgroundController.this.main.post(() -> BackgroundController.this.onArtwork(view, bitmap3));
        }
    };
    private final Set<String> loggedOnce = new HashSet<String>();
    private final Set<String> reportedFailures = new HashSet<String>();

    BackgroundController(PluginContext pluginContext, SettingsStore settingsStore) {
        this.context = pluginContext;
        this.settings = settingsStore;
        this.audio = new AudioLevelSource(pluginContext);
    }

    void prepare() throws PluginUnsupportedException {
        Method method;
        ClassLoader classLoader = this.context.getHostClassLoader();
        try {
            this.layerClass = Class.forName(LAYER_CLASS, false, classLoader);
        }
        catch (Throwable throwable) {
            throw new PluginUnsupportedException("当前 Apple Music 没有 com.apple.android.music.player.LyricsBackgroundLayerView，本插件仅支持 7.0.0 系列");
        }
        if (!View.class.isAssignableFrom(this.layerClass)) {
            throw new PluginUnsupportedException("com.apple.android.music.player.LyricsBackgroundLayerView 不是 View，宿主结构已变化");
        }
        try {
            method = this.layerClass.getDeclaredMethod("setArtwork", Bitmap.class);
        }
        catch (Throwable throwable) {
            throw new PluginUnsupportedException("背景层缺少 setArtwork(Bitmap) 接缝");
        }
        this.registrations.add(this.context.getHooks().hook((Executable)method, false, this.artworkHook));

        // The host paints the background layer before it ever calls setArtwork,
        // so waiting for setArtwork leaves Apple's own background on screen for
        // the first frames. Constructors give us each instance at creation.
        int constructorHooks = 0;
        for (java.lang.reflect.Constructor<?> constructor : this.layerClass.getDeclaredConstructors()) {
            try {
                this.registrations.add(this.context.getHooks().hook(constructor, false, this.constructorHook));
                ++constructorHooks;
            }
            catch (Throwable unavailable) {
                // A rejected constructor does not stop the others.
            }
        }
        if (constructorHooks == 0) {
            this.log("无法监听背景层构造，接管仍依赖 setArtwork");
        }

        // Geometry alone cannot tell the lyrics page from the full player page:
        // both are full-size. The lyrics fragment's lifecycle is the real signal.
        try {
            this.lyricsFragmentClass = Class.forName(
                    "com.apple.android.music.player.fragment.PlayerLyricsViewFragment", false, classLoader);
            Method resume = this.findLifecycleMethod(this.lyricsFragmentClass, "onResume");
            Method pause = this.findLifecycleMethod(this.lyricsFragmentClass, "onPause");
            if (resume != null) {
                this.registrations.add(this.context.getHooks().hook((Executable)resume, false, this.lyricsResumeHook));
            }
            if (pause != null) {
                this.registrations.add(this.context.getHooks().hook((Executable)pause, false, this.lyricsPauseHook));
            }
            this.log("歌词页开关: onResume=" + (resume != null) + " onPause=" + (pause != null));
        }
        catch (Throwable missing) {
            this.lyricsFragmentClass = null;
            this.log("未定位到歌词 Fragment，背景回退为几何判断");
        }
        this.suppressNativeDrawing();
        if (this.settings.audioTap()) {
            this.audio.install();
        }
        this.prepared = true;
        this.log("已接入 " + this.context.getHostVersionName() + " (" + this.context.getHostVersionCode() + ")，验证范围 " + this.isVerifiedBuild() + "，图层 " + this.layerClass.getSimpleName());
    }

    private final PluginHook lyricsResumeHook = new PluginHook(){

        public void before(PluginCall pluginCall) {
            Object receiver = pluginCall.getReceiver();
            if (receiver == null || BackgroundController.this.lyricsFragmentClass == null
                    || !BackgroundController.this.lyricsFragmentClass.isInstance(receiver)) {
                return;
            }
            BackgroundController.this.lyricsPageActive = true;
            try {
                Object view = receiver.getClass().getMethod("getView").invoke(receiver);
                if (view instanceof View) {
                    BackgroundController.this.lyricsAnchor = new WeakReference<View>((View)view);
                }
            }
            catch (Throwable ignored) {
                // The drawer anchor is optional; the layer ratio is the fallback.
            }
            BackgroundController.this.main.post(() -> BackgroundController.this.attachFromLyricsFragment(receiver));
        }
    };

    private final PluginHook lyricsPauseHook = new PluginHook(){

        public void before(PluginCall pluginCall) {
            Object receiver = pluginCall.getReceiver();
            if (receiver == null || BackgroundController.this.lyricsFragmentClass == null
                    || !BackgroundController.this.lyricsFragmentClass.isInstance(receiver)) {
                return;
            }
            BackgroundController.this.lyricsPageActive = false;
            BackgroundController.this.main.post(BackgroundController.this::removeAllSessions);
        }
    };

    private Method findLifecycleMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name);
            }
            catch (Throwable ignored) {
                // Keep walking up; the host may declare it in a base fragment.
            }
        }
        return null;
    }

    private boolean isLyricsPageActive() {
        return this.lyricsFragmentClass == null || this.lyricsPageActive;
    }

    private void attachFromLyricsFragment(Object fragment) {
        try {
            List<View> found = new ArrayList<View>();
            Object view = fragment.getClass().getMethod("getView").invoke(fragment);
            if (view instanceof View) {
                this.collectLayers((View)view, found, 0);
            }
            // The background layer usually lives in the player chrome rather
            // than inside the lyrics fragment, so fall back to the whole window.
            if (found.isEmpty()) {
                Object activity = fragment.getClass().getMethod("getActivity").invoke(fragment);
                if (activity instanceof Activity) {
                    View decor = ((Activity)activity).getWindow().getDecorView();
                    this.collectLayers(decor, found, 0);
                }
            }
            for (int i = 0; i < found.size(); ++i) {
                View layer = found.get(i);
                this.onArtwork(layer, this.artworkFromHostNow(layer));
            }
        }
        catch (Throwable throwable) {
            this.reportOnce("lyrics-resume", throwable);
        }
    }

    private void removeAllSessions() {
        for (View view : new ArrayList<View>(this.sessions.keySet())) {
            this.removeSession(view);
        }
    }

    /**
     * How far the lyrics drawer is open, 0..1. The fragment view travels with
     * the sheet, so its top offset is the continuous signal; the background
     * layer itself stays full-size and cannot express the drawer position.
     */
    private float revealAlpha(View fallbackAnchor) {
        View anchor = this.lyricsAnchor.get();
        if (anchor == null || !anchor.isAttachedToWindow()) {
            // With a known lyrics fragment, a missing anchor means the drawer is
            // gone, so the background must be fully transparent. Only when the
            // fragment could not be resolved do we keep the old always-on path.
            if (this.lyricsFragmentClass != null) {
                return 0f;
            }
            anchor = fallbackAnchor;
        }
        if (anchor == null || !anchor.isAttachedToWindow()) {
            return 0f;
        }
        View root = anchor.getRootView();
        if (root == null || root.getHeight() <= 0) {
            return 0f;
        }
        int[] anchorLocation = new int[2];
        int[] rootLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        root.getLocationOnScreen(rootLocation);
        float topFraction = (anchorLocation[1] - rootLocation[1]) / (float)root.getHeight();
        float progress = (0.34f - topFraction) / (0.34f - 0.14f);
        progress = Math.max(0f, Math.min(1f, progress));
        return progress * progress * (3f - 2f * progress);
    }

    /** Apple's layer carries the drawer when ours is transparent, and vice versa. */
    private void updateHostVisibility(Session session, float alpha) {
        if (session == null || !session.host.isAttachedToWindow()) {
            return;
        }
        if (alpha > 0.02f) {
            session.hiddenByUs = true;
            if (session.host.getVisibility() != View.INVISIBLE) {
                session.host.setVisibility(View.INVISIBLE);
            }
        }
        else {
            session.hiddenByUs = false;
            if (session.host.getVisibility() == View.INVISIBLE) {
                session.host.setVisibility(View.VISIBLE);
            }
        }
    }

    private void suppressNativeDrawing() {
        try {
            Method method = this.layerClass.getDeclaredMethod("onDraw", Canvas.class);
            this.registrations.add(this.context.getHooks().hook((Executable)method, false, new PluginHook(){

                public void before(PluginCall pluginCall) {
                    pluginCall.returnResult(null);
                }
            }));
            this.log("原生背景 onDraw 已拦截");
        }
        catch (Throwable throwable) {
            this.log("原生背景未覆写 onDraw，改为隐藏视图");
        }
    }

    private final PluginHook constructorHook = new PluginHook(){

        public void before(PluginCall pluginCall) {
            Object receiver = pluginCall.getReceiver();
            if (!(receiver instanceof View) || !BackgroundController.this.layerClass.isInstance(receiver)) {
                return;
            }
            View layer = (View)receiver;
            // Still under construction here; act once the host call has returned.
            BackgroundController.this.main.post(() -> BackgroundController.this.onArtwork(layer, BackgroundController.this.artworkFromHostNow(layer)));
        }
    };

    /**
     * Reads the cover straight off the player's artwork view so the very first
     * replacement frame already shows the cover instead of a bare gradient.
     */
    private Bitmap artworkFromHostNow(View view) {
        try {
            View root = view.getRootView();
            if (root == null) {
                return null;
            }
            int id = root.getResources().getIdentifier("artwork_image", "id", this.context.getHostPackageName());
            if (id == 0) {
                return null;
            }
            View image = root.findViewById(id);
            if (!(image instanceof ImageView)) {
                return null;
            }
            Drawable drawable = ((ImageView)image).getDrawable();
            if (!(drawable instanceof BitmapDrawable)) {
                return null;
            }
            Bitmap bitmap = ((BitmapDrawable)drawable).getBitmap();
            if (bitmap == null || bitmap.isRecycled()) {
                return null;
            }
            return ArtworkProcessor.capture(bitmap);
        }
        catch (Throwable throwable) {
            this.reportOnce("pull-now", throwable);
            return null;
        }
    }

    /** The same layer class backs the mini player; only the full player area qualifies. */
    private boolean isBigEnough(View layer) {
        View root = layer.getRootView();
        if (root == null) {
            return false;
        }
        int rootWidth = root.getWidth();
        int rootHeight = root.getHeight();
        int width = layer.getWidth();
        int height = layer.getHeight();
        if (rootWidth <= 0 || rootHeight <= 0 || width <= 0 || height <= 0) {
            return false;
        }
        return width >= rootWidth * 0.72f && height >= rootHeight * 0.55f
                && (long)width * height >= (long)rootWidth * rootHeight * 0.50d;
    }

    /** Re-check once the layer has been measured; before that its size is 0. */
    private void watchLayout(View layer) {
        if (this.layoutChecks.put(layer, Boolean.TRUE) != null) {
            return;
        }
        layer.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){

            public void onLayoutChange(View view, int n, int n2, int n3, int n4, int n5, int n6, int n7, int n8) {
                BackgroundController.this.layoutChecks.remove(view);
                view.removeOnLayoutChangeListener((View.OnLayoutChangeListener)this);
                BackgroundController.this.main.post(() -> BackgroundController.this.onArtwork(view, BackgroundController.this.artworkFromHostNow(view)));
            }
        });
    }

    private void onArtwork(View view, Bitmap bitmap) {
        ++this.attachAttempts;
        try {
            if (!this.prepared || !this.settings.enabled()) {
                this.removeSession(view);
                return;
            }
            if (!this.isBigEnough(view)) {
                this.watchLayout(view);
                return;
            }
            MeshBackgroundView meshBackgroundView = this.ensureAttached(view);
            if (meshBackgroundView == null) {
                return;
            }
            meshBackgroundView.setIntensity(this.settings.intensity());
            if (this.settings.followArtwork() && bitmap != null) {
                this.rememberRawArtwork(bitmap);
                Bitmap processed = this.preprocessArtwork(bitmap);
                this.rememberArtwork(processed);
                meshBackgroundView.setArtwork(processed);
                if (processed != null && processed.getWidth() > 0) {
                    this.logOnce("artwork-applied", "背景图已应用 " + processed.getWidth() + "x" + processed.getHeight());
                }
            } else if (this.settings.followArtwork() && this.lastArtwork != null
                    && !this.lastArtwork.isRecycled()) {
                // The page was rebuilt after the process sat in the background:
                // reuse the last cover instead of falling back to the palette.
                Bitmap restored = null;
                try {
                    restored = this.lastArtwork.copy(Bitmap.Config.ARGB_8888, false);
                }
                catch (Throwable ignored) {
                }
                if (restored != null) {
                    meshBackgroundView.setArtwork(restored);
                } else {
                    meshBackgroundView.setPalette(Palette.fallback());
                }
            } else {
                meshBackgroundView.setPalette(Palette.fallback());
            }
        }
        catch (Throwable throwable) {
            ++this.attachFailures;
            this.reportOnce("attach", throwable);
        }
    }

    private MeshBackgroundView ensureAttached(View view) {
        ViewGroup viewParent;
        Session session = this.sessions.get(view);
        if (session != null) {
            viewParent = (ViewGroup)view.getParent();
            if (viewParent != null && session.background.getParent() == viewParent) {
                if (view.getVisibility() != 4) {
                    view.setVisibility(4);
                }
                return session.background;
            }
            this.removeSession(view);
        }
        if (!(view.getParent() instanceof ViewGroup)) {
            this.watchAttachment(view);
            return null;
        }
        viewParent = (ViewGroup)view.getParent();
        MeshBackgroundView meshBackgroundView = new MeshBackgroundView(view.getContext());
        meshBackgroundView.setTag(TAG);
        meshBackgroundView.setIntensity(this.settings.intensity());
        meshBackgroundView.setPulseSource(this.audio);
        meshBackgroundView.setBlurEnabled(this.settings.blurEnabled());
        // Collapsed drawer means invisible background; the first preDraw raises it.
        meshBackgroundView.setAlpha(0f);
        int n = Math.max(0, viewParent.indexOfChild(view));
        ViewGroup.LayoutParams layoutParams = BackgroundController.copyParams(view.getLayoutParams());
        if (layoutParams != null) {
            viewParent.addView((View)meshBackgroundView, n, layoutParams);
        } else {
            viewParent.addView((View)meshBackgroundView, n);
        }
        view.setVisibility(4);
        Session session2 = new Session(view, meshBackgroundView);
        session2.hiddenByUs = true;
        this.scheduleAudioCheck();
        this.sessions.put(view, session2);
        view.addOnAttachStateChangeListener((View.OnAttachStateChangeListener)session2);
        View rootView = view.getRootView();
        if (rootView != null && rootView.getViewTreeObserver().isAlive()) {
            rootView.getViewTreeObserver().addOnPreDrawListener((ViewTreeObserver.OnPreDrawListener)session2);
        }
        this.main.postDelayed(() -> this.pullArtwork(view, session2), 500L);
        this.main.postDelayed(() -> this.pullArtwork(view, session2), 2500L);
        return meshBackgroundView;
    }

    /** Keeps one software copy of the cover so a rebuilt page can restore it. */
    private void rememberArtwork(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        try {
            Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (copy == null) return;
            Bitmap old = this.lastArtwork;
            this.lastArtwork = copy;
            if (old != null && !old.isRecycled()) old.recycle();
        }
        catch (Throwable ignored) {
        }
    }

    private void rememberRawArtwork(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        try {
            Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (copy == null) return;
            Bitmap old = this.lastRawArtwork;
            this.lastRawArtwork = copy;
            if (old != null && !old.isRecycled()) old.recycle();
        }
        catch (Throwable ignored) {
        }
    }

    private Bitmap preprocessArtwork(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return bitmap;
        }
        if (!this.settings.cleanExtremes()) {
            return bitmap;
        }
        try {
            Bitmap cleaned = ArtworkProcessor.cleanExtremes(bitmap, this.settings.cleanStrength());
            if (cleaned != null && cleaned != bitmap) {
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                return cleaned;
            }
        }
        catch (Throwable throwable) {
            this.reportOnce("clean-extremes", throwable);
        }
        return bitmap;
    }

    private void reapplyCleanArtwork() {
        Bitmap raw = this.lastRawArtwork;
        Bitmap fresh = null;
        if (raw == null || raw.isRecycled()) {
            for (Session session : new ArrayList<Session>(this.sessions.values())) {
                fresh = this.artworkFromHostNow(session.host);
                if (fresh != null) {
                    this.rememberRawArtwork(fresh);
                    raw = fresh;
                    break;
                }
            }
        }
        if (raw == null || raw.isRecycled()) {
            return;
        }
        Bitmap copy = null;
        try {
            copy = raw.copy(Bitmap.Config.ARGB_8888, false);
        }
        catch (Throwable ignored) {
        }
        if (copy == null) {
            if (fresh != null && !fresh.isRecycled()) {
                fresh.recycle();
            }
            return;
        }
        Bitmap processed = this.preprocessArtwork(copy);
        this.rememberArtwork(processed);
        for (Session session : new ArrayList<Session>(this.sessions.values())) {
            Bitmap target = null;
            try {
                target = processed.copy(Bitmap.Config.ARGB_8888, false);
            }
            catch (Throwable ignored) {
            }
            if (target != null) {
                session.background.setArtwork(target);
            }
        }
        if (processed != null && !processed.isRecycled()) {
            processed.recycle();
        }
        if (fresh != null && !fresh.isRecycled()) {
            fresh.recycle();
        }
    }

    private void pullArtwork(View view, Session session) {
        if (session.pullAttempts >= 2) {
            return;
        }
        session.pullAttempts++;
        session.pulled = true;
        try {
            View view2 = view.getRootView();
            if (view2 == null) {
                return;
            }
            int n = view2.getResources().getIdentifier("artwork_image", "id", this.context.getHostPackageName());
            if (n == 0) {
                return;
            }
            View view3 = view2.findViewById(n);
            if (!(view3 instanceof ImageView)) {
                return;
            }
            Drawable drawable = ((ImageView)view3).getDrawable();
            if (!(drawable instanceof BitmapDrawable)) {
                return;
            }
            Bitmap bitmap = ((BitmapDrawable)drawable).getBitmap();
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            Bitmap bitmap2 = ArtworkProcessor.capture(bitmap);
            if (bitmap2 == null) {
                return;
            }
            this.rememberRawArtwork(bitmap2);
            Bitmap processed = this.preprocessArtwork(bitmap2);
            this.rememberArtwork(processed);
            session.background.setArtwork(processed);
            session.pullAttempts = 2;
            this.logOnce("artwork-pulled", "从播放器封面视图取图成功 " + processed.getWidth() + "x" + processed.getHeight());
        }
        catch (Throwable throwable) {
            this.reportOnce("pull", throwable);
        }
    }

    String probeInternalCapture() {
        if (!this.settings.enabled()) {
            return "背景替换已关闭，收到的音频不会驱动背景";
        }
        if (!this.settings.audioTap()) {
            return "读取播放音频开关已关闭；请在设置中启用";
        }
        return this.audio.captureBridgeStatus();
    }

    String openCaptureHelper() {
        return this.audio.openCaptureHelper();
    }

    private void scheduleAudioCheck() {
        if (this.audioCheckScheduled) {
            return;
        }
        this.audioCheckScheduled = true;
        this.main.postDelayed(() -> {
            if (noAudioToasted || !this.settings.enabled() || !this.settings.audioTap()) {
                return;
            }
            if (this.audio.usingRealAudio() || this.audio.bridgeState() >= 1) {
                return;
            }
            if (this.audio.writeCount() == 0L && this.audio.hooksRegistered() == 0) {
                return;
            }
            noAudioToasted = true;
            try {
                String string = this.audio.writeCount() == 0L ? "歌词背景：Apple Music 未使用 Java 音频层，律动不可用" : "歌词背景：音频编码被跳过，律动不可用";
                Toast.makeText((Context)this.context.getApplication(), (CharSequence)string, (int)1).show();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }, 12000L);
    }

    private void watchAttachment(View view) {
        if (this.waiting.put(view, Boolean.TRUE) != null) {
            return;
        }
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){

            public void onViewAttachedToWindow(View view) {
                view.removeOnAttachStateChangeListener((View.OnAttachStateChangeListener)this);
                BackgroundController.this.waiting.remove(view);
                BackgroundController.this.main.post(() -> BackgroundController.this.onArtwork(view, BackgroundController.this.artworkFromHostNow(view)));
            }

            public void onViewDetachedFromWindow(View view) {
                view.removeOnAttachStateChangeListener((View.OnAttachStateChangeListener)this);
                BackgroundController.this.waiting.remove(view);
            }
        });
    }

    private void removeSession(View view) {
        try {
            Session session = this.sessions.remove(view);
            if (session == null) {
                return;
            }
            view.removeOnAttachStateChangeListener((View.OnAttachStateChangeListener)session);
            View rootView = view.getRootView();
            if (rootView != null && rootView.getViewTreeObserver().isAlive()) {
                rootView.getViewTreeObserver().removeOnPreDrawListener((ViewTreeObserver.OnPreDrawListener)session);
            }
            this.layoutChecks.remove(view);
            ViewParent viewParent = session.background.getParent();
            if (viewParent instanceof ViewGroup) {
                ((ViewGroup)viewParent).removeView((View)session.background);
            }
            session.background.setTag(null);
            if (session.hiddenByUs && view.getVisibility() == 4) {
                view.setVisibility(0);
            }
        }
        catch (Throwable throwable) {
            this.reportOnce("detach", throwable);
        }
    }

    void start() {
        this.prepared = true;
        if (!this.lifecycleRegistered) {
            try {
                this.context.getApplication().registerActivityLifecycleCallbacks(this.lifecycle);
                this.lifecycleRegistered = true;
            }
            catch (Throwable throwable) {
                this.log("Activity 回调不可用: " + throwable.getClass().getSimpleName());
            }
        }
        this.log("插件已启动");
        this.applySettings();
    }

    void stop() {
        this.unregisterLifecycle();
        this.runOnMain(() -> {
            for (View view : new ArrayList<View>(this.sessions.keySet())) {
                this.removeSession(view);
            }
        });
        this.log("插件已停止");
    }

    void applySettings() {
        this.runOnMain(() -> {
            int mask = this.settings.presetMask();
            if (mask != this.lastPresetMask) {
                this.lastPresetMask = mask;
                dev.zen.lyricbg.render.MeshWarp.setPresetMask(mask);
                for (Session session : new ArrayList<Session>(this.sessions.values())) {
                    session.background.rebuildMesh();
                }
            }
            if (this.settings.audioTap()) {
                this.audio.install();
            } else {
                this.audio.uninstall();
            }
            this.audio.setBpm(this.settings.bpm());
            this.audio.setMetronomeEnabled(this.settings.metronome());
            this.audio.setEnabled(this.settings.enabled() && this.settings.audioTap());
            if (!this.settings.enabled()) {
                for (View view : new ArrayList<View>(this.sessions.keySet())) {
                    this.removeSession(view);
                }
                return;
            }
            boolean cleanChanged = this.appliedCleanEnabled != this.settings.cleanExtremes()
                    || Math.abs(this.appliedCleanStrength - this.settings.cleanStrength()) > 0.001f;
            if (cleanChanged) {
                this.appliedCleanEnabled = this.settings.cleanExtremes();
                this.appliedCleanStrength = this.settings.cleanStrength();
                this.reapplyCleanArtwork();
            }
            for (Session session : new ArrayList<Session>(this.sessions.values())) {
                session.background.setIntensity(this.settings.intensity());
                session.background.setBlurEnabled(this.settings.blurEnabled());
            }
        });
    }

    String describeStatus() {
        Object object;
        String string = this.context.getHostVersionName() + " (" + this.context.getHostVersionCode() + ")";
        if (!this.prepared) {
            return "宿主 " + string + " · 尚未接入";
        }
        if (!this.settings.enabled()) {
            return "宿主 " + string + " · 已关闭";
        }
        int n = 0;
        for (Session session : this.sessions.values()) {
            if (session.background.getParent() == null) continue;
            ++n;
        }
        if (!this.settings.audioTap()) {
            object = " · 音频关闭";
        } else if (this.audio.bridgeState() == 3) {
            object = " · 内录已授权但 Apple Music 音频静音";
        } else if (this.audio.usingPlaybackCapture()) {
            object = " · 系统内录脉冲 " + this.audio.externalPulsePercent() + "% / " + this.audio.externalReportCount();
        } else if (this.audio.bridgeState() == 2) {
            object = " · 系统内录已启动，等待音频数据";
        } else if (this.audio.bridgeState() == -2) {
            object = " · 插件与音频捕获 UID 校验失败";
        } else if (this.audio.bridgeState() == -4) {
            object = " · Visualizer 被设备拦截，等待备用授权";
        } else if (this.audio.bridgeState() == -3) {
            object = " · 正在连接音频捕获";
        } else if (this.audio.bridgeState() == 1) {
            object = " · 音频捕获已连接，等待启动";
        } else if (this.audio.usingRealAudio()) {
            object = " · AudioTrack 音频";
        } else {
            object = " · 钩子 " + this.audio.hooksRegistered() + "/" + (this.audio.hooksRegistered() + this.audio.hooksFailed()) + " · 写入 " + this.audio.writeCount();
            if (this.audio.firstHookError() != null) {
                object = (String)object + " · " + this.audio.firstHookError();
            }
        }
        return "宿主 " + string + " · 挂载 " + n + "/" + this.attachAttempts + (this.attachFailures > 0 ? " 失败" + this.attachFailures : "") + (String)object + (this.isVerifiedBuild() ? " · 已验证构建" : " · 未验证构建");
    }

    void shutdown() {
        this.unregisterLifecycle();
        this.audio.uninstall();
        this.registrations.clear();
        this.worker.shutdownNow();
        this.runOnMain(() -> {
            for (View view : new ArrayList<View>(this.sessions.keySet())) {
                this.removeSession(view);
            }
        });
    }

    private void unregisterLifecycle() {
        if (!this.lifecycleRegistered) {
            return;
        }
        this.lifecycleRegistered = false;
        try {
            this.context.getApplication().unregisterActivityLifecycleCallbacks(this.lifecycle);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private void scanForLayer(Activity activity) {
        View view;
        if (!this.prepared || !this.settings.enabled()) {
            return;
        }
        try {
            view = activity.getWindow().getDecorView();
        }
        catch (Throwable throwable) {
            return;
        }
        ArrayList<View> arrayList = new ArrayList<View>(2);
        this.collectLayers(view, arrayList, 0);
        for (View view2 : arrayList) {
            this.onArtwork(view2, null);
        }
    }

    private void collectLayers(View view, List<View> list, int n) {
        if (view == null || n > 48) {
            return;
        }
        if (this.layerClass.isInstance(view)) {
            list.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup)view;
            int n2 = viewGroup.getChildCount();
            for (int i = 0; i < n2; ++i) {
                this.collectLayers(viewGroup.getChildAt(i), list, n + 1);
            }
        }
    }

    private boolean isVerifiedBuild() {
        long l = this.context.getHostVersionCode();
        for (long l2 : VERIFIED_VERSION_CODES) {
            if (l2 != l) continue;
            return true;
        }
        Object object = this.context.getHostVersionName();
        return object != null && ((String)object).startsWith("7.0.0");
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            this.main.post(runnable);
        }
    }

    private void logOnce(String string, String string2) {
        if (this.loggedOnce.add(string)) {
            this.context.log(string2, null);
        }
    }

    private void log(String string) {
        this.context.log(string, null);
    }

    private void reportOnce(String string, Throwable throwable) {
        if (!this.reportedFailures.add(string)) {
            return;
        }
        this.context.log("背景层操作失败(" + string + "): " + throwable.getClass().getSimpleName(), throwable);
    }

    private static ViewGroup.LayoutParams copyParams(ViewGroup.LayoutParams layoutParams) {
        if (layoutParams == null) {
            return null;
        }
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams)layoutParams);
        }
        return new ViewGroup.LayoutParams(layoutParams);
    }

    private final class Session
    implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        final View host;
        final MeshBackgroundView background;
        int generation;
        boolean hiddenByUs;
        boolean pulled;
        int pullAttempts;

        Session(View view, MeshBackgroundView meshBackgroundView) {
            this.host = view;
            this.background = meshBackgroundView;
        }

        /** Drives the background's own opacity from the drawer position, every frame. */
        public boolean onPreDraw() {
            if (!this.host.isAttachedToWindow() || this.background.getParent() == null) {
                return true;
            }
            try {
                float alpha = BackgroundController.this.revealAlpha(this.host);
                if (Math.abs(this.background.getAlpha() - alpha) > 0.008f) {
                    this.background.setAlpha(alpha);
                }
                BackgroundController.this.updateHostVisibility(this, alpha);
            }
            catch (Throwable ignored) {
                // Never let a frame-time failure break the host.
            }
            return true;
        }

        public void onViewAttachedToWindow(View view) {
            BackgroundController.this.main.post(() -> BackgroundController.this.onArtwork(view, BackgroundController.this.artworkFromHostNow(view)));
        }

        public void onViewDetachedFromWindow(View view) {
            BackgroundController.this.main.post(() -> BackgroundController.this.removeSession(view));
        }
    }
}

