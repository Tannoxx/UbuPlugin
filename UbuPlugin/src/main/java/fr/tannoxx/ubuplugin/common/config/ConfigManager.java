package fr.tannoxx.ubuplugin.common.config;

import fr.tannoxx.ubuplugin.UbuPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire centralisé de la configuration du plugin
 * Gère le fichier config.yml principal et les fichiers de configuration additionnels
 * 
 * @author Tannoxx
 * @version 2.0.0
 */
public class ConfigManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    
    private final UbuPlugin plugin;
    private FileConfiguration config;
    
    // Fichiers de configuration additionnels
    private final Map<String, FileConfiguration> additionalConfigs;
    private final Map<String, File> configFiles;
    
    // Cache des valeurs fréquemment utilisées
    private final Map<String, Object> cache;
    
    /**
     * Constructeur
     * @param plugin Instance du plugin
     */
    public ConfigManager(@NotNull UbuPlugin plugin) {
        this.plugin = plugin;
        this.additionalConfigs = new ConcurrentHashMap<>();
        this.configFiles = new ConcurrentHashMap<>();
        this.cache = new ConcurrentHashMap<>();
    }
    
    /**
     * Charge la configuration principale et les fichiers additionnels
     */
    public void loadConfiguration() {
        LOGGER.info("Chargement de la configuration...");
        
        // Charger config.yml principal
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // Charger les fichiers additionnels
        loadAdditionalConfig("country_replacements.yml");
        loadAdditionalConfig("messages_en.yml");
        loadAdditionalConfig("messages_fr.yml");
        
        // Remplir le cache
        populateCache();
        
        LOGGER.info("Configuration chargée avec succès");
    }
    
    /**
     * Recharge toute la configuration
     */
    public void reloadConfiguration() {
        LOGGER.info("Rechargement de la configuration...");
        
        // Vider le cache
        cache.clear();
        
        // Recharger config principal
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // Recharger les fichiers additionnels
        for (Map.Entry<String, File> entry : configFiles.entrySet()) {
            String name = entry.getKey();
            File file = entry.getValue();
            additionalConfigs.put(name, YamlConfiguration.loadConfiguration(file));
            LOGGER.debug("Rechargé: {}", name);
        }
        
        // Remplir le cache
        populateCache();
        
        LOGGER.info("Configuration rechargée avec succès");
    }
    
    /**
     * Charge un fichier de configuration additionnel
     *
     * @param fileName Nom du fichier
     */
    private void loadAdditionalConfig(@NotNull String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        
        // Sauvegarder le fichier par défaut si nécessaire
        if (!file.exists()) {
            saveDefaultResource(fileName);
        }
        
        // Charger le fichier
        if (file.exists()) {
            FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(file);
            additionalConfigs.put(fileName, fileConfig);
            configFiles.put(fileName, file);
            LOGGER.debug("Chargé: {}", fileName);
        } else {
            LOGGER.warn("Fichier de configuration introuvable: {}", fileName);
        }
    }
    
    /**
     * Sauvegarde un fichier de ressources par défaut
     * @param fileName Nom du fichier
     */
    private void saveDefaultResource(@NotNull String fileName) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            
            InputStream resource = plugin.getResource(fileName);
            if (resource != null) {
                Files.copy(resource, file.toPath());
                LOGGER.debug("Sauvegardé depuis ressources: {}", fileName);
            } else {
                // Créer un fichier vide si la ressource n'existe pas
                if (file.createNewFile()) {
                    LOGGER.debug("Créé fichier vide: {}", fileName);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Erreur lors de la sauvegarde de {}", fileName, e);
        }
    }
    
    /**
     * Remplit le cache avec les valeurs fréquemment utilisées
     */
    private void populateCache() {
        cache.put("general.prefix", config.getString("general.prefix", "&8[&6UbuPlugin&8]&r"));
        cache.put("general.debug", config.getBoolean("general.debug", false));
        cache.put("general.default-language", config.getString("general.default-language", "fr"));
        cache.put("general.auto-detect-language", config.getBoolean("general.auto-detect-language", true));
        
        // Cache des états d'activation des modules
        cache.put("modules.enchants", config.getBoolean("modules.enabled.enchants", true));
        cache.put("modules.ranks", config.getBoolean("modules.enabled.ranks", true));
        cache.put("modules.earthtools", config.getBoolean("modules.enabled.earthtools", true));
        cache.put("modules.lobbychat", config.getBoolean("modules.enabled.lobbychat", true));
        cache.put("modules.anvil", config.getBoolean("modules.enabled.anvil", true));
        
        LOGGER.debug("Cache rempli avec {} entrées", cache.size());
    }
    
    /**
     * Nettoie les ressources
     */
    public void cleanup() {
        cache.clear();
        additionalConfigs.clear();
        configFiles.clear();
    }
    
    // ═══════════════════════════════════════════════════════════
    //                    MÉTHODES D'ACCÈS RAPIDE
    // ═══════════════════════════════════════════════════════════
    
    /**
     * Récupère une valeur String avec cache
     * @param path Chemin de la configuration
     * @param def Valeur par défaut
     * @return Valeur
     */
    @NotNull
    public String getString(@NotNull String path, @NotNull String def) {
        Object cached = cache.get(path);
        if (cached instanceof String) {
            return (String) cached;
        }
        return config.getString(path, def);
    }
    
    /**
     * Récupère une valeur int
     * @param path Chemin de la configuration
     * @param def Valeur par défaut
     * @return Valeur
     */
    public int getInt(@NotNull String path, int def) {
        Object cached = cache.get(path);
        if (cached instanceof Integer) {
            return (Integer) cached;
        }
        return config.getInt(path, def);
    }
    
    /**
     * Récupère une valeur boolean avec cache
     * @param path Chemin de la configuration
     * @param def Valeur par défaut
     * @return Valeur
     */
    public boolean getBoolean(@NotNull String path, boolean def) {
        Object cached = cache.get(path);
        if (cached instanceof Boolean) {
            return (Boolean) cached;
        }
        return config.getBoolean(path, def);
    }
    
    /**
     * Récupère une valeur double
     * @param path Chemin de la configuration
     * @param def Valeur par défaut
     * @return Valeur
     */
    public double getDouble(@NotNull String path, double def) {
        return config.getDouble(path, def);
    }
    
    /**
     * Récupère une liste de strings
     * @param path Chemin de la configuration
     * @return Liste de strings
     */
    @NotNull
    public List<String> getStringList(@NotNull String path) {
        return config.getStringList(path);
    }
    
    /**
     * Récupère une section de configuration
     * @param path Chemin de la configuration
     * @return ConfigurationSection ou null
     */
    @Nullable
    public ConfigurationSection getConfigurationSection(@NotNull String path) {
        return config.getConfigurationSection(path);
    }
    
    /**
     * Récupère un fichier de configuration additionnel
     * @param fileName Nom du fichier
     * @return FileConfiguration ou null
     */
    @Nullable
    public FileConfiguration getAdditionalConfig(@NotNull String fileName) {
        return additionalConfigs.get(fileName);
    }
    
    /**
     * Sauvegarde un fichier de configuration additionnel
     * @param fileName Nom du fichier
     */
    public void saveAdditionalConfig(@NotNull String fileName) {
        FileConfiguration fileConfig = additionalConfigs.get(fileName);
        File file = configFiles.get(fileName);
        
        if (fileConfig != null && file != null) {
            try {
                fileConfig.save(file);
                LOGGER.debug("Sauvegardé: {}", fileName);
            } catch (IOException e) {
                LOGGER.error("Erreur lors de la sauvegarde de {}", fileName, e);
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════
    //                    MÉTHODES UTILITAIRES
    // ═══════════════════════════════════════════════════════════
    
    /**
     * Vérifie si un module est activé
     * @param moduleName Nom du module
     * @return true si activé
     */
    public boolean isModuleEnabled(@NotNull String moduleName) {
        return getBoolean("modules.enabled." + moduleName, false);
    }
    
    /**
     * Récupère le préfixe des messages
     * @return Préfixe formaté
     */
    @NotNull
    public String getPrefix() {
        return getString("general.prefix", "&8[&6UbuPlugin&8]&r");
    }
    
    /**
     * Vérifie si le mode debug est activé
     * @return true si debug activé
     */
    public boolean isDebugEnabled() {
        return getBoolean("general.debug", false);
    }
    
    /**
     * Récupère la langue par défaut
     * @return Code de langue (ex: "fr", "en")
     */
    @NotNull
    public String getDefaultLanguage() {
        return getString("general.default-language", "fr");
    }
    
    /**
     * Vérifie si la détection automatique de langue est activée
     * @return true si activé
     */
    public boolean isAutoDetectLanguage() {
        return getBoolean("general.auto-detect-language", true);
    }
    
    /**
     * Récupère le type de base de données
     * @return Type (SQLITE, MYSQL)
     */
    @NotNull
    public String getDatabaseType() {
        return getString("database.type", "SQLITE").toUpperCase();
    }
    
    /**
     * Récupère le nom du fichier SQLite
     * @return Nom du fichier
     */
    @NotNull
    public String getSQLiteFileName() {
        return getString("database.file", "ubuplugin.db");
    }
    
    /**
     * Vérifie si la migration automatique est activée
     * @return true si activé
     */
    public boolean isMigrationEnabled() {
        return getBoolean("database.migration.enabled", true);
    }
    
    /**
     * Vérifie si le backup avant migration est activé
     * @return true si activé
     */
    public boolean isMigrationBackupEnabled() {
        return getBoolean("database.migration.backup", true);
    }
    
    /**
     * Récupère la configuration principale
     * @return FileConfiguration
     */
    @NotNull
    public FileConfiguration getConfig() {
        return config;
    }
}
