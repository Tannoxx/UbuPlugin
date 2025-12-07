package fr.tannoxx.ubuplugin.modules.ranks.data;

import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire des données de ranks (BDD + Teams Scoreboard)
 */
public class RankDataManager {

    private final RanksModule module;
    private final Scoreboard scoreboard;
    private final Map<UUID, PlayerRankData> cache;

    public RankDataManager(@NotNull RanksModule module) {
        this.module = module;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        this.cache = new HashMap<>();
    }

    /**
     * Crée les tables nécessaires
     */
    public void createTables() {
        // Les tables sont déjà créées par DatabaseManager
        module.debug("Tables ranks vérifiées");
    }

    /**
     * Charge les données d'un joueur
     */
    public PlayerRankData loadPlayerData(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        // Vérifier le cache
        if (cache.containsKey(uuid)) {
            return cache.get(uuid);
        }

        try (Connection conn = module.getDatabaseManager().getConnection()) {
            String sql = "SELECT rank, prefix, muted, mute_reason, mute_expires FROM players WHERE uuid = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    PlayerRankData data = new PlayerRankData(
                            uuid,
                            player.getName(),
                            rs.getString("rank"),
                            rs.getString("prefix"),
                            rs.getBoolean("muted"),
                            rs.getString("mute_reason"),
                            rs.getLong("mute_expires")
                    );
                    cache.put(uuid, data);
                    return data;
                } else {
                    // Créer nouveau joueur
                    return createPlayerData(player);
                }
            }
        } catch (SQLException e) {
            module.error("Erreur chargement données joueur", e);
            return new PlayerRankData(uuid, player.getName(), "JOUEUR", "", false, null, 0);
        }
    }

    /**
     * Crée un nouveau joueur dans la BDD
     */
    private PlayerRankData createPlayerData(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        try (Connection conn = module.getDatabaseManager().getConnection()) {
            String sql = "INSERT INTO players (uuid, username, rank, prefix, first_join, last_seen) VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, player.getName());
                stmt.setString(3, "JOUEUR");
                stmt.setString(4, "");
                stmt.setLong(5, now);
                stmt.setLong(6, now);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            module.error("Erreur création joueur", e);
        }

        PlayerRankData data = new PlayerRankData(uuid, player.getName(), "JOUEUR", "", false, null, 0);
        cache.put(uuid, data);
        return data;
    }

    /**
     * Sauvegarde les données d'un joueur
     */
    public void savePlayerData(@NotNull PlayerRankData data) {
        try (Connection conn = module.getDatabaseManager().getConnection()) {
            String sql = "UPDATE players SET rank = ?, prefix = ?, muted = ?, mute_reason = ?, mute_expires = ?, last_seen = ? WHERE uuid = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, data.rank());
                stmt.setString(2, data.prefix());
                stmt.setBoolean(3, data.muted());
                stmt.setString(4, data.muteReason());
                stmt.setLong(5, data.muteExpires());
                stmt.setLong(6, System.currentTimeMillis());
                stmt.setString(7, data.uuid().toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            module.error("Erreur sauvegarde données joueur", e);
        }
    }

    /**
     * Définit le rank d'un joueur
     */
    public void setRank(@NotNull Player player, @NotNull String rank) {
        PlayerRankData data = loadPlayerData(player);
        PlayerRankData updated = new PlayerRankData(
                data.uuid(), data.username(), rank, data.prefix(),
                data.muted(), data.muteReason(), data.muteExpires()
        );
        cache.put(player.getUniqueId(), updated);
        savePlayerData(updated);
        updatePlayerDisplay(player, updated);
    }

    /**
     * Définit le prefix d'un joueur
     */
    public void setPrefix(@NotNull Player player, @NotNull String prefix) {
        PlayerRankData data = loadPlayerData(player);
        PlayerRankData updated = new PlayerRankData(
                data.uuid(), data.username(), data.rank(), prefix,
                data.muted(), data.muteReason(), data.muteExpires()
        );
        cache.put(player.getUniqueId(), updated);
        savePlayerData(updated);
        updatePlayerDisplay(player, updated);
    }

    /**
     * Mute un joueur
     */
    public void mutePlayer(@NotNull Player player, @NotNull String reason, long durationMinutes) {
        PlayerRankData data = loadPlayerData(player);
        long expires = durationMinutes > 0 ? System.currentTimeMillis() + (durationMinutes * 60 * 1000) : 0;

        PlayerRankData updated = new PlayerRankData(
                data.uuid(), data.username(), data.rank(), data.prefix(),
                true, reason, expires
        );
        cache.put(player.getUniqueId(), updated);
        savePlayerData(updated);
    }

    /**
     * Unmute un joueur
     */
    public void unmutePlayer(@NotNull Player player) {
        PlayerRankData data = loadPlayerData(player);
        PlayerRankData updated = new PlayerRankData(
                data.uuid(), data.username(), data.rank(), data.prefix(),
                false, null, 0
        );
        cache.put(player.getUniqueId(), updated);
        savePlayerData(updated);
    }

    /**
     * Vérifie si un joueur est mute
     */
    public boolean isMuted(@NotNull Player player) {
        PlayerRankData data = loadPlayerData(player);
        if (!data.muted()) return false;

        // Vérifier expiration
        if (data.muteExpires() > 0 && System.currentTimeMillis() > data.muteExpires()) {
            unmutePlayer(player);
            return false;
        }

        return true;
    }

    /**
     * Met à jour l'affichage d'un joueur (TAB, nametag, team)
     */
    public void updatePlayerDisplay(@NotNull Player player, @NotNull PlayerRankData data) {
        // Mettre à jour la team
        updatePlayerTeam(player, data);

        // Mettre à jour le TAB
        updatePlayerTab(player, data);
    }

    /**
     * Met à jour la team du joueur
     */
    private void updatePlayerTeam(@NotNull Player player, @NotNull PlayerRankData data) {
        // Supprimer l'ancienne team
        Team oldTeam = scoreboard.getPlayerTeam(player);
        if (oldTeam != null) {
            oldTeam.removePlayer(player);
        }

        // Obtenir la priorité
        int priority = module.getConfigManager().getInt("ranks.list." + data.rank() + ".priority", 0);
        String teamName = String.format("%03d_%s", 999 - priority, player.getUniqueId().toString().substring(0, 8));

        // Créer/obtenir la team
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // Appliquer prefix et suffix
        if (!data.prefix().isEmpty()) {
            team.prefix(module.getTranslationManager().getComponent(player, data.prefix()));
        }

        String suffix = module.getConfigManager().getString("ranks.list." + data.rank() + ".suffix", "");
        if (!suffix.isEmpty()) {
            team.suffix(module.getTranslationManager().getComponent(player, suffix));
        }

        team.addPlayer(player);
    }

    /**
     * Met à jour le TAB list du joueur
     */
    private void updatePlayerTab(@NotNull Player player, @NotNull PlayerRankData data) {
        String header = module.getConfigManager().getString("ranks.tab.header", "");
        String footer = module.getConfigManager().getString("ranks.tab.footer", "");

        player.sendPlayerListHeaderAndFooter(
                module.getTranslationManager().getComponent(player, header),
                module.getTranslationManager().getComponent(player, footer)
        );
    }

    /**
     * Nettoie les ressources
     */
    public void cleanup() {
        cache.clear();

        // Nettoyer les teams créées
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().matches("\\d{3}_[a-f0-9]{8}")) {
                team.unregister();
            }
        }
    }

    /**
     * Supprime du cache un joueur qui se déconnecte
     */
    public void clearCache(@NotNull UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Record pour stocker les données d'un joueur
     */
    public record PlayerRankData(
            UUID uuid,
            String username,
            String rank,
            String prefix,
            boolean muted,
            String muteReason,
            long muteExpires
    ) {}
}