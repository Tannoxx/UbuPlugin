package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
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
 * Listener pour l'enchantement Veinminer
 * Mine automatiquement tous les minerais connectés du même type
 * Thread-safe avec ThreadLocal
 * <p>
 * ✅ FIX v2.0.3: Correction logique findConnectedOres - ne mine QUE les minerais connectés
 *
 * @author Tannoxx
 * @version 2.0.3
 */
public class VeinminerListener implements Listener {

    private final EnchantsModule module;
    private final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    // ✅ OPTIMISATION: Offsets précalculés (réutilisables, pas de garbage collection)
    private static final int[][] ADJACENT_OFFSETS = {
            {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1},
            {-1, -1, 0}, {-1, 1, 0}, {1, -1, 0}, {1, 1, 0},
            {-1, 0, -1}, {-1, 0, 1}, {1, 0, -1}, {1, 0, 1},
            {0, -1, -1}, {0, -1, 1}, {0, 1, -1}, {0, 1, 1},
            {-1, -1, -1}, {-1, -1, 1}, {-1, 1, -1}, {-1, 1, 1},
            {1, -1, -1}, {1, -1, 1}, {1, 1, -1}, {1, 1, 1}
    };

    private static final Set<Material> ORE_TYPES = EnumSet.of(
            Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE,
            Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE,
            Material.DIAMOND_ORE, Material.EMERALD_ORE,
            Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS,
            Material.GLOWSTONE, Material.AMETHYST_CLUSTER
    );

    public VeinminerListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        if (processing.get()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!ORE_TYPES.contains(block.getType())) return;
        if (!tool.getType().name().endsWith("_PICKAXE")) return;

        Enchantment veinminer = module.getVeinminerEnchantment();
        if (veinminer == null || !tool.containsEnchantment(veinminer)) return;

        UUID uuid = player.getUniqueId();
        Boolean veinminerEnabled = module.getVeinminerToggles().getIfPresent(uuid);
        if (veinminerEnabled != null && !veinminerEnabled) {
            return;
        }

        if (player.isSneaking()) return;

        Set<Block> vein = findConnectedOres(block, block.getType());
        if (vein.size() <= 1) return;

        Long lastUse = module.getVeinminerCooldowns().getIfPresent(uuid);
        int cooldown = module.getConfigManager().getInt("enchants.veinminer.cooldown", 5);

        if (lastUse != null && System.currentTimeMillis() - lastUse < cooldown * 1000L) {
            long remaining = (cooldown * 1000L - (System.currentTimeMillis() - lastUse)) / 1000 + 1;
            module.getTranslationManager().send(player, "enchants.veinminer.cooldown", remaining);
            return;
        }

        module.getVeinminerCooldowns().put(uuid, System.currentTimeMillis());

        boolean hasSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        boolean isUnbreakable = tool.getItemMeta() != null && tool.getItemMeta().isUnbreakable();

        Enchantment experienceEnchant = module.getExperienceEnchantment();
        int experienceLevel = 0;
        if (experienceEnchant != null && tool.containsEnchantment(experienceEnchant)) {
            experienceLevel = tool.getEnchantmentLevel(experienceEnchant);
        }

        Enchantment magneticEnchant = module.getMagneticEnchantment();
        Boolean magneticToggle = module.getMagneticToggles().getIfPresent(uuid);
        boolean hasMagnetic = magneticEnchant != null &&
                tool.containsEnchantment(magneticEnchant) &&
                (magneticToggle == null || magneticToggle);

        processing.set(true);
        try {
            int blocksMined = 0;
            int totalXP = 0;

            for (Block ore : vein) {
                if (ore.equals(block)) continue;

                if (!canBreakBlock(player, ore)) continue;

                Collection<ItemStack> drops = getDrops(ore, tool, hasSilkTouch, fortuneLevel);
                int xp = getOreExperience(ore.getType());
                totalXP += xp;

                ore.setType(Material.AIR);
                handleDrops(player, ore, drops, hasMagnetic);

                blocksMined++;

                if (!isUnbreakable && shouldDamage(unbreakingLevel)) {
                    if (!damageItem(tool, player)) {
                        break;
                    }
                }
            }

            if (totalXP > 0 && !hasSilkTouch) {
                if (experienceLevel > 0) {
                    double multiplier = 1.0 + (0.25 * experienceLevel);
                    totalXP = (int) Math.ceil(totalXP * multiplier);
                }

                ExperienceOrb orb = block.getWorld().spawn(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        ExperienceOrb.class
                );
                orb.setExperience(totalXP);
            }

            if (blocksMined > 0) {
                Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                player.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 0.5, 0.5, 0.5, 0.02);
            }

            module.debug("Veinminer: {} blocs minés, {} XP total", blocksMined, totalXP);

        } finally {
            processing.set(false);
        }
    }

    /**
     * ✅ FIX v2.0.3: Correction de la logique
     * On ajoute dans visited UNIQUEMENT les blocs du bon type
     * Cela empêche de casser des blocs non-minerais
     */
    @NotNull
    private Set<Block> findConnectedOres(@NotNull Block start, @NotNull Material targetType) {
        int maxBlocks = module.getConfigManager().getInt("enchants.veinminer.max-blocks", 150);

        Set<Block> visited = new HashSet<>(maxBlocks);
        Deque<Block> queue = new ArrayDeque<>(maxBlocks);

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Block current = queue.poll();

            for (int[] offset : ADJACENT_OFFSETS) {
                Block relative = current.getRelative(offset[0], offset[1], offset[2]);

                // ✅ FIX: Vérifier le type AVANT d'ajouter dans visited
                // On n'ajoute que les blocs du même type de minerai
                if (relative.getType() == targetType && !visited.contains(relative)) {
                    visited.add(relative);
                    queue.add(relative);
                }
            }
        }

        return visited;
    }

    @NotNull
    private Collection<ItemStack> getDrops(@NotNull Block block, @NotNull ItemStack tool,
                                           boolean hasSilkTouch, int fortuneLevel) {
        if (hasSilkTouch) {
            return Collections.singletonList(new ItemStack(block.getType()));
        }
        return block.getDrops(tool);
    }

    private void handleDrops(@NotNull Player player, @NotNull Block block,
                             @NotNull Collection<ItemStack> drops, boolean hasMagnetic) {
        if (hasMagnetic) {
            for (ItemStack drop : drops) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
                if (!leftover.isEmpty()) {
                    for (ItemStack leftoverItem : leftover.values()) {
                        player.getWorld().dropItemNaturally(
                                block.getLocation().add(0.5, 0.5, 0.5),
                                leftoverItem
                        );
                    }
                }
            }
        } else {
            for (ItemStack drop : drops) {
                player.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        drop
                );
            }
        }
    }

    private boolean canBreakBlock(@NotNull Player player, @NotNull Block block) {
        BlockBreakEvent testEvent = new BlockBreakEvent(block, player);
        module.plugin.getServer().getPluginManager().callEvent(testEvent);
        return !testEvent.isCancelled();
    }

    private boolean shouldDamage(int unbreakingLevel) {
        if (unbreakingLevel <= 0) return true;
        return ThreadLocalRandom.current().nextDouble() > (1.0 / (unbreakingLevel + 1));
    }

    /**
     * ✅ FIX: Protection contre durabilité négative
     */
    private boolean damageItem(@NotNull ItemStack item, @NotNull Player player) {
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return true;
        }

        int newDamage = Math.max(0, damageable.getDamage() + 1);

        if (newDamage >= item.getType().getMaxDurability()) {
            item.setAmount(0);
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return false;
        } else {
            damageable.setDamage(newDamage);
            item.setItemMeta(damageable);
            return true;
        }
    }

    private int getOreExperience(@NotNull Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> getRandomInRange(0, 2);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE, NETHER_QUARTZ_ORE -> getRandomInRange(2, 5);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> getRandomInRange(1, 5);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> getRandomInRange(3, 7);
            case NETHER_GOLD_ORE -> getRandomInRange(0, 1);
            case ANCIENT_DEBRIS -> getRandomInRange(0, 1);
            default -> 0;
        };
    }

    private int getRandomInRange(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}