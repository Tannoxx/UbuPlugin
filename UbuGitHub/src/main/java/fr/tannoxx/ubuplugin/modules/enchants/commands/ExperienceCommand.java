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

public record ExperienceCommand(EnchantsModule module) implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ubuplugin.enchants.admin")) {
            module.getTranslationManager().sendPrefixed(sender, "errors.no-permission");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§cUsage: /experience give <joueur> [niveau]");
            sender.sendMessage("§7Niveaux disponibles: 1-3");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            module.getTranslationManager().sendPrefixed(sender, "general.player-not-found", args[1]);
            return true;
        }

        // Niveau (défaut: 1)
        int level = 1;
        if (args.length >= 3) {
            try {
                level = Integer.parseInt(args[2]);
                if (level < 1 || level > 3) {
                    module.getTranslationManager().send(sender, "enchants.errors.invalid-level", "1", "3");
                    return true;
                }
            } catch (NumberFormatException e) {
                module.getTranslationManager().send(sender, "errors.invalid-number");
                return true;
            }
        }

        Enchantment experience = module.getExperienceEnchantment();
        if (experience == null) {
            module.getTranslationManager().send(sender, "enchants.errors.not-loaded", "Experience");
            module.getTranslationManager().send(sender, "enchants.errors.datapack-missing");
            return true;
        }

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(experience, level, true);
        book.setItemMeta(meta);

        target.getInventory().addItem(book);
        module.getTranslationManager().send(sender, "enchants.experience.given", level, target.getName());
        module.getTranslationManager().send(target, "enchants.experience.received", level);

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
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            completions.add("1");
            completions.add("2");
            completions.add("3");
        }
        return completions;
    }
}