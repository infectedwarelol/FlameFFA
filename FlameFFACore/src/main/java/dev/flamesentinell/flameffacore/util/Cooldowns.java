package dev.flamesentinell.flameffacore.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Cooldowns {
   private final Map<UUID, Map<String, Long>> expiry = new ConcurrentHashMap<>();

   public void set(UUID uuid, String bucket, long seconds) {
      if (seconds <= 0L) {
         this.clear(uuid, bucket);
      } else {
         this.expiry.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(bucket, System.currentTimeMillis() + seconds * 1000L);
      }
   }

   public long remaining(UUID uuid, String bucket) {
      Map<String, Long> buckets = this.expiry.get(uuid);
      if (buckets == null) {
         return 0L;
      } else {
         Long until = buckets.get(bucket);
         if (until == null) {
            return 0L;
         } else {
            long left = until - System.currentTimeMillis();
            if (left <= 0L) {
               buckets.remove(bucket);
               return 0L;
            } else {
               return (left + 999L) / 1000L;
            }
         }
      }
   }

   public boolean active(UUID uuid, String bucket) {
      return this.remaining(uuid, bucket) > 0L;
   }

   public void clear(UUID uuid, String bucket) {
      Map<String, Long> buckets = this.expiry.get(uuid);
      if (buckets != null) {
         buckets.remove(bucket);
      }
   }

   public void clearAll(UUID uuid) {
      this.expiry.remove(uuid);
   }
}
