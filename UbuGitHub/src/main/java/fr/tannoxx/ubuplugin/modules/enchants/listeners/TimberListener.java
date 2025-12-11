package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gère l'enchantement Timber (coupe tous les troncs connectés)
 * Thread-safe avec ThreadLocal
 */
public class TimberListener implements Listener {

    private final EnchantsModule module;
    private final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    private static final Set<Material> LOG_TYPES = EnumSet.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG, Material.MANGROVE_ROOTS,
            Material.MUDDY_MANGROVE_ROOTS, Material.CHERRY_LOG, Material.PALE_OAK_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM,
            // Stripped versions
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
            Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
            Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG,
            Material.STRIPPED_PALE_OAK_LOG, Material.STRIPPED_CRIMSON_STEM,
            Material.STRIPPED_WARPED_STEM,
            // Wood versions
            Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD,
            Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD,
            Material.MANGROVE_WOOD, Material.CHERRY_WOOD, Material.PALE_OAK_WOOD,
            Material.CRIMSON_HYPHAE, Material.WARPED_HYPHAE,
            // Stripped wood
            Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD,
            Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_JUNGLE_WOOD,
            Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_DARK_OAK_WOOD,
            Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_CHERRY_WOOD,
            Material.STRIPPED_PALE_OAK_WOOD, Material.STRIPPED_CRIMSON_HYPHAE,
            Material.STRIPPED_WARPED_HYPHAE
    );

    public TimberListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        if (processing.get()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Vérifications de base
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (player.isSneaking()) return;
        if (!LOG_TYPES.contains(block.getType())) return;
        if (!tool.getType().name().endsWith("_AXE")) return;

        Enchantment timber = module.getTimberEnchantment();
        if (timber == null || !tool.containsEnchantment(timber)) return;

        // Vérifier cooldown
        UUID uuid = player.getUniqueId();
        Long lastUse = module.getTimberCooldowns().getIfPresent(uuid);
        int cooldown = module.getConfigManager().getInt("enchants.timber.cooldown", 3);

        if (lastUse != null && System.currentTimeMillis() - lastUse < cooldown * 1000L) {
            long remaining = (cooldown * 1000L - (System.currentTimeMillis() - lastUse)) / 1000 + 1;
            module.getTranslationManager().send(player, "enchants.timber.cooldown", remaining);
            return;
        }

        // Trouver tous les troncs connectés
        Set<Block> logs = findConnectedLogs(block);
        if (logs.size() <= 1) return;

        // Appliquer cooldown
        module.getTimberCooldowns().put(uuid, System.currentTimeMillis());

        // Durabilité
        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        boolean isUnbreakable = tool.getItemMeta() != null && tool.getItemMeta().isUnbreakable();

        // Casser tous les troncs
        processing.set(true);
        try {
            int broken = 0;
            for (Block log : logs) {
                if (log.equals(block)) continue;

                log.breakNaturally(tool);
                broken++;

                // Appliquer durabilité
                if (!isUnbreakable && shouldDamage(unbreakingLevel)) {
                    if (!damageItem(tool, player)) {
                        break;
                    }
                }
            }

            module.debug("Timber: {} blocs cassés", broken);
        } finally {
            processing.set(false);
        }
    }

    @NotNull
    private Set<Block> findConnectedLogs(@NotNull Block start) {
        int maxBlocks = module.getConfigManager().getInt("enchants.timber.max-blocks", 150);
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Block current = queue.poll();

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        Block relative = current.getRelative(x, y, z);
                        if (!visited.contains(relative) && LOG_TYPES.contains(relative.getType())) {
                            visited.add(relative);
                            queue.add(relative);
                        }
                    }
                }
            }
        }

        return visited;
    }

    private boolean shouldDamage(int unbreakingLevel) {
        if (unbreakingLevel <= 0) return true;
        return ThreadLocalRandom.current().nextDouble() > (1.0 / (unbreakingLevel + 1));
    }

    private boolean damageItem(@NotNull ItemStack item, @NotNull Player player) {
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return true;
        }

        int newDamage = damageable.getDamage() + 1;

        if (newDamage >= item.getType().getMaxDurability()) {
            item.setAmount(0);
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return false;
        } else {
            damageable.setDamage(newDamage);
            item.setItemMeta(damageable);
            return true;
        }
    }
}