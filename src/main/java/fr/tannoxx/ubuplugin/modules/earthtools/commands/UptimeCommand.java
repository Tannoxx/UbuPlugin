package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commande /uptime avec GUI interactif pour le leaderboard
 * <p>
 * AMÉLIORATIONS v2.0.3:
 * - GUI avec pagination complète (flèches)
 * - Console affiche top 100 avec rangs
 * - Support de tous les joueurs avec temps > 0
 */
public record UptimeCommand(EarthToolsModule module) implements CommandExecutor, TabCompleter, Listener {

    private static final int PLAYERS_PER_PAGE = 45;
    private static final int MAX_CONSOLE_DISPLAY = 100;
    private static final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(module.getTranslationManager().getComponent(sender,
                    "<red>Usage: /uptime <player|leaderboard></red>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("leaderboard") || args[0].equalsIgnoreCase("top")) {
            if (sender instanceof Player player) {
                // ✅ GUI pour les joueurs avec pagination
                showLeaderboardGUI(player, 0);
            } else {
                // ✅ Console: top 100 avec rangs
                showLeaderboardText(sender);
            }
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

    /**
     * ✅ NOUVEAU: Affiche le leaderboard avec pagination
     */
    private void showLeaderboardGUI(@NotNull Player player, int page) {
        module.getTranslationManager().send(player, "earthtools.uptime.loading");

        new BukkitRunnable() {
            @Override
            public void run() {
                OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();

                List<PlayerStats> allStats = Arrays.stream(allPlayers)
                        .parallel()
                        .filter(p -> p.hasPlayedBefore() && p.getName() != null)
                        .map(p -> {
                            long ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
                            long millis = ticks * 50L;
                            return new PlayerStats(p.getName(), p, millis);
                        })
                        .filter(stats -> stats.playtime > 0)
                        .sorted(Comparator.comparingLong(PlayerStats::playtime).reversed())
                        .toList();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        openLeaderboardInventory(player, allStats, page);
                    }
                }.runTask(module.plugin);
            }
        }.runTaskAsynchronously(module.plugin);
    }

    /**
     * Crée et ouvre l'inventaire du leaderboard avec pagination
     */
    private void openLeaderboardInventory(@NotNull Player player, @NotNull List<PlayerStats> allStats, int page) {
        int totalPages = (int) Math.ceil((double) allStats.size() / PLAYERS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1)); // Clamp page

        // Sauvegarder la page actuelle du joueur
        playerPages.put(player.getUniqueId(), page);

        // Créer un inventaire de 54 slots (6 rangées)
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("🏆 Top Joueurs - Page " + (page + 1) + "/" + totalPages,
                        NamedTextColor.GOLD, TextDecoration.BOLD));

        // Calculer les indices pour cette page
        int startIndex = page * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, allStats.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            PlayerStats stat = allStats.get(i);
            int rank = i + 1; // Rang global

            ItemStack skull = createPlayerSkull(stat, rank);
            inv.setItem(slot, skull);
            slot++;
        }

        // Décoration: bordure
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);

        // Remplir la dernière rangée avec la bordure
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        // ✅ Bouton page précédente (slot 48)
        if (page > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            var prevMeta = prevPage.getItemMeta();
            prevMeta.displayName(Component.text("← Page précédente", NamedTextColor.YELLOW, TextDecoration.BOLD));
            prevMeta.lore(List.of(
                    Component.text(""),
                    Component.text("Page " + page + "/" + totalPages, NamedTextColor.GRAY)
            ));
            prevPage.setItemMeta(prevMeta);
            inv.setItem(48, prevPage);
        }

        // Item d'info au centre (slot 49)
        ItemStack infoItem = new ItemStack(Material.BOOK);
        var infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(Component.text("📊 Statistiques", NamedTextColor.AQUA, TextDecoration.BOLD));
        infoMeta.lore(Arrays.asList(
                Component.text(""),
                Component.text("Total joueurs: " + allStats.size(), NamedTextColor.GRAY),
                Component.text("Page: " + (page + 1) + "/" + totalPages, NamedTextColor.GRAY),
                Component.text("Mis à jour en temps réel", NamedTextColor.DARK_GRAY)
        ));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(49, infoItem);

        // ✅ Bouton page suivante (slot 50)
        if (page < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            var nextMeta = nextPage.getItemMeta();
            nextMeta.displayName(Component.text("Page suivante →", NamedTextColor.YELLOW, TextDecoration.BOLD));
            nextMeta.lore(List.of(
                    Component.text(""),
                    Component.text("Page " + (page + 2) + "/" + totalPages, NamedTextColor.GRAY)
            ));
            nextPage.setItemMeta(nextMeta);
            inv.setItem(50, nextPage);
        }

        player.openInventory(inv);
    }

    /**
     * ✅ Gestion des clics sur les boutons de pagination
     */
    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Vérifier le titre de manière sûre avec serialization
        Component title = event.getView().title();
        String titleStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title);

        if (!titleStr.startsWith("🏆 Top Joueurs")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Integer currentPage = playerPages.get(player.getUniqueId());
        if (currentPage == null) currentPage = 0;

        // Détection des boutons
        if (clicked.getType() == Material.ARROW) {
            Component displayName = clicked.getItemMeta().displayName();
            assert displayName != null;
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName);

            if (name.contains("précédente")) {
                // Page précédente
                showLeaderboardGUI(player, currentPage - 1);
            } else if (name.contains("suivante")) {
                // Page suivante
                showLeaderboardGUI(player, currentPage + 1);
            }
        }
    }

    /**
     * Crée une tête de joueur avec ses stats
     */
    private ItemStack createPlayerSkull(@NotNull PlayerStats stat, int rank) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        meta.setOwningPlayer(stat.player);

        // Couleur du nom selon le rang
        Component displayName = switch (rank) {
            case 1 -> Component.text("🥇 #" + rank + " " + stat.playerName, NamedTextColor.GOLD, TextDecoration.BOLD);
            case 2 -> Component.text("🥈 #" + rank + " " + stat.playerName, NamedTextColor.GRAY, TextDecoration.BOLD);
            case 3 -> Component.text("🥉 #" + rank + " " + stat.playerName, NamedTextColor.RED, TextDecoration.BOLD);
            default -> Component.text("#" + rank + " " + stat.playerName, NamedTextColor.YELLOW);
        };

        meta.displayName(displayName);

        // Lore avec les stats
        String timeFormatted = formatTime(stat.playtime);
        long hours = stat.playtime / (1000 * 60 * 60);
        long days = hours / 24;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("⏱ Temps de jeu:", NamedTextColor.GRAY));
        lore.add(Component.text("  " + timeFormatted, NamedTextColor.AQUA));
        lore.add(Component.text(""));

        if (days > 0) {
            lore.add(Component.text("📅 " + days + " jour" + (days > 1 ? "s" : ""), NamedTextColor.GREEN));
        }
        lore.add(Component.text("🕐 " + hours + " heure" + (hours > 1 ? "s" : ""), NamedTextColor.GREEN));

        meta.lore(lore);
        skull.setItemMeta(meta);

        return skull;
    }

    /**
     * ✅ AMÉLIORÉ: Affiche le top 100 avec rangs en console
     */
    private void showLeaderboardText(@NotNull CommandSender sender) {
        module.getTranslationManager().send(sender, "earthtools.uptime.loading");

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
                            return new PlayerStats(player.getName(), player, millis);
                        })
                        .filter(stats -> stats.playtime > 0)
                        .sorted(Comparator.comparingLong(PlayerStats::playtime).reversed())
                        .limit(MAX_CONSOLE_DISPLAY) // ✅ Top 100 max
                        .toList();

                int totalPlayers = playerStats.size();

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
                                    rankColor + "#" + rank + " <white>" + stats.playerName +
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
                                      @NotNull String label, @NotNull String @NonNull [] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("leaderboard");
            completions.add("top");
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        return completions;
    }

    private record PlayerStats(String playerName, OfflinePlayer player, long playtime) {
    }
}