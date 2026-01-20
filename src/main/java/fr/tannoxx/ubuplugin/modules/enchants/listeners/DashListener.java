package fr.tannoxx.ubuplugin.modules.enchants.listeners;

import fr.tannoxx.ubuplugin.modules.enchants.EnchantsModule;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener pour Dash avec cleanup automatique et niveau 3 invulnérable
 * Thread-safe avec ConcurrentHashMap
 * <p>
 * ✅ FIX v2.0.4: Particules trail niveau 3 supprimées
 */
public class DashListener implements Listener {

    private final EnchantsModule module;
    private final Map<UUID, Long> lastSneakTime = new ConcurrentHashMap<>();
    private final Set<UUID> invulnerablePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> justDashed = ConcurrentHashMap.newKeySet();

    public DashListener(@NotNull EnchantsModule module) {
        this.module = module;

        // Scheduled cleanup toutes les 60 secondes
        module.plugin.getServer().getScheduler().runTaskTimer(
                module.plugin, this::cleanup, 1200L, 1200L
        );
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

            performDash(player, level);
            lastSneakTime.remove(uuid);
        } else {
            lastSneakTime.put(uuid, currentTime);
        }
    }

    private void performDash(@NotNull Player player, int level) {
        UUID uuid = player.getUniqueId();

        Vector direction = player.getLocation().getDirection();
        direction.normalize();

        double speed = switch (level) {
            case 1 -> module.getConfigManager().getDouble("enchants.dash.speed.level-1", 1.5);
            case 2 -> module.getConfigManager().getDouble("enchants.dash.speed.level-2", 2.0);
            case 3 -> module.getConfigManager().getDouble("enchants.dash.speed.level-3", 2.5);
            default -> 1.5;
        };

        Vector velocity = direction.multiply(speed);

        justDashed.add(uuid);
        player.setVelocity(velocity);

        module.plugin.getServer().getScheduler().runTaskLater(
                module.plugin,
                () -> justDashed.remove(uuid),
                2L
        );

        // Effets visuels et sonores (particules basiques seulement)
        Location loc = player.getLocation();
        World world = player.getWorld();

        world.spawnParticle(Particle.CLOUD, loc, 20, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.CRIT, loc, 15, 0.3, 0.3, 0.3, 0.1);

        // ✅ FIX: Particules soul fire flame supprimées pour niveau 3
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);

        // Appliquer cooldown
        int cooldown = switch (level) {
            case 1 -> module.getConfigManager().getInt("enchants.dash.cooldown.level-1", 10);
            case 2 -> module.getConfigManager().getInt("enchants.dash.cooldown.level-2", 5);
            case 3 -> module.getConfigManager().getInt("enchants.dash.cooldown.level-3", 3);
            default -> 10;
        };
        module.getDashCooldowns().put(uuid, System.currentTimeMillis() + cooldown * 1000L);

        // Niveau 3 : Invulnérabilité (SANS particules trail)
        if (level == 3) {
            applyLevel3Effects(player);
        }
    }

    /**
     * ✅ FIX: Invulnérabilité niveau 3 SANS particules trail
     */
    private void applyLevel3Effects(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        invulnerablePlayers.add(uuid);

        // Effet de résistance
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE,
                30, // 1.5 secondes
                4,  // Niveau 5
                false,
                false,
                true
        ));

        // ✅ FIX: Trail de particules complètement supprimé
        // Juste un timer pour retirer l'invulnérabilité
        new BukkitRunnable() {
            @Override
            public void run() {
                invulnerablePlayers.remove(uuid);
            }
        }.runTaskLater(module.plugin, 30L); // 1.5 secondes
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        if (invulnerablePlayers.contains(uuid)) {
            event.setCancelled(true);
            module.debug("Dash niveau 3: Dégâts annulés pour {}", player.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerVelocity(@NotNull PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (justDashed.contains(uuid)) {
            return;
        }

        if (invulnerablePlayers.contains(uuid)) {
            event.setCancelled(true);
            module.debug("Dash niveau 3: Knockback bloqué pour {}", player.getName());
        }
    }

    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        lastSneakTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 2000
        );

        invulnerablePlayers.removeIf(uuid ->
                Bukkit.getPlayer(uuid) == null || !Objects.requireNonNull(Bukkit.getPlayer(uuid)).isOnline()
        );

        justDashed.removeIf(uuid ->
                Bukkit.getPlayer(uuid) == null || !Objects.requireNonNull(Bukkit.getPlayer(uuid)).isOnline()
        );
    }
}