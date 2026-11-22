package org.telegram.utils.settings;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AtomicFile;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class HeartGramChatWallpaper {
    private static final StringSetting selection = StringSetting.of("heartgram_independent_wallpaper", null);
    private static volatile Bitmap customImage;

    public static boolean isSet() {
        return selection.get() != null;
    }

    public static boolean isPhoto() {
        return "photo".equals(selection.get());
    }

    public static Integer color() {
        String value = selection.get();
        return value != null && value.startsWith("color:") ? (int) Long.parseLong(value.substring(6), 16) : null;
    }

    public static Theme.ThemeInfo theme() {
        String value = selection.get();
        return value != null && value.startsWith("theme:") ? Theme.getTheme(value.substring(6)) : null;
    }

    public static void followTheme() {
        selection.set(null);
    }

    public static void selectTheme(Theme.ThemeInfo theme) {
        selection.set("theme:" + theme.getKey());
    }

    public static void selectColor(int color) {
        selection.set("color:" + Integer.toHexString(color | 0xff000000));
    }

    public static Drawable createDrawable() {
        if (isPhoto()) {
            Bitmap image = customImage;
            if (image == null) {
                try (java.io.FileInputStream input = imageFile().openRead()) {
                    image = BitmapFactory.decodeStream(input);
                    customImage = image;
                } catch (IOException ignored) {
                }
            }
            return image == null ? null : new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), image);
        }
        Integer color = color();
        if (color != null) {
            return new ColorDrawable(color);
        }
        Theme.ThemeInfo theme = theme();
        return theme == null ? null : Theme.createHeartGramThemeWallpaper(theme);
    }

    public static void importPhoto(Uri uri) throws IOException {
        Bitmap image = ImageLoader.loadBitmap(null, uri, 1600, 2560, true);
        if (image == null) {
            throw new IOException("Invalid wallpaper image");
        }
        AtomicFile file = imageFile();
        FileOutputStream output = file.startWrite();
        try {
            if (!image.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                throw new IOException("Could not write wallpaper");
            }
            file.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            file.failWrite(output);
            throw error;
        }
        customImage = image;
    }

    public static void selectPhoto() {
        selection.set("photo");
    }

    private static AtomicFile imageFile() {
        return new AtomicFile(new File(ApplicationLoader.getFilesDirFixed(), "heartgram_chat_wallpaper.jpg"));
    }

    private HeartGramChatWallpaper() {
    }
}
