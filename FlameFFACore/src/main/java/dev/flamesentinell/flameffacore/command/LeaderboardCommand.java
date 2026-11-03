package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.ui.LeaderboardService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class LeaderboardCommand implements BasicCommand {
   private static final String USAGE = "/leaderboard set <kills|deaths|streak|playtime|damage> [size] | remove | list";
   private final FlameFFACore plugin;

   public LeaderboardCommand(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public boolean canUse(@NotNull CommandSender sender) {
      return this.plugin.staff().isOperator(sender);
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      CommandSender sender = source.getSender();
      if (args.length == 0) {
         sender.sendMessage(Component.text("/leaderboard set <kills|deaths|streak|playtime|damage> [size] | remove | list"));
      } else {
         String var4 = args[0].toLowerCase(Locale.ROOT);
         switch (var4) {
            case "set":
               this.set(sender, args);
               break;
            case "remove":
               this.remove(sender);
               break;
            case "list":
               sender.sendMessage(this.plugin.configs().msg("leaderboard.count", "amount", String.valueOf(this.plugin.leaderboards().count())));
               break;
            default:
               sender.sendMessage(Component.text("/leaderboard set <kills|deaths|streak|playtime|damage> [size] | remove | list"));
         }
      }
   }

   private void set(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(Component.text("/leaderboard set <kills|deaths|streak|playtime|damage> [size] | remove | list"));
      } else {
         String metric = args[1].toLowerCase(Locale.ROOT);
         if (!LeaderboardService.METRICS.contains(metric)) {
            sender.sendMessage(Component.text("metrics: " + String.join(", ", LeaderboardService.METRICS)));
         } else {
            int size = 10;
            if (args.length > 2) {
               try {
                  size = Integer.parseInt(args[2]);
               } catch (NumberFormatException var9) {
                  sender.sendMessage(Component.text("size has to be a number"));
                  return;
               }
            }

            Location at;
            if (args.length >= 6) {
               try {
                  at = new Location((World)Bukkit.getWorlds().get(0), Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]));
                  if (!Double.isFinite(at.getX()) || !Double.isFinite(at.getY()) || !Double.isFinite(at.getZ())) {
                     throw new NumberFormatException("coordinate out of range");
                  }
               } catch (NumberFormatException var8) {
                  sender.sendMessage(Component.text("/leaderboard set <metric> <size> <x> <y> <z>"));
                  return;
               }
            } else {
               if (!(sender instanceof Player player)) {
                  sender.sendMessage(Component.text("/leaderboard set <metric> <size> <x> <y> <z> from the console"));
                  return;
               }

               at = player.getEyeLocation();
            }

            this.plugin.leaderboards().place(at, metric, size);
            sender.sendMessage(this.plugin.configs().msg("leaderboard.placed", "metric", metric));
         }
      }
   }

   private void remove(CommandSender sender) {
      if (sender instanceof Player player) {
         boolean gone = this.plugin.leaderboards().removeNear(player.getLocation(), 6.0);
         sender.sendMessage(this.plugin.configs().msg(gone ? "leaderboard.removed" : "leaderboard.none-near"));
      } else {
         sender.sendMessage(Component.text("in game only"));
      }
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (args.length <= 1) {
         return List.of("set", "remove", "list");
      } else {
         return args[0].equalsIgnoreCase("set") && args.length == 2 ? LeaderboardService.METRICS : List.of();
      }
   }
}
