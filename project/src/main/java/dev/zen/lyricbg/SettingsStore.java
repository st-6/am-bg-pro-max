/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.amenhancer.plugin.api.PluginContext
 */
package dev.zen.lyricbg;

import dev.amenhancer.plugin.api.PluginContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

final class SettingsStore {
    private static final String FILE_NAME = "settings.properties";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_INTENSITY = "intensity";
    private static final String KEY_FOLLOW_ARTWORK = "followArtwork";
    private static final String KEY_BPM = "bpm";
    private static final String KEY_METRONOME = "metronome";
    private static final String KEY_PRESETS = "presetMask";
    private static final String KEY_AUDIO_TAP = "audioTap";
    private static final String KEY_BLUR = "blurEnabled";
    private final File file;
    private volatile boolean enabled;
    private volatile float intensity;
    private volatile boolean followArtwork;
    private volatile float bpm;
    private volatile boolean metronome;
    private volatile int presetMask;
    private volatile boolean audioTap;
    private volatile boolean blurEnabled;

    SettingsStore(PluginContext pluginContext) {
        this.file = new File(pluginContext.getDataDirectory(), FILE_NAME);
        Properties properties = new Properties();
        if (this.file.isFile()) {
            try (FileInputStream fileInputStream = new FileInputStream(this.file);){
                properties.load(fileInputStream);
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
        this.enabled = Boolean.parseBoolean(properties.getProperty(KEY_ENABLED, "true"));
        this.intensity = SettingsStore.clamp(Float.parseFloat(properties.getProperty(KEY_INTENSITY, "0.5")));
        this.followArtwork = Boolean.parseBoolean(properties.getProperty(KEY_FOLLOW_ARTWORK, "true"));
        this.bpm = SettingsStore.clampBpm(Float.parseFloat(properties.getProperty(KEY_BPM, "100")));
        this.metronome = Boolean.parseBoolean(properties.getProperty(KEY_METRONOME, "false"));
        int mask = Integer.parseInt(properties.getProperty(KEY_PRESETS, "31"));
        this.presetMask = (mask & 31) == 0 ? 31 : (mask & 31);
        this.audioTap = Boolean.parseBoolean(properties.getProperty(KEY_AUDIO_TAP, "false"));
        this.blurEnabled = Boolean.parseBoolean(properties.getProperty(KEY_BLUR, "true"));
    }

    boolean enabled() {
        return this.enabled;
    }

    float intensity() {
        return this.intensity;
    }

    boolean followArtwork() {
        return this.followArtwork;
    }

    float bpm() {
        return this.bpm;
    }

    int presetMask() {
        return this.presetMask;
    }

    boolean metronome() {
        return this.metronome;
    }

    boolean audioTap() {
        return this.audioTap;
    }

    boolean blurEnabled() {
        return this.blurEnabled;
    }

    void setEnabled(boolean bl) {
        this.enabled = bl;
        this.save();
    }

    void setIntensity(float f) {
        this.intensity = SettingsStore.clamp(f);
        this.save();
    }

    void setFollowArtwork(boolean bl) {
        this.followArtwork = bl;
        this.save();
    }

    void setBpm(float f) {
        this.bpm = SettingsStore.clampBpm(f);
        this.save();
    }

    void setPresetMask(int mask) {
        this.presetMask = (mask & 31) == 0 ? 31 : (mask & 31);
        this.save();
    }

    void setMetronome(boolean bl) {
        this.metronome = bl;
        this.save();
    }

    void setAudioTap(boolean bl) {
        this.audioTap = bl;
        this.save();
    }

    void setBlurEnabled(boolean bl) {
        this.blurEnabled = bl;
        this.save();
    }

    private static float clampBpm(float f) {
        if (Float.isNaN(f)) {
            return 100.0f;
        }
        return Math.max(40.0f, Math.min(220.0f, f));
    }

    private static float clamp(float f) {
        if (Float.isNaN(f)) {
            return 0.5f;
        }
        return Math.max(0.0f, Math.min(1.0f, f));
    }

    private synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty(KEY_ENABLED, Boolean.toString(this.enabled));
        properties.setProperty(KEY_INTENSITY, Float.toString(this.intensity));
        properties.setProperty(KEY_FOLLOW_ARTWORK, Boolean.toString(this.followArtwork));
        properties.setProperty(KEY_BPM, Float.toString(this.bpm));
        properties.setProperty(KEY_METRONOME, Boolean.toString(this.metronome));
        properties.setProperty(KEY_PRESETS, Integer.toString(this.presetMask));
        properties.setProperty(KEY_AUDIO_TAP, Boolean.toString(this.audioTap));
        properties.setProperty(KEY_BLUR, Boolean.toString(this.blurEnabled));
        try (FileOutputStream fileOutputStream = new FileOutputStream(this.file);){
            properties.store(fileOutputStream, "AM++ fluid background settings");
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }
}

