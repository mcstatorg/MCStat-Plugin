package org.mcstat.common.config;

public class McStatConfig {

    private String apiKey;
    private String apiBaseUrl;
    private int syncIntervalSeconds;
    private boolean trackPlayerTime;
    private boolean sendTps;
    private boolean sendPlayerEvents;
    private String voteUrl;

    public McStatConfig() {
        this.apiBaseUrl = "https://mcstat.org";
        this.syncIntervalSeconds = 90;
        this.trackPlayerTime = true;
        this.sendTps = true;
        this.sendPlayerEvents = true;
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey != null ? apiKey.trim() : null; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) {
        String normalized = apiBaseUrl != null ? apiBaseUrl.trim() : "";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.apiBaseUrl = normalized.isEmpty() ? "https://mcstat.org" : normalized;
    }

    public int getSyncIntervalSeconds() { return syncIntervalSeconds; }
    public void setSyncIntervalSeconds(int syncIntervalSeconds) {
        this.syncIntervalSeconds = Math.max(10, syncIntervalSeconds);
    }

    public boolean isTrackPlayerTime() { return trackPlayerTime; }
    public void setTrackPlayerTime(boolean trackPlayerTime) { this.trackPlayerTime = trackPlayerTime; }

    public boolean isSendTps() { return sendTps; }
    public void setSendTps(boolean sendTps) { this.sendTps = sendTps; }

    public boolean isSendPlayerEvents() { return sendPlayerEvents; }
    public void setSendPlayerEvents(boolean sendPlayerEvents) { this.sendPlayerEvents = sendPlayerEvents; }

    public String getVoteUrl() { return voteUrl; }
    public void setVoteUrl(String voteUrl) { this.voteUrl = voteUrl != null ? voteUrl.trim() : null; }

    public boolean isValid() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_API_KEY_HERE");
    }

    public void copyFrom(McStatConfig other) {
        setApiKey(other.getApiKey());
        setApiBaseUrl(other.getApiBaseUrl());
        setSyncIntervalSeconds(other.getSyncIntervalSeconds());
        setTrackPlayerTime(other.isTrackPlayerTime());
        setSendTps(other.isSendTps());
        setSendPlayerEvents(other.isSendPlayerEvents());
        setVoteUrl(other.getVoteUrl());
    }
}
