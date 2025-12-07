package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyWorld;
import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Commande /tpr avec support Towny complet
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
        sender.sendMessage(module.getTranslationManager().get(sender, "earthtools.tpr.coords",
                String.format(Locale.US, "%.0f", safeLocation.getX()),
                String.format(Locale.US, "%.0f", safeLocation.getY()),
                String.format(Locale.US, "%.0f", safeLocation.getZ())));

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

            // Zone protégée, refuser
        } catch (Exception e) {
            module.debug("Erreur vérification Towny: {}", e.getMessage());
            return true; // En cas d'erreur, autoriser
        }
    }

    @Nullable
    private Location findSafeOverworldLocation(@NotNull World world, int x, int z) {
        // Obtenir le bloc le plus haut
        int y = world.getHighestBlockYAt(x, z);

        // Vérifier zone dangereuse (laves, eau profonde)
        Block ground = world.getBlockAt(x, y, z);
        if (ground.getType() == Material.LAVA || ground.getType() == Material.WATER) {
            return null;
        }

        // Vérifier espace libre
        Block above1 = world.getBlockAt(x, y + 1, z);
        Block above2 = world.getBlockAt(x, y + 2, z);

        if (!above1.getType().isAir() || !above2.getType().isAir()) {
            return null;
        }

        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    @Nullable
    private Location findSafeNetherLocation(@NotNull World world, int x, int z) {
        // Chercher une plateforme sûre dans le Nether
        for (int y = 32; y < 120; y++) {
            Block ground = world.getBlockAt(x, y, z);
            Block above1 = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);
            Block above3 = world.getBlockAt(x, y + 3, z);

            // Sol solide
            if (!ground.getType().isSolid()) continue;

            // Pas de lave
            if (ground.getType() == Material.LAVA) continue;

            // Espace libre au-dessus
            if (!above1.getType().isAir() || !above2.getType().isAir()) continue;

            // Vérifier pas de lave au-dessus
            if (above1.getType() == Material.LAVA || above2.getType() == Material.LAVA ||
                    above3.getType() == Material.LAVA) {
                continue;
            }

            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }

        return null;
    }
}