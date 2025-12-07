package fr.tannoxx.ubuplugin.modules.ranks;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.ranks.commands.*;
import fr.tannoxx.ubuplugin.modules.ranks.data.RankDataManager;
import fr.tannoxx.ubuplugin.modules.ranks.listeners.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Module gérant le système de ranks et le chat
 *
 * @author Tannoxx
 * @version 2.0.0
 */
public class RanksModule extends Module {

    private RankDataManager rankDataManager;

    public RanksModule(@NotNull UbuPlugin plugin, @NotNull ModuleManager moduleManager) {
        super(plugin, moduleManager);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Initialiser le gestionnaire de données
        rankDataManager = new RankDataManager(this);
    }

    @Override
    public void onEnable() {
        // Créer les tables si nécessaire
        rankDataManager.createTables();

        // Enregistrer les listeners
        plugin.getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ChatListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new MuteListener(this), plugin);

        // Enregistrer les commandes
        Objects.requireNonNull(plugin.getCommand("rank")).setExecutor(new RankCommand(this));
        Objects.requireNonNull(plugin.getCommand("prefix")).setExecutor(new PrefixCommand(this));
        Objects.requireNonNull(plugin.getCommand("mute")).setExecutor(new MuteCommand(this));
        Objects.requireNonNull(plugin.getCommand("unmute")).setExecutor(new UnmuteCommand(this));

        info("Module Ranks activé");
    }

    @Override
    public void onDisable() {
        // Nettoyer les teams
        if (rankDataManager != null) {
            rankDataManager.cleanup();
        }
        info("Module Ranks désactivé");
    }

    @NotNull
    @Override
    public String getName() {
        return "Ranks";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Gère le système de ranks et le chat";
    }

    public RankDataManager getRankDataManager() {
        return rankDataManager;
    }
}