package dev.flamesentinell.flameffacore.tweaks;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Tasks;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
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

final class Watch implements Listener {
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
   private final FlameFFACore plugin;
   private final TweaksModule tweaks;
   private final Map<UUID, Watch.Recording> open = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> lastFight = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> nextTrap = new ConcurrentHashMap<>();
   private final Map<UUID, int[]> pending = new ConcurrentHashMap<>();
   private volatile int tick;
   private ScheduledTask clock;
   private ExecutorService io;

   Watch(FlameFFACore plugin, TweaksModule tweaks) {
      this.plugin = plugin;
      this.tweaks = tweaks;
   }

   void start() {
      this.io = Executors.newSingleThreadExecutor(runnable -> {
         Thread thread = new Thread(runnable, "FlameFFACore-Watch");
         thread.setDaemon(true);
         return thread;
      });
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      this.clock = this.plugin.tasks().globalTimer(task -> this.tick++, 1L, 1L);

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.watched(player.getName())) {
            this.plugin.tasks().runOn(player, () -> this.begin(player));
         }
      }
   }

   void stop() {
      HandlerList.unregisterAll(this);
      Tasks.cancel(this.clock);
      this.clock = null;

      for (UUID uuid : new ArrayList<>(this.open.keySet())) {
         try {
            this.end(uuid, "shutdown");
         } catch (RuntimeException offThread) {
            this.close(uuid, "shutdown");
         }
      }

      if (this.io != null) {
         this.io.shutdown();

         try {
            if (!this.io.awaitTermination(5L, TimeUnit.SECONDS)) {
               this.plugin.getSLF4JLogger().warn("watch recordings did not finish writing in time");
            }
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
         }

         this.io = null;
      }

      this.lastFight.clear();
      this.nextTrap.clear();
      this.pending.clear();
   }

   private List<String> names() {
      return this.tweaks.cfg().getStringList("watch-players");
   }

   private boolean watched(String name) {
      for (String entry : this.names()) {
         if (entry.equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   private static String f(double value) {
      return String.format(Locale.ROOT, "%.3f", value);
   }

   private static String q(String value) {
      return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
   }

   private static String item(ItemStack stack) {
      return stack != null && !stack.getType().isAir() ? stack.getType().name() : "AIR";
   }

   private void submit(Runnable work) {
      ExecutorService executor = this.io;
      if (executor != null) {
         try {
            executor.execute(work);
         } catch (RejectedExecutionException closed) {
            work.run();
         }
      }
   }

   private void write(Player player, String line) {
      Watch.Recording recording = this.open.get(player.getUniqueId());
      if (recording != null) {
         UUID uuid = player.getUniqueId();
         String name = player.getName();
         this.submit(() -> {
            if (recording.writer != null && !recording.failed) {
               try {
                  recording.writer.write(line);
                  recording.writer.write(10);
               } catch (IOException failure) {
                  recording.failed = true;
                  this.plugin.getSLF4JLogger().warn("watch write failed for {} {}", name, failure.getMessage());
                  Player online = Bukkit.getPlayer(uuid);
                  if (online != null) {
                     this.plugin.tasks().entity(online, () -> this.end(uuid, "error"));
                  } else {
                     this.close(uuid, "error");
                  }
               }
            }
         });
      }
   }

   private void begin(Player player) {
      UUID uuid = player.getUniqueId();
      Watch.Recording recording = new Watch.Recording();
      if (this.open.putIfAbsent(uuid, recording) == null) {
         Path folder = this.plugin.getDataFolder().toPath().resolve("watch").resolve(player.getName().toLowerCase(Locale.ROOT));
         Path file = folder.resolve(LocalDateTime.now().format(STAMP) + ".jsonl.gz");
         String name = player.getName();
         this.submit(() -> {
            try {
               Files.createDirectories(folder);
               recording.writer = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file), 65536), StandardCharsets.UTF_8);
            } catch (IOException failure) {
               recording.failed = true;
               this.open.remove(uuid, recording);
               this.plugin.getSLF4JLogger().warn("watch could not start for {} {}", name, failure.getMessage());
            }
         });
         String brand = player.getClientBrandName() == null ? "" : player.getClientBrandName();
         this.write(
            player,
            "{\"k\":\"start\",\"v\":1,\"tk\":"
               + this.tick
               + ",\"ms\":"
               + System.currentTimeMillis()
               + ",\"name\":"
               + q(player.getName())
               + ",\"uuid\":"
               + q(uuid.toString())
               + ",\"brand\":"
               + q(brand)
               + ",\"proto\":"
               + player.getProtocolVersion()
               + "}"
         );
         recording.ticker = this.plugin.tasks().entityTimer(player, task -> this.onTick(player), () -> this.close(uuid, "quit"), 1L, 1L);
      }
   }

   private void end(UUID uuid, String why) {
      int[] trap = this.pending.remove(uuid);
      Player player = Bukkit.getPlayer(uuid);
      if (trap != null && player != null && Bukkit.isOwnedByCurrentRegion(player)) {
         PlayerInventory inventory = player.getInventory();
         ItemStack moved = inventory.getItem(trap[0]);
         ItemStack swapped = inventory.getItem(trap[1]);
         if (type(moved) == trap[4] && type(swapped) == trap[3]) {
            inventory.setItem(trap[0], swapped);
            inventory.setItem(trap[1], moved);
         }
      }

      this.close(uuid, why);
   }

   private void close(UUID uuid, String why) {
      Watch.Recording recording = this.open.remove(uuid);
      if (recording != null) {
         Tasks.cancel(recording.ticker);
         String last = "{\"k\":\"end\",\"tk\":" + this.tick + ",\"ms\":" + System.currentTimeMillis() + ",\"why\":" + q(why) + "}\n";
         this.submit(() -> {
            if (recording.writer != null) {
               try {
                  if (!recording.failed) {
                     recording.writer.write(last);
                  }

                  recording.writer.close();
               } catch (IOException ignored) {
                  recording.failed = true;
               }

               recording.writer = null;
            }
         });
      }
   }

   void stopWatching(Player target, String why) {
      this.plugin.tasks().runOn(target, () -> this.end(target.getUniqueId(), why));
   }

   void startWatching(Player target) {
      this.plugin.tasks().runOn(target, () -> {
         if (target.isOnline()) {
            this.begin(target);
         }
      });
   }

   private void onTick(Player player) {
      if (player.isOnline() && this.open.containsKey(player.getUniqueId())) {
         Location at = player.getLocation();
         StringBuilder line = new StringBuilder(320);
         line.append("{\"k\":\"t\",\"tk\":")
            .append(this.tick)
            .append(",\"x\":")
            .append(f(at.getX()))
            .append(",\"y\":")
            .append(f(at.getY()))
            .append(",\"z\":")
            .append(f(at.getZ()))
            .append(",\"yaw\":")
            .append(f(at.getYaw()))
            .append(",\"pit\":")
            .append(f(at.getPitch()))
            .append(",\"g\":")
            .append(player.isOnGround() ? 1 : 0)
            .append(",\"spr\":")
            .append(player.isSprinting() ? 1 : 0)
            .append(",\"snk\":")
            .append(player.isSneaking() ? 1 : 0)
            .append(",\"gl\":")
            .append(player.isGliding() ? 1 : 0)
            .append(",\"hp\":")
            .append(f(player.getHealth()))
            .append(",\"abs\":")
            .append(f(player.getAbsorptionAmount()))
            .append(",\"slot\":")
            .append(player.getInventory().getHeldItemSlot())
            .append(",\"held\":")
            .append(q(item(player.getInventory().getItemInMainHand())))
            .append(",\"use\":")
            .append(player.isHandRaised() ? 1 : 0)
            .append(",\"cd\":")
            .append(f(player.getAttackCooldown()))
            .append(",\"fall\":")
            .append(f(player.getFallDistance()))
            .append(",\"ping\":")
            .append(player.getPing())
            .append(",\"near\":[");
         boolean first = true;

         for (Entity nearby : player.getNearbyEntities(16.0, 16.0, 16.0)) {
            if (nearby instanceof Player other && !other.isDead()) {
               Location there = other.getLocation();
               if (!first) {
                  line.append(',');
               }

               first = false;
               line.append('[')
                  .append(q(other.getName()))
                  .append(',')
                  .append(f(there.getX() - at.getX()))
                  .append(',')
                  .append(f(there.getY() - at.getY()))
                  .append(',')
                  .append(f(there.getZ() - at.getZ()))
                  .append(',')
                  .append(f(other.getHealth()))
                  .append(',')
                  .append(other.isOnGround() ? 1 : 0)
                  .append(']');
            }
         }

         line.append("]}");
         this.write(player, line.toString());
         this.trap(player);
      }
   }

   private static int type(ItemStack stack) {
      return (stack == null ? Material.AIR : stack.getType()).ordinal();
   }

   private static boolean weapon(Material type) {
      String name = type.name();
      return type == Material.MACE
         || type == Material.WIND_CHARGE
         || type == Material.ENDER_PEARL
         || name.endsWith("_SPEAR")
         || name.endsWith("_AXE")
         || name.endsWith("_SWORD");
   }

   private void trap(Player player) {
      UUID uuid = player.getUniqueId();
      int[] trap = this.pending.get(uuid);
      int now = this.tick;
      if (trap != null && now >= trap[2]) {
         this.pending.remove(uuid);
         PlayerInventory inventory = player.getInventory();
         ItemStack moved = inventory.getItem(trap[0]);
         ItemStack swapped = inventory.getItem(trap[1]);
         if (type(moved) == trap[4] && type(swapped) == trap[3]) {
            inventory.setItem(trap[0], swapped);
            inventory.setItem(trap[1], moved);
            this.write(player, "{\"k\":\"trapback\",\"tk\":" + now + ",\"held\":" + inventory.getHeldItemSlot() + "}");
         }
      } else if (trap == null && this.tweaks.cfg().getBoolean("watch-trap", true) && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)) {
         Integer fought = this.lastFight.get(uuid);
         if (fought != null && now - fought <= 40 && now >= this.nextTrap.getOrDefault(uuid, 0)) {
            Player closest = null;
            double best = 6.0;

            for (Entity nearby : player.getNearbyEntities(6.0, 4.0, 6.0)) {
               if (nearby instanceof Player other && !other.isDead()) {
                  double distance = player.getLocation().distance(other.getLocation());
                  if (distance < best) {
                     best = distance;
                     closest = other;
                  }
               }
            }

            if (closest != null && !closest.getName().toLowerCase(Locale.ROOT).startsWith("smbot")) {
               this.swap(player, closest, now);
            }
         }
      }
   }

   private void swap(Player player, Player opponent, int now) {
      PlayerInventory inventory = player.getInventory();
      int held = inventory.getHeldItemSlot();
      List<Integer> weapons = new ArrayList<>();
      List<Integer> others = new ArrayList<>();

      for (int slot = 0; slot < 9; slot++) {
         if (slot != held) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir() && weapon(stack.getType())) {
               weapons.add(slot);
            } else {
               others.add(slot);
            }
         }
      }

      Material inHand = inventory.getItemInMainHand().getType();
      weapons.removeIf(slot -> inventory.getItem(slot).getType() == inHand);
      if (!weapons.isEmpty() && !others.isEmpty()) {
         ThreadLocalRandom rng = ThreadLocalRandom.current();
         int from = weapons.get(rng.nextInt(weapons.size()));
         Material weaponType = inventory.getItem(from).getType();
         others.removeIf(slot -> inventory.getItem(slot) != null && inventory.getItem(slot).getType() == weaponType);
         if (!others.isEmpty()) {
            List<Integer> filled = new ArrayList<>(others);
            filled.removeIf(slot -> inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir());
            if (!filled.isEmpty()) {
               others = filled;
            }

            int to = others.get(rng.nextInt(others.size()));
            ItemStack weaponStack = inventory.getItem(from);
            ItemStack otherStack = inventory.getItem(to);
            inventory.setItem(from, otherStack);
            inventory.setItem(to, weaponStack);
            int seconds = Math.max(1, this.tweaks.cfg().getInt("watch-trap-seconds", 90));
            this.nextTrap.put(player.getUniqueId(), now + 20 * (seconds / 2 + rng.nextInt(seconds)));
            Material otherType = otherStack == null ? Material.AIR : otherStack.getType();
            this.pending.put(player.getUniqueId(), new int[]{from, to, now + 60, weaponType.ordinal(), otherType.ordinal()});
            this.write(
               player,
               "{\"k\":\"trap\",\"tk\":"
                  + now
                  + ",\"weapon\":"
                  + q(weaponType.name())
                  + ",\"from\":"
                  + from
                  + ",\"to\":"
                  + to
                  + ",\"other\":"
                  + q(otherType.name())
                  + ",\"held\":"
                  + held
                  + ",\"vs\":"
                  + q(opponent.getName())
                  + "}"
            );
         }
      }
   }

   private static String aim(Player player, Entity target) {
      Location eye = player.getEyeLocation();
      Vector origin = eye.toVector();
      Vector look = eye.getDirection();
      BoundingBox box = target.getBoundingBox();
      double nearX = Math.max(box.getMinX(), Math.min(origin.getX(), box.getMaxX()));
      double nearY = Math.max(box.getMinY(), Math.min(origin.getY(), box.getMaxY()));
      double nearZ = Math.max(box.getMinZ(), Math.min(origin.getZ(), box.getMaxZ()));
      double reach = origin.distance(new Vector(nearX, nearY, nearZ));
      Vector toCentre = box.getCenter().subtract(origin);
      double angle = toCentre.lengthSquared() < 1.0E-6 ? 0.0 : Math.toDegrees(look.angle(toCentre));
      RayTraceResult hit = box.clone().expand(0.1).rayTrace(origin, look, 8.0);
      return ",\"reach\":" + f(reach) + ",\"ang\":" + f(angle) + ",\"onbox\":" + (hit != null ? 1 : 0);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPreAttack(PrePlayerAttackEntityEvent event) {
      Player player = event.getPlayer();
      if (this.open.containsKey(player.getUniqueId())) {
         Entity target = event.getAttacked();
         if (target instanceof Player) {
            this.lastFight.put(player.getUniqueId(), this.tick);
         }

         this.write(
            player,
            "{\"k\":\"atk\",\"tk\":"
               + this.tick
               + ",\"to\":"
               + q(target instanceof Player other ? other.getName() : target.getType().name())
               + ",\"cd\":"
               + f(player.getAttackCooldown())
               + ",\"held\":"
               + q(item(player.getInventory().getItemInMainHand()))
               + ",\"fall\":"
               + f(player.getFallDistance())
               + ",\"g\":"
               + (player.isOnGround() ? 1 : 0)
               + ",\"spr\":"
               + (player.isSprinting() ? 1 : 0)
               + aim(player, target)
               + ",\"ok\":"
               + (event.willAttack() ? 1 : 0)
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDamage(EntityDamageByEntityEvent event) {
      if (!this.open.isEmpty()) {
         Entity damager = event.getDamager();
         Player attacker = damager instanceof Player direct
            ? direct
            : (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null);
         String via = damager instanceof Projectile ? damager.getType().name() : "MELEE";
         if (attacker != null && this.open.containsKey(attacker.getUniqueId())) {
            this.write(
               attacker,
               "{\"k\":\"hit\",\"tk\":"
                  + this.tick
                  + ",\"to\":"
                  + q(event.getEntity().getName())
                  + ",\"via\":"
                  + q(via)
                  + ",\"dmg\":"
                  + f(event.getDamage())
                  + ",\"fin\":"
                  + f(event.getFinalDamage())
                  + ",\"cancel\":"
                  + (event.isCancelled() ? 1 : 0)
                  + "}"
            );
         }

         if (event.getEntity() instanceof Player victim && this.open.containsKey(victim.getUniqueId())) {
            if (attacker != null) {
               this.lastFight.put(victim.getUniqueId(), this.tick);
            }

            this.write(
               victim,
               "{\"k\":\"hurt\",\"tk\":"
                  + this.tick
                  + ",\"by\":"
                  + q(attacker != null ? attacker.getName() : damager.getType().name())
                  + ",\"via\":"
                  + q(via)
                  + ",\"fin\":"
                  + f(event.getFinalDamage())
                  + ",\"cancel\":"
                  + (event.isCancelled() ? 1 : 0)
                  + "}"
            );
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onOtherDamage(EntityDamageEvent event) {
      if (!(event instanceof EntityDamageByEntityEvent) && event.getEntity() instanceof Player player && this.open.containsKey(player.getUniqueId())) {
         this.write(
            player,
            "{\"k\":\"hurt\",\"tk\":"
               + this.tick
               + ",\"by\":"
               + q(event.getCause().name())
               + ",\"fin\":"
               + f(event.getFinalDamage())
               + ",\"cancel\":"
               + (event.isCancelled() ? 1 : 0)
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onSwing(PlayerAnimationEvent event) {
      if (event.getAnimationType() == PlayerAnimationType.ARM_SWING && this.open.containsKey(event.getPlayer().getUniqueId())) {
         this.write(event.getPlayer(), "{\"k\":\"swing\",\"tk\":" + this.tick + ",\"cd\":" + f(event.getPlayer().getAttackCooldown()) + "}");
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onHeld(PlayerItemHeldEvent event) {
      if (this.open.containsKey(event.getPlayer().getUniqueId())) {
         this.write(
            event.getPlayer(),
            "{\"k\":\"slot\",\"tk\":"
               + this.tick
               + ",\"from\":"
               + event.getPreviousSlot()
               + ",\"to\":"
               + event.getNewSlot()
               + ",\"item\":"
               + q(item(event.getPlayer().getInventory().getItem(event.getNewSlot())))
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onUse(PlayerInteractEvent event) {
      if (this.open.containsKey(event.getPlayer().getUniqueId())) {
         this.write(
            event.getPlayer(),
            "{\"k\":\"use\",\"tk\":"
               + this.tick
               + ",\"act\":"
               + q(event.getAction().name())
               + ",\"item\":"
               + q(item(event.getItem()))
               + ",\"hand\":"
               + q(event.getHand() == null ? "" : event.getHand().name())
               + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onConsume(PlayerItemConsumeEvent event) {
      if (this.open.containsKey(event.getPlayer().getUniqueId())) {
         this.write(event.getPlayer(), "{\"k\":\"eat\",\"tk\":" + this.tick + ",\"item\":" + q(item(event.getItem())) + "}");
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onLaunch(ProjectileLaunchEvent event) {
      if (event.getEntity().getShooter() instanceof Player player && this.open.containsKey(player.getUniqueId())) {
         Location at = player.getLocation();
         this.write(
            player,
            "{\"k\":\"throw\",\"tk\":" + this.tick + ",\"what\":" + q(event.getEntity().getType().name()) + ",\"yaw\":" + f(at.getYaw()) + ",\"pit\":" + f(at.getPitch()) + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onVelocity(PlayerVelocityEvent event) {
      if (this.open.containsKey(event.getPlayer().getUniqueId())) {
         Vector velocity = event.getVelocity();
         this.write(
            event.getPlayer(),
            "{\"k\":\"kb\",\"tk\":" + this.tick + ",\"vx\":" + f(velocity.getX()) + ",\"vy\":" + f(velocity.getY()) + ",\"vz\":" + f(velocity.getZ()) + "}"
         );
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onTeleport(PlayerTeleportEvent event) {
      if (this.open.containsKey(event.getPlayer().getUniqueId())) {
         this.write(event.getPlayer(), "{\"k\":\"tp\",\"tk\":" + this.tick + ",\"cause\":" + q(event.getCause().name()) + "}");
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDeath(PlayerDeathEvent event) {
      Player dead = event.getEntity();
      Player killer = dead.getKiller();
      if (this.open.containsKey(dead.getUniqueId())) {
         this.write(dead, "{\"k\":\"died\",\"tk\":" + this.tick + ",\"by\":" + q(killer == null ? "" : killer.getName()) + "}");
      }

      if (killer != null && this.open.containsKey(killer.getUniqueId())) {
         this.write(killer, "{\"k\":\"kill\",\"tk\":" + this.tick + ",\"who\":" + q(dead.getName()) + "}");
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onJoin(PlayerJoinEvent event) {
      if (this.watched(event.getPlayer().getName())) {
         this.begin(event.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.end(uuid, "quit");
      this.lastFight.remove(uuid);
      this.nextTrap.remove(uuid);
   }

   boolean command(CommandSender sender, String[] args) {
      if (args.length != 0 && !args[0].equalsIgnoreCase("list")) {
         if (args.length >= 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            boolean adding = args[0].equalsIgnoreCase("add");
            String name = args[1];
            List<String> names = new ArrayList<>(this.names());
            names.removeIf(entry -> entry.equalsIgnoreCase(name));
            Player target = Bukkit.getPlayerExact(name);
            if (adding) {
               names.add(name);
               if (target != null) {
                  this.startWatching(target);
               }
            } else if (target != null) {
               this.stopWatching(target, "removed");
            }

            this.tweaks.cfg().set("watch-players", names);
            this.tweaks.saveConfig();
            sender.sendMessage((adding ? "now watching " : "stopped watching ") + name + (target != null && adding ? "  recording" : ""));
         } else {
            sender.sendMessage("/watch add|remove|list <player>");
         }
      } else {
         List<String> recording = new ArrayList<>();

         for (UUID uuid : this.open.keySet()) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
               recording.add(online.getName());
            }
         }

         sender.sendMessage("watched " + this.names() + "  recording now " + recording);
      }

      return true;
   }

   private static final class Recording {
      volatile Writer writer;
      volatile boolean failed;
      volatile ScheduledTask ticker;
   }
}
