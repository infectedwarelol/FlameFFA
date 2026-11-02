package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Particle.DustTransition;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public abstract class DashWeapon implements CustomWeapon {
   private static final DustOptions GOLD = new DustOptions(Color.fromRGB(255, 198, 58), 1.1F);
   private static final DustTransition FEATHER = new DustTransition(Color.fromRGB(255, 243, 196), Color.fromRGB(255, 166, 41), 1.4F);
   private static final ItemStack PLUME = ItemStack.of(Material.FEATHER);
   private static final ItemStack FLAKE = ItemStack.of(Material.GOLD_NUGGET);

   @Override
   public boolean onAbility(FlameFFACore plugin, Player player, ItemStack held) {
      FileConfiguration cfg = plugin.configs().main();
      double speed = cfg.getDouble(this.path() + ".speed", 2.0);
      int ticks = Math.max(1, cfg.getInt(this.path() + ".boost-ticks", 10));
      double tail = cfg.getDouble(this.path() + ".tail", 0.6);
      Vector dir = player.getLocation().getDirection().normalize();
      if (player.isOnGround() && dir.getY() < 0.12) {
         dir.setY(0.12).normalize();
      }

      plugin.weapons().grantFallImmunity(player);
      this.wingBeat(player, dir);
      World world = player.getWorld();
      world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0F, 1.35F);
      world.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8F, 1.2F);
      world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.35F, 1.6F);
      int[] tick = new int[]{0};
      plugin.tasks().entityTimer(player, task -> {
         if (tick[0] < ticks && player.isOnline() && !player.isDead()) {
            double t = (double)tick[0] / Math.max(1, ticks - 1);
            player.setVelocity(dir.clone().multiply(speed * (1.0 - (1.0 - tail) * t)));
            player.setFallDistance(0.0F);
            this.wake(player, dir);
            tick[0]++;
         } else {
            task.cancel();
         }
      }, null, 0L, 1L);
      return true;
   }

   private void wingBeat(Player player, Vector dir) {
      Location feet = player.getLocation().add(0.0, 0.15, 0.0);
      World world = feet.getWorld();
      Vector flat = dir.clone().setY(0);
      if (flat.lengthSquared() < 0.01) {
         flat = new Vector(0, 0, 1);
      }

      flat.normalize();
      Vector right = flat.clone().crossProduct(new Vector(0, 1, 0)).normalize();
      ThreadLocalRandom rng = ThreadLocalRandom.current();

      for (int side = -1; side <= 1; side += 2) {
         for (int i = 0; i < 16; i++) {
            double spread = i / 15.0;
            Vector v = right.clone()
               .multiply(side * (0.25 + spread * 0.45))
               .add(flat.clone().multiply(-0.15 - spread * 0.25))
               .setY(0.12 + Math.sin(spread * Math.PI) * 0.42);
            v.multiply(0.9 + rng.nextDouble() * 0.4);
            Location at = feet.clone().add(right.clone().multiply(side * 0.25));
            world.spawnParticle(Particle.ITEM, at, 0, v.getX(), v.getY(), v.getZ(), 1.0, PLUME);
            if (i % 2 == 0) {
               world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(v.clone().multiply(0.6)), 1, 0.05, 0.05, 0.05, 0.0, FEATHER);
            }

            if (i % 4 == 0) {
               world.spawnParticle(Particle.ITEM, at, 0, v.getX() * 0.7, v.getY() * 0.9, v.getZ() * 0.7, 1.0, FLAKE);
            }
         }
      }

      world.spawnParticle(Particle.CLOUD, feet, 10, 0.35, 0.05, 0.35, 0.02);
      world.spawnParticle(Particle.FLAME, feet, 14, 0.3, 0.1, 0.3, 0.04);
      world.spawnParticle(Particle.DUST, feet, 16, 0.5, 0.15, 0.5, 0.0, GOLD);
   }

   private void wake(Player player, Vector dir) {
      Location feet = player.getLocation().add(0.0, 0.2, 0.0);
      World world = feet.getWorld();
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      Vector back = dir.clone().multiply(-0.35);

      for (int i = 0; i < 2; i++) {
         Vector v = back.clone().add(new Vector((rng.nextDouble() - 0.5) * 0.3, 0.1 + rng.nextDouble() * 0.2, (rng.nextDouble() - 0.5) * 0.3));
         world.spawnParticle(Particle.ITEM, feet, 0, v.getX(), v.getY(), v.getZ(), 1.0, PLUME);
      }

      world.spawnParticle(Particle.DUST_COLOR_TRANSITION, feet, 3, 0.3, 0.2, 0.3, 0.0, FEATHER);
      world.spawnParticle(Particle.DUST, feet, 2, 0.35, 0.2, 0.35, 0.0, GOLD);
      world.spawnParticle(Particle.CLOUD, feet, 1, 0.2, 0.05, 0.2, 0.01);
      world.spawnParticle(Particle.FLAME, feet, 1, 0.2, 0.1, 0.2, 0.02);
      if (rng.nextInt(3) == 0) {
         world.spawnParticle(Particle.ITEM, feet, 0, back.getX(), 0.25, back.getZ(), 1.0, FLAKE);
      }
   }

   @Override
   public void demo(FlameFFACore plugin, Location at) {
      World world = at.getWorld();
      Vector dir = new Vector(0.0, 0.1, 1.0).normalize();
      Vector right = new Vector(1, 0, 0);
      Location feet = at.clone().add(0.0, 0.15, 0.0);
      ThreadLocalRandom rng = ThreadLocalRandom.current();

      for (int side = -1; side <= 1; side += 2) {
         for (int i = 0; i < 16; i++) {
            double spread = i / 15.0;
            Vector v = right.clone()
               .multiply(side * (0.25 + spread * 0.45))
               .add(new Vector(0.0, 0.0, -0.15 - spread * 0.25))
               .setY(0.12 + Math.sin(spread * Math.PI) * 0.42)
               .multiply(0.9 + rng.nextDouble() * 0.4);
            world.spawnParticle(Particle.ITEM, feet, 0, v.getX(), v.getY(), v.getZ(), 1.0, PLUME);
         }
      }

      world.spawnParticle(Particle.FLAME, feet, 14, 0.3, 0.1, 0.3, 0.04);
      world.spawnParticle(Particle.DUST, feet, 16, 0.5, 0.15, 0.5, 0.0, GOLD);

      for (int i = 1; i <= 12; i++) {
         Location step = feet.clone().add(dir.clone().multiply(i * 1.2));
         world.spawnParticle(Particle.DUST_COLOR_TRANSITION, step, 3, 0.3, 0.2, 0.3, 0.0, FEATHER);
         world.spawnParticle(Particle.ITEM, step, 0, 0.0, 0.2, -0.3, 1.0, PLUME);
      }

      world.playSound(at, Sound.ENTITY_PHANTOM_FLAP, 1.0F, 1.35F);
   }

   @Override
   public void selfTest(FlameFFACore plugin) {
      Location at = plugin.arena().spawn();
      World world = at.getWorld();
      world.spawnParticle(Particle.ITEM, at, 1, 0.0, 0.0, 0.0, 0.0, PLUME);
      world.spawnParticle(Particle.ITEM, at, 1, 0.0, 0.0, 0.0, 0.0, FLAKE);
      world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.0, 0.0, 0.0, 0.0, FEATHER);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, GOLD);
      world.spawnParticle(Particle.CLOUD, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.FLAME, at, 1, 0.0, 0.0, 0.0, 0.0);
   }
}
