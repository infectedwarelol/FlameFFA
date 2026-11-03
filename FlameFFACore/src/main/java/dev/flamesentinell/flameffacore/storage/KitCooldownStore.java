package dev.flamesentinell.flameffacore.storage;

import dev.flamesentinell.flameffacore.util.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KitCooldownStore {
   private final Database db;
   private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();

   public KitCooldownStore(Database db) {
      this.db = db;
   }

   public void load(UUID uuid) {
      Map<String, Long> mine = new ConcurrentHashMap<>();
      long now = System.currentTimeMillis();

      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("SELECT rank_id, until FROM ffa_kit_cooldowns WHERE uuid = ? AND until > ?");
      ) {
         ps.setBytes(1, Uuids.toBytes(uuid));
         ps.setLong(2, now);

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               mine.put(rs.getString(1), rs.getLong(2));
            }
         }
      } catch (SQLException var16) {
      }

      Map<String, Long> current = this.cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
      mine.forEach((rankId, until) -> current.merge(rankId, until, Math::max));
   }

   public void forget(UUID uuid) {
      long now = System.currentTimeMillis();
      this.cache.computeIfPresent(uuid, (key, mine) -> {
         mine.values().removeIf(until -> until <= now);
         return mine.isEmpty() ? null : mine;
      });
   }

   public long remaining(UUID uuid, String rankId) {
      Map<String, Long> mine = this.cache.get(uuid);
      if (mine == null) {
         return 0L;
      } else {
         Long until = mine.get(rankId);
         if (until == null) {
            return 0L;
         } else {
            long left = until - System.currentTimeMillis();
            if (left <= 0L) {
               mine.remove(rankId);
               return 0L;
            } else {
               return (left + 999L) / 1000L;
            }
         }
      }
   }

   public long mark(UUID uuid, String rankId, long seconds) {
      if (seconds <= 0L) {
         return 0L;
      } else {
         long until = System.currentTimeMillis() + seconds * 1000L;
         this.cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(rankId, until);
         return until;
      }
   }

   public void persist(UUID uuid, String rankId, long until) {
      if (until > 0L) {
         this.write(uuid, rankId, until);
      }
   }

   public void start(UUID uuid, String rankId, long seconds) {
      this.persist(uuid, rankId, this.mark(uuid, rankId, seconds));
   }

   private void write(UUID uuid, String rankId, long until) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement(
            "INSERT INTO ffa_kit_cooldowns (uuid, rank_id, until) VALUES (?,?,?) ON DUPLICATE KEY UPDATE until = VALUES(until)"
         );
      ) {
         ps.setBytes(1, Uuids.toBytes(uuid));
         ps.setString(2, rankId);
         ps.setLong(3, until);
         ps.executeUpdate();
      } catch (SQLException var13) {
      }
   }

   public void clear(UUID uuid, String rankId) {
      Map<String, Long> mine = this.cache.get(uuid);
      if (mine != null) {
         if (rankId == null) {
            mine.clear();
         } else {
            mine.remove(rankId);
         }
      }

      String sql = rankId == null ? "DELETE FROM ffa_kit_cooldowns WHERE uuid = ?" : "DELETE FROM ffa_kit_cooldowns WHERE uuid = ? AND rank_id = ?";

      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement(sql);
      ) {
         ps.setBytes(1, Uuids.toBytes(uuid));
         if (rankId != null) {
            ps.setString(2, rankId);
         }

         ps.executeUpdate();
      } catch (SQLException var13) {
      }
   }

   public int prune() {
      try {
         int var3;
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement("DELETE FROM ffa_kit_cooldowns WHERE until <= ?");
         ) {
            ps.setLong(1, System.currentTimeMillis());
            var3 = ps.executeUpdate();
         }

         return var3;
      } catch (SQLException var9) {
         return 0;
      }
   }
}
