package fr.tannoxx.ubuplugin.modules.earthtools.listeners;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Listener pour protéger le GUI du leaderboard Uptime
 * Empêche les joueurs de prendre les items du GUI
 * <p>
 * ✅ FIX v2.0.3: Comparaison correcte du titre avec PlainTextSerializer
 */
public class UptimeGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        // Convertir le titre Component en texte brut pour comparaison
        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());

        // Vérifier si c'est notre GUI (le titre contient "Top Joueurs")
        if (title.contains("Top Joueurs") || title.contains("Temps de Jeu")) {
            // Bloquer TOUTES les interactions
            event.setCancelled(true);
        }
    }
}