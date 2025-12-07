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

import java.util.*;

/**
 * Gère l'enchantement Timber (coupe tous les troncs connectés)
 */
public class TimberListener implements Listener {

    private final EnchantsModule module;
    private final Set<Material> LOG_TYPES;
    private final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    public TimberListener(EnchantsModule module) {
        this.module = module;

        // Types de bois acceptés
        LOG_TYPES = EnumSet.of(
                Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
                Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
                Material.MANGROVE_LOG, Material.CHERRY_LOG,
                Material.CRIMSON_STEM, Material.WARPED_STEM
                // Ajoutez stripped, wood, hyphae...
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
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

        if (lastUse != null && System.currentTimeMillis() - lastUse < cooldown * 1000) {
            long remaining = (cooldown * 1000 - (System.currentTimeMillis() - lastUse)) / 1000 + 1;
            module.getTranslationManager().send(player, "enchants.timber.cooldown", remaining);
            return;
        }

        // Trouver tous les troncs connectés
        Set<Block> logs = findConnectedLogs(block);
        if (logs.size() <= 1) return;

        // Appliquer cooldown
        module.getTimberCooldowns().put(uuid, System.currentTimeMillis());

        // Casser tous les troncs
        processing.set(true);
        try {
            for (Block log : logs) {
                if (!log.equals(block)) {
                    log.breakNaturally(tool);
                    // Appliquer durabilité (avec Unbreaking pris en compte)
                }
            }
        } finally {
            processing.set(false);
        }
    }

    private Set<Block> findConnectedLogs(Block start) {
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
}