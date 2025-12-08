package fr.tannoxx.ubuplugin.modules.ranks.listeners;

import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Vérifie si un joueur est mute avant d'envoyer un message
 */
public class MuteListener implements Listener {

    private final RanksModule module;

    public MuteListener(@NotNull RanksModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatMuteCheck(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (module.getRankDataManager().isMuted(player)) {
            event.setCancelled(true);
            module.getTranslationManager().send(player, "ranks.mute.cannot-chat");
        }
    }
}