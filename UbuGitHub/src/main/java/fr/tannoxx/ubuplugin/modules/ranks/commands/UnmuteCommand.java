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
import java.util.List;

/**
 * Commande /unmute pour unmute un joueur
 */
public record UnmuteCommand(RanksModule module) implements CommandExecutor, TabCompleter {

    public UnmuteCommand(@NotNull RanksModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ubuplugin.admin")) {
            module.getTranslationManager().sendPrefixed(sender, "errors.no-permission");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /unmute <joueur>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            module.getTranslationManager().sendPrefixed(sender, "general.player-not-found", args[0]);
            return true;
        }

        // Vérifier si mute
        if (!module.getRankDataManager().isMuted(target)) {
            module.getTranslationManager().send(sender, "ranks.mute.not-muted");
            return true;
        }

        // Unmute le joueur
        module.getRankDataManager().unmutePlayer(target);

        module.getTranslationManager().send(sender, "ranks.mute.unmute-success", target.getName());
        module.getTranslationManager().send(target, "ranks.mute.unmuted");

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