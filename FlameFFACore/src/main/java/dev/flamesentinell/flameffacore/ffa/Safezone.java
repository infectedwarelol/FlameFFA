package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Bots;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Safezone {
   private final FlameFFACore plugin;
   private String world;
   private int minX;
   private int minY;
   private int minZ;
   private int maxX;
   private int maxY;
   private int maxZ;
   private boolean set;
   private final List<PotionEffect> effects = new ArrayList<>();
   private final Set<UUID> inside = new HashSet<>();

   public Safezone(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void load() {
      FileConfiguration cfg = this.plugin.configs().main();
      ConfigurationSection s = cfg.getConfigurationSection("arena.safezone");
      this.set = s != null && s.contains("x1") && s.contains("x2");
      if (this.set) {
         this.world = s.getString("world", cfg.getString("arena.world", "world"));
         this.minX = Math.min(s.getInt("x1"), s.getInt("x2"));
         this.minY = Math.min(s.getInt("y1"), s.getInt("y2"));
         this.minZ = Math.min(s.getInt("z1"), s.getInt("z2"));
         this.maxX = Math.max(s.getInt("x1"), s.getInt("x2"));
         this.maxY = Math.max(s.getInt("y1"), s.getInt("y2"));
         this.maxZ = Math.max(s.getInt("z1"), s.getInt("z2"));
      }

      this.effects.clear();

      for (String line : cfg.getStringList("arena.safezone-effects")) {
         String[] parts = line.split(":");
         PotionEffectType type = (PotionEffectType)Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
         if (type == null) {
            this.plugin.getSLF4JLogger().warn("unknown safezone effect {}", line);
         } else {
            int level = parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1].trim())) : 1;
            this.effects.add(new PotionEffect(type, -1, level - 1, false, false, true));
         }
      }
   }

   public boolean isSet() {
      return this.set;
   }

   public boolean contains(Location loc) {
      return loc.getWorld() != null && this.contains(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
   }

   public boolean contains(World w, int x, int y, int z) {
      return this.set && w.getName().equals(this.world)
         ? x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ
         : false;
   }

   public String describe() {
      return !this.set ? "not set" : this.world + " " + this.minX + " " + this.minY + " " + this.minZ + " to " + this.maxX + " " + this.maxY + " " + this.maxZ;
   }

   public boolean define(World w, int x1, int y1, int z1, int x2, int y2, int z2) {
      FileConfiguration cfg = this.plugin.configs().main();
      cfg.set("arena.safezone.world", w.getName());
      cfg.set("arena.safezone.x1", x1);
      cfg.set("arena.safezone.y1", y1);
      cfg.set("arena.safezone.z1", z1);
      cfg.set("arena.safezone.x2", x2);
      cfg.set("arena.safezone.y2", y2);
      cfg.set("arena.safezone.z2", z2);
      if (!this.save(cfg)) {
         return false;
      } else {
         this.load();
         return true;
      }
   }

   public boolean clear() {
      FileConfiguration cfg = this.plugin.configs().main();
      cfg.set("arena.safezone", null);
      if (!this.save(cfg)) {
         return false;
      } else {
         for (UUID uuid : new ArrayList<>(this.inside)) {
            Player p = this.plugin.getServer().getPlayer(uuid);
            if (p != null) {
               this.strip(p);
            }
         }

         this.inside.clear();
         this.load();
         return true;
      }
   }

   private boolean save(FileConfiguration cfg) {
      this.plugin.configs().saveAsync(cfg, new File(this.plugin.getDataFolder(), "config.yml"));
      return true;
   }

   public void tick(Player player) {
      if (!Bots.is(player)) {
         UUID uuid = player.getUniqueId();
         if (this.contains(player.getWorld(), (int)Math.floor(player.getX()), (int)Math.floor(player.getY()), (int)Math.floor(player.getZ()))) {
            this.inside.add(uuid);

            for (PotionEffect effect : this.effects) {
               if (!player.hasPotionEffect(effect.getType())) {
                  player.addPotionEffect(effect);
               }
            }
         } else if (this.inside.remove(uuid)) {
            this.strip(player);
         }
      }
   }

   public void forget(UUID uuid) {
      this.inside.remove(uuid);
   }

   private void strip(Player player) {
      for (PotionEffect effect : this.effects) {
         player.removePotionEffect(effect.getType());
      }
   }
}
