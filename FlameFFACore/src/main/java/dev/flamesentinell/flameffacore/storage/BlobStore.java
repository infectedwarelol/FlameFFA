package dev.flamesentinell.flameffacore.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

public final class BlobStore {
   public static final String KIND_RANK_KIT = "rankkit";
   public static final String KIND_CRATE_LOOT = "crateloot";
   public static final String KIND_VILLAGER = "villager";
   private static final int FORMAT = 1;
   private final Database db;

   public BlobStore(Database db) {
      this.db = db;
   }

   public static String encode(ItemStack[] contents) {
      return "1:" + Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents));
   }

   public static ItemStack[] decode(String payload) {
      int split = payload.indexOf(58);
      if (split < 0) {
         throw new IllegalArgumentException("malformed payload");
      } else {
         int version = Integer.parseInt(payload.substring(0, split));
         if (version != 1) {
            throw new IllegalArgumentException("unsupported payload format " + version);
         } else {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(payload.substring(split + 1)));
         }
      }
   }

   public boolean save(String kind, String id, ItemStack[] contents, String setBy) {
      try {
         boolean var7;
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement(
               "INSERT INTO ffa_blobs (kind, blob_id, payload, set_by, set_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE payload = VALUES(payload), set_by = VALUES(set_by), set_at = VALUES(set_at)"
            );
         ) {
            ps.setString(1, kind);
            ps.setString(2, id);
            ps.setString(3, encode(contents));
            ps.setString(4, setBy.length() > 48 ? setBy.substring(0, 48) : setBy);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            var7 = true;
         }

         return var7;
      } catch (SQLException var13) {
         return false;
      }
   }

   public boolean delete(String kind, String id) {
      try {
         boolean var5;
         try (
            Connection c = this.db.connection();
            PreparedStatement ps = c.prepareStatement("DELETE FROM ffa_blobs WHERE kind = ? AND blob_id = ?");
         ) {
            ps.setString(1, kind);
            ps.setString(2, id);
            var5 = ps.executeUpdate() > 0;
         }

         return var5;
      } catch (SQLException var11) {
         return false;
      }
   }

   public Map<String, BlobStore.Blob> loadAll(String kind) {
      Map<String, BlobStore.Blob> out = new LinkedHashMap<>();

      try (
         Connection c = this.db.connection();
         PreparedStatement ps = c.prepareStatement("SELECT blob_id, payload, set_by, set_at FROM ffa_blobs WHERE kind = ?");
      ) {
         ps.setString(1, kind);

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               String id = rs.getString(1);

               try {
                  out.put(id, new BlobStore.Blob(kind, id, decode(rs.getString(2)), rs.getString(3), rs.getLong(4)));
               } catch (Exception var11) {
               }
            }
         }
      } catch (SQLException var15) {
      }

      return out;
   }

   public record Blob(String kind, String id, ItemStack[] contents, String setBy, long setAt) {
      public int filledSlots() {
         int n = 0;

         for (ItemStack stack : this.contents) {
            if (stack != null && !stack.getType().isAir()) {
               n++;
            }
         }

         return n;
      }
   }
}
