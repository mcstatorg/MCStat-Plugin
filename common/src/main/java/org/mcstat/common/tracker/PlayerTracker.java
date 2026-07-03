package org.mcstat.common.tracker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.mcstat.common.model.PlayerInfo;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PlayerTracker {

    private final ConcurrentHashMap<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> cumulativePlayTime = new ConcurrentHashMap<>();
    private final Path dataFile;
    private final Logger logger;
    private final Gson gson = new Gson();

    public PlayerTracker(Path dataDirectory, Logger logger) {
        this.dataFile = dataDirectory.resolve("playtime.json");
        this.logger = logger;
        loadPlayTimeData();
    }

    public void startSession(UUID uuid, String name) {
        activeSessions.put(uuid, new PlayerSession(uuid, name, System.currentTimeMillis()));
    }

    public void endSession(UUID uuid) {
        PlayerSession session = activeSessions.remove(uuid);
        if (session != null) {
            long sessionDuration = System.currentTimeMillis() - session.getJoinTime();
            cumulativePlayTime.merge(uuid, sessionDuration, Long::sum);
            savePlayTimeData();
        }
    }

    public List<PlayerInfo> getOnlinePlayers() {
        List<PlayerInfo> players = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (PlayerSession session : activeSessions.values()) {
            long sessionTime = now - session.getJoinTime();
            long cumulative = cumulativePlayTime.getOrDefault(session.getUuid(), 0L);
            players.add(new PlayerInfo(
                    session.getUuid(),
                    session.getName(),
                    session.getJoinTime(),
                    cumulative + sessionTime
            ));
        }
        return players;
    }

    public int getOnlineCount() {
        return activeSessions.size();
    }

    public long getPlayTime(UUID uuid) {
        long cumulative = cumulativePlayTime.getOrDefault(uuid, 0L);
        PlayerSession session = activeSessions.get(uuid);
        if (session != null) {
            cumulative += System.currentTimeMillis() - session.getJoinTime();
        }
        return cumulative;
    }

    public void saveAllSessions() {
        for (UUID uuid : new ArrayList<>(activeSessions.keySet())) {
            endSession(uuid);
        }
    }

    private void loadPlayTimeData() {
        if (!Files.exists(dataFile)) return;

        try (Reader reader = new InputStreamReader(
                Files.newInputStream(dataFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Long>>() {}.getType();
            Map<String, Long> data = gson.fromJson(reader, type);
            if (data != null) {
                data.forEach((key, value) -> {
                    try {
                        cumulativePlayTime.put(UUID.fromString(key), value);
                    } catch (IllegalArgumentException ignored) {}
                });
            }
        } catch (IOException e) {
            logger.warning("[McStat] Failed to load playtime data: " + e.getMessage());
        }
    }

    private void savePlayTimeData() {
        try {
            Files.createDirectories(dataFile.getParent());
            Map<String, Long> data = new HashMap<>();
            cumulativePlayTime.forEach((uuid, time) -> data.put(uuid.toString(), time));

            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(dataFile), StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            logger.warning("[McStat] Failed to save playtime data: " + e.getMessage());
        }
    }

    public static class PlayerSession {
        private final UUID uuid;
        private final String name;
        private final long joinTime;

        public PlayerSession(UUID uuid, String name, long joinTime) {
            this.uuid = uuid;
            this.name = name;
            this.joinTime = joinTime;
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public long getJoinTime() { return joinTime; }
    }
}
