package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Listener pour l'enchantement Magnetic
 * Thread-safe avec Caffeine Cache
 * <p>
 * ✅ FIX v2.0.3: Utilisation de Caffeine pour auto-expiration (évite memory leak)
 */
public class MagneticListener implements Listener {

    private final EnchantsModule module;

    // ✅ FIX: Caffeine avec expiration automatique (plus besoin de cleanup manuel)
    private final Cache<UUID, Long> recentBreakers = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(500)
            .build();

    private static final long COLLECTION_WINDOW = 5000;
    private static final double TRIDENT_SEARCH_RADIUS = 10.0;

    private static final Set<Material> MAGNETIC_TOOLS = EnumSet.of(
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
            Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
            Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE,
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.SHEARS, Material.TRIDENT, Material.MACE
    );

    private static final Set<Material> CHAIN_REACTION_BLOCKS = EnumSet.of(
            Material.SUGAR_CANE, Material.KELP, Material.KELP_PLANT,
            Material.BAMBOO, Material.CACTUS, Material.CHORUS_PLANT, Material.CHORUS_FLOWER
    );

    public MagneticListener(@NotNull EnchantsModule module) {
        this.module = module;
        // ✅ Plus besoin de cleanup task (Caffeine le gère automatiquement)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(@NotNull BlockDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Boolean magneticEnabled = module.getMagneticToggles().getIfPresent(uuid);
        if (magneticEnabled != null && !magneticEnabled) {
            return;
        }

        Enchantment magnetic = module.getMagneticEnchantment();
        if (magnetic == null) return;

        boolean hasMagneticTool = MAGNETIC_TOOLS.contains(tool.getType()) &&
                tool.containsEnchantment(magnetic);

        if (!hasMagneticTool) return;

        recentBreakers.put(uuid, System.currentTimeMillis());

        List<Item> items = new ArrayList<>(event.getItems());
        event.getItems().clear();

        for (Item item : items) {
            ItemStack stack = item.getItemStack();
            addToInventoryOrDrop(player, stack, event.getBlockState().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        if (killer.getGameMode() != GameMode.SURVIVAL &&
                killer.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        UUID uuid = killer.getUniqueId();

        Boolean magneticEnabled = module.getMagneticToggles().getIfPresent(uuid);
        if (magneticEnabled != null && !magneticEnabled) {
            return;
        }

        Enchantment magnetic = module.getMagneticEnchantment();
        if (magnetic == null) return;

        boolean hasMagnetic = false;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (!weapon.getType().isAir() && weapon.containsEnchantment(magnetic)) {
            hasMagnetic = true;
            module.debug("Magnetic activé via arme en main: {}", weapon.getType());
        }

        if (!hasMagnetic) {
            for (ItemStack item : killer.getInventory().getContents()) {
                if (item != null && item.getType() == Material.TRIDENT &&
                        item.containsEnchantment(magnetic)) {
                    hasMagnetic = true;
                    module.debug("Magnetic activé via trident dans l'inventaire");
                    break;
                }
            }
        }

        if (!hasMagnetic) {
            Trident trident = findNearbyMagneticTrident(killer, event.getEntity().getLocation());
            if (trident != null) {
                hasMagnetic = true;
                module.debug("Magnetic activé via trident lancé (entité)");
            }
        }

        if (!hasMagnetic) return;

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();

        for (ItemStack drop : drops) {
            Map<Integer, ItemStack> leftover = killer.getInventory().addItem(drop);
            if (!leftover.isEmpty()) {
                event.getDrops().addAll(leftover.values());
            }
        }

        module.debug("Magnetic: {} drops récupérés pour {}", drops.size(), killer.getName());
    }

    @Nullable
    private Trident findNearbyMagneticTrident(@NotNull Player player, @NotNull org.bukkit.Location location) {
        Enchantment magnetic = module.getMagneticEnchantment();
        if (magnetic == null) return null;

        Collection<Entity> nearbyEntities = location.getWorld()
                .getNearbyEntities(location, TRIDENT_SEARCH_RADIUS, TRIDENT_SEARCH_RADIUS, TRIDENT_SEARCH_RADIUS);

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof Trident trident)) continue;

            if (trident.getShooter() == null || !trident.getShooter().equals(player)) continue;

            ItemStack tridentItem = trident.getItemStack();

            if (tridentItem.containsEnchantment(magnetic)) {
                return trident;
            }
        }

        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(@NotNull ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();

        if (!CHAIN_REACTION_BLOCKS.contains(stack.getType())) {
            return;
        }

        Player nearestPlayer = findNearestMagneticPlayer(item);

        if (nearestPlayer != null) {
            Map<Integer, ItemStack> leftover = nearestPlayer.getInventory().addItem(stack);
            if (leftover.isEmpty()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * ✅ OPTIMISATION: Utilisation de getIfPresent() sur Caffeine (thread-safe)
     */
    @Nullable
    private Player findNearestMagneticPlayer(@NotNull Item item) {
        Player nearest = null;
        double minDistance = 30.0;
        long currentTime = System.currentTimeMillis();

        Enchantment magnetic = module.getMagneticEnchantment();
        if (magnetic == null) return null;

        Collection<Player> nearbyPlayers = item.getWorld().getNearbyPlayers(item.getLocation(), 30.0);

        for (Player player : nearbyPlayers) {
            UUID uuid = player.getUniqueId();

            // ✅ FIX: Utilisation de Caffeine getIfPresent() (thread-safe)
            Long lastBreak = recentBreakers.getIfPresent(uuid);
            if (lastBreak == null || (currentTime - lastBreak) > COLLECTION_WINDOW) {
                continue;
            }

            Boolean magneticEnabled = module.getMagneticToggles().getIfPresent(uuid);
            if (magneticEnabled != null && !magneticEnabled) {
                continue;
            }

            ItemStack tool = player.getInventory().getItemInMainHand();
            if (!MAGNETIC_TOOLS.contains(tool.getType()) || !tool.containsEnchantment(magnetic)) {
                continue;
            }

            double distance = player.getLocation().distance(item.getLocation());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    private void addToInventoryOrDrop(@NotNull Player player, @NotNull ItemStack stack,
                                      @NotNull org.bukkit.Location location) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            for (ItemStack leftoverItem : leftover.values()) {
                player.getWorld().dropItemNaturally(
                        location.add(0.5, 0.5, 0.5),
                        leftoverItem
                );
            }
        }
    }
}