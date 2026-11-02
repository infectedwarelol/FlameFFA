package dev.flamesentinell.flameholo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public final class FlameHolo extends JavaPlugin implements Listener {
   final Map<String, Holo> holos = new LinkedHashMap<>();
   final MiniMessage mm = MiniMessage.miniMessage();
   private NamespacedKey key;
   private File file;
   private int refreshTask = -1;

   public void onEnable() {
      this.saveDefaultConfig();
      this.key = new NamespacedKey(this, "id");
      this.file = new File(this.getDataFolder(), "holograms.yml");
      this.load();
      this.getServer().getPluginManager().registerEvents(this, this);
      HoloCommand var1 = new HoloCommand(this);
      this.getCommand("holo").setExecutor(var1);
      this.getCommand("holo").setTabCompleter(var1);

      for (World var3 : Bukkit.getWorlds()) {
         for (Chunk var7 : var3.getLoadedChunks()) {
            this.purge(var7.getEntities());
         }
      }

      this.spawnAll();
      this.startRefresh();
   }

   public void onDisable() {
      for (Holo var2 : this.holos.values()) {
         this.despawn(var2);
      }
   }

   void reload() {
      for (Holo var2 : this.holos.values()) {
         this.despawn(var2);
      }

      this.reloadConfig();
      this.load();
      this.spawnAll();
      this.startRefresh();
   }

   private void startRefresh() {
      if (this.refreshTask != -1) {
         Bukkit.getScheduler().cancelTask(this.refreshTask);
      }

      long var1 = Math.max(1, this.getConfig().getInt("refresh-seconds", 5)) * 20L;
      this.refreshTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
         for (Holo var2 : this.holos.values()) {
            if (var2.live()) {
               this.redraw(var2);
            }
         }
      }, var1, var1).getTaskId();
   }

   private void load() {
      this.holos.clear();
      YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
      ConfigurationSection var2 = var1.getConfigurationSection("holograms");
      if (var2 != null) {
         for (String var4 : var2.getKeys(false)) {
            ConfigurationSection var5 = var2.getConfigurationSection(var4);
            if (var5 != null) {
               Holo var6 = new Holo(var4);
               var6.world = var5.getString("world", "world");
               var6.x = var5.getDouble("x");
               var6.y = var5.getDouble("y");
               var6.z = var5.getDouble("z");
               var6.yaw = (float)var5.getDouble("yaw");
               var6.lines.addAll(var5.getStringList("lines"));
               var6.scale = (float)var5.getDouble("scale", 1.0);
               var6.billboard = billboard(var5.getString("billboard", "center"));
               var6.shadow = var5.getBoolean("shadow", true);
               var6.seeThrough = var5.getBoolean("see-through", false);
               var6.lineWidth = var5.getInt("line-width", 240);
               var6.background = background(var5.getString("background", "none"));
               this.holos.put(var4.toLowerCase(Locale.ROOT), var6);
            }
         }
      }
   }

   void save() {
      YamlConfiguration var1 = new YamlConfiguration();
      var1.options().setHeader(List.of("written by /holo  edit with /holo then /holo reload if you change it by hand"));

      for (Holo var3 : this.holos.values()) {
         String var4 = "holograms." + var3.name + ".";
         var1.set(var4 + "world", var3.world);
         var1.set(var4 + "x", round(var3.x));
         var1.set(var4 + "y", round(var3.y));
         var1.set(var4 + "z", round(var3.z));
         var1.set(var4 + "yaw", round(var3.yaw));
         var1.set(var4 + "lines", var3.lines);
         var1.set(var4 + "scale", (double)var3.scale);
         var1.set(var4 + "billboard", var3.billboard.name().toLowerCase(Locale.ROOT));
         var1.set(var4 + "shadow", var3.shadow);
         var1.set(var4 + "see-through", var3.seeThrough);
         var1.set(var4 + "line-width", var3.lineWidth);
         var1.set(var4 + "background", backgroundName(var3.background));
      }

      try {
         var1.save(this.file);
      } catch (IOException var5) {
         this.getLogger().warning("could not save holograms.yml  " + var5.getMessage());
      }
   }

   private static double round(double var0) {
      return Math.round(var0 * 1000.0) / 1000.0;
   }

   Holo fresh(String var1) {
      Holo var2 = new Holo(var1);
      ConfigurationSection var3 = this.getConfig().getConfigurationSection("defaults");
      if (var3 != null) {
         var2.scale = (float)var3.getDouble("scale", 1.0);
         var2.billboard = billboard(var3.getString("billboard", "center"));
         var2.shadow = var3.getBoolean("shadow", true);
         var2.seeThrough = var3.getBoolean("see-through", false);
         var2.lineWidth = var3.getInt("line-width", 240);
         var2.background = background(var3.getString("background", "none"));
      }

      return var2;
   }

   static Billboard billboard(String var0) {
      try {
         return Billboard.valueOf(var0.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var2) {
         return null;
      }
   }

   static Integer background(String var0) {
      if (var0 == null || var0.equalsIgnoreCase("none")) {
         return 0;
      } else if (var0.equalsIgnoreCase("default")) {
         return null;
      } else {
         String var1 = var0.startsWith("#") ? var0.substring(1) : var0;

         try {
            if (var1.length() == 6) {
               return (int)(2147483648L | Long.parseLong(var1, 16));
            }

            if (var1.length() == 8) {
               return (int)Long.parseLong(var1, 16);
            }
         } catch (NumberFormatException var3) {
         }

         return -1;
      }
   }

   static String backgroundName(Integer var0) {
      if (var0 == null) {
         return "default";
      } else {
         return var0 == 0 ? "none" : String.format("#%08X", var0);
      }
   }

   private void spawnAll() {
      for (Holo var2 : this.holos.values()) {
         World var3 = Bukkit.getWorld(var2.world);
         if (var3 != null && var3.isChunkLoaded(var2.chunkX(), var2.chunkZ())) {
            this.spawn(var2);
         }
      }
   }

   void spawn(Holo var1) {
      this.despawn(var1);
      World var2 = Bukkit.getWorld(var1.world);
      if (var2 != null && var2.isChunkLoaded(var1.chunkX(), var1.chunkZ())) {
         Location var3 = new Location(var2, var1.x, var1.y, var1.z, var1.yaw, 0.0F);
         TextDisplay var4 = (TextDisplay)var2.spawn(var3, TextDisplay.class, var2x -> {
            var2x.setPersistent(false);
            var2x.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, var1.name);
            this.style(var2x, var1);
         });
         var1.entity = var4.getUniqueId();
      }
   }

   void despawn(Holo var1) {
      if (var1.entity != null) {
         Entity var2 = Bukkit.getEntity(var1.entity);
         if (var2 != null) {
            var2.remove();
         }

         var1.entity = null;
      }
   }

   void redraw(Holo var1) {
      if ((var1.entity == null ? null : Bukkit.getEntity(var1.entity)) instanceof TextDisplay var3 && var3.isValid()) {
         var3.teleport(new Location(var3.getWorld(), var1.x, var1.y, var1.z, var1.yaw, 0.0F));
         this.style(var3, var1);
      } else {
         this.spawn(var1);
      }
   }

   private void style(TextDisplay var1, Holo var2) {
      var1.text(this.render(var2));
      var1.setBillboard(var2.billboard);
      var1.setShadowed(var2.shadow);
      var1.setSeeThrough(var2.seeThrough);
      var1.setLineWidth(var2.lineWidth);
      var1.setAlignment(TextAlignment.CENTER);
      var1.setBrightness(new Brightness(15, 15));
      if (var2.background == null) {
         var1.setDefaultBackground(true);
      } else {
         var1.setDefaultBackground(false);
         var1.setBackgroundColor(Color.fromARGB(var2.background));
      }

      Transformation var3 = var1.getTransformation();
      var1.setTransformation(
         new Transformation(var3.getTranslation(), var3.getLeftRotation(), new Vector3f(var2.scale, var2.scale, var2.scale), var3.getRightRotation())
      );
      var1.setViewRange(1.0F);
   }

   Component render(Holo var1) {
      String var2 = String.join("\n", var1.lines)
         .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
         .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
      return this.mm.deserialize(var2);
   }

   private void purge(Entity[] var1) {
      for (Entity var5 : var1) {
         if (var5 instanceof TextDisplay) {
            String var6 = (String)var5.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
            if (var6 != null) {
               Holo var7 = this.holos.get(var6.toLowerCase(Locale.ROOT));
               if (var7 == null || !var5.getUniqueId().equals(var7.entity)) {
                  var5.remove();
               }
            }
         }
      }
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent var1) {
      Chunk var2 = var1.getChunk();
      this.purge(var2.getEntities());

      for (Holo var4 : this.holos.values()) {
         if (var4.world.equals(var2.getWorld().getName()) && var4.chunkX() == var2.getX() && var4.chunkZ() == var2.getZ()) {
            Bukkit.getScheduler().runTask(this, () -> this.spawn(var4));
         }
      }
   }

   @EventHandler
   public void onChunkUnload(ChunkUnloadEvent var1) {
      Chunk var2 = var1.getChunk();

      for (Holo var4 : this.holos.values()) {
         if (var4.world.equals(var2.getWorld().getName()) && var4.chunkX() == var2.getX() && var4.chunkZ() == var2.getZ()) {
            this.despawn(var4);
         }
      }
   }

   void send(CommandSender var1, String var2, String... var3) {
      String var4 = this.getConfig().getString("messages." + var2, var2);
      String var5 = this.getConfig().getString("messages.prefix", "");
      ArrayList var6 = new ArrayList();
      var6.add(Placeholder.parsed("prefix", var5));

      for (byte var7 = 0; var7 + 1 < var3.length; var7 += 2) {
         var6.add(Placeholder.unparsed(var3[var7], var3[var7 + 1]));
      }

      var1.sendMessage(this.mm.deserialize(var4, TagResolver.resolver(var6)));
   }

   void sendRaw(CommandSender var1, Component var2) {
      var1.sendMessage(var2);
   }

   Collection<Holo> all() {
      return this.holos.values();
   }
}
