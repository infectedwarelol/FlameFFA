package dev.flamesentinell.spawnguard;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public final class SpawnGuard extends JavaPlugin implements Listener {
   private final Set<UUID> dropping = new HashSet<>();
   private final Map<UUID, Long> warned = new HashMap<>();
   private String world;
   private int x1;
   private int y1;
   private int z1;
   private int x2;
   private int y2;
   private int z2;
   private boolean zoneSet;
   private boolean dropGuard;
   private boolean projectiles;
   private boolean noPush;
   private Component blocked;

   public void onEnable() {
      this.saveDefaultConfig();
      this.load();
      Bukkit.getPluginManager().registerEvents(this, this);
      Bukkit.getScheduler().runTaskTimer(this, this::collisions, 20L, 10L);
   }

   private void load() {
      this.reloadConfig();
      this.dropGuard = this.getConfig().getBoolean("spawn-drop-guard", true);
      this.projectiles = this.getConfig().getBoolean("block-projectiles", true);
      this.noPush = this.getConfig().getBoolean("no-push-in-safezone", true);
      this.blocked = MiniMessage.miniMessage().deserialize(this.getConfig().getString("blocked-message", "<red>Land first before you can attack"));
      File var1 = new File(this.getDataFolder().getParentFile(), "FlameFFACore/config.yml");
      ConfigurationSection var2 = YamlConfiguration.loadConfiguration(var1).getConfigurationSection("arena.safezone");
      this.zoneSet = var2 != null && var2.contains("x1");
      if (this.zoneSet) {
         this.world = var2.getString("world", "world");
         this.x1 = Math.min(var2.getInt("x1"), var2.getInt("x2"));
         this.x2 = Math.max(var2.getInt("x1"), var2.getInt("x2"));
         this.y1 = Math.min(var2.getInt("y1"), var2.getInt("y2"));
         this.y2 = Math.max(var2.getInt("y1"), var2.getInt("y2"));
         this.z1 = Math.min(var2.getInt("z1"), var2.getInt("z2"));
         this.z2 = Math.max(var2.getInt("z1"), var2.getInt("z2"));
         this.getLogger().info("safezone " + this.world + " " + this.x1 + " " + this.y1 + " " + this.z1 + " to " + this.x2 + " " + this.y2 + " " + this.z2);
      } else {
         this.getLogger().warning("no safezone in flameffacore config  guard idle");
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      this.load();
      var1.sendMessage("FlameSpawnGuard reloaded");
      return true;
   }

   private boolean inZone(Location var1) {
      if (this.zoneSet && var1.getWorld() != null && var1.getWorld().getName().equals(this.world)) {
         int var2 = var1.getBlockX();
         int var3 = var1.getBlockY();
         int var4 = var1.getBlockZ();
         return var2 >= this.x1 && var2 <= this.x2 && var3 >= this.y1 && var3 <= this.y2 && var4 >= this.z1 && var4 <= this.z2;
      } else {
         return false;
      }
   }

   private boolean landed(Player var1) {
      if (!var1.isInWater() && !var1.isClimbing() && !var1.isInsideVehicle()) {
         if (!var1.isOnGround()) {
            return false;
         } else {
            Location var2 = var1.getLocation();
            World var3 = var2.getWorld();

            for (double var4 = -0.3; var4 <= 0.3; var4 += 0.3) {
               for (double var6 = -0.3; var6 <= 0.3; var6 += 0.3) {
                  Block var8 = var3.getBlockAt(
                     Location.locToBlock(var2.getX() + var4), Location.locToBlock(var2.getY() - 0.2), Location.locToBlock(var2.getZ() + var6)
                  );
                  if (!var8.isPassable()) {
                     return true;
                  }
               }
            }

            return false;
         }
      } else {
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent var1) {
      if (this.dropGuard) {
         Player var2 = var1.getPlayer();
         UUID var3 = var2.getUniqueId();
         boolean var4 = this.inZone(var1.getFrom());
         boolean var5 = this.inZone(var1.getTo());
         if (var5) {
            this.dropping.remove(var3);
         } else if (var4 && !var2.isOnGround()) {
            this.dropping.add(var3);
         } else if (this.dropping.contains(var3) && this.landed(var2)) {
            this.dropping.remove(var3);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onTeleport(PlayerTeleportEvent var1) {
      if (var1.getCause() != TeleportCause.ENDER_PEARL) {
         this.dropping.remove(var1.getPlayer().getUniqueId());
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent var1) {
      this.dropping.remove(var1.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.dropping.remove(var1.getPlayer().getUniqueId());
      this.warned.remove(var1.getPlayer().getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent var1) {
      if (this.dropGuard && var1.getEntity() instanceof Player) {
         Entity var2 = var1.getDamager();
         Player var3 = var2 instanceof Player var4 ? var4 : null;
         if (var3 == null && this.projectiles && var2 instanceof Projectile var7 && var7.getShooter() instanceof Player var5) {
            var3 = var5;
         }

         if (var3 != null && this.dropping.contains(var3.getUniqueId())) {
            var1.setCancelled(true);
            long var8 = System.currentTimeMillis();
            Long var9 = this.warned.get(var3.getUniqueId());
            if (var9 == null || var8 - var9 > 1000L) {
               this.warned.put(var3.getUniqueId(), var8);
               var3.sendActionBar(this.blocked);
            }
         }
      }
   }

   private void collisions() {
      for (Player var2 : Bukkit.getOnlinePlayers()) {
         Team var3 = var2.getScoreboard().getEntryTeam(var2.getName());
         if (var3 != null) {
            OptionStatus var4 = this.noPush && this.inZone(var2.getLocation()) ? OptionStatus.NEVER : OptionStatus.ALWAYS;
            if (var3.getOption(Option.COLLISION_RULE) != var4) {
               var3.setOption(Option.COLLISION_RULE, var4);
            }
         }
      }
   }
}
