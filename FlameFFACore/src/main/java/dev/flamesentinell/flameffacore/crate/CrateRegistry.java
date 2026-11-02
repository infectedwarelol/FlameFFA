package dev.flamesentinell.flameffacore.crate;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.storage.CrateBlockStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class CrateRegistry {
   public static final Material CRATE_BLOCK = Material.CHEST;
   private final FlameFFACore plugin;
   private final Map<String, Crate> crates = new LinkedHashMap<>();
   private final Map<String, String> placements = new LinkedHashMap<>();

   public CrateRegistry(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public static String key(Location loc) {
      return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
   }

   public void load() {
      this.crates.clear();
      ConfigurationSection root = this.plugin.configs().crates().getConfigurationSection("crates");
      if (root == null) {
         this.plugin.getSLF4JLogger().warn("crates.yml has no crates section");
      } else {
         for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s != null) {
               String lower = id.toLowerCase(Locale.ROOT);
               Material candle = Material.matchMaterial(s.getString("voucher", "WHITE_CANDLE"));
               this.crates
                  .put(lower, new Crate(lower, s.getString("display", id), s.getInt("token-price", 0), candle == null ? Material.WHITE_CANDLE : candle));
            }
         }

         this.plugin.getSLF4JLogger().info("loaded {} crate tiers", this.crates.size());
      }
   }

   public Crate get(String id) {
      return id == null ? null : this.crates.get(id.toLowerCase(Locale.ROOT));
   }

   public Map<String, Crate> all() {
      return this.crates;
   }

   public void loadPlacements() {
      this.placements.clear();

      for (CrateBlockStore.Placement p : this.plugin.crateBlocks().loadAll()) {
         if (this.get(p.crateId()) != null) {
            this.placements.put(key(p.location()), p.crateId());
         }
      }

      this.plugin.getSLF4JLogger().info("loaded {} placed crates", this.placements.size());
   }

   public boolean place(String crateId, Location loc, String placedBy) {
      Crate crate = this.get(crateId);
      if (crate != null && loc.getWorld() != null) {
         if (!this.plugin.crateBlocks().add(crate.id(), loc, placedBy)) {
            return false;
         } else {
            this.placements.put(key(loc), crate.id());
            this.materialise(loc);
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean unplace(Location loc) {
      if (loc.getWorld() != null && this.placements.remove(key(loc)) != null) {
         this.plugin.crateBlocks().remove(loc);
         loc.getBlock().setType(Material.AIR);
         return true;
      } else {
         return false;
      }
   }

   public void materialise(Location loc) {
      if (loc.getWorld() != null && loc.getBlock().getType() != CRATE_BLOCK) {
         loc.getBlock().setType(CRATE_BLOCK, false);
      }
   }

   public void materialiseAll() {
      for (Location loc : this.locations()) {
         this.materialise(loc);
      }
   }

   public Crate atBlock(Location block) {
      return block.getWorld() == null ? null : this.get(this.placements.get(key(block)));
   }

   public boolean isPlaced(Location block) {
      return block.getWorld() != null && this.placements.containsKey(key(block));
   }

   public int placedCount() {
      return this.placements.size();
   }

   public int placedCount(String crateId) {
      int n = 0;

      for (String id : this.placements.values()) {
         if (id.equals(crateId)) {
            n++;
         }
      }

      return n;
   }

   public List<Location> locations() {
      List<Location> out = new ArrayList<>();

      for (String raw : this.placements.keySet()) {
         Location loc = this.parse(raw);
         if (loc != null) {
            out.add(loc);
         }
      }

      return out;
   }

   public List<CrateBlockStore.Placement> placements() {
      List<CrateBlockStore.Placement> out = new ArrayList<>();
      this.placements.forEach((raw, id) -> {
         Location loc = this.parse(raw);
         if (loc != null) {
            out.add(new CrateBlockStore.Placement(id, loc, ""));
         }
      });
      return out;
   }

   private Location parse(String raw) {
      String[] parts = raw.split(":");
      if (parts.length != 4) {
         return null;
      } else {
         World world = Bukkit.getWorld(parts[0]);
         if (world == null) {
            return null;
         } else {
            try {
               return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (NumberFormatException var5) {
               return null;
            }
         }
      }
   }
}
