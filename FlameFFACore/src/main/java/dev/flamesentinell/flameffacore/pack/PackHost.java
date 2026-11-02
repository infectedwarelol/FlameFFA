package dev.flamesentinell.flameffacore.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Bots;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackInfoLike;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PackHost implements Listener {
   private final FlameFFACore plugin;
   private HttpServer server;
   private byte[] data;
   private String sha1;
   private UUID packId;
   private String url;

   public PackHost(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void start() {
      FileConfiguration cfg = this.plugin.configs().main();
      if (cfg.getBoolean("resource-pack.enabled", false)) {
         File file = new File(this.plugin.getDataFolder(), cfg.getString("resource-pack.file", "pack.zip"));
         int port = cfg.getInt("resource-pack.port", 0);
         String host = cfg.getString("resource-pack.public-host", "");
         if (file.isFile() && port > 0 && !host.isBlank()) {
            try {
               this.data = Files.readAllBytes(file.toPath());
               this.sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(this.data));
               this.packId = UUID.nameUUIDFromBytes(("flameffa-pack-" + this.sha1).getBytes());
               String path = "/pack/" + this.sha1 + ".zip";
               this.url = "http://" + host + ":" + port + path;
               this.server = HttpServer.create(new InetSocketAddress(cfg.getString("resource-pack.bind", "0.0.0.0"), port), 16);
               this.server.createContext("/pack/", ex -> this.serve(ex, path));
               this.server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                  Thread t = new Thread(r, "flameffa-pack");
                  t.setDaemon(true);
                  return t;
               }));
               this.server.start();
               this.plugin.getSLF4JLogger().info("resource pack {} ({} bytes) served at {}", new Object[]{this.sha1, this.data.length, this.url});
            } catch (Exception var6) {
               this.plugin.getSLF4JLogger().error("could not host the resource pack", var6);
               this.stop();
            }
         } else {
            this.plugin.getSLF4JLogger().warn("resource pack hosting is on but the file port or public host is missing");
         }
      }
   }

   private void serve(HttpExchange ex, String path) throws IOException {
      HttpExchange var3 = ex;

      label89: {
         label90: {
            label91: {
               try {
                  if (!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
                     ex.sendResponseHeaders(405, -1L);
                     break label89;
                  }

                  if (!path.equals(ex.getRequestURI().getPath())) {
                     ex.sendResponseHeaders(404, -1L);
                     break label90;
                  }

                  ex.getResponseHeaders().set("Content-Type", "application/zip");
                  ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
                  if ("HEAD".equals(ex.getRequestMethod())) {
                     ex.sendResponseHeaders(200, -1L);
                     break label91;
                  }

                  ex.sendResponseHeaders(200, this.data.length);

                  try (OutputStream out = ex.getResponseBody()) {
                     out.write(this.data);
                  }
               } catch (Throwable var10) {
                  if (ex != null) {
                     try {
                        var3.close();
                     } catch (Throwable var7) {
                        var10.addSuppressed(var7);
                     }
                  }

                  throw var10;
               }

               if (ex != null) {
                  ex.close();
               }

               return;
            }

            if (ex != null) {
               ex.close();
            }

            return;
         }

         if (ex != null) {
            ex.close();
         }

         return;
      }

      if (ex != null) {
         ex.close();
      }
   }

   public void stop() {
      if (this.server != null) {
         this.server.stop(0);
         this.server = null;
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (this.server != null && !Bots.is(player)) {
         FileConfiguration cfg = this.plugin.configs().main();
         ResourcePackRequest request = (ResourcePackRequest)ResourcePackRequest.resourcePackRequest()
            .packs(ResourcePackInfo.resourcePackInfo(this.packId, URI.create(this.url), this.sha1), new ResourcePackInfoLike[0])
            .required(cfg.getBoolean("resource-pack.required", true))
            .replace(true)
            .prompt(this.plugin.configs().msg("pack.prompt"))
            .build();
         player.sendResourcePacks(request);
      }
   }
}
