package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.utils.settings.HeartGramPlugins;

import java.util.ArrayList;

public final class HeartGramPluginsActivity extends UniversalFragment {
    private static final int POWER = 1;
    private static final int IMPORT = 2;
    private static final int CONSOLE = 3;
    private static final int TUTORIAL = 4;
    private static final int FIRST_PLUGIN = 100;
    private static final int PICK_PLUGIN = 9364;
    private ArrayList<HeartGramPlugins.Plugin> plugins = new ArrayList<>();
    private boolean installing;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.HeartGramPluginsTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(POWER, getString(R.string.HeartGramPluginsEnable)).setChecked(HeartGramPlugins.enabled.get()));
        items.add(UItem.asButton(IMPORT, getString(R.string.HeartGramPluginInstall)));
        items.add(UItem.asButton(CONSOLE, getString(R.string.HeartGramPluginConsole)));
        items.add(UItem.asButton(TUTORIAL, getString(R.string.HeartGramPluginTutorial)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.HeartGramPluginInstalled)));
        plugins = HeartGramPlugins.installed();
        if (plugins.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.HeartGramPluginEmpty)));
        }
        for (int i = 0; i < plugins.size(); i++) {
            HeartGramPlugins.Plugin plugin = plugins.get(i);
            items.add(UItem.asCheck(FIRST_PLUGIN + i, plugin.name + "   " + plugin.version).setChecked(plugin.active));
            items.add(UItem.asShadow(plugin.description));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == POWER) {
            boolean enabled = !HeartGramPlugins.enabled.get();
            HeartGramPlugins.enabled.set(enabled);
            HeartGramPlugins.log("plugins", "INFO", enabled ? "manager enabled" : "manager disabled");
        } else if (item.id == IMPORT && !installing) {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"), PICK_PLUGIN);
            return;
        } else if (item.id == CONSOLE) {
            showConsole(null);
            return;
        } else if (item.id == TUTORIAL) {
            showTutorial();
            return;
        } else if (item.id >= FIRST_PLUGIN && item.id < FIRST_PLUGIN + plugins.size()) {
            HeartGramPlugins.Plugin plugin = plugins.get(item.id - FIRST_PLUGIN);
            try {
                HeartGramPlugins.toggle(plugin);
            } catch (Exception e) {
                HeartGramPlugins.log(plugin.id, "ERROR", e.getMessage());
                showConsole(plugin.id);
            }
        }
        refresh();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_PLUGIN || resultCode != Activity.RESULT_OK || data == null || data.getData() == null
                || installing || getContext() == null) {
            return;
        }
        installing = true;
        final Context context = getContext().getApplicationContext();
        final Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            String error = null;
            try {
                HeartGramPlugins.install(context, uri);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                HeartGramPlugins.log("import", "ERROR", error);
            }
            final String failure = error;
            AndroidUtilities.runOnUIThread(() -> {
                installing = false;
                if (getContext() == null) {
                    return;
                }
                if (failure != null) {
                    showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                            .setTitle(getString(R.string.HeartGramPluginRejected))
                            .setMessage(failure).setPositiveButton(getString(R.string.OK), null).create());
                }
                refresh();
            });
        });
    }

    private void showConsole(String id) {
        String text = HeartGramPlugins.console(id);
        LinearLayout body = dialogBody();
        addText(body, text.isEmpty() ? getString(R.string.HeartGramPluginConsoleEmpty) : text, 13, Typeface.MONOSPACE);
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.HeartGramPluginConsole)).setView(scroll(body), 380)
                .setPositiveButton(getString(R.string.Close), null);
        if (id == null && !text.isEmpty()) {
            dialog.setNeutralButton(getString(R.string.HeartGramPluginClearLog), (ignored, which) -> HeartGramPlugins.clearConsole());
        }
        showDialog(dialog.create());
    }

    private void showTutorial() {
        LinearLayout body = dialogBody();
        addText(body, getString(R.string.HeartGramPluginTutorialOverview), 15, null);
        addText(body, getString(R.string.HeartGramPluginTutorialManifestTitle), 16, AndroidUtilities.bold());
        addText(body, getString(R.string.HeartGramPluginTutorialManifest), 15, null);
        addText(body, getString(R.string.HeartGramPluginTutorialUiTitle), 16, AndroidUtilities.bold());
        addText(body, getString(R.string.HeartGramPluginTutorialUi), 15, null);
        final String example = "{\n  \"manifest\": {\n    \"api\": 1,\n    \"id\": \"example.orbit\",\n"
                + "    \"name\": \"Orbit\",\n    \"version\": \"1.0\",\n    \"description\": \"One tiny star\"\n  },\n"
                + "  \"homeChip\": {\n    \"label\": \"Orbit\",\n    \"icon\": \"orbit\",\n"
                + "    \"onTap\": {\n      \"type\": \"dialog\",\n      \"title\": \"Orbit\",\n"
                + "      \"message\": \"Hello, explorer!\"\n    }\n  }\n}";
        TextView code = addText(body, example, 12, Typeface.MONOSPACE);
        code.setPadding(dp(12), dp(12), dp(12), dp(12));
        code.setTextIsSelectable(true);
        code.setBackground(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_dialogSearchBackground)));
        addText(body, getString(R.string.HeartGramPluginTutorialTest), 15, null);
        showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.HeartGramPluginTutorial))
                .setView(scroll(body), 460).setPositiveButton(getString(R.string.Close), null).create());
    }

    private LinearLayout dialogBody() {
        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(8), dp(20), dp(8));
        return body;
    }

    private ScrollView scroll(LinearLayout body) {
        ScrollView scroll = new ScrollView(body.getContext());
        scroll.addView(body);
        return scroll;
    }

    private TextView addText(LinearLayout body, String text, int size, Typeface face) {
        TextView view = new TextView(body.getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        if (face != null) {
            view.setTypeface(face);
        }
        body.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        view.setPadding(0, 0, 0, dp(12));
        return view;
    }

    private void refresh() {
        if (listView != null) {
            listView.adapter.update(false);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < FIRST_PLUGIN || item.id >= FIRST_PLUGIN + plugins.size()) {
            return false;
        }
        HeartGramPlugins.Plugin plugin = plugins.get(item.id - FIRST_PLUGIN);
        showDialog(new AlertDialog.Builder(getContext(), getResourceProvider()).setTitle(plugin.name)
                .setItems(new CharSequence[]{getString(R.string.HeartGramPluginConsole), getString(R.string.Delete)},
                        (dialog, which) -> {
                            if (which == 0) {
                                showConsole(plugin.id);
                            } else {
                                showDialog(new AlertDialog.Builder(getContext(), getResourceProvider())
                                        .setTitle(getString(R.string.HeartGramPluginRemove))
                                        .setMessage(plugin.name).setNegativeButton(getString(R.string.Cancel), null)
                                        .setPositiveButton(getString(R.string.Delete), (confirm, button) -> {
                                            HeartGramPlugins.remove(plugin);
                                            refresh();
                                        }).create());
                            }
                        }).create());
        return true;
    }
}
