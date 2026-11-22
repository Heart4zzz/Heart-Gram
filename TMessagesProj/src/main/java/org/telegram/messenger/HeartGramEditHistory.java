package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.utils.settings.HeartGramFeatures;

import java.util.ArrayList;
import java.util.HashMap;

public final class HeartGramEditHistory {
    public static final class Version {
        public final int date;
        public final String text;

        Version(int date, String text) {
            this.date = date;
            this.text = text;
        }
    }

    public interface Callback {
        void onLoaded(ArrayList<Version> versions);
    }

    public static boolean canShow(MessageObject message) {
        return message != null && !message.scheduled && !message.isImportedForward() && !message.isSponsored()
                && !message.isQuickReply() && !message.isWelcomeMessage() && !message.isRepostPreview
                && !message.messageOwner.edit_hide && canKeep(message.messageOwner)
                && canKeepDialog(message.currentAccount, message.getDialogId());
    }

    private static boolean canKeepDialog(int account, long dialogId) {
        if (!HeartGramFeatures.editHistory.get() || DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        MessagesController controller = MessagesController.getInstance(account);
        if (DialogObject.isUserDialog(dialogId)) {
            return dialogId != UserObject.REPLY_BOT && !controller.isUserNoForwards(dialogId);
        }
        return DialogObject.isChatDialog(dialogId) && controller.getChat(-dialogId) != null && !controller.isChatNoForwards(-dialogId);
    }

    private static boolean canKeep(TLRPC.Message message) {
        return message instanceof TLRPC.TL_message && message.id > 0 && !message.out && !message.noforwards
                && message.ttl == 0 && message.ttl_period == 0 && (message.media == null || message.media.ttl_seconds == 0)
                && (message.fwd_from == null || !message.fwd_from.imported);
    }

    static void record(int account, SQLiteDatabase database, long dialogId, ArrayList<TLRPC.Message> edits) {
        if (!canKeepDialog(account, dialogId)) {
            return;
        }
        SQLitePreparedStatement insert = null;
        try {
            HashMap<Integer, TLRPC.Message> previousEdits = new HashMap<>();
            for (TLRPC.Message edit : edits) {
                TLRPC.Message previous = previousEdits.get(edit.id);
                if (!canKeep(edit) || edit.edit_hide || edit.edit_date <= 0) {
                    previousEdits.put(edit.id, edit);
                    continue;
                }
                if (previous == null) {
                    previous = storedMessage(database, dialogId, edit.id);
                }
                if (previous != null && previous.edit_date > edit.edit_date) {
                    continue;
                }
                previousEdits.put(edit.id, edit);
                if (previous == null || !canKeep(previous) || previous.message == null || TextUtils.equals(previous.message, edit.message)) {
                    continue;
                }
                if (insert == null) {
                    createTable(database);
                    insert = database.executeFast("INSERT OR IGNORE INTO heartgram_edits(uid, mid, date, text) VALUES(?, ?, ?, ?)");
                }
                insert.requery();
                insert.bindLong(1, dialogId);
                insert.bindInteger(2, edit.id);
                insert.bindInteger(3, previous.edit_date > 0 ? previous.edit_date : previous.date);
                insert.bindString(4, previous.message);
                insert.step();
            }
        } catch (Exception error) {
            FileLog.e(error);
        } finally {
            if (insert != null) insert.dispose();
        }
    }

    private static TLRPC.Message storedMessage(SQLiteDatabase database, long dialogId, int messageId) throws Exception {
        String filter = " WHERE uid = " + dialogId + " AND mid = " + messageId;
        SQLiteCursor cursor = database.queryFinalized("SELECT data FROM messages_v2" + filter
                + " UNION ALL SELECT data FROM messages_topics" + filter + " LIMIT 1");
        try {
            if (!cursor.next()) return null;
            NativeByteBuffer data = cursor.byteBufferValue(0);
            if (data == null) return null;
            try {
                return TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
            } finally {
                data.reuse();
            }
        } finally {
            cursor.dispose();
        }
    }

    public static void load(MessageObject message, Callback callback) {
        MessagesStorage storage = MessagesStorage.getInstance(message.currentAccount);
        final long dialog = message.getDialogId();
        final int id = message.getId();
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<Version> versions = new ArrayList<>();
            if (canShow(message)) {
                SQLiteCursor cursor = null;
                try {
                    createTable(storage.getDatabase());
                    cursor = storage.getDatabase().queryFinalized("SELECT date, text FROM heartgram_edits WHERE uid = " + dialog
                            + " AND mid = " + id + " ORDER BY date DESC, rowid DESC");
                    while (cursor.next()) {
                        versions.add(new Version(cursor.intValue(0), cursor.stringValue(1)));
                    }
                } catch (Exception error) {
                    FileLog.e(error);
                } finally {
                    if (cursor != null) cursor.dispose();
                }
            }
            AndroidUtilities.runOnUIThread(() -> callback.onLoaded(versions));
        });
    }

    private static void createTable(SQLiteDatabase database) throws Exception {
        database.executeFast("CREATE TABLE IF NOT EXISTS heartgram_edits (uid INTEGER, mid INTEGER, date INTEGER, text TEXT, "
                + "PRIMARY KEY(uid, mid, date, text))").stepThis().dispose();
    }

    private HeartGramEditHistory() {
    }
}
