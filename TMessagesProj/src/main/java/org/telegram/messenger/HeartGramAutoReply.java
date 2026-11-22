package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.Calendar;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HeartGramAutoReply {
    private static final int MAX_UPDATE_AGE = 120;
    public static final int REPLY_INTERVAL_SECONDS = 30;
    public static final int MAX_REPLY_LENGTH = 4096;
    private static final History[] histories = new History[UserConfig.MAX_ACCOUNT_COUNT];

    private static final class History extends LinkedHashMap<Long, Reply> {
        final long accountId;

        History(long accountId) {
            this.accountId = accountId;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Reply> eldest) {
            return size() > 256;
        }
    }

    private static final class Reply {
        final int messageId;
        final long sentAt;

        Reply(int messageId, long sentAt) {
            this.messageId = messageId;
            this.sentAt = sentAt;
        }
    }

    public static SharedPreferences settings(long accountId) {
        return ApplicationLoader.applicationContext.getSharedPreferences("heartgram_auto_reply_" + accountId, Context.MODE_PRIVATE);
    }

    public static void setEnabled(int account, boolean enabled) {
        settings(UserConfig.getInstance(account).getClientUserId()).edit()
                .putBoolean("enabled", enabled)
                .putInt("enabled_since", ConnectionsManager.getInstance(account).getCurrentTime())
                .apply();
    }

    static boolean inWindow(int minute, int start, int end) {
        if (start == end) {
            return true;
        }
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    static void onNewMessages(int account, long dialogId, List<MessageObject> messages) {
        long accountId = UserConfig.getInstance(account).getClientUserId();
        if (!DialogObject.isUserDialog(dialogId) || accountId == 0 || dialogId == accountId
                || UserObject.isService(dialogId) || UserObject.isReplyUser(dialogId)) {
            return;
        }
        SharedPreferences preferences = settings(accountId);
        if (!preferences.getBoolean("enabled", false)) {
            return;
        }
        String text = preferences.getString("text", "");
        if (text == null || text.isEmpty()) {
            return;
        }
        if (preferences.getBoolean("scheduled", false)) {
            Calendar now = Calendar.getInstance();
            int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            if (!inWindow(minute, preferences.getInt("start", 9 * 60), preferences.getInt("end", 18 * 60))) {
                return;
            }
        }

        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.User user = controller.getUser(dialogId);
        if (user == null || UserObject.isBot(user) || UserObject.isDeleted(user)
                || controller.blockePeers.indexOfKey(dialogId) >= 0 || controller.getSendPaidMessagesStars(dialogId) > 0) {
            return;
        }
        int oldestDate = Math.max(preferences.getInt("enabled_since", 0),
                ConnectionsManager.getInstance(account).getCurrentTime() - MAX_UPDATE_AGE);
        History history = histories[account];
        if (history == null || history.accountId != accountId) {
            history = new History(accountId);
            histories[account] = history;
        }
        long now = SystemClock.elapsedRealtime();
        Reply previous = history.get(dialogId);
        if (previous != null && now - previous.sentAt < REPLY_INTERVAL_SECONDS * 1000L) {
            return;
        }
        MessageObject latest = null;
        for (MessageObject message : messages) {
            TLRPC.Message incoming = message.messageOwner;
            if (incoming instanceof TLRPC.TL_message && message.getId() > 0 && !message.isOut()
                    && !message.isSponsored() && incoming.date >= oldestDate
                    && incoming.via_bot_id == 0 && incoming.via_business_bot_id == 0
                    && (incoming.from_id == null || DialogObject.getPeerDialogId(incoming.from_id) == dialogId)
                    && (previous == null || message.getId() > previous.messageId)
                    && (latest == null || message.getId() > latest.getId())) {
                latest = message;
            }
        }
        if (latest != null) {
            history.put(dialogId, new Reply(latest.getId(), now));
            SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                    text, dialogId, null, null, null, false, null, null, null, true, 0, 0, null, false));
        }
    }

    private HeartGramAutoReply() {
    }
}
