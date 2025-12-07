package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BeaconatorListener implements Runnable {

    private final EnchantsModule module;
    private final Map<Location, BeaconData> beaconCache = new HashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 5000;

    public BeaconatorListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheUpdate > CACHE_DURATION) {
            updateBeaconCache();
            lastCacheUpdate = currentTime;
        }

        Enchantment beaconator = module.getBeaconatorEnchantment();
        if (beaconator == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet == null || helmet.getType() == Material.AIR) continue;
            if (!helmet.containsEnchantment(beaconator)) continue;

            int level = helmet.getEnchantmentLevel(beaconator);
            int rangeBonus = module.getConfigManager()
                    .getInt("enchants.beaconator.range-bonus.level-" + level, 20 * level);

            applyBeaconEffects(player, rangeBonus, level);
        }
    }

    private void updateBeaconCache() {
        beaconCache.clear();

        Bukkit.getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState blockState : chunk.getTileEntities()) {
                    if (blockState instanceof Beacon beacon) {
                        PotionEffect primary = beacon.getPrimaryEffect();
                        PotionEffect secondary = beacon.getSecondaryEffect();

                        if (primary != null || secondary != null) {
                            int tier = getBeaconTier(beacon);
                            int primaryLevel = primary != null ? primary.getAmplifier() + 1 : 0;
                            int secondaryLevel = secondary != null ? secondary.getAmplifier() + 1 : 0;

                            beaconCache.put(beacon.getLocation(), new BeaconData(
                                    primary != null ? primary.getType() : null,
                                    secondary != null ? secondary.getType() : null,
                                    tier, primaryLevel, secondaryLevel
                            ));
                        }
                    }
                }
            }
        });
    }

    private void applyBeaconEffects(@NotNull Player player, int rangeBonus, int beaconatorLevel) {
        Location playerLoc = player.getLocation();

        for (Map.Entry<Location, BeaconData> entry : beaconCache.entrySet()) {
            Location beaconLoc = entry.getKey();
            BeaconData data = entry.getValue();

            if (!beaconLoc.getWorld().equals(playerLoc.getWorld())) continue;

            int baseRange = switch (data.tier) {
                case 1 -> 20;
                case 2 -> 30;
                case 3 -> 40;
                case 4 -> 50;
                default -> 0;
            };

            int totalRange = baseRange + rangeBonus;

            double horizontalDistance = Math.sqrt(
                    Math.pow(playerLoc.getX() - beaconLoc.getX(), 2) +
                            Math.pow(playerLoc.getZ() - beaconLoc.getZ(), 2)
            );

            if (horizontalDistance <= totalRange) {
                if (data.primaryEffect != null) {
                    int baseLevel = data.primaryLevel;
                    boolean level4Boost = module.getConfigManager()
                            .getBoolean("enchants.beaconator.level-4-boost", true);
                    int finalLevel = (beaconatorLevel == 4 && level4Boost) ? baseLevel + 1 : baseLevel;

                    player.addPotionEffect(new PotionEffect(
                            data.primaryEffect, 220, finalLevel - 1, true, true, true
                    ));
                }

                if (data.secondaryEffect != null) {
                    int baseLevel = data.secondaryLevel;
                    boolean level4Boost = module.getConfigManager()
                            .getBoolean("enchants.beaconator.level-4-boost", true);
                    int finalLevel = (beaconatorLevel == 4 && level4Boost) ? baseLevel + 1 : baseLevel;

                    player.addPotionEffect(new PotionEffect(
                            data.secondaryEffect, 220, finalLevel - 1, true, true, true
                    ));
                }
            }
        }
    }

    private int getBeaconTier(@NotNull Beacon beacon) {
        Location loc = beacon.getLocation();
        int tier = 0;

        for (int level = 1; level <= 4; level++) {
            int y = loc.getBlockY() - level;
            boolean fullLayer = true;

            for (int x = -level; x <= level; x++) {
                for (int z = -level; z <= level; z++) {
                    Material block = loc.getWorld()
                            .getBlockAt(loc.getBlockX() + x, y, loc.getBlockZ() + z)
                            .getType();
                    if (!isValidBeaconBase(block)) {
                        fullLayer = false;
                        break;
                    }
                }
                if (!fullLayer) break;
            }

            if (fullLayer) {
                tier = level;
            } else {
                break;
            }
        }

        return tier;
    }

    private boolean isValidBeaconBase(@NotNull Material material) {
        return material == Material.IRON_BLOCK ||
                material == Material.GOLD_BLOCK ||
                material == Material.DIAMOND_BLOCK ||
                material == Material.EMERALD_BLOCK ||
                material == Material.NETHERITE_BLOCK;
    }

    public void cleanup() {
        beaconCache.clear();
    }

    private static class BeaconData {
        final PotionEffectType primaryEffect;
        final PotionEffectType secondaryEffect;
        final int tier;
        final int primaryLevel;
        final int secondaryLevel;

        BeaconData(PotionEffectType primary, PotionEffectType secondary,
                   int tier, int primaryLevel, int secondaryLevel) {
            this.primaryEffect = primary;
            this.secondaryEffect = secondary;
            this.tier = tier;
            this.primaryLevel = primaryLevel;
            this.secondaryLevel = secondaryLevel;
        }
    }
}