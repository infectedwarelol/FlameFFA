package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.kit.KitLayouts;
import dev.flamesentinell.flameffacore.kit.RankKit;
import dev.flamesentinell.flameffacore.rank.Rank;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class KitCommand implements BasicCommand {
   private final FlameFFACore plugin;

   public KitCommand(FlameFFACore var1) {
      this.plugin = var1;
   }

   public void execute(@NotNull CommandSourceStack var1, @NotNull String[] var2) {
      if (var1.getSender() instanceof Player var3) {
         if (var2.length == 0) {
            this.plugin.kitMenu().open(var3);
         } else if (var2[0].equalsIgnoreCase("edit")) {
            if (var2.length < 2) {
               var3.sendMessage(MiniMessage.miniMessage().deserialize("<red>Use /kit edit <kit> [reset]"));
               return;
            }

            Rank var6 = this.plugin.ranks().rank(var2[1].toLowerCase(Locale.ROOT));
            RankKit var5 = var6 == null ? null : this.plugin.kits().get(var6.id());
            if (var6 != null && var5 != null) {
               if (!this.plugin.kits().canUse(var3, var6)) {
                  var3.sendMessage(this.plugin.configs().msg("kit.locked", "kit", var6.id(), "rank", var6.id()));
               } else if (var2.length >= 3 && var2[2].equalsIgnoreCase("reset")) {
                  KitLayouts.of(this.plugin).reset(var3, var6.id());
               } else {
                  KitLayouts.of(this.plugin).open(var3, var5);
               }
            } else {
               var3.sendMessage(this.plugin.configs().msg("kit.unknown", "kit", var2[1]));
            }
         } else {
            Rank var7 = this.plugin.ranks().rank(var2[0].toLowerCase(Locale.ROOT));
            if (var7 == null) {
               var3.sendMessage(this.plugin.configs().msg("kit.unknown", "kit", var2[0]));
            } else {
               this.plugin.kits().give(var3, var7);
            }
         }
      } else {
         var1.getSender().sendMessage(this.plugin.configs().msg("general.players-only"));
      }
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack var1, @NotNull String[] var2) {
      if (var2.length <= 1 && var1.getSender() instanceof Player var3) {
         String var9 = var2.length == 0 ? "" : var2[0].toLowerCase(Locale.ROOT);
         ArrayList var6 = new ArrayList<>(
            this.plugin.ranks().unlockedRanks(var3).stream().filter(var1x -> var1x.startsWith(var9)).filter(this.plugin.kits()::isSet).toList()
         );
         if ("edit".startsWith(var9)) {
            var6.add("edit");
         }

         return var6;
      } else if (var2.length == 2 && var2[0].equalsIgnoreCase("edit") && var1.getSender() instanceof Player var4) {
         String var8 = var2[1].toLowerCase(Locale.ROOT);
         return this.plugin.ranks().unlockedRanks(var4).stream().filter(var1x -> var1x.startsWith(var8)).filter(this.plugin.kits()::isSet).toList();
      } else {
         return var2.length == 3 && var2[0].equalsIgnoreCase("edit") ? List.of("reset") : List.of();
      }
   }
}
