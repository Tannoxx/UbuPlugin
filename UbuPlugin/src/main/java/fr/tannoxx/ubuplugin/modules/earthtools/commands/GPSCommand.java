package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class GPSCommand implements CommandExecutor {

    private static final double LATITUDE_TO_Z = -136.653;
    private static final double LONGITUDE_TO_X = 136.653;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /gps tominecraft <latitude> <longitude>");
            sender.sendMessage("/gps togps <x> <z>");
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        try {
            if (mode.equals("tominecraft")) {
                double latitude = Double.parseDouble(args[1]);
                double longitude = Double.parseDouble(args[2]);
                if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                    sender.sendMessage("⓪ §cCoordonnées GPS invalides ! / §rⓧ §cInvalid GPS coordinates!");
                    sender.sendMessage("§7Latitude doit être entre -90 et 90 / §r§7Latitude must be between -90 and 90");
                    sender.sendMessage("§7Longitude doit être entre -180 et 180 / §r§7Longitude must be between -180 and 180");
                    return true;
                }
                double x = longitude * LONGITUDE_TO_X;
                double z = latitude * LATITUDE_TO_Z;
                sender.sendMessage(String.format("⓪ §aCoordinates Minecraft: / §rⓧ §aMinecraft coordinates: X = %.2f, Z = %.2f", x, z));
                return true;
            } else if (mode.equals("togps")) {
                double x = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                double latitude = z / LATITUDE_TO_Z;
                double longitude = x / LONGITUDE_TO_X;
                if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                    sender.sendMessage("⓪ §cCoordonnées GPS invalides ! / §rⓧ §cInvalid GPS coordinates!");
                    return true;
                }
                String googleMaps = String.format(Locale.US, "https://www.google.com/maps?q=%.6f,%.6f", latitude, longitude);
                sender.sendMessage("§bGoogle Maps: " + googleMaps);
                return true;
            } else {
                sender.sendMessage("⓪ §cSous-commande inconnue ! Utilisez tominecraft ou togps. / §rⓧ §cUnknown subcommand! Use tominecraft or togps.");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("⓪ §cVeuillez entrer des nombres valides ! / §rⓧ §cPlease enter valid numbers!");
            return true;
        }
    }
}