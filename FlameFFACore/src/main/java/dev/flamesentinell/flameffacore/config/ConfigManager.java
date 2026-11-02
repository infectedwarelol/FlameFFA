package dev.flamesentinell.flameffacore.config;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Text;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigManager {
   private final Map<File, String> pendingWrites = new ConcurrentHashMap<>();
   private static final String[] FILES = new String[]{"config.yml", "messages.yml", "crates.yml", "ranks.yml", "chat.yml", "cosmetics.yml"};
   private static final Map<Integer, String[]> RETUNED = Map.ofEntries(
      Map.entry(
         27,
         new String[]{
            "messages.yml:hud.title",
            "messages.yml:hud.kills",
            "messages.yml:hud.deaths",
            "messages.yml:hud.kd",
            "messages.yml:hud.streak",
            "messages.yml:hud.playtime",
            "messages.yml:hud.ip",
            "messages.yml:tab.title",
            "messages.yml:tab.subtitle",
            "messages.yml:tab.footer"
         }
      ),
      Map.entry(
         26,
         new String[]{
            "arena.combat-tag-seconds", "weapons.dash-sword.cooldown-seconds", "weapons.dash-spear.cooldown-seconds", "weapons.dash-mace.cooldown-seconds"
         }
      ),
      Map.entry(
         25,
         new String[]{
            "weapons.dash-sword.speed",
            "weapons.dash-sword.boost-ticks",
            "weapons.comet-spear.speed",
            "weapons.tether-trident.pull-speed",
            "weapons.tether-trident.pull-ticks",
            "weapons.tether-trident.pull-lift"
         }
      ),
      Map.entry(24, new String[]{"-weapons.projection-spear.frame-spacing"}),
      Map.entry(23, new String[]{"sweep.seconds"}),
      Map.entry(
         22,
         new String[]{
            "messages.yml:cooldown-bar.entry",
            "messages.yml:cooldown-bar.separator",
            "messages.yml:-cooldown-bar.filled",
            "messages.yml:-cooldown-bar.empty",
            "-combat-hud.cooldown-bar-segments"
         }
      ),
      Map.entry(
         21,
         new String[]{
            "arena.combat-tag-seconds",
            "-weapons.overkill-mace.require-smash",
            "-weapons.tether-trident.self-yank-power",
            "-weapons.tether-trident.pull-power",
            "-weapons.tether-trident.pull-arc",
            "-weapons.tether-trident.pull-max-horizontal",
            "-weapons.tether-trident.pull-max-vertical",
            "-weapons.tether-trident.yank-lift",
            "-weapons.tether-trident.yank-damage"
         }
      ),
      Map.entry(19, new String[]{"weapons.aegis-shield.visual.mode"}),
      Map.entry(18, new String[]{"weapons.overkill-mace.fx.logo-glyph"}),
      Map.entry(17, new String[]{"messages.yml:weapon.overkill-title", "weapons.overkill-mace.fx.aura"}),
      Map.entry(16, new String[]{"messages.yml:weapon.overkill-title"}),
      Map.entry(15, new String[]{"messages.yml:weapon.overkill-stack", "messages.yml:weapon.overkill-title", "messages.yml:weapon.overkill-subtitle"}),
      Map.entry(14, new String[]{"weapons.aegis-shield.visual.sigil-size"}),
      Map.entry(
         13,
         new String[]{
            "weapons.aegis-shield.visual.colour",
            "weapons.aegis-shield.visual.ribs",
            "weapons.aegis-shield.visual.runes",
            "weapons.aegis-shield.visual.rune-size",
            "weapons.aegis-shield.visual.rune-band",
            "weapons.aegis-shield.visual.sigil-mode",
            "weapons.aegis-shield.visual.sigil-size",
            "weapons.dash-sword.speed"
         }
      ),
      Map.entry(12, new String[]{"combat-hud.cooldown-bar"}),
      Map.entry(11, new String[]{"weapons.overkill-mace.blast.radius"}),
      Map.entry(10, new String[]{"messages.yml:arena.killstreak"}),
      Map.entry(9, new String[]{"killstreaks.announce-at", "weapons.comet-spear.min-air-blocks"}),
      Map.entry(8, new String[]{"weapons.phoenix-mace.cooldown-seconds"}),
      Map.entry(
         7,
         new String[]{
            "weapons.comet-spear.finisher.comets",
            "weapons.comet-spear.finisher.spread",
            "weapons.comet-spear.finisher.stagger",
            "weapons.comet-spear.finisher.height",
            "afk.seconds",
            "afk.action",
            "afk.server",
            "afk.warn-seconds"
         }
      ),
      Map.entry(6, new String[]{"tebex.stack-renewals"}),
      Map.entry(5, new String[]{"weapons.comet-spear.hover-ticks", "tab.enabled", "tab.region", "tab.version"}),
      Map.entry(4, new String[]{"weapons.comet-spear.charge-while-hovering"}),
      Map.entry(
         3,
         new String[]{
            "weapons.comet-spear.finisher.window-ticks",
            "weapons.comet-spear.finisher.comets",
            "weapons.comet-spear.finisher.damage",
            "weapons.comet-spear.finisher.blast-radius"
         }
      ),
      Map.entry(
         2,
         new String[]{
            "weapons.comet-spear.speed",
            "weapons.comet-spear.boost-ticks",
            "weapons.comet-spear.cooldown-seconds",
            "weapons.comet-spear.ground-cooldown-seconds",
            "weapons.projection-spear.cooldown-seconds",
            "weapons.dash-potion.speed"
         }
      )
   );
   private final FlameFFACore plugin;
   private final Map<String, FileConfiguration> loaded = new ConcurrentHashMap<>();

   public ConfigManager(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   private int versionWanted() {
      try {
         int var2;
         try (InputStream in = this.plugin.getResource("config.yml")) {
            if (in == null) {
               return 1;
            }

            var2 = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)).getInt("config-version", 1);
         }

         return var2;
      } catch (IOException var6) {
         return 1;
      }
   }

   public void load() {
      this.plugin.getDataFolder().mkdirs();

      for (String name : FILES) {
         File file = new File(this.plugin.getDataFolder(), name);
         if (!file.exists()) {
            this.plugin.saveResource(name, false);
         }

         YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

         try (InputStream in = this.plugin.getResource(name)) {
            if (in != null) {
               YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
               boolean changed = false;
               int have = cfg.getInt("config-version", 1);

               for (String key : shipped.getKeys(true)) {
                  if (!shipped.isConfigurationSection(key) && !cfg.contains(key, true)) {
                     cfg.set(key, shipped.get(key));
                     changed = true;
                  }
               }

               int want = this.versionWanted();

               for (int v = have + 1; v <= want; v++) {
                  for (String entry : RETUNED.getOrDefault(v, new String[0])) {
                     int split = entry.indexOf(58);
                     String owner = split < 0 ? "config.yml" : entry.substring(0, split);
                     String keyx = split < 0 ? entry : entry.substring(split + 1);
                     if (owner.equals(name)) {
                        if (keyx.startsWith("-")) {
                           if (cfg.contains(keyx.substring(1), true)) {
                              cfg.set(keyx.substring(1), null);
                              this.plugin.getSLF4JLogger().info("dropped {} from {}", keyx.substring(1), name);
                              changed = true;
                           }
                        } else if (shipped.contains(keyx)) {
                           cfg.set(keyx, shipped.get(keyx));
                           this.plugin.getSLF4JLogger().info("retuned {} in {}", keyx, name);
                           changed = true;
                        }
                     }
                  }
               }

               if (want > have) {
                  cfg.set("config-version", want);
                  changed = true;
               }

               if (changed) {
                  cfg.save(file);
                  this.plugin.getSLF4JLogger().info("added new settings to {}", name);
               }
            }
         } catch (IOException var22) {
            this.plugin.getSLF4JLogger().warn("could not update {}", name, var22);
         }

         this.loaded.put(name, cfg);
      }

      Text.setPrefix(this.messages().getString("prefix", ""));
   }

   public FileConfiguration get(String name) {
      FileConfiguration cfg = this.loaded.get(name);
      if (cfg == null) {
         throw new IllegalStateException("config not loaded " + name);
      } else {
         return cfg;
      }
   }

   public FileConfiguration main() {
      return this.get("config.yml");
   }

   public FileConfiguration messages() {
      return this.get("messages.yml");
   }

   public FileConfiguration crates() {
      return this.get("crates.yml");
   }

   public FileConfiguration ranks() {
      return this.get("ranks.yml");
   }

   public FileConfiguration chat() {
      return this.get("chat.yml");
   }

   public void saveAsync(FileConfiguration cfg, File file) {
      this.pendingWrites.put(file, cfg.saveToString());
      if (!this.plugin.isEnabled() || this.plugin.tasks() == null || this.plugin.tasks().async(this::flushWrites) == null) {
         this.flushWrites();
      }
   }

   public void saveAsync(String name) {
      this.saveAsync(this.get(name), new File(this.plugin.getDataFolder(), name));
   }

   public synchronized void flushWrites() {
      for (File file : this.pendingWrites.keySet()) {
         String data = this.pendingWrites.remove(file);
         if (data != null) {
            try {
               File parent = file.getParentFile();
               if (parent != null) {
                  parent.mkdirs();
               }

               Path temp = file.toPath().resolveSibling(file.getName() + ".tmp");
               Files.writeString(temp, data, StandardCharsets.UTF_8);
               try {
                  Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
               } catch (AtomicMoveNotSupportedException unsupported) {
                  Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
               }
            } catch (IOException failure) {
               this.plugin.getSLF4JLogger().error("could not save {}", file.getName(), failure);
            }
         }
      }
   }

   public Component msg(String path, String... kv) {
      String raw = this.messages().getString(path);
      return (Component)(raw == null ? Component.text("missing message " + path) : Text.parse(raw, kv));
   }
}
