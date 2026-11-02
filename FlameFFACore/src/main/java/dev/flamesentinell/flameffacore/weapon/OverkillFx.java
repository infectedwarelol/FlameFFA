package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Text;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class OverkillFx {
   private static final DustOptions ORANGE = new DustOptions(Color.fromRGB(255, 90, 26), 0.9F);
   private static final Color FLASH = Color.fromRGB(196, 18, 8);
   private final FlameFFACore plugin;
   private final String path;
   private final Map<String, Component> glyphLines = new HashMap<>();

   public OverkillFx(FlameFFACore plugin, String path) {
      this.plugin = plugin;
      this.path = path + ".fx";
   }

   private Particle omen() {
      try {
         return Particle.valueOf(this.plugin.configs().main().getString(this.path + ".omen-particle", "RAID_OMEN").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var2) {
         return Particle.RAID_OMEN;
      }
   }

   private double num(String key, double fallback) {
      return this.plugin.configs().main().getDouble(this.path + "." + key, fallback);
   }

   private int whole(String key, int fallback) {
      return this.plugin.configs().main().getInt(this.path + "." + key, fallback);
   }

   private Component meter(int stacks, int need) {
      FileConfiguration msg = this.plugin.configs().get("messages.yml");
      String lit = msg.getString("weapon.overkill-meter-lit", "<#ff3b00>✦");
      String dark = msg.getString("weapon.overkill-meter-dark", "<#4a1010>✧");
      Component out = Component.empty();

      for (int i = 0; i < need; i++) {
         out = out.append(Text.parse(i < stacks ? lit : dark));
         if (i + 1 < need) {
            out = out.append(Component.text(" "));
         }
      }

      return out;
   }

   private boolean glyphs() {
      return this.plugin.configs().main().getBoolean(this.path + ".logo-glyph", true);
   }

   private Component glyphLine(char glyph) {
      String template = this.plugin.configs().get("messages.yml").getString("weapon.overkill-bar-glyph", "<glyph>");
      String key = template + glyph;
      Component out = this.glyphLines.get(key);
      if (out == null) {
         if (this.glyphLines.size() > 256) {
            this.glyphLines.clear();
         }

         out = this.plugin.configs().msg("weapon.overkill-bar-glyph", "glyph", String.valueOf(glyph));
         this.glyphLines.put(key, out);
      }

      return out;
   }

   private Component bar(int stacks, int need) {
      int state = Math.max(0, Math.min(5, (int)Math.round(5.0 * stacks / Math.max(1, need))));
      return this.glyphLine((char)(57360 + state));
   }

   private Component litBar(double fraction) {
      int level = (int)Math.round(30.0 * Math.max(0.0, Math.min(1.0, fraction)));
      return this.glyphLine((char)(57376 + level));
   }

   public void holdLit(Player player, double fraction, int secondsLeft) {
      if (this.glyphs()) {
         this.plugin.cooldownBar().pin(player, this.litBar(fraction), 12);
      } else {
         FileConfiguration msg = this.plugin.configs().get("messages.yml");
         this.plugin
            .cooldownBar()
            .pin(
               player,
               Text.parse(msg.getString("weapon.overkill-lit", "<meter> <white><seconds>s"), "meter", this.meter(5, 5), "seconds", String.valueOf(secondsLeft)),
               12
            );
      }
   }

   public Component meterFor(int stacks, int need) {
      return this.glyphs()
         ? this.bar(stacks, need)
         : Text.parse(
            this.plugin.configs().get("messages.yml").getString("weapon.overkill-stack", "<meter>"),
            "meter",
            this.meter(stacks, need),
            "stacks",
            String.valueOf(stacks),
            "needed",
            String.valueOf(need)
         );
   }

   public void hold(Player player, int stacks, int need) {
      this.plugin.cooldownBar().pin(player, this.meterFor(stacks, need), 12);
   }

   public void release(Player player) {
      this.plugin.cooldownBar().unpin(player);
   }

   public void stack(Player attacker, Player victim, int stacks, int need) {
      FileConfiguration msg = this.plugin.configs().get("messages.yml");
      this.hold(attacker, stacks, need);
      if (this.glyphs()) {
         this.pulse(attacker, victim, stacks, need);
         attacker.playSound(attacker, Sound.BLOCK_FIRE_AMBIENT, 0.7F, 0.7F + stacks * 0.14F);
         attacker.playSound(attacker, Sound.ITEM_FLINTANDSTEEL_USE, 0.5F, 0.6F + stacks * 0.1F);
      } else {
         this.pulse(attacker, victim, stacks, need);
         attacker.playSound(attacker, Sound.BLOCK_FIRE_AMBIENT, 0.7F, 0.7F + stacks * 0.14F);
         attacker.playSound(attacker, Sound.ITEM_FLINTANDSTEEL_USE, 0.5F, 0.6F + stacks * 0.1F);
      }
   }

   private void pulse(Player attacker, Player victim, int stacks, int need) {
      World world = victim.getWorld();
      Location at = victim.getLocation().add(0.0, 1.0, 0.0);
      double heat = (double)stacks / Math.max(1, need);
      int rings = 2 + (int)Math.round(heat * 2.0);

      for (int r = 0; r < rings; r++) {
         double radius = 0.6 + r * 0.55;
         int points = 12 + r * 6;
         Particle wisp = this.omen();
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            for (int i = 0; i < points; i++) {
               double a = i * Math.PI * 2.0 / points;
               Location p = at.clone().add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
               if (i % 5 == 0 && heat > 0.5) {
                  world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, ORANGE);
               } else {
                  world.spawnParticle(wisp, p, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }, r * 2L);
      }

      Location hand = attacker.getEyeLocation();
      Vector look = hand.getDirection();
      Vector right = look.clone().setY(0).crossProduct(new Vector(0, 1, 0)).normalize();
      Location fist = hand.clone().add(look.clone().multiply(0.45)).subtract(right.clone().multiply(0.35)).subtract(0.0, 0.35, 0.0);
      world.spawnParticle(this.omen(), fist, 3 + stacks * 2, 0.12, 0.12, 0.12, 0.01);
      world.spawnParticle(Particle.SMOKE, fist, 1 + stacks, 0.08, 0.08, 0.08, 0.01);
      if (heat >= 0.6) {
         world.spawnParticle(Particle.FLAME, fist, 2, 0.1, 0.1, 0.1, 0.02);
      }
   }

   public void ignite(Player player, int seconds) {
      this.holdLit(player, 1.0, seconds);
      World world = player.getWorld();
      Location at = player.getLocation().add(0.0, 1.0, 0.0);
      world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.5F);
      world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8F, 0.6F);
      world.playSound(at, Sound.ENTITY_WITHER_AMBIENT, 0.35F, 1.6F);
      world.spawnParticle(Particle.LARGE_SMOKE, at, 40, 0.35, 0.9, 0.35, 0.02);
      world.spawnParticle(this.omen(), at, 70, 0.6, 0.9, 0.6, 0.03);
      world.spawnParticle(Particle.FLAME, at, 14, 0.3, 0.5, 0.3, 0.09);
      world.spawnParticle(Particle.LAVA, at, 6, 0.3, 0.3, 0.3, 0.0);
   }

   public void aura(Player player, int ticksLeft) {
      if (this.plugin.configs().main().getBoolean(this.path + ".aura", true)) {
         World world = player.getWorld();
         Location body = player.getLocation().add(0.0, 1.0, 0.0);
         double density = this.num("aura-density", 1.0);
         int omen = Math.max(1, (int)Math.round(this.num("aura-omen", 8.0) * density));
         world.spawnParticle(this.omen(), body, omen, 0.45, 0.7, 0.45, 0.02);
         if (ThreadLocalRandom.current().nextInt(2) == 0) {
            world.spawnParticle(Particle.LARGE_SMOKE, body.clone().add(0.0, 0.5, 0.0), 1, 0.25, 0.3, 0.25, 0.004);
         }
      }
   }

   public void fade(Player player) {
      World world = player.getWorld();
      Location at = player.getLocation().add(0.0, 1.0, 0.0);
      world.spawnParticle(Particle.LARGE_SMOKE, at, 18, 0.3, 0.6, 0.3, 0.01);
      world.spawnParticle(this.omen(), at, 14, 0.4, 0.6, 0.4, 0.01);
      world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.8F, 0.9F);
   }

   public void nova(Location centre, double radius) {
      World world = centre.getWorld();
      Location core = centre.clone().add(0.0, 1.0, 0.0);
      int frames = Math.max(2, this.whole("nova-ticks", 5));
      int shell = Math.max(24, this.whole("nova-shell-points", 90));
      double floor = centre.getY() - 0.2;
      world.spawnParticle(Particle.FLASH, core, 1, 0.0, 0.0, 0.0, 0.0, FLASH);
      world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 1.3F, 0.55F);
      world.playSound(core, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2F, 0.6F);
      world.playSound(core, Sound.ENTITY_BLAZE_DEATH, 0.9F, 0.45F);
      world.playSound(core, Sound.ENTITY_WITHER_AMBIENT, 0.5F, 1.3F);
      world.spawnParticle(Particle.LARGE_SMOKE, core, 30, 0.6, 0.6, 0.6, 0.08);
      Particle wisp = this.omen();

      for (int f = 0; f < frames; f++) {
         double r = radius * (f + 1) / frames;
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            for (int i = 0; i < shell; i++) {
               double y = 1.0 - 2.0 * (i + 0.5) / shell;
               double ring = Math.sqrt(1.0 - y * y);
               double a = i * 2.399963;
               Location p = core.clone().add(Math.cos(a) * ring * r, y * r, Math.sin(a) * ring * r);
               if (!(p.getY() < floor)) {
                  if (i % 9 == 0) {
                     world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, ORANGE);
                  } else {
                     world.spawnParticle(wisp, p, 1, 0.0, 0.0, 0.0, 0.0);
                  }
               }
            }

            int edge = (int)Math.max(24.0, r * 8.0);

            for (int ix = 0; ix < edge; ix++) {
               double a = ix * Math.PI * 2.0 / edge;
               Location p = centre.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r);
               world.spawnParticle(wisp, p, 1, 0.0, 0.0, 0.0, 0.0);
            }
         }, f);
      }

      int burn = Math.max(4, this.whole("edge-burn-ticks", 14));
      int points = (int)Math.max(32.0, radius * 7.0);

      for (int t = frames; t < frames + burn; t += 2) {
         int age = t - frames;
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            for (int i = 0; i < points; i++) {
               double a = i * Math.PI * 2.0 / points + age * 0.05;
               Location p = centre.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius);
               world.spawnParticle(Particle.FLAME, p, 0, 0.0, 0.06 + ThreadLocalRandom.current().nextDouble() * 0.05, 0.0, 1.0);
               if (i % 4 == 0) {
                  world.spawnParticle(Particle.SMOKE, p, 0, 0.0, 0.05, 0.0, 1.0);
               }

               if (i % 6 == 0) {
                  world.spawnParticle(wisp, p, 1, 0.1, 0.2, 0.1, 0.0);
               }
            }
         }, t);
      }
   }

   public void selfTest(Location at) {
      World world = at.getWorld();
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, ORANGE);
      world.spawnParticle(this.omen(), at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH);
      world.spawnParticle(Particle.LARGE_SMOKE, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.SMOKE, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.SMALL_FLAME, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.FLAME, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.LAVA, at, 1, 0.0, 0.0, 0.0, 0.0);
   }
}
