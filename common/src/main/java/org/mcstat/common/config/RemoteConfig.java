package org.mcstat.common.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Module switches the server owner set in the MCStat dashboard, delivered on
 * every handshake and batch response. Defaults are what the plugin does before
 * it has heard from MCStat. The local config can still switch things off: a
 * module runs only when both sides allow it.
 */
public final class RemoteConfig {

    public final boolean performance;
    public final boolean worlds;
    public final boolean sessions;
    public final boolean afk;
    public final boolean gameplay;
    public final boolean clientInfo;
    public final boolean geo;
    public final boolean hostname;
    public final int heartbeatSeconds;
    public final int afkMinutes;

    public static final RemoteConfig DEFAULT = new RemoteConfig(true, true, true, true, true, true, true, true, 60, 5);

    public RemoteConfig(boolean performance, boolean worlds, boolean sessions, boolean afk, boolean gameplay,
                        boolean clientInfo, boolean geo, boolean hostname, int heartbeatSeconds, int afkMinutes) {
        this.performance = performance;
        this.worlds = worlds;
        this.sessions = sessions;
        this.afk = afk;
        this.gameplay = gameplay;
        this.clientInfo = clientInfo;
        this.geo = geo;
        this.hostname = hostname;
        this.heartbeatSeconds = clamp(heartbeatSeconds, 30, 300);
        this.afkMinutes = clamp(afkMinutes, 1, 60);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Parses the server's config object; anything missing keeps its default. */
    public static RemoteConfig fromJson(JsonObject json) {
        if (json == null) return DEFAULT;
        JsonObject modules = json.has("modules") && json.get("modules").isJsonObject()
                ? json.getAsJsonObject("modules") : new JsonObject();
        return new RemoteConfig(
                flag(modules, "performance"), flag(modules, "worlds"), flag(modules, "sessions"),
                flag(modules, "afk"), flag(modules, "gameplay"), flag(modules, "clientInfo"), flag(modules, "geo"), flag(modules, "hostname"),
                integer(json, "heartbeatSeconds", DEFAULT.heartbeatSeconds),
                integer(json, "afkMinutes", DEFAULT.afkMinutes));
    }

    private static boolean flag(JsonObject modules, String name) {
        JsonElement value = modules.get(name);
        return value == null || !value.isJsonPrimitive() || value.getAsBoolean();
    }

    private static int integer(JsonObject json, String name, int fallback) {
        JsonElement value = json.get(name);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The effective config: a module runs only if the local config allows it too. */
    public RemoteConfig restrictedBy(McStatConfig local) {
        return new RemoteConfig(
                performance && local.isSendTps(), worlds && local.isSendTps(),
                sessions && local.isTrackPlayerTime() && local.isSendPlayerEvents(),
                afk && local.isTrackPlayerTime() && local.isSendPlayerEvents(),
                gameplay && local.isTrackGameplay() && local.isTrackPlayerTime() && local.isSendPlayerEvents(),
                clientInfo && local.isSendClientInfo(), geo && local.isSendCountry(), hostname && local.isSendJoinAddress(),
                heartbeatSeconds, afkMinutes);
    }

    /** Switches off modules the platform cannot measure (proxies see no movement or gameplay). */
    public RemoteConfig restrictedByPlatform(boolean afkSupported, boolean gameplaySupported) {
        return new RemoteConfig(performance, worlds, sessions, afk && afkSupported, gameplay && gameplaySupported,
                clientInfo, geo, hostname, heartbeatSeconds, afkMinutes);
    }

    /** Module names in display order, paired with their state. */
    public java.util.Map<String, Boolean> modules() {
        java.util.Map<String, Boolean> map = new java.util.LinkedHashMap<>();
        map.put("performance", performance);
        map.put("worlds", worlds);
        map.put("sessions", sessions);
        map.put("afk", afk);
        map.put("gameplay", gameplay);
        map.put("clientInfo", clientInfo);
        map.put("geo", geo);
        map.put("hostname", hostname);
        return map;
    }

}
