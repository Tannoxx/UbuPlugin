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
 *
 * Compatibilités:
 * - Fortune: chaque bloc a sa propre chance
 * - Silk Touch: tous les blocs donnent leur forme brute
 * - Experience: XP totale multipliée
 * - Magnetic: items directement dans l'inventaire
 *
 * @author Tannoxx
 * @version 2.0.1
 */
public class VeinminerListener implements Listener {

    private final EnchantsModule module;
    private final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    // Tous les minerais vanilla
    private static final Set<Material> ORE_TYPES = EnumSet.of(
            // Overworld
            Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE,
            Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE,
            Material.DIAMOND_ORE, Material.EMERALD_ORE,

            // Deepslate variants
            Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE,

            // Nether
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS,

            // Autres minerais
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

        // Vérifications de base
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        // Vérifier que c'est un minerai
        if (!ORE_TYPES.contains(block.getType())) return;

        // Vérifier que c'est une pioche
        if (!tool.getType().name().endsWith("_PICKAXE")) return;

        Enchantment veinminer = module.getVeinminerEnchantment();
        if (veinminer == null || !tool.containsEnchantment(veinminer)) return;

        // Vérifier le toggle
        UUID uuid = player.getUniqueId();
        Boolean veinminerEnabled = module.getVeinminerToggles().getIfPresent(uuid);
        if (veinminerEnabled != null && !veinminerEnabled) {
            return;
        }

        // Désactiver avec Shift
        if (player.isSneaking()) return;

        // Trouver tous les minerais connectés du même type
        Set<Block> vein = findConnectedOres(block, block.getType());
        if (vein.size() <= 1) return; // Pas de filon, juste un bloc isolé

        // Vérifier cooldown SEULEMENT si c'est un vrai filon
        Long lastUse = module.getVeinminerCooldowns().getIfPresent(uuid);
        int cooldown = module.getConfigManager().getInt("enchants.veinminer.cooldown", 5);

        if (lastUse != null && System.currentTimeMillis() - lastUse < cooldown * 1000L) {
            long remaining = (cooldown * 1000L - (System.currentTimeMillis() - lastUse)) / 1000 + 1;
            module.getTranslationManager().send(player, "enchants.veinminer.cooldown", remaining);
            return;
        }

        // Appliquer cooldown
        module.getVeinminerCooldowns().put(uuid, System.currentTimeMillis());

        // Vérifier enchantements
        boolean hasSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
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
        Boolean magneticToggle = module.getMagneticToggles().getIfPresent(uuid);
        boolean hasMagnetic = magneticEnchant != null &&
                tool.containsEnchantment(magneticEnchant) &&
                (magneticToggle == null || magneticToggle);

        // Miner le filon
        processing.set(true);
        try {
            int blocksMined = 0;
            int totalXP = 0;

            for (Block ore : vein) {
                if (ore.equals(block)) continue; // Bloc déjà cassé par l'événement principal

                // Vérifier protection Towny
                if (!canBreakBlock(player, ore)) continue;

                // Obtenir les drops
                Collection<ItemStack> drops = getDrops(ore, tool, hasSilkTouch, fortuneLevel);

                // Obtenir l'XP
                int xp = getOreExperience(ore.getType());
                totalXP += xp;

                // Casser le bloc
                ore.setType(Material.AIR);

                // Gérer les drops
                handleDrops(player, ore, drops, hasMagnetic);

                blocksMined++;

                // Appliquer durabilité
                if (!isUnbreakable && shouldDamage(unbreakingLevel)) {
                    if (!damageItem(tool, player)) {
                        break; // Outil cassé, arrêter immédiatement
                    }
                }
            }

            // Spawn de l'XP totale
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

            // Particules de fumée
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
     * Trouve tous les minerais connectés du même type
     */
    @NotNull
    private Set<Block> findConnectedOres(@NotNull Block start, @NotNull Material targetType) {
        int maxBlocks = module.getConfigManager().getInt("enchants.veinminer.max-blocks", 150);
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Block current = queue.poll();

            // Vérifier les 26 blocs adjacents (3x3x3 autour)
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        Block relative = current.getRelative(x, y, z);
                        if (!visited.contains(relative) && relative.getType() == targetType) {
                            visited.add(relative);
                            queue.add(relative);
                        }
                    }
                }
            }
        }

        return visited;
    }

    /**
     * Obtient les drops d'un minerai avec Fortune/Silk Touch
     */
    @NotNull
    private Collection<ItemStack> getDrops(@NotNull Block block, @NotNull ItemStack tool,
                                           boolean hasSilkTouch, int fortuneLevel) {
        if (hasSilkTouch) {
            // Silk Touch: retourner le minerai brut
            return Collections.singletonList(new ItemStack(block.getType()));
        }

        // Fortune: utiliser la méthode native de Minecraft
        return block.getDrops(tool);
    }

    /**
     * Gère les drops (inventaire ou sol)
     */
    private void handleDrops(@NotNull Player player, @NotNull Block block,
                             @NotNull Collection<ItemStack> drops, boolean hasMagnetic) {
        if (hasMagnetic) {
            // Magnetic: mettre dans l'inventaire
            for (ItemStack drop : drops) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
                if (!leftover.isEmpty()) {
                    // Inventaire plein: drop au sol
                    for (ItemStack leftoverItem : leftover.values()) {
                        player.getWorld().dropItemNaturally(
                                block.getLocation().add(0.5, 0.5, 0.5),
                                leftoverItem
                        );
                    }
                }
            }
        } else {
            // Pas Magnetic: drop au sol
            for (ItemStack drop : drops) {
                player.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        drop
                );
            }
        }
    }

    /**
     * Vérifie si le joueur peut casser un bloc (Towny)
     */
    private boolean canBreakBlock(@NotNull Player player, @NotNull Block block) {
        BlockBreakEvent testEvent = new BlockBreakEvent(block, player);
        module.plugin.getServer().getPluginManager().callEvent(testEvent);
        return !testEvent.isCancelled();
    }

    /**
     * Vérifie si l'outil doit perdre de la durabilité (Unbreaking)
     */
    private boolean shouldDamage(int unbreakingLevel) {
        if (unbreakingLevel <= 0) return true;
        return ThreadLocalRandom.current().nextDouble() > (1.0 / (unbreakingLevel + 1));
    }

    /**
     * Applique la durabilité à l'outil
     */
    private boolean damageItem(@NotNull ItemStack item, @NotNull Player player) {
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return true;
        }

        int newDamage = damageable.getDamage() + 1;

        if (newDamage >= item.getType().getMaxDurability()) {
            // Outil cassé
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

    /**
     * Obtient l'XP d'un minerai
     */
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