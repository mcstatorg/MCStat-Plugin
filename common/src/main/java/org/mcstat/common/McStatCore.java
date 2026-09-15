package org.mcstat.common;

import org.mcstat.common.api.McStatApiClient;
import org.mcstat.common.config.McStatConfig;
import org.mcstat.common.model.*;
import org.mcstat.common.queue.DataQueue;
import org.mcstat.common.tracker.PlayerTracker;
import org.mcstat.common.update.McStatUpdateService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class McStatCore {

    private final McStatConfig config;
    private McStatApiClient apiClient;
    private final DataQueue dataQueue;
    private final PlayerTracker playerTracker;
    private final Logger logger;
    private final ScheduledExecutorService scheduler;
    private final long startTime;

    private Supplier<ServerHeartbeat> heartbeatSupplier;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> updateTask;
    private volatile boolean apiKeyValid = false;
    private volatile String serverName = "Unknown";
    private volatile String serverSlug = null;
    private String pluginVersion = "1.1.5";
    private final String installationId;
    private McStatUpdateService updateService;

    public McStatCore(McStatConfig config, Path dataDirectory, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.installationId = loadOrCreateInstallationId(dataDirectory);
        this.apiClient = new McStatApiClient(config, installationId, logger);
        this.dataQueue = new DataQueue();
        this.playerTracker = new PlayerTracker(dataDirectory, logger);
        this.startTime = System.currentTimeMillis();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "McStat-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    private String loadOrCreateInstallationId(Path dataDirectory) {
        Path file = dataDirectory.resolve("installation.id");
        try {
            Files.createDirectories(dataDirectory);
            if (Files.exists(file)) {
                return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
            }
            String id = UUID.randomUUID().toString();
            Files.write(file, id.getBytes(StandardCharsets.UTF_8));
            return id;
        } catch (IOException e) {
            logger.warning("[McStat] Could not load/create installation ID: " + e.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    public synchronized void enable() {
        logger.info("========================================");
        logger.info("  McStat Plugin v" + pluginVersion);
        logger.info("  https://mcstat.org");
        logger.info("========================================");

        scheduleUpdateChecks();

        if (!config.isValid()) {
            logger.warning("[McStat] Invalid API key! Please set your API key in config.yml");
            logger.warning("[McStat] Get your API key at https://mcstat.org/dashboard");
            return;
        }

        validateConnection();
        scheduleHeartbeat();

        logger.info("[McStat] Plugin enabled - sending data every "
                + config.getSyncIntervalSeconds() + "s");
    }

    public synchronized void disable() {
        logger.info("[McStat] Shutting down...");

        playerTracker.saveAllSessions();

        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }

        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }

        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        apiClient.shutdown();
        if (updateService != null) {
            updateService.shutdown();
        }

        logger.info("[McStat] Plugin disabled");
    }

    public void onPlayerJoin(UUID uuid, String name, String serverName) {
        if (config.isTrackPlayerTime()) {
            playerTracker.startSession(uuid, name);
        }

        if (config.isSendPlayerEvents() && config.isValid() && apiKeyValid) {
            apiClient.sendPlayerJoin(uuid.toString(), name, null);
        }
    }

    public void onPlayerLeave(UUID uuid, String name, String serverName) {
        if (config.isTrackPlayerTime()) {
            playerTracker.endSession(uuid);
        }

        if (config.isSendPlayerEvents() && config.isValid() && apiKeyValid) {
            apiClient.sendPlayerLeave(uuid.toString(), null);
        }
    }

    public void setHeartbeatSupplier(Supplier<ServerHeartbeat> supplier) {
        this.heartbeatSupplier = supplier;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    private void sendHeartbeat() {
        try {
            McStatApiClient currentClient = apiClient;
            if (!apiKeyValid) {
                apiKeyValid = currentClient.validateApiKey();
                if (!apiKeyValid) return;
                logger.info("[McStat] API key validated successfully");
                serverSlug = currentClient.validateAndGetServerSlug();
                if (serverSlug != null) {
                    logger.info("[McStat] Server slug: " + serverSlug);
                }
            }

            ServerHeartbeat heartbeat;
            if (heartbeatSupplier != null) {
                heartbeat = heartbeatSupplier.get();
            } else {
                heartbeat = new ServerHeartbeat();
            }

            heartbeat.setTimestamp(System.currentTimeMillis());
            heartbeat.setUptimeMillis(System.currentTimeMillis() - startTime);
            if (config.isTrackPlayerTime()) {
                heartbeat.setPlayers(playerTracker.getOnlinePlayers());
            }

            if (!dataQueue.isEmpty()) {
                List<ServerHeartbeat> queued = dataQueue.drainUpTo(3);
                if (queued.size() < 3) {
                    queued.add(heartbeat);
                } else {
                    dataQueue.enqueue(heartbeat);
                }
                for (ServerHeartbeat hb : queued) {
                    currentClient.sendServerStats(hb, new McStatApiClient.ApiCallback() {
                        @Override
                        public void onSuccess() {}

                        @Override
                        public void onFailure(Exception e) {
                            dataQueue.enqueue(hb);
                        }
                    });
                }
            } else {
                currentClient.sendServerStats(heartbeat, new McStatApiClient.ApiCallback() {
                    @Override
                    public void onSuccess() {}

                    @Override
                    public void onFailure(Exception e) {
                        dataQueue.enqueue(heartbeat);
                    }
                });
            }
        } catch (Exception e) {
            logger.warning("[McStat] Error building heartbeat: " + e.getMessage());
        }
    }

    public synchronized boolean reload(McStatConfig newConfig) {
        boolean wasTrackingPlayTime = config.isTrackPlayerTime();
        config.copyFrom(newConfig);

        McStatApiClient oldClient = apiClient;
        apiClient = new McStatApiClient(config, installationId, logger);
        oldClient.shutdown();

        apiKeyValid = false;
        serverSlug = null;

        if (wasTrackingPlayTime && !config.isTrackPlayerTime()) {
            playerTracker.saveAllSessions();
        }

        if (!config.isValid()) {
            cancelHeartbeat();
            scheduleUpdateChecks();
            logger.warning("[McStat] Configuration reloaded, but API key is not configured.");
            return false;
        }

        validateConnection();
        scheduleHeartbeat();
        scheduleUpdateChecks();
        logger.info("[McStat] Configuration reloaded.");
        return apiKeyValid;
    }

    private void validateConnection() {
        logger.info("[McStat] Validating API key...");
        apiKeyValid = apiClient.validateApiKey();
        if (apiKeyValid) {
            logger.info("[McStat] API key validated successfully");
            serverSlug = apiClient.validateAndGetServerSlug();
            if (serverSlug != null) {
                logger.info("[McStat] Server slug: " + serverSlug);
            }
        } else {
            logger.warning("[McStat] API key validation failed - will retry on next heartbeat");
        }
    }

    private void scheduleHeartbeat() {
        cancelHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(this::sendHeartbeat,
                config.getSyncIntervalSeconds(),
                config.getSyncIntervalSeconds(),
                TimeUnit.SECONDS);
    }

    private void cancelHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void scheduleUpdateChecks() {
        cancelUpdateChecks();
        if (updateService == null || !config.isUpdateChecksEnabled()) {
            return;
        }

        long intervalHours = config.getUpdateCheckIntervalHours();
        updateTask = scheduler.scheduleAtFixedRate(() -> runUpdateCheck(config.isUpdateAutoDownload(), true),
                10,
                TimeUnit.HOURS.toSeconds(intervalHours),
                TimeUnit.SECONDS);
    }

    private void cancelUpdateChecks() {
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
    }

    private void runUpdateCheck(boolean download, boolean background) {
        if (updateService == null) {
            return;
        }

        try {
            McStatUpdateService.UpdateInfo info = updateService.check(pluginVersion);
            if (!info.isUpdateAvailable()) {
                if (!background) {
                    logger.info("[McStat] " + info.getSummary());
                }
                return;
            }

            if (config.isUpdateNotifyAdmins()) {
                logger.warning("[McStat] " + info.getSummary() + " Release: " + info.getReleaseUrl());
            }

            if (download) {
                Path downloaded = updateService.download(info);
                logger.warning("[McStat] Update downloaded to " + downloaded
                        + ". Stop the server, replace the old jar, then start it again.");
            }
        } catch (Exception e) {
            if (!background) {
                logger.warning("[McStat] Update check failed: " + e.getMessage());
            }
        }
    }

    public synchronized String checkForUpdates(boolean download) {
        if (updateService == null) {
            return "§6[McStat] §cUpdate checker is not available for this platform.";
        }

        try {
            McStatUpdateService.UpdateInfo info = updateService.check(pluginVersion);
            if (!info.isUpdateAvailable()) {
                return "§6[McStat] §a" + info.getSummary();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("§6[McStat] §e").append(info.getSummary()).append("\n");
            sb.append("§7Release: §b").append(info.getReleaseUrl()).append("\n");

            if (download) {
                Path downloaded = updateService.download(info);
                sb.append("§aDownloaded: §f").append(downloaded).append("\n");
                sb.append("§7Stop the server, replace the old jar, then start it again.");
            } else if (info.canDownload()) {
                sb.append("§7Run §e/mcstat update download §7to download it safely.");
            } else {
                sb.append("§cThe release is missing the expected jar asset.");
            }
            return sb.toString();
        } catch (Exception e) {
            return "§6[McStat] §cUpdate check failed: " + e.getMessage();
        }
    }

    public String getUpdateStatusMessage() {
        if (updateService == null) {
            return "Update checker is not available.";
        }
        return updateService.getLastMessage();
    }

    public void setPluginVersion(String version) {
        this.pluginVersion = version;
    }

    public void configureUpdater(String artifactPrefix, Path dataDirectory) {
        if (artifactPrefix == null || artifactPrefix.trim().isEmpty() || dataDirectory == null) {
            return;
        }
        this.updateService = new McStatUpdateService(artifactPrefix.trim(), dataDirectory.resolve("updates"), logger);
    }

    public String getPluginVersion() { return pluginVersion; }
    public String getInstallationId() { return installationId; }
    public McStatConfig getConfig() { return config; }
    public McStatApiClient getApiClient() { return apiClient; }
    public PlayerTracker getPlayerTracker() { return playerTracker; }
    public DataQueue getDataQueue() { return dataQueue; }
    public boolean isApiKeyValid() { return apiKeyValid; }
    public long getUptimeMillis() { return System.currentTimeMillis() - startTime; }
    public String getServerSlug() { return serverSlug; }

    public String getVoteUrl() {
        if (config.getVoteUrl() != null && !config.getVoteUrl().isEmpty()) {
            return config.getVoteUrl();
        }
        if (serverSlug != null) {
            return config.getApiBaseUrl() + "/tr/servers/" + serverSlug + "#vote";
        }
        return null;
    }
}
