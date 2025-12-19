package fr.tannoxx.ubuplugin.modules.lobbychat;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.lobbychat.commands.LobbyCommand;
import fr.tannoxx.ubuplugin.modules.lobbychat.listeners.ChatReplacementListener;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module LobbyChat avec remplacements thread-safe
 */
public class LobbyChatModule extends Module {

    private final Map<String, String> chatReplacements = new ConcurrentHashMap<>();

    public LobbyChatModule(@NotNull UbuPlugin plugin, @NotNull ModuleManager moduleManager) {
        super(plugin, moduleManager);
    }

    @Override
    public void onEnable() {
        // Charger les remplacements depuis la config
        loadChatReplacements();

        // Enregistrer les commandes
        Objects.requireNonNull(plugin.getCommand("lobby")).setExecutor(new LobbyCommand(this));
        Objects.requireNonNull(plugin.getCommand("hub")).setExecutor(new LobbyCommand(this));

        // Enregistrer le listener
        plugin.getServer().getPluginManager().registerEvents(
                new ChatReplacementListener(this), plugin
        );

        info("Module LobbyChat activé avec {} remplacements", chatReplacements.size());
    }

    @Override
    public void onDisable() {
        chatReplacements.clear();
        info("Module LobbyChat désactivé");
    }

    @NotNull
    @Override
    public String getName() {
        return "LobbyChat";
    }

    private void loadChatReplacements() {
        chatReplacements.clear();

        String path = "lobbychat.chat-replacements.replacements";

        // Méthode 1 : Si getConfigManager retourne une ConfigurationSection
        if (getConfigManager().contains(path)) {
            ConfigurationSection section = getConfigManager().getConfigurationSection(path);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String replacement = section.getString(key, "");
                    if (!replacement.isEmpty()) {
                        chatReplacements.put(":" + key + ":", replacement);
                        debug("Ajouté : :{}: -> {}", key, replacement);
                    }
                }
            }
        }

        info("Chargé {} remplacements de chat", chatReplacements.size());
    }

    @NotNull
    public Map<String, String> getChatReplacements() {
        return chatReplacements;
    }
}