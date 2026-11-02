package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Bots;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class AfkWatcher implements Listener {
   public static final String CHANNEL = "bungeecord:main";
   private final FlameFFACore plugin;
   private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
   private final Map<UUID, Boolean> warned = new ConcurrentHashMap<>();
   private long idleMs = 300000L;
   private long warnMs = 30000L;
   private String action = "lobby";
   private String server = "lobby";
   private String exempt = "flameffacore.afk.exempt";

   public AfkWatcher(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void load() {
      FileConfiguration cfg = this.plugin.configs().main();
      this.idleMs = Math.max(10, cfg.getInt("afk.seconds", 300)) * 1000L;
      this.warnMs = Math.max(0, cfg.getInt("afk.warn-seconds", 30)) * 1000L;
      this.action = cfg.getString("afk.action", "lobby").toLowerCase(Locale.ROOT);
      this.server = cfg.getString("afk.server", "lobby");
      this.exempt = cfg.getString("afk.exempt-permission", "flameffacore.afk.exempt");
      if (!Bukkit.getMessenger().isOutgoingChannelRegistered(this.plugin, "bungeecord:main")) {
         Bukkit.getMessenger().registerOutgoingPluginChannel(this.plugin, "bungeecord:main");
      }
   }

   private void active(Player player) {
      UUID id = player.getUniqueId();
      this.lastActive.put(id, System.currentTimeMillis());
      this.warned.remove(id);
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      Location from = event.getFrom();
      Location to = event.getTo();
      if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ() || from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()) {
         this.active(event.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent event) {
      this.active(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onSwing(PlayerAnimationEvent event) {
      this.active(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onInventory(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         this.active(player);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHeld(PlayerItemHeldEvent event) {
      this.active(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onSwap(PlayerSwapHandItemsEvent event) {
      this.active(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent event) {
      this.active(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      this.active(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent event) {
      this.active(event.getPlayer());
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (!Bots.is(event.getPlayer())) {
         this.active(event.getPlayer());
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.lastActive.remove(event.getPlayer().getUniqueId());
      this.warned.remove(event.getPlayer().getUniqueId());
   }

   public void tick(Player player) {
      if (!this.action.equals("none") && !player.hasPermission(this.exempt)) {
         UUID id = player.getUniqueId();
         if (!this.plugin.safezone().contains(player.getLocation())) {
            this.lastActive.put(id, System.currentTimeMillis());
            this.warned.remove(id);
         } else {
            long since = System.currentTimeMillis() - this.lastActive.getOrDefault(id, System.currentTimeMillis());
            long left = this.idleMs - since;
            if (left <= 0L) {
               this.move(player);
            } else {
               if (this.warnMs > 0L && left <= this.warnMs) {
                  this.warned.put(id, Boolean.TRUE);
                  this.plugin.cooldownBar().flash(player, this.plugin.configs().msg("afk.warning", "time", String.valueOf((left + 999L) / 1000L)));
               }
            }
         }
      }
   }

   private void move(Player player) {
      UUID id = player.getUniqueId();
      this.lastActive.put(id, System.currentTimeMillis());
      this.warned.remove(id);
      if (this.action.equals("kick")) {
         player.kick(this.plugin.configs().msg("afk.kicked"));
      } else if (!this.send(player, this.server)) {
         player.sendMessage(this.plugin.configs().msg("afk.no-lobby"));
      } else {
         player.sendMessage(this.plugin.configs().msg("afk.moved"));
      }
   }

   public boolean send(Player player, String target) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try (DataOutputStream out = new DataOutputStream(bytes)) {
         out.writeUTF("Connect");
         out.writeUTF(target);
      } catch (IOException var10) {
         return false;
      }

      try {
         player.sendPluginMessage(this.plugin, "bungeecord:main", bytes.toByteArray());
         return true;
      } catch (Exception var8) {
         this.plugin.getSLF4JLogger().warn("could not send {} to {}", new Object[]{player.getName(), target, var8});
         return false;
      }
   }

   public long idleSeconds(Player player) {
      Long last = this.lastActive.get(player.getUniqueId());
      return last == null ? 0L : (System.currentTimeMillis() - last) / 1000L;
   }

   public long idleLimitSeconds() {
      return this.idleMs / 1000L;
   }

   public String action() {
      return this.action;
   }

   public String targetServer() {
      return this.server;
   }
}
