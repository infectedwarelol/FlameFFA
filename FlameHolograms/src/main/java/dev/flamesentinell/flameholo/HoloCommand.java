package dev.flamesentinell.flameholo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display.Billboard;

final class HoloCommand implements TabExecutor {
   private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
   private static final List<String> SUBS = List.of(
      "create",
      "createat",
      "addline",
      "setline",
      "insertline",
      "removeline",
      "movehere",
      "tp",
      "delete",
      "info",
      "list",
      "near",
      "scale",
      "billboard",
      "background",
      "shadow",
      "seethrough",
      "width",
      "reload",
      "help"
   );
   private final FlameHolo p;

   HoloCommand(FlameHolo var1) {
      this.p = var1;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!var1.hasPermission("flameholo.admin")) {
         this.p.send(var1, "no-permission");
         return true;
      } else if (var4.length != 0 && !var4[0].equalsIgnoreCase("help")) {
         String var5 = var4[0].toLowerCase(Locale.ROOT);
         switch (var5) {
            case "list":
               this.list(var1);
               break;
            case "reload":
               this.p.reload();
               this.p.send(var1, "reloaded", "count", String.valueOf(this.p.holos.size()));
               break;
            case "near":
               this.near(var1, var4);
               break;
            case "create":
               this.create(var1, var4);
               break;
            case "createat":
               this.createAt(var1, var4);
               break;
            default:
               this.edit(var1, var5, var4);
         }

         return true;
      } else {
         this.p.send(var1, "help");
         return true;
      }
   }

   private void create(CommandSender var1, String[] var2) {
      if (var1 instanceof Player var3) {
         if (var2.length < 3) {
            this.p.send(var1, "help");
         } else {
            Location var4 = var3.getEyeLocation();
            this.make(var1, var2[1], var4, join(var2, 2));
         }
      } else {
         this.p.send(var1, "players-only");
      }
   }

   private void createAt(CommandSender var1, String[] var2) {
      if (var2.length < 7) {
         this.p.send(var1, "help");
      } else {
         World var3 = Bukkit.getWorld(var2[2]);
         if (var3 == null) {
            this.p.send(var1, "bad-world", "world", var2[2]);
         } else {
            try {
               Location var4 = new Location(var3, Double.parseDouble(var2[3]), Double.parseDouble(var2[4]), Double.parseDouble(var2[5]));
               this.make(var1, var2[1], var4, join(var2, 6));
            } catch (NumberFormatException var5) {
               this.p.send(var1, "bad-value", "value", var2[3] + " " + var2[4] + " " + var2[5]);
            }
         }
      }
   }

   private void make(CommandSender var1, String var2, Location var3, String var4) {
      if (!NAME.matcher(var2).matches()) {
         this.p.send(var1, "bad-name");
      } else if (this.p.holos.containsKey(var2.toLowerCase(Locale.ROOT))) {
         this.p.send(var1, "exists", "name", var2);
      } else {
         Holo var5 = this.p.fresh(var2);
         var5.at(var3);
         var5.lines.addAll(split(var4));
         this.p.holos.put(var2.toLowerCase(Locale.ROOT), var5);
         this.p.save();
         this.p.spawn(var5);
         this.p.send(var1, "created", "name", var2);
      }
   }

   private void edit(CommandSender var1, String var2, String[] var3) {
      if (SUBS.contains(var2) && var3.length >= 2) {
         Holo var4 = this.p.holos.get(var3[1].toLowerCase(Locale.ROOT));
         if (var4 == null) {
            this.p.send(var1, "unknown", "name", var3[1]);
         } else {
            switch (var2) {
               case "delete":
                  this.p.despawn(var4);
                  this.p.holos.remove(var4.name.toLowerCase(Locale.ROOT));
                  this.p.save();
                  this.p.send(var1, "deleted", "name", var4.name);
                  return;
               case "info":
                  this.info(var1, var4);
                  return;
               case "tp":
                  if (var1 instanceof Player var15) {
                     World var22 = Bukkit.getWorld(var4.world);
                     if (var22 == null) {
                        this.p.send(var1, "bad-world", "world", var4.world);
                        return;
                     }

                     var15.teleport(new Location(var22, var4.x, var4.y - 1.6, var4.z, var15.getYaw(), var15.getPitch()));
                     this.p.send(var1, "teleported", "name", var4.name);
                     return;
                  }

                  this.p.send(var1, "players-only");
                  return;
               case "movehere":
                  if (var1 instanceof Player var14) {
                     Location var21 = var14.getEyeLocation();
                     var21.setYaw(var14.getYaw() + 180.0F);
                     var4.at(var21);
                     this.p.save();
                     this.p.redraw(var4);
                     this.p.send(var1, "moved", "name", var4.name);
                     return;
                  }

                  this.p.send(var1, "players-only");
                  return;
               default:
                  if (var3.length < 3) {
                     this.p.send(var1, "help");
                  } else {
                     String var5 = var3[2];
                     switch (var2) {
                        case "addline":
                           var4.lines.addAll(split(join(var3, 2)));
                           break;
                        case "setline":
                        case "insertline":
                        case "removeline":
                           int var20;
                           try {
                              var20 = Integer.parseInt(var5);
                           } catch (NumberFormatException var11) {
                              this.p.send(var1, "bad-value", "value", var5);
                              return;
                           }

                           int var9 = var2.equals("insertline") ? var4.lines.size() + 1 : var4.lines.size();
                           if (var20 < 1 || var20 > var9) {
                              this.p.send(var1, "bad-line", "line", var5, "count", String.valueOf(var4.lines.size()));
                              return;
                           }

                           if (var2.equals("removeline")) {
                              var4.lines.remove(var20 - 1);
                           } else {
                              if (var3.length < 4) {
                                 this.p.send(var1, "help");
                                 return;
                              }

                              List var10 = split(join(var3, 3));
                              if (var2.equals("setline")) {
                                 var4.lines.remove(var20 - 1);
                              }

                              var4.lines.addAll(var20 - 1, var10);
                           }
                           break;
                        case "scale":
                           try {
                              float var19 = Float.parseFloat(var5);
                              if (!(var19 <= 0.0F) && !(var19 > 20.0F)) {
                                 var4.scale = var19;
                                 break;
                              }

                              throw new NumberFormatException();
                           } catch (NumberFormatException var13) {
                              this.p.send(var1, "bad-value", "value", var5);
                              return;
                           }
                        case "billboard":
                           Billboard var18 = FlameHolo.billboard(var5);
                           if (var18 == null) {
                              this.p.send(var1, "bad-value", "value", var5);
                              return;
                           }

                           var4.billboard = var18;
                           break;
                        case "background":
                           Integer var17 = FlameHolo.background(var5);
                           if (var17 != null && var17 == -1) {
                              this.p.send(var1, "bad-value", "value", var5);
                              return;
                           }

                           var4.background = var17;
                           break;
                        case "shadow":
                        case "seethrough":
                           Boolean var16 = bool(var5);
                           if (var16 == null) {
                              this.p.send(var1, "bad-value", "value", var5);
                              return;
                           }

                           if (var2.equals("shadow")) {
                              var4.shadow = var16;
                           } else {
                              var4.seeThrough = var16;
                           }
                           break;
                        case "width":
                           try {
                              int var8 = Integer.parseInt(var5);
                              if (var8 >= 10 && var8 <= 2000) {
                                 var4.lineWidth = var8;
                                 break;
                              }

                              throw new NumberFormatException();
                           } catch (NumberFormatException var12) {
                              this.p.send(var1, "bad-value", "value", var5);
                              return;
                           }
                        default:
                           this.p.send(var1, "help");
                           return;
                     }

                     this.p.save();
                     this.p.redraw(var4);
                     this.p.send(var1, "edited", "name", var4.name);
                  }
            }
         }
      } else {
         this.p.send(var1, "help");
      }
   }

   private void info(CommandSender var1, Holo var2) {
      this.p
         .send(
            var1,
            "info",
            "name",
            var2.name,
            "world",
            var2.world,
            "x",
            fmt(var2.x),
            "y",
            fmt(var2.y),
            "z",
            fmt(var2.z),
            "yaw",
            fmt(var2.yaw),
            "scale",
            fmt(var2.scale),
            "billboard",
            var2.billboard.name().toLowerCase(Locale.ROOT),
            "background",
            FlameHolo.backgroundName(var2.background),
            "shadow",
            String.valueOf(var2.shadow),
            "seethrough",
            String.valueOf(var2.seeThrough),
            "width",
            String.valueOf(var2.lineWidth)
         );

      for (int var3 = 0; var3 < var2.lines.size(); var3++) {
         String var4 = this.p.getConfig().getString("messages.info-line", "<line>. <text>");
         var1.sendMessage(this.p.mm.deserialize(var4.replace("<line>", String.valueOf(var3 + 1)).replace("<text>", var2.lines.get(var3))));
      }
   }

   private void list(CommandSender var1) {
      this.p.send(var1, "list-header", "count", String.valueOf(this.p.holos.size()));

      for (Holo var3 : this.p.all()) {
         this.row(var1, var3);
      }
   }

   private void near(CommandSender var1, String[] var2) {
      if (var1 instanceof Player var3) {
         double var4 = 10.0;
         if (var2.length > 1) {
            try {
               var4 = Double.parseDouble(var2[1]);
            } catch (NumberFormatException var16) {
               this.p.send(var1, "bad-value", "value", var2[1]);
               return;
            }
         }

         int var6 = 0;
         Location var7 = var3.getLocation();

         for (Holo var9 : this.p.all()) {
            if (var9.world.equals(var7.getWorld().getName())) {
               double var10 = var9.x - var7.getX();
               double var12 = var9.y - var7.getY();
               double var14 = var9.z - var7.getZ();
               if (var10 * var10 + var12 * var12 + var14 * var14 <= var4 * var4) {
                  this.row(var1, var9);
                  var6++;
               }
            }
         }

         if (var6 == 0) {
            this.p.send(var1, "near-none", "radius", fmt(var4));
         }
      } else {
         this.p.send(var1, "players-only");
      }
   }

   private void row(CommandSender var1, Holo var2) {
      String var3 = var2.lines.isEmpty() ? "" : var2.lines.get(0);
      this.p
         .send(
            var1, "list-row", "name", var2.name, "world", var2.world, "x", fmt(var2.x), "y", fmt(var2.y), "z", fmt(var2.z), "first", this.p.mm.stripTags(var3)
         );
   }

   private static String fmt(double var0) {
      return String.format(Locale.ROOT, "%.1f", var0);
   }

   private static String join(String[] var0, int var1) {
      return String.join(" ", Arrays.copyOfRange(var0, var1, var0.length));
   }

   private static List<String> split(String var0) {
      return new ArrayList<>(Arrays.asList(var0.split("\\\\n", -1)));
   }

   private static Boolean bool(String var0) {
      String var1 = var0.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "true", "on", "yes" -> true;
         case "false", "off", "no" -> false;
         default -> null;
      };
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!var1.hasPermission("flameholo.admin")) {
         return List.of();
      } else if (var4.length == 1) {
         return filter(SUBS, var4[0]);
      } else {
         String var5 = var4[0].toLowerCase(Locale.ROOT);
         if (var4.length == 2 && !List.of("create", "createat", "list", "near", "reload", "help").contains(var5)) {
            ArrayList var6 = new ArrayList();

            for (Holo var12 : this.p.all()) {
               var6.add(var12.name);
            }

            return filter(var6, var4[1]);
         } else if (var4.length == 3) {
            return switch (var5) {
               case "billboard" -> filter(List.of("center", "fixed", "vertical", "horizontal"), var4[2]);
               case "background" -> filter(List.of("none", "default", "#80000000"), var4[2]);
               case "shadow", "seethrough" -> filter(List.of("on", "off"), var4[2]);
               case "createat" -> {
                  ArrayList var8 = new ArrayList();

                  for (World var10 : Bukkit.getWorlds()) {
                     var8.add(var10.getName());
                  }

                  yield filter(var8, var4[2]);
               }
               default -> List.of();
            };
         } else {
            return List.of();
         }
      }
   }

   private static List<String> filter(List<String> var0, String var1) {
      String var2 = var1.toLowerCase(Locale.ROOT);
      ArrayList var3 = new ArrayList();

      for (String var5 : var0) {
         if (var5.toLowerCase(Locale.ROOT).startsWith(var2)) {
            var3.add(var5);
         }
      }

      return var3;
   }
}
