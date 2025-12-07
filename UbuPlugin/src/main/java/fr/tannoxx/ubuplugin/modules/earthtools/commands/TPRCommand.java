package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TPRCommand implements CommandExecutor {

    private final EarthToolsModule module;
    private final Random random = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private boolean townyEnabled = false;

    public TPRCommand(EarthToolsModule module) {
        this.module = module;

        // Vérifier si Towny est présent
        if (module.plugin.getServer().getPluginManager().getPlugin("Towny") != null) {
            townyEnabled = true;
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

        int maxX = module.getConfigManager().getInt("earthtools.tpr.max-x", 24597);
        int maxZ = module.getConfigManager().getInt("earthtools.tpr.max-z", 12298);

        module.getTranslationManager().send(sender, "earthtools.tpr.searching");

        Location safeLocation = findSafeLocation(world, maxX, maxZ, player);

        if (safeLocation == null) {
            module.getTranslationManager().send(sender, "earthtools.tpr.failed");
            return true;
        }

        player.teleport(safeLocation);
        module.getTranslationManager().send(sender, "earthtools.tpr.success");
        sender.sendMessage(module.getTranslationManager().get(sender, "earthtools.tpr.coords",
                String.format("%.0f", safeLocation.getX()),
                String.format("%.0f", safeLocation.getY()),
                String.format("%.0f", safeLocation.getZ())));

        cooldowns.put(uuid, System.currentTimeMillis() + cooldown * 1000L);

        return true;
    }

    private Location findSafeLocation(World world, int maxX, int maxZ, Player player) {
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;
        int maxAttempts = module.getConfigManager().getInt("earthtools.tpr.max-attempts", 50);

        for (int i = 0; i < maxAttempts; i++) {
            int x = random.nextInt(maxX * 2) - maxX;
            int z = random.nextInt(maxZ * 2) - maxZ;

            // Vérifier Towny si activé
            if (townyEnabled && !canTeleportToLocation(player, world, x, z)) {
                continue;
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

    private boolean canTeleportToLocation(Player player, World world, int x, int z) {
        // VOTRE CODE TOWNY EXISTANT ICI
        return true; // Placeholder
    }

    private Location findSafeOverworldLocation(World world, int x, int z) {
        // VOTRE CODE EXISTANT ICI
        return null; // Placeholder
    }

    private Location findSafeNetherLocation(World world, int x, int z) {
        // VOTRE CODE EXISTANT ICI
        return null; // Placeholder
    }
}