package org.mcstat.spigot;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.mcstat.common.session.GameplayTracker;

/**
 * Advancements exist from 1.12; this listener is a separate class so that on
 * older servers it is simply never loaded (registering an event class the
 * server does not have would fail the whole plugin).
 */
final class SpigotAdvancementListener implements Listener {

    private final GameplayTracker gameplay;

    SpigotAdvancementListener(GameplayTracker gameplay) {
        this.gameplay = gameplay;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        // Recipe unlocks are advancements internally; they are not achievements.
        if (event.getAdvancement().getKey().getKey().startsWith("recipes/")) return;
        gameplay.add(event.getPlayer().getUniqueId(), GameplayTracker.Counter.ADVANCEMENTS, 1);
    }
}
