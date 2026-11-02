package dev.flamesentinell.flametweaks;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlameTweaks extends JavaPlugin implements Listener {
   private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();
   private Watch watch;
   private final Map<UUID, Boolean> peeking = new ConcurrentHashMap<>();
   private final Set<UUID> ghosts = ConcurrentHashMap.newKeySet();

   public void onEnable() {
      this.saveDefaultConfig();
      this.getServer().getPluginManager().registerEvents(this, this);
      this.watch = new Watch(this);
      this.getCommand("watch").setExecutor(this.watch);
      this.watch.start();
   }

   public void onDisable() {
      if (this.watch != null) {
         this.watch.stop();
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent var1) {
      double var2 = this.getConfig().getDouble("chat-cooldown-seconds", 3.0);
      if (!(var2 <= 0.0) && !var1.getPlayer().hasPermission("flametweaks.chat.bypass")) {
         long var4 = System.currentTimeMillis();
         Long var6 = this.lastChat.get(var1.getPlayer().getUniqueId());
         long var7 = var6 == null ? 0L : var6 + (long)(var2 * 1000.0) - var4;
         if (var7 > 0L) {
            var1.setCancelled(true);
            String var9 = String.format(Locale.ROOT, "%.1f", var7 / 1000.0);
            var1.getPlayer()
               .sendMessage(
                  MiniMessage.miniMessage()
                     .deserialize(
                        this.getConfig().getString("chat-cooldown-message", "<red>Wait <time>s before chatting again."), Placeholder.unparsed("time", var9)
                     )
               );
         } else {
            this.lastChat.put(var1.getPlayer().getUniqueId(), var4);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.lastChat.remove(var1.getPlayer().getUniqueId());
      this.ghosts.remove(var1.getPlayer().getUniqueId());
      this.peeking.remove(var1.getPlayer().getUniqueId());
   }

   private boolean see(CommandSender var1, String[] var2, boolean var3) {
      if (var1 instanceof Player var4) {
         if (var2.length < 1) {
            var1.sendMessage((var3 ? "/ecsee" : "/invsee") + " <player>");
            return true;
         } else {
            Player var5 = this.getServer().getPlayerExact(var2[0]);
            if (var5 == null) {
               var4.sendMessage(MiniMessage.miniMessage().deserialize("<red>" + var2[0] + " is not online."));
               return true;
            } else {
               var4.openInventory((Inventory)(var3 ? var5.getEnderChest() : var5.getInventory()));
               this.peeking.put(var4.getUniqueId(), var4.hasPermission("flametweaks.invsee.edit"));
               this.getLogger().info(var4.getName() + " opened the " + (var3 ? "ender chest" : "inventory") + " of " + var5.getName());
               return true;
            }
         }
      } else {
         var1.sendMessage("players only");
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPeekClick(InventoryClickEvent var1) {
      Boolean var2 = this.peeking.get(var1.getWhoClicked().getUniqueId());
      if (var2 != null && !var2) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPeekDrag(InventoryDragEvent var1) {
      Boolean var2 = this.peeking.get(var1.getWhoClicked().getUniqueId());
      if (var2 != null && !var2) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onPeekClose(InventoryCloseEvent var1) {
      this.peeking.remove(var1.getPlayer().getUniqueId());
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      if (var5.equals("trash")) {
         return this.trash(var1);
      } else if (var5.equals("vanish")) {
         return this.vanish(var1);
      } else if (!var5.equals("tempban") && !var5.equals("permban") && !var5.equals("unban")) {
         if (var2.getName().equalsIgnoreCase("freeze")) {
            if (var1 instanceof Player var6) {
               if (var4.length < 1) {
                  var6.sendMessage("/freeze <player>");
               } else {
                  var6.performCommand("staff freeze " + var4[0]);
               }
            } else {
               this.getServer().dispatchCommand(var1, "staff freeze " + String.join(" ", var4));
            }

            return true;
         } else if (var2.getName().equalsIgnoreCase("invsee")) {
            return this.see(var1, var4, false);
         } else if (var2.getName().equalsIgnoreCase("ecsee")) {
            return this.see(var1, var4, true);
         } else {
            this.reloadConfig();
            var1.sendMessage("FlameTweaks reloaded");
            return true;
         }
      } else if (!(var1 instanceof Player)
         || var1.hasPermission("flametweaks.ban")
         || var5.equals("tempban") && var1.hasPermission("flametweaks.tempban.limited")) {
         return var5.equals("unban") ? this.unban(var1, var4) : this.ban(var1, var4, var5.equals("tempban"));
      } else {
         var1.sendMessage(MiniMessage.miniMessage().deserialize("<red>Only moderators and up can use this."));
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onTrapdoor(PlayerInteractEvent var1) {
      if (var1.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Block var2 = var1.getClickedBlock();
         if (var2 != null && Tag.TRAPDOORS.isTagged(var2.getType()) && var1.getPlayer().getGameMode() != GameMode.CREATIVE) {
            var1.setUseInteractedBlock(Result.DENY);
         }
      }
   }

   private boolean ban(CommandSender var1, String[] var2, boolean var3) {
      int var4 = var3 ? 2 : 1;
      int var5 = var1 instanceof Player && !var1.hasPermission("flametweaks.ban")
         ? Math.max(1, Math.min(365, this.getConfig().getInt("limited-tempban-max-days", 7)))
         : 365;
      if (var2.length < var4) {
         var1.sendMessage(var3 ? "/tempban <player> <days 1-" + var5 + "> [reason]" : "/permban <player> [reason]");
         return true;
      } else {
         int var6 = 0;
         if (var3) {
            try {
               var6 = Integer.parseInt(var2[1]);
            } catch (NumberFormatException var15) {
               var6 = -1;
            }

            if (var6 < 1 || var6 > var5) {
               var1.sendMessage(MiniMessage.miniMessage().deserialize("<red>Days must be a whole number from 1 to " + var5 + "."));
               return true;
            }
         }

         String var7 = var2.length > var4 ? String.join(" ", Arrays.copyOfRange(var2, var4, var2.length)) : "No reason given";
         OfflinePlayer var8 = this.getServer().getOfflinePlayer(var2[0]);
         if (var8.getName() == null && !var8.hasPlayedBefore() && var8.getPlayer() == null) {
            PlayerProfile var9 = var8.getPlayerProfile();
            if (var9.getUniqueId() == null) {
               var1.sendMessage(MiniMessage.miniMessage().deserialize("<red>No player called " + var2[0] + "."));
               return true;
            }
         }

         if (var1 instanceof Player var16 && var16.getUniqueId().equals(var8.getUniqueId())) {
            var1.sendMessage(MiniMessage.miniMessage().deserialize("<red>You can't ban yourself."));
            return true;
         } else {
            Player var10 = var8.getPlayer();
            if (var10 != null
               && (var10.hasPermission("flametweaks.ban") || var10.hasPermission("flametweaks.tempban.limited"))
               && !var1.hasPermission("flametweaks.ban.override")) {
               var1.sendMessage(MiniMessage.miniMessage().deserialize("<red>You can't ban another staff member."));
               return true;
            } else {
               Date var11 = var3 ? new Date(System.currentTimeMillis() + var6 * 86400000L) : null;
               String var12 = var3 ? var6 + (var6 == 1 ? " day" : " days") : "permanently";
               ((ProfileBanList)this.getServer().getBanList(BanListType.PROFILE)).addBan(var8.getPlayerProfile(), var7, var11, var1.getName());
               String var13 = this.getConfig().getString("ban-appeal", "");
               if (var10 != null) {
                  var10.kick(
                     MiniMessage.miniMessage()
                        .deserialize(
                           "<red><bold>You are banned "
                              + (var3 ? "for " : "")
                              + var12
                              + "</bold>\n<gray>Reason: <white>"
                              + escape(var7)
                              + (var13.isBlank() ? "" : "\n<gray>Appeal: <white>" + escape(var13))
                        )
                  );
               }

               String var14 = var8.getName() != null ? var8.getName() : var2[0];
               this.getLogger().info(var1.getName() + " banned " + var14 + " " + var12 + "  reason " + var7);
               this.notifyStaff("<red>" + escape(var1.getName()) + " banned " + escape(var14) + " " + var12 + "<gray>: " + escape(var7));
               return true;
            }
         }
      }
   }

   private boolean unban(CommandSender var1, String[] var2) {
      if (var2.length < 1) {
         var1.sendMessage("/unban <player>");
         return true;
      } else {
         OfflinePlayer var3 = this.getServer().getOfflinePlayer(var2[0]);
         ProfileBanList var4 = (ProfileBanList)this.getServer().getBanList(BanListType.PROFILE);
         if (!var4.isBanned(var3.getPlayerProfile())) {
            var1.sendMessage(MiniMessage.miniMessage().deserialize("<red>" + escape(var2[0]) + " is not banned."));
            return true;
         } else {
            var4.pardon(var3.getPlayerProfile());
            this.getLogger().info(var1.getName() + " unbanned " + var2[0]);
            this.notifyStaff("<green>" + escape(var1.getName()) + " unbanned " + escape(var2[0]));
            return true;
         }
      }
   }

   private void notifyStaff(String var1) {
      Component var2 = MiniMessage.miniMessage().deserialize(var1);

      for (Player var4 : this.getServer().getOnlinePlayers()) {
         if (var4.hasPermission("flametweaks.ban") || var4.hasPermission("flametweaks.tempban.limited")) {
            var4.sendMessage(var2);
         }
      }

      this.getServer().getConsoleSender().sendMessage(var2);
   }

   private static String escape(String var0) {
      return MiniMessage.miniMessage().escapeTags(var0);
   }

   private boolean trash(CommandSender var1) {
      if (var1 instanceof Player var2) {
         if (this.inCombat(var2)) {
            var2.sendMessage(MiniMessage.miniMessage().deserialize("<red>You can't use the trash while in combat."));
            return true;
         } else {
            var2.openInventory(
               this.getServer()
                  .createInventory(
                     null,
                     54,
                     MiniMessage.miniMessage()
                        .deserialize(this.getConfig().getString("trash-title", "<dark_red>Trash <dark_gray>(closing deletes everything)"))
                  )
            );
            return true;
         }
      } else {
         var1.sendMessage("players only");
         return true;
      }
   }

   private boolean inCombat(Player var1) {
      try {
         Plugin var2 = this.getServer().getPluginManager().getPlugin("FlameFFACore");
         Object var3 = var2.getClass().getMethod("cooldowns").invoke(var2);
         Object var4 = var3.getClass().getMethod("active", UUID.class, String.class).invoke(var3, var1.getUniqueId(), "combat");
         return Boolean.TRUE.equals(var4);
      } catch (ReflectiveOperationException | RuntimeException var5) {
         return false;
      }
   }

   private boolean vanish(CommandSender var1) {
      if (var1 instanceof Player var2) {
         var2.performCommand("staff vanish");
         this.getServer().getScheduler().runTask(this, () -> this.ghost(var2, this.coreVanished(var2)));
         return true;
      } else {
         var1.sendMessage("players only");
         return true;
      }
   }

   private boolean coreVanished(Player var1) {
      try {
         Plugin var2 = this.getServer().getPluginManager().getPlugin("FlameFFACore");
         Object var3 = var2.getClass().getMethod("staff").invoke(var2);
         return Boolean.TRUE.equals(var3.getClass().getMethod("vanished", Player.class).invoke(var3, var1));
      } catch (ReflectiveOperationException | RuntimeException var4) {
         return false;
      }
   }

   private void ghost(Player var1, boolean var2) {
      if (var2) {
         this.ghosts.add(var1.getUniqueId());
         var1.setAllowFlight(true);
         var1.setFlying(true);
      } else {
         this.ghosts.remove(var1.getUniqueId());
         if (var1.getGameMode() == GameMode.SURVIVAL || var1.getGameMode() == GameMode.ADVENTURE) {
            var1.setFlying(false);
            var1.setAllowFlight(false);
         }
      }

      var1.setInvulnerable(var2);
      var1.setCollidable(!var2);
      var1.setCanPickupItems(!var2);
      var1.setSilent(var2);
      var1.sendActionBar(
         MiniMessage.miniMessage().deserialize(var2 ? "<gray>Vanished  <white>flying  invulnerable  hidden from players and tab" : "<gray>Visible again")
      );
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onGhostHit(EntityDamageByEntityEvent var1) {
      if (this.ghosts.contains(var1.getDamager().getUniqueId()) || this.ghosts.contains(var1.getEntity().getUniqueId())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onGhostTarget(EntityTargetEvent var1) {
      if (var1.getTarget() != null && this.ghosts.contains(var1.getTarget().getUniqueId())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onGhostJoin(PlayerJoinEvent var1) {
      Player var2 = var1.getPlayer();
      this.getServer().getScheduler().runTaskLater(this, () -> {
         if (var2.isOnline() && this.coreVanished(var2)) {
            this.ghost(var2, true);
         }
      }, 20L);
   }
}
