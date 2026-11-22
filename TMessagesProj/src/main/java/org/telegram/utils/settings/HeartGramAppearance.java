package org.telegram.utils.settings;

import android.graphics.Color;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public final class HeartGramAppearance {
    public static final IntSetting palette = IntSetting.of("heartgram_palette", 0);
    public static final IntSetting fabRadius = IntSetting.of("heartgram_fab_radius", 16);
    public static final IntSetting dialogRadius = IntSetting.of("heartgram_dialog_radius", 28);
    public static final IntSetting sheetRadius = IntSetting.of("heartgram_sheet_radius", 28);
    public static final IntSetting searchRadius = IntSetting.of("heartgram_search_radius", 28);
    public static final IntSetting avatarRadius = IntSetting.of("heartgram_avatar_radius", 50);
    public static final IntSetting avatarShape = IntSetting.of("heartgram_avatar_shape", 0);
    public static final IntSetting barRadius = IntSetting.of("heartgram_bar_radius", 22);
    public static final BooleanSetting solidBars = BooleanSetting.of("heartgram_solid_bars", true);
    public static final IntSetting sendRadius = IntSetting.of("heartgram_send_radius", 14);
    public static final StringSetting previousWorkspaceTheme = StringSetting.of("heartgram_previous_workspace_theme", null);
    public static final BooleanSetting workspace = BooleanSetting.of("heartgram_workspace_layout", false);
    public static final BooleanSetting workspaceMigrated = BooleanSetting.of("heartgram_workspace_migrated", false);
    public static final IntSetting tabRadius = IntSetting.of("heartgram_tab_radius", 16);
    public static final BooleanSetting profileUsernames = BooleanSetting.of("heartgram_profile_usernames", true);
    public static final BooleanSetting profileGifts = BooleanSetting.of("heartgram_profile_gifts", true);
    public static final BooleanSetting giftOrbit = BooleanSetting.of("heartgram_gift_orbit", true);
    public static final BooleanSetting storiesFirst = BooleanSetting.of("heartgram_stories_first", true);
    public static final IntSetting outgoingAnimation = IntSetting.of("heartgram_outgoing_animation", 0);
    public static final IntSetting incomingAnimation = IntSetting.of("heartgram_incoming_animation", 0);

    public enum MessageAnimation {
        DEFAULT(R.string.HeartGramAnimationDefault), BOUNCE(R.string.HeartGramAnimationBounce),
        SLIDE(R.string.HeartGramAnimationSlide), FADE(R.string.HeartGramAnimationFade),
        SCALE(R.string.HeartGramAnimationScale);

        public final int title;

        MessageAnimation(int title) {
            this.title = title;
        }

        public static MessageAnimation of(int index) {
            return index >= 0 && index < values().length ? values()[index] : DEFAULT;
        }
    }

    public enum ColorRole {
        SURFACES("surfaces", R.string.HeartGramSurfaceColor,
                new int[] {0xFFF8EF, 0xFAF0E4, 0xF5E9DB, 0xEEE0D1, 0xDAC9B9},
                new int[] {0x231B15, 0x281F18, 0x30251D, 0x3A2C22, 0x554438},
                new int[] {0x292F39, 0x1D2229, 0x333C48}),
        ACCENT("accent", R.string.HeartGramAccentColor,
                new int[] {0x735337, 0x604329, 0x805D3C, 0xEFDCC7, 0xE4CBB0, 0x3D2B1D},
                new int[] {0xEABF98, 0x785637, 0x68472C, 0x845F40, 0x4C392B, 0x5B4534, 0xF7E1CB},
                new int[] {0xF8AFBE, 0x8D4053, 0x41313B, 0x523B49, 0xFFDEE5}),
        TEXT("text", R.string.HeartGramTextColor,
                new int[] {0x211A15, 0x6B5748, 0x897462},
                new int[] {0xF3E7D9, 0xD1BBA8, 0xA8917C},
                new int[] {0xF3ECEF, 0xCBC3CB}),
        INCOMING("incoming", R.string.HeartGramIncomingColor,
                new int[] {0xFFFBF6}, new int[] {0x2C231C}, new int[] {0x242830}),
        OUTGOING("outgoing", R.string.HeartGramOutgoingColor,
                new int[] {0xF1DBC4, 0xE9CDB0, 0x2C1E14, 0x644C39},
                new int[] {0x4D3828, 0x5D4634, 0xF6E6D8, 0xE4C7AB},
                new int[] {0x40333B, 0x54404D, 0xFBE8ED, 0xF0BBC8}),
        WALLPAPER("wallpaper", R.string.HeartGramWallpaperColor,
                new int[] {0xF0E4D5}, new int[] {0x1A1511}, new int[] {0x16191E});

        public final int title;
        public final IntSetting color;
        private final int[] light;
        private final int[] dark;
        private final int[] workspace;

        ColorRole(String key, int title, int[] light, int[] dark, int[] workspace) {
            this.title = title;
            this.color = IntSetting.of("heartgram_color_" + key, 0);
            this.light = light;
            this.dark = dark;
            this.workspace = workspace;
        }

        private int getSeed() {
            final int seed = color.get();
            final int preset = palette.get();
            return seed != 0 ? seed : PRESET_SEEDS[preset >= 0 && preset < PRESET_SEEDS.length ? preset : 0];
        }

        private int[] colors(boolean darkMode, boolean workspaceMode) {
            return workspaceMode ? workspace : darkMode ? dark : light;
        }

        public int getPreviewColor(boolean darkMode) {
            final int base = Color.BLACK | colors(darkMode, Theme.isHeartGramWorkspace())[0];
            final int seed = getSeed();
            if (seed == 0) {
                return base;
            }
            return this == WALLPAPER && color.get() != 0 ? seed : tint(base, seed, new float[3], new float[3]);
        }
    }

    private static final int[] PRESET_SEEDS = {0, 0xFFA55B46, 0xFF667448, 0xFF477283, 0xFFA65C78, 0xFF7565A4,
            0xFF644237, 0xFF77917E, 0xFFC99837};

    private HeartGramAppearance() {
    }

    public static int[] previewPreset(int preset, boolean dark) {
        int seed = PRESET_SEEDS[preset];
        int[] colors = dark ? new int[] {0xff30251d, 0xffeabf98, 0xff4d3828}
                : new int[] {0xfff5e9db, 0xff735337, 0xfff1dbc4};
        if (seed != 0) {
            final float[] source = new float[3];
            final float[] target = new float[3];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = tint(colors[i], seed, source, target);
            }
        }
        return colors;
    }

    public static void applyColors(SparseIntArray colors, boolean dark, boolean workspace) {
        final SparseIntArray replacements = new SparseIntArray();
        final float[] originalHsl = new float[3];
        final float[] seedHsl = new float[3];
        for (ColorRole role : ColorRole.values()) {
            final int seed = role.getSeed();
            if (seed == 0) {
                continue;
            }
            if (role == ColorRole.WALLPAPER && role.color.get() != 0) {
                colors.put(Theme.key_chat_wallpaper, seed);
                continue;
            }
            for (int rgb : role.colors(dark, workspace)) {
                replacements.put(rgb, tint(Color.BLACK | rgb, seed, originalHsl, seedHsl) & 0xFFFFFF);
            }
        }
        if (replacements.size() == 0) {
            return;
        }
        for (int i = 0; i < colors.size(); ++i) {
            final int original = colors.valueAt(i);
            final int replacement = replacements.get(original & 0xFFFFFF, -1);
            if (replacement >= 0) {
                colors.put(colors.keyAt(i), (original & 0xFF000000) | replacement);
            }
        }
    }

    private static int tint(int original, int seed, float[] originalHsl, float[] seedHsl) {
        ColorUtils.colorToHSL(original, originalHsl);
        ColorUtils.colorToHSL(seed, seedHsl);
        originalHsl[0] = seedHsl[0];
        originalHsl[1] *= 0.35f + 0.65f * seedHsl[1];

        // Keep the original contrast between roles even when the chosen hue is much brighter.
        final double targetLuminance = ColorUtils.calculateLuminance(original);
        float low = 0f;
        float high = 1f;
        for (int i = 0; i < 12; ++i) {
            originalHsl[2] = (low + high) / 2f;
            if (ColorUtils.calculateLuminance(ColorUtils.HSLToColor(originalHsl)) < targetLuminance) {
                low = originalHsl[2];
            } else {
                high = originalHsl[2];
            }
        }
        return ColorUtils.HSLToColor(originalHsl);
    }

    public static void reset() {
        outgoingAnimation.set(0);
        incomingAnimation.set(0);
        palette.set(0);
        for (ColorRole role : ColorRole.values()) {
            role.color.set(0);
        }
        fabRadius.set(16);
        dialogRadius.set(28);
        sheetRadius.set(28);
        searchRadius.set(28);
        avatarRadius.set(50);
        avatarShape.set(0);
        barRadius.set(22);
        solidBars.set(true);
        sendRadius.set(14);
        previousWorkspaceTheme.set(null);
        workspace.set(false);
        tabRadius.set(16);
        profileUsernames.set(true);
        profileGifts.set(true);
        giftOrbit.set(true);
        storiesFirst.set(true);
    }
}
