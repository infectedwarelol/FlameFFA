package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.crate.Crate;
import dev.flamesentinell.flameffacore.storage.KeyStore;
import dev.flamesentinell.flameffacore.util.Bots;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class KeyAll {
   private static KeyAll instance;
   private final FlameFFACore plugin;

   private KeyAll(FlameFFACore var1) {
      this.plugin = var1;
      var1.getLifecycleManager()
         .registerEventHandler(
            LifecycleEvents.COMMANDS,
            var1x -> ((Commands)var1x.registrar()).register("keyall", "give every online player crate keys once per ip", new KeyAll.Cmd())
         );
   }

   public static KeyAll start(FlameFFACore var0) {
      if (instance == null || instance.plugin != var0) {
         instance = new KeyAll(var0);
      }

      return instance;
   }

   private static void say(CommandSender var0, String var1) {
      var0.sendMessage(MiniMessage.miniMessage().deserialize(var1));
   }

   private static String ip(Player var0) {
      InetSocketAddress var1 = var0.getAddress();
      return var1 != null && var1.getAddress() != null ? var1.getAddress().getHostAddress() : var0.getUniqueId().toString();
   }

   private final class Cmd implements BasicCommand {
      public void execute(@NotNull CommandSourceStack var1, @NotNull String[] var2) {
         CommandSender var3 = var1.getSender();
         if (!KeyAll.this.plugin.staff().isTrusted(var3) && !(var3 instanceof RemoteConsoleCommandSender)) {
            var3.sendMessage(KeyAll.this.plugin.configs().msg("general.owner-only"));
         } else if (var2.length < 1) {
            KeyAll.say(var3, "<red>/keyall <crate> [amount]");
         } else {
            String var4 = var2[0].toLowerCase(Locale.ROOT);
            Crate var5 = KeyAll.this.plugin.crateRegistry().get(var4);
            if (var5 == null) {
               var3.sendMessage(KeyAll.this.plugin.configs().msg("crate.unknown", "crate", var4));
            } else {
               int var6 = 1;
               if (var2.length > 1) {
                  try {
                     var6 = Integer.parseInt(var2[1]);
                  } catch (NumberFormatException var14) {
                     var6 = -1;
                  }
               }

               if (var6 >= 1 && var6 <= 64) {
                  HashSet var7 = new HashSet();
                  ArrayList<Player> var8 = new ArrayList<>();
                  int var9 = 0;

                  for (Player var11 : Bukkit.getOnlinePlayers()) {
                     if (!Bots.is(var11)) {
                        if (!var7.add(KeyAll.ip(var11))) {
                           var9++;
                        } else {
                           var8.add(var11);
                        }
                     }
                  }

                  int var15 = var6;
                  int var16 = var9;
                  String var12 = var3.getName();
                  String var13 = var5.display();
                  KeyAll.this.plugin
                     .async(
                        () -> {
                           ArrayList var8x = new ArrayList();

                           for (Player var10 : var8) {
                              if (KeyAll.this.plugin.keys().grant(var10.getUniqueId(), var4, var15, "keyall", var12, null) == KeyStore.Result.OK) {
                                 var8x.add(var10);
                              }
                           }

                           KeyAll.this.plugin
                              .sync(
                                 () -> {
                                    Bukkit.broadcast(
                                       MiniMessage.miniMessage()
                                          .deserialize("<gold><bold>KEY ALL</bold></gold> <gray>Everyone online got <white>" + var15 + "x</white> ")
                                          .append(MiniMessage.miniMessage().deserialize(var13))
                                          .append(MiniMessage.miniMessage().deserialize(" <gray>key" + (var15 == 1 ? "" : "s") + "."))
                                    );
                                    KeyAll.say(
                                       var3, "<green>Gave " + var15 + "x " + var4 + " to " + var8x.size() + " players  " + var16 + " skipped for a shared ip."
                                    );
                                    KeyAll.this.plugin
                                       .getLogger()
                                       .info(
                                          "keyall "
                                             + var15
                                             + "x "
                                             + var4
                                             + " by "
                                             + var12
                                             + " to "
                                             + var8x.size()
                                             + " players  "
                                             + var16
                                             + " shared ip skipped"
                                       );
                                 }
                              );
                        }
                     );
               } else {
                  KeyAll.say(var3, "<red>Amount must be 1 to 64.");
               }
            }
         }
      }

      @NotNull
      public Collection<String> suggest(@NotNull CommandSourceStack var1, @NotNull String[] var2) {
         if (!KeyAll.this.plugin.staff().isTrusted(var1.getSender())) {
            return List.of();
         } else if (var2.length <= 1) {
            String var3 = var2.length == 0 ? "" : var2[0].toLowerCase(Locale.ROOT);
            return KeyAll.this.plugin.crateRegistry().all().keySet().stream().filter(var1x -> var1x.startsWith(var3)).toList();
         } else {
            return List.of("1", "2", "3");
         }
      }
   }
}
