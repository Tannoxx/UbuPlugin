package fr.tannoxx.ubuplugin.modules.enchants.commands;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Template de base pour toutes les commandes d'enchantements
 * TOUTES utilisent getComponent() pour MiniMessage
 */
public class TimberCommand implements CommandExecutor, TabCompleter {

    private final EnchantsModule module;

    public TimberCommand(EnchantsModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ubuplugin.enchants.admin")) {
            module.getTranslationManager().sendPrefixed(sender, "errors.no-permission");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(module.getTranslationManager().getComponent(sender,
                    "<red>Usage: /timber give <joueur></red>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            module.getTranslationManager().sendPrefixed(sender, "general.player-not-found", args[1]);
            return true;
        }

        Enchantment timber = module.getTimberEnchantment();
        if (timber == null) {
            module.getTranslationManager().send(sender, "enchants.errors.not-loaded", "Timber");
            module.getTranslationManager().send(sender, "enchants.errors.datapack-missing");
            return true;
        }

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(timber, 1, true);
        book.setItemMeta(meta);

        target.getInventory().addItem(book);
        module.getTranslationManager().send(sender, "enchants.timber.given", target.getName());
        module.getTranslationManager().send(target, "enchants.timber.received");

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("give");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }
        return completions;
    }
}