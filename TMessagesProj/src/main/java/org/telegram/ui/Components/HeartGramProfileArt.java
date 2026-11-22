package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.messenger.R;
import org.telegram.utils.settings.HeartGramAppearance;
import org.telegram.utils.settings.HeartGramProfiles;

public final class HeartGramProfileArt {
    private static final int[] FRAME_COLORS = {0, 0xffc57c6f, 0xff90a988, 0xff708dad,
            0xffb28cd0, 0xffdaab4e, 0xffee8c64, 0xff2cc3ce, 0xffb662cd, 0xffe2699c};
    private static final int[] BADGE_COLORS = {0xff793697, 0xff376c59, 0xff9c3840, 0xff316189, 0xff87591c};
    private static final int[] BADGE_ICONS = {R.drawable.heartgram_badge_developer, R.drawable.heartgram_badge_tester,
            R.drawable.heartgram_badge_banned, R.drawable.heartgram_badge_creator, R.drawable.heartgram_badge_donate};
    private static final int[] RAINBOW = {0xffff9298, 0xffffce82, 0xffffff9a, 0xff9ce4a9,
            0xff8fddff, 0xffc5adff, 0xffff9298};

    public static final class AvatarFrame {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Matrix transform = new Matrix();
        private final DashPathEffect dashes = new DashPathEffect(new float[] {dp(5), dp(3)}, 0);
        private int lastShape = -1;
        private int lastWidth, lastHeight, lastRoundRadius;

        public void draw(Canvas canvas, int width, int height, int frame, int roundRadius) {
            if (frame <= 0 || frame >= FRAME_COLORS.length || width <= dp(6) || height <= dp(6)) {
                return;
            }
            int shape = HeartGramAppearance.avatarShape.get();
            if (shape != lastShape || width != lastWidth || height != lastHeight || roundRadius != lastRoundRadius) {
                lastShape = shape;
                lastWidth = width;
                lastHeight = height;
                lastRoundRadius = roundRadius;
                if (shape == HeartGramAvatarShape.ROUND) {
                    float radius = Math.max(0, roundRadius - dp(2));
                    path.reset();
                    path.addRoundRect(dp(2), dp(2), width - dp(2), height - dp(2), radius, radius, Path.Direction.CW);
                } else {
                    HeartGramAvatarShape.build(path, shape);
                    transform.setScale(width - dp(4), height - dp(4));
                    transform.postTranslate(dp(2), dp(2));
                    path.transform(transform);
                }
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(FRAME_COLORS[frame]);
            paint.setPathEffect(frame == 7 || frame == 9 ? dashes : null);
            paint.setStrokeWidth(dp(frame >= 5 ? 3 : 2));
            canvas.drawPath(path, paint);
            if (frame == 6 || frame == 8) {
                paint.setColor(0xccffffff);
                paint.setStrokeWidth(dp(1));
                canvas.drawPath(path, paint);
            }
        }
    }

    public static void bindBadges(LinearLayout group, int badges) {
        group.removeAllViews();
        LinearLayout row = null;
        int index = 0;
        for (int i = 0; i < HeartGramProfiles.BADGES.length; i++) {
            if ((badges & HeartGramProfiles.BADGES[i]) == 0) {
                continue;
            }
            if (index % 2 == 0) {
                row = new LinearLayout(group.getContext());
                group.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 3));
            }
            TextView badge = i == 0 ? new RainbowBadge(group.getContext()) : new TextView(group.getContext());
            badge.setText(HeartGramProfiles.BADGE_NAMES[i]);
            final int ink = i == 0 ? 0xff23152b : 0xffffffff;
            badge.setTextColor(ink);
            badge.setTextSize(12);
            badge.setPadding(dp(9), dp(5), dp(9), dp(5));
            badge.setGravity(Gravity.CENTER);
            badge.setCompoundDrawablePadding(dp(5));
            badge.setCompoundDrawablesRelative(icon(group.getContext(), i, ink), null, null, null);
            if (i != 0) {
                badge.setBackground(Theme.createRoundRectDrawable(dp(15), BADGE_COLORS[i]));
            }
            row.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 7, 0));
            index++;
        }
    }

    public static CharSequence badgeLabel(Context context, int index, int tint) {
        SpannableString label = new SpannableString("\ufffc  " + HeartGramProfiles.BADGE_NAMES[index]);
        label.setSpan(new ImageSpan(icon(context, index, tint), ImageSpan.ALIGN_BASELINE), 0, 1, 0);
        return label;
    }

    public static CharSequence chatBadges(Context context, int badges, int availableWidth) {
        int count = Integer.bitCount(badges & 31);
        int step = count == 0 ? 0 : Math.min(dp(19), availableWidth / count);
        if (step < dp(9)) {
            return "";
        }
        int size = step - dp(2);
        Drawable[] icons = new Drawable[count];
        int[] colors = new int[count];
        StringBuilder description = new StringBuilder();
        int index = 0;
        for (int i = 0; i < BADGE_ICONS.length; i++) {
            if ((badges & HeartGramProfiles.BADGES[i]) != 0) {
                icons[index] = icon(context, i, i == 0 ? 0xff23152b : 0xffffffff);
                colors[index] = i == 0 ? RAINBOW[0] : BADGE_COLORS[i];
                if (index++ != 0) {
                    description.append(", ");
                }
                description.append(HeartGramProfiles.BADGE_NAMES[i]);
            }
        }
        ReplacementSpan span = new ReplacementSpan() {
            private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final LinearGradient rainbow = new LinearGradient(0, 0, size, size, RAINBOW, null, Shader.TileMode.CLAMP);

            @Override
            public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
                return step * count;
            }

            @Override
            public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
                float offsetY = y + (paint.ascent() + paint.descent() - size) / 2f;
                int saved = canvas.save();
                canvas.translate(x + dp(2), offsetY);
                int iconInset = Math.max(1, size / 6);
                for (int i = 0; i < icons.length; i++) {
                    background.setColor(colors[i]);
                    background.setShader(colors[i] == RAINBOW[0] ? rainbow : null);
                    background.setAlpha(paint.getAlpha());
                    canvas.drawRoundRect(0, 0, size, size, dp(5), dp(5), background);
                    icons[i].setAlpha(paint.getAlpha());
                    icons[i].setBounds(iconInset, iconInset, size - iconInset, size - iconInset);
                    icons[i].draw(canvas);
                    canvas.translate(step, 0);
                }
                canvas.restoreToCount(saved);
            }
        };
        if (Build.VERSION.SDK_INT >= 30) {
            span.setContentDescription(description);
        }
        SpannableString text = new SpannableString("\ufffc");
        text.setSpan(span, 0, text.length(), 0);
        return text;
    }

    private static Drawable icon(Context context, int index, int tint) {
        Drawable icon = context.getDrawable(BADGE_ICONS[index]).mutate();
        icon.setTint(tint);
        icon.setBounds(0, 0, dp(16), dp(16));
        return icon;
    }

    private static final class RainbowBadge extends TextView {
        private final Matrix transform = new Matrix();
        private final Shader rainbow = new LinearGradient(0, 0, dp(105), 0, RAINBOW, null, Shader.TileMode.REPEAT);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private RainbowBadge(Context context) {
            super(context);
            backgroundPaint.setShader(rainbow);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            transform.setTranslate(dp(105) * ((SystemClock.uptimeMillis() % 2400) / 2400f), 0);
            rainbow.setLocalMatrix(transform);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(15), dp(15), backgroundPaint);
            super.onDraw(canvas);
            if (isShown()) {
                postInvalidateOnAnimation();
            }
        }
    }

    private HeartGramProfileArt() {
    }
}
