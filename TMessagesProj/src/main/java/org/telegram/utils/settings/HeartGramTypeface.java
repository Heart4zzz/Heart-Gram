package org.telegram.utils.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.AnimatedTextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.WeakHashMap;

public final class HeartGramTypeface {
    public static final int CUSTOM = 4;
    public static final int SYSTEM = 3;
    public enum Preset {
        SERIF(6, "Serif", "serif"),
        CONDENSED(7, "Sans Condensed", "sans-serif-condensed"),
        LIGHT(8, "Sans Light", "sans-serif-light");

        public final int id;
        public final String name;
        private final Typeface face;

        Preset(int id, String name, String family) {
            this.id = id;
            this.name = name;
            face = Typeface.create(family, Typeface.NORMAL);
        }
    }

    private static final Preset[] PRESETS = Preset.values();
    private static final IntSetting selected = IntSetting.of("heartgram_typeface", SYSTEM);
    private static Typeface custom;
    private static Typeface italicBase, italic;
    private static final WeakHashMap<View, Boolean> roots = new WeakHashMap<>();

    public static int selected() {
        int index = selected.get();
        if (index == CUSTOM && hasCustomFont()) return CUSTOM;
        for (Preset preset : PRESETS) {
            if (preset.id == index) return index;
        }
        return SYSTEM;
    }

    public static void select(int index) {
        selected.set(index);
    }

    public static Typeface current() {
        return get(selected());
    }

    public static Typeface title() {
        return selected() == SYSTEM ? AndroidUtilities.bold() : current();
    }

    public static void install(View root) {
        if (roots.put(root, true) == null) {
            root.getViewTreeObserver().addOnGlobalLayoutListener(() -> apply(root));
        }
        apply(root);
    }

    public static void refreshViews() {
        for (View root : roots.keySet().toArray(new View[0])) {
            apply(root);
        }
    }

    private static void apply(View root) {
        Typeface face = selected() == SYSTEM ? null : current();
        if (face != null && italicBase != face) {
            italicBase = face;
            italic = Typeface.create(face, Typeface.ITALIC);
        }
        apply(root, face);
    }

    private static void apply(View view, Typeface face) {
        if (Boolean.TRUE.equals(view.getTag(R.id.heartgram_keep_typeface))) {
            return;
        }
        if (view instanceof TextView && !(((TextView) view).getTransformationMethod() instanceof PasswordTransformationMethod)) {
            TextView text = (TextView) view;
            Typeface next = selectForView(view, text.getTypeface(), face);
            if (next != null) text.setTypeface(next);
        } else if (view instanceof SimpleTextView) {
            SimpleTextView text = (SimpleTextView) view;
            Typeface next = selectForView(view, text.getPaint().getTypeface(), face);
            if (next != null) text.setTypeface(next);
        } else if (view instanceof AnimatedTextView) {
            AnimatedTextView text = (AnimatedTextView) view;
            Typeface next = selectForView(view, text.getPaint().getTypeface(), face);
            if (next != null) text.setTypeface(next);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                apply(group.getChildAt(i), face);
            }
        }
    }

    private static Typeface selectForView(View view, Typeface current, Typeface face) {
        if (current == Typeface.MONOSPACE || current == AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO)) {
            return null;
        }
        Typeface original = (Typeface) view.getTag(R.id.heartgram_original_typeface);
        if (face == null && original == null) {
            return null;
        }
        if (original == null) {
            original = current == null || current == face ? Typeface.DEFAULT : current;
            view.setTag(R.id.heartgram_original_typeface, original);
        }
        Typeface next = face == null ? original : original.isItalic() ? italic : face;
        return next == current ? null : next;
    }

    public static boolean hasCustomFont() {
        return file().isFile();
    }

    public static synchronized Typeface get(int index) {
        if (index == CUSTOM) {
            if (custom == null && file().isFile()) {
                try {
                    custom = Typeface.createFromFile(file());
                } catch (RuntimeException ignored) {
                    return Typeface.DEFAULT;
                }
            }
            return custom == null ? Typeface.DEFAULT : custom;
        }
        for (Preset preset : PRESETS) {
            if (preset.id == index) {
                return preset.face;
            }
        }
        return Typeface.DEFAULT;
    }

    public static synchronized void importFile(Context context, Uri uri) throws IOException {
        File destination = file();
        File candidate = File.createTempFile("heartgram_font", ".tmp", destination.getParentFile());
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(candidate)) {
                if (in == null) {
                    throw new IOException("Font could not be opened");
                }
                final java.io.DataInputStream source = new java.io.DataInputStream(in);
                final int format = source.readInt();
                if (format != 0x00010000 && format != 0x4f54544f && format != 0x74727565) {
                    throw new IOException("Unsupported font format");
                }
                new java.io.DataOutputStream(out).writeInt(format);
                byte[] buffer = new byte[8192];
                int total = 4;
                int size;
                while ((size = source.read(buffer)) != -1) {
                    total += size;
                    if (total > 16 * 1024 * 1024) {
                        throw new IOException("Font exceeds 16 MiB");
                    }
                    out.write(buffer, 0, size);
                }
            }
            Typeface validated = Typeface.createFromFile(candidate);
            if (!candidate.renameTo(destination)) {
                throw new IOException("Font could not be saved");
            }
            custom = validated;
            select(CUSTOM);
        } finally {
            candidate.delete();
        }
    }

    private static File file() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "heartgram_custom_font.ttf");
    }

    private HeartGramTypeface() {
    }
}
