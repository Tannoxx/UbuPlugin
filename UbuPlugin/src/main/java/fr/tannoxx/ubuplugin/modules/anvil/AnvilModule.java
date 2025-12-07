package fr.tannoxx.ubuplugin.modules.anvil;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.anvil.listeners.AnvilListener;
import org.jetbrains.annotations.NotNull;

public class AnvilModule extends Module {
    
    public AnvilModule(@NotNull UbuPlugin plugin, @NotNull ModuleManager moduleManager) {
        super(plugin, moduleManager);
    }
    
    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(new AnvilListener(this), plugin);
        info("Module Anvil activé - Limite \"Too Expensive\" supprimée");
    }
    
    @Override
    public void onDisable() {
        info("Module Anvil désactivé");
    }
    
    @NotNull
    @Override
    public String getName() {
        return "Anvil";
    }
    
    @NotNull
    @Override
    public String getDescription() {
        return "Supprime la limite \"Too Expensive\" des enclumes";
    }
}