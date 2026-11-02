package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TetherChain {
   public static final String TAG = "flameffacore_tether";
   private static final DustOptions PALE = new DustOptions(Color.fromRGB(234, 247, 255), 0.7F);
   private static final DustOptions BRIGHT = new DustOptions(Color.fromRGB(255, 255, 255), 1.1F);
   private final FlameFFACore plugin;
   private final String path;
   private final Map<UUID, TetherChain.Live> live = new HashMap<>();

   public TetherChain(FlameFFACore plugin, String path) {
      this.plugin = plugin;
      this.path = path + ".chain";
   }

   private double num(String key, double fallback) {
      return this.plugin.configs().main().getDouble(this.path + "." + key, fallback);
   }

   private String mode() {
      return this.plugin.configs().main().getString(this.path + ".mode", "model").toLowerCase(Locale.ROOT);
   }

   private static Vector along(Vector a, Vector b, double sag, double t) {
      Vector p = a.clone().multiply(1.0 - t).add(b.clone().multiply(t));
      p.setY(p.getY() - sag * 4.0 * t * (1.0 - t));
      return p;
   }

   private double sag(TetherChain.Live ward, double distance) {
      return Bukkit.getCurrentTick() < ward.tautUntil ? 0.0 : Math.min(this.num("max-sag", 2.5), distance * this.num("sag-per-block", 0.06));
   }

   public void draw(Player caster, Location from, Location to) {
      TetherChain.Live ward = this.live.computeIfAbsent(caster.getUniqueId(), k -> new TetherChain.Live());
      World world = caster.getWorld();
      Vector a = from.toVector();
      Vector b = to.toVector();
      double distance = a.distance(b);
      String mode = this.mode();
      if (mode.equals("dust")) {
         this.dust(world, a, b, this.sag(ward, distance), distance);
      } else {
         this.links(ward, world, a, b, this.sag(ward, distance), distance, mode.equals("block"));
      }

      this.motes(ward, world, a, b, this.sag(ward, distance), distance);
   }

   private void dust(World world, Vector a, Vector b, double sag, double distance) {
      int points = (int)Math.min(90.0, Math.max(4.0, distance * 2.0));

      for (int i = 0; i <= points; i++) {
         Vector p = along(a, b, sag, (double)i / points);
         world.spawnParticle(Particle.DUST, p.toLocation(world), 1, 0.0, 0.0, 0.0, 0.0, PALE);
      }
   }

   private void links(TetherChain.Live ward, World world, Vector a, Vector b, double sag, double distance, boolean block) {
      double span = block ? 1.0 : this.num("span-blocks", 3.0);
      int cap = Math.max(1, (int)this.num("max-links", block ? 40.0 : 30.0));
      int count = Math.max(1, Math.min(cap, (int)Math.ceil(distance / span)));
      float thick = (float)this.num("thickness", 1.0);

      for (int i = 0; i < count; i++) {
         Vector p0 = along(a, b, sag, (double)i / count);
         Vector p1 = along(a, b, sag, (double)(i + 1) / count);
         Vector mid = p0.clone().add(p1).multiply(0.5);
         Vector dir = p1.clone().subtract(p0);
         double length = dir.length();
         if (length < 0.01) {
            dir = new Vector(0, 1, 0);
            length = 0.01;
         }

         dir.normalize();
         Quaternionf turn = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float)dir.getX(), (float)dir.getY(), (float)dir.getZ());
         float sy = (float)(length / span);
         Vector3f centre = new Vector3f(0.0F, block ? 0.5F * sy : 0.5F * sy, 0.0F).rotate(turn);
         Transformation t = new Transformation(centre.negate(), turn, new Vector3f(thick, sy, thick), new Quaternionf());
         Location at = mid.toLocation(world);
         Display display = i < ward.links.size() ? ward.links.get(i) : null;
         if (display != null && !display.isDead()) {
            display.teleport(at);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(2);
            display.setTransformation(t);
         } else {
            display = block ? this.blockLink(world, at, t) : this.modelLink(world, at, t);
            if (i < ward.links.size()) {
               ward.links.set(i, display);
            } else {
               ward.links.add(display);
            }
         }
      }

      while (ward.links.size() > count) {
         Display gone = ward.links.remove(ward.links.size() - 1);
         if (gone != null && !gone.isDead()) {
            gone.remove();
         }
      }
   }

   private void dress(Display d, Transformation t) {
      d.setBillboard(Billboard.FIXED);
      d.setBrightness(new Brightness(15, 15));
      d.setViewRange((float)this.num("view-range", 0.8));
      d.setPersistent(false);
      d.setTeleportDuration(2);
      d.addScoreboardTag("flameffacore_tether");
      d.setTransformation(t);
   }

   private Display modelLink(World world, Location at, Transformation t) {
      ItemStack stack = ItemStack.of(Material.PAPER);
      stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(this.plugin.configs().main().getString(this.path + ".model", "flameffa:chain")));
      return (Display)world.spawn(at, ItemDisplay.class, d -> {
         d.setItemStack(stack);
         d.setItemDisplayTransform(ItemDisplayTransform.NONE);
         this.dress(d, t);
      });
   }

   private Display blockLink(World world, Location at, Transformation t) {
      return (Display)world.spawn(at, BlockDisplay.class, d -> {
         d.setBlock(Material.IRON_CHAIN.createBlockData());
         this.dress(d, t);
      });
   }

   private void motes(TetherChain.Live ward, World world, Vector a, Vector b, double sag, double distance) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      Vector p = along(a, b, sag, rng.nextDouble());
      world.spawnParticle(Particle.END_ROD, p.toLocation(world), 1, 0.05, 0.05, 0.05, 0.002);
      if (rng.nextInt(3) == 0) {
         Vector q = along(a, b, sag, rng.nextDouble());
         world.spawnParticle(Particle.DUST, q.toLocation(world), 1, 0.0, 0.0, 0.0, 0.0, BRIGHT);
      }

      if (ward.pulse >= 0.0) {
         double speed = this.num("pulse-speed", 0.22);

         for (int k = 0; k < 4; k++) {
            double t = ward.pulse - k * speed * 0.25 * ward.pulseDir;
            if (!(t < 0.0) && !(t > 1.0)) {
               world.spawnParticle(Particle.DUST, along(a, b, sag, t).toLocation(world), 2, 0.08, 0.08, 0.08, 0.0, BRIGHT);
            }
         }

         world.spawnParticle(Particle.END_ROD, along(a, b, sag, Math.max(0.0, Math.min(1.0, ward.pulse))).toLocation(world), 2, 0.05, 0.05, 0.05, 0.01);
         ward.pulse = ward.pulse + speed * ward.pulseDir;
         if (ward.pulse < 0.0 || ward.pulse > 1.0) {
            ward.pulse = -1.0;
         }
      }
   }

   public void pulse(Player caster, boolean fromCaster) {
      TetherChain.Live ward = this.live.computeIfAbsent(caster.getUniqueId(), k -> new TetherChain.Live());
      ward.pulse = fromCaster ? 0.0 : 1.0;
      ward.pulseDir = fromCaster ? 1 : -1;
   }

   public void taut(Player caster, int ticks) {
      TetherChain.Live ward = this.live.computeIfAbsent(caster.getUniqueId(), k -> new TetherChain.Live());
      ward.tautUntil = Bukkit.getCurrentTick() + ticks;
   }

   public void attach(Player caster, Player target) {
      World world = target.getWorld();
      Location at = target.getLocation().add(0.0, 1.0, 0.0);
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, Color.fromRGB(234, 247, 255));
      world.spawnParticle(Particle.DUST, at, 26, 0.4, 0.6, 0.4, 0.0, PALE);
      world.spawnParticle(Particle.END_ROD, at, 14, 0.3, 0.5, 0.3, 0.06);
      world.spawnParticle(Particle.SOUL, at, 6, 0.3, 0.4, 0.3, 0.01);
      world.playSound(at, Sound.ITEM_TRIDENT_HIT, 1.0F, 0.7F);
      world.playSound(at, Sound.BLOCK_CHAIN_PLACE, 1.2F, 0.6F);
      world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9F, 0.7F);
      this.pulse(caster, true);
      this.taut(caster, 6);
   }

   public void reel(Player caster, Player target, boolean toThem) {
      World world = caster.getWorld();
      Location end = (toThem ? caster : target).getLocation().add(0.0, 1.0, 0.0);
      world.spawnParticle(Particle.DUST, end, 16, 0.4, 0.4, 0.4, 0.0, BRIGHT);
      world.spawnParticle(Particle.END_ROD, end, 8, 0.3, 0.3, 0.3, 0.12);
      world.playSound(end, Sound.BLOCK_CHAIN_HIT, 1.0F, toThem ? 0.8F : 0.6F);
      world.playSound(end, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7F, 1.3F);
      this.pulse(caster, !toThem);
      this.taut(caster, 8);
   }

   public void release(Player caster, Location from, Location to, boolean hard) {
      TetherChain.Live ward = this.live.remove(caster.getUniqueId());
      World world = caster.getWorld();
      if (from != null && to != null) {
         Vector a = from.toVector();
         Vector b = to.toVector();
         double distance = a.distance(b);
         double sag = Math.min(this.num("max-sag", 2.5), distance * this.num("sag-per-block", 0.06));
         int points = (int)Math.min(40.0, Math.max(6.0, distance));

         for (int i = 0; i <= points; i++) {
            Location p = along(a, b, sag, (double)i / points).toLocation(world);
            world.spawnParticle(Particle.SOUL, p, 1, 0.05, 0.05, 0.05, 0.01);
            if (hard || i % 2 == 0) {
               world.spawnParticle(Particle.DUST, p, 1, 0.1, 0.1, 0.1, 0.0, PALE);
            }
         }

         Location mid = along(a, b, sag, 0.5).toLocation(world);
         world.playSound(mid, Sound.BLOCK_CHAIN_BREAK, hard ? 1.2F : 0.8F, hard ? 1.1F : 1.4F);
         if (hard) {
            world.playSound(mid, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.1F, 1.2F);
            world.playSound(mid, Sound.ENTITY_VEX_HURT, 0.5F, 0.6F);
         }
      }

      if (ward != null) {
         int ticks = hard ? 8 : 6;
         ThreadLocalRandom rng = ThreadLocalRandom.current();
         List<Display> gone = new ArrayList<>(ward.links);

         for (Display d : gone) {
            if (d != null && !d.isDead()) {
               Transformation now = d.getTransformation();
               Vector3f shift = hard
                  ? new Vector3f((float)(rng.nextDouble() - 0.5) * 2.4F, (float)(rng.nextDouble() * 1.2), (float)(rng.nextDouble() - 0.5) * 2.4F)
                  : new Vector3f(0.0F, -0.4F, 0.0F);
               d.setInterpolationDelay(0);
               d.setInterpolationDuration(ticks);
               d.setTransformation(
                  new Transformation(
                     new Vector3f(now.getTranslation()).add(shift),
                     new Quaternionf(now.getLeftRotation()).rotateZ((float)((rng.nextDouble() - 0.5) * (hard ? 2.0 : 0.4))),
                     new Vector3f(now.getScale()).mul(0.05F),
                     new Quaternionf(now.getRightRotation())
                  )
               );
            }
         }

         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            for (Display dx : gone) {
               if (dx != null && !dx.isDead()) {
                  dx.remove();
               }
            }
         }, ticks + 1L);
      }
   }

   public void clear(Player caster) {
      TetherChain.Live ward = this.live.remove(caster.getUniqueId());
      if (ward != null) {
         for (Display d : ward.links) {
            if (d != null && !d.isDead()) {
               d.remove();
            }
         }
      }
   }

   public int sweepLeftovers() {
      int gone = 0;

      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains("flameffacore_tether")) {
               entity.remove();
               gone++;
            }
         }
      }

      return gone;
   }

   public void shutdown() {
      for (UUID id : new ArrayList<>(this.live.keySet())) {
         Player p = Bukkit.getPlayer(id);
         if (p != null) {
            this.clear(p);
         }
      }

      this.live.clear();
      this.sweepLeftovers();
   }

   public void demo(Location at, double length) {
      World world = at.getWorld();
      Vector a = at.toVector().add(new Vector(0.0, 1.5, 0.0));
      Vector b = a.clone().add(new Vector(length, 0.0, 0.0));
      double sag = Math.min(this.num("max-sag", 2.5), length * this.num("sag-per-block", 0.06));
      int points = (int)Math.max(6.0, length * 2.0);

      for (int i = 0; i <= points; i++) {
         world.spawnParticle(Particle.DUST, along(a, b, sag, (double)i / points).toLocation(world), 1, 0.0, 0.0, 0.0, 0.0, PALE);
      }

      world.spawnParticle(Particle.END_ROD, along(a, b, sag, 0.5).toLocation(world), 6, 0.2, 0.2, 0.2, 0.02);
      world.playSound(at, Sound.BLOCK_CHAIN_PLACE, 1.2F, 0.6F);
   }

   public void selfTest(Location at) {
      World world = at.getWorld();
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, PALE);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, BRIGHT);
      world.spawnParticle(Particle.END_ROD, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.SOUL, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, Color.fromRGB(234, 247, 255));
      Material.IRON_CHAIN.createBlockData();
   }

   private static final class Live {
      final List<Display> links = new ArrayList<>();
      double pulse = -1.0;
      int pulseDir = 1;
      int tautUntil;
   }
}
