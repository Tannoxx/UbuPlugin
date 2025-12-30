package fr.tannoxx.ubuplugin.common.module;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.config.ConfigManager;
import fr.tannoxx.ubuplugin.common.database.DatabaseManager;
import fr.tannoxx.ubuplugin.common.i18n.TranslationManager;
import fr.tannoxx.ubuplugin.modules.antiafk.AntiAFKModule;
import fr.tannoxx.ubuplugin.modules.earthtools.EarthToolsModule;
import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import fr.tannoxx.ubuplugin.modules.lobbychat.LobbyChatModule;
import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import fr.tannoxx.ubuplugin.modules.webmap.WebMapModule;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire centralisé des modules du plugin
 * Gère le cycle de vie, les dépendances et la communication entre modules
 *
 * @author Tannoxx
 * @version 2.0.0
 */
public class ModuleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleManager.class);

    private final UbuPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final TranslationManager translationManager;

    // Modules enregistrés
    private final Map<String, Module> modules;
    private final Map<String, ModuleState> moduleStates;

    /**
     * États possibles d'un module
     */
    public enum ModuleState {
        UNLOADED,
        LOADED,
        ENABLED,
        DISABLED,
        ERROR
    }

    /**
     * Constructeur
     * @param plugin Instance du plugin
     * @param configManager Gestionnaire de configuration
     * @param databaseManager Gestionnaire de base de données
     * @param translationManager Gestionnaire de traductions
     */
    public ModuleManager(@NotNull UbuPlugin plugin,
                         @NotNull ConfigManager configManager,
                         @NotNull DatabaseManager databaseManager,
                         @NotNull TranslationManager translationManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.translationManager = translationManager;
        this.modules = new ConcurrentHashMap<>();
        this.moduleStates = new ConcurrentHashMap<>();
    }

    /**
     * Charge tous les modules disponibles
     */
    public void loadAllModules() {
        LOGGER.info("Chargement des modules...");

        // Instancier les modules
        registerModule(new EnchantsModule(plugin, this));
        registerModule(new RanksModule(plugin, this));
        registerModule(new EarthToolsModule(plugin, this));
        registerModule(new LobbyChatModule(plugin, this));
        registerModule(new AntiAFKModule(plugin, this));
        registerModule(new WebMapModule(plugin, this));

        // Charger chaque module
        for (Module module : modules.values()) {
            loadModule(module);
        }

        LOGGER.info("✓ {} modules chargés", modules.size());
    }

    /**
     * Active tous les modules chargés
     */
    public void enableAllModules() {
        LOGGER.info("Activation des modules...");

        int enabledCount = 0;

        for (Module module : modules.values()) {
            if (shouldEnableModule(module)) {
                if (enableModule(module)) {
                    enabledCount++;
                }
            } else {
                LOGGER.info("Module {} désactivé dans la configuration", module.getName());
            }
        }

        LOGGER.info("✓ {}/{} modules activés", enabledCount, modules.size());
    }

    /**
     * Désactive tous les modules
     */
    public void disableAllModules() {
        LOGGER.info("Désactivation des modules...");

        // Désactiver dans l'ordre inverse pour respecter les dépendances
        List<Module> moduleList = new ArrayList<>(modules.values());
        Collections.reverse(moduleList);

        for (Module module : moduleList) {
            if (moduleStates.get(module.getName()) == ModuleState.ENABLED) {
                disableModule(module);
            }
        }
    }

    /**
     * Recharge tous les modules
     */
    public void reloadAllModules() {
        LOGGER.info("Rechargement des modules...");

        for (Module module : modules.values()) {
            if (moduleStates.get(module.getName()) == ModuleState.ENABLED) {
                try {
                    module.reload();
                    LOGGER.info("✓ Module {} rechargé", module.getName());
                } catch (Exception e) {
                    LOGGER.error("Erreur lors du rechargement du module {}",
                            module.getName(), e);
                }
            }
        }
    }

    /**
     * Enregistre un module
     * @param module Module à enregistrer
     */
    private void registerModule(@NotNull Module module) {
        modules.put(module.getName(), module);
        moduleStates.put(module.getName(), ModuleState.UNLOADED);
        LOGGER.debug("Module enregistré: {}", module.getName());
    }

    /**
     * Charge un module
     *
     * @param module Module à charger
     */
    private void loadModule(@NotNull Module module) {
        try {
            LOGGER.debug("Chargement du module {}...", module.getName());

            module.onLoad();
            moduleStates.put(module.getName(), ModuleState.LOADED);

            LOGGER.debug("✓ Module {} chargé", module.getName());

        } catch (Exception e) {
            LOGGER.error("Erreur lors du chargement du module {}",
                    module.getName(), e);
            moduleStates.put(module.getName(), ModuleState.ERROR);
        }
    }

    /**
     * Active un module
     * @param module Module à activer
     * @return true si succès
     */
    private boolean enableModule(@NotNull Module module) {
        try {
            LOGGER.debug("Activation du module {}...", module.getName());

            module.onEnable();
            moduleStates.put(module.getName(), ModuleState.ENABLED);

            LOGGER.info("✓ Module {} activé", module.getName());
            return true;

        } catch (Exception e) {
            LOGGER.error("Erreur lors de l'activation du module {}",
                    module.getName(), e);
            moduleStates.put(module.getName(), ModuleState.ERROR);
            return false;
        }
    }

    /**
     * Désactive un module
     * @param module Module à désactiver
     */
    private void disableModule(@NotNull Module module) {
        try {
            LOGGER.debug("Désactivation du module {}...", module.getName());

            module.onDisable();
            moduleStates.put(module.getName(), ModuleState.DISABLED);

            LOGGER.debug("✓ Module {} désactivé", module.getName());

        } catch (Exception e) {
            LOGGER.error("Erreur lors de la désactivation du module {}",
                    module.getName(), e);
        }
    }

    /**
     * Vérifie si un module doit être activé selon la configuration
     * @param module Module à vérifier
     * @return true si doit être activé
     */
    private boolean shouldEnableModule(@NotNull Module module) {
        return configManager.isModuleEnabled(module.getName().toLowerCase());
    }

    /**
     * Récupère tous les modules
     * @return Collection de modules
     */
    @NotNull
    public Collection<Module> getAllModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    /**
     * Récupère le nombre de modules chargés
     * @return Nombre de modules
     */
    public int getLoadedModulesCount() {
        return (int) moduleStates.values().stream()
                .filter(state -> state == ModuleState.ENABLED)
                .count();
    }

    /**
     * Génère un rapport d'état des modules
     * @return Liste des états
     */
    @NotNull
    public List<String> getModulesStatusReport() {
        List<String> report = new ArrayList<>();

        for (Map.Entry<String, Module> entry : modules.entrySet()) {
            String name = entry.getKey();
            Module module = entry.getValue();
            ModuleState state = moduleStates.get(name);

            String status = switch (state) {
                case ENABLED -> "✓ Activé";
                case DISABLED -> "○ Désactivé";
                case ERROR -> "✗ Erreur";
                case LOADED -> "○ Chargé";
                case UNLOADED -> "○ Non chargé";
            };

            report.add(String.format("%s v%s - %s",
                    module.getName(), module.getVersion(), status));
        }

        return report;
    }

    // ═══════════════════════════════════════════════════════════
    //                         GETTERS
    // ═══════════════════════════════════════════════════════════

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TranslationManager getTranslationManager() {
        return translationManager;
    }
}