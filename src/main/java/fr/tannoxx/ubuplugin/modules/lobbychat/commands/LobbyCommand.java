package fr.tannoxx.ubuplugin.modules.lobbychat.commands;

import fr.tannoxx.ubuplugin.modules.lobbychat.LobbyChatModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public record LobbyCommand(LobbyChatModule module) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            module.getTranslationManager().send(sender, "errors.player-only");
            return true;
        }

        // Obtenir le monde "world" (monde principal)
        World world = Bukkit.getWorld("world");

        if (world == null) {
            module.getTranslationManager().send(sender, "lobbychat.world-not-found");
            return true;
        }

        // Téléporter au spawn exact du monde
        Location spawnLocation = world.getSpawnLocation();
        Location exactSpawn = new Location(
                world,
                spawnLocation.getX(),
                spawnLocation.getY(),
                spawnLocation.getZ(),
                spawnLocation.getYaw(),
                spawnLocation.getPitch()
        );

        player.teleport(exactSpawn);
        module.getTranslationManager().send(sender, "lobbychat.teleport");

        return true;
    }
}