package fr.tannoxx.ubuplugin.modules.anvil.listeners;

import fr.tannoxx.ubuplugin.modules.anvil.AnvilModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

public class AnvilListener implements Listener {
    
    private final AnvilModule module;
    
    public AnvilListener(AnvilModule module) {
        this.module = module;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        
        if (!module.getConfigManager().getBoolean("anvil.remove-too-expensive", true)) {
            return;
        }
        
        ItemStack result = event.getResult();
        if (result == null) return;
        
        int repairCost = inventory.getRepairCost();
        int maxCost = module.getConfigManager().getInt("anvil.max-cost", 0);
        
        // Si max-cost = 0, pas de limite
        if (maxCost > 0 && repairCost > maxCost) {
            inventory.setRepairCost(maxCost);
        }
        
        // Avertissement si coût élevé
        if (module.getConfigManager().getBoolean("anvil.high-cost-warning.enabled", true)) {
            int threshold = module.getConfigManager().getInt("anvil.high-cost-warning.threshold", 50);
            if (repairCost > threshold && event.getView().getPlayer() instanceof org.bukkit.entity.Player player) {
                module.getTranslationManager().send(player, "anvil.high-cost-warning", repairCost);
            }
        }
    }
}