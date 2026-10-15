package org.mcstat.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.mcstat.common.api.McStatApiClient;
import org.mcstat.common.config.McStatConfig;
import org.mcstat.common.config.RemoteConfig;
import org.mcstat.common.geo.GeoIpService;
import org.mcstat.common.i18n.Messages;
import org.mcstat.common.platform.JoinInfo;
import org.mcstat.common.platform.PlatformAdapter;
import org.mcstat.common.platform.ServerSample;
import org.mcstat.common.session.GameplayTracker;
import org.mcstat.common.session.SessionTracker;
import org.mcstat.common.spool.BatchSpool;
import org.mcstat.common.update.McStatUpdateService;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * The platform-independent plugin: sessions and AFK, the heartbeat, the
 * handshake with MCStat and delivery of everything through the batch spool.
 *
 * All network and disk work happens on one background thread ("McStat-Worker").
 * Game threads only touch the session tracker and the spool's in-memory
 * buffer, both lock-free for the common path.
 */
public class McStatCore {

    /** Seconds between spool flushes: how quickly a join reaches the dashboard. */
    private static final int FLUSH_SECONDS = 5;
    /** Delay before a join is reported, so the client has sent its brand and locale. */
    private static final int JOIN_DESCRIBE_DELAY_SECONDS = 3;
    private static final long HELLO_INTERVAL_HOURS = 6;
    private static final long MAX_BACKOFF_SECONDS = 300;

    private final McStatConfig config;
    private final PlatformAdapter platform;
    private final Path dataDirectory;
    private final Logger logger;
    private final String installationId;
    private final SessionTracker sessions = new SessionTracker();
    private final BatchSpool spool;
    private final GeoIpService geo;
    private final ScheduledExecutorService worker;
    private final long startTime = System.currentTimeMillis();
    private final boolean afkSupported;
    private final boolean gameplaySupported;
    private final GameplayTracker gameplay = new GameplayTracker();

    private volatile McStatApiClient apiClient;
    private volatile RemoteConfig remote;
    private volatile boolean connected;
    private volatile String serverSlug;
    private volatile String serverName;
    /** Public MCStat address from the handshake; vote and dashboard links use it. */
    private volatile String siteUrl;
    private volatile String lastConnectionError;
    private volatile String pluginVersion = "unknown";
    private McStatUpdateService updateService;

    private final Object heartbeatLock = new Object();
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> flushTask;
    private ScheduledFuture<?> helloTask;
    private ScheduledFuture<?> updateTask;
    private ScheduledFuture<?> geoTask;
    private volatile int heartbeatSeconds;
    private int failures;
    private long nextSendAt;

    public McStatCore(McStatConfig config, PlatformAdapter platform, Path dataDirectory, Logger logger) {
        this.config = config;
        this.platform = platform;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.installationId = loadOrCreateInstallationId(dataDirectory);
        this.spool = new BatchSpool(dataDirectory, logger);
        this.geo = new GeoIpService(dataDirectory, logger);
        // AFK needs movement events; proxies only see chat and commands, so on
        // a proxy every quiet player would count as AFK.
        this.afkSupported = platform.capabilities().contains("afk");
        this.gameplaySupported = platform.capabilities().contains("gameplay");
        this.remote = RemoteConfig.DEFAULT.restrictedBy(config).restrictedByPlatform(afkSupported, gameplaySupported);
        this.apiClient = new McStatApiClient(config, installationId, logger);
        this.worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "McStat-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    private String loadOrCreateInstallationId(Path dir) {
        Path file = dir.resolve("installation.id");
        try {
            Files.createDirectories(dir);
            if (Files.exists(file)) {
                String id = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
                if (id.matches("[0-9a-fA-F-]{36}")) return id;
            }
            String id = UUID.randomUUID().toString();
            Files.write(file, id.getBytes(StandardCharsets.UTF_8));
            return id;
        } catch (IOException e) {
            logger.warning("Could not load/create installation ID: " + e.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    public synchronized void enable() {
        logger.info("McStat v" + pluginVersion + " (" + platform.platform() + ") - https://mcstat.org");
        spool.recover();
        scheduleUpdateChecks();
        if (!config.isValid()) {
            logger.warning("No API key set. Add api-key to the config, then run /mcstat reload.");
            logger.warning("Get your key at https://mcstat.org/dashboard");
            return;
        }
        start();
    }

    private void start() {
        worker.execute(this::handshake);
        flushTask = worker.scheduleWithFixedDelay(this::flush, FLUSH_SECONDS, FLUSH_SECONDS, TimeUnit.SECONDS);
        helloTask = worker.scheduleWithFixedDelay(this::handshake, HELLO_INTERVAL_HOURS, HELLO_INTERVAL_HOURS, TimeUnit.HOURS);
        geoTask = worker.scheduleWithFixedDelay(this::refreshGeo, 1, 24 * 60, TimeUnit.MINUTES);
        rescheduleHeartbeat(remote.heartbeatSeconds);
    }

    private void stopTasks() {
        for (ScheduledFuture<?> task : new ScheduledFuture<?>[] { flushTask, helloTask, geoTask }) {
            if (task != null) task.cancel(false);
        }
        flushTask = helloTask = geoTask = null;
        synchronized (heartbeatLock) {
            if (heartbeatTask != null) heartbeatTask.cancel(false);
            heartbeatTask = null;
            heartbeatSeconds = 0;
        }
    }

    public synchronized void disable() {
        stopTasks();
        if (updateTask != null) updateTask.cancel(false);
        // Bukkit disables plugins before it kicks players, so their quit events
        // never reach us on shutdown: close every session here.
        long now = System.currentTimeMillis();
        for (SessionTracker.Session session : sessions.snapshot()) reportLeave(session, now);
        sessions.clear();
        spool.seal();
        // One bounded delivery attempt; whatever does not make it stays on disk
        // and is sent on the next start.
        Thread last = new Thread(() -> spool.send(apiClient, json -> {}), "McStat-Shutdown");
        last.setDaemon(true);
        last.start();
        try {
            last.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        worker.shutdownNow();
        apiClient.shutdown();
        geo.close();
        if (updateService != null) updateService.shutdown();
    }

    public enum ReloadResult { CONNECTED, PENDING, NO_KEY }

    /**
     * Applies a reloaded local config and reports the outcome to `done` once
     * the new handshake has run — on the worker thread, so a command never
     * blocks the server's main thread on the network.
     */
    public synchronized void reload(McStatConfig newConfig, java.util.function.Consumer<ReloadResult> done) {
        config.copyFrom(newConfig);
        stopTasks();
        McStatApiClient old = apiClient;
        apiClient = new McStatApiClient(config, installationId, logger);
        old.shutdown();
        connected = false;
        serverSlug = null;
        remote = RemoteConfig.DEFAULT.restrictedBy(config).restrictedByPlatform(afkSupported, gameplaySupported);
        scheduleUpdateChecks();
        if (!config.isValid()) {
            done.accept(ReloadResult.NO_KEY);
            return;
        }
        start();
        // The worker is single-threaded: this runs right after the handshake queued by start().
        worker.execute(() -> done.accept(connected ? ReloadResult.CONNECTED : ReloadResult.PENDING));
    }

    // ─── Handshake & remote config ────────────────────────────────────────

    private void handshake() {
        JsonObject hello = new JsonObject();
        hello.addProperty("v", 2);
        hello.addProperty("plugin", pluginVersion);
        hello.addProperty("platform", platform.platform());
        hello.addProperty("software", truncate(platform.software(), 96));
        hello.addProperty("mc", truncate(platform.minecraftVersion(), 32));
        hello.addProperty("java", truncate(System.getProperty("java.version"), 24));
        Boolean online = platform.onlineMode();
        if (online != null) hello.addProperty("online", online);
        hello.addProperty("proxy", platform.proxyMode());
        JsonArray caps = new JsonArray();
        for (String cap : platform.capabilities()) caps.add(cap);
        hello.add("caps", caps);

        McStatApiClient.Response response = apiClient.hello(hello);
        if (response.ok() && response.data != null) {
            boolean wasConnected = connected;
            connected = true;
            lastConnectionError = null;
            if (response.data.has("server") && response.data.get("server").isJsonObject()) {
                JsonObject server = response.data.getAsJsonObject("server");
                serverSlug = server.has("slug") ? server.get("slug").getAsString() : null;
                serverName = server.has("name") ? server.get("name").getAsString() : null;
            }
            if (response.data.has("config") && response.data.get("config").isJsonObject()) {
                applyRemoteConfig(response.data.getAsJsonObject("config"));
            }
            if (response.data.has("site") && response.data.get("site").isJsonPrimitive()) {
                String site = response.data.get("site").getAsString();
                if (site.matches("https://[a-z0-9.-]+(:[0-9]+)?")) siteUrl = site;
            }
            if (!wasConnected) logger.info("Connected to MCStat as \"" + serverName + "\".");
            return;
        }
        connected = false;
        lastConnectionError = "HTTP " + response.status + (response.detail.isEmpty() ? "" : " - " + response.detail);
        if (response.status == 401 || response.status == 403) {
            logger.warning("MCStat refused the API key (" + lastConnectionError + "). Check api-key, then /mcstat reload.");
        } else {
            logger.warning("Could not reach MCStat (" + lastConnectionError + "); retrying in a minute.");
            worker.schedule(this::handshake, 60, TimeUnit.SECONDS);
        }
    }

    private void applyRemoteConfig(JsonObject json) {
        RemoteConfig next = RemoteConfig.fromJson(json).restrictedBy(config).restrictedByPlatform(afkSupported, gameplaySupported);
        RemoteConfig previous = remote;
        remote = next;
        sessions.setAfkMinutes(next.afkMinutes);
        if (next.heartbeatSeconds != heartbeatSeconds) rescheduleHeartbeat(next.heartbeatSeconds);
        if (next.geo && !previous.geo) worker.execute(this::refreshGeo);
    }

    // Its own lock, not the core's: the worker reschedules from inside a
    // handshake, and must never wait on a caller that holds the core lock.
    private void rescheduleHeartbeat(int seconds) {
        synchronized (heartbeatLock) {
            if (heartbeatTask != null) heartbeatTask.cancel(false);
            heartbeatSeconds = seconds;
            heartbeatTask = worker.scheduleWithFixedDelay(this::heartbeat, Math.min(10, seconds), seconds, TimeUnit.SECONDS);
        }
    }

    private void refreshGeo() {
        if (remote.geo) geo.refresh();
    }

    // ─── Events ───────────────────────────────────────────────────────────

    /** A player joined. Call from the platform's join listener. */
    public void onPlayerJoin(UUID uuid, String name) {
        SessionTracker.Session session = sessions.start(uuid, name, System.currentTimeMillis());
        // Brand and locale arrive from the client just after the join, so the
        // join is described a moment later — on the worker thread.
        worker.schedule(() -> {
            if (session.joinSent.compareAndSet(false, true)) reportJoin(session, platform.describePlayer(uuid));
        }, JOIN_DESCRIBE_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /** A player left. Call from the platform's quit listener. */
    public void onPlayerQuit(UUID uuid) {
        SessionTracker.Session session = sessions.end(uuid);
        if (session != null) reportLeave(session, System.currentTimeMillis());
        else gameplay.remove(uuid);
    }

    /** Gameplay counters; listeners add to them from any thread. */
    public GameplayTracker gameplay() {
        return gameplay;
    }

    /** The player moved, chatted or interacted. Cheap; call from any thread. */
    public void onActivity(UUID uuid) {
        sessions.activity(uuid, System.currentTimeMillis());
    }

    private void reportJoin(SessionTracker.Session session, JoinInfo info) {
        RemoteConfig cfg = remote;
        if (!cfg.sessions) return;
        JsonObject event = new JsonObject();
        event.addProperty("t", "join");
        event.addProperty("at", session.joinedAt);
        event.addProperty("u", session.uuid.toString());
        event.addProperty("n", session.name);
        if (info != null) {
            if (cfg.hostname && info.hostname != null) event.addProperty("h", truncate(info.hostname, 255));
            if (cfg.clientInfo) {
                if (info.protocol != null && info.protocol > 0) event.addProperty("p", info.protocol);
                if (info.brand != null && !info.brand.isEmpty()) event.addProperty("b", truncate(info.brand, 64));
                if (info.locale != null && !info.locale.isEmpty()) event.addProperty("l", truncate(info.locale, 16));
                if (info.bedrock) event.addProperty("bd", true);
            }
            if (cfg.geo) {
                String country = geo.country(info.address);
                if (country != null) event.addProperty("c", country);
            }
            if (info.firstPlayed > 0) event.addProperty("fp", info.firstPlayed);
        }
        spool.add(event);
    }

    private void reportLeave(SessionTracker.Session session, long now) {
        RemoteConfig cfg = remote;
        if (!cfg.sessions) return;
        // A player gone before their join was described still gets a join,
        // so the server sees a complete session.
        if (session.joinSent.compareAndSet(false, true)) reportJoin(session, null);
        JsonObject event = new JsonObject();
        event.addProperty("t", "leave");
        event.addProperty("at", now);
        event.addProperty("u", session.uuid.toString());
        if (cfg.afk) event.addProperty("afk", session.afkSeconds(now, sessions.thresholdMillis()));
        JsonObject stats = gameplay.drain(session.uuid);
        gameplay.remove(session.uuid);
        if (cfg.gameplay && stats != null) event.add("st", stats);
        spool.add(event);
    }

    private void heartbeat() {
        try {
            RemoteConfig cfg = remote;
            long now = System.currentTimeMillis();
            JsonObject event = new JsonObject();
            event.addProperty("t", "hb");
            event.addProperty("at", now);
            event.addProperty("on", platform.onlinePlayers());
            event.addProperty("max", platform.maxPlayers());
            if (cfg.performance) addPerformance(event, cfg.worlds);
            if (cfg.sessions) {
                JsonArray roster = new JsonArray();
                for (SessionTracker.Session session : sessions.snapshot()) {
                    JsonObject p = new JsonObject();
                    p.addProperty("u", session.uuid.toString());
                    p.addProperty("n", session.name);
                    p.addProperty("s", session.joinedAt);
                    if (cfg.afk) p.addProperty("afk", session.afkSeconds(now, sessions.thresholdMillis()));
                    JsonObject stats = gameplay.drain(session.uuid);
                    if (cfg.gameplay && stats != null) p.add("st", stats);
                    roster.add(p);
                }
                event.add("pl", roster);
            }
            spool.add(event);
        } catch (RuntimeException e) {
            logger.warning("Heartbeat failed: " + e.getMessage());
        }
    }

    private void addPerformance(JsonObject event, boolean worlds) {
        ServerSample sample = platform.sampleServer(worlds);
        if (sample.tps != null) event.addProperty("tps", round(Math.min(sample.tps, 20.0)));
        if (sample.mspt != null) event.addProperty("mspt", round(sample.mspt));
        Double cpu = processCpuPercent();
        if (cpu != null) event.addProperty("cpu", round(cpu));
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long maxMb = rt.maxMemory() == Long.MAX_VALUE ? rt.totalMemory() >> 20 : rt.maxMemory() >> 20;
        JsonArray ram = new JsonArray();
        ram.add(usedMb);
        ram.add(maxMb);
        event.add("ram", ram);
        if (worlds) {
            if (sample.chunks != null) event.addProperty("ch", sample.chunks);
            if (sample.entities != null) event.addProperty("en", sample.entities);
            if (sample.tiles != null) event.addProperty("te", sample.tiles);
        }
    }

    /** Process CPU as a share of the whole machine, when the JVM exposes it. */
    private static Double processCpuPercent() {
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                double load = ((com.sun.management.OperatingSystemMXBean) os).getProcessCpuLoad();
                return load >= 0 ? load * 100.0 : null;
            }
        } catch (LinkageError | RuntimeException ignored) {
            // Non-HotSpot JVM without the extension: no CPU reading.
        }
        return null;
    }

    private void flush() {
        try {
            spool.seal();
            if (System.currentTimeMillis() < nextSendAt) return;
            BatchSpool.Outcome outcome = spool.send(apiClient, this::applyRemoteConfig);
            if (outcome == BatchSpool.Outcome.RETRY_LATER) {
                failures++;
                long delay = Math.min(MAX_BACKOFF_SECONDS, FLUSH_SECONDS * (1L << Math.min(failures, 6)));
                nextSendAt = System.currentTimeMillis() + delay * 1000L;
                if (failures == 3) logger.warning("Delivery is failing (" + spool.lastError() + "); data is kept on disk and retried.");
            } else if (outcome == BatchSpool.Outcome.SENT) {
                if (failures >= 3) logger.info("Delivery recovered.");
                failures = 0;
                nextSendAt = 0;
            }
        } catch (RuntimeException e) {
            logger.warning("Delivery error: " + e.getMessage());
        }
    }

    // ─── Updates ──────────────────────────────────────────────────────────

    public void configureUpdater(String artifactPrefix) {
        if (artifactPrefix == null || artifactPrefix.trim().isEmpty()) return;
        this.updateService = new McStatUpdateService(artifactPrefix.trim(), dataDirectory.resolve("updates"), logger);
    }

    private void scheduleUpdateChecks() {
        if (updateTask != null) updateTask.cancel(false);
        updateTask = null;
        if (updateService == null || !config.isUpdateChecksEnabled()) return;
        long hours = config.getUpdateCheckIntervalHours();
        updateTask = worker.scheduleWithFixedDelay(this::backgroundUpdateCheck, 10, TimeUnit.HOURS.toSeconds(hours), TimeUnit.SECONDS);
    }

    private void backgroundUpdateCheck() {
        try {
            McStatUpdateService.UpdateInfo info = updateService.check(pluginVersion);
            if (!info.isUpdateAvailable()) return;
            if (config.isUpdateNotifyAdmins()) logger.warning("" + info.getSummary() + " Release: " + info.getReleaseUrl());
            if (config.isUpdateAutoDownload()) {
                Path downloaded = updateService.download(info);
                logger.warning("Update downloaded to " + downloaded + ". Stop the server, replace the old jar, then start it again.");
            }
        } catch (Exception ignored) {
            // Background check: a failure only means we try again next interval.
        }
    }

    /** Runs a task on the worker thread (network calls from commands). */
    public void runAsync(Runnable task) {
        worker.execute(task);
    }

    // ─── State for commands ───────────────────────────────────────────────

    public McStatUpdateService getUpdateService() { return updateService; }
    public void setPluginVersion(String version) { this.pluginVersion = version; }
    public String getPluginVersion() { return pluginVersion; }
    public McStatConfig getConfig() { return config; }
    public boolean isConnected() { return connected; }
    public String getServerSlug() { return serverSlug; }
    public String getServerName() { return serverName; }
    public String getLastConnectionError() { return lastConnectionError; }
    public long getUptimeMillis() { return System.currentTimeMillis() - startTime; }
    public RemoteConfig getRemoteConfig() { return remote; }
    public BatchSpool getSpool() { return spool; }
    public int getTrackedPlayers() { return sessions.size(); }
    public String getPlatformName() { return platform.platform(); }

    /** Vote link in the player's language, or null when the server slug is not known yet. */
    public String getVoteUrl(String language) {
        if (config.getVoteUrl() != null && !config.getVoteUrl().isEmpty()) return config.getVoteUrl();
        if (serverSlug == null) return null;
        String code = Messages.resolve(language);
        String prefix = Messages.DEFAULT.equals(code) ? "" : "/" + code;
        return getSiteUrl() + prefix + "/servers/" + serverSlug + "#vote";
    }

    /** Where players and owners are sent: MCStat's public address, not the API endpoint. */
    public String getSiteUrl() {
        String site = siteUrl;
        return site != null ? site : config.getApiBaseUrl();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
