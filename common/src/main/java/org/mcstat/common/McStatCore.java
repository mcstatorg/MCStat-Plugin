package org.mcstat.common;

import org.mcstat.common.api.McStatApiClient;
import org.mcstat.common.config.McStatConfig;
import org.mcstat.common.model.*;
import org.mcstat.common.queue.DataQueue;
import org.mcstat.common.tracker.PlayerTracker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class McStatCore {

    private final McStatConfig config;
    private final McStatApiClient apiClient;
    private final DataQueue dataQueue;
    private final PlayerTracker playerTracker;
    private final Logger logger;
    private final ScheduledExecutorService scheduler;
    private final long startTime;

    private Supplier<ServerHeartbeat> heartbeatSupplier;
    private boolean apiKeyValid = false;
    private String serverName = "Unknown";
    private String serverSlug = null;
    private String pluginVersion = "1.1.0";
    private final String installationId;

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

    public void enable() {
        logger.info("========================================");
        logger.info("  McStat Plugin v" + pluginVersion);
        logger.info("  https://mcstat.org");
        logger.info("========================================");

        if (!config.isValid()) {
            logger.warning("[McStat] Invalid API key! Please set your API key in config.yml");
            logger.warning("[McStat] Get your API key at https://mcstat.org/dashboard");
            return;
        }

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

        scheduler.scheduleAtFixedRate(this::sendHeartbeat,
                config.getSyncIntervalSeconds(),
                config.getSyncIntervalSeconds(),
                TimeUnit.SECONDS);

        logger.info("[McStat] Plugin enabled - sending data every "
                + config.getSyncIntervalSeconds() + "s");
    }

    public void disable() {
        logger.info("[McStat] Shutting down...");

        playerTracker.saveAllSessions();

        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        apiClient.shutdown();

        logger.info("[McStat] Plugin disabled");
    }

    public void onPlayerJoin(UUID uuid, String name, String serverName) {
        playerTracker.startSession(uuid, name);

        if (config.isSendPlayerEvents()) {
            apiClient.sendPlayerJoin(uuid.toString(), name, null);
        }
    }

    public void onPlayerLeave(UUID uuid, String name, String serverName) {
        playerTracker.endSession(uuid);

        if (config.isSendPlayerEvents()) {
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
            if (!apiKeyValid) {
                apiKeyValid = apiClient.validateApiKey();
                if (!apiKeyValid) return;
                logger.info("[McStat] API key validated successfully");
                serverSlug = apiClient.validateAndGetServerSlug();
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
            heartbeat.setPlayers(playerTracker.getOnlinePlayers());

            if (!dataQueue.isEmpty()) {
                List<ServerHeartbeat> queued = dataQueue.drainAll();
                queued.add(heartbeat);
                for (ServerHeartbeat hb : queued) {
                    apiClient.sendServerStats(hb, new McStatApiClient.ApiCallback() {
                        @Override
                        public void onSuccess() {}

                        @Override
                        public void onFailure(Exception e) {
                            dataQueue.enqueue(hb);
                        }
                    });
                }
            } else {
                apiClient.sendServerStats(heartbeat, new McStatApiClient.ApiCallback() {
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

    public void setPluginVersion(String version) {
        this.pluginVersion = version;
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
