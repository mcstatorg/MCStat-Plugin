package org.mcstat.common.command;

import org.mcstat.common.McStatCore;
import org.mcstat.common.config.McStatConfig;
import org.mcstat.common.i18n.Messages;
import org.mcstat.common.update.McStatUpdateService;

import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * /mcstat and /vote for every platform. Replies are lists of legacy-colour
 * lines in the sender's language; commands that need the network (reload,
 * update) answer later, from the worker thread, so the server's main thread
 * never waits on MCStat or GitHub.
 */
public final class McStatCommandHandler {

    private static final String PREFIX = "§6[McStat] ";

    private final McStatCore core;
    private final Supplier<McStatConfig> configLoader;

    public McStatCommandHandler(McStatCore core, Supplier<McStatConfig> configLoader) {
        this.core = core;
        this.configLoader = configLoader;
    }

    /** Admin command. `reply` may be called on another thread. */
    public void handle(String[] args, String language, Consumer<List<String>> reply) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status":
                reply.accept(status(language));
                return;
            case "link":
                reply.accept(link(language));
                return;
            case "reload":
                core.reload(configLoader.get(), result -> {
                    String key = result == McStatCore.ReloadResult.CONNECTED ? "reload.ok"
                            : result == McStatCore.ReloadResult.PENDING ? "reload.pending" : "reload.no-key";
                    String colour = result == McStatCore.ReloadResult.CONNECTED ? "§a" : result == McStatCore.ReloadResult.PENDING ? "§e" : "§c";
                    reply.accept(Arrays.asList(PREFIX + colour + Messages.get(language, key)));
                });
                return;
            case "update":
                boolean download = args.length >= 2 && args[1].equalsIgnoreCase("download");
                core.runAsync(() -> reply.accept(update(language, download)));
                return;
            default:
                reply.accept(help(language));
        }
    }

    public List<String> noPermission(String language) {
        return Arrays.asList("§c" + Messages.get(language, "no-permission"));
    }

    private List<String> help(String language) {
        List<String> lines = new ArrayList<>();
        lines.add("§6§l[McStat] §e" + Messages.get(language, "help.title"));
        lines.add("§e/mcstat status §7- " + Messages.get(language, "help.status"));
        lines.add("§e/mcstat reload §7- " + Messages.get(language, "help.reload"));
        lines.add("§e/mcstat update §7- " + Messages.get(language, "help.update"));
        lines.add("§e/mcstat update download §7- " + Messages.get(language, "help.update-download"));
        lines.add("§e/mcstat link §7- " + Messages.get(language, "help.link"));
        lines.add("§e/vote §7- " + Messages.get(language, "help.vote"));
        return lines;
    }

    private List<String> status(String language) {
        long uptime = core.getUptimeMillis();
        String uptimeText = Messages.get(language, "status.uptime-value", TimeUnit.MILLISECONDS.toHours(uptime), TimeUnit.MILLISECONDS.toMinutes(uptime) % 60);
        long lastSent = core.getSpool().lastSentAt();
        String lastSentText = lastSent > 0
                ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.forLanguageTag(Messages.resolve(language))).format(new Date(lastSent))
                : Messages.get(language, "status.never");

        List<String> lines = new ArrayList<>();
        lines.add("§6§l[McStat] §e" + Messages.get(language, "status.title"));
        lines.add(row(language, "status.api-key", core.getConfig().isValid() ? "§a" + Messages.get(language, "status.configured") : "§c" + Messages.get(language, "status.not-set")));
        lines.add(row(language, "status.connection", core.isConnected() ? "§a" + Messages.get(language, "status.connected") : "§c" + Messages.get(language, "status.disconnected")));
        if (core.getServerName() != null) lines.add(row(language, "status.server", "§f" + core.getServerName()));
        lines.add(row(language, "status.uptime", "§f" + uptimeText));
        lines.add(row(language, "status.queue", "§f" + Messages.get(language, "status.queue-value", core.getSpool().bufferedEvents(), core.getSpool().spooledBatches())));
        lines.add(row(language, "status.last-sent", "§f" + lastSentText));
        String error = core.getSpool().lastError() != null ? core.getSpool().lastError() : core.getLastConnectionError();
        if (error != null) lines.add(row(language, "status.last-error", "§c" + error));
        lines.add(row(language, "status.players", "§f" + core.getTrackedPlayers()));
        lines.add(row(language, "status.modules", modules(language)));
        lines.add(row(language, "status.version", "§f" + core.getPluginVersion() + " (" + core.getPlatformName() + ")"));
        lines.add(row(language, "status.updates", "§f" + updateState(language)));
        return lines;
    }

    private String modules(String language) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Boolean> module : core.getRemoteConfig().modules().entrySet()) {
            if (sb.length() > 0) sb.append("§7, ");
            sb.append(module.getValue() ? "§a" : "§c").append(Messages.get(language, "module." + module.getKey()));
        }
        return sb.toString();
    }

    private String updateState(String language) {
        McStatUpdateService service = core.getUpdateService();
        McStatUpdateService.UpdateInfo info = service == null ? null : service.getLastInfo();
        if (info == null) return Messages.get(language, "status.update-unknown");
        return info.isUpdateAvailable()
                ? Messages.get(language, "status.update-available", info.getLatestVersion())
                : Messages.get(language, "status.update-current");
    }

    private static String row(String language, String key, String value) {
        return "§7" + Messages.get(language, key) + ": " + value;
    }

    private List<String> link(String language) {
        List<String> lines = new ArrayList<>();
        lines.add("§6§l[McStat] §e" + Messages.get(language, "link.title"));
        if (core.getConfig().isValid()) {
            String key = core.getConfig().getApiKey();
            lines.add(row(language, "link.key", "§f" + key.substring(0, Math.min(10, key.length())) + "****"));
        } else {
            lines.add("§c" + Messages.get(language, "link.no-key"));
        }
        lines.add(row(language, "link.dashboard", "§b" + core.getSiteUrl() + "/dashboard"));
        return lines;
    }

    private List<String> update(String language, boolean download) {
        McStatUpdateService service = core.getUpdateService();
        if (service == null) return Arrays.asList(PREFIX + "§c" + Messages.get(language, "update.unavailable"));
        try {
            McStatUpdateService.UpdateInfo info = service.check(core.getPluginVersion());
            if (!info.isUpdateAvailable()) {
                return Arrays.asList(PREFIX + "§a" + Messages.get(language, "update.current", info.getCurrentVersion()));
            }
            List<String> lines = new ArrayList<>();
            lines.add(PREFIX + "§e" + Messages.get(language, info.canDownload() ? "update.available" : "update.missing-asset", info.getLatestVersion()));
            lines.add("§7" + Messages.get(language, "update.release", "§b" + info.getReleaseUrl()));
            if (download && info.canDownload()) {
                Path downloaded = service.download(info);
                lines.add("§a" + Messages.get(language, "update.downloaded", "§f" + downloaded));
                lines.add("§7" + Messages.get(language, "update.restart-hint"));
            } else if (info.canDownload()) {
                lines.add("§7" + Messages.get(language, "update.download-hint"));
            }
            return lines;
        } catch (Exception e) {
            return Arrays.asList(PREFIX + "§c" + Messages.get(language, "update.failed", e.getMessage()));
        }
    }

    /** /vote in the player's language. */
    public List<String> vote(String language) {
        String url = core.getVoteUrl(language);
        List<String> lines = new ArrayList<>();
        if (url == null) {
            lines.add("§c§l[McStat] §c" + Messages.get(language, "vote.unavailable"));
            String reason = !core.getConfig().isValid() ? "vote.no-key" : !core.isConnected() ? "vote.not-connected" : "vote.try-later";
            lines.add("§7" + Messages.get(language, reason));
            return lines;
        }
        lines.add("§6§l[McStat] §e" + Messages.get(language, "vote.title"));
        lines.add("§7" + Messages.get(language, "vote.link", "§b" + url));
        lines.add("§7" + Messages.get(language, "vote.thanks"));
        return lines;
    }
}
