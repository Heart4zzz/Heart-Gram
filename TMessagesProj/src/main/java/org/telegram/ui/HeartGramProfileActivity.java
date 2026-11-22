package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.HeartGramProfileArt;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.utils.settings.HeartGramProfiles;
import org.telegram.utils.settings.HeartGramProfiles.Profile;

import java.util.ArrayList;

public final class HeartGramProfileActivity extends UniversalFragment {
    private static final int LINK = 1, VERIFY = 2, CHOOSE_FRAME = 5;
    private static final int SELECT_USER = 6, BAN = 7, APPLY_ADMIN = 8, FIRST_BADGE = 20;
    private static final int[] DURATIONS = {0, 3600, 86400, 604800, 2592000, 31536000};
    private static final int[] BAN_LABELS = {R.string.HeartGramBanNone, R.string.HeartGramBanHour,
            R.string.HeartGramBanDay, R.string.HeartGramBanWeek, R.string.HeartGramBanMonth, R.string.HeartGramBanYear};
    private final boolean admin;
    private final boolean badgesFirst;
    private long uid, target;
    private boolean busy;
    private boolean chooseNewDuration;
    private Profile profile, moderated;
    private int grants, duration;
    private LinearLayout preview;

    public HeartGramProfileActivity(boolean admin, boolean badgesFirst) {
        this.admin = admin;
        this.badgesFirst = badgesFirst;
    }

    @Override
    public boolean onFragmentCreate() {
        uid = getUserConfig().getClientUserId();
        return uid != 0 && (!admin || HeartGramProfiles.isOwner(uid)) && super.onFragmentCreate();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(admin ? R.string.HeartGramAdmin : R.string.HeartGramProfileStyle);
    }

    @Override
    public View createView(Context context) {
        profile = HeartGramProfiles.cached(uid, true);
        preview = new LinearLayout(context);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(16), dp(10), dp(16), dp(6));
        renderPreview();
        View result = super.createView(context);
        if (!admin) {
            refreshProfile();
        }
        return result;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (HeartGramProfiles.online() && !HeartGramProfiles.linked(uid) && HeartGramProfiles.pendingLink(uid) != null && !busy) {
            verifyLink(false);
        }
    }

    private void renderPreview() {
        if (preview == null) {
            return;
        }
        preview.removeAllViews();
        if (profile.image != null) {
            ImageView banner = new ImageView(preview.getContext());
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setImageBitmap(profile.image);
            banner.setBackground(Theme.createRoundRectDrawable(dp(24), getThemedColor(Theme.key_dialogSearchBackground)));
            banner.setClipToOutline(true);
            preview.addView(banner, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 120));
        }
        LinearLayout badges = new LinearLayout(preview.getContext());
        badges.setOrientation(LinearLayout.VERTICAL);
        HeartGramProfileArt.bindBadges(badges, profile.badges);
        preview.addView(badges);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (HeartGramProfiles.online()) {
            if (HeartGramProfiles.linked(uid)) {
                items.add(UItem.asShadow(getString(R.string.HeartGramAccountVerified)));
            } else if (HeartGramProfiles.pendingLink(uid) != null) {
                items.add(UItem.asButton(VERIFY, getString(R.string.HeartGramVerifyLink)));
                items.add(UItem.asButton(LINK, getString(R.string.HeartGramOpenVerificationBot)));
                items.add(UItem.asShadow(getString(R.string.HeartGramConnectHelp)));
            } else {
                items.add(UItem.asButton(LINK, getString(R.string.HeartGramConnectTelegram)));
            }
        } else {
            items.add(UItem.asShadow(getString(admin ? R.string.HeartGramServiceUnavailable : R.string.HeartGramLocalProfile)));
        }

        if (admin) {
            fillAdmin(items);
            return;
        }
        items.add(UItem.asCustom(preview));
        if (badgesFirst) {
            fillBadges(items, profile.granted, profile.badges, false);
        }
        items.add(UItem.asButton(CHOOSE_FRAME, getString(R.string.HeartGramAvatarFrame), frameNames()[profile.frame]));
        if (!badgesFirst) {
            fillBadges(items, profile.granted, profile.badges, false);
        }
    }

    private void fillBadges(ArrayList<UItem> items, int available, int flags, boolean moderator) {
        items.add(UItem.asHeader(getString(R.string.HeartGramBadges)));
        if (available == 0) {
            items.add(UItem.asShadow(getString(R.string.HeartGramNoBadges)));
        }
        for (int i = 0; i < HeartGramProfiles.BADGES.length; i++) {
            int flag = HeartGramProfiles.BADGES[i];
            if ((available & flag) == 0) {
                continue;
            }
            items.add(UItem.asCheck(FIRST_BADGE + i, HeartGramProfileArt.badgeLabel(getContext(), i,
                    getThemedColor(Theme.key_windowBackgroundWhiteBlackText)))
                    .setChecked((flags & flag) != 0));
        }
        if (moderator) {
            items.add(UItem.asShadow(null));
        }
    }

    private void fillAdmin(ArrayList<UItem> items) {
        if (!HeartGramProfiles.online() || !HeartGramProfiles.linked(uid)) {
            return;
        }
        items.add(UItem.asButton(SELECT_USER, getString(R.string.HeartGramAdminTarget), target == 0 ? "" : String.valueOf(target)));
        if (moderated == null) {
            return;
        }
        fillBadges(items, HeartGramProfiles.TESTER | HeartGramProfiles.CREATOR | HeartGramProfiles.DONATE, grants, true);
        items.add(UItem.asButton(BAN, getString(R.string.HeartGramBanDuration), chooseNewDuration ? getString(BAN_LABELS[duration])
                : Long.toString(Math.max(0, (moderated.bannedUntil - System.currentTimeMillis() / 1000 + 59) / 60)) + " min"));
        items.add(UItem.asButton(APPLY_ADMIN, getString(R.string.Save)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (busy || getContext() == null) {
            return;
        }
        if (item.id >= FIRST_BADGE && item.id < FIRST_BADGE + HeartGramProfiles.BADGES.length) {
            int flag = HeartGramProfiles.BADGES[item.id - FIRST_BADGE];
            if (admin) {
                grants ^= flag;
                updateItems();
            } else {
                save(profile.frame, (profile.badges & profile.granted) ^ flag);
            }
            return;
        }
        switch (item.id) {
            case LINK:
                beginLink();
                break;
            case VERIFY:
                verifyLink(true);
                break;
            case CHOOSE_FRAME:
                pickFrame();
                break;
            case SELECT_USER:
                selectUser();
                break;
            case BAN: {
                String[] labels = new String[BAN_LABELS.length];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = getString(BAN_LABELS[i]);
                }
                new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramBanDuration))
                        .setItems(labels, (dialog, index) -> {
                            duration = index;
                            chooseNewDuration = true;
                            updateItems();
                        }).show();
                break;
            }
            case APPLY_ADMIN:
                moderate(target, true);
                break;
        }
    }

    private void pickFrame() {
        String[] names = frameNames();
        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext());
        LinearLayout choices = new LinearLayout(getContext());
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(dp(16), 0, dp(16), 0);
        scroll.addView(choices);
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramAvatarFrame))
                .setView(scroll, 340).setNegativeButton(getString(R.string.Cancel), null).create();
        for (int i = 0; i < names.length; i++) {
            final int frame = i;
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), 0, dp(8), 0);
            row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(18), getThemedColor(Theme.key_dialogSearchBackground),
                    getThemedColor(Theme.key_listSelector)));
            View sample = new View(getContext()) {
                private final HeartGramProfileArt.AvatarFrame outline = new HeartGramProfileArt.AvatarFrame();

                @Override
                protected void onDraw(Canvas canvas) {
                    outline.draw(canvas, getWidth(), getHeight(), frame, dp(18));
                }
            };
            row.addView(sample, LayoutHelper.createLinear(38, 38));
            TextView label = new TextView(getContext());
            label.setText(names[i] + (profile.frame == i ? "   ✓" : ""));
            label.setTextSize(15);
            label.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            row.addView(label, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));
            choices.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 6));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                save(frame, profile.badges & profile.granted);
            });
        }
        showDialog(dialog);
    }

    private String[] frameNames() {
        return getContext().getResources().getStringArray(R.array.HeartGramFrameNames);
    }

    private boolean canEdit() {
        if (profile.bannedUntil > System.currentTimeMillis() / 1000) {
            explain(R.string.HeartGramProfileBanned);
            return false;
        }
        if (HeartGramProfiles.online() && !HeartGramProfiles.linked(uid)) {
            explain(R.string.HeartGramConnectHelp);
            return false;
        }
        return true;
    }

    private void save(int frame, int masks) {
        if (!canEdit()) {
            return;
        }
        busy = true;
        HeartGramProfiles.edit(uid, profile, frame, masks, (updated, error) -> {
            if (finished(updated, error)) {
                profile = updated;
                renderPreview();
                updateItems();
            }
        });
    }

    private void refreshProfile() {
        HeartGramProfiles.load(uid, true, (updated, error) -> {
            if (getContext() != null && updated != null) {
                profile = updated;
                renderPreview();
                updateItems();
            }
        });
    }

    private void beginLink() {
        busy = true;
        HeartGramProfiles.connect(uid, (result, error) -> {
            if (finished(result, error)) {
                updateItems();
                String bot = result.optString("bot");
                if (bot.matches("https://t\\.me/[A-Za-z0-9_]+\\?start=hg_[A-Za-z0-9_-]+")) {
                    Browser.openUrl(getParentActivity(), bot);
                }
            }
        });
    }

    private void verifyLink(boolean explicit) {
        busy = true;
        HeartGramProfiles.redeem(uid, (verified, error) -> {
            busy = false;
            if (getContext() == null) {
                return;
            }
            updateItems();
            if (Boolean.TRUE.equals(verified)) {
                if (!admin) {
                    refreshProfile();
                }
            } else if (explicit) {
                explain("expired".equals(error) ? R.string.HeartGramLinkExpired
                        : verified != null ? R.string.HeartGramConnectHelp : R.string.HeartGramServiceError);
            }
        });
    }

    private void selectUser() {
        if (!HeartGramProfiles.linked(uid)) {
            explain(R.string.HeartGramConnectHelp);
            return;
        }
        EditText input = new EditText(getContext());
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setHint("@username / ID");
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setPadding(dp(22), 0, dp(22), dp(10));
        wrapper.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
        showDialog(new AlertDialog.Builder(getContext()).setTitle(getString(R.string.HeartGramAdminTarget))
                .setView(wrapper).setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.Search), (dlg, index) -> resolve(input.getText().toString().trim())).create());
    }

    private void resolve(String query) {
        if (query.matches("[1-9][0-9]{0,15}")) {
            moderate(Long.parseLong(query), false);
        } else if (query.matches("@?[A-Za-z0-9_]{5,32}")) {
            busy = true;
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = query.startsWith("@") ? query.substring(1) : query;
            int id = getConnectionsManager().sendRequest(req, (result, error) -> AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                if (getContext() != null && result instanceof TLRPC.TL_contacts_resolvedPeer
                        && ((TLRPC.TL_contacts_resolvedPeer) result).peer instanceof TLRPC.TL_peerUser) {
                    moderate(((TLRPC.TL_contacts_resolvedPeer) result).peer.user_id, false);
                } else if (getContext() != null) {
                    explain(R.string.HeartGramTargetUnknown);
                }
            }));
            getConnectionsManager().bindRequestToGuid(id, getClassGuid());
        } else {
            explain(R.string.HeartGramTargetUnknown);
        }
    }

    private void moderate(long id, boolean write) {
        if (HeartGramProfiles.isOwner(id)) {
            explain(R.string.HeartGramTargetUnknown);
            return;
        }
        busy = true;
        Integer seconds = chooseNewDuration ? DURATIONS[duration] : null;
        HeartGramProfiles.moderate(uid, id, grants, seconds, write, (updated, error) -> {
            if (finished(updated, error)) {
                target = id;
                moderated = updated;
                grants = updated.granted;
                chooseNewDuration = updated.bannedUntil == 0;
                duration = 0;
                updateItems();
                if (write) {
                    explain(R.string.HeartGramAdminSaved);
                }
            }
        });
    }

    private boolean finished(Object result, String error) {
        busy = false;
        if (getContext() == null) {
            return false;
        }
        if (result == null) {
            if ("link_account".equals(error)) {
                HeartGramProfiles.clearSession(uid);
                updateItems();
                explain(R.string.HeartGramConnectHelp);
            } else {
                explain("banned".equals(error) ? R.string.HeartGramProfileBanned : R.string.HeartGramServiceError);
            }
            return false;
        }
        return true;
    }

    private void explain(int text) {
        new AlertDialog.Builder(getContext()).setMessage(getString(text)).setPositiveButton(getString(R.string.OK), null).show();
    }

    private void updateItems() {
        if (listView != null) {
            listView.adapter.update(false);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
