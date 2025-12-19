package fr.tannoxx.ubuplugin.modules.ranks.listeners;

import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import fr.tannoxx.ubuplugin.modules.ranks.data.RankDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Gère la connexion des joueurs (chargement rank, détection langue)
 */
public record PlayerJoinListener(RanksModule module) implements Listener {

    public PlayerJoinListener(@NotNull RanksModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Détecter la langue du joueur
        module.getTranslationManager().detectPlayerLanguage(player);

        // Charger les données du joueur
        RankDataManager.PlayerRankData data = module.getRankDataManager().loadPlayerData(player);

        // Mettre à jour l'affichage
        module.getRankDataManager().updatePlayerDisplay(player, data);

        module.debug("Joueur {} connecté - Rank: {}", player.getName(), data.rank());
    }
}