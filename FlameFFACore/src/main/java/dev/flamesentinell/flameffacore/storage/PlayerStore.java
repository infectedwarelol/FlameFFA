package dev.flamesentinell.flameffacore.storage;

import dev.flamesentinell.flameffacore.util.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerStore {
   private final Database db;

   public PlayerStore(Database db) {
      this.db = db;
   }

   public void touch(UUID uuid, String name) {
      long now = System.currentTimeMillis();

      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement(
            "INSERT INTO ffa_players (uuid, name, first_join, last_join) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE name = VALUES(name), last_join = VALUES(last_join)"
         );
      ) {
         ps.setBytes(1, Uuids.toBytes(uuid));
         ps.setString(2, name);
         ps.setLong(3, now);
         ps.setLong(4, now);
         ps.executeUpdate();
      } catch (SQLException var13) {
      }
   }

   public UUID uuidByName(String name) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("SELECT uuid FROM ffa_players WHERE LOWER(name) = LOWER(?) ORDER BY last_join DESC LIMIT 1");
      ) {
         ps.setString(1, name);

         try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Uuids.fromBytes(rs.getBytes(1)) : null;
         }
      } catch (SQLException var13) {
         return null;
      }
   }

   public PlayerStore.Stats stats(UUID uuid) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("SELECT kills, deaths, best_streak, last_kit, playtime FROM ffa_players WHERE uuid = ?");
      ) {
         ps.setBytes(1, Uuids.toBytes(uuid));

         try (ResultSet rs = ps.executeQuery()) {
            return rs.next()
               ? new PlayerStore.Stats(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getLong(5))
               : new PlayerStore.Stats(0, 0, 0, null, 0L);
         }
      } catch (SQLException var13) {
         return new PlayerStore.Stats(0, 0, 0, null, 0L);
      }
   }

   public void addKill(UUID uuid, int streak) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("UPDATE ffa_players SET kills = kills + 1, best_streak = GREATEST(best_streak, ?) WHERE uuid = ?");
      ) {
         ps.setInt(1, streak);
         ps.setBytes(2, Uuids.toBytes(uuid));
         ps.executeUpdate();
      } catch (SQLException var11) {
      }
   }

   public void addDeath(UUID uuid) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("UPDATE ffa_players SET deaths = deaths + 1 WHERE uuid = ?");
      ) {
         ps.setBytes(1, Uuids.toBytes(uuid));
         ps.executeUpdate();
      } catch (SQLException var10) {
      }
   }

   public void addPlaytime(UUID uuid, long millis) {
      if (millis > 0L) {
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement("UPDATE ffa_players SET playtime = playtime + ? WHERE uuid = ?");
         ) {
            ps.setLong(1, millis);
            ps.setBytes(2, Uuids.toBytes(uuid));
            ps.executeUpdate();
         } catch (SQLException var12) {
         }
      }
   }

   public void setLastKit(UUID uuid, String kitId) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("UPDATE ffa_players SET last_kit = ? WHERE uuid = ?");
      ) {
         ps.setString(1, kitId);
         ps.setBytes(2, Uuids.toBytes(uuid));
         ps.executeUpdate();
      } catch (SQLException var11) {
      }
   }

   public List<PlayerStore.Top> top(String metric, int limit) {
      String column = switch (metric) {
         case "kills" -> "kills";
         case "deaths" -> "deaths";
         case "streak" -> "best_streak";
         case "playtime" -> "playtime";
         default -> null;
      };
      List<PlayerStore.Top> rows = new ArrayList<>();
      if (column == null) {
         return rows;
      } else {
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement(
               "SELECT name, " + column + " FROM ffa_players WHERE name IS NOT NULL AND " + column + " > 0 ORDER BY " + column + " DESC LIMIT ?"
            );
         ) {
            ps.setInt(1, Math.max(1, Math.min(50, limit)));

            try (ResultSet rs = ps.executeQuery()) {
               while (rs.next()) {
                  rows.add(new PlayerStore.Top(rs.getString(1), (long)rs.getDouble(2)));
               }
            }
         } catch (SQLException var16) {
         }

         return rows;
      }
   }

   public record Stats(int kills, int deaths, int bestStreak, String lastKit, long playtimeMs) {
   }

   public record Top(String name, long value) {
   }
}
