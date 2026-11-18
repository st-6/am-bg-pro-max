/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  dev.amenhancer.plugin.api.AmppPlugin
 *  dev.amenhancer.plugin.api.PluginContext
 *  dev.amenhancer.plugin.api.PluginSettingsSession
 */
package dev.zen.lyricbg;

import android.content.Context;
import dev.amenhancer.plugin.api.AmppPlugin;
import dev.amenhancer.plugin.api.PluginContext;
import dev.amenhancer.plugin.api.PluginSettingsSession;
import dev.zen.lyricbg.BackgroundController;
import dev.zen.lyricbg.FallbackSession;
import dev.zen.lyricbg.SettingsPage;
import dev.zen.lyricbg.SettingsStore;

public final class LyricBackgroundsPlugin
extends AmppPlugin {
    private SettingsStore settings;
    private BackgroundController controller;

    public void onLoad(PluginContext pluginContext) throws Exception {
        this.settings = new SettingsStore(pluginContext);
        this.controller = new BackgroundController(pluginContext, this.settings);
        this.controller.prepare();
        pluginContext.onClose(() -> this.controller.shutdown());
    }

    public void onStart() {
        this.controller.start();
    }

    public void onStop() {
        this.controller.stop();
    }

    public PluginSettingsSession createSettings(Context context) {
        try {
            return new SettingsPage(context, this.controller, this.settings).create();
        }
        catch (Throwable throwable) {
            return new FallbackSession(context, throwable);
        }
    }
}

