package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener pour Beaconator
 * Thread-safe avec cache concurrent
 * <p>
 * OPTIMISATIONS v2.0.3:
 * - Indexation par chunk pour réduire les calculs
 * - Cache spatial pour éviter de recalculer tous les beacons
 * - Calcul dynamique du rayon de chunks à vérifier
 * - Propagation des effets aux animaux du joueur
 */
public class BeaconatorListener implements Runnable {

    private final EnchantsModule module;

    // ✅ OPTIMISATION: Cache par chunk
    private final Map<ChunkPos, Set<BeaconData>> beaconsByChunk = new ConcurrentHashMap<>();

    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 5000;

    public BeaconatorListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @Override
    public void run() {
        try {
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
        } catch (Exception e) {
            module.error("Erreur dans BeaconatorListener", e);
        }
    }

    /**
     * ✅ OPTIMISATION: Mise à jour du cache avec indexation par chunk
     */
    private void updateBeaconCache() {
        beaconsByChunk.clear();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState blockState : chunk.getTileEntities()) {
                    if (blockState instanceof Beacon beacon) {
                        try {
                            PotionEffect primary = beacon.getPrimaryEffect();
                            PotionEffect secondary = beacon.getSecondaryEffect();

                            if (primary != null || secondary != null) {
                                int tier = getBeaconTier(beacon);
                                int primaryLevel = primary != null ? primary.getAmplifier() + 1 : 0;
                                int secondaryLevel = secondary != null ? secondary.getAmplifier() + 1 : 0;

                                Location loc = beacon.getLocation();
                                ChunkPos chunkPos = new ChunkPos(loc.getChunk().getX(), loc.getChunk().getZ(), world.getName());

                                BeaconData data = new BeaconData(
                                        loc,
                                        primary != null ? primary.getType() : null,
                                        secondary != null ? secondary.getType() : null,
                                        tier, primaryLevel, secondaryLevel
                                );

                                beaconsByChunk.computeIfAbsent(chunkPos, k -> new HashSet<>()).add(data);
                            }
                        } catch (Exception e) {
                            module.debug("Erreur beacon à {}: {}",
                                    beacon.getLocation(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * ✅ CORRECTION: Calcul dynamique du rayon de chunks en fonction de la portée maximale
     */
    private void applyBeaconEffects(@NotNull Player player, int rangeBonus, int beaconatorLevel) {
        Location playerLoc = player.getLocation();
        Chunk playerChunk = playerLoc.getChunk();
        String worldName = playerLoc.getWorld().getName();

        // ✅ Calculer la portée maximale possible (beacon tier 4 + bonus)
        int maxRange = 50 + rangeBonus; // 50 = portée max d'un beacon tier 4

        // ✅ Calculer le rayon de chunks à vérifier (1 chunk = 16 blocs)
        // On ajoute 1 pour être sûr de couvrir toute la zone
        int chunkRadius = (maxRange / 16) + 1;

        // Liste pour stocker les effets à appliquer
        List<EffectToApply> effectsToApply = new ArrayList<>();

        // ✅ Vérifier tous les chunks dans le rayon calculé
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos chunkPos = new ChunkPos(
                        playerChunk.getX() + dx,
                        playerChunk.getZ() + dz,
                        worldName
                );

                Set<BeaconData> beacons = beaconsByChunk.get(chunkPos);
                if (beacons == null) continue;

                for (BeaconData data : beacons) {
                    int baseRange = switch (data.tier) {
                        case 1 -> 20;
                        case 2 -> 30;
                        case 3 -> 40;
                        case 4 -> 50;
                        default -> 0;
                    };

                    int totalRange = baseRange + rangeBonus;

                    double horizontalDistance = Math.sqrt(
                            Math.pow(playerLoc.getX() - data.location.getX(), 2) +
                                    Math.pow(playerLoc.getZ() - data.location.getZ(), 2)
                    );

                    if (horizontalDistance <= totalRange) {
                        // Appliquer au joueur
                        applyEffect(player, data.primaryEffect, data.primaryLevel, beaconatorLevel);
                        applyEffect(player, data.secondaryEffect, data.secondaryLevel, beaconatorLevel);

                        // Stocker les effets pour les animaux
                        if (data.primaryEffect != null) {
                            effectsToApply.add(new EffectToApply(data.primaryEffect, data.primaryLevel));
                        }
                        if (data.secondaryEffect != null) {
                            effectsToApply.add(new EffectToApply(data.secondaryEffect, data.secondaryLevel));
                        }
                    }
                }
            }
        }

        // ✅ NOUVEAU: Appliquer les effets aux animaux du joueur
        if (!effectsToApply.isEmpty()) {
            applyEffectsToPets(player, effectsToApply, beaconatorLevel, maxRange);
        }
    }

    /**
     * ✅ NOUVEAU: Applique les effets aux animaux apprivoisés du joueur
     */
    private void applyEffectsToPets(@NotNull Player player, @NotNull List<EffectToApply> effects,
                                    int beaconatorLevel, int searchRange) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        // Chercher les entités dans un rayon raisonnable autour du joueur
        Collection<Entity> nearbyEntities = world.getNearbyEntities(playerLoc, searchRange, searchRange, searchRange);

        for (Entity entity : nearbyEntities) {
            if (isPlayerPet(entity, player)) {
                LivingEntity pet = (LivingEntity) entity;

                // Appliquer tous les effets collectés
                for (EffectToApply effect : effects) {
                    applyEffect(pet, effect.type, effect.level, beaconatorLevel);
                }
            }
        }
    }

    /**
     * ✅ NOUVEAU: Vérifie si l'entité est un animal apprivoisé appartenant au joueur
     */
    private boolean isPlayerPet(@NotNull Entity entity, @NotNull Player player) {
        // Animaux apprivoisables (Wolf, Cat, Parrot)
        if (entity instanceof Tameable tameable) {
            return tameable.isTamed() &&
                    tameable.getOwner() != null &&
                    tameable.getOwner().getUniqueId().equals(player.getUniqueId());
        }

        // Chevaux et variantes (Horse, Donkey, Mule, SkeletonHorse, ZombieHorse, Llama, TraderLlama)
        if (entity instanceof AbstractHorse horse) {
            return horse.isTamed() &&
                    horse.getOwner() != null &&
                    horse.getOwner().getUniqueId().equals(player.getUniqueId());
        }

        return false;
    }

    private void applyEffect(@NotNull LivingEntity livingEntity, @Nullable PotionEffectType effectType,
                             int baseLevel, int beaconatorLevel) {
        if (effectType == null) return;

        boolean level4Boost = module.getConfigManager()
                .getBoolean("enchants.beaconator.level-4-boost", true);
        int finalLevel = (beaconatorLevel == 4 && level4Boost) ? baseLevel + 1 : baseLevel;

        livingEntity.addPotionEffect(new PotionEffect(
                effectType, 220, finalLevel - 1, true, true, true
        ));
    }

    private int getBeaconTier(@NotNull Beacon beacon) {
        Location loc = beacon.getLocation();
        int tier = 0;

        for (int level = 1; level <= 4; level++) {
            int y = loc.getBlockY() - level;
            boolean fullLayer = true;

            for (int x = -level; x <= level; x++) {
                for (int z = -level; z <= level; z++) {
                    World world = loc.getWorld();
                    if (world == null) return tier;

                    Material block = world.getBlockAt(
                            loc.getBlockX() + x, y, loc.getBlockZ() + z
                    ).getType();

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
        beaconsByChunk.clear();
    }

    /**
     * ✅ OPTIMISATION: Record pour identifier les chunks
     */
    private record ChunkPos(int x, int z, String world) {}

    /**
     * ✅ OPTIMISATION: Stocke la location pour éviter les lookups
     */
    private record BeaconData(
            Location location,
            PotionEffectType primaryEffect,
            PotionEffectType secondaryEffect,
            int tier,
            int primaryLevel,
            int secondaryLevel
    ) {}

    /**
     * ✅ NOUVEAU: Record pour stocker les effets à appliquer
     */
    private record EffectToApply(PotionEffectType type, int level) {}
}