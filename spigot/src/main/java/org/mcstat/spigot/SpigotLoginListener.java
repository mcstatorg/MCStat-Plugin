package org.mcstat.spigot;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * The address a player connected to, on servers without Paper's
 * Player#getVirtualHost(). Registered only there: on modern Paper, merely
 * listening to PlayerLoginEvent turns off the configuration-phase APIs for
 * every plugin on the server.
 */
final class SpigotLoginListener implements Listener {

    private final SpigotPlatform platform;

    SpigotLoginListener(SpigotPlatform platform) {
        this.platform = platform;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            platform.rememberHostname(event.getPlayer().getUniqueId(), event.getHostname());
        }
    }
}
