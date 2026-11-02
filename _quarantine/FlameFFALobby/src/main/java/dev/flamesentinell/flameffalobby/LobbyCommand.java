package dev.flamesentinell.flameffalobby;

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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class LobbyCommand implements BasicCommand {
   private static final String USAGE = "/lobby npc set|remove|refreshskin | setspawn | send <server> | proxytest | reload";
   private final FlameFFALobby plugin;

   public LobbyCommand(FlameFFALobby plugin) {
      this.plugin = plugin;
   }

   public boolean canUse(@NotNull CommandSender sender) {
      return this.plugin.isOwner(sender) || sender.hasPermission("flameffalobby.admin");
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      CommandSender sender = source.getSender();
      if (args.length == 0) {
         sender.sendMessage(Component.text("/lobby npc set|remove|refreshskin | setspawn | send <server> | proxytest | reload"));
      } else {
         String var4 = args[0].toLowerCase(Locale.ROOT);
         switch (var4) {
            case "npc":
               this.npc(sender, args);
               break;
            case "setspawn":
               this.setspawn(sender);
               break;
            case "send":
               this.send(sender, args);
               break;
            case "proxytest":
               this.proxyTest(sender);
               break;
            case "reload":
               this.plugin.reloadAll();
               sender.sendMessage(this.plugin.text("<gray>Reloaded, npc respawned."));
               break;
            default:
               sender.sendMessage(Component.text("/lobby npc set|remove|refreshskin | setspawn | send <server> | proxytest | reload"));
         }
      }
   }

   private void npc(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(Component.text("/lobby npc set|remove"));
      } else if (args[1].equalsIgnoreCase("refreshskin")) {
         sender.sendMessage(this.plugin.text("<gray>Asking mojang for the current skin, one moment."));
         this.plugin.npc().resolveSkin(true, () -> {
            this.plugin.npc().spawn();
            sender.sendMessage(this.plugin.text("<gray>Skin snapshot taken and frozen again."));
         });
      } else if (args[1].equalsIgnoreCase("remove")) {
         this.plugin.npc().unplace();
         sender.sendMessage(this.plugin.text("<gray>Npc removed."));
      } else if (!args[1].equalsIgnoreCase("set")) {
         sender.sendMessage(Component.text("/lobby npc set|remove|refreshskin"));
      } else {
         Location at;
         if (args.length >= 5) {
            World world = (World)Bukkit.getWorlds().get(0);

            try {
               at = new Location(
                  world,
                  Double.parseDouble(args[2]),
                  Double.parseDouble(args[3]),
                  Double.parseDouble(args[4]),
                  args.length > 5 ? Float.parseFloat(args[5]) : 0.0F,
                  0.0F
               );
            } catch (NumberFormatException var7) {
               sender.sendMessage(Component.text("/lobby npc set <x> <y> <z> [yaw]"));
               return;
            }
         } else {
            if (!(sender instanceof Player player)) {
               sender.sendMessage(Component.text("/lobby npc set <x> <y> <z> [yaw] from the console"));
               return;
            }

            at = player.getLocation().clone();
            at.setYaw(at.getYaw() + 180.0F);
            at.setPitch(0.0F);
         }

         this.plugin.npc().place(at);
         sender.sendMessage(
            this.plugin.text("<gray>Npc placed at <white><where></white>.", "where", (int)at.getX() + " " + (int)at.getY() + " " + (int)at.getZ())
         );
      }
   }

   private void setspawn(CommandSender sender) {
      if (sender instanceof Player player) {
         this.plugin.setSpawn(player.getLocation());
         sender.sendMessage(this.plugin.text("<gray>Lobby spawn set where you stand."));
      } else {
         sender.sendMessage(Component.text("in game only"));
      }
   }

   private void send(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         String target = args.length > 1 ? args[1] : this.plugin.getConfig().getString("npc.target-server", "ffa");
         if (!this.plugin.proxy().connect(player, target)) {
            sender.sendMessage(this.plugin.text("<red>No proxy answered."));
         }
      } else {
         sender.sendMessage(Component.text("in game only"));
      }
   }

   private void proxyTest(CommandSender sender) {
      FileConfiguration cfg = this.plugin.getConfig();
      String ping = cfg.getString("count.ping", "");
      Integer pinged = this.plugin.pinged();
      sender.sendMessage(this.plugin.text("<gray>proxy answered a count: <white>" + (this.plugin.proxy().connected() ? "yes" : "no")));
      sender.sendMessage(
         this.plugin
            .text(
               "<gray>direct ping "
                  + (ping != null && !ping.isBlank() ? "<white>" + ping : "<red>not set")
                  + "<gray>: <white>"
                  + (pinged == null ? "not yet" : (pinged < 0 ? "no answer" : pinged + " online"))
            )
      );
      String transfer = cfg.getString("click.transfer-host", "");
      sender.sendMessage(
         this.plugin
            .text(
               "<gray>transfer host: <white>"
                  + (transfer != null && !transfer.isBlank() ? transfer + ":" + cfg.getInt("click.transfer-port", 25565) : "not set")
            )
      );
      if (sender instanceof Player player) {
         boolean hub = this.plugin.proxy().hubOpen(player);
         boolean bungee = this.plugin.proxy().bungeeOpen(player);
         sender.sendMessage(
            this.plugin
               .text(
                  "<gray>FlameFFAProxy channel: "
                     + (hub ? "<green>open" : "<red>closed")
                     + "<gray>, answered: <white>"
                     + (this.plugin.proxy().hubAlive() ? "yes" : "no")
               )
         );
         sender.sendMessage(this.plugin.text("<gray>bungee channel: " + (bungee ? "<green>open" : "<red>closed")));
         sender.sendMessage(this.plugin.text("<gray>channels you announce: <white>" + player.getListeningPluginChannels()));
         if (!hub) {
            sender.sendMessage(
               this.plugin
                  .text("<yellow>closed means the FlameFFAProxy jar is not running on velocity, or you joined this server directly and not through the proxy.")
            );
         }
      }
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (args.length <= 1) {
         return List.of("npc", "setspawn", "send", "proxytest", "reload");
      } else if (args[0].equalsIgnoreCase("npc")) {
         return List.of("set", "remove", "refreshskin");
      } else {
         return args[0].equalsIgnoreCase("send") ? List.of(this.plugin.getConfig().getString("npc.target-server", "ffa")) : List.of();
      }
   }
}
