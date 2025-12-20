package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyWorld;
import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Commande /tpr avec support Towny complet et vérifications de sécurité avancées
 * <p>
 * CORRECTIONS v2.0.2:
 * - Fix max-attempts = 0 (boucle infinie)
 */
public class TPRCommand implements CommandExecutor {

    private final EarthToolsModule module;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final boolean townyEnabled;

    public TPRCommand(EarthToolsModule module) {
        this.module = module;

        townyEnabled = module.plugin.getServer().getPluginManager().getPlugin("Towny") != null;

        if (townyEnabled) {
            module.info("Towny détecté - Protection TPR activée");
        }

        module.plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                module.plugin, this::cleanupCooldowns, 6000L, 6000L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            module.getTranslationManager().send(sender, "errors.player-only");
            return true;
        }

        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL &&
                world.getEnvironment() != World.Environment.NETHER) {
            module.getTranslationManager().send(sender, "earthtools.tpr.world-not-allowed");
            return true;
        }

        UUID uuid = player.getUniqueId();
        int cooldown = module.getConfigManager().getInt("earthtools.tpr.cooldown", 60);

        synchronized (cooldowns) {
            if (cooldowns.containsKey(uuid)) {
                long timeLeft = cooldowns.get(uuid) - System.currentTimeMillis();
                if (timeLeft > 0) {
                    long remaining = timeLeft / 1000 + 1;
                    module.getTranslationManager().send(sender, "earthtools.tpr.cooldown", remaining);
                    return true;
                } else {
                    cooldowns.remove(uuid);
                }
            }
        }

        int maxX = module.getConfigManager().getInt("earthtools.tpr.max-x", 24597);
        int maxZ = module.getConfigManager().getInt("earthtools.tpr.max-z", 12298);

        module.getTranslationManager().send(sender, "earthtools.tpr.searching");

        Location safeLocation = findSafeLocation(world, maxX, maxZ);

        if (safeLocation == null) {
            module.getTranslationManager().send(sender, "earthtools.tpr.failed");
            return true;
        }

        player.teleport(safeLocation);
        module.getTranslationManager().send(sender, "earthtools.tpr.success");

        module.getTranslationManager().send(sender, "earthtools.tpr.coords",
                String.format(Locale.US, "%.0f", safeLocation.getX()),
                String.format(Locale.US, "%.0f", safeLocation.getY()),
                String.format(Locale.US, "%.0f", safeLocation.getZ()));

        synchronized (cooldowns) {
            cooldowns.put(uuid, System.currentTimeMillis() + cooldown * 1000L);
        }

        return true;
    }

    @Nullable
    private Location findSafeLocation(@NotNull World world, int maxX, int maxZ) {
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;

        // ✅ FIX: Garantir minimum 1 tentative (évite boucle infinie)
        int maxAttempts = Math.max(1, module.getConfigManager().getInt("earthtools.tpr.max-attempts", 50));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < maxAttempts; i++) {
            int x = random.nextInt(-maxX, maxX + 1);
            int z = random.nextInt(-maxZ, maxZ + 1);

            if (townyEnabled && module.getConfigManager().getBoolean("earthtools.tpr.check-towny", true)) {
                if (!canTeleportToLocation(world, x, z)) {
                    continue;
                }
            }

            Location safeLoc = isNether ?
                    findSafeNetherLocation(world, x, z) :
                    findSafeOverworldLocation(world, x, z);

            if (safeLoc != null) {
                return safeLoc;
            }
        }

        return null;
    }

    private boolean canTeleportToLocation(@NotNull World world, int x, int z) {
        if (!townyEnabled) return true;

        try {
            TownyWorld townyWorld = TownyAPI.getInstance().getTownyWorld(world);
            if (townyWorld == null) return true;

            TownBlock townBlock = townyWorld.getTownBlock(x >> 4, z >> 4);

            if (townBlock == null) return true;

            return !townBlock.hasTown();

        } catch (Exception e) {
            module.debug("Erreur vérification Towny: {}", e.getMessage());
            return true;
        }
    }

    @Nullable
    private Location findSafeOverworldLocation(@NotNull World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.loadChunk(x >> 4, z >> 4);
        }

        int highestY = world.getHighestBlockYAt(x, z);

        if (highestY < world.getMinHeight() + 1 || highestY >= world.getMaxHeight() - 2) {
            return null;
        }

        int groundY = highestY;
        while (groundY > world.getMinHeight() && world.getBlockAt(x, groundY, z).getType().isAir()) {
            groundY--;
        }

        int spawnY = groundY + 1;

        Material ground = world.getBlockAt(x, groundY, z).getType();
        Material blockAtFeet = world.getBlockAt(x, spawnY, z).getType();
        Material blockAtHead = world.getBlockAt(x, spawnY + 1, z).getType();

        if (isUnsafeGroundBlock(ground)) {
            return null;
        }

        if (!blockAtFeet.isAir() || !blockAtHead.isAir()) {
            return null;
        }

        if (hasLiquidNearby(world, x, spawnY, z)) {
            return null;
        }

        if (isDangerousArea(world, x, spawnY, z)) {
            return null;
        }

        return new Location(world, x + 0.5, spawnY, z + 0.5);
    }

    @Nullable
    private Location findSafeNetherLocation(@NotNull World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.loadChunk(x >> 4, z >> 4);
        }

        for (int y = 32; y < 120; y++) {
            Material ground = world.getBlockAt(x, y - 1, z).getType();
            Material blockAtFeet = world.getBlockAt(x, y, z).getType();
            Material blockAtHead = world.getBlockAt(x, y + 1, z).getType();
            Material blockAbove = world.getBlockAt(x, y + 2, z).getType();

            if (!ground.isSolid() || isUnsafeNetherBlock(ground)) {
                continue;
            }

            if (!blockAtFeet.isAir() || !blockAtHead.isAir()) {
                continue;
            }

            if (blockAtFeet == Material.LAVA || blockAtHead == Material.LAVA || blockAbove == Material.LAVA) {
                continue;
            }

            if (hasLavaNearby(world, x, y, z)) {
                continue;
            }

            if (isDangerousNetherArea(world, x, y, z)) {
                continue;
            }

            return new Location(world, x + 0.5, y, z + 0.5);
        }

        return null;
    }

    private boolean isUnsafeGroundBlock(Material material) {
        return material == Material.WATER ||
                material == Material.LAVA ||
                material == Material.AIR ||
                material == Material.BEDROCK ||
                material.name().contains("LEAVES") ||
                material.name().contains("ICE") ||
                material.name().contains("SNOW") ||
                material == Material.CACTUS ||
                material == Material.MAGMA_BLOCK ||
                material == Material.SWEET_BERRY_BUSH ||
                material == Material.FIRE ||
                material == Material.SOUL_FIRE ||
                material == Material.POWDER_SNOW ||
                material == Material.SLIME_BLOCK ||
                material == Material.HONEY_BLOCK;
    }

    private boolean isUnsafeNetherBlock(Material material) {
        return material == Material.LAVA ||
                material == Material.BEDROCK ||
                material == Material.MAGMA_BLOCK ||
                material == Material.FIRE ||
                material == Material.SOUL_FIRE ||
                material == Material.SOUL_SAND ||
                material == Material.NETHER_WART_BLOCK;
    }

    private boolean hasLavaNearby(World world, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Material block = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (block == Material.LAVA) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLiquidNearby(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material block = world.getBlockAt(x + dx, y, z + dz).getType();
                if (block == Material.LAVA || block == Material.WATER) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDangerousArea(World world, int x, int y, int z) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    Material block = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (block == Material.SPAWNER || block == Material.END_PORTAL_FRAME) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isDangerousNetherArea(World world, int x, int y, int z) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    Material block = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (block == Material.SPAWNER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        synchronized (cooldowns) {
            cooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        }
    }
}