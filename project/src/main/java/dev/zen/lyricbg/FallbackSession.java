/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.view.View
 *  android.widget.LinearLayout
 *  android.widget.TextView
 *  dev.amenhancer.plugin.api.PluginSettingsSession
 */
package dev.zen.lyricbg;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.amenhancer.plugin.api.PluginSettingsSession;

final class FallbackSession
implements PluginSettingsSession {
    private final LinearLayout root;

    FallbackSession(Context context, Throwable throwable) {
        this.root = new LinearLayout(context);
        this.root.setOrientation(1);
        int n = Math.round(20.0f * context.getResources().getDisplayMetrics().density);
        this.root.setPadding(n, n, n, n);
        TextView textView = new TextView(context);
        String string = throwable.getClass().getSimpleName();
        if (throwable.getMessage() != null && !throwable.getMessage().isEmpty()) {
            string = string + ": " + throwable.getMessage();
        }
        textView.setText((CharSequence)("设置页无法创建（" + string + "）。\n背景替换本身仍在运行，可用 adb logcat -s AppleMusicEnhancer 查看原因。"));
        textView.setTextSize(14.0f);
        this.root.addView((View)textView);
    }

    public View getView() {
        return this.root;
    }

    public void close() {
    }
}

