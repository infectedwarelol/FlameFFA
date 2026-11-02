package dev.flamesentinell.flameffacore.staff;

import java.util.concurrent.ConcurrentHashMap;
import dev.flamesentinell.flameffacore.FlameFFACore;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class FreezeService implements Listener {
   private static final String MOVE_NOTICE = "frozenmove";
   private final FlameFFACore plugin;
   private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

   public FreezeService(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public boolean frozen(Player player) {
      return this.frozen.contains(player.getUniqueId());
   }

   public boolean toggle(Player target) {
      if (this.frozen(target)) {
         this.frozen.remove(target.getUniqueId());
         target.sendMessage(this.plugin.configs().msg("staff.freeze-off", "target", target.getName()));
         return false;
      } else {
         this.frozen.add(target.getUniqueId());
         target.showTitle(
            Title.title(
               this.plugin.configs().msg("staff.frozen"),
               this.plugin.configs().msg("staff.frozen-sub"),
               Times.times(Duration.ofMillis(300L), Duration.ofSeconds(8L), Duration.ofMillis(500L))
            )
         );
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (!this.frozen.isEmpty() && this.frozen(event.getPlayer())) {
         if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()
            || !(event.getTo().getY() <= event.getFrom().getY())) {
            event.setTo(event.getFrom());
            UUID uuid = event.getPlayer().getUniqueId();
            if (!this.plugin.cooldowns().active(uuid, "frozenmove")) {
               this.plugin.cooldowns().set(uuid, "frozenmove", 3L);
               event.getPlayer().sendMessage(this.plugin.configs().msg("staff.frozen-move"));
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onTeleport(PlayerTeleportEvent event) {
      if (this.frozen(event.getPlayer()) && event.getCause() != TeleportCause.PLUGIN) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player && this.frozen(player)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent event) {
      if (this.frozen(event.getPlayer())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onOpenContainer(InventoryOpenEvent event) {
      if (event.getPlayer() instanceof Player player && this.frozen(player)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player && this.frozen(player)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.frozen(event.getPlayer())) {
         String typed = event.getMessage().toLowerCase(Locale.ROOT).split(" ")[0];

         for (String allowed : this.plugin.configs().main().getStringList("staff.freeze.allowed-commands")) {
            if (typed.equals(allowed.toLowerCase(Locale.ROOT))) {
               return;
            }
         }

         event.setCancelled(true);
         event.getPlayer().sendMessage(this.plugin.configs().msg("staff.frozen-command"));
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      if (this.frozen.remove(player.getUniqueId())) {
         String command = this.plugin.configs().main().getString("staff.freeze.logout-command", "");
         if (command != null && !command.isBlank()) {
            String resolved = command.replace("{player}", player.getName());
            this.plugin.async(() -> this.plugin.audit().staff(null, "CONSOLE", "freeze_logout", player.getUniqueId(), player.getName(), resolved));
            this.plugin.sync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved));
         }
      }
   }
}
