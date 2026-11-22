package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.HeartGramEditHistory;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.utils.settings.HeartGramTypeface;

import java.util.ArrayList;

public final class HeartGramEditHistorySheet extends BottomSheet {
    public HeartGramEditHistorySheet(Context context, Theme.ResourcesProvider colors, String current,
                                     ArrayList<HeartGramEditHistory.Version> versions) {
        super(context, false, colors);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(20));
        TextView title = label(context, getString(R.string.HeartGramEditHistory), 22, Theme.key_dialogTextBlack);
        title.setTypeface(HeartGramTypeface.title());
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        addVersion(content, getString(R.string.HeartGramEditCurrentVersion), current);
        if (versions.isEmpty()) {
            content.addView(label(context, getString(R.string.HeartGramEditHistoryEmpty), 15, Theme.key_windowBackgroundWhiteGrayText));
        } else {
            for (HeartGramEditHistory.Version version : versions) {
                addVersion(content, LocaleController.formatDateAudio(version.date, true), version.text);
            }
        }
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setCustomView(scroll);
    }

    private void addVersion(LinearLayout content, String title, String text) {
        Context context = content.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(12));
        card.setBackground(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_dialogSearchBackground)));
        card.addView(label(context, title, 13, Theme.key_windowBackgroundWhiteGrayText));
        card.addView(label(context, TextUtils.isEmpty(text) ? getString(R.string.HeartGramEditNoText) : text,
                16, Theme.key_dialogTextBlack), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));
        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
    }

    private TextView label(Context context, String text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(getThemedColor(color));
        return view;
    }
}
