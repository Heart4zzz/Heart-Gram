package org.telegram.utils.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.NotificationCenter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public final class HeartGramProfiles {
    public static final long OWNER = BuildConfig.HEARTGRAM_OWNER_ID;

    public static boolean isOwner(long uid) {
        return OWNER > 0 && uid == OWNER;
    }
    public static final int DEVELOPER = 1;
    public static final int TESTER = 2;
    public static final int BANNED = 4;
    public static final int CREATOR = 8;
    public static final int DONATE = 16;
    public static final int[] BADGES = {DEVELOPER, TESTER, BANNED, CREATOR, DONATE};
    public static final String[] BADGE_NAMES = {"Developer", "Tester", "Banned", "Content Creator", "Donate"};
    public static final int MAX_FRAME = 9;
    public static final int BUBBLE_THEME = -1;
    public static final int BUBBLE_RGB = -2;
    private static final int MAX_BANNER = 768 * 1024;
    private static final DispatchQueue requests = new DispatchQueue("heartgramProfiles");
    private static final LinkedHashMap<Long, Profile> cache = new LinkedHashMap<Long, Profile>(20, .75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, Profile> entry) {
            return size() > 16;
        }
    };
    private static final LinkedHashMap<Long, Decoration> decorations = new LinkedHashMap<Long, Decoration>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, Decoration> entry) {
            return size() > 512;
        }
    };
    private static final LinkedHashSet<Long> queuedDecorations = new LinkedHashSet<>();
    private static final int DECORATION_BATCH_SIZE = 40;
    private static final long DECORATION_TTL = 60_000L;
    private static boolean decorationsLoading;

    public interface Result<T> {
        void complete(T result, String error);
    }

    private interface Job<T> {
        T run() throws Exception;
    }

    public static final class Profile {
        public final int frame;
        public final int badges;
        public final int granted;
        public final long bannedUntil;
        public final Bitmap image;
        public final int bubble;

        private Profile(int frame, int badges, int granted, long bannedUntil, Bitmap image) {
            this(frame, badges, granted, bannedUntil, image, BUBBLE_THEME);
        }

        private Profile(int frame, int badges, int granted, long bannedUntil, Bitmap image, int bubble) {
            this.frame = frame;
            this.badges = badges;
            this.granted = granted;
            this.bannedUntil = bannedUntil;
            this.image = image;
            this.bubble = bubble;
        }
    }

    public static final class Decoration {
        public final int frame;
        public final int badges;
        public final int bubble;
        private final long expires;

        private Decoration(int frame, int badges, int bubble, long expires) {
            this.frame = frame;
            this.badges = badges;
            this.bubble = bubble;
            this.expires = expires;
        }
    }

    // Chat rows call this on the UI thread. Only Telegram user IDs go into the request.
    public static Decoration chatDecoration(long uid, boolean self) {
        if (uid <= 0) {
            return null;
        }
        Decoration decoration = decorations.get(uid);
        if (online()) {
            if ((decoration == null || decoration.expires <= SystemClock.elapsedRealtime()) && queuedDecorations.add(uid)) {
                if (!decorationsLoading && queuedDecorations.size() == 1) {
                    AndroidUtilities.runOnUIThread(HeartGramProfiles::loadDecorations, 120);
                }
            }
        } else if (decoration == null && (self || isOwner(uid))) {
            Profile profile = cached(uid, self);
            decoration = new Decoration(profile.frame, profile.badges, profile.bubble, Long.MAX_VALUE);
            decorations.put(uid, decoration);
        }
        return decoration;
    }

    private static void loadDecorations() {
        if (queuedDecorations.isEmpty() || decorationsLoading) {
            return;
        }
        decorationsLoading = true;
        ArrayList<Long> ids = new ArrayList<>(DECORATION_BATCH_SIZE);
        StringBuilder path = new StringBuilder("/decorations?ids=");
        for (long uid : queuedDecorations) {
            if (!ids.isEmpty()) {
                path.append(',');
            }
            ids.add(uid);
            path.append(uid);
            if (ids.size() == DECORATION_BATCH_SIZE) {
                break;
            }
        }
        async(() -> {
            JSONArray profiles = json(path.toString(), null, null).getJSONArray("profiles");
            HashMap<Long, Decoration> result = new HashMap<>();
            long expires = SystemClock.elapsedRealtime() + DECORATION_TTL;
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.getJSONObject(i);
                result.put(profile.getLong("uid"), new Decoration(
                        Math.max(0, Math.min(MAX_FRAME, profile.optInt("frame"))), profile.optInt("badges") & 31, decodeBubble(profile), expires));
            }
            return result;
        }, (result, error) -> {
            ArrayList<Long> changed = new ArrayList<>();
            long now = SystemClock.elapsedRealtime();
            for (long uid : ids) {
                Decoration previous = decorations.get(uid);
                Decoration decoration = result == null ? null : result.get(uid);
                if (decoration == null && result == null && previous != null) {
                    decoration = new Decoration(previous.frame, previous.badges, previous.bubble, now + 12_000);
                } else if (decoration == null) {
                    decoration = new Decoration(0, 0, BUBBLE_THEME, now + (result == null ? 12_000 : DECORATION_TTL));
                }
                if (rememberDecoration(uid, decoration)) {
                    changed.add(uid);
                }
            }
            queuedDecorations.removeAll(ids);
            decorationsLoading = false;
            if (!changed.isEmpty()) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.heartGramDecorationsUpdated, changed);
            }
            loadDecorations();
        });
    }

    private static boolean rememberDecoration(long uid, Decoration decoration) {
        Decoration old = decorations.put(uid, decoration);
        return old == null ? decoration.frame != 0 || decoration.badges != 0 || decoration.bubble != BUBBLE_THEME
                : old.frame != decoration.frame || old.badges != decoration.badges || old.bubble != decoration.bubble;
    }

    private static void rememberProfileDecoration(long uid, Profile profile) {
        if (rememberDecoration(uid, new Decoration(profile.frame, profile.badges, profile.bubble, SystemClock.elapsedRealtime() + DECORATION_TTL))) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.heartGramDecorationsUpdated, java.util.Collections.singletonList(uid));
        }
    }

    public static boolean online() {
        return !BuildConfig.HEARTGRAM_PROFILE_URL.isEmpty();
    }

    public static boolean linked(long uid) {
        return file("session", uid).getBaseFile().isFile();
    }

    public static JSONObject pendingLink(long uid) {
        try {
            JSONObject pending = new JSONObject(new String(file("link", uid).readFully(), StandardCharsets.UTF_8));
            return pending.optLong("expires") > System.currentTimeMillis() ? pending : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Profile cached(long uid, boolean self) {
        Profile data = cache.get(uid);
        if (data != null) {
            return data;
        }
        int badge = isOwner(uid) ? DEVELOPER : 0;
        if (!self) {
            return new Profile(0, badge, 0, 0, null);
        }
        SharedPreferences local = preferences(uid);
        return new Profile(local.getInt("frame", 0), local.getInt("badges", badge), badge, 0, null);
    }

    public static void load(long uid, boolean self, Result<Profile> callback) {
        async(() -> {
            if (online()) {
                return decode(uid, readProfile(uid, self), null, true);
            }
            int badge = isOwner(uid) ? DEVELOPER : 0;
            if (!self) {
                return new Profile(0, badge, 0, 0, null);
            }
            SharedPreferences local = preferences(uid);
            return new Profile(local.getInt("frame", 0), local.getInt("badges", badge),
                    badge, 0, BitmapFactory.decodeFile(file("banner", uid).getBaseFile().getPath()));
        }, (data, error) -> {
            if (data != null) {
                cache.put(uid, data);
                rememberProfileDecoration(uid, data);
            }
            callback.complete(data, error);
        });
    }

    private static JSONObject readProfile(long uid, boolean self) throws Exception {
        if (self && linked(uid)) {
            try {
                return json("/me", null, token(uid));
            } catch (IOException e) {
                if (!"link_account".equals(e.getMessage())) throw e;
                clearSession(uid);
            }
        }
        return json("/profiles/" + uid, null, null);
    }

    public static void loadBubble(long uid, Result<Profile> callback) {
        async(() -> decode(uid, readProfile(uid, true), null, false), (data, error) -> {
            if (data != null) rememberProfileDecoration(uid, data);
            callback.complete(data, error);
        });
    }

    public static void connect(long uid, Result<JSONObject> callback) {
        async(() -> {
            JSONObject pending = pendingLink(uid);
            if (pending != null) {
                return pending;
            }
            pending = json("/connect", new JSONObject().put("uid", Long.toString(uid)), null);
            pending.put("expires", System.currentTimeMillis() + 300_000);
            write(file("link", uid), pending.toString().getBytes(StandardCharsets.UTF_8));
            return pending;
        }, callback);
    }

    public static void redeem(long uid, Result<Boolean> callback) {
        async(() -> {
            if (linked(uid)) {
                return true;
            }
            JSONObject pending = pendingLink(uid);
            if (pending == null) {
                throw new IOException("expired");
            }
            JSONObject response;
            try {
                response = json("/connect/redeem", new JSONObject().put("ticket", pending.getString("ticket")), null);
            } catch (IOException e) {
                if ("expired".equals(e.getMessage())) {
                    file("link", uid).delete();
                }
                throw e;
            }
            if (response.optBoolean("pending")) {
                return false;
            }
            if (!Long.toString(uid).equals(response.getString("uid"))) {
                throw new IOException("account_mismatch");
            }
            write(file("session", uid), response.getString("token").getBytes(StandardCharsets.US_ASCII));
            file("link", uid).delete();
            return true;
        }, callback);
    }

    public static void edit(long uid, Profile current, int frame, int badges, Result<Profile> callback) {
        edit(uid, current, frame, badges, null, false, callback);
    }

    public static void editBanner(long uid, Profile current, Uri newBanner, Result<Profile> callback) {
        edit(uid, current, null, null, newBanner, newBanner == null, callback);
    }

    public static void editBubble(long uid, int bubble, Result<Profile> callback) {
        async(() -> {
            try {
                return decode(uid, json("/me", new JSONObject().put("bubble", bubble), token(uid)), null, false);
            } catch (IOException error) {
                if ("link_account".equals(error.getMessage())) {
                    clearSession(uid);
                }
                throw error;
            }
        }, (data, error) -> {
            if (data != null) {
                cache.remove(uid);
                rememberProfileDecoration(uid, data);
            }
            callback.complete(data, error);
        });
    }

    private static int decodeBubble(JSONObject data) {
        int bubble = data.optInt("bubble", BUBBLE_THEME);
        return bubble >= BUBBLE_RGB && bubble <= 0xffffff ? bubble : BUBBLE_THEME;
    }

    private static void edit(long uid, Profile current, Integer frame, Integer badges, Uri newBanner, boolean removeBanner, Result<Profile> callback) {
        async(() -> {
            byte[] jpeg = newBanner == null ? null : jpeg(newBanner);
            Bitmap image = jpeg == null ? (removeBanner ? null : current.image) : BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (online()) {
                JSONObject body = new JSONObject();
                if (frame != null && badges != null) {
                    body.put("frame", frame).put("badges", badges);
                }
                if (jpeg != null || removeBanner) {
                    body.put("banner", jpeg == null ? JSONObject.NULL : Base64.encodeToString(jpeg, Base64.NO_WRAP));
                }
                return decode(uid, json("/me", body, token(uid)), image, false);
            }
            if (jpeg != null) {
                write(file("banner", uid), jpeg);
            } else if (removeBanner) {
                file("banner", uid).delete();
            }
            if (frame != null && badges != null) {
                preferences(uid).edit().putInt("frame", frame).putInt("badges", badges).apply();
            }
            return new Profile(frame == null ? current.frame : frame, badges == null ? current.badges : badges,
                    current.granted, 0, image);
        }, (data, error) -> {
            if (data != null) {
                cache.put(uid, data);
                rememberProfileDecoration(uid, data);
            }
            callback.complete(data, error);
        });
    }

    public static void moderate(long admin, long target, int grants, Integer seconds, boolean write, Result<Profile> callback) {
        async(() -> {
            if (!isOwner(admin)) {
                throw new IOException("owner_only");
            }
            JSONObject body = write ? new JSONObject().put("granted", grants) : null;
            if (body != null && seconds != null) {
                body.put("seconds", seconds);
            }
            return decode(target, json("/admin/" + target, body, token(admin)), null, false);
        }, (data, error) -> {
            if (data != null && write) {
                cache.remove(target);
                rememberProfileDecoration(target, data);
            }
            callback.complete(data, error);
        });
    }

    private static Profile decode(long uid, JSONObject data, Bitmap image, boolean fetchImage) throws Exception {
        if (fetchImage && data.optBoolean("banner")) {
            byte[] bytes = request("/profiles/" + uid + "/banner?v=" + data.optLong("revision"), null, null);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (options.outWidth > 0 && options.outHeight > 0 && options.outWidth <= 1200 && options.outHeight <= 600) {
                image = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        }
        return new Profile(Math.max(0, Math.min(MAX_FRAME, data.optInt("frame"))), data.optInt("badges"),
                data.optInt("granted"), data.optLong("banned_until"), image, decodeBubble(data));
    }

    private static byte[] jpeg(Uri uri) throws IOException {
        Context context = ApplicationLoader.applicationContext;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw new IOException("invalid_banner");
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1800) {
            options.inSampleSize *= 2;
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            Bitmap source = BitmapFactory.decodeStream(input, null, options);
            if (source == null) {
                throw new IOException("invalid_banner");
            }
            Bitmap output = Bitmap.createBitmap(900, 360, Bitmap.Config.ARGB_8888);
            int cropWidth = Math.min(source.getWidth(), Math.round(source.getHeight() * 2.5f));
            int cropHeight = Math.min(source.getHeight(), Math.round(source.getWidth() / 2.5f));
            int left = (source.getWidth() - cropWidth) / 2;
            int top = (source.getHeight() - cropHeight) / 2;
            new Canvas(output).drawBitmap(source, new Rect(left, top, left + cropWidth, top + cropHeight),
                    new Rect(0, 0, 900, 360), new Paint(Paint.FILTER_BITMAP_FLAG));
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            output.compress(Bitmap.CompressFormat.JPEG, 87, stream);
            source.recycle();
            output.recycle();
            return stream.toByteArray();
        }
    }

    private static JSONObject json(String path, JSONObject body, String token) throws Exception {
        byte[] bytes = request(path, body, token);
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }

    private static byte[] request(String path, JSONObject body, String token) throws Exception {
        if (!online()) {
            throw new IOException("service_unconfigured");
        }
        HttpURLConnection request = (HttpURLConnection) new URL(BuildConfig.HEARTGRAM_PROFILE_URL + path).openConnection();
        request.setInstanceFollowRedirects(false);
        request.setConnectTimeout(6500);
        request.setReadTimeout(6500);
        if (token != null) {
            request.setRequestProperty("Authorization", "Bearer " + token);
        }
        try {
            if (body != null) {
                request.setRequestMethod("POST");
                request.setDoOutput(true);
                request.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                request.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream stream = request.getOutputStream()) {
                    stream.write(bytes);
                }
            }
            int status = request.getResponseCode();
            if (status >= 200 && status < 300) {
                try (InputStream stream = request.getInputStream()) {
                    return read(stream);
                }
            }
            if (status == 401) {
                throw new IOException("link_account");
            }
            try (InputStream stream = request.getErrorStream()) {
                JSONObject failure = new JSONObject(new String(read(stream), StandardCharsets.UTF_8));
                throw new IOException(failure.optString("error", "service_error"));
            }
        } finally {
            request.disconnect();
        }
    }

    private static byte[] read(InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) != -1) {
            if (result.size() + length > MAX_BANNER) {
                throw new IOException("payload_too_large");
            }
            result.write(buffer, 0, length);
        }
        return result.toByteArray();
    }

    private static void write(AtomicFile file, byte[] data) throws IOException {
        FileOutputStream stream = file.startWrite();
        try {
            stream.write(data);
            file.finishWrite(stream);
        } catch (IOException e) {
            file.failWrite(stream);
            throw e;
        }
    }

    private static String token(long uid) throws IOException {
        if (!linked(uid)) {
            throw new IOException("link_account");
        }
        return new String(file("session", uid).readFully(), StandardCharsets.US_ASCII);
    }

    public static void clearSession(long uid) {
        file("session", uid).delete();
        file("link", uid).delete();
    }

    private static AtomicFile file(String kind, long uid) {
        return new AtomicFile(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "heartgram_" + kind + "_" + uid));
    }

    private static SharedPreferences preferences(long uid) {
        return ApplicationLoader.applicationContext.getSharedPreferences("heartgram_profile_" + uid, Context.MODE_PRIVATE);
    }

    private static <T> void async(Job<T> job, Result<T> callback) {
        requests.postRunnable(() -> {
            try {
                T result = job.run();
                AndroidUtilities.runOnUIThread(() -> callback.complete(result, null));
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> callback.complete(null, e.getMessage()));
            }
        });
    }

    private HeartGramProfiles() {
    }
}
