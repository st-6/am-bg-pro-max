/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.Typeface
 *  android.view.View
 *  android.widget.Button
 *  android.widget.LinearLayout
 *  android.widget.SeekBar
 *  android.widget.SeekBar$OnSeekBarChangeListener
 *  android.widget.Switch
 *  android.widget.TextView
 *  android.widget.Toast
 *  dev.amenhancer.plugin.api.PluginSettingsSession
 */
package dev.zen.lyricbg;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import dev.amenhancer.plugin.api.PluginSettingsSession;
import dev.zen.lyricbg.BackgroundController;
import dev.zen.lyricbg.SettingsStore;

final class SettingsPage {
    private final Context context;
    private final BackgroundController controller;
    private final SettingsStore settings;

    SettingsPage(Context context, BackgroundController backgroundController, SettingsStore settingsStore) {
        this.context = context;
        this.controller = backgroundController;
        this.settings = settingsStore;
    }

    PluginSettingsSession create() {
        final LinearLayout linearLayout = new LinearLayout(this.context);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(this.dp(20), this.dp(16), this.dp(20), this.dp(16));
        TextView textView = new TextView(this.context);
        textView.setText((CharSequence)"AM背景Pro Max");
        textView.setTextSize(18.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        linearLayout.addView((View)textView);
        TextView textView2 = new TextView(this.context);
        textView2.setText((CharSequence)this.controller.describeStatus());
        textView2.setTextSize(12.0f);
        textView2.setPadding(0, this.dp(4), 0, this.dp(12));
        linearLayout.addView((View)textView2);
        TextView textView3 = new TextView(this.context);
        textView3.setText((CharSequence)"替换 Apple Music 播放器背景，颜色取自当前封面。");
        textView3.setTextSize(13.0f);
        textView3.setPadding(0, 0, 0, this.dp(10));
        linearLayout.addView((View)textView3);
        final Switch switch_ = new Switch(this.context);
        switch_.setText((CharSequence)"启用背景替换");
        switch_.setChecked(this.settings.enabled());
        switch_.setOnCheckedChangeListener((compoundButton, bl) -> {
            this.settings.setEnabled(bl);
            this.controller.applySettings();
            textView2.setText((CharSequence)this.controller.describeStatus());
        });
        linearLayout.addView((View)switch_);
        final Switch switch_2 = new Switch(this.context);
        switch_2.setText((CharSequence)"使用封面生成背景");
        switch_2.setChecked(this.settings.followArtwork());
        switch_2.setOnCheckedChangeListener((compoundButton, bl) -> {
            this.settings.setFollowArtwork(bl);
            this.controller.applySettings();
        });
        linearLayout.addView((View)switch_2);
        final Switch switch_3 = new Switch(this.context);
        switch_3.setText((CharSequence)"背景模糊");
        switch_3.setChecked(this.settings.blurEnabled());
        switch_3.setOnCheckedChangeListener((compoundButton, bl) -> {
            this.settings.setBlurEnabled(bl);
            this.controller.applySettings();
        });
        linearLayout.addView((View)switch_3);

        final TextView presetLabel = new TextView(this.context);
        presetLabel.setPadding(0, this.dp(12), 0, 0);
        presetLabel.setText((CharSequence)"形变预设（勾选多个时每次随机，只勾一个则固定）");
        linearLayout.addView((View)presetLabel);
        final CheckBox[] presetBoxes = new CheckBox[5];
        for (int i = 0; i < 5; i++) {
            final CheckBox box = new CheckBox(this.context);
            final int bit = 1 << i;
            box.setText((CharSequence)("预设 " + (i + 1)));
            box.setChecked((this.settings.presetMask() & bit) != 0);
            box.setOnCheckedChangeListener((compoundButton, bl) -> {
                int mask = this.settings.presetMask();
                mask = bl ? (mask | bit) : (mask & ~bit);
                this.settings.setPresetMask(mask);
                int current = this.settings.presetMask();
                if (current != mask) {
                    for (int j = 0; j < presetBoxes.length; j++) {
                        presetBoxes[j].setChecked((current & (1 << j)) != 0);
                    }
                }
                this.controller.applySettings();
            });
            presetBoxes[i] = box;
            linearLayout.addView((View)box);
        }
        final Switch switch_4 = new Switch(this.context);
        switch_4.setText((CharSequence)"读取播放音频（请使用搭配的音频捕获软件）");
        switch_4.setChecked(this.settings.audioTap());
        switch_4.setOnCheckedChangeListener((compoundButton, bl) -> {
            this.settings.setAudioTap(bl);
            this.controller.applySettings();
            textView2.setText((CharSequence)this.controller.describeStatus());
        });
        linearLayout.addView((View)switch_4);
        Button button = new Button(this.context);
        button.setText((CharSequence)"打开音频捕获并授权");
        button.setOnClickListener(view -> Toast.makeText((Context)this.context, (CharSequence)this.controller.openCaptureHelper(), (int)1).show());
        linearLayout.addView((View)button);
        Button button2 = new Button(this.context);
        button2.setText((CharSequence)"检查内录状态");
        button2.setOnClickListener(view -> {
            this.controller.probeInternalCapture();
            button2.postDelayed(() -> Toast.makeText((Context)this.context, (CharSequence)this.controller.probeInternalCapture(), (int)1).show(), 700L);
        });
        linearLayout.addView((View)button2);
        TextView textView6 = new TextView(this.context);
        textView6.setText((CharSequence)"音频捕获需单独安装。Android 会在启动时显示系统授权；若 Apple Music 的捕获策略或 DRM 返回静音，无法由插件绕过。");
        textView6.setTextSize(12.0f);
        textView6.setPadding(0, this.dp(12), 0, 0);
        linearLayout.addView((View)textView6);
        TextView textView7 = new TextView(this.context);
        textView7.setText((CharSequence)"改动即时生效。插件的启用、停用与删除仍由 AM++ 管理，重启 Apple Music 后生效。");
        textView7.setTextSize(12.0f);
        textView7.setPadding(0, this.dp(12), 0, 0);
        linearLayout.addView((View)textView7);
        return new PluginSettingsSession(){

            public View getView() {
                return linearLayout;
            }

            public void close() {
                switch_.setOnCheckedChangeListener(null);
                switch_2.setOnCheckedChangeListener(null);
                switch_3.setOnCheckedChangeListener(null);
                switch_4.setOnCheckedChangeListener(null);
            }
        };
    }

    private int dp(int n) {
        return Math.round((float)n * this.context.getResources().getDisplayMetrics().density);
    }
}

