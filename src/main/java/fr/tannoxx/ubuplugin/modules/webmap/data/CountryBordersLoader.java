package fr.tannoxx.ubuplugin.modules.webmap.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.tannoxx.ubuplugin.modules.webmap.WebMapModule;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Charge les données des frontières des pays depuis Natural Earth Data
 * Thread-safe avec téléchargement automatique
 */
public class CountryBordersLoader {

    private final WebMapModule module;

    // URL des données Natural Earth (10m = haute résolution)
    private static final String GEOJSON_URL =
            "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_countries.geojson";

    private static final String CACHE_FILE = "countries_borders.json";
    private static final int TIMEOUT = 30000; // 30 secondes

    public CountryBordersLoader(@NotNull WebMapModule module) {
        this.module = module;
    }

    /**
     * Charge les frontières de tous les pays
     */
    @NotNull
    public List<BorderData> loadBorders() {
        module.info("Chargement des frontières des pays...");

        File cacheFile = new File(module.plugin.getDataFolder(), CACHE_FILE);

        // Télécharger si pas en cache
        if (!cacheFile.exists()) {
            module.info("Téléchargement des données depuis Natural Earth...");
            if (!downloadGeoJSON(cacheFile)) {
                module.error("Échec du téléchargement des données");
                return List.of();
            }
        }

        // Parser le GeoJSON
        return parseGeoJSON(cacheFile);
    }

    /**
     * Télécharge le fichier GeoJSON
     */
    private boolean downloadGeoJSON(@NotNull File destination) {
        try {
            URL url = new URL(GEOJSON_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("User-Agent", "UbuPlugin/2.0 (Minecraft Server)");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                module.error("Erreur HTTP: {}", responseCode);
                return false;
            }

            // Télécharger le fichier
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            module.info("✓ Données téléchargées ({} KB)", destination.length() / 1024);
            return true;

        } catch (Exception e) {
            module.error("Erreur téléchargement GeoJSON", e);
            return false;
        }
    }

    /**
     * Parse le fichier GeoJSON
     */
    @NotNull
    private List<BorderData> parseGeoJSON(@NotNull File file) {
        List<BorderData> borders = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray features = root.getAsJsonArray("features");

            int totalCountries = features.size();
            module.info("Parsing de {} pays...", totalCountries);

            int processed = 0;
            int progressInterval = totalCountries / 10; // Log tous les 10%

            for (JsonElement featureElement : features) {
                JsonObject feature = featureElement.getAsJsonObject();
                JsonObject properties = feature.getAsJsonObject("properties");
                JsonObject geometry = feature.getAsJsonObject("geometry");

                // Nom du pays
                String countryName = properties.get("NAME").getAsString();

                // Type de géométrie
                String geometryType = geometry.get("type").getAsString();
                JsonElement coords = geometry.get("coordinates");

                // Extraire les coordonnées selon le type
                List<List<double[]>> allCoordinates = new ArrayList<>();

                if ("Polygon".equals(geometryType)) {
                    allCoordinates.addAll(parsePolygon(coords.getAsJsonArray()));
                } else if ("MultiPolygon".equals(geometryType)) {
                    allCoordinates.addAll(parseMultiPolygon(coords.getAsJsonArray()));
                }

                if (!allCoordinates.isEmpty()) {
                    borders.add(new BorderData(countryName, allCoordinates));
                }

                processed++;
                if (processed % progressInterval == 0) {
                    int percent = (processed * 100) / totalCountries;
                    module.info("  Progression: {}% ({}/{})", percent, processed, totalCountries);
                }
            }

            module.info("✓ {} pays parsés avec succès", borders.size());

        } catch (Exception e) {
            module.error("Erreur parsing GeoJSON", e);
        }

        return borders;
    }

    /**
     * Parse un Polygon GeoJSON
     */
    @NotNull
    private List<List<double[]>> parsePolygon(@NotNull JsonArray polygonCoords) {
        List<List<double[]>> result = new ArrayList<>();

        for (JsonElement ringElement : polygonCoords) {
            JsonArray ring = ringElement.getAsJsonArray();
            List<double[]> points = new ArrayList<>();

            for (JsonElement pointElement : ring) {
                JsonArray point = pointElement.getAsJsonArray();
                double lon = point.get(0).getAsDouble();
                double lat = point.get(1).getAsDouble();
                points.add(new double[]{lat, lon});
            }

            result.add(points);
        }

        return result;
    }

    /**
     * Parse un MultiPolygon GeoJSON
     */
    @NotNull
    private List<List<double[]>> parseMultiPolygon(@NotNull JsonArray multiPolygonCoords) {
        List<List<double[]>> result = new ArrayList<>();

        for (JsonElement polygonElement : multiPolygonCoords) {
            result.addAll(parsePolygon(polygonElement.getAsJsonArray()));
        }

        return result;
    }

    /**
     * Supprime le cache pour forcer un nouveau téléchargement
     */
    public boolean clearCache() {
        File cacheFile = new File(module.plugin.getDataFolder(), CACHE_FILE);
        if (cacheFile.exists()) {
            return cacheFile.delete();
        }
        return false;
    }
}