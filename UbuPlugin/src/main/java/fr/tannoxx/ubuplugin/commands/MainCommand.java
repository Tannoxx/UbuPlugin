package fr.tannoxx.ubuplugin.commands;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.config.ConfigManager;
import fr.tannoxx.ubuplugin.common.i18n.TranslationManager;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commande principale /ubu du plugin
 * Gère help, reload, modules, info, etc.
 * 
 * @author Tannoxx
 * @version 2.0.0
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    
    private final UbuPlugin plugin;
    private final ConfigManager configManager;
    private final ModuleManager moduleManager;
    private final TranslationManager translationManager;
    
    /**
     * Constructeur
     * @param plugin Instance du plugin
     * @param configManager Gestionnaire de configuration
     * @param moduleManager Gestionnaire de modules
     * @param translationManager Gestionnaire de traductions
     */
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
        
        // /ubu sans arguments = help
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        return switch (subCommand) {
            case "help", "?" -> {
                showHelp(sender);
                yield true;
            }
            case "reload" -> {
                handleReload(sender);
                yield true;
            }
            case "modules" -> {
                handleModules(sender);
                yield true;
            }
            case "info", "version" -> {
                showInfo(sender);
                yield true;
            }
            case "debug" -> {
                handleDebug(sender);
                yield true;
            }
            default -> {
                translationManager.sendPrefixed(sender, "commands.unknown", subCommand);
                showHelp(sender);
                yield true;
            }
        };
    }
    
    /**
     * Affiche l'aide de la commande
     * @param sender Sender
     */
    private void showHelp(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("═══════════════════════════════════════════════")
            .color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  UbuPlugin - Commandes")
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("═══════════════════════════════════════════════")
            .color(NamedTextColor.GOLD));
        
        sender.sendMessage(Component.text(""));
        
        sendCommandHelp(sender, "/ubu help", "Affiche cette aide");
        sendCommandHelp(sender, "/ubu info", "Informations sur le plugin");
        sendCommandHelp(sender, "/ubu modules", "Liste des modules et leur état");
        
        if (sender.hasPermission("ubuplugin.command.reload")) {
            sendCommandHelp(sender, "/ubu reload", "Recharge la configuration");
        }
        
        if (sender.hasPermission("ubuplugin.admin")) {
            sendCommandHelp(sender, "/ubu debug", "Active/désactive le mode debug");
        }
        
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("═══════════════════════════════════════════════")
            .color(NamedTextColor.GOLD));
    }
    
    /**
     * Envoie une ligne d'aide formatée
     * @param sender Sender
     * @param command Commande
     * @param description Description
     */
    private void sendCommandHelp(@NotNull CommandSender sender, @NotNull String command, 
                                @NotNull String description) {
        sender.sendMessage(Component.text("  " + command)
            .color(NamedTextColor.AQUA)
            .append(Component.text(" - " + description)
                .color(NamedTextColor.GRAY)));
    }
    
    /**
     * Gère le rechargement
     * @param sender Sender
     */
    private void handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission("ubuplugin.command.reload")) {
            translationManager.sendPrefixed(sender, "errors.no-permission");
            return;
        }
        
        sender.sendMessage(Component.text("Rechargement du plugin...")
            .color(NamedTextColor.YELLOW));
        
        long startTime = System.currentTimeMillis();
        
        try {
            boolean success = plugin.reload();
            long loadTime = System.currentTimeMillis() - startTime;
            
            if (success) {
                sender.sendMessage(Component.text("✓ Plugin rechargé avec succès ! (" + loadTime + "ms)")
                    .color(NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("✗ Erreur lors du rechargement")
                    .color(NamedTextColor.RED));
            }
        } catch (Exception e) {
            sender.sendMessage(Component.text("✗ Erreur: " + e.getMessage())
                .color(NamedTextColor.RED));
        }
    }
    
    /**
     * Affiche la liste des modules
     * @param sender Sender
     */
    private void handleModules(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("═══════════════════════════════════════════════")
            .color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  Modules (" + moduleManager.getLoadedModulesCount() + 
            "/" + moduleManager.getAllModules().size() + " activés)")
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("═══════════════════════════════════════════════")
            .color(NamedTextColor.GOLD));
        
        sender.sendMessage(Component.text(""));
        
        List<String> statusReport = moduleManager.getModulesStatusReport();
        for (String line : statusReport) {
            NamedTextColor color = line.contains("✓") ? NamedTextColor.GREEN : 
                                  line.contains("✗") ? NamedTextColor.RED : 
                                  NamedTextColor.GRAY;
            sender.sendMessage(Component.text("  " + line).color(color));
        }
        
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("═══════════════════════════════════════════════")
            .color(NamedTextColor.GOLD));
    }
    
    /**
     * Affiche les informations du plugin
     * @param sender Sender
     */
    private void showInfo(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("═══════════════════════════════════════════════")
            .color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  UbuPlugin")
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("═══════════════════════════════════════════════")
            .color(NamedTextColor.GOLD));
        
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("  Version: " + plugin.getDescription().getVersion())
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Auteur: " + plugin.getDescription().getAuthors())
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Site: " + plugin.getDescription().getWebsite())
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("  Base de données: " + 
            plugin.getDatabaseManager().getDatabaseType())
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Langues: " + 
            plugin.getTranslationManager().getLoadedLanguagesCount())
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Modules: " + 
            moduleManager.getLoadedModulesCount() + "/" + moduleManager.getAllModules().size())
            .color(NamedTextColor.GRAY));
        
        if (sender.hasPermission("ubuplugin.admin")) {
            sender.sendMessage(Component.text(""));
            sender.sendMessage(Component.text("  " + 
                plugin.getDatabaseManager().getPoolStats())
                .color(NamedTextColor.DARK_GRAY));
        }
        
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("═══════════════════════════════════════════════")
            .color(NamedTextColor.GOLD));
    }
    
    /**
     * Active/désactive le mode debug
     * @param sender Sender
     */
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
        sender.sendMessage(Component.text("✓ Mode debug " + status)
            .color(NamedTextColor.GREEN));
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, 
                                               @NotNull Command command, 
                                               @NotNull String label, 
                                               @NotNull String[] args) {
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Suggestions principales
            completions.add("help");
            completions.add("info");
            completions.add("modules");
            
            if (sender.hasPermission("ubuplugin.command.reload")) {
                completions.add("reload");
            }
            
            if (sender.hasPermission("ubuplugin.admin")) {
                completions.add("debug");
            }
            
            // Filtrer selon ce que l'utilisateur tape
            String input = args[0].toLowerCase();
            return completions.stream()
                .filter(s -> s.startsWith(input))
                .sorted()
                .collect(Collectors.toList());
        }
        
        return completions;
    }
}
