package dev.flamesentinell.flameffacore.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class CrateBlockStore {
   private final Database db;

   public CrateBlockStore(Database db) {
      this.db = db;
   }

   public List<CrateBlockStore.Placement> loadAll() {
      List<CrateBlockStore.Placement> out = new ArrayList<>();

      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("SELECT world, x, y, z, crate_id, placed_by FROM ffa_crate_blocks");
         ResultSet rs = ps.executeQuery();
      ) {
         while (rs.next()) {
            World world = Bukkit.getWorld(rs.getString(1));
            if (world != null) {
               out.add(new CrateBlockStore.Placement(rs.getString(5), new Location(world, rs.getInt(2), rs.getInt(3), rs.getInt(4)), rs.getString(6)));
            }
         }
      } catch (SQLException var13) {
      }

      return out;
   }

   public boolean add(String crateId, Location loc, String placedBy) {
      if (loc.getWorld() == null) {
         return false;
      } else {
         try {
            boolean var6;
            try (
               Connection c = this.db.connection();
               PreparedStatement ps = c.prepareStatement(
                  "INSERT INTO ffa_crate_blocks (world, x, y, z, crate_id, placed_by, placed_at) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE crate_id = VALUES(crate_id), placed_by = VALUES(placed_by), placed_at = VALUES(placed_at)"
               );
            ) {
               ps.setString(1, loc.getWorld().getName());
               ps.setInt(2, loc.getBlockX());
               ps.setInt(3, loc.getBlockY());
               ps.setInt(4, loc.getBlockZ());
               ps.setString(5, crateId);
               ps.setString(6, placedBy.length() > 48 ? placedBy.substring(0, 48) : placedBy);
               ps.setLong(7, System.currentTimeMillis());
               ps.executeUpdate();
               var6 = true;
            }

            return var6;
         } catch (SQLException var12) {
            return false;
         }
      }
   }

   public boolean remove(Location loc) {
      if (loc.getWorld() == null) {
         return false;
      } else {
         try {
            boolean var4;
            try (
               Connection c = this.db.connection();
               PreparedStatement ps = c.prepareStatement("DELETE FROM ffa_crate_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?");
            ) {
               ps.setString(1, loc.getWorld().getName());
               ps.setInt(2, loc.getBlockX());
               ps.setInt(3, loc.getBlockY());
               ps.setInt(4, loc.getBlockZ());
               var4 = ps.executeUpdate() > 0;
            }

            return var4;
         } catch (SQLException var10) {
            return false;
         }
      }
   }

   public int count() {
      try {
         int var4;
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM ffa_crate_blocks");
            ResultSet rs = ps.executeQuery();
         ) {
            var4 = rs.next() ? rs.getInt(1) : 0;
         }

         return var4;
      } catch (SQLException var12) {
         return 0;
      }
   }

   public record Placement(String crateId, Location location, String placedBy) {
   }
}
