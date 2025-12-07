package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CountryCommand implements CommandExecutor {

    private final EarthToolsModule module;
    private static final double LATITUDE_TO_Z = -136.653;
    private static final double LONGITUDE_TO_X = 136.653;

    public CountryCommand(EarthToolsModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getTranslationManager().send(sender, "errors.player-only");
            return true;
        }

        Location loc = player.getLocation();
        double x = loc.getX();
        double z = loc.getZ();

        // Convertir en GPS
        double latitude = z / LATITUDE_TO_Z;
        double longitude = x / LONGITUDE_TO_X;

        // Vérifier limites
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            module.getTranslationManager().send(sender, "earthtools.country.out-of-bounds");
            return true;
        }

        // Vérifier le cache
        String cacheKey = getCacheKey(latitude, longitude);
        EarthToolsModule.CountryCacheEntry cached = module.getCountryCache().getIfPresent(cacheKey);

        if (cached != null) {
            sendResult(sender, cached.countryName, latitude, longitude, true);
            return true;
        }

        // Rechercher de manière asynchrone
        module.getTranslationManager().send(sender, "earthtools.country.searching");

        module.plugin.getServer().getScheduler().runTaskAsynchronously(module.plugin, () -> {
            String countryName = fetchCountryName(latitude, longitude);

            // Mettre en cache
            boolean isError = countryName == null || countryName.isEmpty();
            module.getCountryCache().put(cacheKey,
                    new EarthToolsModule.CountryCacheEntry(countryName, isError));

            // Retour au thread principal
            module.plugin.getServer().getScheduler().runTask(module.plugin, () ->
                    sendResult(sender, countryName, latitude, longitude, false)
            );
        });

        return true;
    }

    private void sendResult(CommandSender sender, String countryName,
                            double lat, double lon, boolean cached) {
        if (countryName != null && !countryName.isEmpty()) {
            module.getTranslationManager().send(sender, "earthtools.country.found", countryName);
            sender.sendMessage(module.getTranslationManager().get(sender, "earthtools.country.coords",
                    String.format("%.4f", lat), String.format("%.4f", lon)) +
                    (cached ? " " + module.getTranslationManager().get(sender, "earthtools.country.cached") : ""));
        } else {
            module.getTranslationManager().send(sender, "earthtools.country.not-found");
        }
    }

    private String fetchCountryName(double latitude, double longitude) {
        try {
            // Utiliser Nominatim en premier
            String result = fetchFromNominatim(latitude, longitude);
            if (result != null) return result;

            // Fallback BigDataCloud
            return fetchFromBigDataCloud(latitude, longitude);
        } catch (Exception e) {
            module.error("Erreur API géolocalisation", e);
            return null;
        }
    }

    private String fetchFromNominatim(double lat, double lon) {
        // VOTRE CODE EXISTANT ICI - gardez votre implémentation
        return null; // Placeholder
    }

    private String fetchFromBigDataCloud(double lat, double lon) {
        // VOTRE CODE EXISTANT ICI - gardez votre implémentation
        return null; // Placeholder
    }

    private String getCacheKey(double latitude, double longitude) {
        double precision = module.getConfigManager().getDouble("earthtools.country.cache.precision", 0.01);
        double roundedLat = Math.round(latitude / precision) * precision;
        double roundedLon = Math.round(longitude / precision) * precision;
        return String.format("%.2f,%.2f", roundedLat, roundedLon);
    }
}