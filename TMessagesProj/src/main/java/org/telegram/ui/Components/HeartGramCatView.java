package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public final class HeartGramCatView extends View {
    private static final int[] PHRASES = {R.string.HeartGramMeow, R.string.HeartGramPrr, R.string.HeartGramPurr,
            R.string.HeartGramCatPaw};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint speechPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF bubble = new RectF();
    private final RectF photoBounds = new RectF();
    private final Bitmap photo;
    private final Rect perchBounds = new Rect();
    private final Point catOrigin = new Point();
    private final float touchSlop;
    private int topInset, bottomInset, perchInset, phraseIndex = -1;
    private float x, y, vx, vy, downX, downY, grabX, grabY;
    private long lastFrame, reactionStart, perchStart;
    private boolean facingLeft;
    private boolean initialized, holding, dragging, movedFromPerch;
    private ViewGroup hotBar;
    private View perchedOn;
    private float perchOffset;
    private VelocityTracker velocity;

    public HeartGramCatView(Context context) {
        super(context);
        Drawable artwork = getResources().getDrawable(R.drawable.heartgram_cat, context.getTheme());
        photo = Bitmap.createBitmap(144, 156, Bitmap.Config.ARGB_8888);
        artwork.setBounds(0, 0, photo.getWidth(), photo.getHeight());
        artwork.draw(new Canvas(photo));
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        speechPaint.setTextSize(dp(12));
        speechPaint.setTypeface(AndroidUtilities.bold());
        setClickable(true);
        setContentDescription(LocaleController.getString(R.string.HeartGramCat));
    }

    public void setEdges(int top, int bottom, int perch) {
        if (topInset == top && bottomInset == bottom && perchInset == perch) {
            return;
        }
        topInset = top;
        bottomInset = bottom;
        perchInset = perch;
        if (initialized) {
            if (!movedFromPerch) {
                y = perchY();
            }
            clampPosition();
        }
        invalidate();
    }

    public void setHotBar(ViewGroup hotBar) {
        this.hotBar = hotBar;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (!initialized && w > 0 && h > 0) {
            initialized = true;
            x = w * .75f;
            y = perchY();
        } else if (!movedFromPerch) {
            x = w * .75f;
            y = perchY();
        }
        clampPosition();
    }

    private float perchY() {
        return bottomEdge() - perchInset + dp(11);
    }

    private float topEdge() {
        return topInset + dp(30);
    }

    private float bottomEdge() {
        return Math.max(topEdge(), getHeight() - bottomInset - dp(26));
    }

    private void clampPosition() {
        x = Math.max(dp(32), Math.min(getWidth() - dp(34), x));
        y = Math.max(topEdge(), Math.min(bottomEdge(), y));
    }

    private boolean getPerchBounds(View view) {
        if (!view.isShown() || view.getAlpha() < .5f || !view.getGlobalVisibleRect(perchBounds)) {
            return false;
        }
        return perchBounds.top - catOrigin.y + dp(2) >= topEdge() + dp(23);
    }

    private View findButton(View view, float pawX, float pawY) {
        if (view == this || !view.isShown() || view.getAlpha() < .5f || !view.getGlobalVisibleRect(perchBounds)
                || pawX < perchBounds.left - dp(5) || pawX > perchBounds.right + dp(5)
                || pawY < perchBounds.top - dp(25) || pawY > perchBounds.bottom + dp(4)) {
            return null;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View button = findButton(group.getChildAt(i), pawX, pawY);
                if (button != null) {
                    return button;
                }
            }
        }
        if (view.isClickable() && view.isEnabled() && view.getHeight() >= dp(24)
                && view.getHeight() <= dp(80) && view.getWidth() >= dp(24) && view.getWidth() < getWidth() - dp(32)
                && getPerchBounds(view)) {
            return view;
        }
        return null;
    }

    private boolean land(View view) {
        if (view == null || !getPerchBounds(view)) {
            return false;
        }
        perchedOn = view;
        perchOffset = Math.max(0, Math.min(1, (x + catOrigin.x - perchBounds.left) / perchBounds.width()));
        vx = vy = 0;
        perchStart = SystemClock.uptimeMillis();
        return followPerch(perchStart);
    }

    private boolean followPerch(long now) {
        if (!getPerchBounds(perchedOn)) {
            perchedOn = null;
            return false;
        }
        float available = Math.max(0, perchBounds.width() / 2f - dp(13));
        float center = perchBounds.exactCenterX() - catOrigin.x;
        float start = perchBounds.left - catOrigin.x + perchBounds.width() * perchOffset;
        float home = Math.max(center - available, Math.min(center + available, start));
        float left = Math.max(center - available, home - dp(62));
        float right = Math.min(center + available, home + dp(62));
        float range = right - left;
        if (range > dp(3)) {
            float walk = (home - left + dp(23) * (now - perchStart) / 1000f) % (2f * range);
            facingLeft = walk >= range;
            x = left + (facingLeft ? 2f * range - walk : walk);
        } else {
            x = center;
        }
        y = perchBounds.top - catOrigin.y - dp(21);
        clampPosition();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!initialized || getAlpha() <= .05f) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float seconds = lastFrame == 0 ? 0 : Math.min(.035f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean perched = false;
        if (!holding && getGlobalVisibleRect(perchBounds, catOrigin)) {
            if (!movedFromPerch && hotBar != null && land(hotBar)) {
                movedFromPerch = true;
                perched = true;
            } else if (perchedOn != null) {
                perched = followPerch(now);
            }
        }
        if (!holding && !perched && movedFromPerch && (Math.abs(vx) > 1 || Math.abs(vy) > 1 || y < bottomEdge() - 1)) {
            float lastY = y;
            if (Math.abs(vx) > dp(18)) {
                facingLeft = vx < 0;
            }
            x += vx * seconds;
            y += vy * seconds;
            vy += dp(1100) * seconds;
            vx *= (float) Math.pow(.96, seconds * 60);
            if (vy >= 0 && hotBar != null && getPerchBounds(hotBar)
                    && lastY + dp(21) <= perchBounds.top - catOrigin.y
                    && y + dp(21) >= perchBounds.top - catOrigin.y
                    && x + catOrigin.x >= perchBounds.left && x + catOrigin.x <= perchBounds.right) {
                land(hotBar);
            } else if (y >= bottomEdge() && vy > 0) {
                vy = vy < dp(105) ? 0 : -vy * .43f;
                vx *= .62f;
            } else if (y < topEdge() && vy < 0) {
                vy = -vy * .45f;
            }
            if ((x < dp(32) && vx < 0) || (x > getWidth() - dp(34) && vx > 0)) {
                vx = -vx * .56f;
            }
            clampPosition();
        }

        if (perched && now - Math.max(perchStart, reactionStart) > 9_000) {
            react(now);
        }
        float age = (now - reactionStart) / 1000f;
        float bounce = age < .45f ? (float) Math.sin(age / .45f * Math.PI) * dp(5) : 0f;
        int saved = canvas.save();
        float step = perched ? dp(1.5f) * (float) Math.abs(Math.sin((now - perchStart) / 140f)) : 0;
        canvas.translate(x, y - bounce - step);
        canvas.rotate(Math.max(-12, Math.min(12, vx / dp(55))));
        canvas.scale(facingLeft ? 1f : -1f, 1f);
        float height = dp(49), width = height * photo.getWidth() / photo.getHeight();
        photoBounds.set(-width / 2, dp(23) - height, width / 2, dp(23));
        canvas.drawBitmap(photo, null, photoBounds, paint);
        canvas.restoreToCount(saved);

        if (phraseIndex >= 0 && age < 2.15f) {
            drawReaction(canvas, age);
        }
        if (isShown() && getWindowVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    private void drawReaction(Canvas canvas, float age) {
        float alpha = Math.min(1f, (2.15f - age) / .35f);
        String text = LocaleController.getString(PHRASES[phraseIndex]);
        float w = speechPaint.measureText(text) + dp(19);
        float left = Math.max(dp(6), Math.min(getWidth() - w - dp(6), x - w / 2f));
        float top = Math.max(topInset + dp(4), y - dp(63));
        bubble.set(left, top, left + w, top + dp(24));
        paint.setColor(0xffffead4);
        paint.setAlpha((int) (255 * alpha));
        canvas.drawRoundRect(bubble, dp(12), dp(12), paint);
        speechPaint.setColor(0xff603e35);
        speechPaint.setAlpha((int) (255 * alpha));
        canvas.drawText(text, left + dp(9), top + dp(16), speechPaint);
        for (int i = 0; i < 4; i++) {
            float t = age - i * .16f;
            if (t < 0) {
                continue;
            }
            float side = i % 2 == 0 ? -1 : 1;
            paint.setColor(i % 2 == 0 ? 0xffdf6684 : 0xffee93a0);
            paint.setAlpha((int) (255 * Math.max(0, 1 - t / 1.68f)));
            heart(canvas, x + side * dp(24 + i * 2 + t * 9), y - dp(9 + t * 33), dp(4 + i % 3), paint);
        }
        paint.setAlpha(255);
    }

    private void heart(Canvas canvas, float x, float y, float size, Paint ink) {
        path.reset();
        path.moveTo(x, y - .45f * size);
        path.cubicTo(x - size, y - 1.4f * size, x - 1.45f * size, y, x, y + size);
        path.cubicTo(x + 1.45f * size, y, x + size, y - 1.4f * size, x, y - .45f * size);
        path.close();
        canvas.drawPath(path, ink);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float px = event.getX();
        float py = event.getY();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (getAlpha() <= .05f || Math.abs(px - x) > dp(35) || Math.abs(py - y) > dp(29)) {
                return false;
            }
            holding = true;
            dragging = false;
            vx = vy = 0;
            downX = px;
            downY = py;
            grabX = x - px;
            grabY = y - py;
            velocity = VelocityTracker.obtain();
        }
        if (!holding) {
            return false;
        }
        velocity.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                dragging |= Math.hypot(px - downX, py - downY) > touchSlop;
                if (dragging) {
                    movedFromPerch = true;
                    perchedOn = null;
                    x = px + grabX;
                    y = py + grabY;
                    clampPosition();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    velocity.computeCurrentVelocity(1000, dp(1900));
                    vx = velocity.getXVelocity();
                    vy = velocity.getYVelocity();
                    if (getGlobalVisibleRect(perchBounds, catOrigin) && getParent() instanceof View) {
                        float pawX = x + catOrigin.x;
                        float pawY = y + catOrigin.y + dp(23);
                        View button = findButton((View) getParent(), pawX, pawY);
                        if (!land(button) && hotBar != null && getPerchBounds(hotBar)
                                && pawX >= perchBounds.left && pawX <= perchBounds.right
                                && pawY >= perchBounds.top - dp(26) && pawY <= perchBounds.bottom) {
                            land(hotBar);
                        }
                    }
                } else {
                    performClick();
                }
                releaseTouch();
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseTouch();
                break;
        }
        return true;
    }

    private void releaseTouch() {
        holding = false;
        velocity.recycle();
        velocity = null;
        invalidate();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        react(SystemClock.uptimeMillis());
        announceForAccessibility(LocaleController.getString(PHRASES[phraseIndex]));
        invalidate();
        return true;
    }

    private void react(long now) {
        phraseIndex = (phraseIndex + 1) % PHRASES.length;
        reactionStart = now;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (holding) {
            releaseTouch();
        }
        lastFrame = 0;
        super.onDetachedFromWindow();
    }
}
