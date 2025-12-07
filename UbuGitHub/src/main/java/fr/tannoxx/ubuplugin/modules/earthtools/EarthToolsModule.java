package fr.tannoxx.ubuplugin.modules.earthtools;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.earthtools.commands.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Module EarthTools avec APIs de géolocalisation
 * Thread-safe avec cache Caffeine
 */
public class EarthToolsModule extends Module {

    private Cache<String, CountryCacheEntry> countryCache;

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

        // Enregistrer les commandes
        Objects.requireNonNull(plugin.getCommand("gps")).setExecutor(new GPSCommand(this));
        Objects.requireNonNull(plugin.getCommand("country")).setExecutor(new CountryCommand(this));
        Objects.requireNonNull(plugin.getCommand("tpr")).setExecutor(new TPRCommand(this));
        Objects.requireNonNull(plugin.getCommand("uptime")).setExecutor(new UptimeCommand(this));
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