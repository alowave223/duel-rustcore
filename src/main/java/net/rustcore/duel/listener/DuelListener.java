package net.rustcore.duel.listener;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.arena.CustomPoly2D;
import net.rustcore.duel.duel.Duel;
import net.rustcore.duel.duel.DuelState;
import net.rustcore.duel.kit.KitMenu;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.mode.impl.KitBuilderMode;
import net.rustcore.duel.modification.Modification;


/**
 * Handles all gameplay events during active duels:
 * deaths, damage modifications, totem usage, hunger, etc.
 */
public class DuelListener implements Listener {

    private final DuelsPlugin plugin;
    private static final Set<DuelState> ALLOW_MOVEMENT = EnumSet.of(
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
        if (killer != null && !killer.getUniqueId().equals(dead.getUniqueId())) {
            plugin.getStatsManager().recordKill(duel.getMode().getId(), killer.getUniqueId());
        }
        plugin.getStatsManager().recordDeath(duel.getMode().getId(), dead.getUniqueId());

        // Notify duel of death
        duel.handleDeath(dead, killer != null && !killer.getUniqueId().equals(dead.getUniqueId()) ? killer : null);
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

        Modification mod = duel.getMode().getModification();

        // Explosions (end crystals, TNT, etc.) — always allow during active duel
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            if (mod.damageMultiplier() != 1.0) {
                event.setDamage(event.getDamage() * mod.damageMultiplier());
            }
            return;
        }

        // Don't allow damage from non-participants (players outside the duel)
        if (event.getDamager() instanceof Player attacker) {
            if (!duel.isParticipant(attacker.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        // Apply damage multiplier
        if (mod.damageMultiplier() != 1.0) {
            event.setDamage(event.getDamage() * mod.damageMultiplier());
        }
    }

    /**
     * Force-allow explosion damage for duel participants even if another plugin cancelled it.
     * Runs at HIGHEST with ignoreCancelled=false so it sees cancelled events too.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onExplosionDamageOverride(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)
            return;

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null || duel.getState() != DuelState.ACTIVE)
            return;

        if (event.isCancelled()) {
            event.setCancelled(false);
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
            return;
        }

        // Explicitly allow explosion damage during active duels
        // The HIGHEST-priority onExplosionDamageOverride handler will un-cancel if needed
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            // If this is an EntityDamageByEntityEvent, the multiplier was already applied
            // in the more specific handler — don't double-apply
            if (!(event instanceof EntityDamageByEntityEvent)) {
                if (mod.damageMultiplier() != 1.0) {
                    event.setDamage(event.getDamage() * mod.damageMultiplier());
                }
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPearlThrow(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl) {
            if (event.getEntity().getShooter() instanceof Player) {
                Player player = (Player) event.getEntity().getShooter();
                Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());

                if (duel == null)
                    return;

                DuelState state = duel.getState();

                if (state != DuelState.ACTIVE) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            Duel duel = plugin.getDuelManager().getDuel(event.getPlayer().getUniqueId());
            if (duel == null)
                return;

            DuelState state = duel.getState();

            if (state != DuelState.ACTIVE) {
                event.setCancelled(true);
                return;
            }

            // Block ender pearl if destination is outside the arena polygon
            CustomPoly2D polygon = duel.getActiveArena().getPolygon();
            if (polygon != null && event.getTo() != null && !polygon.contains(event.getTo())) {
                event.setCancelled(true);
                return;
            }
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
        
        if (ALLOW_MOVEMENT.contains(duel.getState())) {
            if (duel.getState() == DuelState.ACTIVE && event.hasChangedPosition() && event.getTo() != null) {
                CustomPoly2D polygon = duel.getActiveArena().getPolygon();
                if (polygon != null && !polygon.contains(event.getTo())) {
                    event.setTo(event.getFrom());
                }
            }
        } else if (event.hasChangedPosition()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        if (duel.getState() != DuelState.ACTIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
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

        DuelMode mode = duel.getMode();
        if (mode instanceof KitBuilderMode kitBuilderMode) {
            if (state == DuelState.COUNTDOWN) {
                KitMenu kitMenu = kitBuilderMode.getKitMenu();
                if (!kitMenu.isKitMenu(event.getView().getTopInventory())) {
                    return;
                }
            }
        } else {
            if (state == DuelState.COUNTDOWN
                    || state == DuelState.ROUND_ENDING
                    || state == DuelState.ENDED) {
                event.setCancelled(true);
            }
        }
    }
}
