package org.mcstat.spigot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.mcstat.common.McStatCore;

/**
 * Sessions and AFK activity. Every handler is MONITOR priority and only hands
 * a timestamp or an id to the core — no I/O, no allocation worth noting —
 * because move events fire many times per second per player.
 *
 * Activity is what a person at the keyboard does, not what an AFK setup
 * produces. Counted: turning the view by a real amount, walking to another
 * block on foot, chatting, commands, inventory clicks. Not counted: being
 * carried (vehicles, water streams, bubble columns), jumping in place, a
 * camera that twitches a few degrees on a timer, and clicking without ever
 * moving (auto-clickers at farms) — PlayerInteractEvent is deliberately not
 * an activity.
 */
final class SpigotPlayerListener implements Listener {

    /** Turning less than this from the last counted view is a twitch, not a look. */
    private static final float MIN_TURN_DEGREES = 10f;

    /** Where a player last did something that counted: view and block column. */
    private static final class Anchor {
        float yaw;
        float pitch;
        int blockX;
        int blockZ;

        Anchor(Location at) {
            set(at);
        }

        void set(Location at) {
            yaw = at.getYaw();
            pitch = at.getPitch();
            blockX = at.getBlockX();
            blockZ = at.getBlockZ();
        }
    }

    private final McStatCore core;
    private final SpigotPlatform platform;
    private final Map<UUID, Anchor> anchors = new ConcurrentHashMap<>();

    SpigotPlayerListener(McStatCore core, SpigotPlatform platform) {
        this.core = core;
        this.platform = platform;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        anchors.put(event.getPlayer().getUniqueId(), new Anchor(event.getPlayer().getLocation()));
        core.onPlayerJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        anchors.remove(event.getPlayer().getUniqueId());
        core.onPlayerQuit(event.getPlayer().getUniqueId());
        platform.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        Player player = event.getPlayer();
        if (isDeliberate(player, to)) core.onActivity(player.getUniqueId());
        // Distance travelled; a jump of more than 10 blocks in one move is a
        // teleport (or a world change), not walking.
        if (from.getWorld() == to.getWorld()) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double dz = to.getZ() - from.getZ();
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared > 0 && squared < 100) core.gameplay().addDistance(player.getUniqueId(), Math.sqrt(squared));
        }
    }

    /** True when this move is the player's own doing, and moves the anchor if so. */
    private boolean isDeliberate(Player player, Location to) {
        Anchor anchor = anchors.computeIfAbsent(player.getUniqueId(), id -> new Anchor(to));
        boolean turned = angleBetween(anchor.yaw, to.getYaw()) >= MIN_TURN_DEGREES
                || Math.abs(anchor.pitch - to.getPitch()) >= MIN_TURN_DEGREES;
        boolean walked = false;
        if (!turned && (anchor.blockX != to.getBlockX() || anchor.blockZ != to.getBlockZ())) {
            // Riding or drifting in a stream moves the player without them.
            walked = !player.isInsideVehicle() && !to.getBlock().isLiquid();
        }
        if (!turned && !walked) return false;
        anchor.set(to);
        return true;
    }

    private static float angleBetween(float a, float b) {
        float diff = Math.abs(a - b) % 360f;
        return diff > 180f ? 360f - diff : diff;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) core.onActivity(event.getWhoClicked().getUniqueId());
    }
}
