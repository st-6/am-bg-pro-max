package dev.zen.lyricbg;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import dev.amenhancer.plugin.api.PluginSettingsSession;

/**
 * Plugin settings rendered as plain Views inside AM++'s MiuiX dialog.
 *
 * The dialog supplies the surface, title and spacing, so this page only builds
 * the cards: rounded groups, a title with an optional summary, the control on
 * the right, and hairline dividers between rows.
 */
final class SettingsPage {

    private final Context context;
    private final BackgroundController controller;
    private final SettingsStore settings;

    private boolean dark;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorAccent;
    private int colorCard;
    private int colorSecondary;
    private int colorDivider;

    SettingsPage(Context context, BackgroundController controller, SettingsStore settings) {
        this.context = context;
        this.controller = controller;
        this.settings = settings;
        resolveColors();
    }

    PluginSettingsSession create() {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(dp(14), dp(4), dp(14), dp(14));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout statusCard = card();
        TextView statusTitle = text("状态", 16f, colorTextPrimary, false);
        final TextView statusValue = text(controller.describeStatus(), 13f, colorTextSecondary, false);
        statusValue.setPadding(0, dp(2), 0, 0);
        LinearLayout statusColumn = new LinearLayout(context);
        statusColumn.setOrientation(LinearLayout.VERTICAL);
        statusColumn.setPadding(dp(16), dp(12), dp(16), dp(12));
        statusColumn.addView(statusTitle);
        statusColumn.addView(statusValue);
        statusCard.addView(statusColumn);
        root.addView(statusCard);

        root.addView(gap());

        LinearLayout appearance = card();
        CompoundButton enabled = makeSwitch(settings.enabled());
        enabled.setOnCheckedChangeListener((button, checked) -> {
            this.settings.setEnabled(checked);
            controller.applySettings();
            statusValue.setText(controller.describeStatus());
        });
        appearance.addView(row("启用背景替换", null, enabled));
        appearance.addView(divider());

        CompoundButton followArtwork = makeSwitch(settings.followArtwork());
        followArtwork.setOnCheckedChangeListener((button, checked) -> {
            this.settings.setFollowArtwork(checked);
            controller.applySettings();
        });
        appearance.addView(row("使用封面生成背景", "关闭后改用调色盘渐变", followArtwork));
        appearance.addView(divider());

        CompoundButton clean = makeSwitch(settings.cleanExtremes());
        clean.setOnCheckedChangeListener((button, checked) -> {
            this.settings.setCleanExtremes(checked);
            controller.applySettings();
        });
        appearance.addView(row("去除黑白", "封面中的纯白和死黑会替换为邻近色调", clean));
        appearance.addView(divider());
        appearance.addView(cleanStrengthRow());
        appearance.addView(divider());

        CompoundButton blur = makeSwitch(settings.blurEnabled());
        blur.setOnCheckedChangeListener((button, checked) -> {
            this.settings.setBlurEnabled(checked);
            controller.applySettings();
        });
        appearance.addView(row("背景模糊", null, blur));
        root.addView(appearance);

        root.addView(gap());
        root.addView(presetCard());

        root.addView(gap());
        LinearLayout audio = card();
        CompoundButton audioTap = makeSwitch(settings.audioTap());
        audioTap.setOnCheckedChangeListener((button, checked) -> {
            this.settings.setAudioTap(checked);
            controller.applySettings();
            statusValue.setText(controller.describeStatus());
        });
        audio.addView(row("读取播放音频", "需要配套的音频捕获应用", audioTap));
        audio.addView(divider());

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView openCapture = textButton("打开音频捕获并授权", true);
        openCapture.setOnClickListener(view -> toast(controller.openCaptureHelper()));
        TextView probe = textButton("检查内录状态", false);
        probe.setOnClickListener(view -> toast(controller.probeInternalCapture()));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams probeParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        probeParams.setMarginStart(dp(8));
        actions.addView(openCapture, openParams);
        actions.addView(probe, probeParams);
        audio.addView(actions);
        root.addView(audio);

        TextView footer = text("插件的启用、停用与删除仍由 AM++ 管理，重启 Apple Music 后生效；其余设置即时生效。",
                12f, colorTextSecondary, false);
        footer.setPadding(dp(4), dp(12), dp(4), dp(4));
        root.addView(footer);

        return new PluginSettingsSession() {
            @Override
            public View getView() {
                return scroll;
            }

            @Override
            public void close() {
                enabled.setOnCheckedChangeListener(null);
                followArtwork.setOnCheckedChangeListener(null);
                clean.setOnCheckedChangeListener(null);
                blur.setOnCheckedChangeListener(null);
                audioTap.setOnCheckedChangeListener(null);
            }
        };
    }

    private LinearLayout presetCard() {
        LinearLayout card = card();
        card.addView(row("形变预设", "勾选多个时每次随机，只勾一个则固定", null));
        card.addView(divider());

        final CompoundButton[] boxes = new CompoundButton[5];
        final boolean[] syncing = {false};
        for (int i = 0; i < 5; i++) {
            final int bit = 1 << i;
            final CompoundButton box = new MiuixControls.Check(context);
            box.setChecked((settings.presetMask() & bit) != 0);
            box.setOnCheckedChangeListener((button, checked) -> {
                if (syncing[0]) return;
                syncing[0] = true;
                try {
                    int mask = settings.presetMask();
                    settings.setPresetMask(checked ? (mask | bit) : (mask & ~bit));
                    int current = settings.presetMask();
                    for (int j = 0; j < boxes.length; j++) {
                        boolean want = (current & (1 << j)) != 0;
                        if (boxes[j].isChecked() != want) boxes[j].setChecked(want);
                    }
                    controller.applySettings();
                } finally {
                    syncing[0] = false;
                }
            });
            boxes[i] = box;
            card.addView(row("预设 " + (i + 1), null, box));
            if (i < 4) card.addView(divider());
        }
        return card;
    }

    private static final float[] CLEAN_LEVELS = {0.25f, 1.0f / 3.0f, 0.5f, 0.75f};

    private LinearLayout cleanStrengthRow() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(8));

        box.addView(text("去除强度", 16f, colorTextPrimary, false));
        final TextView value = text(cleanLabel(nearestCleanIndex(settings.cleanStrength())),
                12f, colorTextSecondary, false);
        value.setPadding(0, dp(2), 0, dp(6));
        box.addView(value);

        SeekBar bar = new SeekBar(context);
        bar.setMax(CLEAN_LEVELS.length - 1);
        bar.setProgress(nearestCleanIndex(settings.cleanStrength()));
        bar.setProgressTintList(ColorStateList.valueOf(colorAccent));
        bar.setThumbTintList(ColorStateList.valueOf(colorAccent));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(cleanLabel(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int index = seekBar.getProgress();
                settings.setCleanStrength(CLEAN_LEVELS[index]);
                controller.applySettings();
            }
        });
        box.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static int nearestCleanIndex(float strength) {
        int best = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < CLEAN_LEVELS.length; i++) {
            float d = Math.abs(CLEAN_LEVELS[i] - strength);
            if (d < distance) {
                distance = d;
                best = i;
            }
        }
        return best;
    }

    private static CharSequence cleanLabel(int index) {
        String[] labels = {"去除 1/4", "去除 1/3", "去除 1/2", "去除 3/4"};
        return labels[Math.max(0, Math.min(labels.length - 1, index))];
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(16));
        background.setColor(colorCard);
        card.setBackground(background);
        card.setClipToPadding(false);
        card.setPadding(0, dp(4), 0, dp(4));
        return card;
    }

    private LinearLayout row(CharSequence title, CharSequence summary, View control) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setMinimumHeight(dp(56));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16f, colorTextPrimary, false));
        if (summary != null) {
            TextView summaryView = text(summary, 12f, colorTextSecondary, false);
            summaryView.setPadding(0, dp(2), 0, 0);
            texts.addView(summaryView);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (control != null) {
            LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            controlParams.setMarginStart(dp(12));
            row.addView(control, controlParams);
        }
        return row;
    }

    private View divider() {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setPadding(dp(16), 0, 0, 0);
        View line = new View(context);
        line.setBackgroundColor(colorDivider);
        wrapper.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return wrapper;
    }

    private View gap() {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        return view;
    }

    private CompoundButton makeSwitch(boolean checked) {
        CompoundButton view = new MiuixControls.Switch(context);
        view.setChecked(checked);
        return view;
    }

    private TextView textButton(CharSequence label, boolean primary) {
        TextView button = text(label, 14f, primary ? 0xFFFFFFFF : colorTextPrimary, false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(primary ? colorAccent : colorSecondary);
        button.setBackground(background);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView text(CharSequence value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private void resolveColors() {
        dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        // Tokens from the MiuiX design system (docs/guide/colors.md).
        colorTextPrimary   = dark ? 0xFFF2F2F2 : 0xFF000000;
        colorTextSecondary = dark ? 0x80FFFFFF : 0x99000000;
        colorAccent        = dark ? 0xFF277AF7 : 0xFF3482FF;
        colorCard          = dark ? 0xFF2D2D2D : 0xFFE8E8E8;
        colorSecondary     = dark ? 0xFF505050 : 0xFFE6E6E6;
        colorDivider       = dark ? 0xFF393939 : 0xFFE0E0E0;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
