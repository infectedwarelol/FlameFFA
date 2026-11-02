package dev.flamesentinell.flameffacore.storage;

import dev.flamesentinell.flameffacore.util.Uuids;
import dev.flamesentinell.flameffacore.villager.SkinFetcher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class VillagerStore {
   private final Database db;

   public VillagerStore(Database db) {
      this.db = db;
   }

   public List<VillagerStore.Row> loadAll() {
      List<VillagerStore.Row> out = new ArrayList<>();

      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement(
            "SELECT id, entity, preset, world, x, y, z, yaw, skin_uuid, skin_name, skin_value, skin_sig FROM ffa_villagers"
         );
         ResultSet rs = ps.executeQuery();
      ) {
         while (rs.next()) {
            World world = Bukkit.getWorld(rs.getString(4));
            if (world != null) {
               byte[] raw = rs.getBytes(2);
               byte[] skinId = rs.getBytes(9);
               SkinFetcher.Skin skin = skinId != null && rs.getString(11) != null
                  ? new SkinFetcher.Skin(Uuids.fromBytes(skinId), rs.getString(10), rs.getString(11), rs.getString(12))
                  : null;
               out.add(
                  new VillagerStore.Row(
                     rs.getLong(1),
                     raw == null ? null : Uuids.fromBytes(raw),
                     rs.getString(3),
                     new Location(world, rs.getDouble(5), rs.getDouble(6), rs.getDouble(7), rs.getFloat(8), 0.0F),
                     skin
                  )
               );
            }
         }
      } catch (SQLException var15) {
      }

      return out;
   }

   public long add(String preset, Location loc, UUID entity, String placedBy) {
      if (loc.getWorld() == null) {
         return -1L;
      } else {
         try {
            long var8;
            try (
               Connection c = this.db.connection();
               PreparedStatement ps = c.prepareStatement(
                  "INSERT INTO ffa_villagers (entity, preset, world, x, y, z, yaw, placed_by, placed_at) VALUES (?,?,?,?,?,?,?,?,?)", 1
               );
            ) {
               ps.setBytes(1, Uuids.toBytes(entity));
               ps.setString(2, preset);
               ps.setString(3, loc.getWorld().getName());
               ps.setDouble(4, loc.getX());
               ps.setDouble(5, loc.getY());
               ps.setDouble(6, loc.getZ());
               ps.setFloat(7, loc.getYaw());
               ps.setString(8, placedBy.length() > 48 ? placedBy.substring(0, 48) : placedBy);
               ps.setLong(9, System.currentTimeMillis());
               ps.executeUpdate();

               try (ResultSet keys = ps.getGeneratedKeys()) {
                  var8 = keys.next() ? keys.getLong(1) : -1L;
               }
            }

            return var8;
         } catch (SQLException var16) {
            return -1L;
         }
      }
   }

   public void rebind(long id, UUID entity) {
      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("UPDATE ffa_villagers SET entity = ? WHERE id = ?");
      ) {
         ps.setBytes(1, Uuids.toBytes(entity));
         ps.setLong(2, id);
         ps.executeUpdate();
      } catch (SQLException var12) {
      }
   }

   public boolean setSkin(long id, SkinFetcher.Skin skin) {
      try {
         boolean var6;
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement("UPDATE ffa_villagers SET skin_uuid = ?, skin_name = ?, skin_value = ?, skin_sig = ? WHERE id = ?");
         ) {
            ps.setBytes(1, skin == null ? null : Uuids.toBytes(skin.uuid()));
            ps.setString(2, skin == null ? null : skin.name());
            ps.setString(3, skin == null ? null : skin.value());
            ps.setString(4, skin == null ? null : skin.signature());
            ps.setLong(5, id);
            var6 = ps.executeUpdate() > 0;
         }

         return var6;
      } catch (SQLException var12) {
         return false;
      }
   }

   public boolean remove(long id) {
      try {
         boolean var5;
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement("DELETE FROM ffa_villagers WHERE id = ?");
         ) {
            ps.setLong(1, id);
            var5 = ps.executeUpdate() > 0;
         }

         return var5;
      } catch (SQLException var11) {
         return false;
      }
   }

   public int removeByPreset(String preset) {
      try {
         int var4;
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement("DELETE FROM ffa_villagers WHERE preset = ?");
         ) {
            ps.setString(1, preset);
            var4 = ps.executeUpdate();
         }

         return var4;
      } catch (SQLException var10) {
         return 0;
      }
   }

   public record Row(long id, UUID entity, String preset, Location location, SkinFetcher.Skin skin) {
   }
}
