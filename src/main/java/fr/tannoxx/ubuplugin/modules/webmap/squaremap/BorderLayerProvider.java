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
import java.util.concurrent.CompletableFuture;

/**
 * Fournisseur de couche pour afficher les frontières des pays sur Squaremap
 * Thread-safe avec chargement asynchrone
 */
public class BorderLayerProvider {

    private final WebMapModule module;
    private final CountryBordersLoader loader;

    private SimpleLayerProvider layerProvider;
    private List<BorderData> bordersCache;

    // Constantes de conversion GPS → Minecraft (depuis ton plugin)
    private static final double LATITUDE_TO_Z = -136.653;
    private static final double LONGITUDE_TO_X = 136.653;

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
     * Enregistre la couche sur Squaremap
     */
    public void register() {
        module.info("Enregistrement de la couche sur Squaremap...");

        // Charger les frontières en asynchrone
        CompletableFuture.runAsync(() -> {
            bordersCache = loader.loadBorders();

            // Créer la couche une fois les données chargées
            module.plugin.getServer().getScheduler().runTask(module.plugin, () -> {
                createLayer();
                module.info("✓ Couche des frontières enregistrée ({} pays)", bordersCache.size());
            });
        });
    }

    /**
     * Crée la couche Squaremap
     */
    private void createLayer() {
        Squaremap squaremap = SquaremapProvider.get();

        // Créer le layer provider
        layerProvider = SimpleLayerProvider.builder("Countries")
                .defaultHidden(false) // Visible par défaut
                .showControls(true)   // Afficher le toggle dans l'interface
                .layerPriority(5)     // Priorité d'affichage
                .build();

        // Ajouter les marqueurs pour chaque monde
        for (MapWorld world : squaremap.mapWorlds()) {
            if (!shouldProcessWorld(world)) continue;

            addMarkersToWorld(world);

            // Enregistrer le provider pour ce monde
            world.layerRegistry().register(Key.of("countries_layer"), layerProvider);
        }
    }

    /**
     * Ajoute les marqueurs de frontières à un monde
     */
    private void addMarkersToWorld(@NotNull MapWorld world) {
        if (bordersCache == null || bordersCache.isEmpty()) return;

        module.info("Ajout des frontières pour le monde '{}'...", world.identifier().asString());

        int totalMarkers = 0;

        for (BorderData border : bordersCache) {
            for (List<double[]> ring : border.coordinates()) {
                // Convertir les points GPS en coordonnées Minecraft
                List<Point> minecraftPoints = new ArrayList<>();

                for (double[] coords : ring) {
                    double lat = coords[0];
                    double lon = coords[1];

                    // Conversion GPS → Minecraft
                    int x = (int) (lon * LONGITUDE_TO_X);
                    int z = (int) (lat * LATITUDE_TO_Z);

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
                            .clickTooltip(border.countryName()) // Nom au survol
                            .build());

                    // Ajouter au layer
                    String markerId = world.identifier().asString() + "_" + border.countryName() + "_" + totalMarkers;
                    layerProvider.addMarker(Key.of(markerId), polyline);
                    totalMarkers++;
                }
            }
        }

        module.info("✓ {} segments de frontières ajoutés pour '{}'", totalMarkers, world.identifier().asString());
    }

    /**
     * Vérifie si on doit traiter ce monde
     */
    private boolean shouldProcessWorld(@NotNull MapWorld world) {
        // Traiter uniquement l'Overworld (pas le Nether/End)
        String worldName = world.identifier().asString();
        return worldName.equals("world") || !worldName.contains("nether") && !worldName.contains("end");
    }

    /**
     * Charge la configuration visuelle depuis config.yml
     */
    private void loadVisualConfig() {
        String hexColor = module.getConfigManager().getString("webmap.border-color", "#FF0000");
        borderColor = Color.decode(hexColor);

        borderWeight = module.getConfigManager().getInt("webmap.border-weight", 2);
        borderOpacity = module.getConfigManager().getDouble("webmap.border-opacity", 0.8);

        module.debug("Config visuelle: couleur={}, poids={}, opacité={}",
                hexColor, borderWeight, borderOpacity);
    }

    /**
     * Désenregistre la couche
     */
    public void unregister() {
        if (layerProvider != null) {
            Squaremap squaremap = SquaremapProvider.get();

            // Désenregistrer de chaque monde
            for (MapWorld world : squaremap.mapWorlds()) {
                world.layerRegistry().unregister(Key.of("countries_layer"));
            }

            module.info("Couche des frontières désenregistrée");
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

        // Re-enregistrer
        register();
    }

    /**
     * Force le rechargement des données depuis Internet
     */
    public void forceUpdate() {
        module.info("Mise à jour forcée des données...");

        loader.clearCache();
        bordersCache = null;

        // Désenregistrer et re-enregistrer
        unregister();
        register();
    }
}