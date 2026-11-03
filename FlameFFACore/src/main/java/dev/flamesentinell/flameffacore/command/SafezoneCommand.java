package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SafezoneCommand implements BasicCommand {
   private static final String USAGE = "/safezone pos1|pos2|set [x1 y1 z1 x2 y2 z2]|info|clear";
   private final FlameFFACore plugin;
   private final Map<UUID, Location[]> corners = new ConcurrentHashMap<>();

   public SafezoneCommand(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public boolean canUse(@NotNull CommandSender sender) {
      return this.plugin.staff().isOperator(sender);
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      CommandSender sender = source.getSender();
      if (!this.plugin.staff().isOperator(sender)) {
         sender.sendMessage(this.plugin.configs().msg("general.operator-only"));
      } else if (args.length == 0) {
         sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/safezone pos1|pos2|set [x1 y1 z1 x2 y2 z2]|info|clear"));
      } else {
         String sub = args[0].toLowerCase(Locale.ROOT);
         if (sub.equals("info")) {
            sender.sendMessage(this.plugin.configs().msg("safezone.info", "zone", this.plugin.safezone().describe()));
         } else if (sub.equals("clear")) {
            sender.sendMessage(this.plugin.configs().msg(this.plugin.safezone().clear() ? "safezone.cleared" : "safezone.save-failed"));
         } else if (sender instanceof Player player) {
            switch (sub) {
               case "pos1":
               case "pos2":
                  Location[] pair = this.corners.computeIfAbsent(player.getUniqueId(), k -> new Location[2]);
                  int idx = sub.equals("pos1") ? 0 : 1;
                  pair[idx] = player.getLocation().toBlockLocation();
                  player.sendMessage(
                     this.plugin
                        .configs()
                        .msg(
                           "safezone.corner",
                           "n",
                           String.valueOf(idx + 1),
                           "x",
                           String.valueOf(pair[idx].getBlockX()),
                           "y",
                           String.valueOf(pair[idx].getBlockY()),
                           "z",
                           String.valueOf(pair[idx].getBlockZ())
                        )
                  );
                  break;
               case "set":
                  this.set(player, player, args);
                  break;
               default:
                  player.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/safezone pos1|pos2|set [x1 y1 z1 x2 y2 z2]|info|clear"));
            }
         } else {
            if (sub.equals("set") && args.length >= 7) {
               this.set(sender, null, args);
            } else {
               sender.sendMessage(this.plugin.configs().msg("general.players-only"));
            }
         }
      }
   }

   private void set(CommandSender sender, Player player, String[] args) {
      int[] c = new int[6];
      World world = player != null ? player.getWorld() : this.plugin.arena().spawn().getWorld();
      if (args.length >= 7) {
         try {
            Location own = player != null ? player.getLocation() : new Location(world, 0.0, 0.0, 0.0);
            double[] base = new double[]{own.getX(), own.getY(), own.getZ(), own.getX(), own.getY(), own.getZ()};

            for (int i = 0; i < 6; i++) {
               c[i] = (int)Math.floor(coord(args[i + 1], base[i]));
            }
         } catch (NumberFormatException var9) {
            sender.sendMessage(this.plugin.configs().msg("safezone.bad-coords"));
            return;
         }
      } else {
         Location[] pair = this.corners.get(player.getUniqueId());
         if (pair == null || pair[0] == null || pair[1] == null) {
            player.sendMessage(this.plugin.configs().msg("safezone.need-corners"));
            return;
         }

         c = new int[]{pair[0].getBlockX(), pair[0].getBlockY(), pair[0].getBlockZ(), pair[1].getBlockX(), pair[1].getBlockY(), pair[1].getBlockZ()};
      }

      boolean ok = this.plugin.safezone().define(world, c[0], c[1], c[2], c[3], c[4], c[5]);
      sender.sendMessage(this.plugin.configs().msg(ok ? "safezone.set" : "safezone.save-failed", "zone", this.plugin.safezone().describe()));
   }

   private static double coord(String raw, double own) {
      if (raw.startsWith("~")) {
         return raw.length() == 1 ? own : own + Double.parseDouble(raw.substring(1));
      } else {
         return Double.parseDouble(raw);
      }
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
      return args.length <= 1 ? List.of("pos1", "pos2", "set", "info", "clear") : List.of();
   }
}
