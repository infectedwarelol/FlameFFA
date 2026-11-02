package dev.flamesentinell.flameholo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Display.Billboard;

final class Holo {
   final String name;
   String world;
   double x;
   double y;
   double z;
   float yaw;
   final List<String> lines = new ArrayList<>();
   float scale = 1.0F;
   Billboard billboard = Billboard.CENTER;
   boolean shadow = true;
   boolean seeThrough = false;
   int lineWidth = 240;
   Integer background = 0;
   UUID entity;

   Holo(String var1) {
      this.name = var1;
   }

   void at(Location var1) {
      this.world = var1.getWorld().getName();
      this.x = var1.getX();
      this.y = var1.getY();
      this.z = var1.getZ();
      this.yaw = var1.getYaw();
   }

   int chunkX() {
      return (int)Math.floor(this.x) >> 4;
   }

   int chunkZ() {
      return (int)Math.floor(this.z) >> 4;
   }

   boolean live() {
      String var1 = String.join("\n", this.lines);
      return var1.contains("{online}") || var1.contains("{max}");
   }
}
