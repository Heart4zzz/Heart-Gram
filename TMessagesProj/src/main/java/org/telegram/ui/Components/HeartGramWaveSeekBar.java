package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.widget.SeekBar;

/** Keeps SeekBar input and accessibility while drawing a wavy elapsed track. */
public final class HeartGramWaveSeekBar extends SeekBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();
    private final int activeColor;
    private final int trackColor;
    private boolean playing;

    public HeartGramWaveSeekBar(Context context, int activeColor, int trackColor) {
        super(context);
        this.activeColor = activeColor;
        this.trackColor = trackColor;
    }

    public void setPlaying(boolean playing) {
        if (this.playing != playing) {
            this.playing = playing;
            invalidate();
        }
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        final int saved = canvas.save();
        canvas.translate(getPaddingLeft(), getHeight() / 2f);
        final int width = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            canvas.translate(width, 0);
            canvas.scale(-1, 1);
        }
        final float elapsed = width * getProgress() / (float) Math.max(1, getMax());
        paint.setColor(trackColor);
        paint.setStrokeWidth(dp(4));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(0, 0, width, 0, paint);

        if (elapsed > 0) {
            final float wavelength = dp(26);
            final float phase = playing ? (SystemClock.uptimeMillis() % 900) / 900f : 0;
            final float step = Math.max(1, dp(1));
            wave.reset();
            wave.moveTo(0, 0);
            for (float x = step; x < elapsed; x += step) {
                float fade = Math.min(1, Math.min(x, elapsed - x) / dp(6));
                float y = (float) Math.sin((x / wavelength - phase) * Math.PI * 2) * dp(3) * fade;
                wave.lineTo(x, y);
            }
            wave.lineTo(elapsed, 0);
            paint.setColor(activeColor);
            canvas.drawPath(wave, paint);
        }
        paint.setColor(activeColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(elapsed, 0, dp(isPressed() ? 8 : 6), paint);
        canvas.restoreToCount(saved);
        if (playing && isShown()) {
            postInvalidateOnAnimation();
        }
    }
}
