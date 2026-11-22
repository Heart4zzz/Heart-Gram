package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

public final class HeartGramIconsActivity extends UniversalFragment {
    private static final int PICK_IMAGE = 9251;
    private static final String CUSTOM_SHORTCUT = "heartgram_custom_launcher";
    private LinearLayout cards;
    private boolean importing;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.HeartGramAppIcons);
    }

    @Override
    public View createView(Context context) {
        cards = new LinearLayout(context);
        cards.setPadding(dp(10), dp(12), dp(10), dp(6));
        iconStrip = new HorizontalScrollView(context);
        iconStrip.setHorizontalScrollBarEnabled(false);
        iconStrip.setClipToPadding(false);
        iconStrip.addView(cards, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        renderIcons();
        return super.createView(context);
    }

    private HorizontalScrollView iconStrip;

    private void renderIcons() {
        Context context = cards.getContext();
        String[] names = context.getResources().getStringArray(R.array.heartgram_launcher_icon_names);
        int selected = LauncherIconController.enabledHeartGramIcon();
        cards.removeAllViews();
        for (int i = 0; i < LauncherIconController.HEARTGRAM_ICONS.length; i++) {
            final int index = i;
            LinearLayout card = new LinearLayout(context);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setContentDescription(names[i]);
            card.setSelected(i == selected);
            FrameLayout artwork = new FrameLayout(context);
            if (i == selected) {
                GradientDrawable background = new GradientDrawable();
                background.setColor(Color.TRANSPARENT);
                background.setCornerRadius(dp(21));
                background.setStroke(dp(2), getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
                artwork.setBackground(background);
            }
            ImageView image = new ImageView(context);
            image.setImageResource(LauncherIconController.HEARTGRAM_ICONS[i]);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            artwork.addView(image, LayoutHelper.createFrame(64, 64, Gravity.CENTER));
            card.addView(artwork, LayoutHelper.createLinear(76, 76));
            TextView title = new TextView(context);
            title.setText(names[i]);
            title.setTextSize(13);
            title.setMaxLines(2);
            title.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            title.setTextColor(getThemedColor(i == selected ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText));
            card.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 5, 0, 0));
            cards.addView(card, LayoutHelper.createLinear(92, LayoutHelper.WRAP_CONTENT));
            card.setOnClickListener(v -> {
                if (index != LauncherIconController.enabledHeartGramIcon()) {
                    LauncherIconController.setHeartGramIcon(index);
                    renderIcons();
                }
            });
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (iconStrip != null) items.add(UItem.asCustom(iconStrip));
        items.add(UItem.asButton(1, R.drawable.msg_gallery, getString(R.string.HeartGramOwnIcon)));
        if (getContext() != null && customFile(getContext()).getBaseFile().isFile()) {
            items.add(UItem.asButton(2, getString(R.string.HeartGramPinIcon)));
        }
        items.add(UItem.asShadow(getString(R.string.HeartGramIconDescription)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (importing || getContext() == null) return;
        if (item.id == 1 || item.id == 2) {
            ShortcutManager shortcuts = supportedShortcuts(getContext());
            if (shortcuts == null) {
                showMessage(R.string.HeartGramIconLauncherUnavailable);
            } else if (item.id == 1) {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/*"), PICK_IMAGE);
            } else {
                Bitmap image = BitmapFactory.decodeFile(customFile(getContext()).getBaseFile().getPath());
                if (image != null) pinIcon(image);
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_IMAGE || resultCode != Activity.RESULT_OK || data == null || data.getData() == null || getContext() == null) return;
        importing = true;
        Context context = getContext().getApplicationContext();
        Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap image = null;
            try {
                image = saveCustomImage(context, uri);
            } catch (IOException | RuntimeException ignored) {
            }
            Bitmap result = image;
            AndroidUtilities.runOnUIThread(() -> {
                importing = false;
                if (getContext() != null) {
                    if (result == null) showMessage(R.string.HeartGramInvalidIcon);
                    else pinIcon(result);
                    listView.adapter.update(false);
                }
            });
        });
    }

    private static AtomicFile customFile(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "heartgram_launcher_icon.png"));
    }

    private static Bitmap saveCustomImage(Context context, Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(stream, null, options);
        }
        int edge = Math.max(options.outWidth, options.outHeight);
        if (options.outWidth <= 0 || options.outHeight <= 0 || edge > 32768) throw new IOException("Invalid image size");
        options.inSampleSize = 1;
        while (edge / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap source;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(stream, null, options);
        }
        if (source == null) throw new IOException("Invalid image");
        Bitmap icon = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        float scale = 256f / Math.max(source.getWidth(), source.getHeight());
        float width = source.getWidth() * scale;
        float height = source.getHeight() * scale;
        new android.graphics.Canvas(icon).drawBitmap(source, null, new android.graphics.RectF(
                (256 - width) / 2, (256 - height) / 2, (256 + width) / 2, (256 + height) / 2),
                new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        AtomicFile file = customFile(context);
        FileOutputStream stream = file.startWrite();
        try {
            if (!icon.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("Could not encode image");
            file.finishWrite(stream);
        } catch (IOException | RuntimeException error) {
            file.failWrite(stream);
            throw error;
        }
        return icon;
    }

    private static ShortcutManager supportedShortcuts(Context context) {
        if (Build.VERSION.SDK_INT < 26) return null;
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        return manager != null && manager.isRequestPinShortcutSupported() ? manager : null;
    }

    private void pinIcon(Bitmap image) {
        if (Build.VERSION.SDK_INT < 26 || getContext() == null) return;
        Context context = getContext();
        ShortcutManager manager = supportedShortcuts(context);
        if (manager == null) {
            showMessage(R.string.HeartGramIconLauncherUnavailable);
            return;
        }
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, CUSTOM_SHORTCUT)
                .setShortLabel(getString(R.string.HeartGramAppName))
                .setIcon(Icon.createWithBitmap(image))
                .setIntent(new Intent(context, LaunchActivity.class).setAction(Intent.ACTION_MAIN)).build();
        try {
            for (ShortcutInfo existing : manager.getPinnedShortcuts()) {
                if (CUSTOM_SHORTCUT.equals(existing.getId()) && manager.updateShortcuts(Collections.singletonList(shortcut))) return;
            }
            if (!manager.requestPinShortcut(shortcut, null)) showMessage(R.string.HeartGramIconLauncherUnavailable);
        } catch (IllegalStateException | SecurityException error) {
            showMessage(R.string.HeartGramIconLauncherUnavailable);
        }
    }

    private void showMessage(int text) {
        showDialog(new AlertDialog.Builder(getContext()).setMessage(getString(text)).setPositiveButton(getString(R.string.OK), null).create());
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
