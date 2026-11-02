package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public final class SpawnCommand implements BasicCommand, Listener {
   private final FlameFFACore plugin;
   private final Map<UUID, BukkitTask> pending = new HashMap<>();

   public SpawnCommand(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (source.getSender() instanceof Player player) {
         UUID var6 = player.getUniqueId();
         if (this.plugin.arena().inCombat(player)) {
            player.sendMessage(this.plugin.configs().msg("spawn.combat"));
         } else if (this.pending.containsKey(var6)) {
            player.sendMessage(this.plugin.configs().msg("spawn.already"));
         } else if (this.plugin.arena().inSafezone(player.getLocation())) {
            player.sendMessage(this.plugin.configs().msg("spawn.already-there"));
         } else {
            int warmup = this.plugin.configs().main().getInt("spawn.warmup-seconds", 3);
            if (warmup > 0 && !this.plugin.staff().isRoot(player)) {
               player.sendMessage(this.plugin.configs().msg("spawn.warmup", "seconds", String.valueOf(warmup)));
               this.pending.put(var6, Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                  this.pending.remove(var6);
                  if (player.isOnline() && !player.isDead() && !this.plugin.arena().inCombat(player)) {
                     this.go(player);
                  }
               }, warmup * 20L));
            } else {
               this.go(player);
            }
         }
      } else {
         source.getSender().sendMessage(this.plugin.configs().msg("general.players-only"));
      }
   }

   private void go(Player player) {
      Location spawn = this.plugin.arena().spawn();
      player.setFallDistance(0.0F);
      player.teleport(spawn);
      player.sendMessage(this.plugin.configs().msg("spawn.arrived"));
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player) {
         BukkitTask task = this.pending.remove(player.getUniqueId());
         if (task != null) {
            task.cancel();
            player.sendMessage(this.plugin.configs().msg("spawn.cancelled"));
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      BukkitTask task = this.pending.remove(event.getPlayer().getUniqueId());
      if (task != null) {
         task.cancel();
      }
   }
}
