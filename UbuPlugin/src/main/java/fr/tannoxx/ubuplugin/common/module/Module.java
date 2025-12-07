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
    protected final UbuPlugin plugin;
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
     * Retourne la description du module
     * @return Description
     */
    @NotNull
    public abstract String getDescription();
    
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
    //                    MÉTHODES UTILITAIRES
    // ═══════════════════════════════════════════════════════════
    
    /**
     * Accès rapide au ConfigManager
     * @return ConfigManager
     */
    @NotNull
    protected ConfigManager getConfigManager() {
        return moduleManager.getConfigManager();
    }
    
    /**
     * Accès rapide au DatabaseManager
     * @return DatabaseManager
     */
    @NotNull
    protected DatabaseManager getDatabaseManager() {
        return moduleManager.getDatabaseManager();
    }
    
    /**
     * Accès rapide au TranslationManager
     * @return TranslationManager
     */
    @NotNull
    protected TranslationManager getTranslationManager() {
        return moduleManager.getTranslationManager();
    }
    
    /**
     * Vérifie si un autre module est activé
     * @param moduleName Nom du module
     * @return true si activé
     */
    protected boolean isModuleEnabled(@NotNull String moduleName) {
        return moduleManager.isModuleEnabled(moduleName);
    }
    
    /**
     * Récupère un autre module
     * @param moduleClass Classe du module
     * @param <T> Type du module
     * @return Module ou null
     */
    protected <T extends Module> T getModule(@NotNull Class<T> moduleClass) {
        return moduleManager.getModule(moduleClass);
    }
    
    /**
     * Log une information
     * @param message Message
     * @param args Arguments
     */
    protected void info(@NotNull String message, Object... args) {
        logger.info(message, args);
    }
    
    /**
     * Log un warning
     * @param message Message
     * @param args Arguments
     */
    protected void warn(@NotNull String message, Object... args) {
        logger.warn(message, args);
    }
    
    /**
     * Log une erreur
     * @param message Message
     * @param throwable Exception
     */
    protected void error(@NotNull String message, @NotNull Throwable throwable) {
        logger.error(message, throwable);
    }
    
    /**
     * Log une erreur
     * @param message Message
     * @param args Arguments
     */
    protected void error(@NotNull String message, Object... args) {
        logger.error(message, args);
    }
    
    /**
     * Log en mode debug
     * @param message Message
     * @param args Arguments
     */
    protected void debug(@NotNull String message, Object... args) {
        if (getConfigManager().isDebugEnabled()) {
            logger.debug(message, args);
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s v%s by %s", getName(), getVersion(), getAuthor());
    }
}
