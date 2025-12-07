package fr.tannoxx.ubuplugin.modules.enchants;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.module.Module;
import fr.tannoxx.ubuplugin.common.module.ModuleManager;
import fr.tannoxx.ubuplugin.modules.enchants.commands.*;
import fr.tannoxx.ubuplugin.modules.enchants.listeners.*;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Module gérant tous les enchantements customs du plugin
 *
 * @author Tannoxx
 * @version 2.0.0
 */
public class EnchantsModule extends Module {

    // Keys des enchantements
    private NamespacedKey timberKey;
    private NamespacedKey magneticKey;
    private NamespacedKey experienceKey;
    private NamespacedKey explosiveKey;
    private NamespacedKey dashKey;
    private NamespacedKey soulboundKey;
    private NamespacedKey autoRepairKey;
    private NamespacedKey beaconatorKey;

    // Enchantements chargés
    private Enchantment timberEnchantment;
    private Enchantment magneticEnchantment;
    private Enchantment experienceEnchantment;
    private Enchantment explosiveEnchantment;
    private Enchantment dashEnchantment;
    private Enchantment soulboundEnchantment;
    private Enchantment autoRepairEnchantment;
    private Enchantment beaconatorEnchantment;

    // Caches
    private Cache<UUID, Long> timberCooldowns;
    private Cache<UUID, Long> dashCooldowns;
    private Cache<UUID, Boolean> magneticToggles;

    // Listeners
    private AutoRepairListener autoRepairListener;
    private BeaconatorListener beaconatorListener;

    public EnchantsModule(@NotNull UbuPlugin plugin, @NotNull ModuleManager moduleManager) {
        super(plugin, moduleManager);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        // Créer les NamespacedKeys
        timberKey = new NamespacedKey(plugin, "timber");
        magneticKey = new NamespacedKey(plugin, "magnetic");
        experienceKey = new NamespacedKey(plugin, "experience");
        explosiveKey = new NamespacedKey(plugin, "explosive");
        dashKey = new NamespacedKey(plugin, "dash");
        soulboundKey = new NamespacedKey(plugin, "soulbound");
        autoRepairKey = new NamespacedKey(plugin, "autorepair");
        beaconatorKey = new NamespacedKey(plugin, "beaconator");
    }

    @Override
    public void onEnable() {
        // Initialiser les caches
        initializeCaches();

        // Charger les enchantements depuis le datapack (avec délai)
        plugin.getServer().getScheduler().runTaskLater(plugin, this::loadEnchantments, 1L);

        // Enregistrer les listeners
        registerListeners();

        // Enregistrer les commandes
        registerCommands();

        // Démarrer les tasks récurrentes
        startTasks();

        info("Module Enchantements activé (8 enchantements)");
    }

    @Override
    public void onDisable() {
        // Arrêter les tasks
        plugin.getServer().getScheduler().cancelTasks(plugin);

        // Nettoyer les caches
        if (timberCooldowns != null) timberCooldowns.invalidateAll();
        if (dashCooldowns != null) dashCooldowns.invalidateAll();
        if (magneticToggles != null) magneticToggles.invalidateAll();

        info("Module Enchantements désactivé");
    }

    private void initializeCaches() {
        int cooldownTTL = getConfigManager().getInt("cache.types.enchantment-cooldowns.ttl", 60);

        timberCooldowns = Caffeine.newBuilder()
                .expireAfterWrite(cooldownTTL, TimeUnit.SECONDS)
                .maximumSize(200)
                .build();

        dashCooldowns = Caffeine.newBuilder()
                .expireAfterWrite(cooldownTTL, TimeUnit.SECONDS)
                .maximumSize(200)
                .build();

        magneticToggles = Caffeine.newBuilder()
                .maximumSize(500)
                .build();
    }

    private void loadEnchantments() {
        timberEnchantment = Enchantment.getByKey(timberKey);
        magneticEnchantment = Enchantment.getByKey(magneticKey);
        experienceEnchantment = Enchantment.getByKey(experienceKey);
        explosiveEnchantment = Enchantment.getByKey(explosiveKey);
        dashEnchantment = Enchantment.getByKey(dashKey);
        soulboundEnchantment = Enchantment.getByKey(soulboundKey);
        autoRepairEnchantment = Enchantment.getByKey(autoRepairKey);
        beaconatorEnchantment = Enchantment.getByKey(beaconatorKey);

        if (timberEnchantment != null) info("✓ Timber chargé");
        else error("✗ Timber non trouvé - Vérifiez le datapack");

        if (magneticEnchantment != null) info("✓ Magnetic chargé");
        if (experienceEnchantment != null) info("✓ Experience chargé");
        if (explosiveEnchantment != null) info("✓ Explosive chargé");
        if (dashEnchantment != null) info("✓ Dash chargé");
        if (soulboundEnchantment != null) info("✓ Soulbound chargé");
        if (autoRepairEnchantment != null) info("✓ Auto-Repair chargé");
        if (beaconatorEnchantment != null) info("✓ Beaconator chargé");
    }

    private void registerListeners() {
        // Timber
        if (getConfigManager().getBoolean("enchants.timber.enabled", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new TimberListener(this), plugin);
        }

        // Magnetic
        if (getConfigManager().getBoolean("enchants.magnetic.enabled", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new MagneticListener(this), plugin);
        }

        // Experience
        if (getConfigManager().getBoolean("enchants.experience.enabled", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new ExperienceListener(this), plugin);
        }

        // Explosive
        if (getConfigManager().getBoolean("enchants.explosive.enabled", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new ExplosiveListener(this), plugin);
        }

        // Dash
        if (getConfigManager().getBoolean("enchants.dash.enabled", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new DashListener(this), plugin);
        }

        // Soulbound
        if (getConfigManager().getBoolean("enchants.soulbound.enabled", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new SoulboundListener(this), plugin);
        }
    }

    private void registerCommands() {
        Objects.requireNonNull(plugin.getCommand("timber")).setExecutor(new TimberCommand(this));
        Objects.requireNonNull(plugin.getCommand("magnetic")).setExecutor(new MagneticCommand(this));
        Objects.requireNonNull(plugin.getCommand("experience")).setExecutor(new ExperienceCommand(this));
        Objects.requireNonNull(plugin.getCommand("explosive")).setExecutor(new ExplosiveCommand(this));
        Objects.requireNonNull(plugin.getCommand("dash")).setExecutor(new DashCommand(this));
        Objects.requireNonNull(plugin.getCommand("soulbound")).setExecutor(new SoulboundCommand(this));
        Objects.requireNonNull(plugin.getCommand("autorepair")).setExecutor(new AutoRepairCommand(this));
        Objects.requireNonNull(plugin.getCommand("beaconator")).setExecutor(new BeaconatorCommand(this));
    }

    private void startTasks() {
        // Auto-Repair task
        if (getConfigManager().getBoolean("enchants.autorepair.enabled", true)) {
            int interval = getConfigManager().getInt("enchants.autorepair.repair-interval", 10) * 20;
            autoRepairListener = new AutoRepairListener(this);
            plugin.getServer().getScheduler().runTaskTimer(plugin, autoRepairListener, interval, interval);
        }

        // Beaconator task
        if (getConfigManager().getBoolean("enchants.beaconator.enabled", true)) {
            int interval = getConfigManager().getInt("enchants.beaconator.check-interval", 5) * 20;
            beaconatorListener = new BeaconatorListener(this);
            plugin.getServer().getScheduler().runTaskTimer(plugin, beaconatorListener, interval, interval);
        }
    }

    @NotNull
    @Override
    public String getName() {
        return "Enchants";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Gère les enchantements customs du plugin";
    }

    // Getters
    public Enchantment getTimberEnchantment() { return timberEnchantment; }
    public Enchantment getMagneticEnchantment() { return magneticEnchantment; }
    public Enchantment getExperienceEnchantment() { return experienceEnchantment; }
    public Enchantment getExplosiveEnchantment() { return explosiveEnchantment; }
    public Enchantment getDashEnchantment() { return dashEnchantment; }
    public Enchantment getSoulboundEnchantment() { return soulboundEnchantment; }
    public Enchantment getAutoRepairEnchantment() { return autoRepairEnchantment; }
    public Enchantment getBeaconatorEnchantment() { return beaconatorEnchantment; }

    public Cache<UUID, Long> getTimberCooldowns() { return timberCooldowns; }
    public Cache<UUID, Long> getDashCooldowns() { return dashCooldowns; }
    public Cache<UUID, Boolean> getMagneticToggles() { return magneticToggles; }
}