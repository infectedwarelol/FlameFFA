package dev.flamesentinell.flameffacore.storage;

import dev.flamesentinell.flameffacore.util.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyStore {
   private final Database db;
   private final Map<UUID, Map<String, Integer>> cache = new ConcurrentHashMap<>();

   public KeyStore(Database db) {
      this.db = db;
   }

   public void loadCache(UUID player) {
      this.cache.put(player, new ConcurrentHashMap<>(this.balances(player)));
   }

   public void forget(UUID player) {
      this.cache.remove(player);
   }

   public int cached(UUID player, String crate) {
      Map<String, Integer> mine = this.cache.get(player);
      return mine == null ? 0 : mine.getOrDefault(crate, 0);
   }

   private void recache(UUID player, String crate, int after) {
      Map<String, Integer> mine = this.cache.get(player);
      if (mine != null) {
         mine.put(crate, Math.max(0, after));
      }
   }

   public KeyStore.Result grant(UUID player, String crate, int amount, String reason, String actor, String txn) {
      if (amount <= 0) {
         return KeyStore.Result.ERROR;
      } else {
         try {
            KeyStore.Result var28;
            try (Connection c = this.db.connection()) {
               c.setAutoCommit(false);

               try {
                  if (txn != null && !this.claimTxn(c, txn, "key", player, crate + " x" + amount)) {
                     c.rollback();
                     return KeyStore.Result.DUPLICATE_TXN;
                  }

                  try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ffa_keys (uuid, crate_id, amount) VALUES (?,?,?) ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)"
                     )) {
                     ps.setBytes(1, Uuids.toBytes(player));
                     ps.setString(2, crate);
                     ps.setInt(3, amount);
                     ps.executeUpdate();
                  }

                  int after = this.readBalance(c, player, crate);
                  this.writeLedger(c, player, crate, amount, after, reason, actor, txn);
                  c.commit();
                  this.recache(player, crate, after);
                  var28 = KeyStore.Result.OK;
               } catch (SQLException var22) {
                  c.rollback();
                  if (!isDuplicate(var22)) {
                     throw var22;
                  }

                  return KeyStore.Result.DUPLICATE_TXN;
               } finally {
                  c.setAutoCommit(true);
               }
            }

            return var28;
         } catch (SQLException var25) {
            return KeyStore.Result.ERROR;
         }
      }
   }

   public KeyStore.Result spend(UUID player, String crate, int amount, String reason, String actor) {
      if (amount <= 0) {
         return KeyStore.Result.ERROR;
      } else {
         try {
            KeyStore.Result var9;
            try (Connection c = this.db.connection()) {
               c.setAutoCommit(false);

               try {
                  int changed;
                  try (PreparedStatement ps = c.prepareStatement("UPDATE ffa_keys SET amount = amount - ? WHERE uuid = ? AND crate_id = ? AND amount >= ?")) {
                     ps.setInt(1, amount);
                     ps.setBytes(2, Uuids.toBytes(player));
                     ps.setString(3, crate);
                     ps.setInt(4, amount);
                     changed = ps.executeUpdate();
                  }

                  if (changed == 0) {
                     c.rollback();
                     return KeyStore.Result.INSUFFICIENT;
                  }

                  int after = this.readBalance(c, player, crate);
                  this.writeLedger(c, player, crate, -amount, after, reason, actor, null);
                  c.commit();
                  this.recache(player, crate, after);
                  var9 = KeyStore.Result.OK;
               } catch (SQLException var22) {
                  c.rollback();
                  throw var22;
               } finally {
                  c.setAutoCommit(true);
               }
            }

            return var9;
         } catch (SQLException var25) {
            return KeyStore.Result.ERROR;
         }
      }
   }

   public int pruneLedger(int retentionDays) {
      if (retentionDays <= 0) {
         return 0;
      } else {
         long cutoff = System.currentTimeMillis() - retentionDays * 86400000L;

         try {
            int var6;
            try (
               Connection c = this.db.connection();
               PreparedStatement ps = c.prepareStatement("DELETE FROM ffa_key_ledger WHERE created_at < ?");
            ) {
               ps.setLong(1, cutoff);
               var6 = ps.executeUpdate();
            }

            return var6;
         } catch (SQLException var12) {
            return 0;
         }
      }
   }

   public int balance(UUID player, String crate) {
      try {
         int var4;
         try (Connection c = this.db.connection()) {
            var4 = this.readBalance(c, player, crate);
         }

         return var4;
      } catch (SQLException var8) {
         return 0;
      }
   }

   public Map<String, Integer> balances(UUID player) {
      Map<String, Integer> out = new LinkedHashMap<>();

      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("SELECT crate_id, amount FROM ffa_keys WHERE uuid = ? AND amount > 0 ORDER BY crate_id");
      ) {
         ps.setBytes(1, Uuids.toBytes(player));

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               out.put(rs.getString(1), rs.getInt(2));
            }
         }
      } catch (SQLException var14) {
      }

      return out;
   }

   private boolean claimTxn(Connection c, String txn, String kind, UUID target, String payload) throws SQLException {
      try {
         boolean var7;
         try (PreparedStatement ps = c.prepareStatement("INSERT INTO ffa_txn (txn, kind, target, payload, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, txn);
            ps.setString(2, kind);
            ps.setBytes(3, Uuids.toBytes(target));
            ps.setString(4, payload);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            var7 = true;
         }

         return var7;
      } catch (SQLException var11) {
         if (isDuplicate(var11)) {
            return false;
         } else {
            throw var11;
         }
      }
   }

   private int readBalance(Connection c, UUID player, String crate) throws SQLException {
      int var6;
      try (PreparedStatement ps = c.prepareStatement("SELECT amount FROM ffa_keys WHERE uuid = ? AND crate_id = ?")) {
         ps.setBytes(1, Uuids.toBytes(player));
         ps.setString(2, crate);

         try (ResultSet rs = ps.executeQuery()) {
            var6 = rs.next() ? rs.getInt(1) : 0;
         }
      }

      return var6;
   }

   private void writeLedger(Connection c, UUID player, String crate, int delta, int after, String reason, String actor, String txn) throws SQLException {
      try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO ffa_key_ledger (uuid, crate_id, delta, balance_after, reason, actor, txn, created_at) VALUES (?,?,?,?,?,?,?,?)"
         )) {
         ps.setBytes(1, Uuids.toBytes(player));
         ps.setString(2, crate);
         ps.setInt(3, delta);
         ps.setInt(4, after);
         ps.setString(5, reason);
         ps.setString(6, actor);
         ps.setString(7, txn);
         ps.setLong(8, System.currentTimeMillis());
         ps.executeUpdate();
      }
   }

   static boolean isDuplicate(SQLException e) {
      String state = e.getSQLState();
      return e.getErrorCode() == 1062 || state != null && state.startsWith("23");
   }

   public static enum Result {
      OK,
      DUPLICATE_TXN,
      INSUFFICIENT,
      ERROR;
   }
}
