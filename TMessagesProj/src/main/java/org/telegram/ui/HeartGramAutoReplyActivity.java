package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.HeartGramAutoReply;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.utils.settings.HeartGramTypeface;

import java.util.ArrayList;
import java.util.Locale;

public final class HeartGramAutoReplyActivity extends UniversalFragment {
    private static final int ENABLED = 1, TEXT = 2, SCHEDULE = 3, START = 4, END = 5;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.HeartGramAutoReply);
    }

    private SharedPreferences settings() {
        return HeartGramAutoReply.settings(getUserConfig().getClientUserId());
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        SharedPreferences settings = settings();
        items.add(UItem.asCheck(ENABLED, getString(R.string.HeartGramAutoReplyEnabled)).setChecked(settings.getBoolean("enabled", false)));
        items.add(UItem.asShadow(getString(R.string.HeartGramAutoReplyDescription)));
        String reply = settings.getString("text", "");
        items.add(UItem.asButton(TEXT, R.drawable.msg_edit, getString(R.string.HeartGramAutoReplyText)));
        if (!reply.isEmpty()) {
            items.add(UItem.asShadow(reply.length() > 140 ? reply.substring(0, reply.offsetByCodePoints(0, Math.min(120, reply.codePointCount(0, reply.length())))) + "…" : reply));
        }
        boolean scheduled = settings.getBoolean("scheduled", false);
        items.add(UItem.asCheck(SCHEDULE, getString(R.string.HeartGramAutoReplySchedule)).setChecked(scheduled));
        if (scheduled) {
            items.add(UItem.asButton(START, getString(R.string.HeartGramAutoReplyStart), displayTime(minutes(START))));
            items.add(UItem.asButton(END, getString(R.string.HeartGramAutoReplyEnd), displayTime(minutes(END))));
        }
        items.add(UItem.asShadow(getString(scheduled ? R.string.HeartGramAutoReplyScheduleInfo : R.string.HeartGramAutoReplyAlways)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        SharedPreferences settings = settings();
        switch (item.id) {
            case ENABLED:
                boolean enable = !settings.getBoolean("enabled", false);
                if (enable && settings.getString("text", "").isEmpty()) {
                    editText(true);
                    return;
                }
                HeartGramAutoReply.setEnabled(currentAccount, enable);
                break;
            case TEXT:
                editText(false);
                return;
            case SCHEDULE:
                settings.edit().putBoolean("scheduled", !settings.getBoolean("scheduled", false)).apply();
                break;
            case START:
            case END:
                selectTime(item.id);
                return;
            default:
                return;
        }
        listView.adapter.update(false);
    }

    private void editText(boolean enableAfterSave) {
        Context context = getContext();
        FrameLayout field = new FrameLayout(context);
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(HeartGramAutoReply.MAX_REPLY_LENGTH)});
        input.setText(settings().getString("text", ""));
        input.setHint(getString(R.string.HeartGramAutoReplyTextHint));
        input.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        input.setTypeface(HeartGramTypeface.current());
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setTextSize(16);
        input.setGravity(Gravity.START | Gravity.TOP);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_dialogSearchBackground)));
        field.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 20, 8, 20, 6));
        showDialog(new AlertDialog.Builder(context).setTitle(getString(R.string.HeartGramAutoReplyText)).setView(field)
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    String message = input.getText().toString().trim();
                    settings().edit().putString("text", message).apply();
                    if (message.isEmpty() || enableAfterSave) {
                        HeartGramAutoReply.setEnabled(currentAccount, !message.isEmpty());
                    }
                    listView.adapter.update(false);
                }).create());
    }

    private int minutes(int item) {
        return item == START ? settings().getInt("start", 540) : settings().getInt("end", 1080);
    }

    private static String displayTime(int time) {
        return String.format(Locale.getDefault(), "%02d:%02d", time / 60, time % 60);
    }

    private void selectTime(int item) {
        Context context = getContext();
        int current = minutes(item);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(22), dp(10), dp(22), dp(10));
        LinearLayout wheels = new LinearLayout(context);
        wheels.setGravity(Gravity.CENTER);
        NumberPicker hours = timeWheel(context, 23, current / 60);
        NumberPicker minutes = timeWheel(context, 59, current % 60);
        wheels.addView(hours, LayoutHelper.createLinear(0, 144, 1f));
        TextView colon = new TextView(context);
        colon.setText(":");
        colon.setTextSize(28);
        colon.setGravity(Gravity.CENTER);
        colon.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        wheels.addView(colon, LayoutHelper.createLinear(28, LayoutHelper.MATCH_PARENT));
        wheels.addView(minutes, LayoutHelper.createLinear(0, 144, 1f));
        hours.setContentDescription(getString(R.string.HeartGramAutoReplyHours));
        minutes.setContentDescription(getString(R.string.HeartGramAutoReplyMinutes));
        container.addView(wheels);
        showDialog(new AlertDialog.Builder(context).setTitle(getString(item == START ? R.string.HeartGramAutoReplyStart : R.string.HeartGramAutoReplyEnd))
                .setView(container).setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    hours.clearFocus();
                    minutes.clearFocus();
                    settings().edit().putInt(item == START ? "start" : "end", hours.getValue() * 60 + minutes.getValue()).apply();
                    listView.adapter.update(false);
                }).create());
    }

    private NumberPicker timeWheel(Context context, int max, int value) {
        NumberPicker picker = new NumberPicker(context, 28, getResourceProvider());
        picker.setMinValue(0);
        picker.setMaxValue(max);
        picker.setFormatter(number -> String.format(Locale.getDefault(), "%02d", number));
        picker.setWrapSelectorWheel(true);
        picker.setValue(value);
        picker.setDrawDividers(false);
        picker.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        picker.setBackground(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_dialogSearchBackground)));
        return picker;
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
