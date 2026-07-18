package org.mcstat.common.command;

import org.mcstat.common.McStatCore;

import java.util.concurrent.TimeUnit;

public class McStatCommandHandler {

    private final McStatCore core;

    public McStatCommandHandler(McStatCore core) {
        this.core = core;
    }

    public String handleCommand(String[] args) {
        if (args.length == 0) {
            return getHelpMessage();
        }

        switch (args[0].toLowerCase()) {
            case "status":
                return getStatusMessage();
            case "link":
                return getLinkMessage();
            case "reload":
                return null;
            case "update":
                return getUpdateMessage(args);
            default:
                return getHelpMessage();
        }
    }

    private String getStatusMessage() {
        long uptimeMs = core.getUptimeMillis();
        long hours = TimeUnit.MILLISECONDS.toHours(uptimeMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(uptimeMs) % 60;

        int queueSize = core.getDataQueue().size();
        int onlinePlayers = core.getPlayerTracker().getOnlineCount();

        StringBuilder sb = new StringBuilder();
        sb.append("§6§l[McStat] §eStatus\n");
        sb.append("§7API Key: §a").append(core.getConfig().isValid() ? "Configured" : "§cNot Set").append("\n");
        sb.append("§7API: ").append(core.isApiKeyValid() ? "§aConnected" : "§cDisconnected").append("\n");
        sb.append("§7Uptime: §f").append(hours).append("h ").append(minutes).append("m ").append(seconds).append("s\n");
        sb.append("§7Queue: §f").append(queueSize).append(" pending\n");
        sb.append("§7Tracked Players: §f").append(onlinePlayers).append("\n");
        sb.append("§7Version: §f").append(core.getPluginVersion()).append("\n");
        sb.append("§7Updates: §f").append(core.getUpdateStatusMessage());

        return sb.toString();
    }

    private String getLinkMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6§l[McStat] §eAPI Key Info\n");
        if (core.getConfig().isValid()) {
            String key = core.getConfig().getApiKey();
            String masked = key.substring(0, Math.min(10, key.length())) + "****";
            sb.append("§7Key: §f").append(masked).append("\n");
        } else {
            sb.append("§cNo API key configured!\n");
        }
        sb.append("§7Dashboard: §bhttps://mcstat.org/dashboard");
        return sb.toString();
    }

    private String getUpdateMessage(String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("download")) {
            return core.checkForUpdates(true);
        }
        return core.checkForUpdates(false);
    }

    public String getVoteMessage() {
        String voteUrl = core.getVoteUrl();
        if (voteUrl == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("§c§l[McStat] §cCould not retrieve vote link!\n");
            if (!core.getConfig().isValid()) {
                sb.append("§7API key is not configured. Use §e/mcstat link §7to check.");
            } else if (!core.isApiKeyValid()) {
                sb.append("§7API connection failed. Use §e/mcstat status §7to check.");
            } else {
                sb.append("§7Server info not available. Please try again later.");
            }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§6§l[McStat] §eVote\n");
        sb.append("§7Vote for our server: §b").append(voteUrl).append("\n");
        sb.append("§7Every vote supports us!");
        return sb.toString();
    }

    private String getHelpMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6§l[McStat] §eCommands\n");
        sb.append("§e/mcstat status §7- Connection status & stats\n");
        sb.append("§e/mcstat reload §7- Reload configuration\n");
        sb.append("§e/mcstat update §7- Check for plugin updates\n");
        sb.append("§e/mcstat update download §7- Download the latest jar safely\n");
        sb.append("§e/mcstat link §7- API key info & dashboard link\n");
        sb.append("§e/vote §7- Sunucumuza oy ver");
        return sb.toString();
    }
}
