package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Listener pour Experience avec null checks complets
 */
public record ExperienceListener(EnchantsModule module) implements Listener {

    public ExperienceListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir()) return;

        Enchantment experience = module.getExperienceEnchantment();
        if (experience == null || !tool.containsEnchantment(experience)) return;

        int level = tool.getEnchantmentLevel(experience);
        int baseXp = event.getExpToDrop();

        if (baseXp > 0) {
            double bonusPerLevel = module.getConfigManager()
                    .getDouble("enchants.experience.bonus-per-level", 0.25);
            double multiplier = 1.0 + (bonusPerLevel * level);
            int newXp = (int) Math.ceil(baseXp * multiplier);
            event.setExpToDrop(newXp);

            module.debug("Experience: {} XP -> {} XP (x{})", baseXp, newXp, multiplier);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (killer.getGameMode() != GameMode.SURVIVAL) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) return;

        Enchantment experience = module.getExperienceEnchantment();
        if (experience == null || !weapon.containsEnchantment(experience)) return;

        int level = weapon.getEnchantmentLevel(experience);
        int baseXp = event.getDroppedExp();

        if (baseXp > 0) {
            double bonusPerLevel = module.getConfigManager()
                    .getDouble("enchants.experience.bonus-per-level", 0.25);
            double multiplier = 1.0 + (bonusPerLevel * level);
            int newXp = (int) Math.ceil(baseXp * multiplier);
            event.setDroppedExp(newXp);

            module.debug("Experience mob: {} XP -> {} XP", baseXp, newXp);
        }
    }
}