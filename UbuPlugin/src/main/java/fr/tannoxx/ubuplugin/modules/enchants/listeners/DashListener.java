package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DashListener implements Listener {

    private final EnchantsModule module;
    private final Map<UUID, Long> lastSneakTime = new HashMap<>();

    public DashListener(@NotNull EnchantsModule module) {
        this.module = module;
    }

    @EventHandler
    public void onPlayerSneak(@NotNull PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings == null || leggings.getType() == Material.AIR) return;

        Enchantment dash = module.getDashEnchantment();
        if (dash == null || !leggings.containsEnchantment(dash)) return;

        long currentTime = System.currentTimeMillis();
        Long lastSneak = lastSneakTime.get(uuid);

        int doubleSneakWindow = module.getConfigManager()
                .getInt("enchants.dash.double-sneak-window", 300);

        if (lastSneak != null && (currentTime - lastSneak) < doubleSneakWindow) {
            // Double-sneak détecté
            int level = leggings.getEnchantmentLevel(dash);

            // Vérifier cooldown
            Long cooldownEnd = module.getDashCooldowns().getIfPresent(uuid);
            if (cooldownEnd != null && currentTime < cooldownEnd) {
                long remaining = (cooldownEnd - currentTime) / 1000 + 1;
                module.getTranslationManager().send(player, "enchants.dash.cooldown", remaining);
                return;
            }

            // Effectuer le dash
            performDash(player, level);

            // Appliquer cooldown
            int cooldown = module.getConfigManager()
                    .getInt("enchants.dash.cooldown.level-" + level, level == 1 ? 10 : 5);
            module.getDashCooldowns().put(uuid, currentTime + cooldown * 1000L);

            lastSneakTime.remove(uuid);
        } else {
            lastSneakTime.put(uuid, currentTime);
        }
    }

    private void performDash(@NotNull Player player, int level) {
        Vector direction = player.getLocation().getDirection();
        direction.setY(0);
        direction.normalize();

        double speed = module.getConfigManager()
                .getDouble("enchants.dash.speed.level-" + level, level == 1 ? 1.5 : 2.0);

        Vector velocity = direction.multiply(speed);
        velocity.setY(0.2);

        player.setVelocity(velocity);

        // Effets
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 20, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().spawnParticle(Particle.CRIT, loc, 15, 0.3, 0.3, 0.3, 0.1);
        player.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);
    }

    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        lastSneakTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 1000
        );
    }
}