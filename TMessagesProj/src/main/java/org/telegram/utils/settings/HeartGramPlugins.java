package org.telegram.utils.settings;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public final class HeartGramPlugins {
    public static final BooleanSetting enabled = BooleanSetting.of("heartgram_plugins_enabled", true);
    private static final String INVENTORY = "heartgram_plugins_inventory_v1";
    private static final String CONSOLE = "heartgram_plugins_console_v1";
    private static final int MAX_BYTES = 64 * 1024;
    private static final int MAX_PLUGINS = 24;
    private static final int MAX_LOG_LINES = 96;

    public static final class Plugin {
        private final JSONObject manifest;
        public final String id, name, version, description, label, icon, title, message;
        public final boolean active;

        private Plugin(JSONObject manifest, boolean active) throws JSONException, IOException {
            this.manifest = manifest;
            this.active = active;
            JSONObject info = manifest.getJSONObject("manifest");
            if (!(info.opt("api") instanceof Number) || info.optDouble("api") != 1d) {
                throw new IOException("manifest.api: 1");
            }
            id = text(info, "id", 64);
            if (!id.matches("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+")) {
                throw new IOException("manifest.id: invalid identifier");
            }
            name = text(info, "name", 48);
            version = text(info, "version", 20);
            description = text(info, "description", 260);
            JSONObject chip = manifest.getJSONObject("homeChip");
            label = text(chip, "label", 30);
            icon = text(chip, "icon", 12);
            if (!icon.equals("spark") && !icon.equals("heart") && !icon.equals("orbit")) {
                throw new IOException("homeChip.icon: spark, heart, orbit");
            }
            JSONObject action = chip.getJSONObject("onTap");
            if (!"dialog".equals(action.optString("type"))) {
                throw new IOException("homeChip.onTap.type: dialog");
            }
            title = text(action, "title", 64);
            message = text(action, "message", 700);
        }
    }

    public static Plugin install(Context context, Uri input) throws IOException, JSONException {
        try (InputStream stream = context.getContentResolver().openInputStream(input)) {
            if (stream == null) {
                throw new IOException("Could not open plugin file");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int count;
            while ((count = stream.read(chunk)) != -1) {
                if (bytes.size() + count > MAX_BYTES) {
                    throw new IOException("File exceeds 64 KiB");
                }
                bytes.write(chunk, 0, count);
            }
            String source = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (source.startsWith("\uFEFF")) {
                source = source.substring(1);
            }
            return storePlugin(parse(source));
        }
    }

    static Plugin parse(String source) throws JSONException, IOException {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char token = source.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (token == '\\') {
                    escaped = true;
                } else if (token == '"') {
                    inString = false;
                }
            } else if (token == '"') {
                inString = true;
            } else if (token == '{' || token == '[') {
                if (++depth > 12) {
                    throw new IOException("JSON nesting exceeds 12 levels");
                }
            } else if (token == '}' || token == ']') {
                if (--depth < 0) {
                    throw new IOException("Unbalanced JSON");
                }
            } else if (token == '/' || token == '#' || token == '\'' || token == '\u0000') {
                throw new IOException("Use standard JSON with double-quoted strings");
            }
        }
        if (depth != 0 || inString) {
            throw new IOException("Incomplete JSON");
        }
        JSONTokener reader = new JSONTokener(source);
        Object document = reader.nextValue();
        if (!(document instanceof JSONObject) || reader.nextClean() != 0) {
            throw new IOException("Expected one JSON object");
        }
        return new Plugin((JSONObject) document, false);
    }

    private static synchronized Plugin storePlugin(Plugin parsed) throws JSONException, IOException {
        JSONArray inventory = readArray(INVENTORY);
        for (int i = 0; i < inventory.length(); i++) {
            JSONObject existing = inventory.getJSONObject(i);
            if (parsed.id.equals(existing.optString("id"))) {
                Plugin updated = new Plugin(parsed.manifest, existing.optBoolean("active"));
                inventory.put(i, record(updated));
                storeInventory(inventory);
                log(parsed.id, "INFO", "updated to " + parsed.version);
                return updated;
            }
        }
        if (inventory.length() >= MAX_PLUGINS) {
            throw new IOException("Plugin limit reached: " + MAX_PLUGINS);
        }
        inventory.put(record(parsed));
        storeInventory(inventory);
        log(parsed.id, "INFO", "installed " + parsed.version);
        return parsed;
    }

    public static synchronized ArrayList<Plugin> installed() {
        JSONArray inventory = readArray(INVENTORY);
        ArrayList<Plugin> plugins = new ArrayList<>(inventory.length());
        for (int i = 0; i < inventory.length(); i++) {
            JSONObject entry = inventory.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            try {
                plugins.add(new Plugin(entry.getJSONObject("body"), entry.optBoolean("active")));
            } catch (IOException | JSONException invalid) {
                // Installed rows are validated before being written. A damaged entry does not block other plugins.
            }
        }
        return plugins;
    }

    public static synchronized void toggle(Plugin plugin) throws JSONException {
        JSONArray inventory = readArray(INVENTORY);
        for (int i = 0; i < inventory.length(); i++) {
            JSONObject entry = inventory.optJSONObject(i);
            if (entry != null && plugin.id.equals(entry.optString("id"))) {
                boolean active = !entry.optBoolean("active");
                entry.put("active", active);
                storeInventory(inventory);
                log(plugin.id, "INFO", active ? "enabled" : "disabled");
                return;
            }
        }
    }

    public static synchronized void remove(Plugin plugin) {
        JSONArray inventory = readArray(INVENTORY);
        for (int i = 0; i < inventory.length(); i++) {
            JSONObject entry = inventory.optJSONObject(i);
            if (entry != null && plugin.id.equals(entry.optString("id"))) {
                inventory.remove(i);
                storeInventory(inventory);
                log(plugin.id, "INFO", "removed");
                return;
            }
        }
    }

    public static synchronized void log(String id, String level, String message) {
        JSONArray lines = readArray(CONSOLE);
        while (lines.length() >= MAX_LOG_LINES) {
            lines.remove(0);
        }
        try {
            String detail = message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
            if (detail.length() > 300) {
                detail = detail.substring(0, 300);
            }
            lines.put(new JSONObject().put("id", id).put("time", System.currentTimeMillis())
                    .put("level", level).put("message", detail));
            SettingsPreferences.get().edit().putString(CONSOLE, lines.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public static synchronized String console(String pluginId) {
        JSONArray lines = readArray(CONSOLE);
        DateFormat time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
        StringBuilder output = new StringBuilder();
        for (int i = lines.length() - 1; i >= 0; i--) {
            JSONObject line = lines.optJSONObject(i);
            if (line != null && (pluginId == null || pluginId.equals(line.optString("id")))) {
                output.append(time.format(new Date(line.optLong("time")))).append("  ")
                        .append(line.optString("level")).append("  ").append(line.optString("id"))
                        .append('\n').append(line.optString("message")).append("\n\n");
            }
        }
        return output.toString().trim();
    }

    public static synchronized void clearConsole() {
        SettingsPreferences.get().edit().remove(CONSOLE).apply();
    }

    private static JSONObject record(Plugin plugin) throws JSONException {
        return new JSONObject().put("id", plugin.id).put("active", plugin.active).put("body", plugin.manifest);
    }

    private static void storeInventory(JSONArray inventory) {
        SettingsPreferences.get().edit().putString(INVENTORY, inventory.toString()).apply();
    }

    private static JSONArray readArray(String key) {
        try {
            return new JSONArray(SettingsPreferences.get().getString(key, "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private static String text(JSONObject object, String name, int max) throws IOException {
        Object raw = object.opt(name);
        if (!(raw instanceof String)) {
            throw new IOException(name + ": expected text");
        }
        String value = ((String) raw).trim();
        if (value.isEmpty() || value.length() > max) {
            throw new IOException(name + ": expected 1–" + max + " characters");
        }
        return value;
    }

    private HeartGramPlugins() {
    }
}
