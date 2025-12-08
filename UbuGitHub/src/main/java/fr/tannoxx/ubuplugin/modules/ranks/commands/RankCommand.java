package fr.tannoxx.ubuplugin.modules.ranks.commands;

import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande /rank pour définir le rank d'un joueur
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    private final RanksModule module;

    public RankCommand(@NotNull RanksModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ubuplugin.admin")) {
            module.getTranslationManager().sendPrefixed(sender, "errors.no-permission");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§cUsage: /rank <joueur> <rank>");
            sender.sendMessage("§7Ranks disponibles: " + String.join(", ", getAvailableRanks()));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            module.getTranslationManager().sendPrefixed(sender, "general.player-not-found", args[0]);
            return true;
        }

        String rank = args[1].toUpperCase();

        // Vérifier si le rank existe
        if (!isValidRank(rank)) {
            module.getTranslationManager().send(sender, "ranks.set.invalid", String.join(", ", getAvailableRanks()));
            return true;
        }

        // Définir le rank
        module.getRankDataManager().setRank(target, rank);

        module.getTranslationManager().send(sender, "ranks.set.success", target.getName(), rank);
        module.getTranslationManager().send(target, "ranks.set.received", rank);

        return true;
    }

    private boolean isValidRank(@NotNull String rank) {
        ConfigurationSection ranksSection = module.getConfigManager().getConfigurationSection("ranks.list");
        return ranksSection != null && (ranksSection.contains(rank) || rank.equals("JOUEUR"));
    }

    private List<String> getAvailableRanks() {
        List<String> ranks = new ArrayList<>();
        ranks.add("JOUEUR");

        ConfigurationSection ranksSection = module.getConfigManager().getConfigurationSection("ranks.list");
        if (ranksSection != null) {
            ranks.addAll(ranksSection.getKeys(false));
        }

        return ranks;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2) {
            completions.addAll(getAvailableRanks());
        }

        return completions;
    }
}