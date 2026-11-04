package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
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
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class CometSpear implements CustomWeapon, Listener {
   public static final String ID = "comet-spear";
   private final FlameFFACore plugin;
   private final Map<UUID, Integer> dashing = new ConcurrentHashMap<>();
   private static final DustOptions DEEP = new DustOptions(Color.fromRGB(27, 42, 140), 1.4F);
   private static final DustOptions VIOLET = new DustOptions(Color.fromRGB(123, 63, 214), 1.2F);
   private static final DustOptions CYAN = new DustOptions(Color.fromRGB(95, 212, 255), 0.9F);
   private static final DustTransition FADE = new DustTransition(Color.fromRGB(157, 232, 255), Color.fromRGB(46, 11, 94), 1.6F);
   private static final Color FLASH_BLUE = Color.fromRGB(157, 232, 255);
   private static final DustOptions SHELL = new DustOptions(Color.fromRGB(191, 240, 255), 0.7F);
   private static final DustOptions ARM = new DustOptions(Color.fromRGB(154, 75, 255), 0.8F);
   private static final DustOptions ARM_HOT = new DustOptions(Color.fromRGB(255, 157, 61), 0.7F);
   private static final DustTransition CORE = new DustTransition(Color.fromRGB(255, 255, 255), Color.fromRGB(58, 15, 122), 1.1F);

   public CometSpear(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   @Override
   public String id() {
      return "comet-spear";
   }

   @Override
   public CustomWeapon.Trigger trigger() {
      return CustomWeapon.Trigger.HOLD_CLICK;
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      ItemStack spear = ItemStack.of(Material.NETHERITE_SPEAR);
      spear.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
      spear.addUnsafeEnchantment(Enchantment.LUNGE, 3);
      return spear;
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.NETHERITE_SPEAR) {
         bad.add("comet spear is not a netherite spear");
      }

      if (built.getEnchantmentLevel(Enchantment.SHARPNESS) != 5) {
         bad.add("comet spear is missing Sharpness V");
      }

      if (built.getEnchantmentLevel(Enchantment.LUNGE) != 3) {
         bad.add("comet spear is missing Lunge III");
      }

      return bad;
   }

   @Override
   public boolean chargesWhileHeld(FlameFFACore plugin) {
      return plugin.configs().main().getBoolean(this.path() + ".charge-while-hovering", true);
   }

   @Override
   public int maxHoldTicks(FlameFFACore plugin) {
      return Math.max(5, Math.min(40, plugin.configs().main().getInt(this.path() + ".hover-ticks", 40)));
   }

   @Override
   public boolean onAbility(FlameFFACore plugin, Player player, ItemStack held) {
      if (player.isOnGround() && !player.isInWater()) {
         FileConfiguration cfg = plugin.configs().main();
         double lift = cfg.getDouble(this.path() + ".ground-lift", 2.3);
         int trail = Math.max(0, cfg.getInt(this.path() + ".trail-ticks", 40));
         Location at = player.getLocation();
         player.setVelocity(new Vector(0.0, lift, 0.0));
         player.setFallDistance(0.0F);
         plugin.weapons().grantFallImmunity(player);
         World world = player.getWorld();
         world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH_BLUE);
         world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 80, 0.7, 0.3, 0.7, 0.15);
         world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 90, 1.2, 0.5, 1.2, 0.0, FADE);
         world.spawnParticle(Particle.DUST, at, 50, 1.0, 0.4, 1.0, 0.0, DEEP);
         world.spawnParticle(Particle.DUST, at, 50, 1.0, 0.4, 1.0, 0.0, VIOLET);
         world.spawnParticle(Particle.END_ROD, at, 40, 0.3, 0.3, 0.3, 0.25);
         world.spawnParticle(Particle.REVERSE_PORTAL, at, 60, 0.6, 0.3, 0.6, 0.6);
         world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.4F);
         world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0F, 0.8F);
         world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0F, 0.6F);
         this.flight(plugin, player, null, 0.0, 0, trail);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean onHoldStart(FlameFFACore plugin, Player player, ItemStack held) {
      if (!player.isOnGround() && !player.isInWater() && !player.isFlying() && !player.isGliding() && !player.isClimbing()) {
         double need = plugin.configs().main().getDouble(this.path() + ".min-air-blocks", 3.0);
         if (airBelow(player, need) < need) {
            plugin.cooldownBar().flash(player, plugin.configs().msg("weapon.comet-too-low", "blocks", String.valueOf((int)Math.ceil(need))));
            return false;
         } else {
            player.setGravity(false);
            player.setVelocity(new Vector(0, 0, 0));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.8F);
            this.shimmer(plugin, player, this.maxHoldTicks(plugin));
            return true;
         }
      } else {
         return false;
      }
   }

   private static double airBelow(Player player, double limit) {
      Location feet = player.getLocation();
      World world = feet.getWorld();
      double step = 0.5;

      for (double drop = step; drop <= limit; drop += step) {
         Location at = feet.clone().subtract(0.0, drop, 0.0);
         if (at.getY() <= world.getMinHeight()) {
            return limit;
         }

         if (!at.getBlock().isPassable()) {
            return drop - step;
         }
      }

      return limit;
   }

   private void shimmer(FlameFFACore plugin, Player player, int ticks) {
      int[] left = new int[]{ticks};
      plugin.tasks().entityTimer(player, task -> {
         if (--left[0] >= 0 && player.isOnline() && plugin.weapons().isHolding(player)) {
            player.setVelocity(new Vector(0, 0, 0));
            double angle = (ticks - left[0]) * 0.45;
            Location c = player.getLocation().add(0.0, 1.0, 0.0);
            World world = player.getWorld();

            for (int i = 0; i < 3; i++) {
               double a = angle + i * (Math.PI * 2.0 / 3.0);
               Location at = c.clone().add(Math.cos(a) * 0.9, Math.sin(a * 0.7) * 0.4, Math.sin(a) * 0.9);
               world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, i == 0 ? CYAN : VIOLET);
            }

            world.spawnParticle(Particle.END_ROD, c, 1, 0.5, 0.5, 0.5, 0.0);
         } else {
            task.cancel();
         }
      }, null, 0L, 1L);
   }

   @Override
   public boolean onRelease(FlameFFACore plugin, Player player, boolean fire) {
      player.setGravity(true);
      if (fire && player.isOnline() && !player.isDead()) {
         FileConfiguration cfg = plugin.configs().main();
         double range = cfg.getDouble(this.path() + ".range", 40.0);
         double speed = cfg.getDouble(this.path() + ".speed", 3.6);
         int boost = Math.max(0, Math.min(20, cfg.getInt(this.path() + ".boost-ticks", 5)));
         int trail = Math.max(0, cfg.getInt(this.path() + ".trail-ticks", 40));
         Location eye = player.getEyeLocation();
         Vector dir = eye.getDirection().normalize();
         RayTraceResult hit = player.rayTraceBlocks(range);
         Vector target = hit != null && hit.getHitBlock() != null ? hit.getHitPosition() : eye.toVector().add(dir.multiply(range));
         Vector flight = target.subtract(eye.toVector());
         if (flight.lengthSquared() < 0.01) {
            flight = eye.getDirection();
         }

         Vector heading = flight.normalize();
         player.setVelocity(heading.clone().multiply(speed));
         player.setFallDistance(0.0F);
         plugin.weapons().grantFallImmunity(player);
         World world = player.getWorld();
         Location at = player.getLocation().add(0.0, 1.0, 0.0);
         world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH_BLUE);
         world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 40, 0.5, 0.5, 0.5, 0.0, FADE);
         world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 30, 0.3, 0.3, 0.3, 0.1);
         world.playSound(at, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0F, 0.7F);
         world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0F, 0.6F);
         world.playSound(at, Sound.BLOCK_END_PORTAL_SPAWN, 0.35F, 1.9F);
         ItemStack spear = player.getInventory().getItemInMainHand();
         if (!spear.getType().isAir()) {
            player.setCooldown(spear, 0);
         }

         int window = Math.max(0, cfg.getInt(this.path() + ".finisher.window-ticks", 60));
         if (window > 0) {
            this.dashing.put(player.getUniqueId(), Bukkit.getCurrentTick() + window);
         }

         this.flight(plugin, player, heading, speed, boost, trail);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void selfTest(FlameFFACore plugin) {
      Location at = plugin.arena().spawn();
      World world = at.getWorld();
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH_BLUE);
      world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.0, 0.0, 0.0, 0.0, FADE);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, DEEP);
      world.spawnParticle(Particle.END_ROD, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.REVERSE_PORTAL, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.SONIC_BOOM, at, 1);
      world.spawnParticle(Particle.EXPLOSION, at, 1);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, SHELL);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, ARM);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, ARM_HOT);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, CYAN);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, VIOLET);
      world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.0, 0.0, 0.0, 0.0, CORE);
   }

   @Override
   public void tick(FlameFFACore plugin) {
      if (!this.dashing.isEmpty()) {
         int now = Bukkit.getCurrentTick();
         this.dashing.values().removeIf(until -> now >= until);
      }
   }

   private void flight(FlameFFACore plugin, Player player, Vector heading, double speed, int boostTicks, int trailTicks) {
      int ticks = Math.max(boostTicks, trailTicks);
      if (ticks > 0) {
         int[] i = new int[]{0};
         plugin.tasks().entityTimer(player, task -> {
            boolean landed = i[0] > 2 && player.isOnGround();
            if (i[0] < ticks && player.isOnline() && !player.isDead() && !landed) {
               if (heading != null && i[0] < boostTicks) {
                  player.setVelocity(heading.clone().multiply(speed));
                  player.setFallDistance(0.0F);
               }

               if (i[0] < trailTicks) {
                  Location at = player.getLocation().add(0.0, 0.9, 0.0);
                  World world = player.getWorld();
                  world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 6, 0.25, 0.25, 0.25, 0.02);
                  world.spawnParticle(Particle.DUST, at, 6, 0.35, 0.35, 0.35, 0.0, DEEP);
                  world.spawnParticle(Particle.DUST, at, 4, 0.3, 0.3, 0.3, 0.0, VIOLET);
                  world.spawnParticle(Particle.DUST, at, 3, 0.2, 0.2, 0.2, 0.0, CYAN);
                  world.spawnParticle(Particle.END_ROD, at, 2, 0.15, 0.15, 0.15, 0.0);
                  world.spawnParticle(Particle.REVERSE_PORTAL, at, 4, 0.3, 0.3, 0.3, 0.05);
               }

               i[0]++;
            } else {
               task.cancel();
            }
         }, null, 0L, 1L);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onSpearHit(EntityDamageByEntityEvent event) {
      if (!this.dashing.isEmpty() && event.getDamager() instanceof Player attacker) {
         Integer until = this.dashing.get(attacker.getUniqueId());
         if (until != null && Bukkit.getCurrentTick() < until) {
            if ("comet-spear".equals(this.plugin.weapons().weaponId(attacker.getInventory().getItemInMainHand()))) {
               FileConfiguration cfg = this.plugin.configs().main();
               if (!cfg.getBoolean(this.path() + ".finisher.require-spear-attack", false)
                  || event.getDamageSource() != null && event.getDamageSource().getDamageType() == DamageType.SPEAR) {
                  this.dashing.remove(attacker.getUniqueId());
                  this.raindown(attacker, event.getEntity().getLocation().clone().add(0.0, 1.0, 0.0));
               }
            }
         }
      }
   }

   private void raindown(Player caster, Location impact) {
      FileConfiguration cfg = this.plugin.configs().main();
      int count = Math.max(1, Math.min(64, cfg.getInt(this.path() + ".finisher.comets", 22)));
      int stagger = Math.max(1, cfg.getInt(this.path() + ".finisher.stagger", 2));
      double fall = Math.max(0.2, cfg.getDouble(this.path() + ".finisher.fall-speed", 1.1));
      double spread = Math.max(0.5, cfg.getDouble(this.path() + ".finisher.spread", 5.5));
      double height = Math.max(0.0, cfg.getDouble(this.path() + ".finisher.height", 5.0));
      double damage = Math.max(0.0, cfg.getDouble(this.path() + ".finisher.damage", 4.0));
      double blast = Math.max(0.5, cfg.getDouble(this.path() + ".finisher.blast-radius", 3.0));
      World world = impact.getWorld();
      if (world != null) {
         this.nebula(world, impact, Math.max(0.2, cfg.getDouble(this.path() + ".finisher.nebula-detail", 1.0)));
         this.shockwave(world, impact, spread);
         if (caster != null) {
            this.plugin.cooldownBar().flash(caster, this.plugin.configs().msg("weapon.comet-finisher"));
         }

         double[][] comet = new double[count][6];
         boolean[] done = new boolean[count];
         ThreadLocalRandom rng = ThreadLocalRandom.current();

         for (int i = 0; i < count; i++) {
            boolean near = rng.nextDouble() < 0.55;
            double r = near ? Math.sqrt(rng.nextDouble()) * spread * 0.4 : spread * (0.45 + rng.nextDouble() * 0.55);
            double a = rng.nextDouble() * Math.PI * 2.0;
            double jx = (rng.nextDouble() - 0.5) * spread * 0.25;
            double jz = (rng.nextDouble() - 0.5) * spread * 0.25;
            comet[i][0] = impact.getX() + Math.cos(a) * r + jx;
            comet[i][1] = impact.getY() + height + rng.nextDouble() * 4.0;
            comet[i][2] = impact.getZ() + Math.sin(a) * r + jz;
            comet[i][3] = Math.round(i * stagger * (0.55 + rng.nextDouble() * 0.9));
            comet[i][4] = fall * (0.85 + rng.nextDouble() * 0.4);
            comet[i][5] = blast * (0.8 + rng.nextDouble() * 0.45);
         }

         int[] age = new int[]{0};
         int[] landed = new int[]{0};
         boolean report = caster == null;
         Location cursor = impact.clone();
         this.plugin.tasks().regionTimer(impact, task -> {
            age[0]++;
            boolean any = false;

            for (int i = 0; i < count; i++) {
               if (!done[i]) {
                  any = true;
                  if (!(age[0] < comet[i][3])) {
                     if (age[0] == (int)comet[i][3]) {
                        cursor.set(comet[i][0], comet[i][1], comet[i][2]);
                        world.playSound(cursor, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6F, 0.5F);
                     }

                     double from = comet[i][1];
                     comet[i][1] = comet[i][1] - comet[i][4];
                     cursor.set(comet[i][0], comet[i][1], comet[i][2]);
                     if (!(comet[i][1] <= world.getMinHeight()) && age[0] <= 300) {
                        Double floor = firstSolid(world, comet[i][0], from, comet[i][1], comet[i][2]);
                        if (floor != null) {
                           done[i] = true;
                           landed[0]++;
                           cursor.set(comet[i][0], floor, comet[i][2]);
                           this.burst(world, cursor.clone().add(0.0, 0.5, 0.0), caster, damage, comet[i][5]);
                        } else {
                           this.head(world, cursor);
                        }
                     } else {
                        done[i] = true;
                     }
                  }
               }
            }

            if (!any) {
               task.cancel();
               if (report) {
                  this.plugin.getSLF4JLogger().info("comet test: {} comets, {} landed and burst, {} ticks", new Object[]{count, landed[0], age[0]});
               }
            }
         }, 1L, 1L);
      }
   }

   private void nebula(World world, Location at, double detail) {
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH_BLUE);
      world.spawnParticle(Particle.SONIC_BOOM, at, 1);
      world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.9F, 1.6F);
      world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F);
      world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.55F);
      world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.7F, 1.9F);
      int life = 18;
      double maxRadius = 4.6;
      double golden = Math.PI * (3.0 - Math.sqrt(5.0));
      int shellPoints = (int)Math.max(12.0, 70.0 * detail);
      int armPoints = (int)Math.max(4.0, 11.0 * detail);
      int ringPoints = (int)Math.max(10.0, 44.0 * detail);
      Location p = at.clone();
      int[] age = new int[]{0};
      this.plugin.tasks().regionTimer(p, task -> {
         int t = (int)(age[0]++);
         if (t >= 18) {
            task.cancel();
         } else {
            double k = (double)t / 18.0;
            double ease = 1.0 - Math.pow(1.0 - k, 3.0);
            double radius = 4.6 * ease;
            double spin = k * 2.4;
            int live = (int)(shellPoints * (1.0 - k * 0.55));

            for (int i = 0; i < live; i++) {
               double y = 1.0 - (double)i / Math.max(1, live - 1) * 2.0;
               double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
               double a = golden * i + spin;
               p.set(at.getX() + Math.cos(a) * ring * radius, at.getY() + y * radius * 0.85, at.getZ() + Math.sin(a) * ring * radius);
               world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, SHELL);
            }

            for (int arm = 0; arm < 4; arm++) {
               double base = (Math.PI / 2) * arm - spin * 1.6;

               for (int i = 0; i < armPoints; i++) {
                  double f = (double)i / armPoints;
                  double r = radius * (0.15 + f * 1.05);
                  double a = base + f * 2.3;
                  double lift = Math.sin(f * Math.PI) * radius * 0.28 * (arm % 2 == 0 ? 1 : -1);
                  p.set(at.getX() + Math.cos(a) * r, at.getY() + lift, at.getZ() + Math.sin(a) * r);
                  world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, f < 0.35 ? ARM_HOT : ARM);
                  if ((i & 1) == 0) {
                     world.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1, 0.0, 0.0, 0.0, 0.0, FADE);
                  }
               }
            }

            double rr = radius * 1.35;

            for (int ix = 0; ix < ringPoints; ix++) {
               double a = (Math.PI * 2) / ringPoints * ix + spin * 0.5;
               double tilt = Math.cos(a) * 0.35;
               p.set(at.getX() + Math.cos(a) * rr, at.getY() + tilt, at.getZ() + Math.sin(a) * rr);
               world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, ix % 3 == 0 ? CYAN : VIOLET);
            }

            if (t < 8) {
               world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, (int)(14.0 * detail), 0.3, 0.3, 0.3, 0.0, CORE);
               world.spawnParticle(Particle.END_ROD, at, (int)(6.0 * detail), 0.1, 0.1, 0.1, 0.22);
               world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, (int)(10.0 * detail), 0.25, 0.25, 0.25, 0.06);
            }
         }
      }, 0L, 1L);
   }

   private static Double firstSolid(World world, double x, double from, double to, double z) {
      Location probe = new Location(world, x, from, z);

      for (double y = from; y > to - 1.0; y -= 0.9) {
         probe.setY(Math.max(y, to));
         if (!probe.getBlock().isPassable()) {
            return probe.getY();
         }

         if (probe.getY() <= to) {
            break;
         }
      }

      return null;
   }

   @Override
   public void demo(FlameFFACore plugin, Location at) {
      this.raindown(null, at.clone());
   }

   private void shockwave(World world, Location impact, double spread) {
      double[] r = new double[]{0.4};
      double edge = Math.max(3.0, spread * 0.8);
      this.plugin.tasks().regionTimer(impact, task -> {
         if (r[0] > edge) {
            task.cancel();
         } else {
            int points = (int)Math.max(8.0, r[0] * 10.0);

            for (int i = 0; i < points; i++) {
               double a = (Math.PI * 2) / points * i;
               Location at = impact.clone().add(Math.cos(a) * r[0], 0.0, Math.sin(a) * r[0]);
               world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, i % 3 == 0 ? CYAN : VIOLET);
            }

            r[0] += 0.9;
         }
      }, 0L, 1L);
   }

   private void head(World world, Location at) {
      world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 3, 0.12, 0.12, 0.12, 0.01);
      world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 3, 0.2, 0.3, 0.2, 0.0, FADE);
      world.spawnParticle(Particle.DUST, at, 2, 0.25, 0.3, 0.25, 0.0, DEEP);
      world.spawnParticle(Particle.END_ROD, at, 1, 0.05, 0.05, 0.05, 0.0);
   }

   private void burst(World world, Location at, Player caster, double damage, double blast) {
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH_BLUE);
      world.spawnParticle(Particle.EXPLOSION, at, 1);
      world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 40, 0.5, 0.3, 0.5, 0.15);
      world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 50, 0.8, 0.4, 0.8, 0.0, FADE);
      world.spawnParticle(Particle.DUST, at, 30, 0.7, 0.3, 0.7, 0.0, VIOLET);
      world.spawnParticle(Particle.END_ROD, at, 12, 0.3, 0.3, 0.3, 0.2);
      world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.9F, 1.5F);
      world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 0.7F);
      if (!(damage <= 0.0)) {
         for (LivingEntity hit : world.getNearbyLivingEntities(at, blast, blast, blast)) {
            if (hit instanceof Player victim && !victim.equals(caster) && !victim.isDead() && !this.plugin.safezone().contains(victim.getLocation())) {
               if (caster == null) {
                  victim.damage(damage);
               } else {
                  victim.damage(damage, caster);
               }
            }
         }
      }
   }
}
