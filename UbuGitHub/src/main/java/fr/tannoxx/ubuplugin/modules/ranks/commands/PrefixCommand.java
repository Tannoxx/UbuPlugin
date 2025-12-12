package fr.tannoxx.ubuplugin.modules.ranks.commands;

import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Commande /prefix pour définir le prefix d'un joueur
 */
public record PrefixCommand(RanksModule module) implements CommandExecutor, TabCompleter {

    public PrefixCommand(@NotNull RanksModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ubuplugin.admin")) {
            module.getTranslationManager().sendPrefixed(sender, "errors.no-permission");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /prefix <joueur> <texte>");
            sender.sendMessage("§7Utilisez '_' pour supprimer le prefix");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            module.getTranslationManager().sendPrefixed(sender, "general.player-not-found", args[0]);
            return true;
        }

        String prefix = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        // Supprimer le prefix si "_"
        if (prefix.equals("_")) {
            prefix = "";
        }

        // Définir le prefix
        module.getRankDataManager().setPrefix(target, prefix);

        module.getTranslationManager().send(sender, "ranks.prefix.set", target.getName());
        module.getTranslationManager().send(target, "ranks.prefix.received");

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        return completions;
    }
}