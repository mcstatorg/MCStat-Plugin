package org.mcstat.common.model;

import java.util.UUID;

public class PlayerInfo {

    private UUID uuid;
    private String name;
    private long sessionStartTime;
    private long totalPlayTimeMillis;

    public PlayerInfo(UUID uuid, String name, long sessionStartTime, long totalPlayTimeMillis) {
        this.uuid = uuid;
        this.name = name;
        this.sessionStartTime = sessionStartTime;
        this.totalPlayTimeMillis = totalPlayTimeMillis;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public long getSessionStartTime() { return sessionStartTime; }
    public long getTotalPlayTimeMillis() { return totalPlayTimeMillis; }
}
