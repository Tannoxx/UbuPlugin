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
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Module gérant tous les enchantements customs du plugin
 * Thread-safe avec caches Caffeine
 *
 * @author Tannoxx
 * @version 2.0.2
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
    private NamespacedKey veinminerKey; // ✅ AJOUTÉ

    // Enchantements chargés
    private volatile Enchantment timberEnchantment;
    private volatile Enchantment magneticEnchantment;
    private volatile Enchantment experienceEnchantment;
    private volatile Enchantment explosiveEnchantment;
    private volatile Enchantment dashEnchantment;
    private volatile Enchantment soulboundEnchantment;
    private volatile Enchantment autoRepairEnchantment;
    private volatile Enchantment beaconatorEnchantment;
    private volatile Enchantment veinminerEnchantment; // ✅ AJOUTÉ

    // Caches thread-safe
    private Cache<UUID, Long> timberCooldowns;
    private Cache<UUID, Long> dashCooldowns;
    private Cache<UUID, Long> veinminerCooldowns; // ✅ AJOUTÉ
    private Cache<UUID, Boolean> magneticToggles;
    private Cache<UUID, Boolean> timberToggles;
    private Cache<UUID, Boolean> excavatorToggles;
    private Cache<UUID, Boolean> veinminerToggles; // ✅ AJOUTÉ

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
        veinminerKey = new NamespacedKey(plugin, "veinminer"); // ✅ AJOUTÉ
    }

    @Override
    public void onEnable() {
        // 1. Initialiser les caches AVANT tout
        initializeCaches();

        // 2. Charger les enchantements de manière SYNCHRONE
        loadEnchantmentsSync();

        // 3. Vérifier que les enchantements sont chargés
        if (timberEnchantment == null) {
            warn("✗ Timber non trouvé - Le datapack est-il installé ?");
        }
        if (magneticEnchantment == null) {
            warn("✗ Magnetic non trouvé - Le datapack est-il installé ?");
        }
        if (veinminerEnchantment == null) {
            warn("✗ Veinminer non trouvé - Le datapack est-il installé ?");
        }

        // 4. Enregistrer les listeners
        registerListeners();

        // 5. Enregistrer les commandes
        registerCommands();

        // 6. Démarrer les tasks récurrentes
        startTasks();

        info("Module Enchantements activé (9 enchantements)"); // ✅ 8 -> 9
    }

    private void loadEnchantmentsSync() {
        timberEnchantment = Enchantment.getByKey(timberKey);
        magneticEnchantment = Enchantment.getByKey(magneticKey);
        experienceEnchantment = Enchantment.getByKey(experienceKey);
        explosiveEnchantment = Enchantment.getByKey(explosiveKey);
        dashEnchantment = Enchantment.getByKey(dashKey);
        soulboundEnchantment = Enchantment.getByKey(soulboundKey);
        autoRepairEnchantment = Enchantment.getByKey(autoRepairKey);
        beaconatorEnchantment = Enchantment.getByKey(beaconatorKey);
        veinminerEnchantment = Enchantment.getByKey(veinminerKey); // ✅ AJOUTÉ

        if (timberEnchantment != null) info("✓ Timber chargé");
        if (magneticEnchantment != null) info("✓ Magnetic chargé");
        if (experienceEnchantment != null) info("✓ Experience chargé");
        if (explosiveEnchantment != null) info("✓ Explosive chargé");
        if (dashEnchantment != null) info("✓ Dash chargé");
        if (soulboundEnchantment != null) info("✓ Soulbound chargé");
        if (autoRepairEnchantment != null) info("✓ Auto-Repair chargé");
        if (beaconatorEnchantment != null) info("✓ Beaconator chargé");
        if (veinminerEnchantment != null) info("✓ Veinminer chargé"); // ✅ AJOUTÉ
    }

    @Override
    public void onDisable() {
        // Arrêter les tasks
        plugin.getServer().getScheduler().cancelTasks(plugin);

        // Nettoyer les caches
        if (timberCooldowns != null) timberCooldowns.invalidateAll();
        if (dashCooldowns != null) dashCooldowns.invalidateAll();
        if (veinminerCooldowns != null) veinminerCooldowns.invalidateAll(); // ✅ AJOUTÉ
        if (magneticToggles != null) magneticToggles.invalidateAll();
        if (timberToggles != null) timberToggles.invalidateAll();
        if (excavatorToggles != null) excavatorToggles.invalidateAll();
        if (veinminerToggles != null) veinminerToggles.invalidateAll(); // ✅ AJOUTÉ

        // Cleanup des listeners
        if (beaconatorListener != null) beaconatorListener.cleanup();

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

        // ✅ AJOUTÉ: Cache pour Veinminer cooldowns
        veinminerCooldowns = Caffeine.newBuilder()
                .expireAfterWrite(cooldownTTL, TimeUnit.SECONDS)
                .maximumSize(200)
                .build();

        magneticToggles = Caffeine.newBuilder()
                .maximumSize(500)
                .build();

        timberToggles = Caffeine.newBuilder()
                .maximumSize(500)
                .build();

        excavatorToggles = Caffeine.newBuilder()
                .maximumSize(500)
                .build();

        // ✅ AJOUTÉ: Cache pour Veinminer toggle
        veinminerToggles = Caffeine.newBuilder()
                .maximumSize(500)
                .build();
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

        // ✅ AJOUTÉ: Veinminer
        if (getConfigManager().getBoolean("enchants.veinminer.enabled", true)) {
            plugin.getServer().getPluginManager().registerEvents(
                    new VeinminerListener(this), plugin);
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
        Objects.requireNonNull(plugin.getCommand("veinminer")).setExecutor(new VeinminerCommand(this)); // ✅ AJOUTÉ

        // Commande /toggle
        ToggleCommand toggleCommand = new ToggleCommand(this);
        Objects.requireNonNull(plugin.getCommand("toggle")).setExecutor(toggleCommand);
        Objects.requireNonNull(plugin.getCommand("toggle")).setTabCompleter(toggleCommand);
    }

    private void startTasks() {
        // Auto-Repair task
        if (getConfigManager().getBoolean("enchants.autorepair.enabled", true)) {
            int interval = getConfigManager().getInt("enchants.autorepair.repair-interval", 10) * 20;
            AutoRepairListener autoRepairListener = new AutoRepairListener(this);
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

    // Getters thread-safe
    @Nullable
    public Enchantment getTimberEnchantment() { return timberEnchantment; }

    @Nullable
    public Enchantment getMagneticEnchantment() { return magneticEnchantment; }

    @Nullable
    public Enchantment getExperienceEnchantment() { return experienceEnchantment; }

    @Nullable
    public Enchantment getExplosiveEnchantment() { return explosiveEnchantment; }

    @Nullable
    public Enchantment getDashEnchantment() { return dashEnchantment; }

    @Nullable
    public Enchantment getSoulboundEnchantment() { return soulboundEnchantment; }

    @Nullable
    public Enchantment getAutoRepairEnchantment() { return autoRepairEnchantment; }

    @Nullable
    public Enchantment getBeaconatorEnchantment() { return beaconatorEnchantment; }

    @Nullable
    public Enchantment getVeinminerEnchantment() { return veinminerEnchantment; } // ✅ AJOUTÉ

    @NotNull
    public Cache<UUID, Long> getTimberCooldowns() { return timberCooldowns; }

    @NotNull
    public Cache<UUID, Long> getDashCooldowns() { return dashCooldowns; }

    @NotNull
    public Cache<UUID, Long> getVeinminerCooldowns() { return veinminerCooldowns; } // ✅ AJOUTÉ

    @NotNull
    public Cache<UUID, Boolean> getMagneticToggles() { return magneticToggles; }

    @NotNull
    public Cache<UUID, Boolean> getTimberToggles() { return timberToggles; }

    @NotNull
    public Cache<UUID, Boolean> getExcavatorToggles() { return excavatorToggles; }

    @NotNull
    public Cache<UUID, Boolean> getVeinminerToggles() { return veinminerToggles; } // ✅ AJOUTÉ
}