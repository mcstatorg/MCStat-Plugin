package org.mcstat.spigot;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.mcstat.common.McStatCore;

public class SpigotPlayerListener implements Listener {

    private final McStatCore core;
    private final String serverName;

    public SpigotPlayerListener(McStatCore core, String serverName) {
        this.core = core;
        this.serverName = serverName;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        core.onPlayerJoin(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                serverName
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        core.onPlayerLeave(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                serverName
        );
    }
}
