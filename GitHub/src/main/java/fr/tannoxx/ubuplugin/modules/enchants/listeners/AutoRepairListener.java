package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listener pour Auto-Repair avec null checks complets
 */
public record AutoRepairListener(EnchantsModule module) implements Runnable {

    public AutoRepairListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @Override
    public void run() {
        try {
            Enchantment autoRepair = module.getAutoRepairEnchantment();
            if (autoRepair == null) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                // Vérifier armure équipée
                repairItem(player.getInventory().getHelmet(), autoRepair);
                repairItem(player.getInventory().getChestplate(), autoRepair);
                repairItem(player.getInventory().getLeggings(), autoRepair);
                repairItem(player.getInventory().getBoots(), autoRepair);

                // Vérifier inventaire si activé
                if (module.getConfigManager().getBoolean("enchants.autorepair.work-in-inventory", true)) {
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType() != Material.AIR) {
                            repairItem(item, autoRepair);
                        }
                    }
                }
            }
        } catch (Exception e) {
            module.error("Erreur dans AutoRepairListener", e);
        }
    }

    private void repairItem(@Nullable ItemStack item, @NotNull Enchantment autoRepair) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!item.containsEnchantment(autoRepair)) return;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        int currentDamage = damageable.getDamage();
        if (currentDamage == 0) return;

        int repairAmount = module.getConfigManager()
                .getInt("enchants.autorepair.repair-amount", 5);

        int newDamage = Math.max(0, currentDamage - repairAmount);
        damageable.setDamage(newDamage);
        item.setItemMeta(damageable);

        module.debug("Auto-Repair: {} -> {} durability", currentDamage, newDamage);
    }
}