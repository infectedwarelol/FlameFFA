package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.storage.VillagerStore;
import dev.flamesentinell.flameffacore.villager.VillagerService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class VillagerCommand implements BasicCommand {
   private static final String USAGE = "/villager preset|edit|spawn|remove|delete|list|skin <mojang name>|skin clear";
   private final FlameFFACore plugin;

   public VillagerCommand(FlameFFACore var1) {
      this.plugin = var1;
   }

   public boolean canUse(@NotNull CommandSender var1) {
      return this.plugin.staff().isOperator(var1);
   }

   public void execute(@NotNull CommandSourceStack var1, @NotNull String[] var2) {
      CommandSender var3 = var1.getSender();
      if (!this.plugin.staff().isOperator(var3)) {
         var3.sendMessage(this.plugin.configs().msg("general.operator-only"));
      } else if (var2.length == 0) {
         var3.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/villager preset|edit|spawn|remove|delete|list|skin <mojang name>|skin clear"));
      } else {
         String var4 = var2[0].toLowerCase(Locale.ROOT);
         switch (var4) {
            case "preset":
               this.preset(var3, var2);
               break;
            case "delete":
               this.delete(var3, var2);
               break;
            case "list":
               this.list(var3);
               break;
            case "amount":
               if (var2.length < 4) {
                  var3.sendMessage("/villager amount <preset> <editor slot> <amount>");
               } else {
                  try {
                     var3.sendMessage(this.plugin.villagers().setAmount(var2[1], Integer.parseInt(var2[2]), Integer.parseInt(var2[3]), var3.getName()));
                  } catch (NumberFormatException var10) {
                     var3.sendMessage("bad number");
                  }
               }
               break;
            default:
               if (!(var3 instanceof Player var5)) {
                  var3.sendMessage(this.plugin.configs().msg("general.players-only"));
                  return;
               }

               switch (var4) {
                  case "edit":
                     this.edit(var5, var2);
                     break;
                  case "spawn":
                     this.spawn(var5, var2);
                     break;
                  case "remove":
                     this.remove(var5);
                     break;
                  case "skin":
                     this.skin(var5, var2);
                     break;
                  default:
                     var5.sendMessage(
                        this.plugin.configs().msg("general.usage", "usage", "/villager preset|edit|spawn|remove|delete|list|skin <mojang name>|skin clear")
                     );
               }
         }
      }
   }

   private String nameArg(CommandSender var1, String[] var2) {
      if (var2.length < 2) {
         var1.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/villager preset|edit|spawn|remove|delete|list|skin <mojang name>|skin clear"));
         return null;
      } else {
         return var2[1].toLowerCase(Locale.ROOT);
      }
   }

   private void preset(CommandSender var1, String[] var2) {
      String var3 = this.nameArg(var1, var2);
      if (var3 != null) {
         if (!VillagerService.PRESET_NAME.matcher(var3).matches()) {
            var1.sendMessage(this.plugin.configs().msg("villager.bad-name"));
         } else if (this.plugin.villagers().exists(var3)) {
            var1.sendMessage(this.plugin.configs().msg("villager.exists", "preset", var3));
         } else if (!this.plugin.villagers().create(var3, var1.getName())) {
            var1.sendMessage(this.plugin.configs().msg("villager.save-failed", "preset", var3));
         } else {
            var1.sendMessage(this.plugin.configs().msg("villager.created", "preset", var3));
            this.audit(var1, "villager_preset_create", var3, "");
         }
      }
   }

   private void edit(Player var1, String[] var2) {
      String var3 = this.nameArg(var1, var2);
      if (var3 != null) {
         if (!this.plugin.villagers().exists(var3)) {
            var1.sendMessage(this.plugin.configs().msg("villager.unknown", "preset", var3));
         } else {
            this.plugin.villagers().openEditor(var1, var3);
            var1.sendMessage(this.plugin.configs().msg("villager.editor-hint", "preset", var3));
         }
      }
   }

   private void spawn(Player var1, String[] var2) {
      String var3 = this.nameArg(var1, var2);
      if (var3 != null) {
         if (!this.plugin.villagers().exists(var3)) {
            var1.sendMessage(this.plugin.configs().msg("villager.unknown", "preset", var3));
         } else {
            Location var4 = var1.getLocation().clone();
            if (var2.length >= 5) {
               try {
                  var4 = new Location(
                     var1.getWorld(),
                     coord(var2[2], var1.getLocation().getX()),
                     coord(var2[3], var1.getLocation().getY()),
                     coord(var2[4], var1.getLocation().getZ()),
                     var1.getLocation().getYaw(),
                     0.0F
                  );
               } catch (NumberFormatException var6) {
                  var1.sendMessage(this.plugin.configs().msg("villager.bad-coords"));
                  return;
               }

               if (var4.getX() == Math.floor(var4.getX())) {
                  var4.add(0.5, 0.0, 0.0);
               }

               if (var4.getZ() == Math.floor(var4.getZ())) {
                  var4.add(0.0, 0.0, 0.5);
               }
            } else if (var2.length != 2) {
               var1.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/villager spawn <preset> [x y z]"));
               return;
            }

            var4.setYaw(var1.getLocation().getYaw() + 180.0F);
            if (!this.plugin.villagers().spawn(var3, var4, var1.getName())) {
               var1.sendMessage(this.plugin.configs().msg("villager.spawn-failed", "preset", var3));
            } else {
               var1.sendMessage(
                  this.plugin
                     .configs()
                     .msg(
                        "villager.spawned",
                        "preset",
                        var3,
                        "x",
                        String.valueOf(var4.getBlockX()),
                        "y",
                        String.valueOf(var4.getBlockY()),
                        "z",
                        String.valueOf(var4.getBlockZ())
                     )
               );
               this.audit(var1, "villager_spawn", var3, var4.getBlockX() + " " + var4.getBlockY() + " " + var4.getBlockZ());
            }
         }
      }
   }

   private static double coord(String var0, double var1) {
      double value = var0.startsWith("~") ? (var0.length() == 1 ? var1 : var1 + Double.parseDouble(var0.substring(1))) : Double.parseDouble(var0);
      if (!Double.isFinite(value) || Math.abs(value) > 3.0E7) {
         throw new NumberFormatException("coordinate out of range");
      } else {
         return value;
      }
   }

   private void remove(Player var1) {
      String var2 = this.plugin.villagers().removeNearest(var1, 5.0);
      if (var2 == null) {
         var1.sendMessage(this.plugin.configs().msg("villager.none-near"));
      } else {
         var1.sendMessage(this.plugin.configs().msg("villager.removed", "preset", var2));
         this.audit(var1, "villager_remove", var2, "");
      }
   }

   private void skin(Player var1, String[] var2) {
      if (var2.length < 2) {
         var1.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/villager preset|edit|spawn|remove|delete|list|skin <mojang name>|skin clear"));
      } else if ("clear".equalsIgnoreCase(var2[1])) {
         String var3 = this.plugin.villagers().unskinNearest(var1, 5.0);
         if (var3 == null) {
            var1.sendMessage(this.plugin.configs().msg("villager.none-near"));
         } else {
            var1.sendMessage(this.plugin.configs().msg("villager.skin-cleared", "preset", var3));
            this.audit(var1, "villager_skin", var3, "cleared");
         }
      } else {
         String var4 = var2[1];
         if (!var4.matches("[A-Za-z0-9_]{1,16}")) {
            var1.sendMessage(this.plugin.configs().msg("villager.skin-unknown", "name", var4));
         } else {
            this.plugin.villagers().skinNearest(var1, 5.0, var4);
            this.audit(var1, "villager_skin", var4, "");
         }
      }
   }

   private void delete(CommandSender var1, String[] var2) {
      String var3 = this.nameArg(var1, var2);
      if (var3 != null) {
         int var4 = this.plugin.villagers().delete(var3);
         if (var4 < 0) {
            var1.sendMessage(this.plugin.configs().msg("villager.unknown", "preset", var3));
         } else {
            var1.sendMessage(this.plugin.configs().msg("villager.deleted", "preset", var3, "count", String.valueOf(var4)));
            this.audit(var1, "villager_preset_delete", var3, var4 + " villagers removed");
         }
      }
   }

   private void list(CommandSender var1) {
      Set<String> var2 = this.plugin.villagers().names();
      if (var2.isEmpty()) {
         var1.sendMessage(this.plugin.configs().msg("villager.list-empty"));
      } else {
         var1.sendMessage(this.plugin.configs().msg("villager.list-header", "count", String.valueOf(var2.size())));

         for (String var4 : var2) {
            int var5 = 0;

            for (VillagerStore.Row var7 : this.plugin.villagers().placed()) {
               if (var7.preset().equals(var4)) {
                  var5++;
               }
            }

            var1.sendMessage(
               this.plugin
                  .configs()
                  .msg(
                     "villager.list-line",
                     "preset",
                     var4,
                     "trades",
                     String.valueOf(this.plugin.villagers().tradeCount(var4)),
                     "placed",
                     String.valueOf(var5),
                     "by",
                     this.plugin.villagers().setBy(var4)
                  )
            );
         }
      }
   }

   private void audit(CommandSender var1, String var2, String var3, String var4) {
      UUID var5 = var1 instanceof Player var6 ? var6.getUniqueId() : null;
      String var7 = var1.getName();
      this.plugin.async(() -> this.plugin.audit().staff(var5, var7, var2, null, var3, var4));
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack var1, @NotNull String[] var2) {
      if (var2.length <= 1) {
         return List.of("preset", "edit", "spawn", "remove", "delete", "list", "skin");
      } else if (var2.length == 2) {
         String var3 = var2[1].toLowerCase(Locale.ROOT);
         ArrayList<String> var4 = new ArrayList<>();
         if (!"preset".equalsIgnoreCase(var2[0])) {
            var4.addAll(this.plugin.villagers().names());
         }

         var4.removeIf(var1x -> !var1x.startsWith(var3));
         return var4;
      } else {
         return "spawn".equalsIgnoreCase(var2[0]) && var2.length <= 5 ? List.of("~") : List.of();
      }
   }
}
