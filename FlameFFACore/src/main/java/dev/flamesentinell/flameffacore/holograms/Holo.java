package dev.flamesentinell.flameffacore.holograms;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Location;
import org.bukkit.entity.Display.Billboard;

final class Holo {
   final String name;
   final List<String> lines = new CopyOnWriteArrayList<>();
   volatile String world;
   volatile double x;
   volatile double y;
   volatile double z;
   volatile float yaw;
   volatile float scale = 1.0F;
   volatile Billboard billboard = Billboard.CENTER;
   volatile boolean shadow = true;
   volatile boolean seeThrough = false;
   volatile int lineWidth = 240;
   volatile Integer background = 0;
   volatile UUID entity;

   Holo(String name) {
      this.name = name;
   }

   void at(Location location) {
      this.world = location.getWorld().getName();
      this.x = location.getX();
      this.y = location.getY();
      this.z = location.getZ();
      this.yaw = location.getYaw();
   }

   int chunkX() {
      return (int)Math.floor(this.x) >> 4;
   }

   int chunkZ() {
      return (int)Math.floor(this.z) >> 4;
   }

   boolean live() {
      for (String line : this.lines) {
         if (line.contains("{online}") || line.contains("{max}")) {
            return true;
         }
      }

      return false;
   }
}
