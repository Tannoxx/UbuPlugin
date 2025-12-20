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
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public record ExplosiveCommand(EnchantsModule module) implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NonNull [] args) {
        if (!sender.hasPermission("ubuplugin.enchants.admin")) {
            module.getTranslationManager().sendPrefixed(sender, "errors.no-permission");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§cUsage: /explosive give <joueur>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            module.getTranslationManager().sendPrefixed(sender, "general.player-not-found", args[1]);
            return true;
        }

        Enchantment explosive = module.getExplosiveEnchantment();
        if (explosive == null) {
            module.getTranslationManager().send(sender, "enchants.errors.not-loaded", "Explosive");
            module.getTranslationManager().send(sender, "enchants.errors.datapack-missing");
            return true;
        }

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(explosive, 1, true);
        book.setItemMeta(meta);

        target.getInventory().addItem(book);
        module.getTranslationManager().send(sender, "enchants.explosive.given", target.getName());
        module.getTranslationManager().send(target, "enchants.explosive.received");

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String @NonNull [] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("give");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }
        return completions;
    }
}