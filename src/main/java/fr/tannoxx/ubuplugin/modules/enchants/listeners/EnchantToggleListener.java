package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Listener pour gérer la persistance des toggles d'enchantements
 * Charge les préférences à la connexion, nettoie à la déconnexion
 *
 * @author Tannoxx
 * @version 2.0.4
 */
public record EnchantToggleListener(EnchantsModule module) implements Listener {

    public EnchantToggleListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    /**
     * Charge les toggles depuis la DB quand un joueur se connecte
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Charger les toggles de manière asynchrone
        module.getToggleManager().loadPlayerToggles(
                player.getUniqueId(),
                module.getTimberToggles(),
                module.getMagneticToggles(),
                module.getExcavatorToggles(),
                module.getVeinminerToggles()
        );

        module.debug("Toggles chargés pour {}", player.getName());
    }

    /**
     * Nettoie le cache quand un joueur se déconnecte
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Nettoyer les caches (les données restent en DB)
        module.getToggleManager().clearPlayerToggles(
                player.getUniqueId(),
                module.getTimberToggles(),
                module.getMagneticToggles(),
                module.getExcavatorToggles(),
                module.getVeinminerToggles()
        );

        module.debug("Caches toggles nettoyés pour {}", player.getName());
    }
}