package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.utils.settings.HeartGramFun;

import java.util.Random;
import java.util.function.BooleanSupplier;

public final class HeartGramShakeEffect implements SensorEventListener {
    private final Activity activity;
    private final FrameLayout root;
    private final BooleanSupplier canCapture;
    private final SensorManager sensors;
    private final Sensor accelerometer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean foreground, listening, hasGravity, capturing;
    private int captureId;
    private float gx, gy, gz;
    private long impulseAt, lastShake;
    private FrameLayout overlay;
    private Pieces pieces;

    public HeartGramShakeEffect(Activity activity, FrameLayout root, BooleanSupplier canCapture) {
        this.activity = activity;
        this.root = root;
        this.canCapture = canCapture;
        sensors = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void resume() {
        foreground = true;
        update();
    }

    public void pause() {
        foreground = false;
        update();
    }

    public void update() {
        boolean wanted = foreground && HeartGramFun.scatter.get() && accelerometer != null;
        if (wanted && !listening) {
            hasGravity = false;
            impulseAt = 0;
            listening = sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler);
        } else if (!wanted && listening) {
            sensors.unregisterListener(this);
            listening = false;
        }
        if (!wanted) {
            restore();
        }
    }

    public boolean isScattered() {
        return overlay != null;
    }

    public void restore() {
        captureId++;
        capturing = false;
        if (overlay != null) {
            root.removeView(overlay);
            overlay = null;
            pieces = null;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!listening || !foreground) {
            return;
        }
        if (!mayCapture()) {
            if (overlay != null || capturing) {
                restore();
            }
            return;
        }
        float ax = event.values[0];
        float ay = event.values[1];
        float az = event.values[2];
        if (!hasGravity) {
            gx = ax;
            gy = ay;
            gz = az;
            hasGravity = true;
        }
        gx += .16f * (ax - gx);
        gy += .16f * (ay - gy);
        gz += .16f * (az - gz);
        if (pieces != null) {
            setGravity(pieces);
        }
        float dx = ax - gx;
        float dy = ay - gy;
        float dz = az - gz;
        if (dx * dx + dy * dy + dz * dz < 11.5f * 11.5f) {
            return;
        }
        final long now = SystemClock.uptimeMillis();
        long gap = now - impulseAt;
        if (gap < 75) {
            return;
        }
        impulseAt = now;
        if (gap <= 440 && now - lastShake > 780) {
            lastShake = now;
            if (pieces == null) {
                scatter();
            } else {
                pieces.kick();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private boolean mayCapture() {
        return canCapture.getAsBoolean()
                && (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) == 0;
    }

    private void setGravity(Pieces stage) {
        float x = -gx;
        float y = gy;
        switch (activity.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                x = -gy;
                y = -gx;
                break;
            case Surface.ROTATION_180:
                x = gx;
                y = -gy;
                break;
            case Surface.ROTATION_270:
                x = gy;
                y = gx;
                break;
        }
        stage.gravityX = x * dp(115);
        stage.gravityY = y * dp(115);
    }

    private void scatter() {
        if (capturing || !root.hasWindowFocus() || root.getWidth() < 4 || root.getHeight() < 4) {
            return;
        }
        int width = root.getWidth();
        int height = root.getHeight();
        final float scale = Math.min(1f, 1320f / Math.max(width, height));
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(Math.round(width * scale), Math.round(height * scale), Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError ignored) {
            return;
        }
        int id = ++captureId;
        capturing = true;
        if (Build.VERSION.SDK_INT >= 26) {
            int[] origin = new int[2];
            root.getLocationInWindow(origin);
            Rect source = new Rect(origin[0], origin[1], origin[0] + width, origin[1] + height);
            try {
                PixelCopy.request(activity.getWindow(), source, bitmap, result -> {
                    if (id == captureId && result != PixelCopy.SUCCESS && mayCapture()) {
                        captureView(bitmap, scale, id);
                    } else {
                        finishCapture(bitmap, id, result == PixelCopy.SUCCESS);
                    }
                }, handler);
                return;
            } catch (IllegalArgumentException ignored) {
                // The window surface may be unavailable during an activity transition.
            }
        }
        captureView(bitmap, scale, id);
    }

    private void captureView(Bitmap bitmap, float scale, int id) {
        boolean copied = false;
        if (id == captureId && mayCapture()) {
            try {
                Canvas canvas = new Canvas(bitmap);
                canvas.scale(scale, scale);
                root.draw(canvas);
                copied = true;
            } catch (RuntimeException ignored) {
                // Some hardware-only views cannot be rendered into a bitmap on older devices.
            }
        }
        finishCapture(bitmap, id, copied);
    }

    private void finishCapture(Bitmap bitmap, int id, boolean copied) {
        if (id != captureId || !copied || !foreground || !listening || !mayCapture()) {
            bitmap.recycle();
            if (id == captureId) {
                capturing = false;
            }
            return;
        }
        capturing = false;
        overlay = new FrameLayout(activity);
        overlay.setClickable(true);
        overlay.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        pieces = new Pieces(activity, bitmap);
        setGravity(pieces);
        overlay.addView(pieces, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TextView restore = new TextView(activity);
        restore.setText(getString(R.string.HeartGramRestoreUi));
        restore.setTextSize(15);
        restore.setTypeface(AndroidUtilities.bold());
        restore.setGravity(Gravity.CENTER);
        restore.setPadding(dp(20), 0, dp(20), 0);
        restore.setTextColor(Theme.getColor(Theme.key_chats_actionIcon));
        restore.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(22),
                Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground)));
        restore.setOnClickListener(v -> restore());
        overlay.addView(restore, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                12, 0, 12, 36));
        root.addView(overlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        restore.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
    }

    private static final class Pieces extends View {
        private final Bitmap snapshot;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF target = new RectF();
        private final Random random = new Random();
        private final Tile[] tiles;
        private long lastFrame;
        private float gravityX, gravityY;

        private Pieces(Context context, Bitmap snapshot) {
            super(context);
            this.snapshot = snapshot;
            final int columns = snapshot.getWidth() > snapshot.getHeight() ? 9 : 6;
            final int rows = snapshot.getWidth() > snapshot.getHeight() ? 6 : 10;
            tiles = new Tile[columns * rows];
            for (int i = 0; i < tiles.length; i++) {
                int column = i % columns;
                int row = i / columns;
                tiles[i] = new Tile(new Rect(column * snapshot.getWidth() / columns, row * snapshot.getHeight() / rows,
                        (column + 1) * snapshot.getWidth() / columns, (row + 1) * snapshot.getHeight() / rows));
            }
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private void kick() {
            for (Tile tile : tiles) {
                tile.vx = (random.nextFloat() - .5f) * dp(920);
                tile.vy = (random.nextFloat() - .7f) * dp(880);
                tile.av = (random.nextFloat() - .5f) * 610f;
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            for (Tile tile : tiles) {
                tile.w = w * tile.source.width() / (float) snapshot.getWidth();
                tile.h = h * tile.source.height() / (float) snapshot.getHeight();
                tile.x = oldw > 0 ? tile.x * w / oldw : w * tile.source.exactCenterX() / snapshot.getWidth();
                tile.y = oldh > 0 ? tile.y * h / oldh : h * tile.source.exactCenterY() / snapshot.getHeight();
            }
            if (oldw <= 0 || oldh <= 0) {
                kick();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final long now = SystemClock.uptimeMillis();
            final float dt = lastFrame == 0 ? 0 : Math.min(.034f, (now - lastFrame) / 1000f);
            lastFrame = now;
            for (Tile tile : tiles) {
                tile.vx = (tile.vx + gravityX * dt) * .995f;
                tile.vy = (tile.vy + gravityY * dt) * .995f;
                tile.x += tile.vx * dt;
                tile.y += tile.vy * dt;
                float left = tile.w * .5f;
                float top = tile.h * .5f;
                if ((tile.x < left && tile.vx < 0) || (tile.x > getWidth() - left && tile.vx > 0)) {
                    tile.vx *= -.52f;
                    tile.av *= .85f;
                }
                if ((tile.y < top && tile.vy < 0) || (tile.y > getHeight() - top && tile.vy > 0)) {
                    tile.vy *= -.45f;
                    tile.av *= .87f;
                }
                tile.x = Math.max(left, Math.min(getWidth() - left, tile.x));
                tile.y = Math.max(top, Math.min(getHeight() - top, tile.y));
                tile.angle += tile.av * dt;
                target.set(tile.x - left, tile.y - top, tile.x + left, tile.y + top);
                int saved = canvas.save();
                canvas.rotate(tile.angle, tile.x, tile.y);
                canvas.drawBitmap(snapshot, tile.source, target, paint);
                canvas.restoreToCount(saved);
            }
            postInvalidateOnAnimation();
        }
    }

    private static final class Tile {
        private final Rect source;
        private float x, y, w, h, vx, vy, angle, av;

        private Tile(Rect source) {
            this.source = source;
        }
    }
}
