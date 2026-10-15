package org.mcstat.spigot;

import org.bukkit.configuration.file.FileConfiguration;
import org.mcstat.common.config.McStatConfig;

final class SpigotConfigLoader {

    private SpigotConfigLoader() {}

    static McStatConfig load(FileConfiguration config) {
        McStatConfig mc = new McStatConfig();
        mc.setApiKey(config.getString("api-key", "YOUR_API_KEY_HERE"));
        mc.setApiBaseUrl(config.getString("api-base-url", "https://mcstat.org"));
        mc.setLanguage(config.getString("language", "en"));
        mc.setVoteUrl(config.getString("vote-url", ""));
        mc.setSendTps(config.getBoolean("metrics.tps", true));
        mc.setTrackPlayerTime(config.getBoolean("metrics.player-playtime", true));
        mc.setSendPlayerEvents(config.getBoolean("metrics.player-events", true));
        mc.setTrackGameplay(config.getBoolean("metrics.gameplay", true));
        mc.setSendClientInfo(config.getBoolean("metrics.client-info", true));
        mc.setSendCountry(config.getBoolean("metrics.country", true));
        mc.setSendJoinAddress(config.getBoolean("metrics.join-address", true));
        mc.setUpdateChecksEnabled(config.getBoolean("updates.check", true));
        mc.setUpdateNotifyAdmins(config.getBoolean("updates.notify-admins", true));
        mc.setUpdateAutoDownload(config.getBoolean("updates.auto-download", false));
        mc.setUpdateCheckIntervalHours(config.getInt("updates.check-interval-hours", 24));
        return mc;
    }
}
