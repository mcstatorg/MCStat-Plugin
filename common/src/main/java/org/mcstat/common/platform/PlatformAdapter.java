package org.mcstat.common.platform;

import java.util.List;
import java.util.UUID;

/**
 * What the shared core needs from the server software it runs on. Everything
 * platform-specific (Bukkit, Velocity, BungeeCord) lives behind this
 * interface, so the core — sessions, AFK, batching, the wire protocol — exists
 * exactly once.
 *
 * Methods are called from the core's own background thread: implementations
 * must not touch state that is only safe on the server's main thread.
 */
public interface PlatformAdapter {

    /** paper, spigot, folia, velocity or bungee. */
    String platform();

    /** Server software name and build, e.g. "Paper 1.21.4-232". */
    String software();

    /** Minecraft version, e.g. "1.21.4". */
    String minecraftVersion();

    /** Online (premium) mode as configured; null when unknown. */
    Boolean onlineMode();

    /** none, bungee or velocity — how a backend server is fronted. */
    String proxyMode();

    /** Optional integrations that were detected (viaversion, floodgate, …). */
    List<String> capabilities();

    int onlinePlayers();

    int maxPlayers();

    /** Performance readings; any field may be null when the platform cannot measure it. */
    ServerSample sampleServer(boolean includeWorlds);

    /** Connection facts about an online player, or null when they are gone. */
    JoinInfo describePlayer(UUID uuid);
}
