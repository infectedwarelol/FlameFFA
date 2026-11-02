package dev.flamesentinell.flametweaks;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class Watch implements Listener, CommandExecutor {
   private final FlameTweaks plugin;
   private final Map<UUID, Writer> open = new HashMap<>();
   private int tick;
   private final Map<UUID, Integer> lastFight = new HashMap<>();
   private final Map<UUID, Integer> nextTrap = new HashMap<>();
   private final Map<UUID, int[]> pending = new HashMap<>();
   private final Random rng = new Random();

   Watch(FlameTweaks var1) {
      this.plugin = var1;
   }

   void start() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      Bukkit.getScheduler().runTaskTimer(this.plugin, this::onTick, 1L, 1L);

      for (Player var2 : Bukkit.getOnlinePlayers()) {
         if (this.watched(var2.getName())) {
            this.begin(var2);
         }
      }
   }

   void stop() {
      for (UUID var2 : new ArrayList<>(this.open.keySet())) {
         this.end(var2, "shutdown");
      }
   }

   private List<String> names() {
      return this.plugin.getConfig().getStringList("watch-players");
   }

   private boolean watched(String var1) {
      for (String var3 : this.names()) {
         if (var3.equalsIgnoreCase(var1)) {
            return true;
         }
      }

      return false;
   }

   private static String f(double var0) {
      return String.format(Locale.ROOT, "%.3f", var0);
   }

   private static String q(String var0) {
      return "\"" + var0.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
   }

   private static String item(ItemStack var0) {
      return var0 != null && !var0.getType().isAir() ? var0.getType().name() : "AIR";
   }

   private void write(Player var1, String var2) {
      Writer var3 = this.open.get(var1.getUniqueId());
      if (var3 != null) {
         try {
            var3.write(var2);
            var3.write(10);
         } catch (IOException var5) {
            this.plugin.getLogger().warning("watch write failed for " + var1.getName() + " " + var5.getMessage());
            this.end(var1.getUniqueId(), "error");
         }
      }
   }

   private void begin(Player var1) {
      if (!this.open.containsKey(var1.getUniqueId())) {
         try {
            Path var2 = this.plugin.getDataFolder().toPath().resolve("watch").resolve(var1.getName().toLowerCase(Locale.ROOT));
            Files.createDirectories(var2);
            Path var3 = var2.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".jsonl.gz");
            OutputStreamWriter var4 = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(var3), 65536), StandardCharsets.UTF_8);
            this.open.put(var1.getUniqueId(), var4);
            String var5 = var1.getClientBrandName() == null ? "" : var1.getClientBrandName();
            this.write(
               var1,
               "{\"k\":\"start\",\"v\":1,\"tk\":"
                  + this.tick
                  + ",\"ms\":"
                  + System.currentTimeMillis()
                  + ",\"name\":"
                  + q(var1.getName())
                  + ",\"uuid\":"
                  + q(var1.getUniqueId().toString())
                  + ",\"brand\":"
                  + q(var5)
                  + ",\"proto\":"
                  + var1.getProtocolVersion()
                  + "}"
            );
         } catch (IOException var6) {
            this.plugin.getLogger().warning("watch could not start for " + var1.getName() + " " + var6.getMessage());
         }
      }
   }

   private void end(UUID var1, String var2) {
      int[] var3 = this.pending.remove(var1);
      Player var4 = Bukkit.getPlayer(var1);
      if (var3 != null && var4 != null) {
         PlayerInventory var5 = var4.getInventory();
         ItemStack var6 = var5.getItem(var3[0]);
         ItemStack var7 = var5.getItem(var3[1]);
         if (type(var6) == var3[4] && type(var7) == var3[3]) {
            var5.setItem(var3[0], var7);
            var5.setItem(var3[1], var6);
         }
      }

      Writer var9 = this.open.remove(var1);
      if (var9 != null) {
         try {
            var9.write("{\"k\":\"end\",\"tk\":" + this.tick + ",\"ms\":" + System.currentTimeMillis() + ",\"why\":" + q(var2) + "}\n");
            var9.close();
         } catch (IOException var8) {
         }
      }
   }

   private void onTick() {
      this.tick++;
      if (!this.open.isEmpty()) {
         for (Player var2 : Bukkit.getOnlinePlayers()) {
            if (this.open.containsKey(var2.getUniqueId())) {
               Location var3 = var2.getLocation();
               StringBuilder var4 = new StringBuilder(320);
               var4.append("{\"k\":\"t\",\"tk\":")
                  .append(this.tick)
                  .append(",\"x\":")
                  .append(f(var3.getX()))
                  .append(",\"y\":")
                  .append(f(var3.getY()))
                  .append(",\"z\":")
                  .append(f(var3.getZ()))
                  .append(",\"yaw\":")
                  .append(f(var3.getYaw()))
                  .append(",\"pit\":")
                  .append(f(var3.getPitch()))
                  .append(",\"g\":")
                  .append(var2.isOnGround() ? 1 : 0)
                  .append(",\"spr\":")
                  .append(var2.isSprinting() ? 1 : 0)
                  .append(",\"snk\":")
                  .append(var2.isSneaking() ? 1 : 0)
                  .append(",\"gl\":")
                  .append(var2.isGliding() ? 1 : 0)
                  .append(",\"hp\":")
                  .append(f(var2.getHealth()))
                  .append(",\"abs\":")
                  .append(f(var2.getAbsorptionAmount()))
                  .append(",\"slot\":")
                  .append(var2.getInventory().getHeldItemSlot())
                  .append(",\"held\":")
                  .append(q(item(var2.getInventory().getItemInMainHand())))
                  .append(",\"use\":")
                  .append(var2.isHandRaised() ? 1 : 0)
                  .append(",\"cd\":")
                  .append(f(var2.getAttackCooldown()))
                  .append(",\"fall\":")
                  .append(f(var2.getFallDistance()))
                  .append(",\"ping\":")
                  .append(var2.getPing())
                  .append(",\"near\":[");
               boolean var5 = true;

               for (Entity var7 : var2.getNearbyEntities(16.0, 16.0, 16.0)) {
                  if (var7 instanceof Player var8 && !var8.isDead()) {
                     Location var9 = var8.getLocation();
                     if (!var5) {
                        var4.append(',');
                     }

                     var5 = false;
                     var4.append('[')
                        .append(q(var8.getName()))
                        .append(',')
                        .append(f(var9.getX() - var3.getX()))
                        .append(',')
                        .append(f(var9.getY() - var3.getY()))
                        .append(',')
                        .append(f(var9.getZ() - var3.getZ()))
                        .append(',')
                        .append(f(var8.getHealth()))
                        .append(',')
                        .append(var8.isOnGround() ? 1 : 0)
                        .append(']');
                  }
               }

               var4.append("]}");
               this.write(var2, var4.toString());
               this.trap(var2);
            }
         }
      }
   }

   private static int type(ItemStack var0) {
      return (var0 == null ? Material.AIR : var0.getType()).ordinal();
   }

   private static boolean weapon(Material var0) {
      String var1 = var0.name();
      return var0 == Material.MACE
         || var0 == Material.WIND_CHARGE
         || var0 == Material.ENDER_PEARL
         || var1.endsWith("_SPEAR")
         || var1.endsWith("_AXE")
         || var1.endsWith("_SWORD");
   }

   private void trap(Player var1) {
      UUID var2 = var1.getUniqueId();
      int[] var3 = this.pending.get(var2);
      if (var3 != null && this.tick >= var3[2]) {
         this.pending.remove(var2);
         PlayerInventory var21 = var1.getInventory();
         ItemStack var22 = var21.getItem(var3[0]);
         ItemStack var23 = var21.getItem(var3[1]);
         if (type(var22) == var3[4] && type(var23) == var3[3]) {
            var21.setItem(var3[0], var23);
            var21.setItem(var3[1], var22);
            this.write(var1, "{\"k\":\"trapback\",\"tk\":" + this.tick + ",\"held\":" + var21.getHeldItemSlot() + "}");
         }
      } else if (var3 == null && this.plugin.getConfig().getBoolean("watch-trap", true)) {
         if (var1.getGameMode() == GameMode.SURVIVAL || var1.getGameMode() == GameMode.ADVENTURE) {
            Integer var4 = this.lastFight.get(var2);
            if (var4 != null && this.tick - var4 <= 40 && this.tick >= this.nextTrap.getOrDefault(var2, 0)) {
               Player var5 = null;
               double var6 = 6.0;

               for (Entity var9 : var1.getNearbyEntities(6.0, 4.0, 6.0)) {
                  if (var9 instanceof Player var10 && !var10.isDead() && var1.getLocation().distance(var10.getLocation()) < var6) {
                     var6 = var1.getLocation().distance(var10.getLocation());
                     var5 = var10;
                  }
               }

               if (var5 != null && !var5.getName().toLowerCase(Locale.ROOT).startsWith("smbot")) {
                  PlayerInventory var24 = var1.getInventory();
                  int var25 = var24.getHeldItemSlot();
                  ArrayList<Integer> var26 = new ArrayList<>();
                  ArrayList<Integer> var11 = new ArrayList<>();

                  for (int var12 = 0; var12 < 9; var12++) {
                     if (var12 != var25) {
                        ItemStack var13 = var24.getItem(var12);
                        if (var13 != null && !var13.getType().isAir() && weapon(var13.getType())) {
                           var26.add(var12);
                        } else {
                           var11.add(var12);
                        }
                     }
                  }

                  ItemStack var27 = var24.getItemInMainHand();
                  var26.removeIf(var2x -> var24.getItem(var2x).getType() == var27.getType());
                  if (!var26.isEmpty() && !var11.isEmpty()) {
                     int var28 = (Integer)var26.get(this.rng.nextInt(var26.size()));
                     Material var14 = var24.getItem(var28).getType();
                     var11.removeIf(var2x -> var24.getItem(var2x) != null && var24.getItem(var2x).getType() == var14);
                     if (!var11.isEmpty()) {
                        ArrayList<Integer> var15 = new ArrayList<>(var11);
                        var15.removeIf(var1x -> var24.getItem(var1x) == null || var24.getItem(var1x).getType().isAir());
                        if (!var15.isEmpty()) {
                           var11 = var15;
                        }

                        int var16 = (Integer)var11.get(this.rng.nextInt(var11.size()));
                        ItemStack var17 = var24.getItem(var28);
                        ItemStack var18 = var24.getItem(var16);
                        var24.setItem(var28, var18);
                        var24.setItem(var16, var17);
                        int var19 = this.plugin.getConfig().getInt("watch-trap-seconds", 90);
                        this.nextTrap.put(var2, this.tick + 20 * (var19 / 2 + this.rng.nextInt(Math.max(1, var19))));
                        Material var20 = var18 == null ? Material.AIR : var18.getType();
                        this.pending.put(var2, new int[]{var28, var16, this.tick + 60, var14.ordinal(), var20.ordinal()});
                        this.write(
                           var1,
                           "{\"k\":\"trap\",\"tk\":"
                              + this.tick
                              + ",\"weapon\":"
                              + q(var14.name())
                              + ",\"from\":"
                              + var28
                              + ",\"to\":"
                              + var16
                              + ",\"other\":"
                              + q(var20.name())
                              + ",\"held\":"
                              + var25
                              + ",\"vs\":"
                              + q(var5.getName())
                              + "}"
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private String aim(Player var1, Entity var2) {
      Location var3 = var1.getEyeLocation();
      Vector var4 = var3.toVector();
      Vector var5 = var3.getDirection();
      BoundingBox var6 = var2.getBoundingBox();
      double var7 = Math.max(var6.getMinX(), Math.min(var4.getX(), var6.getMaxX()));
      double var9 = Math.max(var6.getMinY(), Math.min(var4.getY(), var6.getMaxY()));
      double var11 = Math.max(var6.getMinZ(), Math.min(var4.getZ(), var6.getMaxZ()));
      double var13 = var4.distance(new Vector(var7, var9, var11));
      Vector var15 = var6.getCenter().subtract(var4);
      double var16 = var15.lengthSquared() < 1.0E-6 ? 0.0 : Math.toDegrees(var5.angle(var15));
      RayTraceResult var18 = var6.clone().expand(0.1).rayTrace(var4, var5, 8.0);
      return ",\"reach\":" + f(var13) + ",\"ang\":" + f(var16) + ",\"onbox\":" + (var18 != null ? 1 : 0);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPreAttack(PrePlayerAttackEntityEvent var1) {
      Player var2 = var1.getPlayer();
      if (this.open.containsKey(var2.getUniqueId())) {
         Entity var3 = var1.getAttacked();
         if (var3 instanceof Player) {
            this.lastFight.put(var2.getUniqueId(), this.tick);
         }

         this.write(
            var2,
            "{\"k\":\"atk\",\"tk\":"
               + this.tick
               + ",\"to\":"
               + q(var3 instanceof Player var4 ? var4.getName() : var3.getType().name())
               + ",\"cd\":"
               + f(var2.getAttackCooldown())
               + ",\"held\":"
               + q(item(var2.getInventory().getItemInMainHand()))
               + ",\"fall\":"
               + f(var2.getFallDistance())
               + ",\"g\":"
               + (var2.isOnGround() ? 1 : 0)
               + ",\"spr\":"
               + (var2.isSprinting() ? 1 : 0)
               + this.aim(var2, var3)
               + ",\"ok\":"
               + (var1.willAttack() ? 1 : 0)
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDamage(EntityDamageByEntityEvent var1) {
      Entity var2 = var1.getDamager();
      Player var3 = var2 instanceof Player var6 ? var6 : (var2 instanceof Projectile var5 && var5.getShooter() instanceof Player var4 ? var4 : null);
      String var8 = var2 instanceof Projectile ? var2.getType().name() : "MELEE";
      if (var3 != null && this.open.containsKey(var3.getUniqueId())) {
         this.write(
            var3,
            "{\"k\":\"hit\",\"tk\":"
               + this.tick
               + ",\"to\":"
               + q(var1.getEntity().getName())
               + ",\"via\":"
               + q(var8)
               + ",\"dmg\":"
               + f(var1.getDamage())
               + ",\"fin\":"
               + f(var1.getFinalDamage())
               + ",\"cancel\":"
               + (var1.isCancelled() ? 1 : 0)
               + "}"
         );
      }

      if (var1.getEntity() instanceof Player var9 && this.open.containsKey(var9.getUniqueId())) {
         if (var3 != null) {
            this.lastFight.put(var9.getUniqueId(), this.tick);
         }

         this.write(
            var9,
            "{\"k\":\"hurt\",\"tk\":"
               + this.tick
               + ",\"by\":"
               + q(var3 != null ? var3.getName() : var2.getType().name())
               + ",\"via\":"
               + q(var8)
               + ",\"fin\":"
               + f(var1.getFinalDamage())
               + ",\"cancel\":"
               + (var1.isCancelled() ? 1 : 0)
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onOtherDamage(EntityDamageEvent var1) {
      if (!(var1 instanceof EntityDamageByEntityEvent) && var1.getEntity() instanceof Player var2 && this.open.containsKey(var2.getUniqueId())) {
         this.write(
            var2,
            "{\"k\":\"hurt\",\"tk\":"
               + this.tick
               + ",\"by\":"
               + q(var1.getCause().name())
               + ",\"fin\":"
               + f(var1.getFinalDamage())
               + ",\"cancel\":"
               + (var1.isCancelled() ? 1 : 0)
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onSwing(PlayerAnimationEvent var1) {
      if (var1.getAnimationType() == PlayerAnimationType.ARM_SWING && this.open.containsKey(var1.getPlayer().getUniqueId())) {
         this.write(var1.getPlayer(), "{\"k\":\"swing\",\"tk\":" + this.tick + ",\"cd\":" + f(var1.getPlayer().getAttackCooldown()) + "}");
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onHeld(PlayerItemHeldEvent var1) {
      if (this.open.containsKey(var1.getPlayer().getUniqueId())) {
         this.write(
            var1.getPlayer(),
            "{\"k\":\"slot\",\"tk\":"
               + this.tick
               + ",\"from\":"
               + var1.getPreviousSlot()
               + ",\"to\":"
               + var1.getNewSlot()
               + ",\"item\":"
               + q(item(var1.getPlayer().getInventory().getItem(var1.getNewSlot())))
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onUse(PlayerInteractEvent var1) {
      if (this.open.containsKey(var1.getPlayer().getUniqueId())) {
         this.write(
            var1.getPlayer(),
            "{\"k\":\"use\",\"tk\":"
               + this.tick
               + ",\"act\":"
               + q(var1.getAction().name())
               + ",\"item\":"
               + q(item(var1.getItem()))
               + ",\"hand\":"
               + q(var1.getHand() == null ? "" : var1.getHand().name())
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onConsume(PlayerItemConsumeEvent var1) {
      if (this.open.containsKey(var1.getPlayer().getUniqueId())) {
         this.write(var1.getPlayer(), "{\"k\":\"eat\",\"tk\":" + this.tick + ",\"item\":" + q(item(var1.getItem())) + "}");
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onLaunch(ProjectileLaunchEvent var1) {
      if (var1.getEntity().getShooter() instanceof Player var2 && this.open.containsKey(var2.getUniqueId())) {
         Location var4 = var2.getLocation();
         this.write(
            var2,
            "{\"k\":\"throw\",\"tk\":"
               + this.tick
               + ",\"what\":"
               + q(var1.getEntity().getType().name())
               + ",\"yaw\":"
               + f(var4.getYaw())
               + ",\"pit\":"
               + f(var4.getPitch())
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onVelocity(PlayerVelocityEvent var1) {
      if (this.open.containsKey(var1.getPlayer().getUniqueId())) {
         Vector var2 = var1.getVelocity();
         this.write(
            var1.getPlayer(),
            "{\"k\":\"kb\",\"tk\":" + this.tick + ",\"vx\":" + f(var2.getX()) + ",\"vy\":" + f(var2.getY()) + ",\"vz\":" + f(var2.getZ()) + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onTeleport(PlayerTeleportEvent var1) {
      if (this.open.containsKey(var1.getPlayer().getUniqueId())) {
         this.write(var1.getPlayer(), "{\"k\":\"tp\",\"tk\":" + this.tick + ",\"cause\":" + q(var1.getCause().name()) + "}");
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDeath(PlayerDeathEvent var1) {
      Player var2 = var1.getEntity();
      Player var3 = var2.getKiller();
      if (this.open.containsKey(var2.getUniqueId())) {
         this.write(var2, "{\"k\":\"died\",\"tk\":" + this.tick + ",\"by\":" + q(var3 == null ? "" : var3.getName()) + "}");
      }

      if (var3 != null && this.open.containsKey(var3.getUniqueId())) {
         this.write(var3, "{\"k\":\"kill\",\"tk\":" + this.tick + ",\"who\":" + q(var2.getName()) + "}");
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onJoin(PlayerJoinEvent var1) {
      if (this.watched(var1.getPlayer().getName())) {
         this.begin(var1.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onQuit(PlayerQuitEvent var1) {
      this.end(var1.getPlayer().getUniqueId(), "quit");
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var4.length != 0 && !var4[0].equalsIgnoreCase("list")) {
         if (var4.length >= 2 && (var4[0].equalsIgnoreCase("add") || var4[0].equalsIgnoreCase("remove"))) {
            ArrayList<String> var9 = new ArrayList<>(this.names());
            String var10 = var4[1];
            var9.removeIf(var1x -> var1x.equalsIgnoreCase(var10));
            Player var11 = Bukkit.getPlayerExact(var10);
            if (var4[0].equalsIgnoreCase("add")) {
               var9.add(var10);
               if (var11 != null) {
                  this.begin(var11);
               }
            } else if (var11 != null) {
               this.end(var11.getUniqueId(), "removed");
            }

            this.plugin.getConfig().set("watch-players", var9);
            this.plugin.saveConfig();
            var1.sendMessage(
               (var4[0].equalsIgnoreCase("add") ? "now watching " : "stopped watching ")
                  + var10
                  + (var11 != null && var4[0].equalsIgnoreCase("add") ? "  recording" : "")
            );
            return true;
         } else {
            var1.sendMessage("/watch add|remove|list <player>");
            return true;
         }
      } else {
         ArrayList var5 = new ArrayList();

         for (UUID var7 : this.open.keySet()) {
            Player var8 = Bukkit.getPlayer(var7);
            if (var8 != null) {
               var5.add(var8.getName());
            }
         }

         var1.sendMessage("watched " + this.names() + "  recording now " + var5);
         return true;
      }
   }
}
