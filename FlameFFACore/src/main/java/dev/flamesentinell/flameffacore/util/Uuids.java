package dev.flamesentinell.flameffacore.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class Uuids {
   private Uuids() {
   }

   public static byte[] toBytes(UUID uuid) {
      if (uuid == null) {
         return null;
      } else {
         ByteBuffer buf = ByteBuffer.allocate(16);
         buf.putLong(uuid.getMostSignificantBits());
         buf.putLong(uuid.getLeastSignificantBits());
         return buf.array();
      }
   }

   public static UUID fromBytes(byte[] bytes) {
      if (bytes != null && bytes.length == 16) {
         ByteBuffer buf = ByteBuffer.wrap(bytes);
         return new UUID(buf.getLong(), buf.getLong());
      } else {
         return null;
      }
   }

   public static UUID parseOrNull(String raw) {
      try {
         return UUID.fromString(raw);
      } catch (Exception var2) {
         return null;
      }
   }
}
