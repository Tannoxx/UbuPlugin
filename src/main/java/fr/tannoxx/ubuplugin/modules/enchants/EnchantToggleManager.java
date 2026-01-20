package fr.tannoxx.ubuplugin.modules.enchants;

import com.github.benmanes.caffeine.cache.Cache;
import fr.tannoxx.ubuplugin.common.database.DatabaseManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Gestionnaire de persistance pour les toggles d'enchantements
 * Sauvegarde dans la base de données pour conserver les préférences
 *
 * @author Tannoxx
 * @version 2.0.4
 */
public class EnchantToggleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnchantToggleManager.class);
    private final DatabaseManager databaseManager;

    public EnchantToggleManager(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        createToggleTable();
    }

    /**
     * Crée la table des toggles si elle n'existe pas
     */
    private void createToggleTable() {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS enchant_toggles (
                    uuid TEXT NOT NULL,
                    enchant_name TEXT NOT NULL,
                    enabled BOOLEAN DEFAULT TRUE,
                    PRIMARY KEY (uuid, enchant_name)
                );
                """)) {
            stmt.execute();
            LOGGER.debug("Table enchant_toggles créée/vérifiée");
        } catch (SQLException e) {
            LOGGER.error("Erreur lors de la création de la table enchant_toggles", e);
        }
    }

    /**
     * Charge tous les toggles d'un joueur dans les caches
     */
    public void loadPlayerToggles(@NotNull UUID uuid,
                                  @NotNull Cache<UUID, Boolean> timberToggle,
                                  @NotNull Cache<UUID, Boolean> magneticToggle,
                                  @NotNull Cache<UUID, Boolean> excavatorToggle,
                                  @NotNull Cache<UUID, Boolean> veinminerToggle) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT enchant_name, enabled FROM enchant_toggles WHERE uuid = ?")) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String enchantName = rs.getString("enchant_name");
                    boolean enabled = rs.getBoolean("enabled");

                    switch (enchantName.toLowerCase()) {
                        case "timber" -> timberToggle.put(uuid, enabled);
                        case "magnetic" -> magneticToggle.put(uuid, enabled);
                        case "excavator" -> excavatorToggle.put(uuid, enabled);
                        case "veinminer" -> veinminerToggle.put(uuid, enabled);
                    }
                }

                LOGGER.debug("Toggles chargés pour {}", uuid);
            } catch (SQLException e) {
                LOGGER.error("Erreur lors du chargement des toggles pour {}", uuid, e);
            }
        });
    }

    /**
     * Sauvegarde un toggle en base de données (asynchrone)
     */
    public void saveToggle(@NotNull UUID uuid, @NotNull String enchantName, boolean enabled) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO enchant_toggles (uuid, enchant_name, enabled) VALUES (?, ?, ?)")) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, enchantName.toLowerCase());
                stmt.setBoolean(3, enabled);
                stmt.executeUpdate();

                LOGGER.debug("Toggle sauvegardé: {} - {} = {}", uuid, enchantName, enabled);
            } catch (SQLException e) {
                LOGGER.error("Erreur lors de la sauvegarde du toggle {} pour {}", enchantName, uuid, e);
            }
        });
    }

    /**
     * Supprime tous les toggles d'un joueur (cleanup à la déconnexion)
     */
    public void clearPlayerToggles(@NotNull UUID uuid,
                                   @NotNull Cache<UUID, Boolean> timberToggle,
                                   @NotNull Cache<UUID, Boolean> magneticToggle,
                                   @NotNull Cache<UUID, Boolean> excavatorToggle,
                                   @NotNull Cache<UUID, Boolean> veinminerToggle) {
        // Nettoyer les caches
        timberToggle.invalidate(uuid);
        magneticToggle.invalidate(uuid);
        excavatorToggle.invalidate(uuid);
        veinminerToggle.invalidate(uuid);

        LOGGER.debug("Caches toggles nettoyés pour {}", uuid);
    }
}