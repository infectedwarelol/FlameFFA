package dev.flamesentinell.flameffacore.storage;

import dev.flamesentinell.flameffacore.util.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CosmeticStore {
   private final Database db;

   public CosmeticStore(Database db) {
      this.db = db;
   }

   public Map<String, String> load(UUID uuid) {
      Map<String, String> picked = new HashMap<>();

      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("SELECT slot, choice FROM ffa_cosmetics WHERE uuid = ?");
      ) {
         ps.setBytes(1, Uuids.toBytes(uuid));

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               picked.put(rs.getString(1), rs.getString(2));
            }
         }
      } catch (SQLException var14) {
      }

      return picked;
   }

   public void save(UUID uuid, String slot, String choice) {
      if (choice != null && !choice.equals("none")) {
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement(
               "INSERT INTO ffa_cosmetics (uuid, slot, choice) VALUES (?,?,?) ON DUPLICATE KEY UPDATE choice = VALUES(choice)"
            );
         ) {
            ps.setBytes(1, Uuids.toBytes(uuid));
            ps.setString(2, slot);
            ps.setString(3, choice);
            ps.executeUpdate();
         } catch (SQLException var12) {
         }
      } else {
         this.clear(uuid, slot);
      }
   }

   public void clear(UUID uuid, String slot) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("DELETE FROM ffa_cosmetics WHERE uuid = ? AND slot = ?");
      ) {
         ps.setBytes(1, Uuids.toBytes(uuid));
         ps.setString(2, slot);
         ps.executeUpdate();
      } catch (SQLException var11) {
      }
   }
}
