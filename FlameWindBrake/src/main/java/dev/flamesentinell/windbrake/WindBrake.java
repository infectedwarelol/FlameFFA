package dev.flamesentinell.windbrake;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class WindBrake extends JavaPlugin implements Listener {
   private final Map<UUID, WindBrake.Move> moves = new HashMap<>();
   private final Map<UUID, Long> cooldowns = new HashMap<>();
   private final Set<UUID> braked = new HashSet<>();
   private boolean enabled;
   private boolean requireAirborne;
   private boolean requireFalling;
   private boolean sound;
   private boolean debug;
   private double minFall;
   private double minGround;
   private double vertical;
   private double verticalMul;
   private double horizontal;
   private int holdTicks;
   private float minPitch;
   private float volume;
   private float pitch;
   private long cooldownMs;

   public void onEnable() {
      this.saveDefaultConfig();
      this.load();
      Bukkit.getPluginManager().registerEvents(this, this);
   }

   private void load() {
      this.reloadConfig();
      FileConfiguration var1 = this.getConfig();
      this.enabled = var1.getBoolean("enabled", true);
      this.requireAirborne = var1.getBoolean("require-airborne", true);
      this.requireFalling = var1.getBoolean("require-falling", true);
      this.minFall = var1.getDouble("min-fall-velocity", -0.25);
      this.minPitch = (float)var1.getDouble("min-pitch-down", 55.0);
      this.minGround = var1.getDouble("min-ground-distance", 10.0);
      this.cooldownMs = Math.max(0L, var1.getLong("cooldown-ms", 400L));
      this.verticalMul = var1.getDouble("vertical-multiplier", 0.3);
      this.vertical = var1.getDouble("vertical-add", 0.0);
      this.horizontal = var1.getDouble("horizontal-multiplier", 0.8);
      this.holdTicks = Math.max(0, var1.getInt("hold-ticks", 0));
      this.sound = var1.getBoolean("sound-enabled", true);
      this.volume = (float)var1.getDouble("sound-volume", 1.0);
      this.pitch = (float)var1.getDouble("sound-pitch", 0.67);
      this.debug = var1.getBoolean("debug", false);
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      this.load();
      var1.sendMessage("FlameWindBrake config reloaded");
      return true;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent var1) {
      Location var2 = var1.getFrom();
      Location var3 = var1.getTo();
      this.moves
         .put(
            var1.getPlayer().getUniqueId(),
            new WindBrake.Move(var3.getX() - var2.getX(), var3.getY() - var2.getY(), var3.getZ() - var2.getZ(), Bukkit.getCurrentTick())
         );
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      this.moves.remove(var2);
      this.cooldowns.remove(var2);
   }

   private Vector motion(Player var1) {
      WindBrake.Move var2 = this.moves.get(var1.getUniqueId());
      return var2 != null && Bukkit.getCurrentTick() - var2.tick() <= 1 ? new Vector(var2.dx(), var2.dy(), var2.dz()) : var1.getVelocity();
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onThrow(PlayerLaunchProjectileEvent var1) {
      if (var1.getProjectile() instanceof WindCharge var2) {
         Player var9 = var1.getPlayer();
         if (!var9.getScoreboardTags().contains("windbrake_bypass")) {
            Vector var4 = this.motion(var9);
            boolean var5 = this.canBrake(var9, var4);
            if (this.debug) {
               this.getLogger()
                  .info(
                     var9.getName()
                        + " threw  motion "
                        + var4
                        + "  ground "
                        + var9.isOnGround()
                        + "  pitch "
                        + var9.getLocation().getPitch()
                        + "  ground below "
                        + this.groundWithin(var9, this.minGround)
                        + "  brake "
                        + var5
                  );
            }

            if (var5) {
               Vector var6 = new Vector(var4.getX() * this.horizontal, var4.getY() * this.verticalMul + this.vertical, var4.getZ() * this.horizontal);
               var9.setVelocity(var6);
               var9.setFallDistance(0.0F);
               UUID var7 = var9.getUniqueId();

               for (int var8 = 1; var8 <= this.holdTicks; var8++) {
                  Bukkit.getScheduler().runTaskLater(this, () -> {
                     Player var2x = Bukkit.getPlayer(var7);
                     if (var2x != null && !var2x.isOnGround()) {
                        var2x.setVelocity(var6);
                        var2x.setFallDistance(0.0F);
                     }
                  }, var8);
               }

               if (this.sound) {
                  var9.getWorld().playSound(var9.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, SoundCategory.PLAYERS, this.volume, this.pitch);
               }

               this.cooldowns.put(var9.getUniqueId(), System.currentTimeMillis() + this.cooldownMs);
               UUID var10 = var2.getUniqueId();
               this.braked.add(var10);
               Bukkit.getScheduler().runTask(this, () -> {
                  this.braked.remove(var10);
                  Entity var2x = Bukkit.getEntity(var10);
                  if (var2x != null) {
                     var2x.remove();
                  }
               });
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onHit(ProjectileHitEvent var1) {
      if (this.braked.contains(var1.getEntity().getUniqueId())) {
         var1.setCancelled(true);
         var1.getEntity().remove();
      }
   }

   private boolean canBrake(Player var1, Vector var2) {
      if (!this.enabled) {
         return false;
      } else if (this.requireAirborne && var1.isOnGround()) {
         return false;
      } else {
         double var3 = var2.getY();
         if (this.requireFalling && var3 >= 0.0) {
            return false;
         } else if (var3 > this.minFall) {
            return false;
         } else if (var1.getLocation().getPitch() < this.minPitch) {
            return false;
         } else if (this.groundWithin(var1, this.minGround)) {
            return false;
         } else {
            Long var5 = this.cooldowns.get(var1.getUniqueId());
            return var5 == null || var5 <= System.currentTimeMillis();
         }
      }
   }

   private boolean groundWithin(Player var1, double var2) {
      if (var2 <= 0.0) {
         return false;
      } else {
         Location var4 = var1.getLocation().add(0.0, 0.01, 0.0);
         return var1.getWorld().rayTraceBlocks(var4, new Vector(0, -1, 0), var2, FluidCollisionMode.NEVER, true) != null;
      }
   }

   private record Move(double dx, double dy, double dz, int tick) {
   }
}
