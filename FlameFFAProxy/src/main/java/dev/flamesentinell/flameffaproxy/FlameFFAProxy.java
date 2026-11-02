package dev.flamesentinell.flameffaproxy;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

@Plugin(
   id = "flameffaproxy",
   name = "FlameFFAProxy",
   version = "1.0.0",
   authors = {"flamesentinell"}
)
public final class FlameFFAProxy {
   public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("flameffa:hub");
   private final ProxyServer proxy;
   private final Logger logger;
   private final Path dataDir;
   private final Properties cfg = new Properties();

   @Inject
   public FlameFFAProxy(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
      this.proxy = proxy;
      this.logger = logger;
      this.dataDir = dataDir;
   }

   @Subscribe
   public void onInit(ProxyInitializeEvent event) {
      this.loadConfig();
      this.proxy.getChannelRegistrar().register(new ChannelIdentifier[]{CHANNEL});
      if (this.bool("commands", true)) {
         this.command("ffa", this.cfg.getProperty("ffa-server", "ffa"));
         this.command("hub", this.cfg.getProperty("lobby-server", "lobby"), "lobby");
      }

      this.logger.info("channel {} registered, backends see it as open on every player", CHANNEL.getId());
      this.logger.info("servers known to the proxy: {}", this.proxy.getAllServers().stream().map(s -> s.getServerInfo().getName()).toList());
   }

   @Subscribe
   public void onMessage(PluginMessageEvent event) {
      if (event.getIdentifier().equals(CHANNEL)) {
         event.setResult(ForwardResult.handled());
         if (event.getSource() instanceof ServerConnection from) {
            Player var12 = from.getPlayer();

            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
               String sub = in.readUTF();
               switch (sub) {
                  case "Connect":
                     this.connect(var12, in.readUTF(), from.getServerInfo().getName());
                     break;
                  case "Count":
                     String name = in.readUTF();
                     this.reply(from, "Count", name, this.count(name));
                     break;
                  case "Ping":
                     this.reply(from, "Pong", from.getServerInfo().getName(), this.proxy.getPlayerCount());
                     break;
                  default:
                     this.logger.warn("unknown request {} from {}", sub, from.getServerInfo().getName());
               }
            } catch (IOException var11) {
               this.logger.warn("bad message from {}", from.getServerInfo().getName(), var11);
            }
         }
      }
   }

   private int count(String name) {
      Optional<RegisteredServer> server = this.proxy.getServer(name);
      return server.<Integer>map(s -> s.getPlayersConnected().size()).orElse(-1);
   }

   private void reply(ServerConnection to, String sub, String name, int value) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try (DataOutputStream out = new DataOutputStream(bytes)) {
         out.writeUTF(sub);
         out.writeUTF(name);
         out.writeInt(value);
      } catch (IOException var11) {
         return;
      }

      to.sendPluginMessage(CHANNEL, bytes.toByteArray());
   }

   private void connect(Player player, String name, String askedBy) {
      Optional<RegisteredServer> server = this.proxy.getServer(name);
      if (server.isEmpty()) {
         this.logger
            .warn(
               "{} asked to send {} to '{}' but velocity.toml has no such server, it has {}",
               new Object[]{askedBy, player.getUsername(), name, this.proxy.getAllServers().stream().map(s -> s.getServerInfo().getName()).toList()}
            );
         player.sendMessage(MiniMessage.miniMessage().deserialize(this.cfg.getProperty("msg-unknown", "<red>That server does not exist.")));
      } else if (player.getCurrentServer().map(c -> c.getServerInfo().getName().equalsIgnoreCase(name)).orElse(false)) {
         player.sendMessage(MiniMessage.miniMessage().deserialize(this.cfg.getProperty("msg-already", "<gray>You are already there.")));
      } else {
         player.createConnectionRequest(server.get())
            .connect()
            .whenComplete(
               (result, error) -> {
                  if (error != null) {
                     this.logger.warn("could not send {} to {}", new Object[]{player.getUsername(), name, error});
                  } else if (result.isSuccessful()) {
                     this.logger.info("sent {} to {} (asked by {})", new Object[]{player.getUsername(), name, askedBy});
                  } else {
                     this.logger
                        .warn(
                           "{} -> {} failed: {} {}",
                           new Object[]{
                              player.getUsername(),
                              name,
                              result.getStatus(),
                              result.getReasonComponent().map(c -> (String)MiniMessage.miniMessage().serialize(c)).orElse("")
                           }
                        );
                     player.sendMessage(
                        MiniMessage.miniMessage()
                           .deserialize(
                              this.cfg.getProperty("msg-failed", "<red>Could not connect you to <white><server></white> right now."),
                              Placeholder.unparsed("server", name)
                           )
                     );
                  }
               }
            );
      }
   }

   private void command(final String label, final String target, String... aliases) {
      SimpleCommand cmd = new SimpleCommand() {
         public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (source instanceof Player player) {
               FlameFFAProxy.this.connect(player, target, "/" + label);
            } else {
               source.sendMessage(MiniMessage.miniMessage().deserialize("<red>Players only."));
            }
         }

         public boolean hasPermission(Invocation invocation) {
            return true;
         }
      };
      CommandMeta meta = this.proxy.getCommandManager().metaBuilder(label).aliases(aliases).plugin(this).build();
      this.proxy.getCommandManager().register(meta, cmd);
   }

   private void loadConfig() {
      try {
         Files.createDirectories(this.dataDir);
         Path file = this.dataDir.resolve("config.properties");
         if (!Files.exists(file)) {
            try (InputStream in = this.getClass().getResourceAsStream("/config.properties")) {
               if (in != null) {
                  Files.copy(in, file);
               }
            }
         }

         try (InputStream inx = Files.newInputStream(file)) {
            this.cfg.load(inx);
         }
      } catch (IOException var9) {
         this.logger.warn("could not read config.properties, using defaults", var9);
      }
   }

   private boolean bool(String key, boolean def) {
      String v = this.cfg.getProperty(key);
      return v == null ? def : List.of("true", "yes", "on").contains(v.trim().toLowerCase(Locale.ROOT));
   }
}
