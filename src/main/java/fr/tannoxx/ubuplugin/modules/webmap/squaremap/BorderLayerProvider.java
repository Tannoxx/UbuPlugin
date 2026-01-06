package fr.tannoxx.ubuplugin.modules.webmap.squaremap;

import fr.tannoxx.ubuplugin.modules.webmap.WebMapModule;
import fr.tannoxx.ubuplugin.modules.webmap.data.BorderData;
import fr.tannoxx.ubuplugin.modules.webmap.data.CountryBordersLoader;
import org.jetbrains.annotations.NotNull;
import xyz.jpenilla.squaremap.api.*;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;
import xyz.jpenilla.squaremap.api.marker.Polyline;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Fournisseur de couche pour afficher les frontières des pays sur Squaremap
 * Thread-safe avec chargement asynchrone
 * <p>
 * ✅ FIX: Ordre d'exécution corrigé pour afficher les frontières
 */
public class BorderLayerProvider {

    private final WebMapModule module;
    private final CountryBordersLoader loader;

    private SimpleLayerProvider layerProvider;
    private List<BorderData> bordersCache;

    // Constantes de conversion GPS → Minecraft
    private static final double LATITUDE_TO_Z = -136.53333;
    private static final double LONGITUDE_TO_X = 136.53333;

    // Configuration visuelle
    private Color borderColor;
    private int borderWeight;
    private double borderOpacity;

    public BorderLayerProvider(@NotNull WebMapModule module) {
        this.module = module;
        this.loader = new CountryBordersLoader(module);

        // Charger la config visuelle
        loadVisualConfig();
    }

    /**
     * ✅ FIX: Enregistre la couche sur Squaremap avec ordre d'exécution correct
     */
    public void register() {
        module.info("Chargement des frontières des pays...");

        // ✅ ÉTAPE 1: Charger les données en asynchrone
        module.plugin.getServer().getScheduler().runTaskAsynchronously(module.plugin, () -> {
            bordersCache = loader.loadBorders();

            if (bordersCache == null || bordersCache.isEmpty()) {
                module.error("Aucune donnée de frontière chargée !");
                return;
            }

            module.info("Données chargées: {} pays", bordersCache.size());

            // ✅ ÉTAPE 2: Créer et remplir la couche sur le thread principal
            module.plugin.getServer().getScheduler().runTask(module.plugin, () -> {
                createAndRegisterLayer();
            });
        });
    }

    /**
     * ✅ FIX: Crée la couche, ajoute TOUS les marqueurs, PUIS enregistre
     */
    private void createAndRegisterLayer() {
        if (bordersCache == null || bordersCache.isEmpty()) {
            module.error("Impossible de créer la couche: pas de données");
            return;
        }

        Squaremap squaremap = SquaremapProvider.get();

        // ✅ ÉTAPE 1: Créer le layer provider VIDE
        layerProvider = SimpleLayerProvider.builder("Countries")
                .defaultHidden(false)
                .showControls(true)
                .layerPriority(5)
                .build();

        module.info("Layer provider créé, ajout des marqueurs...");

        // ✅ ÉTAPE 2: Ajouter TOUS les marqueurs AVANT d'enregistrer
        int totalMarkers = 0;
        for (MapWorld world : squaremap.mapWorlds()) {
            if (!shouldProcessWorld(world)) continue;

            int markersAdded = addMarkersToWorld(world);
            totalMarkers += markersAdded;
        }

        module.info("Total de {} marqueurs ajoutés au layer provider", totalMarkers);

        // ✅ ÉTAPE 3: MAINTENANT on enregistre le provider (qui contient déjà les marqueurs)
        for (MapWorld world : squaremap.mapWorlds()) {
            if (!shouldProcessWorld(world)) continue;

            world.layerRegistry().register(Key.of("countries_layer"), layerProvider);
            module.info("Layer enregistré pour le monde: {}", world.identifier().asString());
        }

        module.info("✓ Couche des frontières complètement enregistrée ({} pays, {} marqueurs)",
                bordersCache.size(), totalMarkers);
    }

    /**
     * ✅ FIX: Retourne le nombre de marqueurs ajoutés
     */
    private int addMarkersToWorld(@NotNull MapWorld world) {
        if (bordersCache == null || bordersCache.isEmpty()) {
            module.warn("Pas de données de frontières à ajouter");
            return 0;
        }

        module.debug("Ajout des frontières pour le monde '{}'...", world.identifier().asString());

        int totalMarkers = 0;
        String worldId = world.identifier().asString();

        for (BorderData border : bordersCache) {
            int ringIndex = 0;

            for (List<double[]> ring : border.coordinates()) {
                // Convertir les points GPS en coordonnées Minecraft
                List<Point> minecraftPoints = new ArrayList<>();

                for (double[] coords : ring) {
                    double lat = coords[0];
                    double lon = coords[1];

                    // Validation des coordonnées
                    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                        continue;
                    }

                    // Conversion GPS → Minecraft
                    int x = (int) Math.round(lon * LONGITUDE_TO_X);
                    int z = (int) Math.round(lat * LATITUDE_TO_Z);

                    minecraftPoints.add(Point.of(x, z));
                }

                // Créer la polyline (ligne de frontière)
                if (minecraftPoints.size() >= 2) {
                    Polyline polyline = Polyline.polyline(minecraftPoints);

                    // Appliquer le style
                    polyline.markerOptions(MarkerOptions.builder()
                            .strokeColor(borderColor)
                            .strokeWeight(borderWeight)
                            .strokeOpacity(borderOpacity)
                            .clickTooltip(border.countryName())
                            .build());

                    // ✅ Créer un ID unique pour chaque segment (nettoyer TOUS les caractères invalides)
                    String cleanWorldId = worldId.replaceAll("[^a-zA-Z0-9._-]", "_");
                    String cleanCountryName = border.countryName().replaceAll("[^a-zA-Z0-9._-]", "_");

                    String markerId = String.format("%s_%s_ring_%d",
                            cleanWorldId,
                            cleanCountryName,
                            ringIndex);

                    layerProvider.addMarker(Key.of(markerId), polyline);
                    totalMarkers++;
                }

                ringIndex++;
            }
        }

        module.info("✓ {} segments de frontières ajoutés pour '{}'", totalMarkers, world.identifier().asString());
        return totalMarkers;
    }

    /**
     * Vérifie si on doit traiter ce monde (uniquement Overworld)
     */
    private boolean shouldProcessWorld(@NotNull MapWorld world) {
        String worldName = world.identifier().asString().toLowerCase();

        // Liste blanche des mondes à traiter
        if (worldName.equals("world")) {
            return true;
        }

        // Liste noire: exclure nether, end, etc.
        return !worldName.contains("nether") &&
                !worldName.contains("end") &&
                !worldName.contains("_the_");
    }

    /**
     * Charge la configuration visuelle depuis config.yml
     */
    private void loadVisualConfig() {
        String hexColor = module.getConfigManager().getString("webmap.border-color", "#FF0000");

        try {
            borderColor = Color.decode(hexColor);
        } catch (NumberFormatException e) {
            module.warn("Couleur invalide '{}', utilisation de rouge par défaut", hexColor);
            borderColor = Color.RED;
        }

        borderWeight = module.getConfigManager().getInt("webmap.border-weight", 2);
        borderOpacity = module.getConfigManager().getDouble("webmap.border-opacity", 0.8);

        module.debug("Config visuelle: couleur={}, poids={}, opacité={}",
                hexColor, borderWeight, borderOpacity);
    }

    /**
     * Désenregistre la couche
     */
    public void unregister() {
        if (layerProvider == null) {
            return;
        }

        try {
            Squaremap squaremap = SquaremapProvider.get();

            // Vider tous les marqueurs
            layerProvider.clearMarkers();

            // Désenregistrer de chaque monde
            for (MapWorld world : squaremap.mapWorlds()) {
                try {
                    world.layerRegistry().unregister(Key.of("countries_layer"));
                } catch (Exception e) {
                    module.debug("Erreur désenregistrement pour {}: {}",
                            world.identifier().asString(), e.getMessage());
                }
            }

            module.info("Couche des frontières désenregistrée");
        } catch (Exception e) {
            module.error("Erreur lors du désenregistrement", e);
        }
    }

    /**
     * Recharge la couche
     */
    public void reload() {
        module.info("Rechargement de la couche...");

        // Désenregistrer l'ancienne
        unregister();

        // Recharger la config
        loadVisualConfig();

        // Re-enregistrer (recharge aussi les données du cache)
        register();
    }

    /**
     * Force le rechargement des données depuis Internet
     */
    public void forceUpdate() {
        module.info("Mise à jour forcée des données...");

        // Supprimer le cache
        loader.clearCache();
        bordersCache = null;

        // Désenregistrer et re-enregistrer (téléchargera à nouveau)
        unregister();
        register();
    }
}