package org.mcstat.bungee;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.mcstat.common.McStatCore;

public class BungeePlayerListener implements Listener {

    private final McStatCore core;

    public BungeePlayerListener(McStatCore core) {
        this.core = core;
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        core.onPlayerJoin(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                "proxy"
        );
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        core.onPlayerLeave(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                "proxy"
        );
    }

}
