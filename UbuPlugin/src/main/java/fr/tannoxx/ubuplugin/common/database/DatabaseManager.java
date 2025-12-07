package fr.tannoxx.ubuplugin.common.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.tannoxx.ubuplugin.UbuPlugin;
import fr.tannoxx.ubuplugin.common.config.ConfigManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

/**
 * Gestionnaire de base de données utilisant HikariCP pour la gestion du pool de connexions
 * Support SQLite avec possibilité d'extension future pour MySQL
 * 
 * @author Tannoxx
 * @version 2.0.0
 */
public class DatabaseManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);
    
    private final UbuPlugin plugin;
    private final ConfigManager configManager;
    
    private HikariDataSource dataSource;
    private DatabaseType databaseType;
    
    /**
     * Type de base de données supporté
     */
    public enum DatabaseType {
        SQLITE,
        MYSQL // Future extension
    }
    
    /**
     * Constructeur
     * @param plugin Instance du plugin
     * @param configManager Gestionnaire de configuration
     */
    public DatabaseManager(@NotNull UbuPlugin plugin, @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    /**
     * Initialise la connexion à la base de données
     * @throws SQLException Si erreur de connexion
     */
    public void initialize() throws SQLException {
        String dbType = configManager.getDatabaseType();
        
        try {
            databaseType = DatabaseType.valueOf(dbType);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Type de base de données invalide '{}', utilisation de SQLite par défaut", dbType);
            databaseType = DatabaseType.SQLITE;
        }
        
        switch (databaseType) {
            case SQLITE -> initializeSQLite();
            case MYSQL -> throw new UnsupportedOperationException("MySQL n'est pas encore supporté");
        }
        
        // Créer les tables
        createTables();
        
        // Migrer depuis YAML si nécessaire
        if (configManager.isMigrationEnabled()) {
            migrateFromYAML();
        }
    }
    
    /**
     * Initialise la connexion SQLite avec HikariCP
     * @throws SQLException Si erreur de connexion
     */
    private void initializeSQLite() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), configManager.getSQLiteFileName());
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("UbuPlugin-SQLite");
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        
        // Configuration du pool
        hikariConfig.setMinimumIdle(configManager.getInt("database.pool.minimum-idle", 2));
        hikariConfig.setMaximumPoolSize(configManager.getInt("database.pool.maximum-pool-size", 10));
        hikariConfig.setConnectionTimeout(configManager.getInt("database.pool.connection-timeout", 5000));
        hikariConfig.setMaxLifetime(configManager.getInt("database.pool.max-lifetime", 1800000));
        
        // Propriétés SQLite pour optimisation
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        // Propriétés SQLite spécifiques
        hikariConfig.setConnectionTestQuery("SELECT 1");
        
        dataSource = new HikariDataSource(hikariConfig);
        
        // Activer WAL mode pour meilleures performances
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
            stmt.execute("PRAGMA foreign_keys=ON;");
        }
        
        LOGGER.info("Connexion SQLite initialisée: {}", dbFile.getName());
    }
    
    /**
     * Crée les tables de la base de données
     */
    private void createTables() {
        try (Connection conn = getConnection()) {
            
            // Table des joueurs (ranks, prefixes)
            String createPlayers = """
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    username TEXT NOT NULL,
                    rank TEXT DEFAULT 'JOUEUR',
                    prefix TEXT DEFAULT '',
                    muted BOOLEAN DEFAULT FALSE,
                    mute_reason TEXT,
                    mute_expires INTEGER,
                    first_join INTEGER NOT NULL,
                    last_seen INTEGER NOT NULL,
                    UNIQUE(uuid)
                );
                """;
            
            // Table des enchantements cooldowns
            String createEnchantCooldowns = """
                CREATE TABLE IF NOT EXISTS enchant_cooldowns (
                    uuid TEXT NOT NULL,
                    enchant_type TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    PRIMARY KEY (uuid, enchant_type),
                    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
                );
                """;
            
            // Table des toggles Magnetic
            String createMagneticToggles = """
                CREATE TABLE IF NOT EXISTS magnetic_toggles (
                    uuid TEXT PRIMARY KEY,
                    enabled BOOLEAN DEFAULT TRUE,
                    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
                );
                """;
            
            // Table du cache de pays
            String createCountryCache = """
                CREATE TABLE IF NOT EXISTS country_cache (
                    cache_key TEXT PRIMARY KEY,
                    country_name TEXT,
                    is_error BOOLEAN DEFAULT FALSE,
                    timestamp INTEGER NOT NULL
                );
                """;
            
            // Créer les index pour optimisation
            String createIndexes = """
                CREATE INDEX IF NOT EXISTS idx_players_username ON players(username);
                CREATE INDEX IF NOT EXISTS idx_players_rank ON players(rank);
                CREATE INDEX IF NOT EXISTS idx_enchant_cooldowns_expires ON enchant_cooldowns(expires_at);
                CREATE INDEX IF NOT EXISTS idx_country_cache_timestamp ON country_cache(timestamp);
                """;
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createPlayers);
                stmt.execute(createEnchantCooldowns);
                stmt.execute(createMagneticToggles);
                stmt.execute(createCountryCache);
                stmt.execute(createIndexes);
            }
            
            LOGGER.info("Tables de base de données créées/vérifiées");
            
        } catch (SQLException e) {
            LOGGER.error("Erreur lors de la création des tables", e);
        }
    }
    
    /**
     * Migre les données depuis les anciens fichiers YAML
     */
    private void migrateFromYAML() {
        CompletableFuture.runAsync(() -> {
            try {
                YAMLMigrator migrator = new YAMLMigrator(plugin, this, configManager);
                migrator.migrate();
            } catch (Exception e) {
                LOGGER.error("Erreur lors de la migration depuis YAML", e);
            }
        });
    }
    
    /**
     * Récupère une connexion depuis le pool
     * @return Connection
     * @throws SQLException Si erreur
     */
    @NotNull
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Le pool de connexions n'est pas initialisé");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Exécute une requête de manière asynchrone
     * @param task Tâche à exécuter
     * @return CompletableFuture
     */
    @NotNull
    public CompletableFuture<Void> executeAsync(@NotNull Runnable task) {
        return CompletableFuture.runAsync(task, plugin.getServer().getScheduler()
            .getScheduler()::runTaskAsynchronously);
    }
    
    /**
     * Exécute une requête avec résultat de manière asynchrone
     * @param task Tâche à exécuter
     * @param <T> Type de retour
     * @return CompletableFuture avec résultat
     */
    @NotNull
    public <T> CompletableFuture<T> supplyAsync(@NotNull java.util.function.Supplier<T> task) {
        return CompletableFuture.supplyAsync(task);
    }
    
    /**
     * Ferme la connexion à la base de données
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Connexion à la base de données fermée");
        }
    }
    
    /**
     * Récupère le type de base de données
     * @return DatabaseType
     */
    @NotNull
    public DatabaseType getDatabaseType() {
        return databaseType;
    }
    
    /**
     * Vérifie si la connexion est active
     * @return true si active
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * Obtient des statistiques sur le pool de connexions
     * @return Statistiques formatées
     */
    @NotNull
    public String getPoolStats() {
        if (dataSource == null) {
            return "Pool non initialisé";
        }
        
        return String.format("Pool: %d actives, %d idle, %d total, %d en attente",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
}
