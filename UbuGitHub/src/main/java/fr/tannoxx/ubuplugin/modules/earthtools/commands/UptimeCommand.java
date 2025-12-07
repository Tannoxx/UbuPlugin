package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record UptimeCommand(EarthToolsModule module) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
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
            sender.sendMessage(module.getTranslationManager().get(sender, "errors.player-not-found", targetName));
            return true;
        }

        long ticks = target.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long millis = ticks * 50L;
        String timePlayed = formatTime(millis);

        module.getTranslationManager().send(sender, "earthtools.uptime.total",
                target.getName(), timePlayed);

        return true;
    }

    private void showLeaderboard(CommandSender sender) {
        module.getTranslationManager().send(sender, "earthtools.uptime.loading");

        // Exécution asynchrone pour éviter le lag
        new BukkitRunnable() {
            @Override
            public void run() {
                OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();

                List<PlayerStats> playerStats = Arrays.stream(allPlayers)
                        .parallel()
                        .filter(player -> player.hasPlayedBefore() && player.getName() != null)
                        .map(player -> {
                            long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                            long millis = ticks * 50L;
                            return new PlayerStats(player.getName(), millis);
                        })
                        .filter(stats -> stats.playtime > 0)
                        .sorted(Comparator.comparingLong(PlayerStats::playtime).reversed())
                        .toList();

                int totalPlayers = playerStats.size();

                // Retour au thread principal pour l'envoi des messages
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sender.sendMessage("§7§m                                                          ");
                        module.getTranslationManager().send(sender, "earthtools.uptime.header");
                        sender.sendMessage(module.getTranslationManager().get(sender,
                                "earthtools.uptime.players", String.valueOf(totalPlayers)));
                        sender.sendMessage("");

                        int rank = 1;
                        for (PlayerStats stats : playerStats) {
                            String formattedTime = formatTime(stats.playtime);

                            String rankColor = switch (rank) {
                                case 1 -> "§6";  // Or
                                case 2 -> "§7";  // Argent
                                case 3 -> "§c";  // Bronze
                                default -> "§e";
                            };

                            sender.sendMessage(module.getTranslationManager().get(sender,
                                    "earthtools.uptime.rank-entry",
                                    rankColor, String.valueOf(rank), stats.playerName, formattedTime));
                            rank++;
                        }

                        sender.sendMessage("");
                        sender.sendMessage("§7§m                                                          ");
                    }
                }.runTask(module.plugin);
            }
        }.runTaskAsynchronously(module.plugin);
    }

    private String formatTime(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis / (1000 * 60)) % 60;
        long seconds = (millis / 1000) % 60;
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    private record PlayerStats(String playerName, long playtime) {
    }
}