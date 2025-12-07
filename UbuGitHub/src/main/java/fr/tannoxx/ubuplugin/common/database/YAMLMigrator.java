package fr.tannoxx.ubuplugin.common.database;

import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe responsable de la migration des données YAML vers SQLite
 * Thread-safe avec compteurs atomiques
 *
 * @author Tannoxx
 * @version 2.0.0
 */
public class YAMLMigrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(YAMLMigrator.class);
    private static final int BATCH_SIZE = 100;

    private final UbuPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;

    private final AtomicInteger playersMigrated = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    /**
     * Constructeur
     * @param plugin Instance du plugin
     * @param databaseManager Gestionnaire de BDD
     * @param configManager Gestionnaire de config
     */
    public YAMLMigrator(@NotNull UbuPlugin plugin,
                        @NotNull DatabaseManager databaseManager,
                        @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
    }

    /**
     * Effectue la migration complète
     */
    public void migrate() {
        LOGGER.info("═══════════════════════════════════════════════");
        LOGGER.info("  Démarrage de la migration YAML → SQLite");
        LOGGER.info("═══════════════════════════════════════════════");

        File playersFile = new File(plugin.getDataFolder(), "players.yml");

        // Vérifier si migration déjà effectuée
        if (isMigrationCompleted()) {
            LOGGER.info("Migration déjà effectuée, passage ignoré");
            return;
        }

        // Vérifier si le fichier existe
        if (!playersFile.exists()) {
            LOGGER.info("Aucun fichier players.yml trouvé, migration ignorée");
            markMigrationCompleted();
            return;
        }

        try {
            // Créer un backup
            if (configManager.isMigrationBackupEnabled()) {
                createBackup(playersFile);
            }

            // Migrer les joueurs
            migratePlayersData(playersFile);

            // Marquer la migration comme terminée
            markMigrationCompleted();

            LOGGER.info("═══════════════════════════════════════════════");
            LOGGER.info("  ✓ Migration terminée avec succès !");
            LOGGER.info("  ✓ {} joueurs migrés", playersMigrated.get());
            if (errorCount.get() > 0) {
                LOGGER.warn("  ⚠ {} erreurs rencontrées", errorCount.get());
            }
            LOGGER.info("═══════════════════════════════════════════════");

        } catch (Exception e) {
            LOGGER.error("Erreur fatale lors de la migration", e);
        }
    }

    /**
     * Vérifie si la migration a déjà été effectuée
     * @return true si déjà migrée
     */
    private boolean isMigrationCompleted() {
        // Vérifier le fichier flag
        File flagFile = new File(plugin.getDataFolder(), ".migration_completed");
        if (flagFile.exists()) {
            return true;
        }

        // Vérifier si la table players contient des données
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM players")) {

            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                return true;
            }
        } catch (SQLException e) {
            LOGGER.debug("Vérification migration échouée: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Marque la migration comme terminée en créant un fichier flag
     */
    private void markMigrationCompleted() {
        File flagFile = new File(plugin.getDataFolder(), ".migration_completed");
        try {
            if (flagFile.createNewFile()) {
                LOGGER.debug("Fichier flag de migration créé");
            }
        } catch (IOException e) {
            LOGGER.warn("Impossible de créer le fichier flag de migration", e);
        }
    }

    /**
     * Crée un backup du fichier YAML
     * @param file Fichier à sauvegarder
     */
    private void createBackup(@NotNull File file) {
        try {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                LOGGER.error("Impossible de créer le dossier de backup");
                return;
            }

            File backupFile = new File(backupDir,
                    file.getName().replace(".yml", "_" + timestamp + ".yml"));

            Files.copy(file.toPath(), backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);

            LOGGER.info("✓ Backup créé: {}", backupFile.getName());

        } catch (IOException e) {
            LOGGER.error("Erreur lors de la création du backup", e);
        }
    }

    /**
     * Migre les données des joueurs depuis players.yml
     * @param playersFile Fichier players.yml
     */
    private void migratePlayersData(@NotNull File playersFile) {
        FileConfiguration playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection playersSection = playersConfig.getConfigurationSection("players");

        if (playersSection == null) {
            LOGGER.warn("Aucune section 'players' trouvée dans players.yml");
            return;
        }

        LOGGER.info("Migration de {} joueurs...", playersSection.getKeys(false).size());

        String insertSQL = """
            INSERT OR REPLACE INTO players\s
            (uuid, username, rank, prefix, first_join, last_seen)\s
            VALUES (?, ?, ?, ?, ?, ?)
           \s""";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSQL)) {

            // Désactiver l'auto-commit pour transaction
            conn.setAutoCommit(false);

            for (String uuidStr : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidStr);

                    if (playerSection == null) {
                        continue;
                    }

                    String rank = playerSection.getString("rank", "JOUEUR");
                    String prefix = playerSection.getString("prefix", "");

                    // Timestamp actuel pour first_join et last_seen
                    long now = System.currentTimeMillis();

                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, "Player_" + uuidStr.substring(0, 8)); // Nom temporaire
                    stmt.setString(3, rank);
                    stmt.setString(4, prefix);
                    stmt.setLong(5, now);
                    stmt.setLong(6, now);

                    stmt.addBatch();

                    int migrated = playersMigrated.incrementAndGet();

                    // Exécuter par lots
                    if (migrated % BATCH_SIZE == 0) {
                        stmt.executeBatch();
                        conn.commit();
                        LOGGER.info("  Progression: {} joueurs migrés...", migrated);
                    }

                } catch (IllegalArgumentException e) {
                    LOGGER.warn("UUID invalide ignoré: {}", uuidStr);
                    errorCount.incrementAndGet();
                } catch (SQLException e) {
                    LOGGER.warn("Erreur SQL pour le joueur {}: {}", uuidStr, e.getMessage());
                    errorCount.incrementAndGet();
                }
            }

            // Exécuter le reste
            stmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);

        } catch (SQLException e) {
            LOGGER.error("Erreur SQL lors de la migration des joueurs", e);
        }
    }

}