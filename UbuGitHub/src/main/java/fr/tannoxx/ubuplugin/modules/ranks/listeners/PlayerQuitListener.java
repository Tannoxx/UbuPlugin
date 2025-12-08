package fr.tannoxx.ubuplugin.modules.ranks.listeners;

import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Gère la déconnexion des joueurs (cleanup cache)
 */
public class PlayerQuitListener implements Listener {

    private final RanksModule module;

    public PlayerQuitListener(@NotNull RanksModule module) {
        this.module = module;
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Nettoyer le cache
        module.getRankDataManager().clearCache(player.getUniqueId());
        module.getTranslationManager().clearPlayerCache(player);

        module.debug("Joueur {} déconnecté - Cache nettoyé", player.getName());
    }
}