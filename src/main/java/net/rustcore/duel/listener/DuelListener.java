package net.rustcore.duel.listener;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.duel.Duel;
import net.rustcore.duel.duel.DuelState;
import net.rustcore.duel.modification.Modification;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.util.Vector;

/**
 * Handles all gameplay events during active duels:
 * deaths, damage modifications, totem usage, hunger, etc.
 */
public class DuelListener implements Listener {

    private final DuelsPlugin plugin;
    private static final List<DuelState> ALLOW_MOVEMENT = List.of(
            DuelState.ACTIVE, DuelState.ROUND_ENDING,
            DuelState.ENDED, DuelState.PREPARING
        );

    public DuelListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        // Block death processing while the arena is still being allocated
        if (plugin.getDuelManager().isAllocating(dead.getUniqueId())) {
            event.setCancelled(true);
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        Duel duel = plugin.getDuelManager().getDuel(dead.getUniqueId());
        if (duel == null)
            return;

        Modification mod = duel.getMode().getModification();

        event.setCancelled(true);

        dead.setHealth(20);
        dead.setFoodLevel(20);
        dead.setSaturation(20f);
        dead.setGameMode(GameMode.SPECTATOR);

        // Keep inventory if configured
        if (mod.isKeepInventory()) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        // Clear death message
        event.deathMessage(null);

        // Record kill/death stats
        Player killer = dead.getKiller();
        if (killer != null) {
            plugin.getStatsManager().recordKill(duel.getMode().getId(), killer.getUniqueId());
        }
        plugin.getStatsManager().recordDeath(duel.getMode().getId(), dead.getUniqueId());

        // Notify duel of death
        duel.handleDeath(dead, killer);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        // Respawn at arena spawn point (team A = index 0, team B = index 1)
        int index = duel.getPlayerIds().indexOf(player.getUniqueId());
        java.util.List<org.bukkit.Location> teamSpawns = (index == 0)
                ? duel.getActiveArena().getTeamASpawns()
                : duel.getActiveArena().getTeamBSpawns();
        if (!teamSpawns.isEmpty()) {
            event.setRespawnLocation(teamSpawns.get(0));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim))
            return;

        if (plugin.getDuelManager().isAllocating(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Duel duel = plugin.getDuelManager().getDuel(victim.getUniqueId());
        if (duel == null)
            return;

        // Don't allow damage during non-active states
        if (duel.getState() != DuelState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Don't allow friendly fire (shouldn't happen in 1v1 but safety)
        if (event.getDamager() instanceof Player attacker) {
            if (!duel.isParticipant(attacker.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        Modification mod = duel.getMode().getModification();

        // Apply damage multiplier
        if (mod.damageMultiplier() != 1.0) {
            event.setDamage(event.getDamage() * mod.damageMultiplier());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageKnockback(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim))
            return;

        Duel duel = plugin.getDuelManager().getDuel(victim.getUniqueId());
        if (duel == null)
            return;

        Modification mod = duel.getMode().getModification();

        // Apply knockback multiplier
        if (mod.knockbackMultiplier() != 1.0) {
            // Schedule for next tick to override vanilla knockback
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Vector velocity = victim.getVelocity();
                velocity.setX(velocity.getX() * mod.knockbackMultiplier());
                velocity.setZ(velocity.getZ() * mod.knockbackMultiplier());
                victim.setVelocity(velocity);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        if (plugin.getDuelManager().isAllocating(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        // Prevent damage during non-active states
        if (duel.getState() != DuelState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        Modification mod = duel.getMode().getModification();

        // No fire tick damage
        if (mod.isNoFireTick() && event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
            event.setCancelled(true);
            player.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTotemUse(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        Modification mod = duel.getMode().getModification();
        if (mod.isDisabledTotems()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        Modification mod = duel.getMode().getModification();
        if (mod.isNoHunger()) {
            event.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler
    public void onPlayerRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        Modification mod = duel.getMode().getModification();
        if (mod.isNoNaturalRegen()
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onElytraUse(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        if (duel.getMode().getModification().isNoElytra() && event.isGliding()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getDuelManager().handleDisconnect(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        if (ALLOW_MOVEMENT.contains(duel.getState()))
            return;

        if (event.hasChangedPosition()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onItemDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        if (duel.getState() == DuelState.DRAFTING || duel.getState() == DuelState.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    /**
     * Block inventory manipulation between states (COUNTDOWN, PREPARING, ROUND_ENDING, ENDED).
     * During DRAFTING the KitMenuListener handles the kit GUI; we only block
     * interactions that happen outside of it (e.g. while the menu is momentarily closed).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        // Allocating guard: arena paste in progress — lock everything
        if (plugin.getDuelManager().isAllocating(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        DuelState state = duel.getState();
        // Lock inventory between states where players shouldn't be able to move items
        if (state == DuelState.COUNTDOWN
                || state == DuelState.ROUND_ENDING
                || state == DuelState.ENDED) {
            event.setCancelled(true);
        }
        // DRAFTING is handled by KitMenuListener; ACTIVE and PREPARING are intentionally open.
    }
}
