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
 * Module Anti-AFK
 * Kick les joueurs inactifs après 5 minutes
 *
 * @author Tannoxx
 * @version 2.0.0
 */
public class AntiAFKModule extends Module {

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
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

        info("Module Anti-AFK activé - Kick après 5 minutes d'inactivité");
    }

    @Override
    public void onDisable() {
        // Arrêter la task
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // Nettoyer les caches
        lastActivity.clear();
        warned.clear();

        info("Module Anti-AFK désactivé");
    }

    @NotNull
    @Override
    public String getName() {
        return "AntiAFK";
    }

    /**
     * Met à jour l'activité d'un joueur
     */
    public void updateActivity(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        warned.remove(uuid); // Retirer l'avertissement si le joueur bouge
    }

    /**
     * Démarre la task de vérification AFK
     */
    private void startCheckTask() {
        int checkInterval = getConfigManager().getInt("antiafk.check-interval", 20) * 20; // En ticks

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            int afkTime = getConfigManager().getInt("antiafk.afk-time", 300); // En secondes
            int warnTime = getConfigManager().getInt("antiafk.warn-time", 270); // En secondes (4min30)

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                // Ignorer si pas d'activité enregistrée
                if (!lastActivity.containsKey(uuid)) {
                    lastActivity.put(uuid, currentTime);
                    continue;
                }

                long lastActive = lastActivity.get(uuid);
                long inactiveSeconds = (currentTime - lastActive) / 1000;

                // Kick si AFK depuis trop longtemps
                if (inactiveSeconds >= afkTime) {
                    kickPlayer(player);
                    continue;
                }

                // Avertir si proche du kick
                if (inactiveSeconds >= warnTime && !warned.containsKey(uuid)) {
                    warnPlayer(player, afkTime - (int) inactiveSeconds);
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
        String kickMessage = getTranslationManager().get(player, "antiafk.kick-message");

        // Notifier les admins
        if (getConfigManager().getBoolean("antiafk.notify-admins", true)) {
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("ubuplugin.admin")) {
                    getTranslationManager().send(admin, "antiafk.admin-notification", player.getName());
                }
            }
        }

        // Kick le joueur
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.kick(getTranslationManager().getComponent(player, "antiafk.kick-message"));
        });

        info("Joueur {} kick pour inactivité", player.getName());

        // Nettoyer
        lastActivity.remove(player.getUniqueId());
        warned.remove(player.getUniqueId());
    }

    /**
     * Nettoie les données d'un joueur déconnecté
     */
    public void cleanupPlayer(@NotNull UUID uuid) {
        lastActivity.remove(uuid);
        warned.remove(uuid);
    }
}