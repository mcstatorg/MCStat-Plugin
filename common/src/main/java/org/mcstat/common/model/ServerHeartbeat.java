package org.mcstat.common.model;

import java.util.List;

public class ServerHeartbeat {

    private String serverName;
    private String serverVersion;
    private int onlinePlayers;
    private int maxPlayers;
    private double tps;
    private long uptimeMillis;
    private long timestamp;
    private List<PlayerInfo> players;

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }

    public int getOnlinePlayers() { return onlinePlayers; }
    public void setOnlinePlayers(int onlinePlayers) { this.onlinePlayers = onlinePlayers; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public double getTps() { return tps; }
    public void setTps(double tps) { this.tps = tps; }

    public long getUptimeMillis() { return uptimeMillis; }
    public void setUptimeMillis(long uptimeMillis) { this.uptimeMillis = uptimeMillis; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public List<PlayerInfo> getPlayers() { return players; }
    public void setPlayers(List<PlayerInfo> players) { this.players = players; }
}
