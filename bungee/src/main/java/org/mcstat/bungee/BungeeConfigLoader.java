package org.mcstat.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.mcstat.common.config.McStatConfig;

import java.io.*;
import java.nio.file.Files;

public class BungeeConfigLoader {

    public static McStatConfig load(Plugin plugin) {
        McStatConfig mcConfig = new McStatConfig();

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdir();
            }

            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                try (InputStream in = plugin.getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    }
                }
            }

            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(configFile);

            mcConfig.setApiKey(config.getString("api-key", "YOUR_API_KEY_HERE"));
            mcConfig.setApiBaseUrl(config.getString("api-base-url", "https://mcstat.org"));
            mcConfig.setSyncIntervalSeconds(config.getInt("sync-interval", 90));
            mcConfig.setSendTps(false);
            mcConfig.setTrackPlayerTime(config.getBoolean("metrics.player-playtime", true));
            mcConfig.setSendPlayerEvents(config.getBoolean("metrics.player-events", true));
            mcConfig.setUpdateChecksEnabled(config.getBoolean("updates.check", true));
            mcConfig.setUpdateNotifyAdmins(config.getBoolean("updates.notify-admins", true));
            mcConfig.setUpdateAutoDownload(config.getBoolean("updates.auto-download", false));
            mcConfig.setUpdateCheckIntervalHours(config.getInt("updates.check-interval-hours", 24));
            String voteUrl = config.getString("vote-url");
            if (voteUrl != null && !voteUrl.isEmpty()) {
                mcConfig.setVoteUrl(voteUrl);
            }

        } catch (IOException e) {
            plugin.getLogger().warning("[McStat] Failed to load config: " + e.getMessage());
        }

        return mcConfig;
    }
}
