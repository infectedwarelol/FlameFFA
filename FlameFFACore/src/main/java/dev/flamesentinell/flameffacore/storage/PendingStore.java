package dev.flamesentinell.flameffacore.storage;

import dev.flamesentinell.flameffacore.util.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PendingStore {
   private final Database db;

   public PendingStore(Database db) {
      this.db = db;
   }

   public void queue(UUID player, String kind, String payload) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("INSERT INTO ffa_pending (uuid, kind, payload, created_at) VALUES (?,?,?,?)");
      ) {
         ps.setBytes(1, Uuids.toBytes(player));
         ps.setString(2, kind);
         ps.setString(3, payload);
         ps.setLong(4, System.currentTimeMillis());
         ps.executeUpdate();
      } catch (SQLException var12) {
      }
   }

   public List<PendingStore.Pending> open(UUID player) {
      List<PendingStore.Pending> out = new ArrayList<>();

      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("SELECT id, kind, payload FROM ffa_pending WHERE uuid = ? AND delivered_at IS NULL ORDER BY id");
      ) {
         ps.setBytes(1, Uuids.toBytes(player));

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               out.add(new PendingStore.Pending(rs.getLong(1), rs.getString(2), rs.getString(3)));
            }
         }
      } catch (SQLException var14) {
      }

      return out;
   }

   public boolean markDelivered(long id) {
      try {
         boolean var5;
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement("UPDATE ffa_pending SET delivered_at = ? WHERE id = ? AND delivered_at IS NULL");
         ) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setLong(2, id);
            var5 = ps.executeUpdate() == 1;
         }

         return var5;
      } catch (SQLException var11) {
         return false;
      }
   }

   public record Pending(long id, String kind, String payload) {
   }
}
