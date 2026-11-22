package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HeartGramDeletedMessages;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class HeartGramDeletedSheet extends BottomSheet {
    public HeartGramDeletedSheet(Context context, int account, long peer, Theme.ResourcesProvider provider) {
        super(context, false, provider);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(14), dp(18), dp(20));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setCustomView(scroll);

        TextView heading = label(context, getString(R.string.HeartGramKeepDeleted), 22, Theme.key_dialogTextBlack);
        heading.setTypeface(AndroidUtilities.bold());
        list.addView(heading, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        ArrayList<HeartGramDeletedMessages.Entry> entries = HeartGramDeletedMessages.forDialog(account, peer);
        if (entries.isEmpty()) {
            list.addView(label(context, getString(R.string.HeartGramDeletedEmpty), 16, Theme.key_windowBackgroundWhiteGrayText));
        }
        for (HeartGramDeletedMessages.Entry entry : entries) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(10), dp(14), dp(12));
            card.setBackground(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_dialogSearchBackground)));
            TextView metadata = label(context, getString(R.string.HeartGramDeletedMarker) + " · "
                    + LocaleController.formatDateAudio(entry.date, true), 12, Theme.key_windowBackgroundWhiteGrayText);
            metadata.setCompoundDrawablePadding(dp(6));
            android.graphics.drawable.Drawable trash = context.getDrawable(R.drawable.msg_delete).mutate();
            trash.setTint(metadata.getCurrentTextColor());
            trash.setBounds(0, 0, dp(16), dp(16));
            metadata.setCompoundDrawablesRelative(trash, null, null, null);
            card.addView(metadata);
            TextView message = label(context, entry.text, 16, Theme.key_dialogTextBlack);
            message.setAlpha(0.70f);
            card.addView(message, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));
            list.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        }
    }

    private TextView label(Context context, String text, int size, int colorKey) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(getThemedColor(colorKey));
        return view;
    }
}
