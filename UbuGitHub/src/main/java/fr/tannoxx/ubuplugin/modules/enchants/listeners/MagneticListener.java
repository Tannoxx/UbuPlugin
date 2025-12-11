package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener pour l'enchantement Magnetic
 * Thread-safe avec ConcurrentHashMap
 */
public class MagneticListener implements Listener {

    private final EnchantsModule module;
    private final Map<UUID, Long> recentBreakers = new ConcurrentHashMap<>();
    private static final long COLLECTION_WINDOW = 5000; // 5 secondes

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
            Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    );

    private static final Set<Material> CHAIN_REACTION_BLOCKS = EnumSet.of(
            Material.SUGAR_CANE, Material.KELP, Material.KELP_PLANT,
            Material.BAMBOO, Material.CACTUS, Material.CHORUS_PLANT, Material.CHORUS_FLOWER
    );

    public MagneticListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (!module.getConfigManager().getBoolean("enchants.magnetic.toggle-enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !player.isSneaking()) return;

        Enchantment magnetic = module.getMagneticEnchantment();
        if (magnetic == null || !item.containsEnchantment(magnetic)) return;

        if (!event.getAction().isRightClick()) return;

        UUID uuid = player.getUniqueId();
        Boolean currentState = module.getMagneticToggles().getIfPresent(uuid);
        boolean newState = currentState == null || !currentState;

        module.getMagneticToggles().put(uuid, newState);

        if (newState) {
            module.getTranslationManager().send(player, "enchants.magnetic.enabled");
        } else {
            module.getTranslationManager().send(player, "enchants.magnetic.disabled");
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(@NotNull BlockDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        // Vérifier toggle
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

        // Enregistrer le casseur récent
        recentBreakers.put(uuid, System.currentTimeMillis());

        // Récupérer tous les items
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

        // Vérifier toggle
        UUID uuid = killer.getUniqueId();
        Boolean magneticEnabled = module.getMagneticToggles().getIfPresent(uuid);
        if (magneticEnabled != null && !magneticEnabled) {
            return;
        }

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) return;

        Enchantment magnetic = module.getMagneticEnchantment();
        if (magnetic == null || !weapon.containsEnchantment(magnetic)) return;

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();

        for (ItemStack drop : drops) {
            Map<Integer, ItemStack> leftover = killer.getInventory().addItem(drop);
            if (!leftover.isEmpty()) {
                event.getDrops().addAll(leftover.values());
            }
        }
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

            // Vérifier casseur récent
            Long lastBreak = recentBreakers.get(uuid);
            if (lastBreak == null || (currentTime - lastBreak) > COLLECTION_WINDOW) {
                continue;
            }

            // Vérifier toggle
            Boolean magneticEnabled = module.getMagneticToggles().getIfPresent(uuid);
            if (magneticEnabled != null && !magneticEnabled) {
                continue;
            }

            // Vérifier outil
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