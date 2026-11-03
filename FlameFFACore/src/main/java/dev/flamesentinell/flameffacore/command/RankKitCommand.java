package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.kit.RankKit;
import dev.flamesentinell.flameffacore.rank.Rank;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class RankKitCommand implements BasicCommand {
   private static final String USAGE = "/rankkit set|clear|list|info <rank>";
   private final FlameFFACore plugin;

   public RankKitCommand(FlameFFACore plugin) {
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
         sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/rankkit set|clear|list|info <rank>"));
      } else {
         String var4 = args[0].toLowerCase(Locale.ROOT);
         switch (var4) {
            case "set":
               this.set(sender, args);
               break;
            case "clear":
               this.clear(sender, args);
               break;
            case "list":
               this.list(sender);
               break;
            case "info":
               this.info(sender, args);
               break;
            default:
               sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/rankkit set|clear|list|info <rank>"));
         }
      }
   }

   private void set(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         Rank rank = this.rankArg(sender, args);
         if (rank != null) {
            this.plugin.kits().capture(player, rank);
            RankKit kit = this.plugin.kits().get(rank.id());
            int slots = kit == null ? 0 : kit.filledSlots();
            sender.sendMessage(this.plugin.configs().msg("rankkit.set", "rank", rank.id(), "slots", String.valueOf(slots)));
            this.plugin.async(() -> this.plugin.audit().staff(player.getUniqueId(), player.getName(), "rankkit_set", null, rank.id(), slots + " slots"));
         }
      } else {
         sender.sendMessage(this.plugin.configs().msg("general.players-only"));
      }
   }

   private void clear(CommandSender sender, String[] args) {
      Rank rank = this.rankArg(sender, args);
      if (rank != null) {
         boolean removed = this.plugin.kits().clear(rank.id());
         sender.sendMessage(this.plugin.configs().msg(removed ? "rankkit.cleared" : "rankkit.not-set", "rank", rank.id()));
         String actorName = sender.getName();
         this.plugin
            .async(() -> this.plugin.audit().staff(sender instanceof Player p ? p.getUniqueId() : null, actorName, "rankkit_clear", null, rank.id(), "cleared"));
      }
   }

   private void list(CommandSender sender) {
      sender.sendMessage(this.plugin.configs().msg("rankkit.list-header"));

      for (Rank rank : this.plugin.ranks().ordered()) {
         RankKit kit = this.plugin.kits().get(rank.id());
         sender.sendMessage(
            this.plugin
               .configs()
               .msg(
                  kit == null ? "rankkit.list-empty" : "rankkit.list-line",
                  "rank",
                  rank.id(),
                  "slots",
                  String.valueOf(kit == null ? 0 : kit.filledSlots()),
                  "by",
                  kit == null ? "-" : kit.setBy()
               )
         );
      }
   }

   private void info(CommandSender sender, String[] args) {
      Rank rank = this.rankArg(sender, args);
      if (rank != null) {
         RankKit kit = this.plugin.kits().get(rank.id());
         if (kit == null) {
            sender.sendMessage(this.plugin.configs().msg("rankkit.not-set", "rank", rank.id()));
         } else {
            sender.sendMessage(this.plugin.configs().msg("rankkit.list-line", "rank", rank.id(), "slots", String.valueOf(kit.filledSlots()), "by", kit.setBy()));

            for (int i = 0; i < kit.contents().length; i++) {
               ItemStack stack = kit.contents()[i];
               if (stack != null && !stack.getType().isAir()) {
                  String where = i < 36 ? "slot " + i : (i < 40 ? "armour" : "offhand");
                  sender.sendMessage(Component.text("  " + where + "  " + stack.getType().getKey().getKey() + " x" + stack.getAmount()));
               }
            }
         }
      }
   }

   private Rank rankArg(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/rankkit set|clear|list|info <rank>"));
         return null;
      } else {
         Rank rank = this.plugin.ranks().rank(args[1].toLowerCase(Locale.ROOT));
         if (rank == null) {
            sender.sendMessage(this.plugin.configs().msg("rankkit.unknown-rank", "rank", args[1]));
         }

         return rank;
      }
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (args.length <= 1) {
         return List.of("set", "clear", "list", "info");
      } else if (args.length == 2) {
         List<String> out = new ArrayList<>(this.plugin.ranks().all().keySet());
         out.removeIf(id -> !id.startsWith(args[1].toLowerCase(Locale.ROOT)));
         return out;
      } else {
         return List.of();
      }
   }
}
