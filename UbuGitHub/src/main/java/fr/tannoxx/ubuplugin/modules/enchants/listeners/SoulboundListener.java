package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Listener pour Soulbound avec vérification joueur en ligne
 */
public record SoulboundListener(EnchantsModule module) implements Listener {

    public SoulboundListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        Enchantment soulbound = module.getSoulboundEnchantment();
        if (soulbound == null) return;

        UUID uuid = event.getEntity().getUniqueId();
        List<ItemStack> toKeep = new ArrayList<>();

        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack item = it.next();
            if (item != null && item.containsEnchantment(soulbound)) {
                toKeep.add(item.clone());
                it.remove();
            }
        }

        if (toKeep.isEmpty()) return;

        int returnDelay = module.getConfigManager()
                .getInt("enchants.soulbound.return-delay", 2);

        Bukkit.getScheduler().runTaskLater(
                module.plugin,
                () -> {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        for (ItemStack item : toKeep) {
                            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

                            // Si inventaire plein, drop au sol
                            if (!leftover.isEmpty()) {
                                for (ItemStack leftoverItem : leftover.values()) {
                                    player.getWorld().dropItemNaturally(
                                            player.getLocation(), leftoverItem
                                    );
                                }
                            }
                        }

                        module.getTranslationManager().send(player,
                                "enchants.soulbound.returned", toKeep.size());
                    }
                },
                returnDelay
        );
    }
}