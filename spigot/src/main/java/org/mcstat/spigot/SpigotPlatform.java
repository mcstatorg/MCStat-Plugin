package org.mcstat.spigot;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstat.common.platform.JoinInfo;
import org.mcstat.common.platform.PlatformAdapter;
import org.mcstat.common.platform.ServerSample;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit-family servers: CraftBukkit, Spigot, Paper and its forks, Folia.
 *
 * One jar runs from 1.8 to current versions, so anything newer than the 1.8
 * API, Paper-only, or provided by another plugin (ViaVersion, Floodgate) is
 * looked up by reflection once and used only when present.
 *
 * World counts are only safe on the main thread, so a light sync task samples
 * them once a minute and the core reads that snapshot. Folia has no main
 * thread (and no Bukkit scheduler), so it reports no world counts.
 */
final class SpigotPlatform implements PlatformAdapter {

    private final JavaPlugin plugin;
    private final boolean folia;
    private final boolean paper;
    private final ConcurrentHashMap<UUID, String> hostnames = new ConcurrentHashMap<>();
    private final TpsTracker tpsTracker;

    private final Method serverGetTps = method(Bukkit.getServer().getClass(), "getTPS");
    private final Method serverAverageTickTime = method(Bukkit.getServer().getClass(), "getAverageTickTime");
    private final Method playerClientBrand = method(Player.class, "getClientBrandName");
    private final Method playerLocale = method(Player.class, "getLocale");
    private final Method playerVirtualHost = method(Player.class, "getVirtualHost");
    private final Method worldChunkCount = method(World.class, "getChunkCount");
    private final Method worldEntityCount = method(World.class, "getEntityCount");
    private final Method worldTileCount = method(World.class, "getTileEntityCount");
    private final Object viaApi;
    private final Method viaPlayerVersion;
    private final Object floodgateApi;
    private final Method floodgateIsPlayer;
    private final Integer serverProtocol;

    private volatile Integer chunks;
    private volatile Integer entities;
    private volatile Integer tiles;

    SpigotPlatform(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folia = classExists("io.papermc.paper.threadedregions.RegionizedServer");
        this.paper = folia || classExists("com.destroystokyo.paper.PaperConfig") || classExists("io.papermc.paper.configuration.Configuration");

        Object via = null;
        Method viaVersion = null;
        if (Bukkit.getPluginManager().getPlugin("ViaVersion") != null) {
            try {
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
                via = viaClass.getMethod("getAPI").invoke(null);
                viaVersion = via.getClass().getMethod("getPlayerVersion", UUID.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                via = null;
            }
        }
        this.viaApi = via;
        this.viaPlayerVersion = viaVersion;

        Object floodgate = null;
        Method floodgatePlayer = null;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgate = apiClass.getMethod("getInstance").invoke(null);
            floodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            floodgate = null;
        }
        this.floodgateApi = floodgate;
        this.floodgateIsPlayer = floodgatePlayer;

        Integer protocol = null;
        try {
            Object unsafe = Bukkit.class.getMethod("getUnsafe").invoke(null);
            protocol = (Integer) unsafe.getClass().getMethod("getProtocolVersion").invoke(unsafe);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
            // Not Paper: without ViaVersion the client protocol equals the server's, which is then unknown.
        }
        this.serverProtocol = protocol;

        // Spigot without getTPS(): measure ticks ourselves (never on Folia).
        this.tpsTracker = serverGetTps == null && !folia ? new TpsTracker(plugin) : null;
        if (!folia) startWorldSampler();
    }

    private void startWorldSampler() {
        try {
            Bukkit.getScheduler().runTaskTimer(plugin, this::sampleWorlds, 200L, 1200L);
        } catch (UnsupportedOperationException ignored) {
            // A Folia-like fork without the Bukkit scheduler: no world counts.
        }
    }

    /** Main thread only. */
    private void sampleWorlds() {
        int c = 0;
        int e = 0;
        int t = 0;
        boolean tilesKnown = worldTileCount != null;
        for (World world : Bukkit.getWorlds()) {
            c += worldChunkCount != null ? (Integer) invoke(worldChunkCount, world, 0) : world.getLoadedChunks().length;
            e += worldEntityCount != null ? (Integer) invoke(worldEntityCount, world, 0) : world.getEntities().size();
            if (tilesKnown) t += (Integer) invoke(worldTileCount, world, 0);
        }
        chunks = c;
        entities = e;
        tiles = tilesKnown ? t : null;
    }

    void rememberHostname(UUID uuid, String hostname) {
        if (hostname != null) hostnames.put(uuid, hostname);
    }

    void forget(UUID uuid) {
        hostnames.remove(uuid);
    }

    @Override
    public String platform() {
        return folia ? "folia" : paper ? "paper" : "spigot";
    }

    @Override
    public String software() {
        return Bukkit.getName() + " " + Bukkit.getVersion();
    }

    @Override
    public String minecraftVersion() {
        String version = Bukkit.getBukkitVersion();
        int dash = version.indexOf('-');
        return dash > 0 ? version.substring(0, dash) : version;
    }

    @Override
    public Boolean onlineMode() {
        return Bukkit.getOnlineMode();
    }

    @Override
    public String proxyMode() {
        try {
            if (Bukkit.spigot().getConfig().getBoolean("settings.bungeecord", false)) return "bungee";
        } catch (LinkageError | RuntimeException ignored) {
            // CraftBukkit without the spigot() config.
        }
        try {
            Class<?> global = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            Object config = global.getMethod("get").invoke(null);
            Object proxies = config.getClass().getField("proxies").get(config);
            Object velocity = proxies.getClass().getField("velocity").get(proxies);
            if (velocity.getClass().getField("enabled").getBoolean(velocity)) return "velocity";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Not modern Paper.
        }
        return "none";
    }

    @Override
    public List<String> capabilities() {
        List<String> caps = new ArrayList<>();
        if (viaApi != null) caps.add("viaversion");
        if (floodgateApi != null) caps.add("floodgate");
        if (playerClientBrand != null) caps.add("brand");
        if (serverAverageTickTime != null) caps.add("mspt");
        if (!folia) caps.add("worlds");
        caps.add("afk");
        caps.add("gameplay");
        caps.add("geo");
        return caps;
    }

    @Override
    public int onlinePlayers() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public int maxPlayers() {
        return Bukkit.getMaxPlayers();
    }

    @Override
    public ServerSample sampleServer(boolean includeWorlds) {
        Double tps = null;
        if (serverGetTps != null) {
            Object value = invoke(serverGetTps, Bukkit.getServer(), null);
            if (value instanceof double[] && ((double[]) value).length > 0) tps = ((double[]) value)[0];
        } else if (tpsTracker != null) {
            tps = tpsTracker.getTps();
        }
        Double mspt = null;
        if (serverAverageTickTime != null) {
            Object value = invoke(serverAverageTickTime, Bukkit.getServer(), null);
            if (value instanceof Number) mspt = ((Number) value).doubleValue();
        }
        return includeWorlds
                ? new ServerSample(tps, mspt, chunks, entities, tiles)
                : new ServerSample(tps, mspt, null, null, null);
    }

    @Override
    public JoinInfo describePlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return null;

        Integer protocol = serverProtocol;
        if (viaApi != null) {
            Object version = invoke(viaPlayerVersion, viaApi, null, uuid);
            if (version instanceof Integer && (Integer) version > 0) protocol = (Integer) version;
        }
        String brand = playerClientBrand != null ? (String) invoke(playerClientBrand, player, null) : null;
        String locale = playerLocale != null ? (String) invoke(playerLocale, player, null) : spigotLocale(player);
        boolean bedrock = floodgateApi != null && Boolean.TRUE.equals(invoke(floodgateIsPlayer, floodgateApi, false, uuid));
        InetSocketAddress socket = player.getAddress();
        InetAddress address = socket != null ? socket.getAddress() : null;
        long firstPlayed = player.getFirstPlayed();
        return new JoinInfo(hostnameOf(player), protocol, brand, locale, address, bedrock, firstPlayed);
    }

    /** Paper knows the connected address itself; elsewhere SpigotLoginListener records it. */
    boolean needsLoginListener() {
        return playerVirtualHost == null;
    }

    private String hostnameOf(Player player) {
        if (playerVirtualHost != null) {
            Object value = invoke(playerVirtualHost, player, null);
            if (value instanceof InetSocketAddress) return ((InetSocketAddress) value).getHostString();
        }
        return hostnames.get(player.getUniqueId());
    }

    /** 1.8–1.11: the locale lives on Player.Spigot. */
    private static String spigotLocale(Player player) {
        try {
            Object spigot = player.getClass().getMethod("spigot").invoke(player);
            return (String) spigot.getClass().getMethod("getLocale").invoke(spigot);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            return null;
        }
    }

    /** Locale of a command sender who is a player, for replying in their language. */
    String localeOf(Player player) {
        return playerLocale != null ? (String) invoke(playerLocale, player, null) : spigotLocale(player);
    }

    private static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object fallback, Object... args) {
        if (method == null) return fallback;
        try {
            Object value = method.invoke(target, args);
            return value != null ? value : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
