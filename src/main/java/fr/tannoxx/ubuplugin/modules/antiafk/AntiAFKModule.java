package fr.tannoxx.ubuplugin.modules.antiafk;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.antiafk.listeners.AFKListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module Anti-AFK amélioré
 * Kick les joueurs inactifs avec détection intelligente
 *
 * @author Tannoxx
 * @version 3.0.0
 */
public class AntiAFKModule extends Module {

    private final Map<UUID, PlayerActivityData> activityData = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> warned = new ConcurrentHashMap<>();
    private int taskId = -1;

    public AntiAFKModule(@NotNull UbuPlugin plugin, @NotNull ModuleManager moduleManager) {
        super(plugin, moduleManager);
    }

    @Override
    public void onEnable() {
        // Enregistrer le listener
        plugin.getServer().getPluginManager().registerEvents(new AFKListener(this), plugin);

        // Démarrer la task de vérification
        startCheckTask();

        info("Module Anti-AFK activé - Détection intelligente des patterns");
    }

    @Override
    public void onDisable() {
        // Arrêter la task
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // Nettoyer les caches
        activityData.clear();
        warned.clear();

        info("Module Anti-AFK désactivé");
    }

    @NotNull
    @Override
    public String getName() {
        return "AntiAFK";
    }

    /**
     * Enregistre une activité forte (preuve réelle d'activité)
     */
    public void recordStrongActivity(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        PlayerActivityData data = activityData.computeIfAbsent(uuid, k -> new PlayerActivityData());

        data.lastStrongActivity = System.currentTimeMillis();
        data.weakActivityCount = 0; // Reset le compteur d'activités faibles
        data.lastActionType = null; // Reset le pattern
        data.suspiciousPatternCount = 0; // Reset les patterns suspects

        warned.remove(uuid); // Retirer l'avertissement

        debug("Activité forte détectée pour {}", player.getName());
    }

    /**
     * Enregistre une activité faible (potentiellement automatisable)
     */
    public void recordWeakActivity(@NotNull Player player, @NotNull String actionType, double x, double y, double z) {
        UUID uuid = player.getUniqueId();
        PlayerActivityData data = activityData.computeIfAbsent(uuid, k -> new PlayerActivityData());

        long currentTime = System.currentTimeMillis();
        long timeSinceLastWeak = currentTime - data.lastWeakActivity;

        // Détecter les patterns suspects
        boolean isSuspicious = false;

        // Pattern 1: Actions trop rapides et identiques (< 1.5 secondes)
        if (timeSinceLastWeak < 1500 && actionType.equals(data.lastActionType)) {
            data.suspiciousPatternCount++;
            isSuspicious = true;
        }

        // Pattern 2: Mouvements répétitifs entre mêmes positions
        if (actionType.equals("MOVE") && data.lastActionType != null && data.lastActionType.equals("MOVE")) {
            double distance = Math.sqrt(
                    Math.pow(x - data.lastX, 2) +
                            Math.pow(y - data.lastY, 2) +
                            Math.pow(z - data.lastZ, 2)
            );

            // Si le joueur alterne entre 2 positions proches (< 2 blocs)
            if (distance < 2.0 && distance > 0.01) {
                data.suspiciousPatternCount++;
                isSuspicious = true;
            }
        }

        // Pattern 3: Rotations parfaitement régulières
        if (actionType.equals("LOOK") && timeSinceLastWeak > 900 && timeSinceLastWeak < 1100) {
            data.suspiciousPatternCount++;
            isSuspicious = true;
        }

        // Si trop de patterns suspects détectés (> 5), on ignore cette activité
        if (data.suspiciousPatternCount > 5) {
            debug("Patterns suspects détectés pour {} - Activité faible ignorée", player.getName());
            return;
        }

        // Incrémenter le compteur d'activités faibles
        data.weakActivityCount++;

        // Si trop d'activités faibles consécutives (> 15), on les ignore
        if (data.weakActivityCount > 15) {
            debug("Trop d'activités faibles pour {} - Ignoré", player.getName());
            return;
        }

        // Mettre à jour les données
        data.lastWeakActivity = currentTime;
        data.lastActionType = actionType;
        data.lastX = x;
        data.lastY = y;
        data.lastZ = z;

        // Réduire le compteur de patterns suspects si l'action semble légitime
        if (!isSuspicious && data.suspiciousPatternCount > 0) {
            data.suspiciousPatternCount--;
        }

        warned.remove(uuid); // Retirer l'avertissement même pour activité faible
    }

    /**
     * Démarre la task de vérification AFK
     */
    private void startCheckTask() {
        int checkInterval = getConfigManager().getInt("antiafk.check-interval", 20) * 20; // En ticks

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            int afkTime = getConfigManager().getInt("antiafk.afk-time", 300) * 1000; // En ms
            int warnTime = getConfigManager().getInt("antiafk.warn-time", 270) * 1000; // En ms

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                // Créer les données si nécessaire
                PlayerActivityData data = activityData.computeIfAbsent(uuid, k -> {
                    PlayerActivityData newData = new PlayerActivityData();
                    newData.lastStrongActivity = currentTime;
                    return newData;
                });

                // Calculer le temps d'inactivité basé sur la dernière activité FORTE
                long inactiveTime = currentTime - data.lastStrongActivity;

                // Kick si AFK depuis trop longtemps
                if (inactiveTime >= afkTime) {
                    kickPlayer(player);
                    continue;
                }

                // Avertir si proche du kick
                if (inactiveTime >= warnTime && !warned.containsKey(uuid)) {
                    int secondsLeft = (int) ((afkTime - inactiveTime) / 1000);
                    warnPlayer(player, secondsLeft);
                    warned.put(uuid, true);
                }
            }
        }, checkInterval, checkInterval).getTaskId();
    }

    /**
     * Avertit un joueur qu'il va être kick pour AFK
     */
    private void warnPlayer(@NotNull Player player, int secondsLeft) {
        getTranslationManager().send(player, "antiafk.warning", String.valueOf(secondsLeft));
        debug("Joueur {} averti - Kick dans {}s", player.getName(), secondsLeft);
    }

    /**
     * Kick un joueur pour inactivité
     */
    private void kickPlayer(@NotNull Player player) {
        // Notifier les admins
        if (getConfigManager().getBoolean("antiafk.notify-admins", true)) {
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("ubuplugin.admin")) {
                    getTranslationManager().send(admin, "antiafk.admin-notification", player.getName());
                }
            }
        }

        // Kick le joueur
        Bukkit.getScheduler().runTask(plugin, () ->
                player.kick(getTranslationManager().getComponent(player, "antiafk.kick-message"))
        );

        info("Joueur {} kick pour inactivité", player.getName());

        // Nettoyer
        activityData.remove(player.getUniqueId());
        warned.remove(player.getUniqueId());
    }

    /**
     * Nettoie les données d'un joueur déconnecté
     */
    public void cleanupPlayer(@NotNull UUID uuid) {
        activityData.remove(uuid);
        warned.remove(uuid);
    }

    /**
     * Classe interne pour stocker les données d'activité d'un joueur
     */
    private static class PlayerActivityData {
        long lastStrongActivity = System.currentTimeMillis();
        long lastWeakActivity = 0;
        int weakActivityCount = 0;
        int suspiciousPatternCount = 0;
        String lastActionType = null;
        double lastX = 0;
        double lastY = 0;
        double lastZ = 0;
    }
}