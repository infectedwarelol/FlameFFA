package dev.flamesentinell.flameffacore.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class Tasks {
   public static final boolean FOLIA = detectFolia();
   private final Plugin plugin;
   private final Set<ScheduledTask> repeating = ConcurrentHashMap.newKeySet();

   public Tasks(Plugin plugin) {
      this.plugin = plugin;
   }

   private static boolean detectFolia() {
      try {
         Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
         return true;
      } catch (ClassNotFoundException missing) {
         return false;
      }
   }

   private static long atLeastOne(long ticks) {
      return Math.max(1L, ticks);
   }

   private ScheduledTask track(ScheduledTask task) {
      if (task == null) {
         return null;
      } else {
         this.repeating.removeIf(Tasks::done);
         this.repeating.add(task);
         return task;
      }
   }

   private static boolean done(ScheduledTask task) {
      return task.isCancelled() || task.getExecutionState() == ScheduledTask.ExecutionState.FINISHED;
   }

   private boolean live() {
      return this.plugin.isEnabled();
   }

   public ScheduledTask global(Runnable action) {
      return !this.live() ? null : Bukkit.getGlobalRegionScheduler().run(this.plugin, task -> action.run());
   }

   public ScheduledTask globalLater(Runnable action, long delayTicks) {
      return !this.live() ? null : Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, task -> action.run(), atLeastOne(delayTicks));
   }

   public ScheduledTask globalTimer(Consumer<ScheduledTask> action, long delayTicks, long periodTicks) {
      return !this.live()
         ? null
         : this.track(Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, action, atLeastOne(delayTicks), atLeastOne(periodTicks)));
   }

   public ScheduledTask entity(Entity entity, Runnable action) {
      return this.entityLater(entity, action, 1L);
   }

   public ScheduledTask entityLater(Entity entity, Runnable action, long delayTicks) {
      return !this.live() || entity == null ? null : entity.getScheduler().runDelayed(this.plugin, task -> action.run(), null, atLeastOne(delayTicks));
   }

   public ScheduledTask entityTimer(Entity entity, Consumer<ScheduledTask> action, Runnable retired, long delayTicks, long periodTicks) {
      if (!this.live() || entity == null) {
         return null;
      }

      AtomicReference<ScheduledTask> scheduled = new AtomicReference<>();
      ScheduledTask task = entity.getScheduler().runAtFixedRate(this.plugin, action, () -> {
         ScheduledTask own = scheduled.get();
         if (own != null) {
            this.repeating.remove(own);
         }

         if (retired != null) {
            retired.run();
         }
      }, atLeastOne(delayTicks), atLeastOne(periodTicks));
      scheduled.set(task);
      return this.track(task);
   }

   public void runOn(Entity entity, Runnable action) {
      if (entity != null) {
         if (Bukkit.isOwnedByCurrentRegion(entity)) {
            action.run();
         } else {
            this.entity(entity, action);
         }
      }
   }

   public ScheduledTask region(Location location, Runnable action) {
      return this.regionLater(location, action, 1L);
   }

   public ScheduledTask regionLater(Location location, Runnable action, long delayTicks) {
      return !this.live() || location == null || location.getWorld() == null
         ? null
         : Bukkit.getRegionScheduler().runDelayed(this.plugin, location, task -> action.run(), atLeastOne(delayTicks));
   }

   public ScheduledTask regionTimer(Location location, Consumer<ScheduledTask> action, long delayTicks, long periodTicks) {
      return !this.live() || location == null || location.getWorld() == null
         ? null
         : this.track(Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, location, action, atLeastOne(delayTicks), atLeastOne(periodTicks)));
   }

   public ScheduledTask chunk(World world, int chunkX, int chunkZ, Runnable action) {
      return !this.live() || world == null ? null : Bukkit.getRegionScheduler().run(this.plugin, world, chunkX, chunkZ, task -> action.run());
   }

   public void runAt(Location location, Runnable action) {
      if (location != null && location.getWorld() != null) {
         if (Bukkit.isOwnedByCurrentRegion(location)) {
            action.run();
         } else {
            this.region(location, action);
         }
      }
   }

   public ScheduledTask async(Runnable action) {
      return !this.live() ? null : Bukkit.getAsyncScheduler().runNow(this.plugin, task -> action.run());
   }

   public ScheduledTask asyncLater(Runnable action, long delayTicks) {
      return !this.live() ? null : Bukkit.getAsyncScheduler().runDelayed(this.plugin, task -> action.run(), atLeastOne(delayTicks) * 50L, TimeUnit.MILLISECONDS);
   }

   public ScheduledTask asyncTimer(Consumer<ScheduledTask> action, long delayTicks, long periodTicks) {
      return !this.live()
         ? null
         : this.track(
            Bukkit.getAsyncScheduler().runAtFixedRate(this.plugin, action, atLeastOne(delayTicks) * 50L, atLeastOne(periodTicks) * 50L, TimeUnit.MILLISECONDS)
         );
   }

   public static void move(Entity entity, Location to) {
      if (FOLIA) {
         entity.teleportAsync(to);
      } else {
         entity.teleport(to);
      }
   }

   public static void move(Entity entity, Location to, TeleportCause cause) {
      if (FOLIA) {
         entity.teleportAsync(to, cause);
      } else {
         entity.teleport(to, cause);
      }
   }

   public static boolean viewedFromOtherRegion(Inventory inventory, Entity owner) {
      if (!FOLIA) {
         return false;
      } else if (owner != null && !Bukkit.isOwnedByCurrentRegion(owner)) {
         return true;
      } else {
         for (HumanEntity viewer : inventory.getViewers()) {
            if (!Bukkit.isOwnedByCurrentRegion(viewer)) {
               return true;
            }
         }

         return false;
      }
   }

   public static void cancel(ScheduledTask task) {
      if (task != null) {
         task.cancel();
      }
   }

   public void cancelAll() {
      for (ScheduledTask task : this.repeating) {
         task.cancel();
      }

      this.repeating.clear();
      Bukkit.getGlobalRegionScheduler().cancelTasks(this.plugin);
      Bukkit.getAsyncScheduler().cancelTasks(this.plugin);
   }
}
