package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.HeartGramBubble;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.utils.settings.HeartGramAppearance;
import org.telegram.utils.settings.HeartGramAppearance.MessageAnimation;
import org.telegram.utils.settings.HeartGramProfiles;
import org.telegram.utils.settings.IntSetting;

import java.util.ArrayList;
import java.util.Locale;

public final class HeartGramMessageStyleActivity extends UniversalFragment {
    private static final int THEME = 1, COLOR = 2, RGB = 3, OUTGOING = 4, INCOMING = 5;
    private final boolean animations;
    private HeartGramProfiles.Profile profile;
    private LinearLayout preview;
    private TextView bubble;
    private boolean busy;
    private boolean loadFailed;

    public HeartGramMessageStyleActivity(boolean animations) {
        this.animations = animations;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(animations ? R.string.HeartGramMessageAnimations : R.string.HeartGramMessageBubbles);
    }

    @Override
    public View createView(Context context) {
        if (!animations) {
            preview = new LinearLayout(context);
            preview.setOrientation(LinearLayout.VERTICAL);
            preview.setPadding(dp(18), dp(16), dp(18), dp(16));
            TextView incoming = new TextView(context);
            incoming.setPadding(dp(14), dp(9), dp(14), dp(9));
            incoming.setText(R.string.HeartGramPreviewIncoming);
            incoming.setTextColor(getThemedColor(Theme.key_chat_messageTextIn));
            incoming.setBackground(Theme.createRoundRectDrawable(dp(SharedConfig.bubbleRadius), getThemedColor(Theme.key_chat_inBubble)));
            preview.addView(incoming, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 0, 2, 0, 5));
            bubble = new TextView(context) {
                private final HeartGramBubble colors = new HeartGramBubble();
                private final Paint themePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void onDraw(Canvas canvas) {
                    int style = currentBubble();
                    int ink = getThemedColor(Theme.key_chat_messageTextOut);
                    themePaint.setColor(getThemedColor(Theme.key_chat_outBubble));
                    Paint paint = style == HeartGramProfiles.BUBBLE_THEME ? themePaint : colors.paint(style, ink, false, 0, 255);
                    canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(SharedConfig.bubbleRadius), dp(SharedConfig.bubbleRadius), paint);
                    super.onDraw(canvas);
                    if (style == HeartGramProfiles.BUBBLE_RGB) postInvalidateOnAnimation();
                }
            };
            bubble.setText(R.string.HeartGramBubblePreview);
            bubble.setPadding(dp(14), dp(9), dp(14), dp(9));
            bubble.setTextColor(getThemedColor(Theme.key_chat_messageTextOut));
            preview.setBackgroundColor(getThemedColor(Theme.key_chat_wallpaper));
            preview.addView(bubble, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END, 0, 3, 0, 0));
        }
        return super.createView(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!animations && HeartGramProfiles.online() && !busy) load();
    }

    private void load() {
        long uid = getUserConfig().getClientUserId();
        if (uid == 0) return;
        busy = true;
        HeartGramProfiles.loadBubble(uid, (data, error) -> {
            busy = false;
            loadFailed = data == null;
            if (data != null) profile = data;
            updateRows();
        });
    }

    private int currentBubble() {
        return profile == null ? HeartGramProfiles.BUBBLE_THEME : profile.bubble;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (animations) {
            items.add(UItem.asButton(OUTGOING, getString(R.string.HeartGramAnimationOutgoing), animationLabel(HeartGramAppearance.outgoingAnimation)));
            items.add(UItem.asButton(INCOMING, getString(R.string.HeartGramAnimationIncoming), animationLabel(HeartGramAppearance.incomingAnimation)));
            items.add(UItem.asShadow(getString(R.string.HeartGramAnimationDescription)));
            return;
        }
        items.add(UItem.asCustom(preview));
        int current = currentBubble();
        items.add(UItem.asRadio(THEME, getString(R.string.HeartGramBubbleTheme)).setChecked(current == HeartGramProfiles.BUBBLE_THEME));
        String colorLabel = current < 0 ? getString(R.string.HeartGramBubbleCustom) :
                getString(R.string.HeartGramBubbleCustom) + "  ·  " + String.format(Locale.ROOT, "#%06X", current);
        items.add(UItem.asRadio(COLOR, colorLabel).setChecked(current >= 0));
        items.add(UItem.asRadio(RGB, getString(R.string.HeartGramBubbleRgb)).setChecked(current == HeartGramProfiles.BUBBLE_RGB));
        items.add(UItem.asShadow(getString(R.string.HeartGramBubbleDescription)));
        if (loadFailed) {
            items.add(UItem.asButton(6, getString(R.string.Retry)));
        }
    }

    private String animationLabel(IntSetting setting) {
        return getString(MessageAnimation.of(setting.get()).title);
    }

    private void updateRows() {
        if (listView != null) listView.adapter.update(false);
        if (bubble != null) bubble.invalidate();
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (busy || getContext() == null) return;
        if (animations) {
            if (item.id == OUTGOING || item.id == INCOMING) chooseAnimation(item.id == OUTGOING);
            return;
        }
        if (item.id == 6) {
            load();
            return;
        }
        if (item.id != THEME && item.id != COLOR && item.id != RGB) return;
        if (!HeartGramProfiles.online()) {
            showMessage(R.string.HeartGramServiceUnavailable);
        } else if (!HeartGramProfiles.linked(getUserConfig().getClientUserId())) {
            linkProfile();
        } else if (item.id == THEME) {
            if (currentBubble() != HeartGramProfiles.BUBBLE_THEME) save(HeartGramProfiles.BUBBLE_THEME);
        } else if (item.id == COLOR) {
            chooseColor();
        } else if (profile != null && profile.granted == 0) {
            showMessage(R.string.HeartGramBubbleBadgeRequired);
        } else if (currentBubble() != HeartGramProfiles.BUBBLE_RGB) {
            save(HeartGramProfiles.BUBBLE_RGB);
        }
    }

    private void chooseAnimation(boolean outgoing) {
        IntSetting setting = outgoing ? HeartGramAppearance.outgoingAnimation : HeartGramAppearance.incomingAnimation;
        MessageAnimation[] styles = MessageAnimation.values();
        CharSequence[] labels = new CharSequence[styles.length];
        for (int i = 0; i < styles.length; i++) labels[i] = getString(styles[i].title);
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(outgoing ? R.string.HeartGramAnimationOutgoing : R.string.HeartGramAnimationIncoming))
                .setItems(labels, (dialog, index) -> {
                    setting.set(index);
                    updateRows();
                }).create());
    }

    private void chooseColor() {
        ColorPicker picker = new ColorPicker(getContext(), false, (color, index, apply) -> {});
        picker.setType(0, false, 1, 1, false, 0, false);
        int style = currentBubble();
        picker.setColor(style < 0 ? getThemedColor(Theme.key_chat_outBubble) : 0xff000000 | style, 0);
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramBubbleCustom))
                .setView(picker, 276).setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> save(picker.getColor() & 0xffffff)).create());
    }

    private void save(int style) {
        busy = true;
        HeartGramProfiles.editBubble(getUserConfig().getClientUserId(), style, (data, error) -> {
            busy = false;
            if (data != null) {
                profile = data;
                loadFailed = false;
                updateRows();
            } else if (getContext() != null) {
                if ("link_account".equals(error)) linkProfile();
                else showMessage("badge_required".equals(error) ? R.string.HeartGramBubbleBadgeRequired : R.string.HeartGramServiceError);
            }
        });
    }

    private void linkProfile() {
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramBubbleLink))
                .setMessage(getString(R.string.HeartGramBubbleLinkDescription))
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    HeartGramProfileActivity screen = new HeartGramProfileActivity(false, false);
                    screen.setCurrentAccount(currentAccount);
                    presentFragment(screen);
                }).create());
    }

    private void showMessage(int string) {
        showDialog(new AlertDialog.Builder(getContext()).setMessage(getString(string)).setPositiveButton(getString(R.string.OK), null).create());
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
