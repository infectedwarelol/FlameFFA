package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import dev.flamesentinell.flameffacore.util.Tasks;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ArenaSweeper implements Listener {
   private final FlameFFACore plugin;
   private final Set<Location> placed = Collections.synchronizedSet(new LinkedHashSet<>());
   private final Map<Location, BlockData> broken = Collections.synchronizedMap(new LinkedHashMap<>());
   private ScheduledTask task;
   private volatile int left;
   private final Map<Location, BlockData> lights = Collections.synchronizedMap(new LinkedHashMap<>());
   private ScheduledTask lightTask;
   private final Set<Location> logged = Collections.synchronizedSet(new LinkedHashSet<>());
   private final Set<Entity> drops = ConcurrentHashMap.newKeySet();
   private static final BlockFace[] SIDES = new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
   private static final int ORPHAN_CAP = 4096;

   public ArenaSweeper(FlameFFACore var1) {
      this.plugin = var1;
   }

   public void placed(Block var1) {
      this.placed.add(var1.getLocation());
   }

   public void waterlogged(Block var1) {
      Location var2 = var1.getLocation();
      if (!this.placed.contains(var2)) {
         this.logged.add(var2);
         this.placed.add(var2);
      }
   }

   public void replaced(Block var1, BlockData var2) {
      Location var3 = var1.getLocation();
      if (!this.placed.contains(var3)) {
         this.broken.putIfAbsent(var3, var2.clone());
      }

      this.placed.add(var3);
      if (var2.getMaterial() == Material.LIGHT) {
         if (this.lights.putIfAbsent(var3, var2.clone()) == null && Tasks.FOLIA) {
            this.watchLight(var3);
         } else if (!Tasks.FOLIA) {
            this.watchLights();
         }
      }
   }

   private void watchLight(Location at) {
      this.plugin.tasks().regionTimer(at, task -> {
         BlockData original = this.lights.get(at);
         if (original == null) {
            task.cancel();
         } else {
            Block block = at.getBlock();
            if (block.getType() == Material.LIGHT) {
               this.lights.remove(at);
               task.cancel();
            } else if (block.getType().isAir()) {
               block.setBlockData(original, false);
               this.placed.remove(at);
               this.broken.remove(at);
               this.lights.remove(at);
               task.cancel();
            }
         }
      }, 5L, 5L);
   }

   private synchronized void watchLights() {
      if (this.lightTask == null) {
         this.lightTask = this.plugin.tasks().globalTimer(task -> {
            synchronized (this.lights) {
            Iterator var1 = this.lights.entrySet().iterator();

            while (var1.hasNext()) {
               Entry var2 = (Entry)var1.next();
               Block var3 = ((Location)var2.getKey()).getBlock();
               if (var3.getType() == Material.LIGHT) {
                  var1.remove();
               } else if (var3.getType().isAir()) {
                  var3.setBlockData((BlockData)var2.getValue(), false);
                  this.placed.remove(var2.getKey());
                  this.broken.remove(var2.getKey());
                  var1.remove();
               }
            }
            }

            if (this.lights.isEmpty()) {
               task.cancel();
               synchronized (this) {
                  this.lightTask = null;
               }
            }
         }, 5L, 5L);
      }
   }

   public void broke(Block var1) {
      Location var2 = var1.getLocation();
      if (!this.placed.remove(var2)) {
         this.broken.putIfAbsent(var2, var1.getBlockData().clone());
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onFlow(BlockFromToEvent var1) {
      if (!this.placed.isEmpty() && this.placed.contains(var1.getBlock().getLocation())) {
         this.placed.add(var1.getToBlock().getLocation());
      }
   }

   public void start() {
      this.stop();
      this.left = this.seconds();
      if (this.left > 0) {
         this.task = this.plugin.tasks().globalTimer(task -> {
            this.left--;
            int var1 = this.plugin.configs().main().getInt("sweep.warn-seconds", 10);
            if (this.left == var1 && var1 > 0) {
               Bukkit.broadcast(this.plugin.configs().msg("sweep.warning", "seconds", String.valueOf(var1)));
            }

            if (this.left <= 0) {
               if (Tasks.FOLIA) {
                  this.sweepRegions(true);
               } else {
                  this.sweep(true);
               }

               this.left = this.seconds();
            }
         }, 20L, 20L);
      }
   }

   public void stop() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
   }

   private int seconds() {
      return this.plugin.configs().main().getInt("sweep.seconds", 180);
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDropSpawn(EntitySpawnEvent event) {
      if (Tasks.FOLIA && sweepable(event.getEntity())) {
         this.drops.add(event.getEntity());
      }
   }

   @EventHandler
   public void onDropGone(EntityRemoveFromWorldEvent event) {
      if (!this.drops.isEmpty()) {
         this.drops.remove(event.getEntity());
      }
   }

   private static boolean sweepable(Entity entity) {
      return entity instanceof Item || entity instanceof ExperienceOrb || entity instanceof AbstractArrow arrow && !(arrow instanceof Trident);
   }

   public int sweepRegions(boolean announce) {
      FileConfiguration cfg = this.plugin.configs().main();
      AtomicInteger blocks = new AtomicInteger();
      AtomicInteger items = new AtomicInteger();
      List<ArenaSweeper.Job> jobs = new ArrayList<>();
      if (cfg.getBoolean("sweep.blocks", true)) {
         List<Location> cleared;
         synchronized (this.placed) {
            cleared = new ArrayList<>(this.placed);
            this.placed.clear();
         }

         Set<Location> waterlogged;
         synchronized (this.logged) {
            waterlogged = new HashSet<>(this.logged);
            this.logged.clear();
         }

         Map<Location, BlockData> restore;
         synchronized (this.broken) {
            restore = new LinkedHashMap<>(this.broken);
            this.broken.clear();
         }

         for (Location at : cleared) {
            jobs.add(new ArenaSweeper.Job(at, null, () -> {
               if (this.clear(at.getBlock(), waterlogged.contains(at))) {
                  blocks.incrementAndGet();
               }

               blocks.addAndGet(this.orphans(List.of(at), 0));
            }));
         }

         for (Entry<Location, BlockData> entry : restore.entrySet()) {
            jobs.add(new ArenaSweeper.Job(entry.getKey(), null, () -> {
               entry.getKey().getBlock().setBlockData(entry.getValue(), false);
               blocks.incrementAndGet();
            }));
         }

         int radius = cfg.getInt("sweep.orphan-scan-radius", 16);
         if (radius > 0) {
            for (Player player : this.plugin.arena().spawn().getWorld().getPlayers()) {
               if (!player.getScoreboardTags().contains("smbot_bot")) {
                  jobs.add(new ArenaSweeper.Job(null, player, () -> blocks.addAndGet(this.orphansAround(player, radius))));
               }
            }
         }
      }

      if (cfg.getBoolean("sweep.items", true)) {
         for (Entity drop : new ArrayList<>(this.drops)) {
            this.drops.remove(drop);
            jobs.add(new ArenaSweeper.Job(null, drop, () -> {
               if (drop.isValid()) {
                  drop.remove();
                  items.incrementAndGet();
               }
            }));
         }
      }

      AtomicInteger pending = new AtomicInteger(jobs.size());
      Runnable finish = () -> {
         if (pending.decrementAndGet() == 0 && announce && (blocks.get() > 0 || items.get() > 0)) {
            Bukkit.broadcast(this.plugin.configs().msg("sweep.done", "blocks", String.valueOf(blocks.get()), "items", String.valueOf(items.get())));
         }
      };

      for (ArenaSweeper.Job job : jobs) {
         Runnable wrapped = () -> {
            try {
               job.work().run();
            } finally {
               finish.run();
            }
         };
         ScheduledTask scheduled = job.at() != null ? this.plugin.tasks().region(job.at(), wrapped) : this.plugin.tasks().entity(job.owner(), wrapped);
         if (scheduled == null) {
            finish.run();
         }
      }

      return jobs.size();
   }

   private int orphansAround(Player player, int radius) {
      World world = player.getWorld();
      int half = Math.max(4, radius / 2);
      int x = player.getLocation().getBlockX();
      int y = player.getLocation().getBlockY();
      int z = player.getLocation().getBlockZ();
      List<Location> seeds = new ArrayList<>();

      for (int bx = x - radius; bx <= x + radius; bx++) {
         for (int bz = z - radius; bz <= z + radius; bz++) {
            if (world.isChunkLoaded(bx >> 4, bz >> 4)) {
               for (int by = Math.max(world.getMinHeight(), y - half); by <= Math.min(world.getMaxHeight() - 1, y + half); by++) {
                  Block block = world.getBlockAt(bx, by, bz);
                  if (flowing(block)) {
                     seeds.add(block.getLocation());
                  }
               }
            }
         }
      }

      return seeds.isEmpty() ? 0 : this.orphans(seeds, 0);
   }

   private record Job(Location at, Entity owner, Runnable work) {
   }

   public int sweep(boolean var1) {
      FileConfiguration var2 = this.plugin.configs().main();
      int var3 = 0;
      int var4 = 0;
      if (var2.getBoolean("sweep.blocks", true)) {
         ArrayList<Location> var5;
         synchronized (this.placed) {
            var5 = new ArrayList<>(this.placed);
         }

         for (Location var7 : var5) {
            if (this.clear(var7.getBlock())) {
               var3++;
            }
         }

         this.placed.clear();
         this.logged.clear();

         synchronized (this.broken) {
            for (Entry var12 : this.broken.entrySet()) {
               ((Location)var12.getKey()).getBlock().setBlockData((BlockData)var12.getValue(), false);
               var3++;
            }
         }

         this.broken.clear();
         var3 += this.orphans(var5, var2.getInt("sweep.orphan-scan-radius", 16));
      }

      if (var2.getBoolean("sweep.items", true)) {
         World var9 = this.plugin.arena().spawn().getWorld();

         for (Entity var13 : var9.getEntities()) {
            if (var13 instanceof Item || var13 instanceof ExperienceOrb || var13 instanceof AbstractArrow var8 && !(var8 instanceof Trident)) {
               var13.remove();
               var4++;
            }
         }
      }

      if (var1 && (var3 > 0 || var4 > 0)) {
         Bukkit.broadcast(this.plugin.configs().msg("sweep.done", "blocks", String.valueOf(var3), "items", String.valueOf(var4)));
      }

      return var3 + var4;
   }

   private boolean clear(Block var1) {
      return this.clear(var1, this.logged.contains(var1.getLocation()));
   }

   private boolean clear(Block var1, boolean wasLogged) {
      if (var1.getBlockData() instanceof Waterlogged var2 && var1.getType() != Material.WATER && wasLogged) {
         if (!var2.isWaterlogged()) {
            return false;
         } else {
            var2.setWaterlogged(false);
            var1.setBlockData(var2, false);
            return true;
         }
      } else if (var1.getType().isAir()) {
         return false;
      } else {
         var1.setType(Material.AIR, var1.getType() == Material.WATER || var1.getBlockData() instanceof Waterlogged var5 && var5.isWaterlogged());
         return true;
      }
   }

   private static boolean flowing(Block var0) {
      Material var1 = var0.getType();
      return (var1 == Material.WATER || var1 == Material.LAVA) && var0.getBlockData() instanceof Levelled var2 && var2.getLevel() > 0;
   }

   private static boolean feeds(Block var0, Material var1) {
      Material var2 = var0.getType();
      if (var2 == var1) {
         return !(var0.getBlockData() instanceof Levelled var5 && var5.getLevel() != 0);
      } else if (var1 != Material.WATER) {
         return false;
      } else {
         return var2 != Material.KELP
               && var2 != Material.KELP_PLANT
               && var2 != Material.SEAGRASS
               && var2 != Material.TALL_SEAGRASS
               && var2 != Material.BUBBLE_COLUMN
            ? var0.getBlockData() instanceof Waterlogged var3 && var3.isWaterlogged()
            : true;
      }
   }

   private int orphans(List<Location> var1, int var2) {
      HashSet var3 = new HashSet();
      ArrayList<Block> var4 = new ArrayList<>();

      for (Location var6 : var1) {
         if (var6.isChunkLoaded()) {
            Block var7 = var6.getBlock();
            var4.add(var7);

            for (BlockFace var11 : SIDES) {
               var4.add(var7.getRelative(var11));
            }
         }
      }

      if (var2 > 0) {
         World var18 = this.plugin.arena().spawn().getWorld();
         int var20 = Math.max(4, var2 / 2);

         for (Player var24 : var18.getPlayers()) {
            if (!var24.getScoreboardTags().contains("smbot_bot")) {
               int var26 = var24.getLocation().getBlockX();
               int var28 = var24.getLocation().getBlockY();
               int var30 = var24.getLocation().getBlockZ();

               for (int var12 = var26 - var2; var12 <= var26 + var2; var12++) {
                  for (int var13 = var30 - var2; var13 <= var30 + var2; var13++) {
                     if (var18.isChunkLoaded(var12 >> 4, var13 >> 4)) {
                        for (int var14 = Math.max(var18.getMinHeight(), var28 - var20); var14 <= Math.min(var18.getMaxHeight() - 1, var28 + var20); var14++) {
                           Block var15 = var18.getBlockAt(var12, var14, var13);
                           if (flowing(var15)) {
                              var4.add(var15);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      int var19 = 0;

      for (Block var23 : var4) {
         if (!var3.contains(var23) && flowing(var23)) {
            Material var25 = var23.getType();
            ArrayList<Block> var27 = new ArrayList<>();
            ArrayDeque var29 = new ArrayDeque();
            var29.add(var23);
            var3.add(var23);
            boolean var31 = false;

            while (!var29.isEmpty() && !var31) {
               Block var32 = (Block)var29.poll();
               var27.add(var32);
               if (var27.size() > 4096) {
                  var31 = true;
                  break;
               }

               for (BlockFace var16 : SIDES) {
                  Block var17 = var32.getRelative(var16);
                  if (!var17.getWorld().isChunkLoaded(var17.getX() >> 4, var17.getZ() >> 4)) {
                     var31 = true;
                     break;
                  }

                  if (feeds(var17, var25)) {
                     var31 = true;
                     break;
                  }

                  if (var17.getType() == var25 && flowing(var17) && var3.add(var17)) {
                     var29.add(var17);
                  }
               }
            }

            if (var31) {
               var3.addAll(var29);
            } else {
               for (Block var35 : var27) {
                  var35.setType(Material.AIR, false);
                  var19++;
               }
            }
         }
      }

      return var19;
   }

   public void shutdown() {
      this.stop();
      Tasks.cancel(this.lightTask);
      this.lightTask = null;
      try {
         this.sweep(false);
      } catch (RuntimeException offThread) {
         this.plugin.getSLF4JLogger().warn("arena sweep on shutdown was refused by the server, placed blocks stay until the next sweep");
      }
   }
}
