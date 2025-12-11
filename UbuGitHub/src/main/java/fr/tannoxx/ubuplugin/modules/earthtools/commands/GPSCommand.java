package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record GPSCommand(EarthToolsModule module) implements CommandExecutor, TabCompleter {

    private static final double LATITUDE_TO_Z = -136.653;
    private static final double LONGITUDE_TO_X = 136.653;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(module.getTranslationManager().getComponent(sender, "<red>Usage: /gps tominecraft <latitude> <longitude></red>"));
            sender.sendMessage(module.getTranslationManager().getComponent(sender, "<red>Usage: /gps togps <x> <z></red>"));
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        try {
            if (mode.equals("tominecraft")) {
                double latitude = Double.parseDouble(args[1]);
                double longitude = Double.parseDouble(args[2]);

                if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                    module.getTranslationManager().send(sender, "earthtools.gps.invalid-coords");
                    return true;
                }

                double x = longitude * LONGITUDE_TO_X;
                double z = latitude * LATITUDE_TO_Z;
                module.getTranslationManager().send(sender, "earthtools.gps.to-minecraft",
                        String.format("%.2f", x), String.format("%.2f", z));
                return true;

            } else if (mode.equals("togps")) {
                double x = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);

                double latitude = z / LATITUDE_TO_Z;
                double longitude = x / LONGITUDE_TO_X;

                if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                    module.getTranslationManager().send(sender, "earthtools.gps.invalid-coords");
                    return true;
                }

                module.getTranslationManager().send(sender, "earthtools.gps.to-gps",
                        String.format(Locale.US, "%.6f", latitude),
                        String.format(Locale.US, "%.6f", longitude));

                String googleMaps = String.format(Locale.US,
                        "https://www.google.com/maps?q=%.6f,%.6f", latitude, longitude);
                sender.sendMessage(module.getTranslationManager().getComponent(sender,
                        "<aqua>Google Maps: " + googleMaps + "</aqua>"));
                return true;

            } else {
                module.getTranslationManager().send(sender, "earthtools.gps.usage");
                return true;
            }
        } catch (NumberFormatException e) {
            module.getTranslationManager().send(sender, "errors.invalid-number");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("tominecraft");
            completions.add("togps");
        }

        return completions;
    }
}