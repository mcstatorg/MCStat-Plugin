package org.mcstat.spigot;

import org.bukkit.configuration.file.FileConfiguration;
import org.mcstat.common.config.McStatConfig;

public class SpigotConfigLoader {

    public static McStatConfig load(FileConfiguration config) {
        McStatConfig mcConfig = new McStatConfig();

        mcConfig.setApiKey(config.getString("api-key", "YOUR_API_KEY_HERE"));
        mcConfig.setApiBaseUrl(config.getString("api-base-url", "https://mcstat.org"));
        mcConfig.setSyncIntervalSeconds(config.getInt("sync-interval", 90));
        mcConfig.setSendTps(config.getBoolean("metrics.tps", true));
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

        return mcConfig;
    }
}
