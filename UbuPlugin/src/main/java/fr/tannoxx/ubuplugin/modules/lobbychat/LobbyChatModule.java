package fr.tannoxx.ubuplugin.modules.lobbychat;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.lobbychat.commands.LobbyCommand;
import fr.tannoxx.ubuplugin.modules.lobbychat.listeners.ChatReplacementListener;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LobbyChatModule extends Module {

    private final Map<String, String> chatReplacements = new HashMap<>();

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

        // Enregistrer le listener pour les remplacements de chat
        plugin.getServer().getPluginManager().registerEvents(
                new ChatReplacementListener(this), plugin
        );

        info("Module LobbyChat activé avec " + chatReplacements.size() + " remplacements");
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

    @NotNull
    @Override
    public String getDescription() {
        return "Gestion du lobby et remplacements de chat";
    }

    /**
     * Charge les remplacements de chat depuis la configuration
     */
    private void loadChatReplacements() {
        chatReplacements.clear();

        if (getConfigManager().contains("lobbychat.chat-replacements")) {
            for (String key : getConfigManager().getKeys("lobbychat.chat-replacements")) {
                String replacement = getConfigManager().getString(
                        "lobbychat.chat-replacements." + key, ""
                );
                if (!replacement.isEmpty()) {
                    chatReplacements.put(":" + key + ":", replacement);
                }
            }
        }

        debug("Chargé " + chatReplacements.size() + " remplacements de chat");
    }

    /**
     * Recharge les remplacements de chat
     */
    public void reloadReplacements() {
        loadChatReplacements();
        info("Remplacements de chat rechargés: " + chatReplacements.size());
    }

    /**
     * Retourne la map des remplacements de chat
     */
    public Map<String, String> getChatReplacements() {
        return chatReplacements;
    }
}