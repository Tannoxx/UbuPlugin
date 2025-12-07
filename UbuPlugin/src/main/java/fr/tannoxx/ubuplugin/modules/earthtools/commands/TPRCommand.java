package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import fr.tannoxx.ubuplugin.UbuPlugin;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class TPRCommand implements CommandExecutor {

    private final UbuPlugin plugin;
    private final Random random = new Random();
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private boolean townyEnabled = false;

    public TPRCommand(UbuPlugin plugin) {
        this.plugin = plugin;

        // Vérifier si Towny est présent
        if (plugin.getServer().getPluginManager().getPlugin("Towny") != null) {
            townyEnabled = true;
            plugin.getLogger().info("✓ Towny détecté - Protection TPR activée");
        } else {
            plugin.getLogger().warning("⚠ Towny non détecté - TPR fonctionnera sans vérification de claims");
        }

        // Nettoyer les cooldowns toutes les 5 minutes
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupCooldowns, 6000L, 6000L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("⓪ §cCommande réservée aux joueurs / §rⓧ §cPlayers only.");
            return true;
        }

        World world = player.getWorld();

        if (world.getEnvironment() != World.Environment.NORMAL && world.getEnvironment() != World.Environment.NETHER) {
            player.sendMessage("⓪ §cCette commande ne fonctionne que dans l'Overworld et le Nether / §rⓧ §cThis command only works in Overworld and Nether.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        int cooldown = plugin.getConfig().getInt("tpr.cooldown", 60);

        // Vérification du cooldown (persistant même après déconnexion)
        if (cooldowns.containsKey(uuid)) {
            long timeLeft = cooldowns.get(uuid) - System.currentTimeMillis();
            if (timeLeft > 0) {
                long remaining = timeLeft / 1000;
                player.sendMessage("⓪ §cVeuillez patienter " + remaining + " secondes / §rⓧ §cPlease wait " + remaining + " seconds.");
                return true;
            } else {
                cooldowns.remove(uuid); // Nettoyer les cooldowns expirés
            }
        }

        int maxX = plugin.getConfig().getInt("tpr.max-x", 24597);
        int maxZ = plugin.getConfig().getInt("tpr.max-z", 12298);

        player.sendMessage("⓪ §7Recherche d'un emplacement sûr... / §rⓧ §7Searching for a safe location...");

        Location safeLocation = findSafeLocation(world, maxX, maxZ, player);

        if (safeLocation == null) {
            player.sendMessage("⓪ §cImpossible de trouver un endroit sûr, réessayez / §rⓧ §cCould not find safe location, try again.");
            player.sendMessage("§7Astuce: Les zones protégées sont évitées");
            return true;
        }

        player.teleport(safeLocation);
        player.sendMessage("⓪ §aTéléportation réussie ! / §rⓧ §aTeleport successful!");
        player.sendMessage(String.format("§7X: %.0f, Y: %.0f, Z: %.0f",
                safeLocation.getX(), safeLocation.getY(), safeLocation.getZ()));

        // Appliquer le cooldown (timestamp absolu)
        cooldowns.put(uuid, System.currentTimeMillis() + cooldown * 1000L);

        return true;
    }

    private Location findSafeLocation(World world, int maxX, int maxZ, Player player) {
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;
        int maxAttempts = plugin.getConfig().getInt("tpr.max-attempts", 50);

        for (int i = 0; i < maxAttempts; i++) {
            int x = random.nextInt(maxX * 2) - maxX;
            int z = random.nextInt(maxZ * 2) - maxZ;

            // Vérifier Towny en premier (plus rapide)
            if (townyEnabled && !canTeleportToLocation(player, world, x, z)) {
                continue; // Zone protégée, essayer une autre position
            }

            Location safeLoc;

            if (isNether) {
                safeLoc = findSafeNetherLocation(world, x, z);
            } else {
                safeLoc = findSafeOverworldLocation(world, x, z);
            }

            if (safeLoc != null) {
                return safeLoc;
            }
        }

        return null;
    }

    /**
     * Vérifie si le joueur peut se téléporter à cette location (Towny)
     */
    private boolean canTeleportToLocation(Player player, World world, int x, int z) {
        try {
            TownyAPI townyAPI = TownyAPI.getInstance();

            // Vérifier si la location est dans un TownBlock
            TownBlock townBlock = townyAPI.getTownBlock(new Location(world, x, 64, z));

            if (townBlock == null) {
                // Wilderness - autorisé
                return true;
            }

            // Vérifier si le joueur a la permission de construire/détruire
            // (si oui, il peut probablement se téléporter)
            // Si le joueur ne peut pas build, on refuse la téléportation
            return PlayerCacheUtil.getCachePermission(
                    player,
                    new Location(world, x, 64, z),
                    Material.STONE,
                    TownyPermission.ActionType.BUILD
            );

        } catch (Exception e) {
            // En cas d'erreur, on autorise (mieux que de bloquer complètement).
            plugin.getLogger().warning("Erreur Towny check: " + e.getMessage());
            return true;
        }
    }

    private Location findSafeOverworldLocation(World world, int x, int z) {
        // Charger le chunk si nécessaire
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

    private Location findSafeNetherLocation(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.loadChunk(x >> 4, z >> 4);
        }

        // Éviter les zones trop basses (lave) et trop hautes (plafond)
        for (int y = 32; y < 120; y++) {
            Material ground = world.getBlockAt(x, y - 1, z).getType();
            Material blockAtFeet = world.getBlockAt(x, y, z).getType();
            Material blockAtHead = world.getBlockAt(x, y + 1, z).getType();

            if (ground.isSolid() && !isUnsafeNetherBlock(ground)) {
                if (blockAtFeet.isAir() && blockAtHead.isAir()) {
                    if (!hasLavaNearby(world, x, y, z)) {
                        // Vérification supplémentaire : pas dans une forteresse
                        if (!isDangerousNetherArea(world, x, y, z)) {
                            return new Location(world, x + 0.5, y, z + 0.5);
                        }
                    }
                }
            }
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
     * Vérifie si la zone du Nether est dangereuse
     */
    private boolean isDangerousNetherArea(World world, int x, int y, int z) {
        // Vérifier les spawners et briques du Nether (forteresses)
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
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }
}