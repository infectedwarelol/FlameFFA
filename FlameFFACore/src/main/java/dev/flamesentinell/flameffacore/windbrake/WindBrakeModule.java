package dev.flamesentinell.flameffacore.windbrake;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.module.Module;
import dev.flamesentinell.flameffacore.module.ModuleCommand;
import dev.flamesentinell.flameffacore.module.ModuleConfig;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

public final class WindBrakeModule implements Module, Listener {
   private static final Vector DOWN = new Vector(0, -1, 0);
   private final FlameFFACore plugin;
   private final ModuleConfig config;
   private final Map<UUID, WindBrakeModule.Move> moves = new ConcurrentHashMap<>();
   private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
   private final Set<UUID> braked = ConcurrentHashMap.newKeySet();
   private volatile WindBrakeModule.Settings settings = WindBrakeModule.Settings.read(null);

   public WindBrakeModule(FlameFFACore plugin) {
      this.plugin = plugin;
      this.config = new ModuleConfig(plugin, "windbrake.yml", "FlameWindBrake", "config.yml");
   }

   @Override
   public String id() {
      return "windbrake";
   }

   @Override
   public void enable() {
      this.load();
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
   }

   @Override
   public void disable() {
      HandlerList.unregisterAll(this);
      this.moves.clear();
      this.cooldowns.clear();
      this.braked.clear();
   }

   @Override
   public void commands(Commands commands) {
      ModuleCommand.register(commands, "windbrake", "reload the wind brake config", List.of(), "windbrake.admin", null, (sender, label, args) -> {
         this.load();
         sender.sendMessage("FlameWindBrake config reloaded");
         return true;
      }, null);
   }

   private void load() {
      this.config.load();
      this.settings = WindBrakeModule.Settings.read(this.config.get());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      Location from = event.getFrom();
      Location to = event.getTo();
      this.moves.put(event.getPlayer().getUniqueId(), new WindBrakeModule.Move(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ(), Bukkit.getCurrentTick()));
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.moves.remove(uuid);
      this.cooldowns.remove(uuid);
   }

   private Vector motion(Player player) {
      WindBrakeModule.Move move = this.moves.get(player.getUniqueId());
      return move != null && Bukkit.getCurrentTick() - move.tick() <= 1 ? new Vector(move.dx(), move.dy(), move.dz()) : player.getVelocity();
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onThrow(PlayerLaunchProjectileEvent event) {
      if (event.getProjectile() instanceof WindCharge charge) {
         Player player = event.getPlayer();
         if (!player.getScoreboardTags().contains("windbrake_bypass")) {
            WindBrakeModule.Settings current = this.settings;
            Vector motion = this.motion(player);
            boolean brake = this.canBrake(current, player, motion);
            if (current.debug()) {
               this.plugin
                  .getSLF4JLogger()
                  .info(
                     "{} threw  motion {}  ground {}  pitch {}  ground below {}  brake {}",
                     player.getName(),
                     motion,
                     player.isOnGround(),
                     player.getLocation().getPitch(),
                     this.groundWithin(player, current.minGround()),
                     brake
                  );
            }

            if (brake) {
               Vector slowed = new Vector(
                  motion.getX() * current.horizontal(), motion.getY() * current.verticalMul() + current.vertical(), motion.getZ() * current.horizontal()
               );
               player.setVelocity(slowed);
               player.setFallDistance(0.0F);

               for (int tick = 1; tick <= current.holdTicks(); tick++) {
                  this.plugin.tasks().entityLater(player, () -> {
                     if (player.isOnline() && !player.isOnGround()) {
                        player.setVelocity(slowed);
                        player.setFallDistance(0.0F);
                     }
                  }, tick);
               }

               if (current.sound()) {
                  player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, SoundCategory.PLAYERS, current.volume(), current.pitch());
               }

               this.cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + current.cooldownMs());
               UUID chargeId = charge.getUniqueId();
               this.braked.add(chargeId);
               if (this.plugin.tasks().entity(charge, () -> {
                  this.braked.remove(chargeId);
                  charge.remove();
               }) == null) {
                  this.braked.remove(chargeId);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onHit(ProjectileHitEvent event) {
      if (!this.braked.isEmpty() && this.braked.remove(event.getEntity().getUniqueId())) {
         event.setCancelled(true);
         event.getEntity().remove();
      }
   }

   private boolean canBrake(WindBrakeModule.Settings current, Player player, Vector motion) {
      if (!current.enabled()) {
         return false;
      } else if (current.requireAirborne() && player.isOnGround()) {
         return false;
      } else {
         double fall = motion.getY();
         if (current.requireFalling() && fall >= 0.0) {
            return false;
         } else if (fall > current.minFall()) {
            return false;
         } else if (player.getLocation().getPitch() < current.minPitch()) {
            return false;
         } else if (this.groundWithin(player, current.minGround())) {
            return false;
         } else {
            Long until = this.cooldowns.get(player.getUniqueId());
            return until == null || until <= System.currentTimeMillis();
         }
      }
   }

   private boolean groundWithin(Player player, double distance) {
      if (distance <= 0.0) {
         return false;
      } else {
         Location feet = player.getLocation().add(0.0, 0.01, 0.0);
         return player.getWorld().rayTraceBlocks(feet, DOWN, distance, FluidCollisionMode.NEVER, true) != null;
      }
   }

   private record Move(double dx, double dy, double dz, int tick) {
   }

   private record Settings(
      boolean enabled,
      boolean requireAirborne,
      boolean requireFalling,
      boolean sound,
      boolean debug,
      double minFall,
      double minGround,
      double vertical,
      double verticalMul,
      double horizontal,
      int holdTicks,
      float minPitch,
      float volume,
      float pitch,
      long cooldownMs
   ) {
      static WindBrakeModule.Settings read(FileConfiguration cfg) {
         if (cfg == null) {
            return new WindBrakeModule.Settings(false, true, true, true, false, -0.25, 10.0, 0.0, 0.3, 0.8, 0, 55.0F, 1.0F, 0.67F, 400L);
         } else {
            return new WindBrakeModule.Settings(
               cfg.getBoolean("enabled", true),
               cfg.getBoolean("require-airborne", true),
               cfg.getBoolean("require-falling", true),
               cfg.getBoolean("sound-enabled", true),
               cfg.getBoolean("debug", false),
               cfg.getDouble("min-fall-velocity", -0.25),
               cfg.getDouble("min-ground-distance", 10.0),
               cfg.getDouble("vertical-add", 0.0),
               cfg.getDouble("vertical-multiplier", 0.3),
               cfg.getDouble("horizontal-multiplier", 0.8),
               Math.max(0, Math.min(200, cfg.getInt("hold-ticks", 0))),
               (float)cfg.getDouble("min-pitch-down", 55.0),
               (float)cfg.getDouble("sound-volume", 1.0),
               (float)cfg.getDouble("sound-pitch", 0.67),
               Math.max(0L, cfg.getLong("cooldown-ms", 400L))
            );
         }
      }
   }
}
