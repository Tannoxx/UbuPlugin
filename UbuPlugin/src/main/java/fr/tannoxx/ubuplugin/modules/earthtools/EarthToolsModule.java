package fr.tannoxx.ubuplugin.modules.earthtools;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.earthtools.commands.*;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

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
        plugin.getCommand("gps").setExecutor(new GPSCommand(this));
        plugin.getCommand("country").setExecutor(new CountryCommand(this));
        plugin.getCommand("tpr").setExecutor(new TPRCommand(this));
        plugin.getCommand("uptime").setExecutor(new UptimeCommand(this));
        plugin.getCommand("countrylist").setExecutor(new CountryListCommand(this));
        plugin.getCommand("setcountrylist").setExecutor(new SetCountryListCommand(this));

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
    @Override
    public String getDescription() {
        return "Outils pour le serveur Earth (GPS, Country, TPR)";
    }

    public Cache<String, CountryCacheEntry> getCountryCache() {
        return countryCache;
    }

    public static class CountryCacheEntry {
        public final String countryName;
        public final boolean isError;
        public final long timestamp;

        public CountryCacheEntry(String countryName, boolean isError) {
            this.countryName = countryName;
            this.isError = isError;
            this.timestamp = System.currentTimeMillis();
        }
    }
}