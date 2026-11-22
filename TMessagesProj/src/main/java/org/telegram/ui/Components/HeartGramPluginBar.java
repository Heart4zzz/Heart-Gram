package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.utils.settings.HeartGramPlugins;

public final class HeartGramPluginBar extends HorizontalScrollView {
    private final BaseFragment screen;
    private final LinearLayout chips;

    public HeartGramPluginBar(Context context, BaseFragment screen) {
        super(context);
        this.screen = screen;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
        chips = new LinearLayout(context);
        chips.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        chips.setPadding(dp(12), 0, dp(12), 0);
        addView(chips, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        setVisibility(GONE);
    }

    public void update() {
        chips.removeAllViews();
        if (HeartGramPlugins.enabled.get()) {
            for (HeartGramPlugins.Plugin plugin : HeartGramPlugins.installed()) {
                if (plugin.active) {
                    addChip(plugin);
                }
            }
        }
        setVisibility(chips.getChildCount() == 0 ? GONE : VISIBLE);
    }

    private void addChip(HeartGramPlugins.Plugin plugin) {
        try {
            Context context = getContext();
            Theme.ResourcesProvider provider = screen.getResourceProvider();
            int ink = Theme.getColor(Theme.key_chats_actionIcon, provider);
            TextView chip = new TextView(context);
            chip.setText(plugin.label);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setMaxWidth(dp(220));
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(13);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setTextColor(ink);
            chip.setPadding(dp(12), 0, dp(12), 0);
            int icon = "heart".equals(plugin.icon) ? R.drawable.heartgram_badge_donate
                    : "spark".equals(plugin.icon) ? R.drawable.msg_premium_badge : R.drawable.heartgram_plugin_orbit;
            Drawable drawable = context.getDrawable(icon).mutate();
            drawable.setTint(ink);
            drawable.setBounds(0, 0, dp(17), dp(17));
            chip.setCompoundDrawablePadding(dp(6));
            chip.setCompoundDrawablesRelative(drawable, null, null, null);
            chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(22),
                    Theme.getColor(Theme.key_chats_actionBackground, provider),
                    Theme.getColor(Theme.key_chats_actionPressedBackground, provider)));
            chip.setOnClickListener(v -> open(plugin));
            chips.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, 0, 0, 6, 0));
        } catch (RuntimeException error) {
            HeartGramPlugins.log(plugin.id, "ERROR", error.getMessage());
        }
    }

    private void open(HeartGramPlugins.Plugin plugin) {
        try {
            if (screen.getContext() == null || screen.showDialog(new AlertDialog.Builder(getContext(), screen.getResourceProvider())
                    .setTitle(plugin.title).setMessage(plugin.message).setPositiveButton(getString(R.string.OK), null).create()) == null) {
                HeartGramPlugins.log(plugin.id, "ERROR", "Could not show homeChip.onTap dialog");
                return;
            }
            HeartGramPlugins.log(plugin.id, "INFO", "homeChip.onTap");
        } catch (RuntimeException error) {
            HeartGramPlugins.log(plugin.id, "ERROR", error.getMessage());
        }
    }
}
