package org.mcstat.velocity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mcstat.common.config.McStatConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Velocity keeps the config as plugins/mcstat/config.json. Keys mirror the
 * Bukkit config.yml; existing files from 1.1.x keep working (unknown keys such
 * as sync-interval are ignored — the heartbeat interval is set in the
 * MCStat dashboard now).
 */
final class VelocityConfigLoader {

    private VelocityConfigLoader() {}

    static McStatConfig load(Path dataDirectory, Logger logger) {
        McStatConfig mc = new McStatConfig();
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.json");
            if (!Files.exists(file)) writeDefaults(file);
            JsonObject config;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config = new Gson().fromJson(reader, JsonObject.class);
            }
            if (config == null) config = new JsonObject();
            mc.setApiKey(string(config, "api-key", "YOUR_API_KEY_HERE"));
            mc.setApiBaseUrl(string(config, "api-base-url", "https://mcstat.org"));
            mc.setLanguage(string(config, "language", "en"));
            mc.setVoteUrl(string(config, "vote-url", ""));
            JsonObject metrics = object(config, "metrics");
            mc.setSendTps(true);
            mc.setTrackPlayerTime(bool(metrics, "player-playtime", true));
            mc.setSendPlayerEvents(bool(metrics, "player-events", true));
            mc.setSendClientInfo(bool(metrics, "client-info", true));
            mc.setSendCountry(bool(metrics, "country", true));
            mc.setSendJoinAddress(bool(metrics, "join-address", true));
            JsonObject updates = object(config, "updates");
            mc.setUpdateChecksEnabled(bool(updates, "check", true));
            mc.setUpdateNotifyAdmins(bool(updates, "notify-admins", true));
            mc.setUpdateAutoDownload(bool(updates, "auto-download", false));
            mc.setUpdateCheckIntervalHours(integer(updates, "check-interval-hours", 24));
        } catch (IOException | RuntimeException e) {
            logger.warning("[McStat] Failed to load config.json: " + e.getMessage());
        }
        return mc;
    }

    private static void writeDefaults(Path file) throws IOException {
        JsonObject defaults = new JsonObject();
        defaults.addProperty("api-key", "YOUR_API_KEY_HERE");
        defaults.addProperty("api-base-url", "https://mcstat.org");
        defaults.addProperty("language", "en");
        defaults.addProperty("vote-url", "");
        JsonObject metrics = new JsonObject();
        metrics.addProperty("player-playtime", true);
        metrics.addProperty("player-events", true);
        metrics.addProperty("client-info", true);
        metrics.addProperty("country", true);
        metrics.addProperty("join-address", true);
        defaults.add("metrics", metrics);
        JsonObject updates = new JsonObject();
        updates.addProperty("check", true);
        updates.addProperty("notify-admins", true);
        updates.addProperty("auto-download", false);
        updates.addProperty("check-interval-hours", 24);
        defaults.add("updates", updates);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(defaults, writer);
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject parent, String key, String fallback) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static boolean bool(JsonObject parent, String key, boolean fallback) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject parent, String key, int fallback) {
        JsonElement value = parent.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
