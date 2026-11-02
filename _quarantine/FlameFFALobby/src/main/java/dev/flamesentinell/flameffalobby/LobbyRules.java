package dev.flamesentinell.flameffalobby;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class LobbyRules implements Listener {
   private final FlameFFALobby plugin;

   public LobbyRules(FlameFFALobby plugin) {
      this.plugin = plugin;
   }

   private boolean on(String key) {
      return this.plugin.getConfig().getBoolean("rules." + key, true);
   }

   private boolean exempt(Player player) {
      return player.hasPermission("flameffalobby.build") && player.getGameMode() == GameMode.CREATIVE;
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (this.on("force-adventure") && !player.hasPermission("flameffalobby.build")) {
         player.setGameMode(GameMode.ADVENTURE);
      }

      if (this.on("clear-inventory-on-join") && !this.exempt(player)) {
         player.getInventory().clear();
         player.getInventory().setArmorContents(null);
         player.setItemOnCursor(null);
      }

      AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
      player.setHealth(max == null ? 20.0 : max.getValue());
      player.setFoodLevel(20);
      player.setSaturation(20.0F);
      player.setFireTicks(0);
      Location spawn = this.plugin.spawn();
      if (spawn != null) {
         player.teleport(spawn);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player && this.on("invulnerable")) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onHunger(FoodLevelChangeEvent event) {
      if (this.on("no-hunger")) {
         event.setCancelled(true);
         if (event.getEntity() instanceof Player player) {
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      if (this.on("no-block-changes") && !this.exempt(event.getPlayer())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      if (this.on("no-block-changes") && !this.exempt(event.getPlayer())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent event) {
      if (this.on("no-item-drops") && !this.exempt(event.getPlayer())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onInventory(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player && !this.exempt(player)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.on("void-catch")) {
         double floor = this.plugin.getConfig().getDouble("rules.void-y", -20.0);
         if (!(event.getTo().getY() > floor)) {
            Location spawn = this.plugin.spawn();
            if (spawn != null) {
               event.getPlayer().teleport(spawn);
               event.getPlayer().setFallDistance(0.0F);
            }
         }
      }
   }
}
