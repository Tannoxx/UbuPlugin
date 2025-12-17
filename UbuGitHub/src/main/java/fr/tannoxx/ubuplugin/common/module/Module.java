package fr.tannoxx.ubuplugin.common.module;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.config.ConfigManager;
import fr.tannoxx.ubuplugin.common.database.DatabaseManager;
import fr.tannoxx.ubuplugin.common.i18n.TranslationManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classe abstraite de base pour tous les modules du plugin
 * Définit le cycle de vie et les méthodes communes
 *
 * @author Tannoxx
 * @version 2.0.0
 */
public abstract class Module {

    protected final Logger logger;
    public final UbuPlugin plugin;
    protected final ModuleManager moduleManager;

    /**
     * Constructeur
     * @param plugin Instance du plugin
     * @param moduleManager Gestionnaire de modules
     */
    protected Module(@NotNull UbuPlugin plugin, @NotNull ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    /**
     * Appelé lors du chargement du module (avant activation)
     * Utilisé pour initialiser les ressources qui ne nécessitent pas d'autres modules
     */
    public void onLoad() {
        logger.debug("Module {} chargé", getName());
    }

    /**
     * Appelé lors de l'activation du module
     * Enregistre les listeners, commandes, tasks, etc.
     */
    public abstract void onEnable();

    /**
     * Appelé lors de la désactivation du module
     * Nettoie les ressources, sauvegarde les données, etc.
     */
    public abstract void onDisable();

    /**
     * Appelé lors du rechargement du module
     * Par défaut, désactive puis réactive le module
     */
    public void reload() {
        logger.info("Rechargement du module {}...", getName());
        onDisable();
        onEnable();
    }

    /**
     * Retourne le nom du module
     * @return Nom du module
     */
    @NotNull
    public abstract String getName();

    /**
     * Retourne la version du module
     * @return Version
     */
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Retourne l'auteur du module
     * @return Auteur
     */
    @NotNull
    public String getAuthor() {
        return "Tannoxx";
    }

    // ═══════════════════════════════════════════════════════════
    //                    MÉTHODES UTILITAIRES - PUBLIC
    // ═══════════════════════════════════════════════════════════

    /**
     * Accès public au ConfigManager (pour les commandes/listeners)
     * @return ConfigManager
     */
    @NotNull
    public ConfigManager getConfigManager() {
        return moduleManager.getConfigManager();
    }

    /**
     * Accès public au DatabaseManager (pour les commandes/listeners)
     * @return DatabaseManager
     */
    @NotNull
    public DatabaseManager getDatabaseManager() {
        return moduleManager.getDatabaseManager();
    }

    /**
     * Accès public au TranslationManager (pour les commandes/listeners)
     * @return TranslationManager
     */
    @NotNull
    public TranslationManager getTranslationManager() {
        return moduleManager.getTranslationManager();
    }

    /**
     * Log une information
     * @param message Message
     * @param args Arguments
     */
    public void info(@NotNull String message, Object... args) {
        logger.info(message, args);
    }

    /**
     * Log un warning
     * @param message Message
     * @param args Arguments
     */
    public void warn(@NotNull String message, Object... args) {
        logger.warn(message, args);
    }

    /**
     * Log une erreur
     * @param message Message
     * @param throwable Exception
     */
    public void error(@NotNull String message, @NotNull Throwable throwable) {
        logger.error(message, throwable);
    }

    /**
     * Log une erreur
     * @param message Message
     * @param args Arguments
     */
    public void error(@NotNull String message, Object... args) {
        logger.error(message, args);
    }

    /**
     * Log en mode debug
     * @param message Message
     * @param args Arguments
     */
    public void debug(@NotNull String message, Object... args) {
        if (getConfigManager().isDebugEnabled()) {
            logger.debug(message, args);
        }
    }

    @Override
    public String toString() {
        return String.format("%s v%s by %s", getName(), getVersion(), getAuthor());
    }
}