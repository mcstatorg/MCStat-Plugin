package org.mcstat.velocity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.mcstat.common.config.McStatConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class VelocityConfigLoader {

    public static McStatConfig load(Path dataDirectory, Logger logger) {
        McStatConfig mcConfig = new McStatConfig();
        mcConfig.setSendTps(false);

        try {
            Files.createDirectories(dataDirectory);
            Path configFile = dataDirectory.resolve("config.json");

            if (!Files.exists(configFile)) {
                JsonObject defaults = new JsonObject();
                defaults.addProperty("api-key", "YOUR_API_KEY_HERE");
                defaults.addProperty("api-base-url", "https://mcstat.org");
                defaults.addProperty("sync-interval", 90);
                defaults.addProperty("vote-url", "");

                JsonObject metrics = new JsonObject();
                metrics.addProperty("player-count", true);
                metrics.addProperty("player-names", true);
                metrics.addProperty("player-playtime", true);
                metrics.addProperty("player-events", true);
                defaults.add("metrics", metrics);

                try (Writer writer = new OutputStreamWriter(
                        Files.newOutputStream(configFile), StandardCharsets.UTF_8)) {
                    new Gson().toJson(defaults, writer);
                }
            }

            try (Reader reader = new InputStreamReader(
                    Files.newInputStream(configFile), StandardCharsets.UTF_8)) {
                JsonObject config = new Gson().fromJson(reader, JsonObject.class);

                if (config.has("api-key"))
                    mcConfig.setApiKey(config.get("api-key").getAsString());
                if (config.has("api-base-url"))
                    mcConfig.setApiBaseUrl(config.get("api-base-url").getAsString());
                if (config.has("sync-interval"))
                    mcConfig.setSyncIntervalSeconds(config.get("sync-interval").getAsInt());
                if (config.has("vote-url"))
                    mcConfig.setVoteUrl(config.get("vote-url").getAsString());

                if (config.has("metrics")) {
                    JsonObject metrics = config.getAsJsonObject("metrics");
                    if (metrics.has("player-playtime"))
                        mcConfig.setTrackPlayerTime(metrics.get("player-playtime").getAsBoolean());
                    if (metrics.has("player-events"))
                        mcConfig.setSendPlayerEvents(metrics.get("player-events").getAsBoolean());
                }
            }
        } catch (IOException e) {
            logger.warning("[McStat] Failed to load config: " + e.getMessage());
        }

        return mcConfig;
    }
}
