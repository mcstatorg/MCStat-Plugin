package org.mcstat.common.config;

public class McStatConfig {

    private String apiKey;
    private String apiBaseUrl;
    private boolean trackPlayerTime;
    private boolean sendTps;
    private boolean sendPlayerEvents;
    private boolean trackGameplay;
    private boolean sendClientInfo;
    private boolean sendCountry;
    private boolean sendJoinAddress;
    private String voteUrl;
    private boolean updateChecksEnabled;
    private boolean updateNotifyAdmins;
    private boolean updateAutoDownload;
    private int updateCheckIntervalHours;
    private String language;

    public McStatConfig() {
        this.apiBaseUrl = "https://mcstat.org";
        this.trackPlayerTime = true;
        this.sendTps = true;
        this.sendPlayerEvents = true;
        this.trackGameplay = true;
        this.sendClientInfo = true;
        this.sendCountry = true;
        this.sendJoinAddress = true;
        this.updateChecksEnabled = true;
        this.updateNotifyAdmins = true;
        this.updateAutoDownload = false;
        this.updateCheckIntervalHours = 24;
        this.language = "en";
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

    public boolean isTrackPlayerTime() { return trackPlayerTime; }
    public void setTrackPlayerTime(boolean trackPlayerTime) { this.trackPlayerTime = trackPlayerTime; }

    public boolean isSendTps() { return sendTps; }
    public void setSendTps(boolean sendTps) { this.sendTps = sendTps; }

    public boolean isSendPlayerEvents() { return sendPlayerEvents; }
    public void setSendPlayerEvents(boolean sendPlayerEvents) { this.sendPlayerEvents = sendPlayerEvents; }

    public boolean isTrackGameplay() { return trackGameplay; }
    public void setTrackGameplay(boolean trackGameplay) { this.trackGameplay = trackGameplay; }

    /** Minecraft version, client brand, language and edition of joining players. */
    public boolean isSendClientInfo() { return sendClientInfo; }
    public void setSendClientInfo(boolean sendClientInfo) { this.sendClientInfo = sendClientInfo; }

    /** Player country, resolved locally from the IP (which itself never leaves the server). */
    public boolean isSendCountry() { return sendCountry; }
    public void setSendCountry(boolean sendCountry) { this.sendCountry = sendCountry; }

    /** The address (domain) a player typed to connect. */
    public boolean isSendJoinAddress() { return sendJoinAddress; }
    public void setSendJoinAddress(boolean sendJoinAddress) { this.sendJoinAddress = sendJoinAddress; }

    public String getVoteUrl() { return voteUrl; }
    public void setVoteUrl(String voteUrl) { this.voteUrl = voteUrl != null ? voteUrl.trim() : null; }

    public boolean isUpdateChecksEnabled() { return updateChecksEnabled; }
    public void setUpdateChecksEnabled(boolean updateChecksEnabled) { this.updateChecksEnabled = updateChecksEnabled; }

    public boolean isUpdateNotifyAdmins() { return updateNotifyAdmins; }
    public void setUpdateNotifyAdmins(boolean updateNotifyAdmins) { this.updateNotifyAdmins = updateNotifyAdmins; }

    public boolean isUpdateAutoDownload() { return updateAutoDownload; }
    public void setUpdateAutoDownload(boolean updateAutoDownload) { this.updateAutoDownload = updateAutoDownload; }

    public int getUpdateCheckIntervalHours() { return updateCheckIntervalHours; }
    public void setUpdateCheckIntervalHours(int updateCheckIntervalHours) {
        this.updateCheckIntervalHours = Math.max(1, updateCheckIntervalHours);
    }

    /** Console language; players always see their own client language. */
    public String getLanguage() { return language; }
    public void setLanguage(String language) {
        this.language = language == null || language.trim().isEmpty() ? "en" : language.trim();
    }

    public boolean isValid() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_API_KEY_HERE");
    }

    public void copyFrom(McStatConfig other) {
        setApiKey(other.getApiKey());
        setApiBaseUrl(other.getApiBaseUrl());
        setTrackPlayerTime(other.isTrackPlayerTime());
        setSendTps(other.isSendTps());
        setSendPlayerEvents(other.isSendPlayerEvents());
        setTrackGameplay(other.isTrackGameplay());
        setSendClientInfo(other.isSendClientInfo());
        setSendCountry(other.isSendCountry());
        setSendJoinAddress(other.isSendJoinAddress());
        setVoteUrl(other.getVoteUrl());
        setUpdateChecksEnabled(other.isUpdateChecksEnabled());
        setUpdateNotifyAdmins(other.isUpdateNotifyAdmins());
        setUpdateAutoDownload(other.isUpdateAutoDownload());
        setUpdateCheckIntervalHours(other.getUpdateCheckIntervalHours());
        setLanguage(other.getLanguage());
    }
}
