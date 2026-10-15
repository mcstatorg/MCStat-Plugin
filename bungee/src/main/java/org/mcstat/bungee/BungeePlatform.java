package org.mcstat.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.mcstat.common.platform.JoinInfo;
import org.mcstat.common.platform.PlatformAdapter;
import org.mcstat.common.platform.ServerSample;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** BungeeCord and Waterfall proxies. No TPS, worlds or AFK on a proxy; sessions are network-wide. */
final class BungeePlatform implements PlatformAdapter {

    private final ProxyServer proxy;
    private final Object floodgateApi;
    private final Method floodgateIsPlayer;

    BungeePlatform(ProxyServer proxy) {
        this.proxy = proxy;
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

    @Override public String platform() { return "bungee"; }

    @Override public String software() { return proxy.getName() + " " + proxy.getVersion(); }

    @Override public String minecraftVersion() { return null; }

    @Override public Boolean onlineMode() { return proxy.getConfig().isOnlineMode(); }

    @Override public String proxyMode() { return "none"; }

    @Override
    public List<String> capabilities() {
        List<String> caps = new ArrayList<>();
        caps.add("geo");
        if (floodgateApi != null) caps.add("floodgate");
        return caps;
    }

    @Override public int onlinePlayers() { return proxy.getOnlineCount(); }

    @Override public int maxPlayers() { return proxy.getConfig().getPlayerLimit(); }

    @Override public ServerSample sampleServer(boolean includeWorlds) { return ServerSample.EMPTY; }

    @Override
    public JoinInfo describePlayer(UUID uuid) {
        ProxiedPlayer player = proxy.getPlayer(uuid);
        if (player == null) return null;
        InetSocketAddress virtualHost = player.getPendingConnection().getVirtualHost();
        SocketAddress socket = player.getSocketAddress();
        InetAddress address = socket instanceof InetSocketAddress ? ((InetSocketAddress) socket).getAddress() : null;
        boolean bedrock = false;
        if (floodgateApi != null) {
            try {
                bedrock = Boolean.TRUE.equals(floodgateIsPlayer.invoke(floodgateApi, uuid));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                bedrock = false;
            }
        }
        return new JoinInfo(virtualHost != null ? virtualHost.getHostString() : null,
                player.getPendingConnection().getVersion(), null, localeOf(player), address, bedrock, 0L);
    }

    static String localeOf(ProxiedPlayer player) {
        Locale locale = player.getLocale();
        return locale != null ? locale.toString() : null;
    }
}
