package org.telegram.messenger;

import android.text.TextUtils;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.utils.settings.HeartGramFeatures;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

public final class HeartGramDeletedMessages {
    private static final int LIMIT = 150;

    public static final class Entry {
        public final int date;
        public final String text;

        private Entry(int date, String text) {
            this.date = date;
            this.text = text;
        }
    }

    public static boolean canKeep(long dialogId, TLRPC.Message message) {
        return DialogObject.isUserDialog(dialogId) && !DialogObject.isEncryptedDialog(dialogId)
                && message instanceof TLRPC.TL_message && !message.out && !message.noforwards
                && message.ttl_period == 0 && (message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty)
                && !TextUtils.isEmpty(message.message);
    }

    static synchronized void capture(int account, SQLiteDatabase database, ArrayList<Integer> ids) {
        if (!HeartGramFeatures.keepDeleted.get() || database == null || ids.isEmpty()) {
            return;
        }
        AtomicFile archive = archive(UserConfig.getInstance(account).getClientUserId());
        if (archive == null) {
            return;
        }
        SQLiteCursor cursor = null;
        try {
            JSONArray saved = read(archive);
            cursor = database.queryFinalized("SELECT uid, mid, data FROM messages_v2 WHERE out = 0 AND ttl = 0 AND is_channel = 0 AND mid IN ("
                    + TextUtils.join(",", ids) + ") ORDER BY date DESC LIMIT " + LIMIT);
            JSONArray captured = new JSONArray();
            while (cursor.next()) {
                long dialogId = cursor.longValue(0);
                if (MessagesController.getInstance(account).isUserNoForwards(dialogId)) {
                    continue;
                }
                NativeByteBuffer data = cursor.byteBufferValue(2);
                if (data == null) {
                    continue;
                }
                TLRPC.Message message;
                try {
                    message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                } finally {
                    data.reuse();
                }
                if (canKeep(dialogId, message)) {
                    captured.put(new JSONObject().put("peer", dialogId).put("id", cursor.intValue(1))
                            .put("date", message.date).put("text", message.message));
                }
            }
            if (captured.length() == 0) {
                return;
            }
            for (int i = 0; i < saved.length() && captured.length() < LIMIT; i++) {
                JSONObject entry = saved.optJSONObject(i);
                if (entry != null && !contains(captured, entry)) {
                    captured.put(entry);
                }
            }
            FileOutputStream stream = archive.startWrite();
            try {
                stream.write(captured.toString().getBytes(StandardCharsets.UTF_8));
                archive.finishWrite(stream);
            } catch (Exception e) {
                archive.failWrite(stream);
                throw e;
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private static boolean contains(JSONArray list, JSONObject entry) {
        for (int i = 0; i < list.length(); i++) {
            JSONObject existing = list.optJSONObject(i);
            if (existing.optLong("peer") == entry.optLong("peer") && existing.optInt("id") == entry.optInt("id")) {
                return true;
            }
        }
        return false;
    }

    public static synchronized ArrayList<Entry> forDialog(int account, long dialogId) {
        ArrayList<Entry> entries = new ArrayList<>();
        AtomicFile archive = archive(UserConfig.getInstance(account).getClientUserId());
        if (archive != null) {
            JSONArray saved = read(archive);
            for (int i = 0; i < saved.length(); i++) {
                JSONObject entry = saved.optJSONObject(i);
                if (entry != null && entry.optLong("peer") == dialogId) {
                    entries.add(new Entry(entry.optInt("date"), entry.optString("text")));
                }
            }
        }
        Collections.sort(entries, (a, b) -> Integer.compare(b.date, a.date));
        return entries;
    }

    public static synchronized void clear(int account) {
        clearForUser(UserConfig.getInstance(account).getClientUserId());
    }

    static synchronized void clearForUser(long user) {
        AtomicFile archive = archive(user);
        if (archive != null) {
            archive.delete();
        }
    }

    private static AtomicFile archive(long user) {
        if (user == 0) {
            return null;
        }
        File directory = ApplicationLoader.applicationContext.getNoBackupFilesDir();
        return new AtomicFile(new File(directory, "heartgram_deleted_" + user + ".json"));
    }

    private static JSONArray read(AtomicFile archive) {
        try {
            return new JSONArray(new String(archive.readFully(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            return new JSONArray();
        } catch (Exception e) {
            FileLog.e(e);
            return new JSONArray();
        }
    }

    private HeartGramDeletedMessages() {
    }
}
