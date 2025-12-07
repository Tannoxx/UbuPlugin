package fr.tannoxx.ubuplugin.modules.earthtools.commands;

import fr.tannoxx.ubuplugin.UbuPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CountryCommand implements CommandExecutor {

    private static final double LATITUDE_TO_Z = -136.653;
    private static final double LONGITUDE_TO_X = 136.653;
    private final UbuPlugin plugin;
    private FileConfiguration replacementsConfig;
    private Map<String, String> countryReplacements;

    // Cache amélioré avec gestion des erreurs
    private final Map<String, CachedCountry> countryCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 300000; // 5 minutes pour les succès
    private static final long ERROR_CACHE_DURATION = 60000; // 1 minute pour les erreurs
    private static final double CACHE_PRECISION = 0.01; // ~1km

    public CountryCommand(UbuPlugin plugin) {
        this.plugin = plugin;
        loadReplacementsConfig();

        // Nettoyer le cache toutes les 10 minutes
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanCache, 12000L, 12000L);
    }

    private void loadReplacementsConfig() {
        File replacementsFile = new File(plugin.getDataFolder(), "country_replacements.yml");

        if (!replacementsFile.exists()) {
            plugin.getLogger().warning("country_replacements.yml n'existe pas, création...");
            try {
                // Vérifier et créer le dossier parent
                File parentDir = replacementsFile.getParentFile();
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    plugin.getLogger().severe("Impossible de créer le dossier: " + parentDir.getPath());
                    return;
                }

                // Créer le fichier
                if (!replacementsFile.createNewFile()) {
                    plugin.getLogger().warning("Le fichier existe déjà ou n'a pas pu être créé");
                }

                FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(replacementsFile);
                defaultConfig.set("replacements.Israel", "Palestine");
                defaultConfig.set("replacements.People's Republic of China", "Taiwan");
                defaultConfig.set("replacements.Western Sahara", "Western Sahara");
                defaultConfig.save(replacementsFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Impossible de créer country_replacements.yml: " + e.getMessage());
            }
        }

        replacementsConfig = YamlConfiguration.loadConfiguration(replacementsFile);
        loadReplacements();
    }

    private void loadReplacements() {
        countryReplacements = new HashMap<>();
        ConfigurationSection replacements = replacementsConfig.getConfigurationSection("replacements");

        if (replacements != null) {
            for (String originalName : replacements.getKeys(false)) {
                String replacement = replacements.getString(originalName);
                if (replacement != null) {
                    countryReplacements.put(originalName, replacement);
                }
            }
            plugin.getLogger().info("Chargé " + countryReplacements.size() + " remplacements de pays");
        }
    }

    /**
     * Recharge la configuration des remplacements de pays
     * Utilisé par la commande de reload du plugin
     */
    @SuppressWarnings("unused")
    public void reloadReplacementsConfig() {
        loadReplacementsConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur !");
            return true;
        }

        // Récupérer les coordonnées Minecraft du joueur
        double x = player.getLocation().getX();
        double z = player.getLocation().getZ();

        // Convertir en coordonnées GPS
        double latitude = z / LATITUDE_TO_Z;
        double longitude = x / LONGITUDE_TO_X;

        // Vérifier que les coordonnées GPS sont valides
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            sender.sendMessage("⓪ §cVous êtes en dehors des limites de la carte ! / §rⓧ §cYou are outside the map limits!");
            return true;
        }

        // Vérifier le cache
        String cacheKey = getCacheKey(latitude, longitude);
        CachedCountry cached = countryCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            if (cached.isError) {
                // Erreur en cache, réessayer plus rapidement
                if (System.currentTimeMillis() - cached.timestamp < ERROR_CACHE_DURATION) {
                    sendCountryMessage(sender, cached.countryName, latitude, longitude, true);
                    return true;
                }
                // Le cache d'erreur a expiré, on va réessayer
                countryCache.remove(cacheKey);
            } else {
                // Succès en cache
                sendCountryMessage(sender, cached.countryName, latitude, longitude, true);
                return true;
            }
        }

        // Afficher un message de chargement
        sender.sendMessage("§7Recherche du pays... / §r§7Searching for country...");

        // Appel API asynchrone
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Essayer Nominatim en premier (gratuit et fiable)
                String countryName = getCountryFromNominatim(latitude, longitude);

                // Fallback sur BigDataCloud si Nominatim échoue
                if (countryName == null || countryName.isEmpty()) {
                    plugin.getLogger().info("Nominatim a échoué, tentative avec BigDataCloud...");
                    countryName = getCountryFromBigDataCloud(latitude, longitude);
                }

                // Appliquer les remplacements
                if (countryName != null && countryReplacements.containsKey(countryName)) {
                    countryName = countryReplacements.get(countryName);
                }

                // Mettre en cache (avec gestion des erreurs)
                if (countryName != null && !countryName.isEmpty()) {
                    countryCache.put(cacheKey, new CachedCountry(countryName, false));
                } else {
                    // Mettre l'absence de résultat en cache (plus court)
                    countryCache.put(cacheKey, new CachedCountry(null, true));
                }

                // Envoyer le résultat
                String finalCountryName = countryName;
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendCountryMessage(sender, finalCountryName, latitude, longitude, false)
                );

            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la recherche du pays: " + e.getMessage());

                // Mettre l'erreur en cache
                countryCache.put(cacheKey, new CachedCountry(null, true));

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("⓪ §cErreur lors de la recherche du pays ! / §rⓧ §cError while searching for country!");
                    sender.sendMessage("§7L'API de géolocalisation est peut-être temporairement indisponible.");
                });
            }
        });

        return true;
    }

    private void sendCountryMessage(CommandSender sender, String countryName,
                                    double latitude, double longitude, boolean fromCache) {
        if (countryName != null && !countryName.isEmpty()) {
            // Correction: un seul %s dans le format string
            sender.sendMessage(String.format("⓪ §aVous êtes en / §rⓧ §aYou are in: §f%s", countryName));
            sender.sendMessage(String.format("§7(GPS: %.4f, %.4f)%s",
                    latitude, longitude, fromCache ? " §8[cache]" : ""));
        } else {
            sender.sendMessage("⓪ §eAucun pays trouvé à cette position (océan/territoire non défini) / §rⓧ §eNo country found at this position (ocean/undefined territory)");
            sender.sendMessage(String.format("§7(GPS: %.4f, %.4f)", latitude, longitude));
        }
    }

    private String getCacheKey(double latitude, double longitude) {
        double roundedLat = Math.round(latitude / CACHE_PRECISION) * CACHE_PRECISION;
        double roundedLon = Math.round(longitude / CACHE_PRECISION) * CACHE_PRECISION;
        return String.format("%.2f,%.2f", roundedLat, roundedLon);
    }

    private void cleanCache() {
        countryCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private String getCountryFromNominatim(double latitude, double longitude) throws Exception {
        String urlString = String.format(Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=3&addressdetails=1",
                latitude, longitude);

        HttpURLConnection conn = createConnection(urlString);
        conn.setRequestProperty("User-Agent", "UbuPlugin/2.0 (Minecraft Server)");

        return extractCountryFromNominatimResponse(conn);
    }

    private String getCountryFromBigDataCloud(double latitude, double longitude) throws Exception {
        // Toujours demander en anglais
        String urlString = String.format(Locale.US,
                "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.6f&longitude=%.6f&localityLanguage=en",
                latitude, longitude);

        HttpURLConnection conn = createConnection(urlString);

        return extractCountryFromBigDataCloudResponse(conn);
    }

    /**
     * Crée une connexion HTTP configurée avec timeouts
     */
    private HttpURLConnection createConnection(String urlString) throws IOException, URISyntaxException {
        URI uri = new URI(urlString);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }

    /**
     * Extrait le nom du pays depuis la réponse de Nominatim
     */
    private String extractCountryFromNominatimResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String response = readResponse(conn);
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

            if (jsonObject.has("address")) {
                JsonObject address = jsonObject.getAsJsonObject("address");
                if (address.has("country")) {
                    return address.get("country").getAsString();
                }
            }
        }
        return null;
    }

    /**
     * Extrait le nom du pays depuis la réponse de BigDataCloud
     */
    private String extractCountryFromBigDataCloudResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String response = readResponse(conn);
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

            if (jsonObject.has("countryName") && !jsonObject.get("countryName").isJsonNull()) {
                return jsonObject.get("countryName").getAsString();
            }
        }
        return null;
    }

    /**
     * Lit la réponse HTTP et retourne le contenu sous forme de String
     */
    private String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }

    /**
     * Classe pour stocker les données en cache
     */
    private static class CachedCountry {
        final String countryName;
        final long timestamp;
        final boolean isError;

        CachedCountry(String countryName, boolean isError) {
            this.countryName = countryName;
            this.timestamp = System.currentTimeMillis();
            this.isError = isError;
        }

        boolean isExpired() {
            long duration = isError ? ERROR_CACHE_DURATION : CACHE_DURATION;
            return System.currentTimeMillis() - timestamp > duration;
        }
    }
}