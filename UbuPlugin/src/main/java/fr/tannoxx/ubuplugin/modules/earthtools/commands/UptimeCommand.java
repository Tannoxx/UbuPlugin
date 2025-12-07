package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.earthtools.util.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record UptimeCommand(UbuPlugin plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /uptime <player|leaderboard>");
            return true;
        }

        if (args[0].equalsIgnoreCase("leaderboard") || args[0].equalsIgnoreCase("top")) {
            showLeaderboard(sender);
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore()) {
            sender.sendMessage("⓪ §cLe joueur §e" + targetName + "§c n'a jamais joué sur ce serveur ! / §rⓧ §cThe player §e" + targetName + "§c has never joined this server!");
            return true;
        }

        long ticks = target.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long millis = ticks * 50L;
        String timePlayed = TimeUtils.formatTime(millis);
        sender.sendMessage("⓪ §aTemps de jeu total de §e / §rⓧ §aTotal playtime of §e" + target.getName() + "§a : §b" + timePlayed);
        return true;
    }

    private void showLeaderboard(CommandSender sender) {
        sender.sendMessage("⓪ §eChargement du classement... / §rⓧ §eLoading leaderboard...");

        // Exécution asynchrone pour éviter le lag
        new BukkitRunnable() {
            @Override
            public void run() {
                OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();

                // Optimisation : utiliser une liste directement triée
                List<PlayerStats> playerStats = Arrays.stream(allPlayers)
                        .parallel() // Traitement parallèle pour optimisation
                        .filter(player -> player.hasPlayedBefore() && player.getName() != null)
                        .map(player -> {
                            long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                            long millis = ticks * 50L;
                            return new PlayerStats(player.getName(), millis);
                        })
                        .filter(stats -> stats.playtime > 0) // Exclure les temps à 0h
                        .sorted(Comparator.comparingLong(PlayerStats::playtime).reversed())
                        .toList();

                int totalPlayers = playerStats.size();

                // Retour au thread principal pour l'envoi des messages
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sender.sendMessage("§7§m                                                          ");
                        sender.sendMessage("⓪ §6§lClassement des temps de jeu §7/ §rⓧ §6§lPlaytime Leaderboard");
                        sender.sendMessage("§8(§7" + totalPlayers + " joueurs§8)");
                        sender.sendMessage("");

                        int rank = 1;
                        for (PlayerStats stats : playerStats) {
                            String formattedTime = TimeUtils.formatTime(stats.playtime);

                            String rankColor = switch (rank) {
                                case 1 -> "§6";  // Or
                                case 2 -> "§7";  // Argent
                                case 3 -> "§c";  // Bronze
                                default -> "§e";
                            };

                            sender.sendMessage(rankColor + "#" + rank + " §f" + stats.playerName + " §7- §b" + formattedTime);
                            rank++;
                        }

                        sender.sendMessage("");
                        sender.sendMessage("§7§m                                                          ");
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    // Record pour stocker les stats de manière optimisée
    private record PlayerStats(String playerName, long playtime) {}
}