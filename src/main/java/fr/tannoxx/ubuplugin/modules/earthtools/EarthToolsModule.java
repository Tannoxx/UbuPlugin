package fr.tannoxx.ubuplugin.modules.earthtools;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.earthtools.commands.*;
import fr.tannoxx.ubuplugin.modules.earthtools.listeners.UptimeGUIListener; // ✅ AJOUTÉ
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Module EarthTools avec APIs de géolocalisation et country replacements
 * Thread-safe avec cache Caffeine
 * <p>
 * ✅ FIX v2.0.3: Protection GUI Uptime
 */
public class EarthToolsModule extends Module {

    private Cache<String, CountryCacheEntry> countryCache;
    private CountryCommand countryCommand;

    public EarthToolsModule(@NotNull UbuPlugin plugin, @NotNull ModuleManager moduleManager) {
        super(plugin, moduleManager);
    }

    @Override
    public void onEnable() {
        // Initialiser le cache
        int successTTL = getConfigManager().getInt("earthtools.country.cache.success-duration", 300);
        countryCache = Caffeine.newBuilder()
                .expireAfterWrite(successTTL, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();

        // ✅ AJOUTÉ: Enregistrer le listener de protection GUI
        plugin.getServer().getPluginManager().registerEvents(new UptimeGUIListener(), plugin);

        // Enregistrer les commandes avec TabCompleters
        GPSCommand gpsCommand = new GPSCommand(this);
        Objects.requireNonNull(plugin.getCommand("gps")).setExecutor(gpsCommand);
        Objects.requireNonNull(plugin.getCommand("gps")).setTabCompleter(gpsCommand);

        // Country command avec replacements
        countryCommand = new CountryCommand(this);
        Objects.requireNonNull(plugin.getCommand("country")).setExecutor(countryCommand);

        Objects.requireNonNull(plugin.getCommand("tpr")).setExecutor(new TPRCommand(this));

        UptimeCommand uptimeCommand = new UptimeCommand(this);
        Objects.requireNonNull(plugin.getCommand("uptime")).setExecutor(uptimeCommand);
        Objects.requireNonNull(plugin.getCommand("uptime")).setTabCompleter(uptimeCommand);
        plugin.getServer().getPluginManager().registerEvents(uptimeCommand, plugin);

        Objects.requireNonNull(plugin.getCommand("countrylist")).setExecutor(new CountryListCommand(this));
        Objects.requireNonNull(plugin.getCommand("setcountrylist")).setExecutor(new SetCountryListCommand(this));

        info("Module EarthTools activé");
    }

    @Override
    public void onDisable() {
        if (countryCache != null) {
            countryCache.invalidateAll();
        }
        info("Module EarthTools désactivé");
    }

    @Override
    public void reload() {
        info("Rechargement du module EarthTools...");

        // Vider le cache pour forcer le rechargement
        if (countryCache != null) {
            countryCache.invalidateAll();
        }

        // Recharger les country replacements
        if (countryCommand != null) {
            countryCommand.reload();
        }

        info("✓ Module EarthTools rechargé");
    }

    @NotNull
    @Override
    public String getName() {
        return "EarthTools";
    }

    @NotNull
    public Cache<String, CountryCacheEntry> getCountryCache() {
        return countryCache;
    }

    public record CountryCacheEntry(
            String countryName,
            boolean isError,
            long timestamp
    ) {
        public CountryCacheEntry(String countryName, boolean isError) {
            this(countryName, isError, System.currentTimeMillis());
        }
    }
}