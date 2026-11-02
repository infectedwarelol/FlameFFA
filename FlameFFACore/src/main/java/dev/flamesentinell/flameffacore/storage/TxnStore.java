package dev.flamesentinell.flameffacore.storage;

import dev.flamesentinell.flameffacore.util.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class TxnStore {
   private final Database db;

   public TxnStore(Database db) {
      this.db = db;
   }

   public boolean claim(String txn, String kind, UUID target, String payload) {
      if (txn != null && !txn.isBlank()) {
         try {
            boolean var7;
            try (
               Connection c = this.db.connection();
               PreparedStatement ps = c.prepareStatement("INSERT INTO ffa_txn (txn, kind, target, payload, created_at) VALUES (?,?,?,?,?)");
            ) {
               ps.setString(1, txn);
               ps.setString(2, kind);
               ps.setBytes(3, Uuids.toBytes(target));
               ps.setString(4, payload.length() > 255 ? payload.substring(0, 255) : payload);
               ps.setLong(5, System.currentTimeMillis());
               ps.executeUpdate();
               var7 = true;
            }

            return var7;
         } catch (SQLException var13) {
            return !KeyStore.isDuplicate(var13);
         }
      } else {
         return true;
      }
   }
}
