package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;

import androidx.core.graphics.ColorUtils;

import org.telegram.utils.settings.HeartGramProfiles;

/** Paint for a sender's public bubble style, adjusted to the viewer's theme. */
public final class HeartGramBubble {
    private static final int[] RAINBOW = {0xfff47b87, 0xffffb169, 0xfff7da67,
            0xff83ce9d, 0xff79c9ee, 0xffbc96f6, 0xfff47b87};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix gradientTransform = new Matrix();
    private LinearGradient gradient;
    private int previousStyle = HeartGramProfiles.BUBBLE_THEME;
    private int previousTextColor;
    private boolean previousSelected;

    public Paint paint(int style, int textColor, boolean selected, float left, int alpha) {
        textColor |= 0xff000000;
        if (style != previousStyle || textColor != previousTextColor || selected != previousSelected) {
            previousStyle = style;
            previousTextColor = textColor;
            previousSelected = selected;
            if (style == HeartGramProfiles.BUBBLE_RGB) {
                int[] colors = new int[RAINBOW.length];
                for (int i = 0; i < colors.length; i++) {
                    colors[i] = readable(RAINBOW[i], textColor, selected);
                }
                gradient = new LinearGradient(0, 0, dp(180), 0, colors, null, Shader.TileMode.REPEAT);
                paint.setColor(Color.WHITE);
            } else {
                gradient = null;
                paint.setColor(readable(style | 0xff000000, textColor, selected));
            }
            paint.setShader(gradient);
        }
        if (gradient != null) {
            gradientTransform.setTranslate(left + dp(180) * (SystemClock.uptimeMillis() % 4000) / 4000f, 0);
            gradient.setLocalMatrix(gradientTransform);
        }
        paint.setAlpha(alpha);
        return paint;
    }

    public static int readable(int color, int textColor, boolean selected) {
        color |= 0xff000000;
        textColor |= 0xff000000;
        if (selected) {
            color = ColorUtils.blendARGB(color, textColor, .13f);
        }
        if (ColorUtils.calculateContrast(textColor, color) >= 4.5) {
            return color;
        }
        int target = ColorUtils.calculateContrast(textColor, Color.WHITE) >= 4.5 ? Color.WHITE : Color.BLACK;
        float low = 0, high = 1;
        for (int i = 0; i < 8; i++) {
            float midpoint = (low + high) / 2;
            if (ColorUtils.calculateContrast(textColor, ColorUtils.blendARGB(color, target, midpoint)) < 4.5) {
                low = midpoint;
            } else {
                high = midpoint;
            }
        }
        return ColorUtils.blendARGB(color, target, high);
    }
}
