package org.mcstat.spigot;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.mcstat.common.McStatCore;

/**
 * Sessions and AFK activity. Every handler is MONITOR priority and only hands
 * a timestamp or an id to the core — no I/O, no allocation worth noting —
 * because move events fire many times per second per player.
 */
final class SpigotPlayerListener implements Listener {

    private final McStatCore core;
    private final SpigotPlatform platform;

    SpigotPlayerListener(McStatCore core, SpigotPlatform platform) {
        this.core = core;
        this.platform = platform;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        core.onPlayerJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        core.onPlayerQuit(event.getPlayer().getUniqueId());
        platform.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // Looking around or walking to another block counts; standing in
        // flowing water (sub-block drift without turning) does not.
        if (to == null) return;
        if (from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()
                || from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            core.onActivity(event.getPlayer().getUniqueId());
        }
        // Distance travelled; a jump of more than 10 blocks in one move is a
        // teleport (or a world change), not walking.
        if (from.getWorld() == to.getWorld()) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double dz = to.getZ() - from.getZ();
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared > 0 && squared < 100) core.gameplay().addDistance(event.getPlayer().getUniqueId(), Math.sqrt(squared));
        }
    }

    @SuppressWarnings("deprecation") // AsyncPlayerChatEvent still fires on Paper and is the only chat event on Spigot.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        core.onActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        core.onActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        core.onActivity(event.getPlayer().getUniqueId());
    }
}
