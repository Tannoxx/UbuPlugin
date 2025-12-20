package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Commande /country avec implémentation complète des APIs et système de remplacement
 * <p>
 * OPTIMISATIONS v2.0.2:
 * - Rate limiting par joueur (max 5 calls/minute)
 * - Réduction des appels API redondants
 */
public record CountryCommand(EarthToolsModule module) implements CommandExecutor {

    private static final double LATITUDE_TO_Z = -136.653;
    private static final double LONGITUDE_TO_X = 136.653;
    private static final int TIMEOUT = 5000;

    // ✅ OPTIMISATION: Rate limiter par joueur
    private static final Cache<UUID, AtomicInteger> API_RATE_LIMITER = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    private static final int MAX_API_CALLS_PER_MINUTE = 5;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            module.getTranslationManager().send(sender, "errors.player-only");
            return true;
        }

        // ✅ OPTIMISATION: Vérifier rate limit
        UUID uuid = player.getUniqueId();
        if (!canCallAPI(uuid)) {
            module.getTranslationManager().send(sender, "earthtools.country.rate-limit");
            module.getTranslationManager().send(sender, "earthtools.country.rate-limit-info");
            module.getTranslationManager().send(sender, "earthtools.country.rate-limit-wait");
            return true;
        }

        Location loc = player.getLocation();
        double x = loc.getX();
        double z = loc.getZ();

        double latitude = z / LATITUDE_TO_Z;
        double longitude = x / LONGITUDE_TO_X;

        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            module.getTranslationManager().send(sender, "earthtools.country.out-of-bounds");
            return true;
        }

        String cacheKey = getCacheKey(latitude, longitude);
        EarthToolsModule.CountryCacheEntry cached = module.getCountryCache().getIfPresent(cacheKey);

        if (cached != null) {
            sendResult(sender, cached.countryName(), latitude, longitude, true);
            return true;
        }

        module.getTranslationManager().send(sender, "earthtools.country.searching");

        module.plugin.getServer().getScheduler().runTaskAsynchronously(module.plugin, () -> {
            String countryName = fetchCountryName(latitude, longitude);

            if (countryName != null && !countryName.isEmpty()) {
                countryName = applyCountryReplacement(countryName);
            }

            boolean isError = countryName == null || countryName.isEmpty();
            module.getCountryCache().put(cacheKey,
                    new EarthToolsModule.CountryCacheEntry(countryName, isError));

            String finalCountryName = countryName;
            module.plugin.getServer().getScheduler().runTask(module.plugin, () ->
                    sendResult(sender, finalCountryName, latitude, longitude, false)
            );
        });

        return true;
    }

    /**
     * ✅ OPTIMISATION: Rate limiting par joueur
     */
    private boolean canCallAPI(@NotNull UUID uuid) {
        AtomicInteger counter = API_RATE_LIMITER.get(uuid, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= MAX_API_CALLS_PER_MINUTE;
    }

    private void sendResult(@NotNull CommandSender sender, @Nullable String countryName,
                            double lat, double lon, boolean cached) {
        if (countryName != null && !countryName.isEmpty()) {
            module.getTranslationManager().send(sender, "earthtools.country.found", countryName);

            String coords = String.format(Locale.US, "%.4f", lat) + ", " + String.format(Locale.US, "%.4f", lon);
            if (cached) {
                sender.sendMessage(module.getTranslationManager().getComponent(sender, "earthtools.country.coords", coords)
                        .append(module.getTranslationManager().getComponent(sender, "earthtools.country.cached")));
            } else {
                module.getTranslationManager().send(sender, "earthtools.country.coords", coords);
            }
        } else {
            module.getTranslationManager().send(sender, "earthtools.country.not-found");
        }
    }

    @Nullable
    private String fetchCountryName(double latitude, double longitude) {
        try {
            String result = fetchFromNominatim(latitude, longitude);
            if (result != null) return result;

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
            HttpURLConnection conn = getHttpURLConnection("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.6f&longitude=%.6f&localityLanguage=en", lat, lon);

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
        String urlStr = String.format(Locale.US, url, lat, lon);

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

    private String applyCountryReplacement(String countryName) {
        Map<String, String> replacements = loadCountryReplacements();
        return replacements.getOrDefault(countryName, countryName);
    }

    private Map<String, String> loadCountryReplacements() {
        Map<String, String> replacements = new HashMap<>();
        File replacementsFile = new File(module.plugin.getDataFolder(), "country_replacements.yml");

        if (!replacementsFile.exists()) {
            createDefaultReplacementsFile(replacementsFile);
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(replacementsFile);
            ConfigurationSection section = config.getConfigurationSection("replacements");

            if (section != null) {
                for (String originalName : section.getKeys(false)) {
                    String replacement = section.getString(originalName);
                    if (replacement != null) {
                        replacements.put(originalName, replacement);
                    }
                }
                module.info("Chargé {} remplacements de pays", replacements.size());
            }
        } catch (Exception e) {
            module.error("Erreur lors du chargement des remplacements de pays", e);
        }

        return replacements;
    }

    private void createDefaultReplacementsFile(File file) {
        try {
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                module.error("Impossible de créer le dossier: {}", parentDir.getPath());
                return;
            }

            if (file.createNewFile()) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);

                config.set("replacements.Israel", "Palestine");
                config.set("replacements.People's Republic of China", "Taiwan");
                config.set("replacements.Western Sahara", "Western Sahara");

                config.save(file);
                module.info("Fichier country_replacements.yml créé avec succès");
            }
        } catch (IOException e) {
            module.error("Impossible de créer country_replacements.yml", e);
        }
    }

    public void reload() {
    }
}