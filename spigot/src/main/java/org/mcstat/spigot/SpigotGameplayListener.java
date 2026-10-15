package org.mcstat.spigot;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.mcstat.common.session.GameplayTracker;
import org.mcstat.common.session.GameplayTracker.Breakdown;
import org.mcstat.common.session.GameplayTracker.Counter;

/**
 * Gameplay counters. MONITOR priority with ignoreCancelled: only what really
 * happened counts (a block protected by another plugin was not broken). Each
 * handler is a counter increment; nothing here does I/O.
 */
final class SpigotGameplayListener implements Listener {

    private final GameplayTracker gameplay;

    SpigotGameplayListener(GameplayTracker gameplay) {
        this.gameplay = gameplay;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        gameplay.add(event.getPlayer().getUniqueId(), Counter.BLOCKS_BROKEN, 1);
        gameplay.add(event.getPlayer().getUniqueId(), Breakdown.BLOCKS, event.getBlock().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        gameplay.add(event.getPlayer().getUniqueId(), Counter.BLOCKS_PLACED, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;
        if (entity instanceof Player) {
            gameplay.add(killer.getUniqueId(), Counter.PLAYER_KILLS, 1);
        } else {
            gameplay.add(killer.getUniqueId(), Counter.MOB_KILLS, 1);
            gameplay.add(killer.getUniqueId(), Breakdown.MOBS, entity.getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        gameplay.add(player.getUniqueId(), Counter.DEATHS, 1);
        EntityDamageEvent cause = player.getLastDamageCause();
        gameplay.add(player.getUniqueId(), Breakdown.DEATH_CAUSES, cause != null ? cause.getCause().name() : "unknown");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        int amount = event.getRecipe() != null && event.getRecipe().getResult() != null ? event.getRecipe().getResult().getAmount() : 1;
        gameplay.add(event.getWhoClicked().getUniqueId(), Counter.ITEMS_CRAFTED, Math.max(1, amount));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            gameplay.add(event.getPlayer().getUniqueId(), Counter.FISH_CAUGHT, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        gameplay.add(event.getEnchanter().getUniqueId(), Counter.ITEMS_ENCHANTED, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExp(PlayerExpChangeEvent event) {
        gameplay.add(event.getPlayer().getUniqueId(), Counter.XP_GAINED, event.getAmount());
    }

    @SuppressWarnings("deprecation") // still fired on Paper; the only chat event on Spigot
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        gameplay.add(event.getPlayer().getUniqueId(), Counter.MESSAGES, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        gameplay.add(event.getPlayer().getUniqueId(), Counter.COMMANDS, 1);
    }
}
