package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.staff.StaffRole;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class StaffCommand implements BasicCommand {
   private final FlameFFACore plugin;

   public StaffCommand(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public boolean canUse(@NotNull CommandSender sender) {
      return this.plugin.staff().role(sender) != StaffRole.NONE;
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      CommandSender sender = source.getSender();
      if (args.length == 0) {
         sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/staff freeze|vanish|invsee|role <player>"));
      } else {
         String var4 = args[0].toLowerCase(Locale.ROOT);
         switch (var4) {
            case "freeze": {
               Player target = this.target(sender, args, 1);
               if (target == null) {
                  return;
               }

               this.plugin.staff().perform(sender, StaffRole.SCREENSHARER, target, "freeze", "toggled freeze", () -> {
                  boolean nowFrozen = this.plugin.freeze().toggle(target);
                  sender.sendMessage(this.plugin.configs().msg(nowFrozen ? "staff.freeze-on" : "staff.freeze-off", "target", target.getName()));
               });
               break;
            }
            case "vanish":
               if (!(sender instanceof Player player)) {
                  sender.sendMessage(this.plugin.configs().msg("general.players-only"));
                  return;
               }

               this.plugin
                  .staff()
                  .perform(
                     sender,
                     StaffRole.MODERATOR,
                     null,
                     "vanish",
                     "toggled vanish",
                     () -> this.plugin.staff().setVanished(player, !this.plugin.staff().vanished(player))
                  );
               break;
            case "invsee": {
               if (!(sender instanceof Player player)) {
                  sender.sendMessage(this.plugin.configs().msg("general.players-only"));
                  return;
               }

               Player target = this.target(sender, args, 1);
               if (target == null) {
                  return;
               }

               this.plugin
                  .staff()
                  .perform(sender, StaffRole.SCREENSHARER, target, "invsee", "opened inventory", () -> player.openInventory(target.getInventory()));
               break;
            }
            case "role": {
               Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
               CommandSender subject = (CommandSender)(target == null ? sender : target);
               sender.sendMessage(Component.text(subject.getName() + " -> " + this.plugin.staff().role(subject).id()));
               break;
            }
            default:
               sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/staff freeze|vanish|invsee|role <player>"));
         }
      }
   }

   private Player target(CommandSender sender, String[] args, int index) {
      if (args.length <= index) {
         sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/staff " + args[0] + " <player>"));
         return null;
      } else {
         Player target = Bukkit.getPlayerExact(args[index]);
         if (target == null) {
            sender.sendMessage(this.plugin.configs().msg("general.unknown-player", "name", args[index]));
         }

         return target;
      }
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (args.length <= 1) {
         return List.of("freeze", "vanish", "invsee", "role");
      } else if (args.length == 2) {
         List<String> names = new ArrayList<>();

         for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
               names.add(online.getName());
            }
         }

         return names;
      } else {
         return List.of();
      }
   }
}
