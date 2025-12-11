package fr.tannoxx.ubuplugin.modules.anvil.listeners;

import fr.tannoxx.ubuplugin.modules.anvil.AnvilModule;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Repairable;

public record AnvilListener(AnvilModule module) implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!module.getConfigManager().getBoolean("anvil.remove-too-expensive", true)) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }

        // Accéder à l'inventaire de l'enclume
        AnvilInventory anvilInventory = event.getInventory();

        // Récupérer les items
        ItemStack firstItem = anvilInventory.getItem(0);  // Slot de gauche
        ItemStack secondItem = anvilInventory.getItem(1); // Slot de droite

        if (firstItem == null || firstItem.getType() == Material.AIR) {
            return;
        }

        // Calculer le coût de réparation
        int calculatedCost = calculateRepairCost(firstItem, secondItem, result);

        // Forcer un coût acceptable même si > 39
        int maxCost = module.getConfigManager().getInt("anvil.max-cost", 0);
        int finalCost;

        if (maxCost == 0) {
            // Pas de limite - accepter jusqu'à 100
            finalCost = Math.min(calculatedCost, 100);
        } else {
            // Limite personnalisée
            finalCost = Math.min(calculatedCost, maxCost);
        }

        // Modifier le coût de réparation du résultat
        if (result.getItemMeta() instanceof Repairable repairableMeta) {
            // Définir le nouveau coût de réparation
            repairableMeta.setRepairCost(finalCost);
            result.setItemMeta(repairableMeta);
            event.setResult(result);
        }

        // Avertissement si coût élevé
        if (module.getConfigManager().getBoolean("anvil.high-cost-warning.enabled", true)) {
            int threshold = module.getConfigManager().getInt("anvil.high-cost-warning.threshold", 50);

            if (finalCost > threshold && event.getViewers().getFirst() instanceof Player player) {
                module.getTranslationManager().send(player, "anvil.high-cost-warning", finalCost);
            }
        }
    }

    /**
     * Calcule le coût de réparation basé sur les items
     * Formule vanilla simplifiée
     */
    private int calculateRepairCost(ItemStack first, ItemStack second, ItemStack result) {
        int cost = 1; // Coût de base

        // Ajouter le coût des réparations précédentes du premier item
        if (first.getItemMeta() instanceof Repairable firstRepairable) {
            int repairCost = firstRepairable.getRepairCost();
            cost += (int) Math.pow(2, repairCost) - 1;
        }

        // Ajouter le coût du second item s'il existe
        if (second != null && second.getType() != Material.AIR) {
            if (second.getItemMeta() instanceof Repairable secondRepairable) {
                int repairCost = secondRepairable.getRepairCost();
                cost += (int) Math.pow(2, repairCost) - 1;
            }

            // Coût des enchantements combinés
            cost += second.getEnchantments().size() * 2;
        }

        // Coût des enchantements du premier item
        cost += first.getEnchantments().size();

        // Coût supplémentaire si renommage
        if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
            cost += 1;
        }

        return Math.max(1, cost);
    }
}