package dev.flamesentinell.flameffacore.cosmetic;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Bots;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Particle.DustTransition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class CosmeticService implements Listener {
   public static final String KILL = "kill";
   public static final String AURA = "aura";
   private final FlameFFACore plugin;
   private final Map<String, List<CosmeticService.Cosmetic>> catalogue = new LinkedHashMap<>();
   private final Map<UUID, Map<String, String>> chosen = new ConcurrentHashMap<>();
   private boolean enabled = true;
   private int auraStreak = 5;

   public CosmeticService(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void load() {
      FileConfiguration cfg = this.plugin.configs().get("cosmetics.yml");
      this.enabled = cfg.getBoolean("enabled", true);
      this.auraStreak = Math.max(1, cfg.getInt("aura-from-streak", 5));
      this.catalogue.clear();

      for (String slot : List.of("kill", "aura")) {
         List<CosmeticService.Cosmetic> list = new ArrayList<>();
         ConfigurationSection section = cfg.getConfigurationSection(slot);
         if (section != null) {
            for (String id : section.getKeys(false)) {
               ConfigurationSection node = section.getConfigurationSection(id);
               if (node != null && node.getBoolean("enabled", true)) {
                  list.add(
                     new CosmeticService.Cosmetic(
                        id,
                        slot,
                        node.getString("display", id),
                        node.getString("permission", "flameffacore.cosmetic." + slot + "." + id),
                        colour(node.getString("primary", "#ffffff")),
                        colour(node.getString("secondary", "#888888"))
                     )
                  );
               }
            }
         }

         this.catalogue.put(slot, list);
      }

      int total = this.catalogue.values().stream().mapToInt(List::size).sum();
      this.plugin.getSLF4JLogger().info("loaded {} cosmetics across {} slots", total, this.catalogue.size());
   }

   private static Color colour(String hex) {
      try {
         return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
      } catch (RuntimeException var2) {
         return Color.WHITE;
      }
   }

   public List<CosmeticService.Cosmetic> catalogue(String slot) {
      return this.catalogue.getOrDefault(slot, List.of());
   }

   public CosmeticService.Cosmetic find(String slot, String id) {
      for (CosmeticService.Cosmetic cosmetic : this.catalogue(slot)) {
         if (cosmetic.id().equalsIgnoreCase(id)) {
            return cosmetic;
         }
      }

      return null;
   }

   public boolean owns(Player player, CosmeticService.Cosmetic cosmetic) {
      return cosmetic == null || cosmetic.permission().isBlank() || player.hasPermission(cosmetic.permission());
   }

   public String picked(Player player, String slot) {
      Map<String, String> mine = this.chosen.get(player.getUniqueId());
      return mine == null ? null : mine.get(slot);
   }

   public void pick(Player player, String slot, String id) {
      UUID uuid = player.getUniqueId();
      Map<String, String> mine = this.chosen.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
      if (id == null) {
         mine.remove(slot);
      } else {
         mine.put(slot, id);
      }

      this.plugin.async(() -> this.plugin.cosmeticStore().save(uuid, slot, id));
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (!Bots.is(event.getPlayer())) {
         UUID uuid = event.getPlayer().getUniqueId();
         this.plugin.async(() -> {
            Map<String, String> saved = this.plugin.cosmeticStore().load(uuid);
            this.chosen.put(uuid, new ConcurrentHashMap<>(saved));
         });
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.chosen.remove(event.getPlayer().getUniqueId());
   }

   public void onKill(Player killer, Location at) {
      if (this.enabled && killer != null && at != null && at.getWorld() != null) {
         CosmeticService.Cosmetic cosmetic = this.find("kill", this.picked(killer, "kill"));
         if (cosmetic != null && this.owns(killer, cosmetic)) {
            Location spot = at.clone().add(0.0, 1.0, 0.0);
            String var5 = cosmetic.id();
            switch (var5) {
               case "lightning":
                  this.lightning(spot, cosmetic);
                  break;
               case "fireworks":
                  this.fireworks(spot, cosmetic);
                  break;
               case "souls":
                  this.souls(killer, spot, cosmetic);
                  break;
               case "inferno":
                  this.inferno(spot, cosmetic);
                  break;
               case "nebula":
                  this.nebula(spot, cosmetic);
                  break;
               default:
                  this.ring(spot, cosmetic);
            }
         }
      }
   }

   public void demo(Player viewer, Location at, CosmeticService.Cosmetic cosmetic) {
      Location spot = at.clone().add(0.0, 1.0, 0.0);
      String var5 = cosmetic.id();
      switch (var5) {
         case "lightning":
            this.lightning(spot, cosmetic);
            break;
         case "fireworks":
            this.fireworks(spot, cosmetic);
            break;
         case "souls":
            this.souls(viewer, spot, cosmetic);
            break;
         case "inferno":
            this.inferno(spot, cosmetic);
            break;
         case "nebula":
            this.nebula(spot, cosmetic);
            break;
         default:
            this.ring(spot, cosmetic);
      }
   }

   private void animate(int ticks, IntConsumer body) {
      BukkitTask[] handle = new BukkitTask[1];
      int[] age = new int[]{0};
      handle[0] = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         int t = (int)(age[0]++);
         if (t >= ticks) {
            handle[0].cancel();
         } else {
            body.accept(t);
         }
      }, 0L, 1L);
   }

   private void ring(Location at, CosmeticService.Cosmetic cosmetic) {
      DustTransition dust = new DustTransition(cosmetic.primary(), cosmetic.secondary(), 1.4F);
      at.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 80, 0.6, 0.7, 0.6, 0.0, dust);
      at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 1.4F);
   }

   private void lightning(Location at, CosmeticService.Cosmetic cosmetic) {
      World world = at.getWorld();
      world.strikeLightningEffect(at);
      world.spawnParticle(Particle.ELECTRIC_SPARK, at, 60, 0.5, 1.0, 0.5, 0.4);
   }

   private void nebula(Location at, CosmeticService.Cosmetic cosmetic) {
      World world = at.getWorld();
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, cosmetic.primary());
      world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.5F);
      world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.9F, 1.9F);
      world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F);
      int life = 26;
      double max = 5.2;
      double golden = Math.PI * (3.0 - Math.sqrt(5.0));
      DustOptions shellDust = new DustOptions(cosmetic.primary(), 0.75F);
      DustOptions armDust = new DustOptions(cosmetic.secondary(), 0.85F);
      DustTransition core = new DustTransition(Color.WHITE, cosmetic.secondary(), 1.2F);
      Location p = at.clone();
      this.animate(26, t -> {
         double k = t / 26.0;
         double ease = 1.0 - Math.pow(1.0 - k, 3.0);
         double radius = 5.2 * ease;
         double spin = k * 2.6;
         int live = (int)(86.0 * (1.0 - k * 0.55));

         for (int i = 0; i < live; i++) {
            double y = 1.0 - (double)i / Math.max(1, live - 1) * 2.0;
            double band = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double a = golden * i + spin;
            p.set(at.getX() + Math.cos(a) * band * radius, at.getY() + y * radius * 0.8, at.getZ() + Math.sin(a) * band * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, shellDust);
         }

         for (int arm = 0; arm < 4; arm++) {
            double base = (Math.PI / 2) * arm - spin * 1.7;

            for (int i = 0; i < 12; i++) {
               double f = i / 12.0;
               double r = radius * (0.12 + f * 1.1);
               double a = base + f * 2.5;
               double lift = Math.sin(f * Math.PI) * radius * 0.3 * (arm % 2 == 0 ? 1 : -1);
               p.set(at.getX() + Math.cos(a) * r, at.getY() + lift, at.getZ() + Math.sin(a) * r);
               world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, armDust);
               if ((i & 1) == 0) {
                  world.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1, 0.0, 0.0, 0.0, 0.0, core);
               }
            }
         }

         double rr = radius * 1.4;

         for (int ix = 0; ix < 46; ix++) {
            double a = 0.13659098493868665 * ix + spin * 0.5;
            p.set(at.getX() + Math.cos(a) * rr, at.getY() + Math.cos(a) * 0.4, at.getZ() + Math.sin(a) * rr);
            world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, ix % 3 == 0 ? shellDust : armDust);
         }

         if (t < 9) {
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 16, 0.35, 0.35, 0.35, 0.0, core);
            world.spawnParticle(Particle.END_ROD, at, 7, 0.1, 0.1, 0.1, 0.25);
         }
      });
   }

   private void souls(Player killer, Location at, CosmeticService.Cosmetic cosmetic) {
      World world = at.getWorld();
      world.playSound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.1F, 0.8F);
      world.playSound(at, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.2F, 0.7F);
      world.spawnParticle(Particle.SCULK_SOUL, at, 30, 0.3, 0.5, 0.3, 0.06);
      Location floor = at.clone().subtract(0.0, 0.9, 0.0);
      DustOptions veinDust = new DustOptions(cosmetic.secondary(), 1.1F);
      Location p = at.clone();
      this.animate(18, t -> {
         double r = 0.5 + t * 0.42;
         int points = (int)Math.max(10.0, r * 9.0);

         for (int ix = 0; ix < points; ix++) {
            double ax = (Math.PI * 2) / points * ix + t * 0.11;
            double wobble = r + Math.sin(ax * 3.0 + t * 0.4) * 0.25;
            p.set(floor.getX() + Math.cos(ax) * wobble, floor.getY() + 0.08, floor.getZ() + Math.sin(ax) * wobble);
            world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, veinDust);
            if (ix % 4 == 0) {
               world.spawnParticle(Particle.SCULK_CHARGE_POP, p, 1, 0.05, 0.05, 0.05, 0.0);
            }
         }

         if (t % 4 == 0) {
            world.playSound(floor, Sound.BLOCK_SCULK_SPREAD, 0.7F, 0.6F + t * 0.02F);
         }
      });
      int rise = 14;
      int travel = 22;
      List<Location> souls = new ArrayList<>();

      for (int i = 0; i < 7; i++) {
         double a = (Math.PI * 2.0 / 7.0) * i;
         souls.add(at.clone().add(Math.cos(a) * 0.55, 0.0, Math.sin(a) * 0.55));
      }

      this.animate(36, t -> {
         for (int ix = 0; ix < souls.size(); ix++) {
            Location soul = souls.get(ix);
            if (t < 14) {
               soul.add(0.0, 0.11, 0.0);
            } else if (killer != null && killer.isOnline()) {
               Location target = killer.getLocation().add(0.0, 1.1, 0.0);
               Vector step = target.toVector().subtract(soul.toVector()).multiply(0.22);
               soul.add(step);
            }

            world.spawnParticle(Particle.SOUL_FIRE_FLAME, soul, 2, 0.05, 0.05, 0.05, 0.005);
            world.spawnParticle(Particle.SCULK_SOUL, soul, 1, 0.06, 0.06, 0.06, 0.01);
         }

         if (t == 14) {
            world.playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 1.2F, 0.5F);
         }

         if (t == 35 && killer != null && killer.isOnline()) {
            Location chest = killer.getLocation().add(0.0, 1.1, 0.0);
            world.spawnParticle(Particle.SCULK_SOUL, chest, 26, 0.3, 0.4, 0.3, 0.05);
            world.playSound(chest, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.0F, 1.4F);
         }
      });
   }

   private void inferno(Location at, CosmeticService.Cosmetic cosmetic) {
      World world = at.getWorld();
      world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.2F, 0.7F);
      world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.6F);
      world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.9F, 0.6F);
      Location base = at.clone().subtract(0.0, 0.9, 0.0);
      DustOptions emberDust = new DustOptions(cosmetic.primary(), 1.0F);
      Location p = at.clone();
      this.animate(30, t -> {
         double climb = t * 0.19;
         double r = Math.max(0.15, 1.5 - climb * 0.4);

         for (int strand = 0; strand < 3; strand++) {
            double a = t * 0.55 + (Math.PI * 2.0 / 3.0) * strand;
            p.set(base.getX() + Math.cos(a) * r, base.getY() + climb, base.getZ() + Math.sin(a) * r);
            world.spawnParticle(Particle.FLAME, p, 3, 0.08, 0.08, 0.08, 0.01);
            world.spawnParticle(Particle.SMALL_FLAME, p, 2, 0.1, 0.1, 0.1, 0.005);
            world.spawnParticle(Particle.DUST, p, 1, 0.1, 0.1, 0.1, 0.0, emberDust);
         }

         if (t < 12) {
            double ring = 0.4 + t * 0.2;

            for (int i = 0; i < 14; i++) {
               double a = (Math.PI / 7) * i + t * 0.2;
               p.set(base.getX() + Math.cos(a) * ring, base.getY() + 0.1, base.getZ() + Math.sin(a) * ring);
               world.spawnParticle(Particle.FLAME, p, 1, 0.0, 0.0, 0.0, 0.02);
               if (i % 5 == 0) {
                  world.spawnParticle(Particle.LAVA, p, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }

         if (t == 20) {
            world.spawnParticle(Particle.FLASH, base.clone().add(0.0, 3.6, 0.0), 1, 0.0, 0.0, 0.0, 0.0, cosmetic.primary());
            world.playSound(base, Sound.ENTITY_BLAZE_DEATH, 0.9F, 1.4F);
         }
      });
   }

   private void fireworks(Location at, CosmeticService.Cosmetic cosmetic) {
      World world = at.getWorld();

      for (int i = 0; i < 3; i++) {
         int delay = i * 6;
         double offX = (i - 1) * 0.9;
         Bukkit.getScheduler()
            .runTaskLater(
               this.plugin,
               () -> {
                  Location spot = at.clone().add(offX, 0.6 + Math.random(), (Math.random() - 0.5) * 1.2);
                  Firework rocket = (Firework)world.spawn(
                     spot,
                     Firework.class,
                     f -> {
                        FireworkMeta meta = f.getFireworkMeta();
                        meta.addEffect(
                           FireworkEffect.builder()
                              .withColor(cosmetic.primary())
                              .withFade(cosmetic.secondary())
                              .with(Type.BALL_LARGE)
                              .trail(true)
                              .flicker(true)
                              .build()
                        );
                        meta.setPower(0);
                        f.setFireworkMeta(meta);
                        f.setSilent(false);
                     }
                  );
                  rocket.detonate();
               },
               delay
            );
      }
   }

   public void tick(Player player) {
      if (this.enabled) {
         CosmeticService.Cosmetic aura = this.find("aura", this.picked(player, "aura"));
         if (aura != null && this.owns(player, aura) && this.plugin.arena().streak(player.getUniqueId()) >= this.auraStreak) {
            Location centre = player.getLocation().add(0.0, 1.1, 0.0);
            DustOptions dust = new DustOptions(aura.primary(), 1.0F);
            double spin = Bukkit.getCurrentTick() % 80 / 80.0 * Math.PI * 2.0;

            for (int i = 0; i < 6; i++) {
               double a = spin + (Math.PI / 3) * i;
               player.getWorld().spawnParticle(Particle.DUST, centre.clone().add(Math.cos(a) * 0.9, 0.0, Math.sin(a) * 0.9), 1, 0.0, 0.0, 0.0, 0.0, dust);
            }
         }
      }
   }

   public boolean enabled() {
      return this.enabled;
   }

   public int auraStreak() {
      return this.auraStreak;
   }

   public record Cosmetic(String id, String slot, String display, String permission, Color primary, Color secondary) {
   }
}
