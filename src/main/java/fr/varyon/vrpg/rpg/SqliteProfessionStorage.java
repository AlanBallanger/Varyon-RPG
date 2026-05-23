package fr.varyon.vrpg.rpg;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public final class SqliteProfessionStorage implements ProfessionStorage {

    private static final String DB_FILE = "varyon-rpg.db";
    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("VaryonRPG-SQLite");

    private final Path dataDirectory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VaryonRPG-SQLite-IO");
        t.setDaemon(false);
        return t;
    });

    private Connection connection;
    private String dbPath;

    public SqliteProfessionStorage(@Nonnull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(dataDirectory);
                dbPath = dataDirectory.resolve(DB_FILE).toAbsolutePath().toString();

                try {
                    Class.forName("org.sqlite.JDBC");
                } catch (ClassNotFoundException e) {
                    LOGGER.at(Level.SEVERE).log("SQLite JDBC driver not found: %s", e.getMessage());
                    throw new RuntimeException("SQLite driver missing", e);
                }

                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA synchronous=NORMAL");
                    st.execute("PRAGMA foreign_keys=ON");
                }
                createTables();
                migrateArchitecteToArtisan();

                LOGGER.at(Level.INFO).log("SQLite database ready at %s", dbPath);
            } catch (IOException | SQLException e) {
                LOGGER.at(Level.SEVERE).log("Failed to initialize SQLite: %s", e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS player_account (
                  uuid              TEXT PRIMARY KEY,
                  player_name       TEXT,
                  active_slot_0     TEXT,
                  active_slot_1     TEXT,
                  last_reconvert_at INTEGER NOT NULL DEFAULT 0,
                  updated_at        INTEGER NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS player_profession (
                  uuid          TEXT NOT NULL,
                  profession_id TEXT NOT NULL,
                  level         INTEGER NOT NULL DEFAULT 1,
                  xp_in_level   INTEGER NOT NULL DEFAULT 0,
                  updated_at    INTEGER NOT NULL,
                  PRIMARY KEY (uuid, profession_id)
                )
            """);
            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_profession_leaderboard
                  ON player_profession (profession_id, level DESC, xp_in_level DESC)
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS player_talent (
                  uuid          TEXT NOT NULL,
                  profession_id TEXT NOT NULL,
                  node_id       TEXT NOT NULL,
                  rank          INTEGER NOT NULL DEFAULT 0,
                  PRIMARY KEY (uuid, profession_id, node_id)
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS player_talent_sound (
                  uuid     TEXT NOT NULL,
                  pref_key TEXT NOT NULL,
                  enabled  INTEGER NOT NULL DEFAULT 1,
                  PRIMARY KEY (uuid, pref_key)
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS player_xp_boost (
                  uuid          TEXT NOT NULL,
                  profession_id TEXT NOT NULL,
                  tier          INTEGER NOT NULL,
                  bonus         REAL NOT NULL,
                  remaining_ms  INTEGER NOT NULL,
                  PRIMARY KEY (uuid, profession_id)
                )
            """);
        }
    }

    private void migrateArchitecteToArtisan() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("UPDATE player_account SET active_slot_0 = 'artisan' WHERE active_slot_0 = 'architecte'");
            st.executeUpdate("UPDATE player_account SET active_slot_1 = 'artisan' WHERE active_slot_1 = 'architecte'");
            st.executeUpdate("UPDATE player_profession SET profession_id = 'artisan' WHERE profession_id = 'architecte'");
            st.executeUpdate("UPDATE player_talent SET profession_id = 'artisan' WHERE profession_id = 'architecte'");
        }
    }

    @Override
    public CompletableFuture<PlayerAccount> loadPlayer(@Nonnull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> loadPlayerSync(uuid), executor);
    }

    private PlayerAccount loadPlayerSync(@Nonnull UUID uuid) {
        try {
            PlayerAccount account = null;
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_name, active_slot_0, active_slot_1, last_reconvert_at FROM player_account WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        account = new PlayerAccount(uuid, rs.getString("player_name"));
                        account.setActiveSlot0(Profession.fromId(rs.getString("active_slot_0")));
                        account.setActiveSlot1(Profession.fromId(rs.getString("active_slot_1")));
                        account.setLastReconvertAt(rs.getLong("last_reconvert_at"));
                    }
                }
            }
            if (account == null) {
                account = new PlayerAccount(uuid, null);
                writeInitialAccount(account);
                return account;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT profession_id, level, xp_in_level FROM player_profession WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Profession p = Profession.fromId(rs.getString("profession_id"));
                        if (p == null) continue;
                        account.getProgress(p).setLevel(rs.getInt("level"), rs.getLong("xp_in_level"));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT profession_id, node_id, rank FROM player_talent WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Profession p = Profession.fromId(rs.getString("profession_id"));
                        if (p == null) continue;
                        account.setTalentRank(p, rs.getString("node_id"), rs.getInt("rank"));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pref_key, enabled FROM player_talent_sound WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        account.applyTalentSoundPref(rs.getString("pref_key"), rs.getInt("enabled") != 0);
                    }
                }
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT profession_id, tier, bonus, remaining_ms FROM player_xp_boost WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Profession p = Profession.fromId(rs.getString("profession_id"));
                        if (p == null) continue;
                        long remaining = rs.getLong("remaining_ms");
                        if (remaining <= 0) continue;
                        account.setBoost(p, new XpBoost(rs.getInt("tier"), rs.getDouble("bonus"), now + remaining));
                    }
                }
            }
            return account;
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("loadPlayer(%s) failed: %s", uuid, e.getMessage());
            return new PlayerAccount(uuid, null);
        }
    }

    private void writeInitialAccount(@Nonnull PlayerAccount account) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO player_account (uuid, player_name, active_slot_0, active_slot_1, last_reconvert_at, updated_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, account.getUuid().toString());
            ps.setString(2, account.getPlayerName());
            ps.setString(3, account.getActiveSlot0() == null ? null : account.getActiveSlot0().getId());
            ps.setString(4, account.getActiveSlot1() == null ? null : account.getActiveSlot1().getId());
            ps.setLong(5, account.getLastReconvertAt());
            ps.setLong(6, now);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO player_profession (uuid, profession_id, level, xp_in_level, updated_at) VALUES (?,?,?,?,?)")) {
            for (Profession p : Profession.values()) {
                ProfessionProgress prog = account.getProgress(p);
                ps.setString(1, account.getUuid().toString());
                ps.setString(2, p.getId());
                ps.setInt(3, prog.getLevel());
                ps.setLong(4, prog.getXpInLevel());
                ps.setLong(5, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public CompletableFuture<Void> savePlayer(@Nonnull UUID uuid, @Nonnull PlayerAccount account) {
        return CompletableFuture.runAsync(() -> savePlayerSync(uuid, account), executor);
    }

    private void savePlayerSync(@Nonnull UUID uuid, @Nonnull PlayerAccount account) {
        long now = System.currentTimeMillis();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_account (uuid, player_name, active_slot_0, active_slot_1, last_reconvert_at, updated_at)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT(uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    active_slot_0 = excluded.active_slot_0,
                    active_slot_1 = excluded.active_slot_1,
                    last_reconvert_at = excluded.last_reconvert_at,
                    updated_at = excluded.updated_at
            """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, account.getPlayerName());
                ps.setString(3, account.getActiveSlot0() == null ? null : account.getActiveSlot0().getId());
                ps.setString(4, account.getActiveSlot1() == null ? null : account.getActiveSlot1().getId());
                ps.setLong(5, account.getLastReconvertAt());
                ps.setLong(6, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_profession (uuid, profession_id, level, xp_in_level, updated_at)
                VALUES (?,?,?,?,?)
                ON CONFLICT(uuid, profession_id) DO UPDATE SET
                    level = excluded.level,
                    xp_in_level = excluded.xp_in_level,
                    updated_at = excluded.updated_at
            """)) {
                for (Profession p : Profession.values()) {
                    ProfessionProgress prog = account.getProgress(p);
                    ps.setString(1, uuid.toString());
                    ps.setString(2, p.getId());
                    ps.setInt(3, prog.getLevel());
                    ps.setLong(4, prog.getXpInLevel());
                    ps.setLong(5, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement del = connection.prepareStatement(
                "DELETE FROM player_talent WHERE uuid = ?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO player_talent (uuid, profession_id, node_id, rank) VALUES (?,?,?,?)")) {
                for (Profession p : Profession.values()) {
                    Map<String, Integer> tal = account.getTalents(p);
                    for (Map.Entry<String, Integer> e : tal.entrySet()) {
                        if (e.getValue() == null || e.getValue() <= 0) continue;
                        ins.setString(1, uuid.toString());
                        ins.setString(2, p.getId());
                        ins.setString(3, e.getKey());
                        ins.setInt(4, e.getValue());
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }
            try (PreparedStatement del = connection.prepareStatement(
                "DELETE FROM player_talent_sound WHERE uuid = ?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO player_talent_sound (uuid, pref_key, enabled) VALUES (?,?,?)")) {
                for (Map.Entry<String, Boolean> e : account.getTalentSoundPrefs().entrySet()) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, e.getKey());
                    ins.setInt(3, Boolean.TRUE.equals(e.getValue()) ? 1 : 0);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            try (PreparedStatement del = connection.prepareStatement(
                "DELETE FROM player_xp_boost WHERE uuid = ?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO player_xp_boost (uuid, profession_id, tier, bonus, remaining_ms) VALUES (?,?,?,?,?)")) {
                for (Map.Entry<Profession, XpBoost> e : account.getActiveBoosts().entrySet()) {
                    long remaining = e.getValue().getRemainingMs();
                    if (remaining <= 0) continue;
                    ins.setString(1, uuid.toString());
                    ins.setString(2, e.getKey().getId());
                    ins.setInt(3, e.getValue().getTier());
                    ins.setDouble(4, e.getValue().getBonus());
                    ins.setLong(5, remaining);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("savePlayer(%s) failed: %s", uuid, e.getMessage());
            try { connection.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    @Override
    public CompletableFuture<Void> saveAll(@Nonnull Map<UUID, PlayerAccount> dirty) {
        return CompletableFuture.runAsync(() -> saveAllSync(dirty), executor);
    }

    @Override
    public void saveAllSync(@Nonnull Map<UUID, PlayerAccount> dirty) {
        for (Map.Entry<UUID, PlayerAccount> e : dirty.entrySet()) {
            savePlayerSync(e.getKey(), e.getValue());
        }
    }

    @Override
    public CompletableFuture<Boolean> playerExists(@Nonnull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM player_account WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("playerExists(%s) failed: %s", uuid, e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deletePlayer(@Nonnull UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.setAutoCommit(false);
                for (String table : new String[]{"player_xp_boost", "player_talent_sound", "player_talent", "player_profession", "player_account"}) {
                    try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                LOGGER.at(Level.SEVERE).log("deletePlayer(%s) failed: %s", uuid, e.getMessage());
                try { connection.rollback(); } catch (SQLException ignored) {}
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<UUID>> findUuidsByName(@Nonnull String exactNameCaseInsensitive) {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid FROM player_account WHERE LOWER(player_name) = ?")) {
                ps.setString(1, exactNameCaseInsensitive.toLowerCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            result.add(UUID.fromString(rs.getString(1)));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("findUuidsByName(%s) failed: %s", exactNameCaseInsensitive, e.getMessage());
            }
            return result;
        }, executor);
    }

    @Override
    public CompletableFuture<String> getSavedName(@Nonnull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_name FROM player_account WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString(1);
                }
            } catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("getSavedName(%s) failed: %s", uuid, e.getMessage());
            }
            return null;
        }, executor);
    }

    @Nullable
    @Override
    public String getName() {
        return "SQLite";
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                LOGGER.at(Level.WARNING).log("Failed to close SQLite connection: %s", e.getMessage());
            } finally {
                executor.shutdown();
            }
        });
    }

    public Map<UUID, ProfessionProgress> leaderboardForProfession(@Nonnull Profession p, int limit) {
        Map<UUID, ProfessionProgress> result = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT uuid, level, xp_in_level FROM player_profession " +
            "WHERE profession_id = ? ORDER BY level DESC, xp_in_level DESC LIMIT ?")) {
            ps.setString(1, p.getId());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID u = UUID.fromString(rs.getString(1));
                        result.put(u, new ProfessionProgress(p, rs.getInt(2), rs.getLong(3)));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("leaderboardForProfession failed: %s", e.getMessage());
            return Collections.emptyMap();
        }
        return result;
    }
}
