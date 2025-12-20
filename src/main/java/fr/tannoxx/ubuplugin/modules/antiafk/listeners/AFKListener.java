package fr.tannoxx.ubuplugin.modules.antiafk.listeners;

import fr.tannoxx.ubuplugin.modules.antiafk.AntiAFKModule;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Listener amélioré pour détecter l'activité des joueurs
 * Distingue les activités fortes (preuves réelles) des activités faibles (automatisables)
 */
public record AFKListener(AntiAFKModule module) implements Listener {

    public AFKListener(@NotNull AntiAFKModule module) {
        this.module = module;
    }

    // ========== INITIALISATION ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        module.recordStrongActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        module.cleanupPlayer(event.getPlayer().getUniqueId());
    }

    // ========== ACTIVITÉS FORTES (Preuves réelles d'activité) ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(@NotNull AsyncPlayerChatEvent event) {
        module.recordStrongActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event) {
        module.recordStrongActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        module.recordStrongActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        module.recordStrongActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            module.recordStrongActivity(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            module.recordStrongActivity(player);
        }
    }

    // ========== ACTIVITÉS FAIBLES (Potentiellement automatisables) ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        // Vérifier mouvement réel (pas juste rotation)
        boolean hasMoved = from.getX() != to.getX() ||
                from.getY() != to.getY() ||
                from.getZ() != to.getZ();

        // Vérifier rotation de la tête
        boolean hasRotated = from.getYaw() != to.getYaw() ||
                from.getPitch() != to.getPitch();

        Player player = event.getPlayer();

        if (hasMoved) {
            module.recordWeakActivity(player, "MOVE", to.getX(), to.getY(), to.getZ());
        } else if (hasRotated) {
            module.recordWeakActivity(player, "LOOK", to.getYaw(), to.getPitch(), 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();
        module.recordWeakActivity(player, "INTERACT", loc.getX(), loc.getY(), loc.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();
        module.recordWeakActivity(player, "DROP", loc.getX(), loc.getY(), loc.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPickupItem(@NotNull PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();
        module.recordWeakActivity(player, "PICKUP", loc.getX(), loc.getY(), loc.getZ());
    }
}