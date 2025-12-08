package fr.tannoxx.ubuplugin.modules.ranks.listeners;

import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import fr.tannoxx.ubuplugin.modules.ranks.data.RankDataManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Gère le formatage du chat avec ranks et prefixes
 */
public class ChatListener implements Listener {

    private final RanksModule module;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public ChatListener(@NotNull RanksModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component originalMessage = event.message();

        // Charger les données du joueur
        RankDataManager.PlayerRankData data = module.getRankDataManager().loadPlayerData(player);

        // Construire le format du message
        StringBuilder formatBuilder = new StringBuilder();

        // Prefix personnalisé
        if (!data.prefix().isEmpty()) {
            formatBuilder.append(data.prefix()).append(" ");
        }

        // Nom du joueur
        formatBuilder.append("<white>").append(player.getName()).append("</white>");

        // Suffix du rank
        String suffix = module.getConfigManager().getString("ranks.list." + data.rank() + ".suffix", "");
        if (!suffix.isEmpty()) {
            formatBuilder.append(" ").append(suffix);
        }

        formatBuilder.append("<white>: </white>");

        // Formatter le message final
        Component formattedMessage = MINI_MESSAGE.deserialize(formatBuilder.toString())
                .append(originalMessage);

        event.renderer((source, sourceDisplayName, message, viewer) -> formattedMessage);
    }
}