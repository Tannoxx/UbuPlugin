package fr.tannoxx.ubuplugin.common.i18n;

import fr.tannoxx.ubuplugin.common.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de traductions multi-langues
 * Thread-safe avec cache concurrent
 *
 * @author Tannoxx
 * @version 2.0.0
 */
public class TranslationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationManager.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ConfigManager configManager;

    // Traductions chargées par langue (thread-safe)
    private final Map<String, Map<String, String>> translations;

    // Cache des langues des joueurs (thread-safe)
    private final Map<UUID, String> playerLanguages;

    // Langues supportées
    private static final String[] SUPPORTED_LANGUAGES = {"en", "fr"};

    /**
     * Constructeur
     * @param configManager Gestionnaire de configuration
     */
    public TranslationManager(@NotNull ConfigManager configManager) {
        this.configManager = configManager;
        this.translations = new ConcurrentHashMap<>();
        this.playerLanguages = new ConcurrentHashMap<>();
    }

    /**
     * Charge toutes les traductions disponibles
     */
    public void loadTranslations() {
        LOGGER.info("Chargement des traductions...");

        for (String lang : SUPPORTED_LANGUAGES) {
            loadLanguage(lang);
        }

        LOGGER.info("✓ {} langues chargées", translations.size());
    }

    /**
     * Recharge toutes les traductions
     */
    public void reloadTranslations() {
        LOGGER.info("Rechargement des traductions...");
        translations.clear();
        loadTranslations();
    }

    /**
     * Charge une langue spécifique
     * @param language Code de langue (en, fr)
     */
    private void loadLanguage(@NotNull String language) {
        String fileName = "messages_" + language + ".yml";
        FileConfiguration langConfig = configManager.getAdditionalConfig(fileName);

        if (langConfig == null) {
            LOGGER.warn("Fichier de langue introuvable: {}", fileName);
            return;
        }

        Map<String, String> langMap = new HashMap<>();

        // Charger toutes les clés récursivement
        loadKeys(langConfig, "", langMap);

        translations.put(language, langMap);
        LOGGER.debug("Langue chargée: {} ({} clés)", language, langMap.size());
    }

    /**
     * Charge les clés de configuration récursivement
     * @param config Configuration
     * @param prefix Préfixe actuel
     * @param map Map de destination
     */
    private void loadKeys(@NotNull Object config, @NotNull String prefix,
                          @NotNull Map<String, String> map) {
        if (config instanceof FileConfiguration fileConfig) {
            for (String key : fileConfig.getKeys(false)) {
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                Object value = fileConfig.get(key);

                if (value instanceof FileConfiguration || fileConfig.isConfigurationSection(key)) {
                    loadKeys(Objects.requireNonNull(fileConfig.getConfigurationSection(key)), fullKey, map);
                } else if (value != null) {
                    map.put(fullKey, value.toString());
                }
            }
        } else if (config instanceof org.bukkit.configuration.ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                Object value = section.get(key);

                if (section.isConfigurationSection(key)) {
                    loadKeys(Objects.requireNonNull(section.getConfigurationSection(key)), fullKey, map);
                } else if (value != null) {
                    map.put(fullKey, value.toString());
                }
            }
        }
    }

    /**
     * Récupère une traduction pour un joueur
     * @param player Joueur
     * @param key Clé de traduction
     * @param args Arguments de remplacement
     * @return Texte traduit
     */
    @NotNull
    public String get(@NotNull Player player, @NotNull String key, @Nullable Object... args) {
        String language = getPlayerLanguage(player);
        return get(language, key, args);
    }

    /**
     * Récupère une traduction pour un sender
     * @param sender CommandSender
     * @param key Clé de traduction
     * @param args Arguments de remplacement
     * @return Texte traduit
     */
    @NotNull
    public String get(@NotNull CommandSender sender, @NotNull String key, @Nullable Object... args) {
        if (sender instanceof Player player) {
            return get(player, key, args);
        }
        return get(configManager.getDefaultLanguage(), key, args);
    }

    /**
     * Récupère une traduction brute
     * @param language Langue
     * @param key Clé de traduction
     * @param args Arguments de remplacement
     * @return Texte traduit
     */
    @NotNull
    public String get(@NotNull String language, @NotNull String key, @Nullable Object... args) {
        Map<String, String> langMap = translations.get(language);

        if (langMap == null) {
            langMap = translations.get(configManager.getDefaultLanguage());
        }

        if (langMap == null) {
            LOGGER.warn("Aucune traduction disponible pour: {}", language);
            return key;
        }

        String message = langMap.getOrDefault(key, key);

        // Remplacer les arguments {0}, {1}, etc.
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null) {
                    message = message.replace("{" + i + "}", String.valueOf(args[i]));
                }
            }
        }

        return message;
    }

    /**
     * Récupère une traduction formatée en Component
     * @param player Joueur
     * @param key Clé de traduction
     * @param args Arguments de remplacement
     * @return Component formaté
     */
    @NotNull
    public Component getComponent(@NotNull Player player, @NotNull String key, @Nullable Object... args) {
        String message = get(player, key, args);
        try {
            return MINI_MESSAGE.deserialize(message);
        } catch (Exception e) {
            LOGGER.warn("Erreur lors du parsing MiniMessage pour '{}': {}", message, e.getMessage());
            return Component.text(message);
        }
    }

    /**
     * Récupère une traduction formatée en Component pour un sender
     * @param sender CommandSender
     * @param key Clé de traduction
     * @param args Arguments de remplacement
     * @return Component formaté
     */
    @NotNull
    public Component getComponent(@NotNull CommandSender sender, @NotNull String key, @Nullable Object... args) {
        String message = get(sender, key, args);
        try {
            return MINI_MESSAGE.deserialize(message);
        } catch (Exception e) {
            LOGGER.warn("Erreur lors du parsing MiniMessage pour '{}': {}", message, e.getMessage());
            return Component.text(message);
        }
    }

    /**
     * Envoie un message traduit à un joueur
     * @param player Joueur
     * @param key Clé de traduction
     * @param args Arguments de remplacement
     */
    public void send(@NotNull Player player, @NotNull String key, @Nullable Object... args) {
        player.sendMessage(getComponent(player, key, args));
    }

    /**
     * Envoie un message traduit à un sender
     * @param sender CommandSender
     * @param key Clé de traduction
     * @param args Arguments de remplacement
     */
    public void send(@NotNull CommandSender sender, @NotNull String key, @Nullable Object... args) {
        sender.sendMessage(getComponent(sender, key, args));
    }

    /**
     * Envoie un message avec préfixe
     * @param sender CommandSender
     * @param key Clé de traduction
     * @param args Arguments de remplacement
     */
    public void sendPrefixed(@NotNull CommandSender sender, @NotNull String key, @Nullable Object... args) {
        String prefix = configManager.getPrefix();
        String message = get(sender, key, args);
        try {
            Component component = MINI_MESSAGE.deserialize(prefix + " " + message);
            sender.sendMessage(component);
        } catch (Exception e) {
            LOGGER.warn("Erreur lors du parsing MiniMessage: {}", e.getMessage());
            sender.sendMessage(prefix + " " + message);
        }
    }

    /**
     * Détecte et enregistre la langue d'un joueur
     * @param player Joueur
     */
    public void detectPlayerLanguage(@NotNull Player player) {
        if (!configManager.isAutoDetectLanguage()) {
            return;
        }

        try {
            // Obtenir la locale du client
            Locale locale = player.locale();
            String language = locale.getLanguage();

            // Vérifier si supportée
            if (isSupportedLanguage(language)) {
                playerLanguages.put(player.getUniqueId(), language);
                LOGGER.debug("Langue détectée pour {}: {}", player.getName(), language);
            } else {
                // Langue par défaut
                playerLanguages.put(player.getUniqueId(),
                        configManager.getDefaultLanguage());
            }
        } catch (Exception e) {
            LOGGER.debug("Erreur détection langue pour {}: {}",
                    player.getName(), e.getMessage());
            playerLanguages.put(player.getUniqueId(),
                    configManager.getDefaultLanguage());
        }
    }

    /**
     * Récupère la langue d'un joueur
     * @param player Joueur
     * @return Code de langue
     */
    @NotNull
    public String getPlayerLanguage(@NotNull Player player) {
        return playerLanguages.getOrDefault(
                player.getUniqueId(),
                configManager.getDefaultLanguage()
        );
    }

    /**
     * Vérifie si une langue est supportée
     * @param language Code de langue
     * @return true si supportée
     */
    public boolean isSupportedLanguage(@NotNull String language) {
        for (String supported : SUPPORTED_LANGUAGES) {
            if (supported.equalsIgnoreCase(language)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Récupère le nombre de langues chargées
     * @return Nombre de langues
     */
    public int getLoadedLanguagesCount() {
        return translations.size();
    }

    /**
     * Nettoie le cache d'un joueur qui se déconnecte
     * @param player Joueur
     */
    public void clearPlayerCache(@NotNull Player player) {
        playerLanguages.remove(player.getUniqueId());
    }
}