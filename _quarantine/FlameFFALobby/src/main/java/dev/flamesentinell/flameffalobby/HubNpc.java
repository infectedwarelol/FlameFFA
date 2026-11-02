package dev.flamesentinell.flameffalobby;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public final class HubNpc implements Listener {
   private static final String TAG = "flameffalobby_npc";
   private final FlameFFALobby plugin;
   private Mannequin body;
   private TextDisplay title;
   private TextDisplay subtitle;
   private Location where;
   private String target = "ffa";
   private String countServer = "ffa";
   private double scale = 1.25;
   private int refreshTicks = 100;
   private Sound clickSound;
   private Integer lastShown;
   private boolean painted;
   private ResolvableProfile profile;

   public HubNpc(FlameFFALobby plugin) {
      this.plugin = plugin;
   }

   public void load() {
      FileConfiguration cfg = this.plugin.getConfig();
      this.target = cfg.getString("npc.target-server", "ffa");
      this.countServer = cfg.getString("count.server", this.target);
      this.scale = cfg.getDouble("npc.scale", 1.25);
      this.refreshTicks = Math.max(20, cfg.getInt("count.seconds", 5) * 20);
      this.clickSound = sound(cfg.getString("click.sound", ""));
      this.where = readLocation(cfg.getConfigurationSection("npc.location"));
      this.lastShown = null;
      this.painted = false;
   }

   public void spawn() {
      this.despawn();
      if (this.plugin.getConfig().getBoolean("npc.enabled", true) && this.where != null) {
         World world = this.where.getWorld();
         if (world == null) {
            this.plugin.getSLF4JLogger().warn("npc world is not loaded, nothing spawned");
         } else {
            this.body = (Mannequin)world.spawn(this.where, Mannequin.class, m -> {
               m.setDescription(Component.empty());
               m.setCustomNameVisible(false);
               m.setImmovable(true);
               m.setGravity(false);
               m.setSilent(true);
               m.setPersistent(false);
               m.setInvulnerable(true);
               m.setCollidable(false);
               m.addScoreboardTag("flameffalobby_npc");
               AttributeInstance s = m.getAttribute(Attribute.SCALE);
               if (s != null) {
                  s.setBaseValue(this.scale);
               }

               if (this.profile != null) {
                  m.setProfile(this.profile);
               }
            });
            world.setChunkForceLoaded(this.where.getBlockX() >> 4, this.where.getBlockZ() >> 4, true);
            double head = 2.0 * this.scale;
            this.title = this.line(world, this.where.clone().add(0.0, head + 0.34, 0.0), 1.0F);
            this.subtitle = this.line(world, this.where.clone().add(0.0, head + 0.05, 0.0), 0.72F);
            this.title.text(this.plugin.text(this.plugin.getConfig().getString("npc.name", "FFA")));
            this.painted = false;
            this.lastShown = null;
            this.paint(null);
            this.plugin.getSLF4JLogger().info("second line drawn: {}", PlainTextComponentSerializer.plainText().serialize(this.subtitle.text()));
            AttributeInstance got = this.body.getAttribute(Attribute.SCALE);
            this.plugin
               .getSLF4JLogger()
               .info(
                  "npc up at {} {} {}, scale {}, skin {}, two lines above it",
                  new Object[]{
                     (int)this.where.getX(),
                     (int)this.where.getY(),
                     (int)this.where.getZ(),
                     got == null ? "default" : got.getBaseValue(),
                     this.profile == null ? "none" : this.profile.name()
                  }
               );
         }
      }
   }

   public void despawn() {
      remove(this.body);
      remove(this.title);
      remove(this.subtitle);
      this.body = null;
      this.title = null;
      this.subtitle = null;
   }

   public int sweep() {
      int gone = 0;

      for (World world : Bukkit.getWorlds()) {
         for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains("flameffalobby_npc")) {
               e.remove();
               gone++;
            }
         }
      }

      return gone;
   }

   private static void remove(Entity e) {
      if (e != null && !e.isDead()) {
         e.remove();
      }
   }

   private TextDisplay line(World world, Location at, float size) {
      return (TextDisplay)world.spawn(
         at,
         TextDisplay.class,
         d -> {
            d.setBillboard(Billboard.CENTER);
            d.setAlignment(TextAlignment.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setPersistent(false);
            d.setViewRange(0.6F);
            d.addScoreboardTag("flameffalobby_npc");
            d.setTransformation(
               new Transformation(
                  new Vector3f(), d.getTransformation().getLeftRotation(), new Vector3f(size, size, size), d.getTransformation().getRightRotation()
               )
            );
         }
      );
   }

   public int refreshTicks() {
      return this.refreshTicks;
   }

   public void refresh() {
      if (this.where == null || this.body != null && !this.body.isDead()) {
         if (this.body != null) {
            this.plugin.proxy().askCount(this.countServer);
            Integer count = this.plugin.proxy().connected() ? this.plugin.proxy().count(this.countServer) : null;
            if (count == null) {
               count = this.plugin.pinged();
            }

            this.paint(count);
         }
      } else {
         this.plugin.getSLF4JLogger().info("npc was missing, putting it back");
         this.spawn();
      }
   }

   private void paint(Integer count) {
      if (this.subtitle != null) {
         if (!this.painted || !Objects.equals(count, this.lastShown)) {
            this.painted = true;
            this.lastShown = count;
            String raw;
            if (count == null) {
               raw = this.plugin.getConfig().getString("count.unknown", "<gray>connecting");
            } else if (count < 0) {
               raw = this.plugin.getConfig().getString("count.offline", "<red>offline");
            } else {
               raw = this.plugin.getConfig().getString("count.line", "<white><count></white> <gray>Players Online");
            }

            this.subtitle.text(this.plugin.text(raw, "count", count != null && count >= 0 ? String.valueOf(count) : "0"));
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onRightClick(PlayerInteractEntityEvent event) {
      if (this.isNpc(event.getRightClicked())) {
         event.setCancelled(true);
         this.send(event.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onSwing(PrePlayerAttackEntityEvent event) {
      if (this.isNpc(event.getAttacked())) {
         event.setCancelled(true);
         this.send(event.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onLeftClick(EntityDamageByEntityEvent event) {
      if (this.isNpc(event.getEntity())) {
         event.setCancelled(true);
         if (event.getDamager() instanceof Player player) {
            this.send(player);
         }
      }
   }

   private boolean isNpc(Entity entity) {
      return entity != null && entity.getScoreboardTags().contains("flameffalobby_npc");
   }

   private void send(Player player) {
      if (this.clickSound != null) {
         player.playSound(player.getLocation(), this.clickSound, 0.7F, 1.4F);
      }

      player.sendActionBar(this.plugin.text(this.plugin.getConfig().getString("click.sending", "")));
      boolean quiet = this.plugin.proxy().channelOpen(player) && this.plugin.proxy().connect(player, this.target);
      this.plugin
         .getSLF4JLogger()
         .info(
            "{} clicked the npc, hub channel {}, bungee channel {}, request {}",
            new Object[]{
               player.getName(),
               this.plugin.proxy().hubOpen(player) ? "open" : "closed",
               this.plugin.proxy().bungeeOpen(player) ? "open" : "closed",
               quiet ? "sent" : "not sent"
            }
         );
      String transferHost = this.plugin.getConfig().getString("click.transfer-host", "");
      if (!quiet && transferHost != null && !transferHost.isBlank()) {
         int port = this.plugin.getConfig().getInt("click.transfer-port", 25565);
         this.plugin.getSLF4JLogger().info("bungee channel closed for {}, transferring to {}:{}", new Object[]{player.getName(), transferHost, port});
         player.transfer(transferHost, port);
      } else {
         int after = quiet ? Math.max(5, this.plugin.getConfig().getInt("click.fallback-ticks", 25)) : 1;
         Bukkit.getScheduler()
            .runTaskLater(
               this.plugin,
               () -> {
                  if (player.isOnline()) {
                     String raw = this.plugin.getConfig().getString("click.fallback", "");
                     if (raw != null && !raw.isBlank()) {
                        this.plugin.getSLF4JLogger().info("quiet switch did not happen for {}, sent the click line", player.getName());
                        player.sendMessage(
                           this.plugin
                              .text(raw, "server", this.target)
                              .clickEvent(ClickEvent.runCommand("/server " + this.target))
                              .hoverEvent(HoverEvent.showText(this.plugin.text("<gray>Run <white>/server " + this.target + "</white>")))
                        );
                     } else {
                        this.plugin.getSLF4JLogger().warn("click.fallback is empty, no fallback line was sent");
                     }
                  }
               },
               after
            );
      }
   }

   public void place(Location at) {
      this.where = at.clone();
      FileConfiguration cfg = this.plugin.getConfig();
      cfg.set("npc.location.world", at.getWorld().getName());
      cfg.set("npc.location.x", at.getX());
      cfg.set("npc.location.y", at.getY());
      cfg.set("npc.location.z", at.getZ());
      cfg.set("npc.location.yaw", at.getYaw());
      this.plugin.saveConfig();
      this.spawn();
   }

   public void unplace() {
      if (this.where != null && this.where.getWorld() != null) {
         this.where.getWorld().setChunkForceLoaded(this.where.getBlockX() >> 4, this.where.getBlockZ() >> 4, false);
      }

      this.despawn();
      this.where = null;
      this.plugin.getConfig().set("npc.location", null);
      this.plugin.saveConfig();
   }

   public boolean placed() {
      return this.where != null;
   }

   public Location where() {
      return this.where == null ? null : this.where.clone();
   }

   public void resolveSkin(Runnable then) {
      this.resolveSkin(false, then);
   }

   public void resolveSkin(boolean force, Runnable then) {
      FileConfiguration cfg = this.plugin.getConfig();
      ConfigurationSection cached = cfg.getConfigurationSection("npc.cached-skin");
      String want = cfg.getString("npc.skin", "flamesentinell");
      if (!force && cached != null && cached.getString("value") != null) {
         this.profile = build(UUID.fromString(cached.getString("uuid")), cached.getString("name"), cached.getString("value"), cached.getString("signature"));
         then.run();
      } else {
         Bukkit.getAsyncScheduler().runNow(this.plugin, task -> {
            HubNpc.Skin skin = fetch(want);
            Bukkit.getGlobalRegionScheduler().run(this.plugin, t -> {
               if (skin == null) {
                  this.plugin.getSLF4JLogger().warn("could not fetch the skin for {}, the npc keeps whatever it had", want);
               } else {
                  this.profile = build(skin.uuid, skin.name, skin.value, skin.signature);
                  cfg.set("npc.cached-skin.name", skin.name);
                  cfg.set("npc.cached-skin.uuid", skin.uuid.toString());
                  cfg.set("npc.cached-skin.value", skin.value);
                  cfg.set("npc.cached-skin.signature", skin.signature);
                  this.plugin.saveConfig();
                  this.plugin.getSLF4JLogger().info("skin for {} cached, no lookup needed next boot", skin.name);
               }

               then.run();
            });
         });
      }
   }

   private static ResolvableProfile build(UUID uuid, String name, String value, String signature) {
      PlayerProfile p = Bukkit.createProfile(uuid, name);
      p.setProperty(new ProfileProperty("textures", value, signature));
      return ResolvableProfile.resolvableProfile(p);
   }

   private static HubNpc.Skin fetch(String name) {
      try {
         String head = get("https://api.mojang.com/users/profiles/minecraft/" + name);
         String id = find(head, "\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
         String real = find(head, "\"name\"\\s*:\\s*\"([^\"]+)\"");
         if (id == null) {
            return null;
         } else {
            UUID uuid = UUID.fromString(id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
            String body = get("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false");
            String value = find(body, "\"value\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
            String signature = find(body, "\"signature\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
            return value == null ? null : new HubNpc.Skin(uuid, real == null ? name : real, value, signature);
         }
      } catch (Exception var8) {
         return null;
      }
   }

   private static String get(String url) throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8L)).header("accept", "application/json").GET().build();
      HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build().send(request, BodyHandlers.ofString());
      return response.statusCode() == 200 ? response.body() : "";
   }

   private static String find(String body, String pattern) {
      Matcher m = Pattern.compile(pattern).matcher(body);
      return m.find() ? m.group(1) : null;
   }

   private static Sound sound(String name) {
      if (name != null && !name.isBlank()) {
         try {
            return (Sound)Registry.SOUNDS.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT).replace('_', '.')));
         } catch (Exception var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   static Location readLocation(ConfigurationSection s) {
      if (s != null && s.getString("world") != null) {
         World world = Bukkit.getWorld(s.getString("world"));
         return world == null
            ? null
            : new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch"));
      } else {
         return null;
      }
   }

   private record Skin(UUID uuid, String name, String value, String signature) {
   }
}
