package fr.tannoxx.ubuplugin.modules.lobbychat.listeners;

import fr.tannoxx.ubuplugin.modules.lobbychat.LobbyChatModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;

public record ChatReplacementListener(LobbyChatModule module) implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        Map<String, String> replacements = module.getChatReplacements();

        // Remplacer tous les patterns configurés
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        event.setMessage(message);
    }
}