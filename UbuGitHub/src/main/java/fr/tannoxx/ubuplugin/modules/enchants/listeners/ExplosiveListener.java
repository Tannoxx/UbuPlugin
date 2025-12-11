package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener pour l'enchantement Explosive
 * Thread-safe avec ThreadLocal pour éviter les conflits
 */
public class ExplosiveListener implements Listener {

    private final EnchantsModule module;
    private final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    private static final Set<Material> PICKAXE_BLOCKS = EnumSet.of(
            Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
            Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.CALCITE,
            Material.SMOOTH_BASALT, Material.DRIPSTONE_BLOCK,Material.BASALT, Material.BLACKSTONE,
            Material.NETHERRACK, Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM,
            Material.END_STONE, Material.SANDSTONE, Material.RED_SANDSTONE, Material.PRISMARINE, Material.MAGMA_BLOCK,
            Material.GLOWSTONE, Material.SEA_LANTERN, Material.AMETHYST_BLOCK,
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE
    );

    private static final Set<Material> SHOVEL_BLOCKS = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM,
            Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MUD, Material.CLAY,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.MOSS_BLOCK,
            Material.SOUL_SAND, Material.SOUL_SOIL, Material.SNOW, Material.SNOW_BLOCK
    );

    public ExplosiveListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        if (processing.get()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (player.getGameMode() != GameMode.SURVIVAL) return;

        // Désactiver avec Shift
        if (module.getConfigManager().getBoolean("enchants.explosive.disable-on-sneak", true)) {
            if (player.isSneaking()) return;
        }

        boolean isPickaxe = tool.getType().name().contains("PICKAXE");
        boolean isShovel = tool.getType().name().contains("SHOVEL");

        if (!isPickaxe && !isShovel) return;

        Enchantment explosive = module.getExplosiveEnchantment();
        if (explosive == null || !tool.containsEnchantment(explosive)) return;

        // Vérifier compatibilité du bloc
        if (isPickaxe && !PICKAXE_BLOCKS.contains(block.getType())) return;
        if (isShovel && !SHOVEL_BLOCKS.contains(block.getType())) return;

        // Obtenir la face cassée
        BlockFace face = getHitBlockFace(player, block);
        List<Block> blocksToBreak = get3x3BlocksFromFace(block, face);

        if (blocksToBreak.isEmpty()) return;

        // Vérifier enchantements
        boolean hasSilkTouch = module.getConfigManager().getBoolean("enchants.explosive.silk-touch-compatible", true) &&
                tool.containsEnchantment(Enchantment.SILK_TOUCH);
        boolean collectXP = module.getConfigManager().getBoolean("enchants.explosive.collect-all-xp", true);

        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        boolean isUnbreakable = tool.getItemMeta() != null && tool.getItemMeta().isUnbreakable();

        // Vérifier Experience
        Enchantment experienceEnchant = module.getExperienceEnchantment();
        int experienceLevel = 0;
        if (experienceEnchant != null && tool.containsEnchantment(experienceEnchant)) {
            experienceLevel = tool.getEnchantmentLevel(experienceEnchant);
        }

        // Vérifier Magnetic
        Enchantment magneticEnchant = module.getMagneticEnchantment();
        UUID uuid = player.getUniqueId();
        Boolean magneticToggle = module.getMagneticToggles().getIfPresent(uuid);
        boolean hasMagnetic = magneticEnchant != null &&
                tool.containsEnchantment(magneticEnchant) &&
                (magneticToggle == null || magneticToggle);

        Set<Material> validBlocks = isPickaxe ? PICKAXE_BLOCKS : SHOVEL_BLOCKS;

        processing.set(true);
        try {
            int blocksBroken = 0;

            for (Block targetBlock : blocksToBreak) {
                if (targetBlock.equals(block)) continue;
                if (!validBlocks.contains(targetBlock.getType())) continue;
                if (!canBreakBlock(player, targetBlock)) continue;

                // Obtenir les drops
                Collection<ItemStack> drops = getDrops(targetBlock, tool, hasSilkTouch);

                // Casser le bloc
                targetBlock.setType(Material.AIR);

                // Gérer l'XP
                if (!hasSilkTouch && collectXP) {
                    int xp = getBlockExperience(targetBlock.getType());
                    if (xp > 0) {
                        if (experienceLevel > 0) {
                            double multiplier = 1.0 + (0.25 * experienceLevel);
                            xp = (int) Math.ceil(xp * multiplier);
                        }

                        ExperienceOrb orb = targetBlock.getWorld().spawn(
                                targetBlock.getLocation().add(0.5, 0.5, 0.5),
                                ExperienceOrb.class
                        );
                        orb.setExperience(xp);
                    }
                }

                // Gérer les drops
                handleDrops(player, targetBlock, drops, hasMagnetic);

                blocksBroken++;

                // Appliquer durabilité
                if (!isUnbreakable && shouldDamage(unbreakingLevel)) {
                    if (!damageItem(tool, player)) {
                        break;
                    }
                }
            }

            // Particules
            if (blocksBroken > 0) {
                Location loc = block.getLocation().add(0.5, 0.5, 0.5);
                player.getWorld().spawnParticle(Particle.SMOKE, loc, 15, 0.5, 0.5, 0.5, 0.02);
            }

        } finally {
            processing.set(false);
        }
    }

    private Collection<ItemStack> getDrops(@NotNull Block block, @NotNull ItemStack tool,
                                           boolean hasSilkTouch) {
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

    private BlockFace getHitBlockFace(@NotNull Player player, @NotNull Block block) {
        RayTraceResult result = player.rayTraceBlocks(6.0);

        if (result != null && result.getHitBlockFace() != null) {
            return result.getHitBlockFace();
        }

        // Fallback
        Location playerLoc = player.getEyeLocation();
        Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);

        double diffX = Math.abs(blockCenter.getX() - playerLoc.getX());
        double diffY = Math.abs(blockCenter.getY() - playerLoc.getY());
        double diffZ = Math.abs(blockCenter.getZ() - playerLoc.getZ());

        if (diffY > diffX && diffY > diffZ) {
            return blockCenter.getY() > playerLoc.getY() ? BlockFace.DOWN : BlockFace.UP;
        } else if (diffX > diffZ) {
            return blockCenter.getX() > playerLoc.getX() ? BlockFace.WEST : BlockFace.EAST;
        } else {
            return blockCenter.getZ() > playerLoc.getZ() ? BlockFace.NORTH : BlockFace.SOUTH;
        }
    }

    private List<Block> get3x3BlocksFromFace(@NotNull Block center, @NotNull BlockFace face) {
        List<Block> blocks = new ArrayList<>();
        World world = center.getWorld();
        int x = center.getX();
        int y = center.getY();
        int z = center.getZ();

        switch (face) {
            case UP, DOWN -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        blocks.add(world.getBlockAt(x + dx, y, z + dz));
                    }
                }
            }
            case EAST, WEST -> {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        blocks.add(world.getBlockAt(x, y + dy, z + dz));
                    }
                }
            }
            case NORTH, SOUTH -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        blocks.add(world.getBlockAt(x + dx, y + dy, z));
                    }
                }
            }
        }

        return blocks;
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

    private boolean damageItem(@NotNull ItemStack item, @NotNull Player player) {
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return true;
        }

        int newDamage = damageable.getDamage() + 1;

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

    private int getBlockExperience(@NotNull Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> getRandomInRange(0, 2);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE, NETHER_QUARTZ_ORE -> getRandomInRange(2, 5);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> getRandomInRange(1, 5);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> getRandomInRange(3, 7);
            case NETHER_GOLD_ORE -> getRandomInRange(0, 1);
            default -> 0;
        };
    }

    private int getRandomInRange(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}