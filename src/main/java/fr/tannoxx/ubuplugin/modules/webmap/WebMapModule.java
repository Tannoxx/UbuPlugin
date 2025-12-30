package fr.tannoxx.ubuplugin.modules.webmap;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.webmap.commands.WebMapCommand;
import fr.tannoxx.ubuplugin.modules.webmap.squaremap.BorderLayerProvider;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import xyz.jpenilla.squaremap.api.SquaremapProvider;

import java.util.Objects;

/**
 * Module WebMap - Affiche les frontières des pays sur Squaremap
 *
 * @author Tannoxx
 * @version 1.0.0
 */
public class WebMapModule extends Module {

    private BorderLayerProvider borderLayerProvider;
    private boolean squaremapAvailable = false;

    public WebMapModule(@NotNull UbuPlugin plugin, @NotNull ModuleManager moduleManager) {
        super(plugin, moduleManager);
    }

    @Override
    public void onEnable() {
        // Vérifier si Squaremap est présent
        if (Bukkit.getPluginManager().getPlugin("squaremap") == null) {
            warn("Squaremap n'est pas installé ! Le module WebMap sera désactivé.");
            return;
        }

        squaremapAvailable = true;

        try {
            // Initialiser le fournisseur de couche
            borderLayerProvider = new BorderLayerProvider(this);
            borderLayerProvider.register();

            // Enregistrer la commande
            Objects.requireNonNull(plugin.getCommand("webmap")).setExecutor(new WebMapCommand(this));

            info("Module WebMap activé - Frontières des pays chargées sur Squaremap");
        } catch (Exception e) {
            error("Erreur lors de l'initialisation du module WebMap", e);
            squaremapAvailable = false;
        }
    }

    @Override
    public void onDisable() {
        if (borderLayerProvider != null) {
            borderLayerProvider.unregister();
        }
        info("Module WebMap désactivé");
    }

    @Override
    public void reload() {
        info("Rechargement du module WebMap...");

        if (borderLayerProvider != null) {
            borderLayerProvider.reload();
        }

        info("✓ Module WebMap rechargé");
    }

    @NotNull
    @Override
    public String getName() {
        return "WebMap";
    }

    public boolean isSquaremapAvailable() {
        return squaremapAvailable;
    }

    @NotNull
    public BorderLayerProvider getBorderLayerProvider() {
        if (borderLayerProvider == null) {
            throw new IllegalStateException("BorderLayerProvider non initialisé");
        }
        return borderLayerProvider;
    }
}