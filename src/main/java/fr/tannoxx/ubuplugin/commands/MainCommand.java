package fr.tannoxx.ubuplugin.commands;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.config.ConfigManager;
import fr.tannoxx.ubuplugin.common.i18n.TranslationManager;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record MainCommand(UbuPlugin plugin, ConfigManager configManager, ModuleManager moduleManager,
                          TranslationManager translationManager) implements CommandExecutor, TabCompleter {

    public MainCommand(@NotNull UbuPlugin plugin,
                       @NotNull ConfigManager configManager,
                       @NotNull ModuleManager moduleManager,
                       @NotNull TranslationManager translationManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.moduleManager = moduleManager;
        this.translationManager = translationManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help", "?" -> showHelp(sender);
            case "reload" -> handleReload(sender);
            case "modules" -> handleModules(sender);
            case "info", "version" -> showInfo(sender);
            case "debug" -> handleDebug(sender);
            default -> {
                translationManager.sendPrefixed(sender, "commands.unknown", subCommand);
                showHelp(sender);
            }
        }
        return true;
    }

    private void showHelp(@NotNull CommandSender sender) {
        sender.sendMessage(translationManager.getComponent(sender,
                "<gold>═══════════════════════════════════════════════</gold>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "  <yellow>UbuPlugin - Commandes</yellow>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "<gold>═══════════════════════════════════════════════</gold>"));

        sender.sendMessage(Component.empty());

        sendCommandHelp(sender, "/ubu help", "Affiche cette aide");
        sendCommandHelp(sender, "/ubu info", "Informations sur le plugin");
        sendCommandHelp(sender, "/ubu modules", "Liste des modules et leur état");

        if (sender.hasPermission("ubuplugin.command.reload")) {
            sendCommandHelp(sender, "/ubu reload", "Recharge la configuration");
        }

        if (sender.hasPermission("ubuplugin.admin")) {
            sendCommandHelp(sender, "/ubu debug", "Active/désactive le mode debug");
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(translationManager.getComponent(sender,
                "<gold>═══════════════════════════════════════════════</gold>"));
    }

    private void sendCommandHelp(@NotNull CommandSender sender, @NotNull String command,
                                 @NotNull String description) {
        sender.sendMessage(translationManager.getComponent(sender,
                "  <aqua>" + command + "</aqua> <gray>- " + description + "</gray>"));
    }

    private void handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission("ubuplugin.command.reload")) {
            translationManager.sendPrefixed(sender, "errors.no-permission");
            return;
        }

        sender.sendMessage(translationManager.getComponent(sender,
                "<yellow>Rechargement du plugin...</yellow>"));

        long startTime = System.currentTimeMillis();

        try {
            boolean success = plugin.reload();
            long loadTime = System.currentTimeMillis() - startTime;

            if (success) {
                sender.sendMessage(translationManager.getComponent(sender,
                        "<green>✓ Plugin rechargé avec succès ! (" + loadTime + "ms)</green>"));
            } else {
                sender.sendMessage(translationManager.getComponent(sender,
                        "<red>✗ Erreur lors du rechargement</red>"));
            }
        } catch (Exception e) {
            sender.sendMessage(translationManager.getComponent(sender,
                    "<red>✗ Erreur: " + e.getMessage() + "</red>"));
        }
    }

    private void handleModules(@NotNull CommandSender sender) {
        sender.sendMessage(translationManager.getComponent(sender,
                "<gold>═══════════════════════════════════════════════</gold>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "  <yellow>Modules (" + moduleManager.getLoadedModulesCount() +
                        "/" + moduleManager.getAllModules().size() + " activés)</yellow>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "<gold>═══════════════════════════════════════════════</gold>"));

        sender.sendMessage(Component.empty());

        List<String> statusReport = moduleManager.getModulesStatusReport();
        for (String line : statusReport) {
            String color = line.contains("✓") ? "<green>" :
                    line.contains("✗") ? "<red>" : "<gray>";
            sender.sendMessage(translationManager.getComponent(sender,
                    "  " + color + line + (color.equals("<gray>") ? "" :
                            (color.equals("<green>") ? "</green>" : "</red>"))));
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(translationManager.getComponent(sender,
                "<gold>═══════════════════════════════════════════════</gold>"));
    }

    private void showInfo(@NotNull CommandSender sender) {
        sender.sendMessage(translationManager.getComponent(sender,
                "<gold>═══════════════════════════════════════════════</gold>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "  <yellow>UbuPlugin</yellow>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "<gold>═══════════════════════════════════════════════</gold>"));

        sender.sendMessage(Component.empty());
        sender.sendMessage(translationManager.getComponent(sender,
                "  <gray>Version: " + plugin.getDescription().getVersion() + "</gray>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "  <gray>Auteur: " + plugin.getDescription().getAuthors() + "</gray>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "  <gray>Site: " + plugin.getDescription().getWebsite() + "</gray>"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(translationManager.getComponent(sender,
                "  <gray>Base de données: " + plugin.getDatabaseManager().getDatabaseType() + "</gray>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "  <gray>Langues: " + plugin.getTranslationManager().getLoadedLanguagesCount() + "</gray>"));
        sender.sendMessage(translationManager.getComponent(sender,
                "  <gray>Modules: " + moduleManager.getLoadedModulesCount() + "/" +
                        moduleManager.getAllModules().size() + "</gray>"));

        if (sender.hasPermission("ubuplugin.admin")) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(translationManager.getComponent(sender,
                    "  <dark_gray>" + plugin.getDatabaseManager().getPoolStats() + "</dark_gray>"));
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(translationManager.getComponent(sender,
                "<gold>═══════════════════════════════════════════════</gold>"));
    }

    private void handleDebug(@NotNull CommandSender sender) {
        if (!sender.hasPermission("ubuplugin.admin")) {
            translationManager.sendPrefixed(sender, "errors.no-permission");
            return;
        }

        boolean currentDebug = configManager.isDebugEnabled();
        configManager.getConfig().set("general.debug", !currentDebug);
        plugin.saveConfig();
        configManager.reloadConfiguration();

        String status = !currentDebug ? "activé" : "désactivé";
        sender.sendMessage(translationManager.getComponent(sender,
                "<green>✓ Mode debug " + status + "</green>"));
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender,
                                               @NotNull Command command,
                                               @NotNull String label,
                                               @NotNull String[] args) {

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("help");
            completions.add("info");
            completions.add("modules");

            if (sender.hasPermission("ubuplugin.command.reload")) {
                completions.add("reload");
            }

            if (sender.hasPermission("ubuplugin.admin")) {
                completions.add("debug");
            }

            String input = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.startsWith(input))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return completions;
    }
}