package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record UptimeCommand(EarthToolsModule module) implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(module.getTranslationManager().getComponent(sender,
                    "<red>Usage: /uptime <player|leaderboard></red>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("leaderboard") || args[0].equalsIgnoreCase("top")) {
            showLeaderboard(sender);
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore()) {
            module.getTranslationManager().send(sender, "errors.player-not-found", targetName);
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
                        sender.sendMessage(module.getTranslationManager().getComponent(sender,
                                "<gray><strikethrough>                                                          </strikethrough></gray>"));
                        module.getTranslationManager().send(sender, "earthtools.uptime.header");
                        module.getTranslationManager().send(sender, "earthtools.uptime.players",
                                String.valueOf(totalPlayers));
                        sender.sendMessage(Component.empty());

                        int rank = 1;
                        for (PlayerStats stats : playerStats) {
                            String formattedTime = formatTime(stats.playtime);

                            String rankColor = switch (rank) {
                                case 1 -> "<gold>";
                                case 2 -> "<gray>";
                                case 3 -> "<red>";
                                default -> "<yellow>";
                            };

                            sender.sendMessage(module.getTranslationManager().getComponent(sender,
                                    rankColor + "#" + rank + "</gold> <white>" + stats.playerName +
                                            "</white> <gray>-</gray> <aqua>" + formattedTime + "</aqua>"));
                            rank++;
                        }

                        sender.sendMessage(Component.empty());
                        sender.sendMessage(module.getTranslationManager().getComponent(sender,
                                "<gray><strikethrough>                                                          </strikethrough></gray>"));
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("leaderboard");
            completions.add("top");
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        return completions;
    }

    private record PlayerStats(String playerName, long playtime) {
    }
}