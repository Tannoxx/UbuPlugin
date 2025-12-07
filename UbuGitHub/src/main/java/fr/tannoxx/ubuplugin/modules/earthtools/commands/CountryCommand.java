package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Commande /country avec implémentation complète des APIs
 */
public record CountryCommand(EarthToolsModule module) implements CommandExecutor {

    private static final double LATITUDE_TO_Z = -136.653;
    private static final double LONGITUDE_TO_X = 136.653;
    private static final int TIMEOUT = 5000;

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
            sendResult(sender, cached.countryName(), latitude, longitude, true);
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

    private void sendResult(@NotNull CommandSender sender, @Nullable String countryName,
                            double lat, double lon, boolean cached) {
        if (countryName != null && !countryName.isEmpty()) {
            module.getTranslationManager().send(sender, "earthtools.country.found", countryName);

            String coords = module.getTranslationManager().get(sender, "earthtools.country.coords",
                    String.format(Locale.US, "%.4f", lat), String.format(Locale.US, "%.4f", lon));

            if (cached) {
                coords += " " + module.getTranslationManager().get(sender, "earthtools.country.cached");
            }
            sender.sendMessage(coords);
        } else {
            module.getTranslationManager().send(sender, "earthtools.country.not-found");
        }
    }

    @Nullable
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

    @Nullable
    private String fetchFromNominatim(double lat, double lon) {
        try {
            HttpURLConnection conn = getHttpURLConnection("https://nominatim.openstreetmap.org/reverse?lat=%.6f&lon=%.6f&format=json", lat, lon);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();

                    if (json.has("address")) {
                        JsonObject address = json.getAsJsonObject("address");
                        if (address.has("country")) {
                            return address.get("country").getAsString();
                        }
                    }
                }
            }

            conn.disconnect();
        } catch (Exception e) {
            module.debug("Nominatim échoué: {}", e.getMessage());
        }
        return null;
    }

    @Nullable
    private String fetchFromBigDataCloud(double lat, double lon) {
        try {
            HttpURLConnection conn = getHttpURLConnection("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.6f&longitude=%.6f&localityLanguage=fr", lat, lon);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();

                    if (json.has("countryName")) {
                        return json.get("countryName").getAsString();
                    }
                }
            }

            conn.disconnect();
        } catch (Exception e) {
            module.debug("BigDataCloud échoué: {}", e.getMessage());
        }
        return null;
    }

    private static @NotNull HttpURLConnection getHttpURLConnection(String url, double lat, double lon) throws IOException {
        String urlStr = String.format(Locale.US,
                url,
                lat, lon);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setRequestProperty("User-Agent", "UbuPlugin/2.0 (Minecraft Server)");
        return conn;
    }

    private String getCacheKey(double latitude, double longitude) {
        double precision = module.getConfigManager().getDouble("earthtools.country.cache.precision", 0.01);
        double roundedLat = Math.round(latitude / precision) * precision;
        double roundedLon = Math.round(longitude / precision) * precision;
        return String.format(Locale.US, "%.2f,%.2f", roundedLat, roundedLon);
    }
}