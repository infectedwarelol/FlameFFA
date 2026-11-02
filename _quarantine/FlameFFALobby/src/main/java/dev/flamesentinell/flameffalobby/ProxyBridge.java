package dev.flamesentinell.flameffalobby;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public final class ProxyBridge implements PluginMessageListener {
   public static final String HUB = "flameffa:hub";
   public static final String CHANNEL = "bungeecord:main";
   public static final String LEGACY = "BungeeCord";
   private final FlameFFALobby plugin;
   private final Map<String, Integer> counts = new HashMap<>();
   private long lastAnswer;
   private long lastHubAnswer;

   public ProxyBridge(FlameFFALobby plugin) {
      this.plugin = plugin;
   }

   public void register() {
      Messenger messenger = Bukkit.getMessenger();
      messenger.registerOutgoingPluginChannel(this.plugin, "flameffa:hub");
      messenger.registerIncomingPluginChannel(this.plugin, "flameffa:hub", this);
      messenger.registerOutgoingPluginChannel(this.plugin, "bungeecord:main");
      messenger.registerIncomingPluginChannel(this.plugin, "bungeecord:main", this);

      try {
         messenger.registerOutgoingPluginChannel(this.plugin, "BungeeCord");
         messenger.registerIncomingPluginChannel(this.plugin, "BungeeCord", this);
      } catch (Exception var3) {
      }

      this.plugin.getSLF4JLogger().info("proxy channel registered, outgoing {}", messenger.getOutgoingChannels(this.plugin));
   }

   public void unregister() {
      Bukkit.getMessenger().unregisterOutgoingPluginChannel(this.plugin);
      Bukkit.getMessenger().unregisterIncomingPluginChannel(this.plugin);
   }

   public Integer count(String server) {
      return this.counts.get(server);
   }

   public boolean hubOpen(Player player) {
      return player.getListeningPluginChannels().contains("flameffa:hub");
   }

   private String bungeeName(Player player) {
      Set<String> listening = player.getListeningPluginChannels();
      if (listening.contains("BungeeCord")) {
         return "BungeeCord";
      } else {
         return listening.contains("bungeecord:main") ? "bungeecord:main" : null;
      }
   }

   public boolean bungeeOpen(Player player) {
      return this.bungeeName(player) != null;
   }

   public boolean channelOpen(Player player) {
      return this.hubOpen(player) || this.bungeeOpen(player);
   }

   public boolean hubAlive() {
      return this.lastHubAnswer > 0L && System.currentTimeMillis() - this.lastHubAnswer < 30000L;
   }

   public boolean connected() {
      return this.lastAnswer > 0L && System.currentTimeMillis() - this.lastAnswer < 30000L;
   }

   public void askCount(String server) {
      Player carrier = this.any();
      if (carrier != null) {
         if (this.hubOpen(carrier)) {
            this.write(carrier, "flameffa:hub", out -> {
               out.writeUTF("Count");
               out.writeUTF(server);
            });
         } else {
            String bungee = this.bungeeName(carrier);
            if (bungee != null) {
               this.write(carrier, bungee, out -> {
                  out.writeUTF("PlayerCount");
                  out.writeUTF(server);
               });
            }
         }
      }
   }

   public boolean connect(Player player, String server) {
      if (this.hubOpen(player)) {
         return this.write(player, "flameffa:hub", out -> {
            out.writeUTF("Connect");
            out.writeUTF(server);
         });
      } else {
         String bungee = this.bungeeName(player);
         if (bungee == null) {
            this.plugin
               .getSLF4JLogger()
               .warn(
                  "{} has no proxy channel open at all, listening on {}. put FlameFFAProxy on velocity, or set bungee-plugin-message-channel = true in velocity.toml",
                  player.getName(),
                  player.getListeningPluginChannels()
               );
            return false;
         } else {
            return this.write(player, bungee, out -> {
               out.writeUTF("Connect");
               out.writeUTF(server);
            });
         }
      }
   }

   private Player any() {
      Iterator var1 = Bukkit.getOnlinePlayers().iterator();
      return var1.hasNext() ? (Player)var1.next() : null;
   }

   private boolean write(Player carrier, String channel, ProxyBridge.Body body) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try (DataOutputStream out = new DataOutputStream(bytes)) {
         body.write(out);
      } catch (IOException var11) {
         return false;
      }

      try {
         carrier.sendPluginMessage(this.plugin, channel, bytes.toByteArray());
         if (this.plugin.debug()) {
            this.plugin.getSLF4JLogger().info("sent {} bytes on {} via {}", new Object[]{bytes.size(), channel, carrier.getName()});
         }

         return true;
      } catch (Exception var9) {
         this.plugin.getSLF4JLogger().warn("proxy message failed, is this server behind velocity", var9);
         return false;
      }
   }

   public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
      boolean hub = channel.equals("flameffa:hub");
      if (hub || channel.equals("bungeecord:main") || channel.equals("BungeeCord")) {
         try {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
               String sub = in.readUTF();
               if (hub) {
                  this.lastHubAnswer = System.currentTimeMillis();
                  if (!sub.equals("Count")) {
                     return;
                  }
               } else if (!sub.equals("PlayerCount")) {
                  return;
               }

               String server = in.readUTF();
               int count = in.readInt();
               if (count >= 0) {
                  this.counts.put(server, count);
                  this.lastAnswer = System.currentTimeMillis();
                  if (this.plugin.debug()) {
                     this.plugin.getSLF4JLogger().info("proxy answered a count for {} over {}: {}", new Object[]{server, channel, count});
                  }

                  return;
               }

               this.plugin.getSLF4JLogger().warn("velocity has no server called '{}', check count.server", server);
            }
         } catch (IOException var11) {
         }
      }
   }

   private interface Body {
      void write(DataOutputStream var1) throws IOException;
   }
}
