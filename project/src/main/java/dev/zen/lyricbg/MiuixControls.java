package dev.zen.lyricbg;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.CompoundButton;

/**
 * Pill switch and round check mark drawn by hand, because AM++ hosts plugin
 * settings in a MiuiX dialog and the platform widgets read as a different
 * design language next to it.
 */
final class MiuixControls {

    private MiuixControls() {
    }

    /** Fixed MiuiX blue; the theme accent would drag in Material's teal. */
    static int accent(Context context) {
        return isDark(context) ? 0xFF277AF7 : 0xFF3482FF;
    }

    static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static final class Switch extends CompoundButton {

        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int offColor;
        private final int onColor;
        private float progress;

        Switch(Context context) {
            super(context);
            offColor = isDark(context) ? 0xFF505050 : 0xFFE6E6E6;
            onColor = accent(context);
            thumb.setColor(Color.WHITE);
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(dp(getContext(), 46), dp(getContext(), 28));
        }

        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            invalidate();
        }

        @Override
        public void toggle() {
            super.toggle();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float target = isChecked() ? 1f : 0f;
            progress += (target - progress) * 0.35f;
            boolean animating = Math.abs(target - progress) > 0.005f;
            if (!animating) progress = target;

            float width = getWidth();
            float height = getHeight();
            float radius = height / 2f;

            track.setColor(blend(offColor, onColor, progress));
            canvas.drawRoundRect(new RectF(0f, 0f, width, height), radius, radius, track);

            float inset = dp(getContext(), 3);
            float thumbRadius = radius - inset;
            float left = radius;
            float right = width - radius;
            float centerX = left + (right - left) * progress;
            canvas.drawCircle(centerX, radius, thumbRadius, thumb);

            if (animating) postInvalidateOnAnimation();
        }
    }

    static final class Check extends CompoundButton {

        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tick = new Path();
        private final int outlineColor;
        private final int onColor;

        Check(Context context) {
            super(context);
            outlineColor = isDark(context) ? 0x66FFFFFF : 0x66000000;
            onColor = accent(context);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(context, 1.5f));
            fill.setStyle(Paint.Style.FILL);
            mark.setStyle(Paint.Style.STROKE);
            mark.setStrokeWidth(dp(context, 2f));
            mark.setStrokeCap(Paint.Cap.ROUND);
            mark.setStrokeJoin(Paint.Join.ROUND);
            mark.setColor(Color.WHITE);
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int size = dp(getContext(), 24);
            setMeasuredDimension(size, size);
        }

        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            invalidate();
        }

        @Override
        public void toggle() {
            super.toggle();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float diameter = Math.min(getWidth(), getHeight());
            float radius = diameter / 2f;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            if (isChecked()) {
                fill.setColor(onColor);
                canvas.drawCircle(cx, cy, radius, fill);
                float unit = diameter / 24f;
                tick.reset();
                tick.moveTo(cx - 5.2f * unit, cy - 0.4f * unit);
                tick.lineTo(cx - 1.4f * unit, cy + 3.6f * unit);
                tick.lineTo(cx + 5.4f * unit, cy - 3.8f * unit);
                canvas.drawPath(tick, mark);
            } else {
                ring.setColor(outlineColor);
                canvas.drawCircle(cx, cy, radius - ring.getStrokeWidth() / 2f, ring);
            }
        }
    }

    private static int blend(int from, int to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * clamped);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped);
        return Color.argb(a, r, g, b);
    }
}
