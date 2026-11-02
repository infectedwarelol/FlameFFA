package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.rank.Rank;
import dev.flamesentinell.flameffacore.staff.StaffRole;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class BoosterCommand implements BasicCommand {
   private static final String USAGE = "/booster add|remove <player>";
   private final FlameFFACore plugin;

   public BoosterCommand(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      CommandSender sender = source.getSender();
      if (this.plugin.staff().require(sender, StaffRole.MODERATOR)) {
         String rankId = this.plugin.configs().main().getString("booster.rank", "booster");
         Rank rank = this.plugin.ranks().rank(rankId);
         if (rank == null) {
            sender.sendMessage(this.plugin.configs().msg("booster.no-rank", "rank", rankId));
         } else if (args.length < 2) {
            sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/booster add|remove <player>"));
         } else {
            String action = args[0].toLowerCase(Locale.ROOT);
            String name = args[1];
            UUID target = this.plugin.tebex().resolve(name);
            if (target == null) {
               sender.sendMessage(this.plugin.configs().msg("general.unknown-player", "name", name));
            } else {
               Player online = Bukkit.getPlayer(target);
               String shown = online != null ? online.getName() : name;
               UUID actorId = sender instanceof Player p ? p.getUniqueId() : null;
               switch (action) {
                  case "add":
                  case "give":
                     this.plugin.async(() -> this.plugin.audit().staff(actorId, sender.getName(), "booster_add", target, shown, rankId));
                     this.plugin.ranks().grant(target, rankId, 0).thenRun(() -> this.plugin.sync(() -> {
                        this.plugin.tebex().notifyOrQueue(target, this.plugin.configs().messages().getString("booster.received", ""), rankId, 0);
                        sender.sendMessage(this.plugin.configs().msg("booster.added", "player", shown));
                     }));
                     break;
                  case "remove":
                  case "take":
                     this.plugin.async(() -> this.plugin.audit().staff(actorId, sender.getName(), "booster_remove", target, shown, rankId));
                     this.plugin.ranks().revoke(target, rankId).thenRun(() -> this.plugin.sync(() -> {
                        if (online != null) {
                           online.sendMessage(this.plugin.configs().msg("booster.lost"));
                        }

                        sender.sendMessage(this.plugin.configs().msg("booster.removed", "player", shown));
                     }));
                     break;
                  default:
                     sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/booster add|remove <player>"));
               }
            }
         }
      }
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (args.length <= 1) {
         return List.of("add", "remove");
      } else {
         return args.length == 2 ? Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).toList() : List.of();
      }
   }
}
