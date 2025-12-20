package fr.tannoxx.ubuplugin.modules.ranks.commands;

import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Commande /mute pour mute un joueur
 */
public record MuteCommand(RanksModule module) implements CommandExecutor, TabCompleter {

    public MuteCommand(@NotNull RanksModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NonNull [] args) {
        if (!sender.hasPermission("ubuplugin.admin")) {
            module.getTranslationManager().sendPrefixed(sender, "errors.no-permission");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /mute <joueur> [durée_minutes] [raison]");
            sender.sendMessage("§7Exemple: /mute Player 60 Spam");
            sender.sendMessage("§7Durée 0 = permanent");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            module.getTranslationManager().sendPrefixed(sender, "general.player-not-found", args[0]);
            return true;
        }

        // Vérifier si déjà mute
        if (module.getRankDataManager().isMuted(target)) {
            module.getTranslationManager().send(sender, "ranks.mute.already-muted");
            return true;
        }

        // Durée (défaut: permanent)
        long duration = 0;
        if (args.length >= 2) {
            try {
                duration = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                module.getTranslationManager().send(sender, "errors.invalid-number");
                return true;
            }
        }

        // Raison
        String reason = "Aucune raison";
        if (args.length >= 3) {
            reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }

        // Mute le joueur
        module.getRankDataManager().mutePlayer(target, reason, duration);

        // Messages
        module.getTranslationManager().send(sender, "ranks.mute.success", target.getName());

        if (duration > 0) {
            module.getTranslationManager().send(target, "ranks.mute.muted", formatDuration(duration));
        } else {
            module.getTranslationManager().send(target, "ranks.mute.muted", "permanent");
        }
        module.getTranslationManager().send(target, "ranks.mute.reason", reason);

        return true;
    }

    private String formatDuration(long minutes) {
        if (minutes < 60) {
            return minutes + " minutes";
        } else if (minutes < 1440) {
            return (minutes / 60) + " heures";
        } else {
            return (minutes / 1440) + " jours";
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String @NonNull [] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2) {
            completions.addAll(List.of("0", "30", "60", "1440"));
        } else if (args.length == 3) {
            // Raisons pré-définies depuis config
            completions.addAll(module.getConfigManager().getStringList("ranks.mute.reasons"));
        }

        return completions;
    }
}