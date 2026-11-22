package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HeartGramDeletedMessages;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.HeartGramAvatarShape;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.utils.settings.HeartGramAppearance;
import org.telegram.utils.settings.HeartGramAppearance.ColorRole;
import org.telegram.utils.settings.BooleanSetting;
import org.telegram.utils.settings.HeartGramFeatures;
import org.telegram.utils.settings.IntSetting;
import org.telegram.utils.settings.HeartGramTypeface;
import org.telegram.utils.settings.HeartGramProfiles;

import java.util.ArrayList;
import java.util.Locale;
import java.util.function.IntConsumer;

public class HeartGramSettingsActivity extends UniversalFragment {
    private LinearLayout sections;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.HeartGramSettings);
    }

    @Override
    public View createView(Context context) {
        sections = new LinearLayout(context);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setPadding(dp(16), dp(12), dp(16), dp(16));
        renderSections();
        return super.createView(context);
    }

    private void renderSections() {
        sections.removeAllViews();
        addSection(R.drawable.msg_colors, R.string.HeartGramCustomization, R.string.HeartGramCustomizeSummary, () ->
                presentFragment(new CustomizationActivity()));
        addSection(R.drawable.msg_settings, R.string.HeartGramFeaturesTitle, R.string.HeartGramFeaturesSummary, () ->
                presentFragment(new FeaturesActivity()));
        addSection(R.drawable.heartgram_plugin_orbit, R.string.HeartGramPluginsTitle, R.string.HeartGramPluginsSummary, () ->
                presentFragment(new HeartGramPluginsActivity()));
        addSection(R.drawable.heartgram_badge_donate, R.string.HeartGramFunTitle, R.string.HeartGramFunSummary, () ->
                presentFragment(new HeartGramFunActivity()));
        if (HeartGramProfiles.isOwner(getUserConfig().getClientUserId())) {
            addSection(R.drawable.msg_settings, R.string.HeartGramAdmin, R.string.HeartGramAdminSummary, () -> {
                HeartGramProfileActivity admin = new HeartGramProfileActivity(true, false);
                admin.setCurrentAccount(currentAccount);
                presentFragment(admin);
            });
        }
    }

    private void addSection(int icon, int title, int subtitle, Runnable open) {
        Context context = sections.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24),
                getThemedColor(Theme.key_dialogSearchBackground), getThemedColor(Theme.key_listSelector)));
        android.widget.ImageView image = new android.widget.ImageView(context);
        image.setImageResource(icon);
        image.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        image.setColorFilter(getThemedColor(Theme.key_chats_actionIcon));
        image.setBackground(Theme.createRoundRectDrawable(dp(15), getThemedColor(Theme.key_chats_actionBackground)));
        card.addView(image, LayoutHelper.createLinear(44, 44));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(getString(title));
        name.setTextSize(18);
        name.setTypeface(HeartGramTypeface.title());
        name.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        labels.addView(name);
        TextView detail = new TextView(context);
        detail.setText(getString(subtitle));
        detail.setTextSize(13);
        detail.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        labels.addView(detail, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        card.addView(labels, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        sections.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 9));
        card.setOnClickListener(view -> open.run());
    }

    @Override
    public void onThemeColorsChanged() {
        super.onThemeColorsChanged();
        if (sections != null) {
            renderSections();
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(sections));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private static class FeaturesActivity extends UniversalFragment {
        private enum Feature {
            DELETED(R.string.HeartGramKeepDeleted, R.string.HeartGramKeepDeletedInfo, HeartGramFeatures.keepDeleted),
            MUSIC(R.string.HeartGramAutoPlayer, R.string.HeartGramAutoPlayerInfo, HeartGramFeatures.openMusicPlayer),
            EDITS(R.string.HeartGramEditHistory, R.string.HeartGramEditHistoryInfo, HeartGramFeatures.editHistory),
            TRANSFERS(R.string.HeartGramFastTransfers, R.string.HeartGramFastTransfersInfo, HeartGramFeatures.fastTransfers);

            final int title;
            final int description;
            final BooleanSetting setting;

            Feature(int title, int description, BooleanSetting setting) {
                this.title = title;
                this.description = description;
                this.setting = setting;
            }
        }

        private static final int CLEAR_COPIES = 10;
        private static final int AUTO_REPLY = 12;
        private static final int PROXIES = 13;
        @Override
        protected CharSequence getTitle() {
            return getString(R.string.HeartGramFeaturesTitle);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            for (Feature feature : Feature.values()) {
                items.add(UItem.asCheck(feature.ordinal() + 1, getString(feature.title)).setChecked(feature.setting.get()));
                items.add(UItem.asShadow(getString(feature.description)));
            }
            items.add(UItem.asButton(CLEAR_COPIES, R.drawable.msg_delete, getString(R.string.HeartGramClearDeleted)));
            items.add(UItem.asButton(AUTO_REPLY, R.drawable.msg_discussion, getString(R.string.HeartGramAutoReply)));
            items.add(UItem.asButton(PROXIES, R.drawable.msg_settings, getString(R.string.HeartGramProxyManager)));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id == PROXIES) {
                ProxyListActivity screen = new ProxyListActivity();
                screen.setCurrentAccount(currentAccount);
                presentFragment(screen);
                return;
            }
            if (item.id == AUTO_REPLY) {
                HeartGramAutoReplyActivity screen = new HeartGramAutoReplyActivity();
                screen.setCurrentAccount(currentAccount);
                presentFragment(screen);
                return;
            }
            if (item.id == CLEAR_COPIES) {
                new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramClearDeleted))
                        .setMessage(getString(R.string.HeartGramClearDeletedInfo))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .setPositiveButton(getString(R.string.Delete), (dialog, which) ->
                                HeartGramDeletedMessages.clear(currentAccount)).show();
                return;
            }
            if (item.id > 0 && item.id <= Feature.values().length) {
                Feature feature = Feature.values()[item.id - 1];
                feature.setting.set(!feature.setting.get());
                listView.adapter.update(false);
            }
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    private static class FontActivity extends UniversalFragment {
        private static final int IMPORT_FONT = 9241;
        private LinearLayout fontCards;
        private boolean importing;

        @Override
        protected CharSequence getTitle() {
            return getString(R.string.HeartGramTypography);
        }

        @Override
        public View createView(Context context) {
            fontCards = new LinearLayout(context);
            fontCards.setOrientation(LinearLayout.VERTICAL);
            fontCards.setPadding(dp(16), dp(8), dp(16), dp(12));
            renderFonts();
            return super.createView(context);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asCustom(fontCards));
            items.add(UItem.asButton(1, R.drawable.msg_add, getString(R.string.HeartGramImportFont)));
            items.add(UItem.asShadow(getString(R.string.HeartGramFontInfo)));
        }

        private void renderFonts() {
            fontCards.removeAllViews();
            for (HeartGramTypeface.Preset preset : HeartGramTypeface.Preset.values()) {
                addFontCard(preset.id, preset.name);
            }
            addFontCard(HeartGramTypeface.SYSTEM, getString(R.string.HeartGramSystemFont));
            if (HeartGramTypeface.hasCustomFont()) {
                addFontCard(HeartGramTypeface.CUSTOM, getString(R.string.HeartGramOwnFont));
            }
        }

        private void addFontCard(int index, String name) {
            Context context = fontCards.getContext();
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(12), dp(16), dp(13));
            boolean selected = index == HeartGramTypeface.selected();
            int container = getThemedColor(selected ? Theme.key_chat_outBubble : Theme.key_dialogSearchBackground);
            card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(22), container, getThemedColor(Theme.key_listSelector)));

            TextView title = new TextView(context);
            title.setText(selected ? name + "  ✓" : name);
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(14);
            title.setTypeface(AndroidUtilities.bold());
            card.addView(title);
            TextView sample = new TextView(context);
            sample.setText(getString(R.string.HeartGramFontSample));
            sample.setTextSize(21);
            sample.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            sample.setTypeface(HeartGramTypeface.get(index));
            sample.setTag(R.id.heartgram_keep_typeface, true);
            card.addView(sample, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));
            fontCards.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
            card.setOnClickListener(v -> {
                HeartGramTypeface.select(index);
                applyFont();
            });
        }

        private void applyFont() {
            Theme.createCommonMessageResources();
            Theme.updateHeartGramDialogTypefaces();
            HeartGramTypeface.refreshViews();
            Theme.refreshThemeColors();
            renderFonts();
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id == 1 && !importing) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                startActivityForResult(intent, IMPORT_FONT);
            }
        }

        @Override
        public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
            if (requestCode != IMPORT_FONT || resultCode != Activity.RESULT_OK || data == null || data.getData() == null || importing) {
                return;
            }
            importing = true;
            Context context = getContext().getApplicationContext();
            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                boolean success;
                try {
                    HeartGramTypeface.importFile(context, data.getData());
                    success = true;
                } catch (Exception ignored) {
                    success = false;
                }
                final boolean imported = success;
                AndroidUtilities.runOnUIThread(() -> {
                    importing = false;
                    Theme.createCommonMessageResources();
                    Theme.updateHeartGramDialogTypefaces();
                    HeartGramTypeface.refreshViews();
                    Theme.refreshThemeColors();
                    if (getContext() != null && fontCards != null) {
                        if (imported) {
                            renderFonts();
                        } else {
                            new AlertDialog.Builder(getContext()).setMessage(getString(R.string.HeartGramInvalidFont))
                                    .setPositiveButton(getString(R.string.OK), null).show();
                        }
                    }
                });
            });
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    public static class CustomizationActivity extends UniversalFragment {
        private static final int MODE = 1;
        private static final int PALETTE = 2;
        private static final int BUBBLES = 3;
        private static final int TEXT_SIZE = 4;
        private static final int USERNAME = 5;
        private static final int GIFTS = 6;
        private static final int ORBIT = 7;
        private static final int STORIES = 8;
        private static final int TELEGRAM_THEMES = 9;
        private static final int RESET = 10;
        private static final int AVATAR_SHAPE = 11;
        private static final int SOLID_BAR = 12;
        private static final int WORKSPACE = 13;
        private static final int[] AVATAR_SHAPES = {R.string.HeartGramAvatarCircle, R.string.HeartGramAvatarSquare,
                R.string.HeartGramAvatarStar, R.string.HeartGramAvatarFlower, R.string.HeartGramAvatarHexagon,
                R.string.HeartGramAvatarCookie, R.string.HeartGramAvatarClover, R.string.HeartGramAvatarHeart,
                R.string.HeartGramAvatarDiamond, R.string.HeartGramAvatarPentagon, R.string.HeartGramAvatarOctagon,
                R.string.HeartGramAvatarCloud, R.string.HeartGramAvatarDrop, R.string.HeartGramAvatarCat,
                R.string.HeartGramAvatarShield, R.string.HeartGramAvatarLeaf, R.string.HeartGramAvatarCross,
                R.string.HeartGramAvatarButterfly, R.string.HeartGramAvatarRosette, R.string.HeartGramAvatarSpark,
                R.string.HeartGramAvatarArch};
        private static final int FIRST_COLOR = 100;
        private static final int FIRST_SHAPE = 200;
        private static final int[] PALETTE_TITLES = {
                R.string.HeartGramPaletteLinen, R.string.HeartGramPaletteClay, R.string.HeartGramPaletteOlive,
                R.string.HeartGramPaletteSlate, R.string.HeartGramPaletteRose, R.string.HeartGramPaletteLilac,
                R.string.HeartGramPaletteMocha, R.string.HeartGramPaletteSage, R.string.HeartGramPaletteHoney
        };

        private enum Shape {
            FAB(R.string.HeartGramShapeFab, HeartGramAppearance.fabRadius, new int[] {0, 8, 12, 16, 20, 24, 28}),
            DIALOG(R.string.HeartGramShapeDialog, HeartGramAppearance.dialogRadius, new int[] {0, 8, 16, 20, 24, 28, 36}),
            SHEET(R.string.HeartGramShapeSheet, HeartGramAppearance.sheetRadius, new int[] {0, 12, 20, 24, 28, 36, 44}),
            SEARCH(R.string.HeartGramShapeSearch, HeartGramAppearance.searchRadius, new int[] {0, 8, 12, 16, 20, 24, 28}),
            AVATAR(R.string.HeartGramShapeAvatar, HeartGramAppearance.avatarRadius, new int[] {0, 12, 20, 30, 40, 50}),
            TAB(R.string.HeartGramShapeTab, HeartGramAppearance.tabRadius, new int[] {0, 4, 8, 12, 16}),
            BAR(R.string.HeartGramBarRadius, HeartGramAppearance.barRadius, new int[] {0, 8, 16, 22, 28, 32}),
            SEND(R.string.HeartGramSendRadius, HeartGramAppearance.sendRadius, new int[] {0, 8, 12, 14, 18, 22});

            final int title;
            final IntSetting setting;
            final int[] options;

            Shape(int title, IntSetting setting, int[] options) {
                this.title = title;
                this.setting = setting;
                this.options = options;
            }
        }

        private enum Section {
            COLORS(R.string.HeartGramColorSection, R.drawable.msg_colors),
            INTERFACE(R.string.HeartGramInterfaceSection, R.drawable.heartgram_workspace_nav),
            SHAPES(R.string.HeartGramShapeSection, R.drawable.msg_settings),
            PROFILE(R.string.HeartGramProfileSection, R.drawable.msg_contacts),
            TYPOGRAPHY(R.string.HeartGramTypography, R.drawable.msg_edit),
            BUBBLES(R.string.HeartGramMessageBubbles, R.drawable.msg_discussion),
            ANIMATIONS(R.string.HeartGramMessageAnimations, R.drawable.heartgram_plugin_orbit),
            WALLPAPER(R.string.HeartGramIndependentWallpaper, R.drawable.msg_gallery),
            ICONS(R.string.HeartGramAppIcons, R.drawable.msg_gallery);

            final int title, icon;

            Section(int title, int icon) {
                this.title = title;
                this.icon = icon;
            }
        }

        private static final int FIRST_SECTION = 300;
        private final Section section;

        public CustomizationActivity() {
            this(null);
        }

        private CustomizationActivity(Section section) {
            this.section = section;
        }

        private LinearLayout preview;
        private TextView previewTitle, incoming, outgoing, previewButton;
        private View avatarPreview;

        @Override
        protected CharSequence getTitle() {
            return getString(section == null ? R.string.HeartGramCustomization : section.title);
        }

        @Override
        public View createView(Context context) {
            if (section != Section.COLORS && section != Section.SHAPES) {
                return super.createView(context);
            }
            preview = new LinearLayout(context);
            preview.setOrientation(LinearLayout.VERTICAL);
            preview.setPadding(dp(20), dp(12), dp(20), dp(12));
            previewTitle = new TextView(context);
            previewTitle.setText(getString(R.string.HeartGramAppName));
            previewTitle.setTextSize(18);
            previewTitle.setTypeface(HeartGramTypeface.title());
            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            avatarPreview = new View(context) {
                private final Path path = new Path();
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void onDraw(Canvas canvas) {
                    HeartGramAvatarShape.build(path, HeartGramAppearance.avatarShape.get());
                    int saved = canvas.save();
                    canvas.scale(getWidth(), getHeight());
                    paint.setColor(getThemedColor(Theme.key_chats_actionBackground));
                    canvas.drawPath(path, paint);
                    canvas.restoreToCount(saved);
                }
            };
            titleRow.addView(avatarPreview, LayoutHelper.createLinear(36, 36, 0f, 0, 0, 10, 0));
            titleRow.addView(previewTitle, LayoutHelper.createLinear(0, 36, 1f));
            preview.addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));
            incoming = addPreviewLabel(context, R.string.HeartGramPreviewIncoming, Gravity.START);
            outgoing = addPreviewLabel(context, R.string.HeartGramPreviewOutgoing, Gravity.END);
            previewButton = addPreviewLabel(context, R.string.HeartGramPreviewButton, Gravity.END);
            previewButton.setGravity(Gravity.CENTER);
            updatePreview();
            return super.createView(context);
        }

        private TextView addPreviewLabel(Context context, int text, int gravity) {
            final TextView view = new TextView(context);
            view.setText(getString(text));
            view.setPadding(dp(13), dp(7), dp(13), dp(7));
            preview.addView(view, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    gravity, 0, 3, 0, 3));
            return view;
        }

        private void updatePreview() {
            if (preview == null) {
                return;
            }
            preview.setBackgroundColor(getThemedColor(Theme.key_chat_wallpaper));
            previewTitle.setBackground(Theme.createRoundRectDrawable(dp(HeartGramAppearance.searchRadius.get()),
                    getThemedColor(Theme.key_dialogSearchBackground)));
            previewTitle.setPadding(dp(12), 0, dp(12), 0);
            previewTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            previewTitle.setTextColor(getThemedColor(Theme.key_dialogSearchText));
            previewTitle.setTypeface(HeartGramTypeface.title());
            incoming.setTypeface(HeartGramTypeface.current());
            outgoing.setTypeface(HeartGramTypeface.current());
            avatarPreview.invalidate();
            incoming.setBackground(Theme.createRoundRectDrawable(dp(SharedConfig.bubbleRadius), getThemedColor(Theme.key_chat_inBubble)));
            incoming.setTextColor(getThemedColor(Theme.key_chat_messageTextIn));
            outgoing.setBackground(Theme.createRoundRectDrawable(dp(SharedConfig.bubbleRadius), getThemedColor(Theme.key_chat_outBubble)));
            outgoing.setTextColor(getThemedColor(Theme.key_chat_messageTextOut));
            incoming.setTextSize(SharedConfig.fontSize);
            outgoing.setTextSize(SharedConfig.fontSize);
            previewButton.setBackground(Theme.createRoundRectDrawable(dp(HeartGramAppearance.fabRadius.get()),
                    getThemedColor(Theme.key_chats_actionBackground)));
            previewButton.setTextColor(getThemedColor(Theme.key_chats_actionIcon));
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            if (section == null) {
                for (Section entry : Section.values()) {
                    items.add(UItem.asButton(FIRST_SECTION + entry.ordinal(), entry.icon, getString(entry.title)));
                }
                items.add(UItem.asShadow(null));
                items.add(UItem.asButton(RESET, getString(R.string.HeartGramResetCustomization)));
                return;
            }
            if (preview != null) items.add(UItem.asCustom(preview));
            switch (section) {
                case COLORS:
                    items.add(UItem.asButton(MODE, getString(R.string.HeartGramColorMode),
                            getString(Theme.isCurrentThemeDark() ? R.string.HeartGramThemeDark : R.string.HeartGramThemeLight)));
                    int preset = Math.max(0, Math.min(HeartGramAppearance.palette.get(), PALETTE_TITLES.length - 1));
                    items.add(UItem.asButton(PALETTE, getString(R.string.HeartGramPalette), getString(PALETTE_TITLES[preset])));
                    for (ColorRole role : ColorRole.values()) {
                        items.add(UItem.asButton(FIRST_COLOR + role.ordinal(), getString(role.title),
                                String.format(Locale.ROOT, "#%06X", role.getPreviewColor(Theme.isCurrentThemeDark()) & 0xFFFFFF)));
                    }
                    items.add(UItem.asButton(TELEGRAM_THEMES, R.drawable.msg_colors, getString(R.string.HeartGramTelegramThemes)));
                    break;
                case INTERFACE:
                    items.add(UItem.asCheck(WORKSPACE, getString(R.string.HeartGramWorkspace)).setChecked(HeartGramAppearance.workspace.get()));
                    items.add(UItem.asShadow(getString(R.string.HeartGramWorkspaceHint)));
                    items.add(UItem.asCheck(SOLID_BAR, getString(R.string.HeartGramSolidBars)).setChecked(HeartGramAppearance.solidBars.get()));
                    break;
                case SHAPES:
                    int avatarShape = Math.max(0, Math.min(HeartGramAppearance.avatarShape.get(), AVATAR_SHAPES.length - 1));
                    items.add(UItem.asButton(AVATAR_SHAPE, getString(R.string.HeartGramAvatarShapeStyle), getString(AVATAR_SHAPES[avatarShape])));
                    for (Shape shape : Shape.values()) {
                        items.add(UItem.asButton(FIRST_SHAPE + shape.ordinal(), getString(shape.title), sizeLabel(shape.setting.get())));
                    }
                    items.add(UItem.asButton(BUBBLES, getString(R.string.HeartGramShapeBubbles), sizeLabel(SharedConfig.bubbleRadius)));
                    items.add(UItem.asButton(TEXT_SIZE, getString(R.string.HeartGramMessageSize), String.valueOf(SharedConfig.fontSize)));
                    break;
                case PROFILE:
                    items.add(UItem.asCheck(USERNAME, getString(R.string.HeartGramProfileUsername)).setChecked(HeartGramAppearance.profileUsernames.get()));
                    items.add(UItem.asCheck(GIFTS, getString(R.string.HeartGramProfileGifts)).setChecked(HeartGramAppearance.profileGifts.get()));
                    items.add(UItem.asCheck(ORBIT, getString(R.string.HeartGramProfileOrbit)).setChecked(HeartGramAppearance.giftOrbit.get()));
                    items.add(UItem.asCheck(STORIES, getString(R.string.HeartGramProfileStoriesFirst)).setChecked(HeartGramAppearance.storiesFirst.get()));
                    break;
            }
        }

        private void openSection(Section selected) {
            org.telegram.ui.ActionBar.BaseFragment screen;
            switch (selected) {
                case TYPOGRAPHY:
                    screen = new FontActivity();
                    break;
                case BUBBLES:
                    screen = new HeartGramMessageStyleActivity(false);
                    break;
                case ANIMATIONS:
                    screen = new HeartGramMessageStyleActivity(true);
                    break;
                case ICONS:
                    screen = new HeartGramIconsActivity();
                    break;
                case WALLPAPER:
                    screen = new HeartGramChatWallpaperActivity();
                    break;
                default:
                    screen = new CustomizationActivity(selected);
            }
            screen.setCurrentAccount(currentAccount);
            presentFragment(screen);
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id >= FIRST_SECTION && item.id < FIRST_SECTION + Section.values().length) {
                openSection(Section.values()[item.id - FIRST_SECTION]);
                return;
            }
            if (item.id >= FIRST_SHAPE && item.id < FIRST_SHAPE + Shape.values().length) {
                final Shape shape = Shape.values()[item.id - FIRST_SHAPE];
                chooseSize(shape.title, shape.options, size -> shape.setting.set(size), true);
                return;
            }
            if (item.id >= FIRST_COLOR && item.id < FIRST_COLOR + ColorRole.values().length) {
                chooseColor(ColorRole.values()[item.id - FIRST_COLOR]);
                return;
            }
            switch (item.id) {
                case WORKSPACE:
                    HeartGramAppearance.workspace.set(!HeartGramAppearance.workspace.get());
                    break;
                case AVATAR_SHAPE:
                    chooseAvatarShape();
                    return;
                case SOLID_BAR:
                    HeartGramAppearance.solidBars.set(!HeartGramAppearance.solidBars.get());
                    break;
                case MODE:
                    new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramColorMode))
                            .setItems(new CharSequence[] {getString(R.string.HeartGramThemeLight), getString(R.string.HeartGramThemeDark)},
                                    (dialog, index) -> selectHeartGramTheme(index == 1)).show();
                    return;
                case PALETTE:
                    choosePalette();
                    return;
                case BUBBLES:
                    chooseSize(R.string.HeartGramShapeBubbles, new int[] {0, 4, 8, 12, 15, 17}, size -> {
                        SharedConfig.bubbleRadius = size;
                        MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", size).apply();
                    }, true);
                    return;
                case TEXT_SIZE:
                    chooseSize(R.string.HeartGramMessageSize, new int[] {12, 14, 16, 18, 20, 22, 24}, size -> {
                        SharedConfig.fontSize = size;
                        SharedConfig.fontSizeIsDefault = false;
                        MessagesController.getGlobalMainSettings().edit().putInt("fons_size", size).apply();
                        Theme.createCommonMessageResources();
                    }, false);
                    return;
                case USERNAME:
                    HeartGramAppearance.profileUsernames.set(!HeartGramAppearance.profileUsernames.get());
                    break;
                case GIFTS:
                    HeartGramAppearance.profileGifts.set(!HeartGramAppearance.profileGifts.get());
                    break;
                case ORBIT:
                    HeartGramAppearance.giftOrbit.set(!HeartGramAppearance.giftOrbit.get());
                    break;
                case STORIES:
                    HeartGramAppearance.storiesFirst.set(!HeartGramAppearance.storiesFirst.get());
                    break;
                case TELEGRAM_THEMES:
                    presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                    return;
                case RESET:
                    new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramResetCustomization))
                            .setMessage(getString(R.string.HeartGramResetCustomizationInfo))
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .setPositiveButton(getString(R.string.Reset), (dialog, which) -> {
                                HeartGramAppearance.reset();
                                SharedConfig.bubbleRadius = 17;
                                SharedConfig.fontSize = AndroidUtilities.isTablet() ? 18 : 16;
                                SharedConfig.fontSizeIsDefault = true;
                                MessagesController.getGlobalMainSettings().edit().remove("bubbleRadius").remove("fons_size").apply();
                                Theme.createCommonMessageResources();
                                selectHeartGramTheme(false);
                            }).show();
                    return;
                default:
                    return;
            }
            applyCustomization(false);
        }

        private void selectHeartGramTheme(boolean dark) {
            Theme.turnOffAutoNight(this);
            if (Theme.getCurrentTheme() == Theme.getDefaultTheme(dark)) {
                applyCustomization(false);
            } else {
                Theme.switchThemeWithReveal(Theme.getDefaultTheme(dark), false, -1, null);
            }
        }

        private void applyCustomization(boolean colorChange) {
            if (colorChange && !Theme.isHeartGramTheme()) {
                selectHeartGramTheme(Theme.isCurrentThemeDark());
                return;
            }
            Theme.refreshThemeColors();
            updatePreview();
            listView.adapter.update(false);
        }

        @Override
        public void onThemeColorsChanged() {
            super.onThemeColorsChanged();
            updatePreview();
            if (listView != null) {
                listView.adapter.update(false);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            updatePreview();
            if (listView != null) {
                listView.adapter.update(false);
            }
        }

        private void chooseAvatarShape() {
            Context context = getContext();
            android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            LinearLayout grid = new LinearLayout(context);
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setPadding(dp(12), dp(4), dp(12), dp(4));
            scroll.addView(grid);
            AlertDialog dialog = new AlertDialog.Builder(context).setTitle(getString(R.string.HeartGramAvatarShapeStyle))
                    .setView(scroll, 386).setNegativeButton(getString(R.string.Cancel), null).create();
            LinearLayout row = null;
            for (int i = 0; i < AVATAR_SHAPES.length; i++) {
                if (i % 3 == 0) {
                    row = new LinearLayout(context);
                    grid.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
                final int shape = i;
                final boolean selected = HeartGramAppearance.avatarShape.get() == shape;
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setSelected(selected);
                card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(18),
                        getThemedColor(selected ? Theme.key_chat_outBubble : Theme.key_dialogSearchBackground),
                        getThemedColor(Theme.key_listSelector)));
                final Path silhouette = new Path();
                HeartGramAvatarShape.build(silhouette, shape);
                View sample = new View(context) {
                    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

                    @Override
                    protected void onDraw(Canvas canvas) {
                        int save = canvas.save();
                        canvas.scale(getWidth(), getHeight());
                        ink.setColor(getThemedColor(Theme.key_chats_actionBackground));
                        canvas.drawPath(silhouette, ink);
                        canvas.restoreToCount(save);
                    }
                };
                card.addView(sample, LayoutHelper.createLinear(36, 36));
                TextView label = new TextView(context);
                label.setText(getString(AVATAR_SHAPES[i]));
                label.setTextSize(12);
                label.setMaxLines(2);
                label.setGravity(Gravity.CENTER);
                label.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                card.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 2, 4, 2, 0));
                row.addView(card, LayoutHelper.createLinear(0, 76, 1f, 3, 3, 3, 3));
                card.setOnClickListener(v -> {
                    HeartGramAppearance.avatarShape.set(shape);
                    dialog.dismiss();
                    applyCustomization(false);
                });
            }
            showDialog(dialog);
        }

        private void chooseColor(ColorRole role) {
            final ColorPicker picker = new ColorPicker(getContext(), false, (color, index, apply) -> {});
            picker.setType(0, false, 1, 1, false, 0, false);
            final int current = role.color.get();
            picker.setColor(current == 0 ? role.getPreviewColor(Theme.isCurrentThemeDark()) : current, 0);
            showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(role.title))
                    .setView(picker, 276)
                    .setNeutralButton(getString(R.string.Default), (dialog, which) -> {
                        role.color.set(0);
                        applyCustomization(true);
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                        role.color.set(Color.BLACK | picker.getColor());
                        applyCustomization(true);
                    }).create());
        }

        private void choosePalette() {
            Context context = getContext();
            android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            LinearLayout choices = new LinearLayout(context);
            choices.setPadding(dp(14), dp(4), dp(14), dp(4));
            choices.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(choices);
            AlertDialog dialog = new AlertDialog.Builder(context).setTitle(getString(R.string.HeartGramPalette))
                    .setView(scroll, 360).setNegativeButton(getString(R.string.Cancel), null).create();
            for (int i = 0; i < PALETTE_TITLES.length; i++) {
                final int preset = i;
                final boolean active = HeartGramAppearance.palette.get() == preset;
                LinearLayout row = new LinearLayout(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), 0, dp(12), 0);
                row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(18),
                        getThemedColor(active ? Theme.key_chat_outBubble : Theme.key_dialogSearchBackground),
                        getThemedColor(Theme.key_listSelector)));
                TextView label = new TextView(context);
                label.setText(getString(PALETTE_TITLES[i]) + (active ? "  ✓" : ""));
                label.setTextSize(15);
                label.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                row.addView(label, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                for (int color : HeartGramAppearance.previewPreset(i, Theme.isCurrentThemeDark())) {
                    View swatch = new View(context);
                    swatch.setBackground(Theme.createRoundRectDrawable(dp(8), color));
                    row.addView(swatch, LayoutHelper.createLinear(22, 22, 0f, 4, 0, 0, 0));
                }
                choices.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 49, 0, 0, 0, 7));
                row.setOnClickListener(view -> {
                    dialog.dismiss();
                    HeartGramAppearance.palette.set(preset);
                    applyCustomization(true);
                });
            }
            showDialog(dialog);
        }

        private void chooseSize(int title, int[] sizes, IntConsumer apply, boolean dpUnit) {
            final CharSequence[] labels = new CharSequence[sizes.length];
            for (int i = 0; i < sizes.length; ++i) {
                labels[i] = dpUnit ? sizeLabel(sizes[i]) : String.valueOf(sizes[i]);
            }
            new AlertDialog.Builder(getContext()).setTitle(getString(title))
                    .setItems(labels, (dialog, index) -> {
                        apply.accept(sizes[index]);
                        applyCustomization(false);
                    }).show();
        }

        private String sizeLabel(int size) {
            return String.format(Locale.ROOT, "%d dp", size);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
