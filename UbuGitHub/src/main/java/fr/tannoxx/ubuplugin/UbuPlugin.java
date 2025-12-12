package fr.tannoxx.ubuplugin;

import fr.tannoxx.ubuplugin.common.config.ConfigManager;
import fr.tannoxx.ubuplugin.common.database.DatabaseManager;
import fr.tannoxx.ubuplugin.common.i18n.TranslationManager;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.commands.MainCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Classe principale du plugin UbuPlugin
 * Plugin professionnel pour serveur Minecraft UbuEarth SMP
 *
 * @author Tannoxx
 * @version 2.0.0
 * @since 1.0.0
 */
public final class UbuPlugin extends JavaPlugin {

    // Instance singleton

    // Logger SLF4J
    private static final Logger LOGGER = LoggerFactory.getLogger(UbuPlugin.class);

    // Gestionnaires principaux
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private TranslationManager translationManager;
    private ModuleManager moduleManager;

    // États
    private boolean fullyEnabled = false;

    /**
     * Méthode appelée au chargement du plugin (AVANT onEnable)
     */
    @Override
    public void onLoad() {
        LOGGER.info("═══════════════════════════════════════════════");
        LOGGER.info("  UbuPlugin v{} - Chargement...", getDescription().getVersion());
        LOGGER.info("  Par {} - {}", getDescription().getAuthors(), getDescription().getWebsite());
        LOGGER.info("═══════════════════════════════════════════════");
    }

    /**
     * Méthode appelée à l'activation du plugin
     */
    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        try {
            // Étape 1: Configuration
            if (!initializeConfiguration()) {
                disablePlugin("Échec de l'initialisation de la configuration");
                return;
            }

            // Étape 2: Base de données
            if (!initializeDatabase()) {
                disablePlugin("Échec de l'initialisation de la base de données");
                return;
            }

            // Étape 3: Traductions
            if (!initializeTranslations()) {
                disablePlugin("Échec de l'initialisation des traductions");
                return;
            }

            // Étape 4: Modules
            if (!initializeModules()) {
                disablePlugin("Échec de l'initialisation des modules");
                return;
            }

            // Étape 5: Commandes
            registerCommands();

            // Étape 6: Finalisation
            fullyEnabled = true;
            long loadTime = System.currentTimeMillis() - startTime;

            LOGGER.info("═══════════════════════════════════════════════");
            LOGGER.info("  ✓ UbuPlugin activé avec succès !");
            LOGGER.info("  ✓ Temps de chargement: {}ms", loadTime);
            LOGGER.info("  ✓ {} modules chargés", moduleManager.getLoadedModulesCount());
            LOGGER.info("═══════════════════════════════════════════════");

        } catch (Exception e) {
            LOGGER.error("Erreur fatale lors de l'activation du plugin", e);
            disablePlugin("Erreur fatale: " + e.getMessage());
        }
    }

    /**
     * Méthode appelée à la désactivation du plugin
     */
    @Override
    public void onDisable() {
        if (!fullyEnabled) {
            LOGGER.warn("Plugin désactivé avant activation complète");
            return;
        }

        LOGGER.info("Désactivation de UbuPlugin...");

        try {
            // Désactiver les modules
            if (moduleManager != null) {
                LOGGER.info("Désactivation des modules...");
                moduleManager.disableAllModules();
            }

            // Fermer la base de données
            if (databaseManager != null) {
                LOGGER.info("Fermeture de la base de données...");
                databaseManager.shutdown();
            }

            // Nettoyer la configuration
            if (configManager != null) {
                configManager.cleanup();
            }

            LOGGER.info("UbuPlugin désactivé avec succès !");

        } catch (Exception e) {
            LOGGER.error("Erreur lors de la désactivation", e);
        }
    }

    /**
     * Initialise le gestionnaire de configuration
     * @return true si succès, false sinon
     */
    private boolean initializeConfiguration() {
        try {
            LOGGER.info("Initialisation de la configuration...");

            // Créer le dossier de données si nécessaire
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                LOGGER.error("Impossible de créer le dossier de données");
                return false;
            }

            // Sauvegarder les fichiers par défaut
            saveDefaultConfig();

            // Initialiser le gestionnaire
            configManager = new ConfigManager(this);
            configManager.loadConfiguration();

            LOGGER.info("✓ Configuration chargée");
            return true;

        } catch (Exception e) {
            LOGGER.error("Erreur lors de l'initialisation de la configuration", e);
            return false;
        }
    }

    /**
     * Initialise le gestionnaire de base de données
     * @return true si succès, false sinon
     */
    private boolean initializeDatabase() {
        try {
            LOGGER.info("Initialisation de la base de données...");

            databaseManager = new DatabaseManager(this, configManager);
            databaseManager.initialize();

            LOGGER.info("✓ Base de données initialisée ({})",
                    configManager.getDatabaseType());
            return true;

        } catch (Exception e) {
            LOGGER.error("Erreur lors de l'initialisation de la base de données", e);
            return false;
        }
    }

    /**
     * Initialise le gestionnaire de traductions
     * @return true si succès, false sinon
     */
    private boolean initializeTranslations() {
        try {
            LOGGER.info("Initialisation des traductions...");

            translationManager = new TranslationManager(configManager);
            translationManager.loadTranslations();

            LOGGER.info("✓ Traductions chargées ({} langues)",
                    translationManager.getLoadedLanguagesCount());
            return true;

        } catch (Exception e) {
            LOGGER.error("Erreur lors de l'initialisation des traductions", e);
            return false;
        }
    }

    /**
     * Initialise le gestionnaire de modules
     * @return true si succès, false sinon
     */
    private boolean initializeModules() {
        try {
            LOGGER.info("Initialisation des modules...");

            moduleManager = new ModuleManager(this, configManager, databaseManager, translationManager);
            moduleManager.loadAllModules();
            moduleManager.enableAllModules();

            LOGGER.info("✓ Modules initialisés");
            return true;

        } catch (Exception e) {
            LOGGER.error("Erreur lors de l'initialisation des modules", e);
            return false;
        }
    }

    /**
     * Enregistre les commandes principales
     */
    private void registerCommands() {
        LOGGER.info("Enregistrement des commandes...");

        MainCommand mainCommand = new MainCommand(this, configManager, moduleManager, translationManager);
        Objects.requireNonNull(getCommand("ubu")).setExecutor(mainCommand);
        Objects.requireNonNull(getCommand("ubu")).setTabCompleter(mainCommand);

        LOGGER.info("✓ Commandes enregistrées");
    }

    /**
     * Désactive le plugin avec un message d'erreur
     * @param reason Raison de la désactivation
     */
    private void disablePlugin(String reason) {
        LOGGER.error("═══════════════════════════════════════════════");
        LOGGER.error("  ✗ ÉCHEC DE L'ACTIVATION DU PLUGIN");
        LOGGER.error("  ✗ Raison: {}", reason);
        LOGGER.error("═══════════════════════════════════════════════");
        getServer().getPluginManager().disablePlugin(this);
    }

    /**
     * Recharge la configuration et les modules
     * @return true si succès, false sinon
     */
    public boolean reload() {
        try {
            LOGGER.info("Rechargement du plugin...");

            // Recharger la configuration
            configManager.reloadConfiguration();

            // Recharger les traductions
            translationManager.reloadTranslations();

            // Recharger les modules
            moduleManager.reloadAllModules();

            LOGGER.info("✓ Plugin rechargé avec succès");
            return true;

        } catch (Exception e) {
            LOGGER.error("Erreur lors du rechargement", e);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //                         GETTERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Retourne le gestionnaire de configuration
     * @return ConfigManager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Retourne le gestionnaire de base de données
     * @return DatabaseManager
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Retourne le gestionnaire de traductions
     * @return TranslationManager
     */
    public TranslationManager getTranslationManager() {
        return translationManager;
    }

}