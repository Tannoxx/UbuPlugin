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
 * Niveau 3 : Le joueur devient invulnérable et inarrêtable pendant 1.5s
 */
public class DashListener implements Listener {

    private final EnchantsModule module;
    private final Map<UUID, Long> lastSneakTime = new ConcurrentHashMap<>();

    // Joueurs actuellement en dash niveau 3 (invulnérables)
    private final Set<UUID> invulnerablePlayers = ConcurrentHashMap.newKeySet();

    // Joueurs qui viennent de lancer un dash (pour ignorer le velocity event)
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

            // Effectuer le dash
            performDash(player, level);

            // Appliquer cooldown
            int cooldown = switch (level) {
                case 1 -> module.getConfigManager().getInt("enchants.dash.cooldown.level-1", 10);
                case 2 -> module.getConfigManager().getInt("enchants.dash.cooldown.level-2", 5);
                case 3 -> module.getConfigManager().getInt("enchants.dash.cooldown.level-3", 3);
                default -> 10;
            };
            module.getDashCooldowns().put(uuid, currentTime + cooldown * 1000L);

            lastSneakTime.remove(uuid);
        } else {
            lastSneakTime.put(uuid, currentTime);
        }
    }

    private void performDash(@NotNull Player player, int level) {
        UUID uuid = player.getUniqueId();

        Vector direction = player.getLocation().getDirection();
        direction.setY(0);
        direction.normalize();

        double speed = switch (level) {
            case 1 -> module.getConfigManager().getDouble("enchants.dash.speed.level-1", 1.5);
            case 2 -> module.getConfigManager().getDouble("enchants.dash.speed.level-2", 2.0);
            case 3 -> module.getConfigManager().getDouble("enchants.dash.speed.level-3", 2.5);
            default -> 1.5;
        };

        Vector velocity = direction.multiply(speed);
        velocity.setY(0.2);

        // Marquer le joueur comme venant de dasher (pour ignorer velocity event)
        justDashed.add(uuid);

        player.setVelocity(velocity);

        // Retirer le flag après 2 ticks (le temps que la vélocité soit appliquée)
        module.plugin.getServer().getScheduler().runTaskLater(
                module.plugin,
                () -> justDashed.remove(uuid),
                2L
        );

        // Effets visuels et sonores
        Location loc = player.getLocation();
        World world = player.getWorld();

        world.spawnParticle(Particle.CLOUD, loc, 20, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.CRIT, loc, 15, 0.3, 0.3, 0.3, 0.1);

        // Niveau 3 : Particules Soul Fire Flame
        if (level == 3) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 30, 0.3, 0.3, 0.3, 0.05);
        }

        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);

        // Niveau 3 : Invulnérabilité et effets spéciaux
        if (level == 3) {
            applyLevel3Effects(player);
        }
    }

    /**
     * Applique les effets du Dash niveau 3 : invulnérabilité et inarrêtable pendant 1.5s
     */
    private void applyLevel3Effects(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        // Marquer comme invulnérable
        invulnerablePlayers.add(uuid);

        // Effet de résistance visuelle
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE,
                30, // 1.5 secondes (30 ticks)
                4, // Niveau 5 (résistance quasi-totale)
                false,
                false,
                true
        ));

        // Trail de particules pendant le dash
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 30) { // 1.5 secondes
                    // Retirer l'invulnérabilité
                    invulnerablePlayers.remove(uuid);
                    cancel();
                    return;
                }

                // Particules de trail
                Location loc = player.getLocation();
                player.getWorld().spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        loc.add(0, 0.5, 0),
                        5,
                        0.2, 0.2, 0.2,
                        0.02
                );

                ticks++;
            }
        }.runTaskTimer(module.plugin, 0L, 1L);
    }

    /**
     * Annule tous les dégâts pour les joueurs en dash niveau 3
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        // Si le joueur est en dash niveau 3, annuler tous les dégâts
        if (invulnerablePlayers.contains(uuid)) {
            event.setCancelled(true);
            module.debug("Dash niveau 3: Dégâts annulés pour {}", player.getName());
        }
    }

    /**
     * Empêche le knockback pendant le dash niveau 3 (inarrêtable)
     * MAIS permet la vélocité initiale du dash
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerVelocity(@NotNull PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Si le joueur vient de dasher, laisser passer la vélocité
        if (justDashed.contains(uuid)) {
            return;
        }

        // Si le joueur est en dash niveau 3, bloquer les vélocités externes
        if (invulnerablePlayers.contains(uuid)) {
            // Annuler complètement les vélocités externes (knockback, explosions, etc.)
            event.setCancelled(true);
            module.debug("Dash niveau 3: Knockback bloqué pour {}", player.getName());
        }
    }

    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        lastSneakTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 2000
        );

        // Cleanup des joueurs déconnectés qui seraient restés dans invulnerablePlayers
        invulnerablePlayers.removeIf(uuid ->
                Bukkit.getPlayer(uuid) == null || !Objects.requireNonNull(Bukkit.getPlayer(uuid)).isOnline()
        );

        // Cleanup justDashed (sécurité)
        justDashed.removeIf(uuid ->
                Bukkit.getPlayer(uuid) == null || !Objects.requireNonNull(Bukkit.getPlayer(uuid)).isOnline()
        );
    }
}