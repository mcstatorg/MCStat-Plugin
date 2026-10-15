package org.mcstat.bungee;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.mcstat.common.McStatCore;

/** Network-wide sessions: a player is online from login until they leave the proxy. */
public final class BungeePlayerListener implements Listener {

    private final McStatCore core;

    BungeePlayerListener(McStatCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PostLoginEvent event) {
        core.onPlayerJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDisconnect(PlayerDisconnectEvent event) {
        core.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
