package fr.tannoxx.ubuplugin.modules.ranks.data;

import fr.tannoxx.ubuplugin.modules.ranks.RanksModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des données de ranks
 * Thread-safe avec cache concurrent
 * MODIFIÉ: Affiche maintenant le prefix ET le suffix sur le nametag (au-dessus de la tête)
 */
public class RankDataManager {

    private final RanksModule module;
    private final Scoreboard scoreboard;
    private final Map<UUID, PlayerRankData> cache;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public RankDataManager(@NotNull RanksModule module) {
        this.module = module;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        this.cache = new ConcurrentHashMap<>();
    }

    public void createTables() {
        module.debug("Tables ranks vérifiées");
    }

    @NotNull
    public PlayerRankData loadPlayerData(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        // Vérifier le cache
        PlayerRankData cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }

        try (Connection conn = module.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT rank, prefix, muted, mute_reason, mute_expires FROM players WHERE uuid = ?")) {

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
                return createPlayerData(player);
            }
        } catch (SQLException e) {
            module.error("Erreur chargement données joueur", e);
            return new PlayerRankData(uuid, player.getName(), "JOUEUR", "", false, null, 0);
        }
    }

    @NotNull
    private PlayerRankData createPlayerData(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        try (Connection conn = module.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO players (uuid, username, rank, prefix, first_join, last_seen) VALUES (?, ?, ?, ?, ?, ?)")) {

            stmt.setString(1, uuid.toString());
            stmt.setString(2, player.getName());
            stmt.setString(3, "JOUEUR");
            stmt.setString(4, "");
            stmt.setLong(5, now);
            stmt.setLong(6, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            module.error("Erreur création joueur", e);
        }

        PlayerRankData data = new PlayerRankData(uuid, player.getName(), "JOUEUR", "", false, null, 0);
        cache.put(uuid, data);
        return data;
    }

    public void savePlayerData(@NotNull PlayerRankData data) {
        try (Connection conn = module.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE players SET rank = ?, prefix = ?, muted = ?, mute_reason = ?, mute_expires = ?, last_seen = ? WHERE uuid = ?")) {

            stmt.setString(1, data.rank());
            stmt.setString(2, data.prefix());
            stmt.setBoolean(3, data.muted());
            stmt.setString(4, data.muteReason());
            stmt.setLong(5, data.muteExpires());
            stmt.setLong(6, System.currentTimeMillis());
            stmt.setString(7, data.uuid().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            module.error("Erreur sauvegarde données joueur", e);
        }
    }

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

    public void unmutePlayer(@NotNull Player player) {
        PlayerRankData data = loadPlayerData(player);
        PlayerRankData updated = new PlayerRankData(
                data.uuid(), data.username(), data.rank(), data.prefix(),
                false, null, 0
        );
        cache.put(player.getUniqueId(), updated);
        savePlayerData(updated);
    }

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

    public void updatePlayerDisplay(@NotNull Player player, @NotNull PlayerRankData data) {
        updatePlayerTeam(player, data);
        updatePlayerTab(player, data);
    }

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

        // MODIFIÉ: Appliquer le prefix personnalisé (visible sur le nametag ET dans le TAB)
        if (!data.prefix().isEmpty()) {
            try {
                // Parser le prefix avec MiniMessage pour supporter les couleurs/formats
                Component prefixComp = MINI_MESSAGE.deserialize(data.prefix() + " ");
                team.prefix(prefixComp);
                module.debug("Prefix appliqué pour {}: {}", player.getName(), data.prefix());
            } catch (Exception e) {
                module.warn("Erreur parsing prefix pour {}: {}", player.getName(), e.getMessage());
                // Fallback: utiliser le prefix brut
                team.prefix(Component.text(data.prefix() + " "));
            }
        } else {
            // Pas de prefix personnalisé, vider
            team.prefix(Component.empty());
        }

        // MODIFIÉ: Appliquer le suffix du rank (visible sur le nametag ET dans le TAB)
        String rankSuffix = module.getConfigManager().getString("ranks.list." + data.rank() + ".suffix", "");
        if (!rankSuffix.isEmpty()) {
            try {
                // Parser le suffix avec MiniMessage
                Component suffixComp = MINI_MESSAGE.deserialize(" " + rankSuffix);
                team.suffix(suffixComp);
                module.debug("Suffix appliqué pour {}: {}", player.getName(), rankSuffix);
            } catch (Exception e) {
                module.warn("Erreur parsing suffix pour {}: {}", player.getName(), e.getMessage());
                // Fallback: utiliser le suffix brut
                team.suffix(Component.text(" " + rankSuffix));
            }
        } else {
            // Pas de suffix, vider
            team.suffix(Component.empty());
        }

        team.addPlayer(player);
    }

    private void updatePlayerTab(@NotNull Player player, @NotNull PlayerRankData data) {
        String header = module.getConfigManager().getString("ranks.tab.header", "");
        String footer = module.getConfigManager().getString("ranks.tab.footer", "");

        try {
            Component headerComp = MINI_MESSAGE.deserialize(header);
            Component footerComp = MINI_MESSAGE.deserialize(footer);
            player.sendPlayerListHeaderAndFooter(headerComp, footerComp);
        } catch (Exception e) {
            module.debug("Erreur TAB: {}", e.getMessage());
        }
    }

    public void cleanup() {
        cache.clear();

        // Nettoyer les teams créées
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().matches("\\d{3}_[a-f0-9]{8}")) {
                team.unregister();
            }
        }
    }

    public void clearCache(@NotNull UUID uuid) {
        cache.remove(uuid);
    }

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