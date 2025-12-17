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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Commande /tpr avec support Towny complet et vérifications de sécurité avancées
 */
public class TPRCommand implements CommandExecutor {

    private final EarthToolsModule module;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final boolean townyEnabled;

    public TPRCommand(EarthToolsModule module) {
        this.module = module;

        // Vérifier si Towny est présent
        townyEnabled = module.plugin.getServer().getPluginManager().getPlugin("Towny") != null;

        if (townyEnabled) {
            module.info("Towny détecté - Protection TPR activée");
        }

        // Nettoyer les cooldowns toutes les 5 minutes
        module.plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                module.plugin, this::cleanupCooldowns, 6000L, 6000L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
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

        // Vérifier cooldown
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
        int maxAttempts = module.getConfigManager().getInt("earthtools.tpr.max-attempts", 50);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < maxAttempts; i++) {
            int x = random.nextInt(-maxX, maxX + 1);
            int z = random.nextInt(-maxZ, maxZ + 1);

            // Vérifier Towny si activé
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

            // Si aucune ville, OK
            if (townBlock == null) return true;

            // Si ville mais non réclamé, OK
            return !townBlock.hasTown();

        } catch (Exception e) {
            module.debug("Erreur vérification Towny: {}", e.getMessage());
            return true; // En cas d'erreur, autoriser
        }
    }

    @Nullable
    private Location findSafeOverworldLocation(@NotNull World world, int x, int z) {
        // Charger le chunk si nécessaire
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.loadChunk(x >> 4, z >> 4);
        }

        // Obtenir le bloc le plus haut
        int highestY = world.getHighestBlockYAt(x, z);

        if (highestY < world.getMinHeight() + 1 || highestY >= world.getMaxHeight() - 2) {
            return null;
        }

        // Trouver le vrai sol
        int groundY = highestY;
        while (groundY > world.getMinHeight() && world.getBlockAt(x, groundY, z).getType().isAir()) {
            groundY--;
        }

        int spawnY = groundY + 1;

        Material ground = world.getBlockAt(x, groundY, z).getType();
        Material blockAtFeet = world.getBlockAt(x, spawnY, z).getType();
        Material blockAtHead = world.getBlockAt(x, spawnY + 1, z).getType();

        // Vérifier que le sol n'est pas dangereux
        if (isUnsafeGroundBlock(ground)) {
            return null;
        }

        // Vérifier espace libre
        if (!blockAtFeet.isAir() || !blockAtHead.isAir()) {
            return null;
        }

        // Vérifications supplémentaires
        if (hasLiquidNearby(world, x, spawnY, z)) {
            return null;
        }

        // Vérifier qu'on n'est pas dans une structure dangereuse
        if (isDangerousArea(world, x, spawnY, z)) {
            return null;
        }

        return new Location(world, x + 0.5, spawnY, z + 0.5);
    }

    @Nullable
    private Location findSafeNetherLocation(@NotNull World world, int x, int z) {
        // Charger le chunk si nécessaire
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.loadChunk(x >> 4, z >> 4);
        }

        // Chercher une plateforme sûre dans le Nether (éviter zones trop basses et trop hautes)
        for (int y = 32; y < 120; y++) {
            Material ground = world.getBlockAt(x, y - 1, z).getType();
            Material blockAtFeet = world.getBlockAt(x, y, z).getType();
            Material blockAtHead = world.getBlockAt(x, y + 1, z).getType();
            Material blockAbove = world.getBlockAt(x, y + 2, z).getType();

            // Sol solide et non dangereux
            if (!ground.isSolid() || isUnsafeNetherBlock(ground)) {
                continue;
            }

            // Espace libre au-dessus
            if (!blockAtFeet.isAir() || !blockAtHead.isAir()) {
                continue;
            }

            // Vérifier pas de lave au-dessus
            if (blockAtFeet == Material.LAVA || blockAtHead == Material.LAVA || blockAbove == Material.LAVA) {
                continue;
            }

            // Vérifier pas de lave à proximité
            if (hasLavaNearby(world, x, y, z)) {
                continue;
            }

            // Vérifier pas dans une forteresse
            if (isDangerousNetherArea(world, x, y, z)) {
                continue;
            }

            return new Location(world, x + 0.5, y, z + 0.5);
        }

        return null;
    }

    /**
     * Vérifie si le bloc au sol est dangereux (Overworld)
     */
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

    /**
     * Vérifie si le bloc est dangereux dans le Nether
     */
    private boolean isUnsafeNetherBlock(Material material) {
        return material == Material.LAVA ||
                material == Material.BEDROCK ||
                material == Material.MAGMA_BLOCK ||
                material == Material.FIRE ||
                material == Material.SOUL_FIRE ||
                material == Material.SOUL_SAND ||
                material == Material.NETHER_WART_BLOCK;
    }

    /**
     * Vérifie s'il y a de la lave à proximité (Nether)
     */
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

    /**
     * Vérifie s'il y a des liquides dangereux à proximité (Overworld)
     */
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

    /**
     * Vérifie si la zone est dangereuse (spawners, structures)
     */
    private boolean isDangerousArea(World world, int x, int y, int z) {
        // Vérifier dans un rayon de 5 blocs
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

    /**
     * Vérifie si la zone du Nether est dangereuse (forteresses)
     */
    private boolean isDangerousNetherArea(World world, int x, int y, int z) {
        // Vérifier les spawners (forteresses du Nether)
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    Material block = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (block == Material.SPAWNER) {
                        return true; // Forteresse du Nether
                    }
                }
            }
        }
        return false;
    }

    /**
     * Nettoyer les cooldowns expirés périodiquement
     * Appelé automatiquement toutes les 5 minutes par le scheduler
     */
    private void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        synchronized (cooldowns) {
            cooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        }
    }
}