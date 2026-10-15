package org.mcstat.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.mcstat.common.platform.JoinInfo;
import org.mcstat.common.platform.PlatformAdapter;
import org.mcstat.common.platform.ServerSample;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Velocity proxies. No TPS, worlds or AFK on a proxy; sessions are network-wide. */
final class VelocityPlatform implements PlatformAdapter {

    private final ProxyServer server;
    private final Object floodgateApi;
    private final Method floodgateIsPlayer;

    VelocityPlatform(ProxyServer server) {
        this.server = server;
        Object api = null;
        Method isPlayer = null;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            isPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            api = null;
        }
        this.floodgateApi = api;
        this.floodgateIsPlayer = isPlayer;
    }

    @Override public String platform() { return "velocity"; }

    @Override public String software() { return server.getVersion().getName() + " " + server.getVersion().getVersion(); }

    @Override public String minecraftVersion() { return null; }

    @Override public Boolean onlineMode() { return server.getConfiguration().isOnlineMode(); }

    @Override public String proxyMode() { return "none"; }

    @Override
    public List<String> capabilities() {
        List<String> caps = new ArrayList<>();
        caps.add("brand");
        caps.add("geo");
        if (floodgateApi != null) caps.add("floodgate");
        return caps;
    }

    @Override public int onlinePlayers() { return server.getPlayerCount(); }

    @Override public int maxPlayers() { return server.getConfiguration().getShowMaxPlayers(); }

    @Override public ServerSample sampleServer(boolean includeWorlds) { return ServerSample.EMPTY; }

    @Override
    public JoinInfo describePlayer(UUID uuid) {
        Optional<Player> found = server.getPlayer(uuid);
        if (!found.isPresent()) return null;
        Player player = found.get();
        String host = player.getVirtualHost().map(InetSocketAddress::getHostString).orElse(null);
        Locale locale = player.getEffectiveLocale();
        boolean bedrock = false;
        if (floodgateApi != null) {
            try {
                bedrock = Boolean.TRUE.equals(floodgateIsPlayer.invoke(floodgateApi, uuid));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                bedrock = false;
            }
        }
        return new JoinInfo(host, player.getProtocolVersion().getProtocol(), player.getClientBrand(),
                locale != null ? locale.toString() : null, player.getRemoteAddress().getAddress(), bedrock, 0L);
    }

    static String localeOf(Player player) {
        Locale locale = player.getEffectiveLocale();
        return locale != null ? locale.toString() : null;
    }
}
