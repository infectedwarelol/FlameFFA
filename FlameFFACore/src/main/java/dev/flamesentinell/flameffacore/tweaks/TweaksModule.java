package dev.flamesentinell.flameffacore.tweaks;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.ffa.EnderVault;
import dev.flamesentinell.flameffacore.ffa.OfflineInvsee;
import dev.flamesentinell.flameffacore.module.Module;
import dev.flamesentinell.flameffacore.module.ModuleCommand;
import dev.flamesentinell.flameffacore.module.ModuleConfig;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TweaksModule implements Module, Listener {
   private static final MiniMessage MM = MiniMessage.miniMessage();
   private static final int MAX_REASON = 256;
   private final FlameFFACore plugin;
   private final ModuleConfig config;
   private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();
   private final Set<UUID> peeking = ConcurrentHashMap.newKeySet();
   private final Set<UUID> ghosts = ConcurrentHashMap.newKeySet();
   private Watch watch;

   public TweaksModule(FlameFFACore plugin) {
      this.plugin = plugin;
      this.config = new ModuleConfig(plugin, "tweaks.yml", "FlameTweaks", "config.yml");
   }

   @Override
   public String id() {
      return "tweaks";
   }

   @Override
   public void enable() {
      this.config.load();
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      this.watch = new Watch(this.plugin, this);
      this.watch.start();
   }

   @Override
   public void disable() {
      HandlerList.unregisterAll(this);
      if (this.watch != null) {
         this.watch.stop();
         this.watch = null;
      }

      this.lastChat.clear();
      this.peeking.clear();
      this.ghosts.clear();
   }

   @Override
   public void commands(Commands commands) {
      ModuleCommand.register(commands, "flametweaks", "reload the config", List.of(), "flametweaks.admin", null, (sender, label, args) -> {
         this.config.load();
         sender.sendMessage("FlameTweaks reloaded");
         return true;
      }, null);
      ModuleCommand.register(
         commands, "tempban", "ban a player for 1 to 365 days", List.of(), null, "/tempban <player> <days> [reason]", (sender, label, args) -> this.bans(sender, label, args), TweaksModule::online
      );
      ModuleCommand.register(
         commands, "permban", "ban a player permanently", List.of(), null, "/permban <player> [reason]", (sender, label, args) -> this.bans(sender, label, args), TweaksModule::online
      );
      ModuleCommand.register(commands, "unban", "lift a ban", List.of(), null, "/unban <player>", (sender, label, args) -> this.bans(sender, label, args), null);
      ModuleCommand.register(
         commands, "vanish", "hide from players and tab  fly  and be invulnerable  admins and up", List.of("v"), "flametweaks.vanish", null, (sender, label, args) -> this.vanish(sender), null
      );
      ModuleCommand.register(
         commands,
         "trash",
         "open a trash can  everything left in it is deleted when you close it",
         List.of("disposal", "bin"),
         null,
         null,
         (sender, label, args) -> this.trash(sender),
         null
      );
      ModuleCommand.register(
         commands, "freeze", "freeze or unfreeze a player for a screenshare", List.of("ss"), null, "/freeze <player>", (sender, label, args) -> this.freeze(sender, args), TweaksModule::online
      );
      ModuleCommand.register(
         commands,
         "invsee",
         "see a player's inventory",
         List.of(),
         "flametweaks.invsee",
         "/invsee <player>",
         (sender, label, args) -> this.see(sender, args, false),
         TweaksModule::online
      );
      ModuleCommand.register(
         commands,
         "ecsee",
         "see a player's ender chest",
         List.of(),
         "flametweaks.invsee",
         "/ecsee <player>",
         (sender, label, args) -> this.see(sender, args, true),
         TweaksModule::online
      );
      ModuleCommand.register(
         commands,
         "watch",
         "record every tick and input of a player for cheat review",
         List.of(),
         "flametweaks.watch",
         "/watch add|remove|list <player>",
         (sender, label, args) -> this.watch != null && this.watch.command(sender, args),
         (sender, args) -> args.length <= 1 ? List.of("add", "remove", "list") : online(sender, args)
      );
   }

   private static List<String> online(CommandSender sender, String[] args) {
      String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
      return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
   }

   FileConfiguration cfg() {
      return this.config.get();
   }

   void saveConfig() {
      this.config.saveAsync();
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent event) {
      double seconds = this.cfg().getDouble("chat-cooldown-seconds", 3.0);
      Player player = event.getPlayer();
      if (seconds > 0.0 && !player.hasPermission("flametweaks.chat.bypass")) {
         long now = System.currentTimeMillis();
         Long last = this.lastChat.get(player.getUniqueId());
         long left = last == null ? 0L : last + (long)(seconds * 1000.0) - now;
         if (left > 0L) {
            event.setCancelled(true);
            String shown = String.format(Locale.ROOT, "%.1f", left / 1000.0);
            player.sendMessage(MM.deserialize(this.cfg().getString("chat-cooldown-message", "<red>Wait <time>s before chatting again."), Placeholder.unparsed("time", shown)));
         } else {
            this.lastChat.put(player.getUniqueId(), now);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.lastChat.remove(uuid);
      this.ghosts.remove(uuid);
      this.peeking.remove(uuid);
   }

   private boolean see(CommandSender sender, String[] args, boolean enderChest) {
      if (!(sender instanceof Player viewer)) {
         sender.sendMessage("players only");
      } else if (args.length < 1) {
         sender.sendMessage((enderChest ? "/ecsee" : "/invsee") + " <player>");
      } else {
         Player target = Bukkit.getPlayerExact(args[0]);
         if (target == null) {
            if (enderChest) {
               EnderVault.openOffline(viewer, args[0]);
            } else {
               OfflineInvsee.openOffline(viewer, args[0]);
            }
         } else {
            viewer.openInventory(enderChest ? target.getEnderChest() : target.getInventory());
            this.peeking.add(viewer.getUniqueId());
            this.plugin.getSLF4JLogger().info("{} opened the {} of {}", viewer.getName(), enderChest ? "ender chest" : "inventory", target.getName());
         }
      }

      return true;
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPeekClick(InventoryClickEvent event) {
      if (!this.peeking.isEmpty() && this.peeking.contains(event.getWhoClicked().getUniqueId()) && !event.getWhoClicked().hasPermission("flametweaks.invsee.edit")) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPeekDrag(InventoryDragEvent event) {
      if (!this.peeking.isEmpty() && this.peeking.contains(event.getWhoClicked().getUniqueId()) && !event.getWhoClicked().hasPermission("flametweaks.invsee.edit")) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onPeekClose(InventoryCloseEvent event) {
      this.peeking.remove(event.getPlayer().getUniqueId());
   }

   private boolean freeze(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         if (args.length < 1) {
            player.sendMessage("/freeze <player>");
         } else {
            player.performCommand("staff freeze " + args[0]);
         }
      } else {
         Bukkit.dispatchCommand(sender, "staff freeze " + String.join(" ", args));
      }

      return true;
   }

   private boolean bans(CommandSender sender, String label, String[] args) {
      boolean temporary = label.equals("tempban");
      if (sender instanceof Player && !sender.hasPermission("flametweaks.ban") && !(temporary && sender.hasPermission("flametweaks.tempban.limited"))) {
         sender.sendMessage(MM.deserialize("<red>Only moderators and up can use this."));
         return true;
      } else {
         return label.equals("unban") ? this.unban(sender, args) : this.ban(sender, args, temporary);
      }
   }

   private boolean ban(CommandSender sender, String[] args, boolean temporary) {
      int needed = temporary ? 2 : 1;
      int maxDays = sender instanceof Player && !sender.hasPermission("flametweaks.ban") ? Math.max(1, Math.min(365, this.cfg().getInt("limited-tempban-max-days", 7))) : 365;
      if (args.length < needed) {
         sender.sendMessage(temporary ? "/tempban <player> <days 1-" + maxDays + "> [reason]" : "/permban <player> [reason]");
         return true;
      } else {
         int days = 0;
         if (temporary) {
            try {
               days = Integer.parseInt(args[1]);
            } catch (NumberFormatException malformed) {
               days = -1;
            }

            if (days < 1 || days > maxDays) {
               sender.sendMessage(MM.deserialize("<red>Days must be a whole number from 1 to " + maxDays + "."));
               return true;
            }
         }

         String reason = args.length > needed ? String.join(" ", Arrays.copyOfRange(args, needed, args.length)) : "No reason given";
         if (reason.length() > MAX_REASON) {
            reason = reason.substring(0, MAX_REASON);
         }

         String name = args[0];
         String finalReason = reason;
         int finalDays = days;
         this.lookup(name, target -> this.applyBan(sender, name, target, finalReason, temporary, finalDays));
         return true;
      }
   }

   private void lookup(String name, Consumer<OfflinePlayer> then) {
      Player online = Bukkit.getPlayerExact(name);
      OfflinePlayer known = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
      if (known != null) {
         this.plugin.sync(() -> then.accept(known));
      } else {
         this.plugin.async(() -> {
            OfflinePlayer resolved = Bukkit.getOfflinePlayer(name);
            this.plugin.sync(() -> then.accept(resolved));
         });
      }
   }

   private void applyBan(CommandSender sender, String typed, OfflinePlayer target, String reason, boolean temporary, int days) {
      if (target.getName() == null && !target.hasPlayedBefore() && target.getPlayer() == null) {
         PlayerProfile profile = target.getPlayerProfile();
         if (profile.getUniqueId() == null) {
            sender.sendMessage(MM.deserialize("<red>No player called <name>.", Placeholder.unparsed("name", typed)));
            return;
         }
      }

      if (sender instanceof Player self && self.getUniqueId().equals(target.getUniqueId())) {
         sender.sendMessage(MM.deserialize("<red>You can't ban yourself."));
      } else {
         Player online = target.getPlayer();
         if (online != null
            && (online.hasPermission("flametweaks.ban") || online.hasPermission("flametweaks.tempban.limited"))
            && !sender.hasPermission("flametweaks.ban.override")) {
            sender.sendMessage(MM.deserialize("<red>You can't ban another staff member."));
         } else {
            Date until = temporary ? new Date(System.currentTimeMillis() + days * 86400000L) : null;
            String length = temporary ? days + (days == 1 ? " day" : " days") : "permanently";
            ((ProfileBanList)Bukkit.getBanList(BanListType.PROFILE)).addBan(target.getPlayerProfile(), reason, until, sender.getName());
            String appeal = this.cfg().getString("ban-appeal", "");
            if (online != null) {
               Component screen = MM.deserialize(
                  "<red><bold>You are banned "
                     + (temporary ? "for " : "")
                     + length
                     + "</bold>\n<gray>Reason: <white>"
                     + escape(reason)
                     + (appeal.isBlank() ? "" : "\n<gray>Appeal: <white>" + escape(appeal))
               );
               this.plugin.tasks().runOn(online, () -> online.kick(screen));
            }

            String shown = target.getName() != null ? target.getName() : typed;
            this.plugin.getSLF4JLogger().info("{} banned {} {}  reason {}", sender.getName(), shown, length, reason);
            this.notifyStaff("<red>" + escape(sender.getName()) + " banned " + escape(shown) + " " + length + "<gray>: " + escape(reason));
         }
      }
   }

   private boolean unban(CommandSender sender, String[] args) {
      if (args.length < 1) {
         sender.sendMessage("/unban <player>");
      } else {
         String name = args[0];
         this.lookup(name, target -> {
            ProfileBanList bans = (ProfileBanList)Bukkit.getBanList(BanListType.PROFILE);
            if (!bans.isBanned(target.getPlayerProfile())) {
               sender.sendMessage(MM.deserialize("<red>" + escape(name) + " is not banned."));
            } else {
               bans.pardon(target.getPlayerProfile());
               this.plugin.getSLF4JLogger().info("{} unbanned {}", sender.getName(), name);
               this.notifyStaff("<green>" + escape(sender.getName()) + " unbanned " + escape(name));
            }
         });
      }

      return true;
   }

   private void notifyStaff(String raw) {
      Component message = MM.deserialize(raw);

      for (Player online : Bukkit.getOnlinePlayers()) {
         if (online.hasPermission("flametweaks.ban") || online.hasPermission("flametweaks.tempban.limited")) {
            online.sendMessage(message);
         }
      }

      Bukkit.getConsoleSender().sendMessage(message);
   }

   private static String escape(String raw) {
      return MM.escapeTags(raw);
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onTrapdoor(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Block block = event.getClickedBlock();
         if (block != null && Tag.TRAPDOORS.isTagged(block.getType()) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setUseInteractedBlock(Result.DENY);
         }
      }
   }

   private boolean trash(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("players only");
      } else if (this.plugin.cooldowns().active(player.getUniqueId(), "combat")) {
         player.sendMessage(MM.deserialize("<red>You can't use the trash while in combat."));
      } else {
         player.openInventory(
            Bukkit.createInventory(null, 54, MM.deserialize(this.cfg().getString("trash-title", "<dark_red>Trash <dark_gray>(closing deletes everything)")))
         );
      }

      return true;
   }

   private boolean vanish(CommandSender sender) {
      if (sender instanceof Player player) {
         player.performCommand("staff vanish");
         this.plugin.tasks().entity(player, () -> this.ghost(player, this.plugin.staff().vanished(player)));
      } else {
         sender.sendMessage("players only");
      }

      return true;
   }

   private void ghost(Player player, boolean hidden) {
      if (hidden) {
         this.ghosts.add(player.getUniqueId());
         player.setAllowFlight(true);
         player.setFlying(true);
      } else {
         this.ghosts.remove(player.getUniqueId());
         if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
         }
      }

      player.setInvulnerable(hidden);
      player.setCollidable(!hidden);
      player.setCanPickupItems(!hidden);
      player.setSilent(hidden);
      player.sendActionBar(MM.deserialize(hidden ? "<gray>Vanished  <white>flying  invulnerable  hidden from players and tab" : "<gray>Visible again"));
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onGhostHit(EntityDamageByEntityEvent event) {
      if (!this.ghosts.isEmpty() && (this.ghosts.contains(event.getDamager().getUniqueId()) || this.ghosts.contains(event.getEntity().getUniqueId()))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onGhostTarget(EntityTargetEvent event) {
      if (event.getTarget() != null && !this.ghosts.isEmpty() && this.ghosts.contains(event.getTarget().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onGhostJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.plugin.tasks().entityLater(player, () -> {
         if (player.isOnline() && this.plugin.staff().vanished(player)) {
            this.ghost(player, true);
         }
      }, 20L);
   }
}
