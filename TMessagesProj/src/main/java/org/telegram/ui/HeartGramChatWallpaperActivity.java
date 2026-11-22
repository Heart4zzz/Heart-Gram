package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.utils.settings.HeartGramChatWallpaper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class HeartGramChatWallpaperActivity extends UniversalFragment {
    private static final int PICK_PHOTO = 9273;
    private static final int FOLLOW_THEME = 1, OTHER_THEME = 2, PHOTO = 3, SOLID = 4;
    private FrameLayout preview;
    private ImageView image;
    private int previewRequest;
    private boolean importing;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.HeartGramIndependentWallpaper);
    }

    @Override
    public View createView(Context context) {
        preview = new FrameLayout(context);
        preview.setPadding(dp(16), dp(8), dp(16), dp(8));
        image = new ImageView(context);
        image.setImageDrawable(activeWallpaperPreview());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(Theme.createRoundRectDrawable(dp(24), getThemedColor(Theme.key_chat_wallpaper)));
        image.setClipToOutline(true);
        preview.addView(image, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.TOP));
        updatePreview();
        return super.createView(context);
    }

    private void updatePreview() {
        final int request = ++previewRequest;
        Utilities.themeQueue.postRunnable(() -> {
            Drawable sample = HeartGramChatWallpaper.createDrawable();
            AndroidUtilities.runOnUIThread(() -> {
                if (request == previewRequest && image != null) image.setImageDrawable(sample == null ? activeWallpaperPreview() : sample);
            });
        });
    }

    private Drawable activeWallpaperPreview() {
        Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
        Drawable.ConstantState state = wallpaper == null ? null : wallpaper.getConstantState();
        return state == null ? new ColorDrawable(getThemedColor(Theme.key_chat_wallpaper)) : state.newDrawable();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Theme.ThemeInfo theme = HeartGramChatWallpaper.theme();
        items.add(UItem.asCustom(preview));
        items.add(UItem.asRadio(FOLLOW_THEME, getString(R.string.HeartGramWallpaperFollowTheme)).setChecked(!HeartGramChatWallpaper.isSet()));
        items.add(UItem.asRadio(OTHER_THEME, getString(R.string.HeartGramWallpaperFromTheme), theme == null ? null : theme.getName()).setChecked(theme != null));
        items.add(UItem.asRadio(PHOTO, getString(R.string.HeartGramWallpaperOwnPhoto)).setChecked(HeartGramChatWallpaper.isPhoto()));
        items.add(UItem.asRadio(SOLID, getString(R.string.HeartGramWallpaperSolidColor)).setChecked(HeartGramChatWallpaper.color() != null));
        items.add(UItem.asShadow(getString(R.string.HeartGramIndependentWallpaperInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (importing) return;
        switch (item.id) {
            case FOLLOW_THEME:
                if (HeartGramChatWallpaper.isSet()) {
                    HeartGramChatWallpaper.followTheme();
                    apply();
                }
                break;
            case OTHER_THEME:
                chooseTheme();
                break;
            case PHOTO:
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/*"), PICK_PHOTO);
                break;
            case SOLID:
                chooseColor();
                break;
        }
    }

    private void chooseTheme() {
        LinkedHashMap<String, Theme.ThemeInfo> installed = new LinkedHashMap<>();
        for (Theme.ThemeInfo theme : Theme.themes) {
            if (theme.assetName != null || theme.pathToFile != null && new File(theme.pathToFile).isFile()
                    || theme.pathToFile == null && theme.info == null) {
                installed.put(theme.getKey(), theme);
            }
        }
        ArrayList<Theme.ThemeInfo> themes = new ArrayList<>(installed.values());
        String[] names = new String[themes.size()];
        for (int i = 0; i < names.length; i++) names[i] = themes.get(i).getName();
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramWallpaperFromTheme))
                .setItems(names, (dialog, which) -> {
                    HeartGramChatWallpaper.selectTheme(themes.get(which));
                    apply();
                }).setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private void chooseColor() {
        ColorPicker picker = new ColorPicker(getContext(), false, (color, index, apply) -> {});
        picker.setType(0, false, 1, 1, false, 0, false);
        Integer previous = HeartGramChatWallpaper.color();
        picker.setColor(previous == null ? getThemedColor(Theme.key_chat_wallpaper) : previous, 0);
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramWallpaperSolidColor))
                .setView(picker, 276).setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    HeartGramChatWallpaper.selectColor(picker.getColor());
                    apply();
                }).create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_PHOTO || resultCode != Activity.RESULT_OK || data == null || data.getData() == null || importing) return;
        importing = true;
        Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            boolean saved;
            try {
                HeartGramChatWallpaper.importPhoto(uri);
                saved = true;
            } catch (Exception error) {
                saved = false;
            }
            final boolean imported = saved;
            AndroidUtilities.runOnUIThread(() -> {
                importing = false;
                if (imported) {
                    HeartGramChatWallpaper.selectPhoto();
                    apply();
                } else if (getContext() != null) {
                    showDialog(new AlertDialog.Builder(getContext()).setMessage(getString(R.string.HeartGramWallpaperPhotoError))
                            .setPositiveButton(getString(R.string.OK), null).create());
                }
            });
        });
    }

    private void apply() {
        Theme.reloadWallpaper(true);
        if (listView != null) {
            listView.adapter.update(false);
            updatePreview();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
