package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.utils.settings.HeartGramFun;

import java.util.ArrayList;

public final class HeartGramFunActivity extends UniversalFragment {
    @Override
    protected CharSequence getTitle() {
        return getString(R.string.HeartGramFunTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(1, getString(R.string.HeartGramScatter)).setChecked(HeartGramFun.scatter.get()));
        items.add(UItem.asShadow(getString(R.string.HeartGramScatterHelp)));
        items.add(UItem.asCheck(2, getString(R.string.HeartGramCat)).setChecked(HeartGramFun.cat.get()));
        items.add(UItem.asShadow(getString(R.string.HeartGramCatHelp)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            HeartGramFun.scatter.set(!HeartGramFun.scatter.get());
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.updateHeartGramShake();
            }
        } else if (item.id == 2) {
            HeartGramFun.cat.set(!HeartGramFun.cat.get());
        }
        listView.adapter.update(false);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
