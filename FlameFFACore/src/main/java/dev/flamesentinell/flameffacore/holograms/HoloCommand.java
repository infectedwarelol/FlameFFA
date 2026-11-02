package dev.flamesentinell.flameffacore.holograms;

import dev.flamesentinell.flameffacore.util.Tasks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Player;

final class HoloCommand {
   private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
   private static final double MAX_NEAR_RADIUS = 512.0;
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
   private static final List<String> NO_NAME = List.of("create", "createat", "list", "near", "reload", "help");
   private final HologramsModule module;

   HoloCommand(HologramsModule module) {
      this.module = module;
   }

   boolean run(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("flameholo.admin")) {
         this.module.send(sender, "no-permission");
      } else if (args.length != 0 && !args[0].equalsIgnoreCase("help")) {
         String sub = args[0].toLowerCase(Locale.ROOT);
         switch (sub) {
            case "list":
               this.list(sender);
               break;
            case "reload":
               this.module.reload();
               this.module.send(sender, "reloaded", "count", String.valueOf(this.module.count()));
               break;
            case "near":
               this.near(sender, args);
               break;
            case "create":
               this.create(sender, args);
               break;
            case "createat":
               this.createAt(sender, args);
               break;
            default:
               this.edit(sender, sub, args);
         }
      } else {
         this.module.send(sender, "help");
      }

      return true;
   }

   private void create(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         this.module.send(sender, "players-only");
      } else if (args.length < 3) {
         this.module.send(sender, "help");
      } else {
         this.make(sender, args[1], player.getEyeLocation(), join(args, 2));
      }
   }

   private void createAt(CommandSender sender, String[] args) {
      if (args.length < 7) {
         this.module.send(sender, "help");
      } else {
         World world = Bukkit.getWorld(args[2]);
         if (world == null) {
            this.module.send(sender, "bad-world", "world", args[2]);
         } else {
            try {
               double x = Double.parseDouble(args[3]);
               double y = Double.parseDouble(args[4]);
               double z = Double.parseDouble(args[5]);
               if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                  throw new NumberFormatException();
               }

               this.make(sender, args[1], new Location(world, x, y, z), join(args, 6));
            } catch (NumberFormatException malformed) {
               this.module.send(sender, "bad-value", "value", args[3] + " " + args[4] + " " + args[5]);
            }
         }
      }
   }

   private void make(CommandSender sender, String name, Location at, String text) {
      if (!NAME.matcher(name).matches()) {
         this.module.send(sender, "bad-name");
      } else {
         Holo holo = this.module.fresh(name);
         holo.at(at);
         holo.lines.addAll(split(text));
         if (!this.module.add(holo)) {
            this.module.send(sender, "exists", "name", name);
         } else {
            this.module.save();
            this.module.spawn(holo);
            this.module.send(sender, "created", "name", name);
         }
      }
   }

   private void edit(CommandSender sender, String sub, String[] args) {
      if (!SUBS.contains(sub) || args.length < 2) {
         this.module.send(sender, "help");
      } else {
         Holo holo = this.module.get(args[1]);
         if (holo == null) {
            this.module.send(sender, "unknown", "name", args[1]);
         } else {
            switch (sub) {
               case "delete":
                  this.module.remove(holo);
                  this.module.despawn(holo);
                  this.module.save();
                  this.module.send(sender, "deleted", "name", holo.name);
                  return;
               case "info":
                  this.info(sender, holo);
                  return;
               case "tp":
                  if (sender instanceof Player player) {
                     World world = Bukkit.getWorld(holo.world);
                     if (world == null) {
                        this.module.send(sender, "bad-world", "world", holo.world);
                     } else {
                        Tasks.move(player, new Location(world, holo.x, holo.y - 1.6, holo.z, player.getYaw(), player.getPitch()));
                        this.module.send(sender, "teleported", "name", holo.name);
                     }
                  } else {
                     this.module.send(sender, "players-only");
                  }

                  return;
               case "movehere":
                  if (sender instanceof Player player) {
                     Location eyes = player.getEyeLocation();
                     eyes.setYaw(player.getYaw() + 180.0F);
                     this.module.despawn(holo);
                     holo.at(eyes);
                     this.module.save();
                     this.module.redraw(holo);
                     this.module.send(sender, "moved", "name", holo.name);
                  } else {
                     this.module.send(sender, "players-only");
                  }

                  return;
               default:
                  if (args.length < 3) {
                     this.module.send(sender, "help");
                  } else if (this.apply(sender, sub, holo, args)) {
                     this.module.save();
                     this.module.redraw(holo);
                     this.module.send(sender, "edited", "name", holo.name);
                  }
            }
         }
      }
   }

   private boolean apply(CommandSender sender, String sub, Holo holo, String[] args) {
      String value = args[2];
      switch (sub) {
         case "addline":
            holo.lines.addAll(split(join(args, 2)));
            return true;
         case "setline":
         case "insertline":
         case "removeline":
            int line;
            try {
               line = Integer.parseInt(value);
            } catch (NumberFormatException malformed) {
               this.module.send(sender, "bad-value", "value", value);
               return false;
            }

            int limit = sub.equals("insertline") ? holo.lines.size() + 1 : holo.lines.size();
            if (line < 1 || line > limit) {
               this.module.send(sender, "bad-line", "line", value, "count", String.valueOf(holo.lines.size()));
               return false;
            } else if (sub.equals("removeline")) {
               holo.lines.remove(line - 1);
               return true;
            } else if (args.length < 4) {
               this.module.send(sender, "help");
               return false;
            } else {
               List<String> replacement = split(join(args, 3));
               if (sub.equals("setline")) {
                  holo.lines.remove(line - 1);
               }

               holo.lines.addAll(line - 1, replacement);
               return true;
            }
         case "scale":
            try {
               float scale = Float.parseFloat(value);
               if (scale > 0.0F && scale <= 20.0F) {
                  holo.scale = scale;
                  return true;
               }
            } catch (NumberFormatException malformed) {
               this.module.send(sender, "bad-value", "value", value);
               return false;
            }

            this.module.send(sender, "bad-value", "value", value);
            return false;
         case "billboard":
            Billboard billboard = HologramsModule.billboard(value);
            if (billboard == null) {
               this.module.send(sender, "bad-value", "value", value);
               return false;
            }

            holo.billboard = billboard;
            return true;
         case "background":
            Integer background = HologramsModule.background(value);
            if (background != null && background == -1) {
               this.module.send(sender, "bad-value", "value", value);
               return false;
            }

            holo.background = background;
            return true;
         case "shadow":
         case "seethrough":
            Boolean flag = bool(value);
            if (flag == null) {
               this.module.send(sender, "bad-value", "value", value);
               return false;
            }

            if (sub.equals("shadow")) {
               holo.shadow = flag;
            } else {
               holo.seeThrough = flag;
            }

            return true;
         case "width":
            try {
               int width = Integer.parseInt(value);
               if (width >= 10 && width <= 2000) {
                  holo.lineWidth = width;
                  return true;
               }
            } catch (NumberFormatException malformed) {
               this.module.send(sender, "bad-value", "value", value);
               return false;
            }

            this.module.send(sender, "bad-value", "value", value);
            return false;
         default:
            this.module.send(sender, "help");
            return false;
      }
   }

   private void info(CommandSender sender, Holo holo) {
      this.module
         .send(
            sender,
            "info",
            "name",
            holo.name,
            "world",
            holo.world,
            "x",
            fmt(holo.x),
            "y",
            fmt(holo.y),
            "z",
            fmt(holo.z),
            "yaw",
            fmt(holo.yaw),
            "scale",
            fmt(holo.scale),
            "billboard",
            holo.billboard.name().toLowerCase(Locale.ROOT),
            "background",
            HologramsModule.backgroundName(holo.background),
            "shadow",
            String.valueOf(holo.shadow),
            "seethrough",
            String.valueOf(holo.seeThrough),
            "width",
            String.valueOf(holo.lineWidth)
         );
      String template = this.module.cfg().getString("messages.info-line", "<line>. <text>");
      List<String> lines = new ArrayList<>(holo.lines);

      for (int index = 0; index < lines.size(); index++) {
         sender.sendMessage(HologramsModule.TEXT.deserialize(template.replace("<line>", String.valueOf(index + 1)).replace("<text>", lines.get(index))));
      }
   }

   private void list(CommandSender sender) {
      this.module.send(sender, "list-header", "count", String.valueOf(this.module.count()));

      for (Holo holo : this.module.all()) {
         this.row(sender, holo);
      }
   }

   private void near(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         this.module.send(sender, "players-only");
      } else {
         double radius = 10.0;
         if (args.length > 1) {
            try {
               radius = Double.parseDouble(args[1]);
            } catch (NumberFormatException malformed) {
               this.module.send(sender, "bad-value", "value", args[1]);
               return;
            }

            if (!Double.isFinite(radius) || radius <= 0.0 || radius > MAX_NEAR_RADIUS) {
               this.module.send(sender, "bad-value", "value", args[1]);
               return;
            }
         }

         int found = 0;
         Location at = player.getLocation();
         String world = at.getWorld().getName();
         double squared = radius * radius;

         for (Holo holo : this.module.all()) {
            if (holo.world.equals(world)) {
               double dx = holo.x - at.getX();
               double dy = holo.y - at.getY();
               double dz = holo.z - at.getZ();
               if (dx * dx + dy * dy + dz * dz <= squared) {
                  this.row(sender, holo);
                  found++;
               }
            }
         }

         if (found == 0) {
            this.module.send(sender, "near-none", "radius", fmt(radius));
         }
      }
   }

   private void row(CommandSender sender, Holo holo) {
      String first = holo.lines.isEmpty() ? "" : holo.lines.get(0);
      this.module
         .send(
            sender,
            "list-row",
            "name",
            holo.name,
            "world",
            holo.world,
            "x",
            fmt(holo.x),
            "y",
            fmt(holo.y),
            "z",
            fmt(holo.z),
            "first",
            HologramsModule.TEXT.stripTags(first)
         );
   }

   private static String fmt(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private static String join(String[] args, int from) {
      return String.join(" ", Arrays.copyOfRange(args, from, args.length));
   }

   private static List<String> split(String text) {
      return new ArrayList<>(Arrays.asList(text.split("\\\\n", -1)));
   }

   private static Boolean bool(String raw) {
      return switch (raw.toLowerCase(Locale.ROOT)) {
         case "true", "on", "yes" -> true;
         case "false", "off", "no" -> false;
         default -> null;
      };
   }

   List<String> complete(CommandSender sender, String[] args) {
      if (!sender.hasPermission("flameholo.admin")) {
         return List.of();
      } else if (args.length == 1) {
         return filter(SUBS, args[0]);
      } else {
         String sub = args[0].toLowerCase(Locale.ROOT);
         if (args.length == 2 && !NO_NAME.contains(sub)) {
            List<String> names = new ArrayList<>();

            for (Holo holo : this.module.all()) {
               names.add(holo.name);
            }

            return filter(names, args[1]);
         } else if (args.length == 3) {
            return switch (sub) {
               case "billboard" -> filter(List.of("center", "fixed", "vertical", "horizontal"), args[2]);
               case "background" -> filter(List.of("none", "default", "#80000000"), args[2]);
               case "shadow", "seethrough" -> filter(List.of("on", "off"), args[2]);
               case "createat" -> {
                  List<String> worlds = new ArrayList<>();

                  for (World world : Bukkit.getWorlds()) {
                     worlds.add(world.getName());
                  }

                  yield filter(worlds, args[2]);
               }
               default -> List.of();
            };
         } else {
            return List.of();
         }
      }
   }

   private static List<String> filter(List<String> options, String typed) {
      String lower = typed.toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();

      for (String option : options) {
         if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
            out.add(option);
         }
      }

      return out;
   }
}
