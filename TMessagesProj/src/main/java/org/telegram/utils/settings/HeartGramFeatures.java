package org.telegram.utils.settings;

public final class HeartGramFeatures {
    public static final BooleanSetting keepDeleted = BooleanSetting.of("heartgram_keep_deleted", false);
    public static final BooleanSetting openMusicPlayer = BooleanSetting.of("heartgram_open_music_player", true);
    public static final BooleanSetting editHistory = BooleanSetting.of("heartgram_edit_history", false);
    public static final BooleanSetting fastTransfers = BooleanSetting.of("heartgram_fast_transfers", false);

    private HeartGramFeatures() {
    }
}
