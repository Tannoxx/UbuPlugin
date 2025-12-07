package fr.tannoxx.ubuplugin.modules.lobbychat;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.lobbychat.commands.*;
import fr.tannoxx.ubuplugin.modules.lobbychat.listeners.*;
import org.jetbrains.annotations.NotNull;

public class LobbyChatModule extends Module {

    public LobbyChatModule(@NotNull UbuPlugin plugin, @NotNull ModuleManager moduleManager) {
        super(plugin, moduleManager);
    }

    @Override
    public void onEnable() {
        // Enregistrer les commandes
        plugin.getCommand("lobby").setExecutor(new LobbyCommand(this));
        plugin.getCommand("hub").setExecutor(new LobbyCommand(this));

        // Enregistrer le listener chat
        plugin.getServer().getPluginManager().registerEvents(new ChatReplacementListener(this), plugin);

        info("Module LobbyChat activé");
    }

    @Override
    public void onDisable() {
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
        return "Gère le lobby et les remplacements de chat";
    }
}