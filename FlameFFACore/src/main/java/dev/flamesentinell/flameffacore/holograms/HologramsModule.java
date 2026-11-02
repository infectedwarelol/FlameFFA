package dev.flamesentinell.flameffacore.holograms;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.module.Module;
import dev.flamesentinell.flameffacore.module.ModuleCommand;
import dev.flamesentinell.flameffacore.module.ModuleConfig;
import dev.flamesentinell.flameffacore.util.Tasks;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public final class HologramsModule implements Module, Listener {
   static final MiniMessage TEXT = MiniMessage.builder()
      .tags(
         TagResolver.resolver(
            StandardTags.color(),
            StandardTags.decorations(),
            StandardTags.gradient(),
            StandardTags.rainbow(),
            StandardTags.reset(),
            StandardTags.newline(),
            StandardTags.font(),
            StandardTags.transition(),
            StandardTags.keybind(),
            StandardTags.translatable(),
            StandardTags.shadowColor(),
            StandardTags.pride()
         )
      )
      .build();
   private static final MiniMessage MESSAGES = MiniMessage.miniMessage();
   private final FlameFFACore plugin;
   private final ModuleConfig config;
   private final File dataFile;
   private final NamespacedKey key = new NamespacedKey("flameholograms", "id");
   private final Map<String, Holo> holos = Collections.synchronizedMap(new LinkedHashMap<>());
   private ScheduledTask refreshTask;

   public HologramsModule(FlameFFACore plugin) {
      this.plugin = plugin;
      this.config = new ModuleConfig(plugin, "holograms.yml", "FlameHolograms", "config.yml");
      this.dataFile = new File(plugin.getDataFolder(), "holograms-data.yml");
   }

   @Override
   public String id() {
      return "holograms";
   }

   @Override
   public void enable() {
      ModuleConfig.migrate(this.plugin, ModuleConfig.legacyFile(this.plugin, "FlameHolograms", "holograms.yml"), this.dataFile);
      this.config.load();
      this.load();
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      if (!Tasks.FOLIA) {
         for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
               this.purge(chunk.getEntities());
            }
         }
      }

      this.spawnAll();
      this.startRefresh();
   }

   @Override
   public void disable() {
      HandlerList.unregisterAll(this);
      Tasks.cancel(this.refreshTask);
      this.refreshTask = null;

      for (Holo holo : this.all()) {
         try {
            this.despawnNow(holo);
         } catch (RuntimeException offThread) {
            holo.entity = null;
         }
      }

      this.holos.clear();
   }

   @Override
   public void commands(Commands commands) {
      HoloCommand command = new HoloCommand(this);
      ModuleCommand.register(
         commands, "holo", "Place and edit floating text", List.of("hologram", "holograms"), "flameholo.admin", null, command::run, command::complete
      );
   }

   FileConfiguration cfg() {
      return this.config.get();
   }

   FlameFFACore plugin() {
      return this.plugin;
   }

   void reload() {
      for (Holo holo : this.all()) {
         this.despawn(holo);
      }

      this.config.load();
      this.load();
      this.spawnAll();
      this.startRefresh();
   }

   private void startRefresh() {
      Tasks.cancel(this.refreshTask);
      long period = Math.max(1, this.cfg().getInt("refresh-seconds", 5)) * 20L;
      this.refreshTask = this.plugin.tasks().globalTimer(task -> {
         for (Holo holo : this.all()) {
            if (holo.live()) {
               this.redraw(holo);
            }
         }
      }, period, period);
   }

   private void load() {
      Map<String, Holo> loaded = new LinkedHashMap<>();
      ConfigurationSection root = YamlConfiguration.loadConfiguration(this.dataFile).getConfigurationSection("holograms");
      if (root != null) {
         for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section != null) {
               Holo holo = new Holo(name);
               holo.world = section.getString("world", "world");
               holo.x = section.getDouble("x");
               holo.y = section.getDouble("y");
               holo.z = section.getDouble("z");
               holo.yaw = (float)section.getDouble("yaw");
               holo.lines.addAll(section.getStringList("lines"));
               holo.scale = (float)section.getDouble("scale", 1.0);
               Billboard billboard = billboard(section.getString("billboard", "center"));
               holo.billboard = billboard == null ? Billboard.CENTER : billboard;
               holo.shadow = section.getBoolean("shadow", true);
               holo.seeThrough = section.getBoolean("see-through", false);
               holo.lineWidth = section.getInt("line-width", 240);
               holo.background = background(section.getString("background", "none"));
               loaded.put(name.toLowerCase(Locale.ROOT), holo);
            }
         }
      }

      synchronized (this.holos) {
         this.holos.clear();
         this.holos.putAll(loaded);
      }
   }

   void save() {
      YamlConfiguration out = new YamlConfiguration();
      out.options().setHeader(List.of("written by /holo  edit with /holo then /holo reload if you change it by hand"));

      for (Holo holo : this.all()) {
         String path = "holograms." + holo.name + ".";
         out.set(path + "world", holo.world);
         out.set(path + "x", round(holo.x));
         out.set(path + "y", round(holo.y));
         out.set(path + "z", round(holo.z));
         out.set(path + "yaw", round(holo.yaw));
         out.set(path + "lines", new ArrayList<>(holo.lines));
         out.set(path + "scale", (double)holo.scale);
         out.set(path + "billboard", holo.billboard.name().toLowerCase(Locale.ROOT));
         out.set(path + "shadow", holo.shadow);
         out.set(path + "see-through", holo.seeThrough);
         out.set(path + "line-width", holo.lineWidth);
         out.set(path + "background", backgroundName(holo.background));
      }

      this.plugin.configs().saveAsync(out, this.dataFile);
   }

   private static double round(double value) {
      return Math.round(value * 1000.0) / 1000.0;
   }

   Holo fresh(String name) {
      Holo holo = new Holo(name);
      ConfigurationSection defaults = this.cfg().getConfigurationSection("defaults");
      if (defaults != null) {
         holo.scale = (float)defaults.getDouble("scale", 1.0);
         Billboard billboard = billboard(defaults.getString("billboard", "center"));
         holo.billboard = billboard == null ? Billboard.CENTER : billboard;
         holo.shadow = defaults.getBoolean("shadow", true);
         holo.seeThrough = defaults.getBoolean("see-through", false);
         holo.lineWidth = defaults.getInt("line-width", 240);
         holo.background = background(defaults.getString("background", "none"));
      }

      return holo;
   }

   static Billboard billboard(String name) {
      try {
         return Billboard.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException unknown) {
         return null;
      }
   }

   static Integer background(String raw) {
      if (raw == null || raw.equalsIgnoreCase("none")) {
         return 0;
      } else if (raw.equalsIgnoreCase("default")) {
         return null;
      } else {
         String hex = raw.startsWith("#") ? raw.substring(1) : raw;

         try {
            if (hex.length() == 6) {
               return (int)(2147483648L | Long.parseLong(hex, 16));
            }

            if (hex.length() == 8) {
               return (int)Long.parseLong(hex, 16);
            }
         } catch (NumberFormatException malformed) {
            return -1;
         }

         return -1;
      }
   }

   static String backgroundName(Integer argb) {
      if (argb == null) {
         return "default";
      } else {
         return argb == 0 ? "none" : String.format("#%08X", argb);
      }
   }

   private Location location(Holo holo) {
      World world = Bukkit.getWorld(holo.world);
      return world == null ? null : new Location(world, holo.x, holo.y, holo.z, holo.yaw, 0.0F);
   }

   private void spawnAll() {
      for (Holo holo : this.all()) {
         this.spawn(holo);
      }
   }

   void spawn(Holo holo) {
      Location at = this.location(holo);
      if (at != null) {
         this.plugin.tasks().runAt(at, () -> this.spawnNow(holo, at));
      }
   }

   private void spawnNow(Holo holo, Location at) {
      this.despawnNow(holo);
      if (at.getWorld().isChunkLoaded(holo.chunkX(), holo.chunkZ()) && this.holos.get(holo.name.toLowerCase(Locale.ROOT)) == holo) {
         TextDisplay display = (TextDisplay)at.getWorld().spawn(at, TextDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, holo.name);
            this.style(spawned, holo);
         });
         holo.entity = display.getUniqueId();
      }
   }

   void despawn(Holo holo) {
      Location at = this.location(holo);
      if (at == null) {
         holo.entity = null;
      } else {
         this.plugin.tasks().runAt(at, () -> this.despawnNow(holo));
      }
   }

   private void despawnNow(Holo holo) {
      if (holo.entity != null) {
         Entity entity = Bukkit.getEntity(holo.entity);
         if (entity != null) {
            entity.remove();
         }

         holo.entity = null;
      }
   }

   void redraw(Holo holo) {
      Location at = this.location(holo);
      if (at != null) {
         this.plugin.tasks().runAt(at, () -> {
            if ((holo.entity == null ? null : Bukkit.getEntity(holo.entity)) instanceof TextDisplay display && display.isValid()) {
               Tasks.move(display, at);
               this.style(display, holo);
            } else {
               this.spawnNow(holo, at);
            }
         });
      }
   }

   private void style(TextDisplay display, Holo holo) {
      display.text(this.render(holo));
      display.setBillboard(holo.billboard);
      display.setShadowed(holo.shadow);
      display.setSeeThrough(holo.seeThrough);
      display.setLineWidth(holo.lineWidth);
      display.setAlignment(TextAlignment.CENTER);
      display.setBrightness(new Brightness(15, 15));
      Integer argb = holo.background;
      if (argb == null) {
         display.setDefaultBackground(true);
      } else {
         display.setDefaultBackground(false);
         display.setBackgroundColor(Color.fromARGB(argb));
      }

      Transformation current = display.getTransformation();
      display.setTransformation(
         new Transformation(current.getTranslation(), current.getLeftRotation(), new Vector3f(holo.scale, holo.scale, holo.scale), current.getRightRotation())
      );
      display.setViewRange(1.0F);
   }

   Component render(Holo holo) {
      String text = String.join("\n", holo.lines)
         .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
         .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
      return TEXT.deserialize(text);
   }

   private void purge(Entity[] entities) {
      for (Entity entity : entities) {
         if (entity instanceof TextDisplay) {
            String owner = entity.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
            if (owner != null) {
               Holo holo = this.holos.get(owner.toLowerCase(Locale.ROOT));
               if (holo == null || !entity.getUniqueId().equals(holo.entity)) {
                  entity.remove();
               }
            }
         }
      }
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent event) {
      Chunk chunk = event.getChunk();
      this.purge(chunk.getEntities());
      String world = chunk.getWorld().getName();

      for (Holo holo : this.all()) {
         if (holo.world.equals(world) && holo.chunkX() == chunk.getX() && holo.chunkZ() == chunk.getZ()) {
            Location at = this.location(holo);
            if (at != null) {
               this.plugin.tasks().region(at, () -> this.spawnNow(holo, at));
            }
         }
      }
   }

   @EventHandler
   public void onChunkUnload(ChunkUnloadEvent event) {
      Chunk chunk = event.getChunk();
      String world = chunk.getWorld().getName();

      for (Holo holo : this.all()) {
         if (holo.world.equals(world) && holo.chunkX() == chunk.getX() && holo.chunkZ() == chunk.getZ()) {
            this.despawnNow(holo);
         }
      }
   }

   void send(CommandSender sender, String path, String... replacements) {
      String raw = this.cfg().getString("messages." + path, path);
      String prefix = this.cfg().getString("messages.prefix", "");
      List<TagResolver> resolvers = new ArrayList<>();
      resolvers.add(Placeholder.parsed("prefix", prefix));

      for (int index = 0; index + 1 < replacements.length; index += 2) {
         resolvers.add(Placeholder.unparsed(replacements[index], replacements[index + 1]));
      }

      sender.sendMessage(MESSAGES.deserialize(raw, TagResolver.resolver(resolvers)));
   }

   Holo get(String name) {
      return this.holos.get(name.toLowerCase(Locale.ROOT));
   }

   boolean exists(String name) {
      return this.holos.containsKey(name.toLowerCase(Locale.ROOT));
   }

   boolean add(Holo holo) {
      return this.holos.putIfAbsent(holo.name.toLowerCase(Locale.ROOT), holo) == null;
   }

   void remove(Holo holo) {
      this.holos.remove(holo.name.toLowerCase(Locale.ROOT), holo);
   }

   int count() {
      return this.holos.size();
   }

   List<Holo> all() {
      synchronized (this.holos) {
         return new ArrayList<>(this.holos.values());
      }
   }
}
