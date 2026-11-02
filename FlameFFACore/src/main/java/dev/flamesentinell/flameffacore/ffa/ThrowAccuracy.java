package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;

public final class ThrowAccuracy implements Listener {
   private final FlameFFACore plugin;

   public ThrowAccuracy(FlameFFACore var1) {
      this.plugin = var1;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onLaunch(ProjectileLaunchEvent var1) {
      Projectile var2 = var1.getEntity();
      if (var2 instanceof EnderPearl || var2 instanceof WindCharge) {
         if (var2.getShooter() instanceof Player var3) {
            if (this.plugin.configs().main().getBoolean("throws.no-spread", true)) {
               Vector var9 = var3.getVelocity().clone();
               if (var3.isOnGround()) {
                  var9.setY(0);
               }

               Vector var5 = var2.getVelocity().clone().subtract(var9);
               double var6 = var5.length();
               if (!(var6 < 1.0E-4)) {
                  Vector var8 = var3.getEyeLocation().getDirection().normalize().multiply(var6);
                  var2.setVelocity(var8.add(var9));
               }
            }
         }
      }
   }
}
