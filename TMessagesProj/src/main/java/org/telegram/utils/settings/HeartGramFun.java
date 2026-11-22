package org.telegram.utils.settings;

public final class HeartGramFun {
    public static final BooleanSetting scatter = BooleanSetting.of("heartgram_scatter", false);
    public static final BooleanSetting cat = BooleanSetting.of("heartgram_cat", false);

    private HeartGramFun() {
    }
}
