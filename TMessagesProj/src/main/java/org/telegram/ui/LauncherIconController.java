package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

import java.util.ArrayList;

public class LauncherIconController {
    public static final int[] HEARTGRAM_ICONS = {R.mipmap.heartgram_icon_0, R.mipmap.heartgram_icon_1,
            R.mipmap.heartgram_icon_2, R.mipmap.heartgram_icon_3, R.mipmap.heartgram_icon_4, R.mipmap.heartgram_icon_5,
            R.mipmap.heartgram_icon_6, R.mipmap.heartgram_icon_7, R.mipmap.heartgram_icon_8, R.mipmap.heartgram_icon_9,
            R.mipmap.heartgram_icon_10, R.mipmap.heartgram_icon_11, R.mipmap.heartgram_icon_12};

    public static void tryFixLauncherIconIfNeeded() {
        if (enabledHeartGramIcon() < 0) {
            setHeartGramIcon(0);
        }
    }

    public static int enabledHeartGramIcon() {
        Context context = ApplicationLoader.applicationContext;
        PackageManager pm = context.getPackageManager();
        for (int i = 0; i < HEARTGRAM_ICONS.length; i++) {
            int state = pm.getComponentEnabledSetting(heartGramComponent(context, i));
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || i == 0 && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                return i;
            }
        }
        return -1;
    }

    public static int selectedHeartGramIconResource() {
        return HEARTGRAM_ICONS[Math.max(0, enabledHeartGramIcon())];
    }

    private static ComponentName heartGramComponent(Context context, int index) {
        return index == 0 ? LauncherIcon.DEFAULT.getComponentName(context)
                : new ComponentName(context.getPackageName(), "org.telegram.messenger.HeartGramIcon" + index);
    }

    public static void setHeartGramIcon(int index) {
        Context context = ApplicationLoader.applicationContext;
        applyComponents(context, heartGramComponent(context, index));
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        applyComponents(ctx, icon.getComponentName(ctx));
    }

    private static void applyComponents(Context context, ComponentName selected) {
        PackageManager pm = context.getPackageManager();
        ArrayList<ComponentName> components = new ArrayList<>();
        components.add(selected);
        for (LauncherIcon legacy : LauncherIcon.values()) {
            if (!legacy.getComponentName(context).equals(selected)) {
                components.add(legacy.getComponentName(context));
            }
        }
        for (int index = 1; index < HEARTGRAM_ICONS.length; index++) {
            ComponentName component = heartGramComponent(context, index);
            if (!component.equals(selected)) {
                components.add(component);
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            ArrayList<PackageManager.ComponentEnabledSetting> changes = new ArrayList<>();
            for (ComponentName component : components) {
                changes.add(new PackageManager.ComponentEnabledSetting(component, component.equals(selected)
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP));
            }
            pm.setComponentEnabledSettings(changes);
        } else {
            for (ComponentName component : components) {
                pm.setComponentEnabledSetting(component, component.equals(selected)
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconDefault),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium, true),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo, true),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox, true);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }
    }
}
