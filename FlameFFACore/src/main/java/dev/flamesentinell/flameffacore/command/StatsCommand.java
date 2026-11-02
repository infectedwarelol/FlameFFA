package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.storage.PlayerStore;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class StatsCommand implements BasicCommand {
   private final FlameFFACore plugin;

   public StatsCommand(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      CommandSender sender = source.getSender();
      String who = args.length > 0 ? args[0] : (sender instanceof Player p ? p.getName() : null);
      if (who == null) {
         sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/stats <player>"));
      } else {
         this.plugin.async(() -> {
            Player online = Bukkit.getPlayerExact(who);
            UUID uuid = online != null ? online.getUniqueId() : this.plugin.players().uuidByName(who);
            if (uuid == null) {
               this.plugin.sync(() -> sender.sendMessage(this.plugin.configs().msg("general.unknown-player", "name", who)));
            } else {
               String name = online != null ? online.getName() : who;
               PlayerStore.Stats stats = this.plugin.players().stats(uuid);
               this.plugin.sync(() -> this.print(sender, name, stats));
            }
         });
      }
   }

   private void print(CommandSender sender, String name, PlayerStore.Stats stats) {
      sender.sendMessage(this.plugin.configs().msg("stats.header", "player", name));
      sender.sendMessage(this.plugin.configs().msg("stats.line", "label", "Kills", "value", String.valueOf(stats.kills())));
      sender.sendMessage(this.plugin.configs().msg("stats.line", "label", "Deaths", "value", String.valueOf(stats.deaths())));
      sender.sendMessage(this.plugin.configs().msg("stats.line", "label", "K/D", "value", ratio(stats.kills(), stats.deaths())));
      sender.sendMessage(this.plugin.configs().msg("stats.line", "label", "Best Streak", "value", String.valueOf(stats.bestStreak())));
      sender.sendMessage(this.plugin.configs().msg("stats.line", "label", "Playtime", "value", playtime(stats.playtimeMs())));
   }

   private static String ratio(int kills, int deaths) {
      double value = deaths <= 0 ? kills : (double)kills / deaths;
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private static String playtime(long ms) {
      long minutes = ms / 60000L;
      long hours = minutes / 60L;
      if (hours >= 24L) {
         return hours / 24L + "d " + hours % 24L + "h";
      } else {
         return hours > 0L ? hours + "h " + minutes % 60L + "m" : minutes + "m";
      }
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (args.length > 1) {
         return List.of();
      } else {
         List<String> names = new ArrayList<>();

         for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
         }

         return names;
      }
   }
}
