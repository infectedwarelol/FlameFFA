package dev.flamesentinell.flameffacore.module;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.config.ConfigManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ModuleConfig {
   private final FlameFFACore plugin;
   private final String name;
   private final File file;
   private final File legacy;
   private volatile YamlConfiguration config = new YamlConfiguration();
   private final Object updateLock = new Object();

   public ModuleConfig(FlameFFACore plugin, String name, String legacyPlugin, String legacyName) {
      this.plugin = plugin;
      this.name = name;
      this.file = new File(plugin.getDataFolder(), name);
      this.legacy = legacyFile(plugin, legacyPlugin, legacyName);
   }

   public static File legacyFile(FlameFFACore plugin, String legacyPlugin, String legacyName) {
      File plugins = plugin.getDataFolder().getParentFile();
      return plugins == null ? new File(legacyPlugin, legacyName) : new File(new File(plugins, legacyPlugin), legacyName);
   }

   public static void migrate(FlameFFACore plugin, File from, File to) {
      if (!to.exists() && from.isFile()) {
         try {
            File parent = to.getParentFile();
            if (parent != null) {
               parent.mkdirs();
            }

            Files.copy(from.toPath(), to.toPath());
            plugin.getSLF4JLogger().info("migrated {} to {}", from.getPath(), to.getName());
         } catch (IOException failure) {
            plugin.getSLF4JLogger().error("could not migrate {} to {}", from.getPath(), to.getName(), failure);
         }
      }
   }

   public void load() {
      this.plugin.getDataFolder().mkdirs();
      migrate(this.plugin, this.legacy, this.file);
      if (!this.file.exists() && this.plugin.getResource(this.name) != null) {
         this.plugin.saveResource(this.name, false);
      }

      YamlConfiguration loaded = YamlConfiguration.loadConfiguration(this.file);

      try (InputStream in = this.plugin.getResource(this.name)) {
         if (in != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
         }
      } catch (IOException failure) {
         this.plugin.getSLF4JLogger().warn("could not read the shipped {}", this.name, failure);
      }

      this.config = loaded;
   }

   public FileConfiguration get() {
      return this.config;
   }

   public File file() {
      return this.file;
   }

   public void update(Consumer<YamlConfiguration> edit) {
      synchronized (this.updateLock) {
         YamlConfiguration copy = ConfigManager.copyOf(this.config);
         edit.accept(copy);
         this.config = copy;
         this.plugin.configs().saveAsync(copy, this.file);
      }
   }
}
