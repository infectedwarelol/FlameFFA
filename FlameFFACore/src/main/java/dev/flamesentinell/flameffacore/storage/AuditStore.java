package dev.flamesentinell.flameffacore.storage;

import dev.flamesentinell.flameffacore.util.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class AuditStore {
   private final Database db;

   public AuditStore(Database db) {
      this.db = db;
   }

   public void staff(UUID actor, String actorName, String action, UUID target, String targetName, String detail) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement(
            "INSERT INTO ffa_staff_log (actor, actor_name, action, target, target_name, detail, created_at) VALUES (?,?,?,?,?,?,?)"
         );
      ) {
         ps.setBytes(1, Uuids.toBytes(actor));
         ps.setString(2, trim(actorName, 48));
         ps.setString(3, trim(action, 32));
         ps.setBytes(4, Uuids.toBytes(target));
         ps.setString(5, targetName == null ? null : trim(targetName, 48));
         ps.setString(6, trim(detail, 255));
         ps.setLong(7, System.currentTimeMillis());
         ps.executeUpdate();
      } catch (SQLException var15) {
      }
   }

   private static String trim(String value, int max) {
      if (value == null) {
         return "";
      } else {
         return value.length() <= max ? value : value.substring(0, max);
      }
   }
}
