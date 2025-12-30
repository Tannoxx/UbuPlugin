package fr.tannoxx.ubuplugin.modules.webmap.commands;

import fr.tannoxx.ubuplugin.modules.webmap.WebMapModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande /webmap pour gérer le module WebMap
 */
public record WebMapCommand(WebMapModule module) implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NonNull [] args) {

        if (!sender.hasPermission("ubuplugin.admin")) {
            module.getTranslationManager().sendPrefixed(sender, "errors.no-permission");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "update" -> handleUpdate(sender);
            case "info" -> handleInfo(sender);
            default -> showHelp(sender);
        }

        return true;
    }

    private void showHelp(@NotNull CommandSender sender) {
        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("§e  Commandes WebMap");
        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("");
        sender.sendMessage("§a/webmap reload §7- Recharge la couche");
        sender.sendMessage("§a/webmap update §7- Télécharge à nouveau les données");
        sender.sendMessage("§a/webmap info §7- Affiche les informations");
        sender.sendMessage("");
        sender.sendMessage("§6§l═══════════════════════════════════");
    }

    private void handleReload(@NotNull CommandSender sender) {
        sender.sendMessage("§eRechargement de la couche des frontières...");

        try {
            module.getBorderLayerProvider().reload();
            sender.sendMessage("§a✓ Couche rechargée avec succès !");
        } catch (Exception e) {
            sender.sendMessage("§c✗ Erreur lors du rechargement");
            module.error("Erreur reload webmap", e);
        }
    }

    private void handleUpdate(@NotNull CommandSender sender) {
        sender.sendMessage("§eTéléchargement des nouvelles données...");
        sender.sendMessage("§7Cela peut prendre quelques secondes...");

        try {
            module.getBorderLayerProvider().forceUpdate();
            sender.sendMessage("§a✓ Données mises à jour !");
        } catch (Exception e) {
            sender.sendMessage("§c✗ Erreur lors de la mise à jour");
            module.error("Erreur update webmap", e);
        }
    }

    private void handleInfo(@NotNull CommandSender sender) {
        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("§e  Informations WebMap");
        sender.sendMessage("§6§l═══════════════════════════════════");
        sender.sendMessage("");
        sender.sendMessage("§7Module: §aWebMap");
        sender.sendMessage("§7Source: §bNatural Earth Data");
        sender.sendMessage("§7Résolution: §b10m (haute)");
        sender.sendMessage("§7Squaremap: §a" + (module.isSquaremapAvailable() ? "Détecté" : "Non détecté"));
        sender.sendMessage("");

        String color = module.getConfigManager().getString("webmap.border-color", "#FF0000");
        int weight = module.getConfigManager().getInt("webmap.border-weight", 2);
        double opacity = module.getConfigManager().getDouble("webmap.border-opacity", 0.8);

        sender.sendMessage("§7Style:");
        sender.sendMessage("§7  Couleur: §e" + color);
        sender.sendMessage("§7  Épaisseur: §e" + weight);
        sender.sendMessage("§7  Opacité: §e" + (opacity * 100) + "%");
        sender.sendMessage("");
        sender.sendMessage("§6§l═══════════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String @NonNull [] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            completions.add("update");
            completions.add("info");
        }

        return completions;
    }
}