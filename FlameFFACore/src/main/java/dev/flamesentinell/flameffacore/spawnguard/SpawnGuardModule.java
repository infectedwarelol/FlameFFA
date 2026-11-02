package dev.flamesentinell.flameffacore.spawnguard;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.module.Module;
import dev.flamesentinell.flameffacore.module.ModuleCommand;
import dev.flamesentinell.flameffacore.module.ModuleConfig;
import dev.flamesentinell.flameffacore.util.Tasks;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public final class SpawnGuardModule implements Module, Listener {
   private final FlameFFACore plugin;
   private final ModuleConfig config;
   private final Set<UUID> dropping = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Long> warned = new ConcurrentHashMap<>();
   private volatile boolean dropGuard;
   private volatile boolean projectiles;
   private volatile boolean noPush;
   private volatile Component blocked = Component.empty();
   private ScheduledTask collisionTask;

   public SpawnGuardModule(FlameFFACore plugin) {
      this.plugin = plugin;
      this.config = new ModuleConfig(plugin, "spawnguard.yml", "FlameSpawnGuard", "config.yml");
   }

   @Override
   public String id() {
      return "spawnguard";
   }

   @Override
   public void enable() {
      this.load();
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      if (!Tasks.FOLIA) {
         this.collisionTask = this.plugin.tasks().globalTimer(task -> this.collisions(), 20L, 10L);
      }
   }

   @Override
   public void disable() {
      HandlerList.unregisterAll(this);
      Tasks.cancel(this.collisionTask);
      this.collisionTask = null;
      this.dropping.clear();
      this.warned.clear();
   }

   @Override
   public void commands(Commands commands) {
      ModuleCommand.register(commands, "spawnguard", "reload the safezone and settings", List.of(), "spawnguard.admin", null, (sender, label, args) -> {
         this.load();
         sender.sendMessage("FlameSpawnGuard reloaded");
         return true;
      }, null);
   }

   private void load() {
      this.config.load();
      FileConfiguration cfg = this.config.get();
      this.dropGuard = cfg.getBoolean("spawn-drop-guard", true);
      this.projectiles = cfg.getBoolean("block-projectiles", true);
      this.noPush = cfg.getBoolean("no-push-in-safezone", true);
      this.blocked = MiniMessage.miniMessage().deserialize(cfg.getString("blocked-message", "<red>Land first before you can attack"));
      if (this.plugin.safezone().isSet()) {
         this.plugin.getSLF4JLogger().info("spawn guard uses safezone {}", this.plugin.safezone().describe());
      } else {
         this.plugin.getSLF4JLogger().warn("no safezone in flameffacore config  guard idle");
      }
   }

   private boolean inZone(Location location) {
      return this.plugin.safezone().contains(location);
   }

   private static boolean landed(Player player) {
      if (player.isInWater() || player.isClimbing() || player.isInsideVehicle()) {
         return true;
      } else if (!player.isOnGround()) {
         return false;
      } else {
         Location at = player.getLocation();
         World world = at.getWorld();

         for (double dx = -0.3; dx <= 0.3; dx += 0.3) {
            for (double dz = -0.3; dz <= 0.3; dz += 0.3) {
               Block below = world.getBlockAt(Location.locToBlock(at.getX() + dx), Location.locToBlock(at.getY() - 0.2), Location.locToBlock(at.getZ() + dz));
               if (!below.isPassable()) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.dropGuard) {
         Player player = event.getPlayer();
         UUID uuid = player.getUniqueId();
         Location from = event.getFrom();
         Location to = event.getTo();
         boolean sameBlock = from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ() && from.getWorld() == to.getWorld();
         if (!sameBlock || this.dropping.contains(uuid)) {
            boolean wasInside = this.inZone(from);
            if (this.inZone(to)) {
               this.dropping.remove(uuid);
            } else if (wasInside && !player.isOnGround()) {
               this.dropping.add(uuid);
            } else if (this.dropping.contains(uuid) && landed(player)) {
               this.dropping.remove(uuid);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onTeleport(PlayerTeleportEvent event) {
      if (event.getCause() != TeleportCause.ENDER_PEARL) {
         this.dropping.remove(event.getPlayer().getUniqueId());
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      this.dropping.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.dropping.remove(event.getPlayer().getUniqueId());
      this.warned.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent event) {
      if (this.dropGuard && !this.dropping.isEmpty() && event.getEntity() instanceof Player) {
         Entity damager = event.getDamager();
         Player attacker = damager instanceof Player direct ? direct : null;
         if (attacker == null && this.projectiles && damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
         }

         if (attacker != null && this.dropping.contains(attacker.getUniqueId())) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = this.warned.get(attacker.getUniqueId());
            if (last == null || now - last > 1000L) {
               this.warned.put(attacker.getUniqueId(), now);
               attacker.sendActionBar(this.blocked);
            }
         }
      }
   }

   private void collisions() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         Team team = player.getScoreboard().getEntryTeam(player.getName());
         if (team != null) {
            OptionStatus wanted = this.noPush && this.inZone(player.getLocation()) ? OptionStatus.NEVER : OptionStatus.ALWAYS;
            if (team.getOption(Option.COLLISION_RULE) != wanted) {
               team.setOption(Option.COLLISION_RULE, wanted);
            }
         }
      }
   }
}
