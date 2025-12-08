package fr.tannoxx.ubuplugin.modules.anvil.listeners;

import fr.tannoxx.ubuplugin.modules.anvil.AnvilModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

public record AnvilListener(AnvilModule module) implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {

        if (!module.getConfigManager().getBoolean("anvil.remove-too-expensive", true)) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null) return;

        // Utiliser AnvilView au lieu des méthodes dépréciées
        AnvilView anvilView = event.getView();

        int repairCost = anvilView.getRepairCost();
        int maxCost = module.getConfigManager().getInt("anvil.max-cost", 0);

        // Si max-cost = 0, pas de limite
        if (maxCost > 0 && repairCost > maxCost) {
            anvilView.setRepairCost(maxCost);
        }

        // Avertissement si coût élevé
        if (module.getConfigManager().getBoolean("anvil.high-cost-warning.enabled", true)) {
            int threshold = module.getConfigManager().getInt("anvil.high-cost-warning.threshold", 50);
            if (repairCost > threshold && event.getView().getPlayer() instanceof Player player) {
                module.getTranslationManager().send(player, "anvil.high-cost-warning", repairCost);
            }
        }
    }
}