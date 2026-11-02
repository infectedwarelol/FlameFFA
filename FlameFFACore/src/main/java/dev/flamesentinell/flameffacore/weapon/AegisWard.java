package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class AegisWard {
   public static final String TAG = "flameffacore_aegis";
   private static final DustOptions GOLD = new DustOptions(Color.fromRGB(255, 210, 74), 1.0F);
   private static final DustOptions PALE = new DustOptions(Color.fromRGB(255, 243, 196), 0.7F);
   private static final String QUAD_TEXT = "MMMMMMMM";
   private static final double QUAD_WIDTH = 1.225;
   private static final double QUAD_HEIGHT = 0.275;
   private static final Key RUNE_FONT = Key.key("minecraft", "alt");
   private static final String RUNE_LETTERS = "abcdefghijklmnopqrstuvwxyz";
   private final List<double[]> sigilPoints = new ArrayList<>();
   private int sigilGrid = -1;
   private final FlameFFACore plugin;
   private final String path;
   private final Map<UUID, AegisWard.Live> live = new HashMap<>();

   public AegisWard(FlameFFACore plugin, String path) {
      this.plugin = plugin;
      this.path = path + ".visual";
   }

   public boolean smooth() {
      return "smooth".equalsIgnoreCase(this.plugin.configs().main().getString(this.path + ".mode", "smooth"));
   }

   private double discSize() {
      return this.num("smooth-size", 2.8);
   }

   private double discDepth(double sideways) {
      double arc = this.discSize() / 27.13 * 16.0;
      double inside = arc * arc - sideways * sideways;
      return inside <= 0.0 ? -arc : Math.sqrt(inside) - arc;
   }

   private float viewRange() {
      return (float)Math.max(0.2, Math.min(2.0, this.num("view-range", 0.6)));
   }

   private double num(String key, double fallback) {
      return this.plugin.configs().main().getDouble(this.path + "." + key, fallback);
   }

   private int whole(String key, int fallback) {
      return this.plugin.configs().main().getInt(this.path + "." + key, fallback);
   }

   static Vector forward(float yaw) {
      double y = Math.toRadians(yaw);
      return new Vector(-Math.sin(y), 0.0, Math.cos(y));
   }

   private static Vector onDome(float yaw, double polar, double azimuth) {
      Vector c = forward(yaw);
      Vector e1 = new Vector(Math.cos(Math.toRadians(yaw)), 0.0, Math.sin(Math.toRadians(yaw)));
      Vector e2 = new Vector(0, 1, 0);
      Vector ring = e1.clone().multiply(Math.cos(azimuth)).add(e2.clone().multiply(Math.sin(azimuth)));
      return c.multiply(Math.cos(polar)).add(ring.multiply(Math.sin(polar)));
   }

   private static Vector meridian(float yaw, double polar, double azimuth) {
      Vector c = forward(yaw);
      Vector e1 = new Vector(Math.cos(Math.toRadians(yaw)), 0.0, Math.sin(Math.toRadians(yaw)));
      Vector e2 = new Vector(0, 1, 0);
      Vector ring = e1.clone().multiply(Math.cos(azimuth)).add(e2.clone().multiply(Math.sin(azimuth)));
      return c.multiply(-Math.sin(polar)).add(ring.multiply(Math.cos(polar)));
   }

   private static float[] facing(Vector d) {
      float yaw = (float)Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
      float pitch = (float)(-Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.getY())))));
      return new float[]{yaw, pitch};
   }

   private static float roll(float yaw, float pitch, Vector d, Vector up) {
      double y = Math.toRadians(yaw);
      double p = Math.toRadians(pitch);
      Vector base = new Vector(-Math.sin(p) * Math.sin(y), Math.cos(p), Math.sin(p) * Math.cos(y));
      double sin = base.clone().crossProduct(up).dot(d);
      double cos = base.dot(up);
      return (float)Math.atan2(sin, cos);
   }

   private static Vector3f toLocal(float yaw, float pitch, Vector world) {
      double y = Math.toRadians(yaw);
      double p = Math.toRadians(pitch);
      double x1 = world.getX() * Math.cos(y) + world.getZ() * Math.sin(y);
      double z1 = -world.getX() * Math.sin(y) + world.getZ() * Math.cos(y);
      double y1 = world.getY();
      double y2 = y1 * Math.cos(p) + z1 * Math.sin(p);
      double z2 = -y1 * Math.sin(p) + z1 * Math.cos(p);
      return new Vector3f((float)x1, (float)y2, (float)z2);
   }

   private static Transformation quad(float width, float height, float roll) {
      float sx = (float)(width / 1.225);
      float sy = (float)(height / 0.275);
      float tx = (float)(height / 2.0F * Math.sin(roll));
      float ty = (float)(-height / 2.0F * Math.cos(roll));
      return new Transformation(new Vector3f(tx, ty, 0.0F), new Quaternionf().rotationZ(roll), new Vector3f(sx, sy, 1.0F), new Quaternionf());
   }

   private List<AegisWard.Cell> cells() {
      int bands = Math.max(1, Math.min(5, this.whole("rings", 3)));
      double radius = Math.toRadians(this.num("radius-degrees", 46.0));
      double reach = this.plugin.configs().main().getDouble(this.path.replace(".visual", "") + ".stand-off", 1.9);
      double overlap = this.num("overlap", 1.06);
      double band = radius / bands;
      List<AegisWard.Cell> out = new ArrayList<>();
      float middle = (float)(2.0 * band * reach * 0.88);
      out.add(new AegisWard.Cell(0, 0.0, 0.0, middle, middle));

      for (int k = 1; k < bands; k++) {
         double polar = (k + 0.5) * band;
         int count = (int)Math.ceil((Math.PI * 2) * Math.sin(polar) / band);
         float width = (float)((Math.PI * 2) * reach * Math.sin(polar + band / 2.0) / count * overlap);
         float height = (float)(band * reach * 1.18);

         for (int i = 0; i < count; i++) {
            double azimuth = (Math.PI * 2) * i / count + (k % 2 == 0 ? Math.PI / count : 0.0);
            out.add(new AegisWard.Cell(k, polar, azimuth, width, height));
         }
      }

      return out;
   }

   public int entityCount() {
      boolean font = "font".equalsIgnoreCase(this.plugin.configs().main().getString(this.path + ".sigil-mode", "font"));
      return this.cells().size() + Math.max(0, this.whole("runes", 15)) + Math.max(0, this.whole("ribs", 0)) + (font ? 1 : 0);
   }

   public void draw(Player player) {
      AegisWard.Live ward = this.live.computeIfAbsent(player.getUniqueId(), k -> new AegisWard.Live());
      boolean fresh = ward.ticks == 0;
      ward.ticks = ward.ticks + Math.max(1, Math.min(4, this.whole("update-ticks", 2)));
      Location base = player.getLocation();
      World world = base.getWorld();
      float yaw = base.getYaw();
      Location centre = base.clone().add(0.0, this.num("height", 1.15), 0.0);
      double reach = this.plugin.configs().main().getDouble(this.path.replace(".visual", "") + ".stand-off", 1.9);
      if (!ward.bloom.isEmpty()) {
         for (Entry<Display, Transformation> e : ward.bloom.entrySet()) {
            Display d = e.getKey();
            if (!d.isDead()) {
               d.setInterpolationDelay(0);
               d.setInterpolationDuration(6);
               d.setTransformation(e.getValue());
            }
         }

         ward.bloom.clear();
      }

      if (fresh) {
         world.playSound(centre, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5F, 1.7F);
      }

      if (this.smooth()) {
         this.disc(ward, world, centre, yaw, reach, fresh);

         for (TextDisplay d : ward.facets) {
            if (d != null && !d.isDead()) {
               d.remove();
            }
         }

         ward.facets.clear();
         this.ribs(ward, world, centre, yaw, reach, fresh);
         this.runes(ward, world, centre, yaw, reach, fresh);
         this.sigil(ward, world, centre, yaw, reach, fresh);
         this.rim(world, centre, yaw, reach, ward.ticks);
      } else {
         if (ward.disc != null && !ward.disc.isDead()) {
            ward.disc.remove();
            ward.disc = null;
         }

         List<AegisWard.Cell> cells = this.cells();
         int bands = Math.max(1, Math.min(5, this.whole("rings", 3)));

         for (int i = 0; i < cells.size(); i++) {
            AegisWard.Cell cell = cells.get(i);
            Vector dx = onDome(yaw, cell.polar(), cell.azimuth());
            Vector up = meridian(yaw, cell.polar(), cell.azimuth());
            float[] face = facing(dx);
            float roll = cell.polar() == 0.0 ? 0.0F : roll(face[0], face[1], dx, up);
            Location at = centre.clone().add(dx.clone().multiply(reach));
            at.setYaw(face[0]);
            at.setPitch(face[1]);
            TextDisplay display = i < ward.facets.size() ? ward.facets.get(i) : null;
            if (display != null && !display.isDead()) {
               display.teleport(at);
            } else {
               boolean rim = cell.ring() == bands - 1;
               display = this.facet(world, at, cell.width(), cell.height(), roll, rim ? this.rimFill() : this.fill(cell.ring(), bands), ward, fresh);
               if (i < ward.facets.size()) {
                  ward.facets.set(i, display);
               } else {
                  ward.facets.add(display);
               }
            }
         }

         while (ward.facets.size() > cells.size()) {
            ward.facets.remove(ward.facets.size() - 1).remove();
         }

         this.ribs(ward, world, centre, yaw, reach, fresh);
         this.runes(ward, world, centre, yaw, reach, fresh);
         this.sigil(ward, world, centre, yaw, reach, fresh);
         this.rim(world, centre, yaw, reach, ward.ticks);
      }
   }

   private static Transformation shrunk(Transformation full) {
      return new Transformation(
         new Vector3f(full.getTranslation()).mul(0.05F),
         new Quaternionf(full.getLeftRotation()),
         new Vector3f(full.getScale()).mul(0.05F),
         new Quaternionf(full.getRightRotation())
      );
   }

   private int rgb(String key, int fallback) {
      try {
         return Integer.parseInt(this.plugin.configs().main().getString(this.path + "." + key, "").replace("#", ""), 16);
      } catch (NumberFormatException var4) {
         return fallback;
      }
   }

   private static Color argb(int alpha, int rgb) {
      return Color.fromARGB(Math.max(0, Math.min(255, alpha)), rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
   }

   private Color fill(int ring, int bands) {
      int alpha = this.whole("alpha", 84);
      int inner = this.whole("centre-alpha", 112);
      double t = bands <= 1 ? 0.0 : (double)ring / (bands - 1);
      return argb((int)Math.round(inner + (alpha - inner) * t), this.rgb("colour", 16754217));
   }

   private Color rimFill() {
      return argb(this.whole("rim-alpha", 150), this.rgb("rim-colour", 16770442));
   }

   private TextDisplay facet(World world, Location at, float width, float height, float roll, Color colour, AegisWard.Live ward, boolean bloom) {
      Transformation full = quad(width, height, roll);
      TextDisplay out = (TextDisplay)world.spawn(at, TextDisplay.class, d -> {
         d.text(Component.text("MMMMMMMM"));
         d.setTextOpacity((byte)4);
         d.setShadowed(false);
         d.setSeeThrough(false);
         d.setDefaultBackground(false);
         d.setBackgroundColor(colour);
         d.setBillboard(Billboard.FIXED);
         d.setBrightness(new Brightness(15, 15));
         d.setViewRange(this.viewRange());
         d.setPersistent(false);
         d.setTeleportDuration(2);
         d.addScoreboardTag("flameffacore_aegis");
         d.setTransformation(bloom ? shrunk(full) : full);
      });
      if (bloom) {
         ward.bloom.put(out, full);
      }

      return out;
   }

   private void disc(AegisWard.Live ward, World world, Location centre, float yaw, double reach, boolean bloom) {
      float scale = (float)(this.discSize() / 27.13 * 16.0);
      Vector d = forward(yaw);
      Location at = centre.clone().add(d.clone().multiply(reach));
      at.setYaw(yaw);
      at.setPitch(0.0F);
      Transformation full = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
      if (ward.disc != null && !ward.disc.isDead()) {
         ward.disc.teleport(at);
      } else {
         ItemStack stack = ItemStack.of(Material.PAPER);
         stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(this.plugin.configs().main().getString(this.path + ".smooth-model", "flameffa:aegis")));
         ItemDisplay made = (ItemDisplay)world.spawn(at, ItemDisplay.class, i -> {
            i.setItemStack(stack);
            i.setItemDisplayTransform(ItemDisplayTransform.NONE);
            i.setBillboard(Billboard.FIXED);
            i.setBrightness(new Brightness(15, 15));
            i.setViewRange(this.viewRange());
            i.setPersistent(false);
            i.setTeleportDuration(2);
            i.addScoreboardTag("flameffacore_aegis");
            i.setTransformation(bloom ? shrunk(full) : full);
         });
         if (bloom) {
            ward.bloom.put(made, full);
         }

         ward.disc = made;
      }
   }

   private void ribs(AegisWard.Live ward, World world, Location centre, float yaw, double reach, boolean bloom) {
      int count = Math.max(0, Math.min(16, this.whole("ribs", 0)));
      double spin = Math.toRadians(this.num("rune-spin-degrees-per-tick", 1.0)) * ward.ticks + this.num("rune-wobble", 0.0) * Math.sin(ward.ticks * 0.07);
      double radius = Math.toRadians(this.num("radius-degrees", 46.0));
      double from = radius * this.num("rib-from", 0.24);
      double to = radius * this.num("rib-to", 0.9);
      double mid = (from + to) / 2.0;
      float length = (float)((to - from) * reach);
      float width = (float)this.num("rib-width", 0.035);
      Color colour = argb(this.whole("rib-alpha", 130), this.rgb("rim-colour", 16770442));

      for (int i = 0; i < count; i++) {
         double azimuth = (Math.PI * 2) * i / count + spin;
         Vector d = onDome(yaw, mid, azimuth);
         Vector up = meridian(yaw, mid, azimuth);
         float[] face = facing(d);
         float roll = roll(face[0], face[1], d, up);
         Location at = centre.clone().add(d.clone().multiply(reach + 0.012));
         at.setYaw(face[0]);
         at.setPitch(face[1]);
         TextDisplay rib = i < ward.ribs.size() ? ward.ribs.get(i) : null;
         if (rib != null && !rib.isDead()) {
            rib.teleport(at);
            rib.setInterpolationDelay(0);
            rib.setInterpolationDuration(2);
            rib.setTransformation(quad(width, length, roll));
         } else {
            rib = this.facet(world, at, width, length, roll, colour, ward, bloom);
            if (i < ward.ribs.size()) {
               ward.ribs.set(i, rib);
            } else {
               ward.ribs.add(rib);
            }
         }
      }

      while (ward.ribs.size() > count) {
         ward.ribs.remove(ward.ribs.size() - 1).remove();
      }
   }

   private void runes(AegisWard.Live ward, World world, Location centre, float yaw, double reach, boolean bloom) {
      int count = Math.max(0, Math.min(24, this.whole("runes", 15)));
      double polar = Math.toRadians(this.num("radius-degrees", 46.0)) * this.num("rune-band", 0.88);
      double spin = Math.toRadians(this.num("rune-spin-degrees-per-tick", 1.0)) * ward.ticks;
      double wobble = this.num("rune-wobble", 0.0) * Math.sin(ward.ticks * 0.07);
      float size = (float)this.num("rune-size", 0.32);
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      boolean flat = this.smooth();
      Vector c = forward(yaw);
      Vector e1 = new Vector(Math.cos(Math.toRadians(yaw)), 0.0, Math.sin(Math.toRadians(yaw)));
      double band = this.discSize() / 2.0 * this.num("rune-band", 0.88);

      for (int i = 0; i < count; i++) {
         double azimuth = (Math.PI * 2) * i / count + spin + wobble;
         Vector d;
         Vector up;
         Location at;
         if (flat) {
            double sx = Math.cos(azimuth) * band;
            double sy = Math.sin(azimuth) * band;
            d = c;
            up = e1.clone().multiply(Math.cos(azimuth)).setY(Math.sin(azimuth));
            at = centre.clone().add(c.clone().multiply(reach + this.discDepth(sx) + 0.04)).add(e1.clone().multiply(sx)).add(0.0, sy, 0.0);
         } else {
            d = onDome(yaw, polar, azimuth);
            up = meridian(yaw, polar, azimuth);
            at = centre.clone().add(d.clone().multiply(reach + 0.02));
         }

         float[] face = facing(d);
         float roll = roll(face[0], face[1], d, up);
         at.setYaw(face[0]);
         at.setPitch(face[1]);
         TextDisplay rune = i < ward.runes.size() ? ward.runes.get(i) : null;
         if (rune != null && !rune.isDead()) {
            rune.teleport(at);
            rune.setInterpolationDelay(0);
            rune.setInterpolationDuration(2);
            rune.setTransformation(glyph(size, roll));
         } else {
            rune = this.rune(world, at, glyph(size, roll), ward, bloom);
            if (i < ward.runes.size()) {
               ward.runes.set(i, rune);
            } else {
               ward.runes.add(rune);
            }
         }

         if (rng.nextInt(14) == 0) {
            rune.text(this.letter());
         }
      }

      while (ward.runes.size() > count) {
         ward.runes.remove(ward.runes.size() - 1).remove();
      }
   }

   private Component letter() {
      char c = "abcdefghijklmnopqrstuvwxyz".charAt(ThreadLocalRandom.current().nextInt("abcdefghijklmnopqrstuvwxyz".length()));
      return ((TextComponent)Component.text(String.valueOf(c)).font(RUNE_FONT)).color(TextColor.color(this.rgb("rune-colour", 16774344)));
   }

   private static Transformation glyph(float size, float roll) {
      float sx = size / 0.175F;
      float sy = size / 0.275F;
      float tx = (float)(size / 2.0F * Math.sin(roll));
      float ty = (float)(-size / 2.0F * Math.cos(roll));
      return new Transformation(new Vector3f(tx, ty, 0.0F), new Quaternionf().rotationZ(roll), new Vector3f(sx, sy, 1.0F), new Quaternionf());
   }

   private TextDisplay rune(World world, Location at, Transformation full, AegisWard.Live ward, boolean bloom) {
      TextDisplay out = (TextDisplay)world.spawn(at, TextDisplay.class, d -> {
         d.text(this.letter());
         d.setShadowed(true);
         d.setSeeThrough(false);
         d.setDefaultBackground(false);
         d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
         d.setBillboard(Billboard.FIXED);
         d.setBrightness(new Brightness(15, 15));
         d.setViewRange(this.viewRange());
         d.setPersistent(false);
         d.setTeleportDuration(2);
         d.addScoreboardTag("flameffacore_aegis");
         d.setTransformation(bloom ? shrunk(full) : full);
      });
      if (bloom) {
         ward.bloom.put(out, full);
      }

      return out;
   }

   private List<double[]> sigilPoints() {
      int grid = Math.max(8, Math.min(48, this.whole("sigil-grid", 26)));
      if (grid == this.sigilGrid) {
         return this.sigilPoints;
      } else {
         this.sigilGrid = grid;
         this.sigilPoints.clear();
         File file = new File(this.plugin.getDataFolder(), "sigil.png");

         try {
            if (!file.exists()) {
               try (InputStream in = this.plugin.getResource("sigil.png")) {
                  if (in != null) {
                     Files.copy(in, file.toPath());
                  }
               }
            }

            BufferedImage image = ImageIO.read(file);
            if (image == null) {
               return this.sigilPoints;
            }

            int w = image.getWidth();
            int h = image.getHeight();

            for (int gy = 0; gy < grid; gy++) {
               for (int gx = 0; gx < grid; gx++) {
                  int x0 = gx * w / grid;
                  int x1 = Math.max(x0 + 1, (gx + 1) * w / grid);
                  int y0 = gy * h / grid;
                  int y1 = Math.max(y0 + 1, (gy + 1) * h / grid);
                  long lit = 0L;
                  long total = 0L;

                  for (int y = y0; y < y1; y++) {
                     for (int x = x0; x < x1; x++) {
                        int argb = image.getRGB(x, y);
                        int alpha = argb >>> 24 & 0xFF;
                        int bright = Math.max(argb >> 16 & 0xFF, Math.max(argb >> 8 & 0xFF, argb & 0xFF));
                        if (alpha > 128 && bright > 90) {
                           lit++;
                        }

                        total++;
                     }
                  }

                  if (total > 0L && lit * 100L / total >= 40L) {
                     this.sigilPoints.add(new double[]{(gx + 0.5) / grid - 0.5, 0.5 - (gy + 0.5) / grid});
                  }
               }
            }

            this.plugin.getSLF4JLogger().info("aegis sigil sampled to {} points on a {} grid", this.sigilPoints.size(), grid);
         } catch (IOException var23) {
            this.plugin.getSLF4JLogger().warn("could not read sigil.png, the ward has no sigil", var23);
         }

         return this.sigilPoints;
      }
   }

   public void reload() {
      this.sigilGrid = -1;
   }

   public int sigilCount() {
      return this.sigilPoints().size();
   }

   private void sigil(AegisWard.Live ward, World world, Location centre, float yaw, double reach, boolean bloom) {
      double size = this.num("sigil-size", 2.0) * (1.0 + 0.06 * Math.sin(ward.ticks * 0.09));
      Vector c = forward(yaw);
      Vector e1 = new Vector(Math.cos(Math.toRadians(yaw)), 0.0, Math.sin(Math.toRadians(yaw)));
      Vector e2 = new Vector(0, 1, 0);
      Location origin = centre.clone().add(c.clone().multiply(reach + 0.03));
      String mode = this.plugin.configs().main().getString(this.path + ".sigil-mode", "font");
      if ("font".equalsIgnoreCase(mode)) {
         String glyph = this.plugin.configs().main().getString(this.path + ".sigil-font-char", "\ue000");
         String font = this.plugin.configs().main().getString(this.path + ".sigil-font", "flameffa:sigil");
         Component text = ((TextComponent)Component.text(glyph).font(Key.key(font))).color(TextColor.color(this.rgb("sigil-colour", 16774344)));
         float scale = (float)(size / 0.8);
         float lift = (float)(this.num("sigil-font-y-offset", 0.0) - size / 2.0);
         Transformation full = new Transformation(new Vector3f(0.0F, lift, 0.0F), new Quaternionf(), new Vector3f(scale, scale, 1.0F), new Quaternionf());
         Location at = origin.clone();
         at.setYaw(yaw);
         at.setPitch(0.0F);
         TextDisplay display = ward.sigil.isEmpty() ? null : ward.sigil.get(0);
         if (display != null && !display.isDead()) {
            display.teleport(at);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(2);
            display.setTransformation(full);
         } else {
            TextDisplay made = (TextDisplay)world.spawn(at, TextDisplay.class, d -> {
               d.text(text);
               d.setShadowed(false);
               d.setSeeThrough(false);
               d.setDefaultBackground(false);
               d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
               d.setBillboard(Billboard.FIXED);
               d.setBrightness(new Brightness(15, 15));
               d.setViewRange(this.viewRange());
               d.setPersistent(false);
               d.setTeleportDuration(2);
               d.addScoreboardTag("flameffacore_aegis");
               d.setTransformation(bloom ? shrunk(full) : full);
            });
            if (bloom) {
               ward.bloom.put(made, full);
            }

            ward.sigil.clear();
            ward.sigil.add(made);
         }
      } else {
         for (TextDisplay d : ward.sigil) {
            if (d != null && !d.isDead()) {
               d.remove();
            }
         }

         ward.sigil.clear();
         DustOptions dust = new DustOptions(Color.fromRGB(this.rgb("sigil-colour", 16774344)), (float)this.num("sigil-particle-size", 0.8));

         for (double[] p : this.sigilPoints()) {
            Location at = origin.clone().add(e1.clone().multiply(p[0] * size)).add(e2.clone().multiply(p[1] * size));
            world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, dust);
         }
      }
   }

   private Location onFace(Location centre, float yaw, double reach, double share, double azimuth, double lift) {
      if (this.smooth()) {
         Vector c = forward(yaw);
         Vector e1 = new Vector(Math.cos(Math.toRadians(yaw)), 0.0, Math.sin(Math.toRadians(yaw)));
         double r = this.discSize() / 2.0 * share;
         double sx = Math.cos(azimuth) * r;
         return centre.clone().add(c.multiply(reach + this.discDepth(sx) + lift)).add(e1.multiply(sx)).add(0.0, Math.sin(azimuth) * r, 0.0);
      } else {
         double polar = Math.toRadians(this.num("radius-degrees", 46.0)) * share;
         return centre.clone().add(onDome(yaw, polar, azimuth).multiply(reach + lift));
      }
   }

   private void rim(World world, Location centre, float yaw, double reach, int ticks) {
      int points = 30;
      double drift = ticks * 0.06;

      for (int i = 0; i < points; i++) {
         double azimuth = (Math.PI * 2) * i / points + drift;
         Location at = this.onFace(centre, yaw, reach, 1.0, azimuth, 0.04);
         world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, i % 3 == 0 ? PALE : GOLD);
      }

      Location mote = this.onFace(centre, yaw, reach, 1.0, ticks * 0.21, 0.06);
      world.spawnParticle(Particle.END_ROD, mote, 1, 0.0, 0.0, 0.0, 0.0);
      int period = Math.max(10, this.whole("pulse-ticks", 44));
      double phase = (double)(ticks % period) / period;

      for (int i = 0; i < 20; i++) {
         Location at = this.onFace(centre, yaw, reach, phase, (Math.PI * 2) * i / 20.0 + phase, 0.05);
         world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, PALE);
      }

      Location front = centre.clone().add(forward(yaw).multiply(reach));
      world.spawnParticle(Particle.DUST, front, 2, 0.8, 0.8, 0.15, 0.0, PALE);
   }

   public void demo(Location at) {
      World world = at.getWorld();
      Location centre = at.clone().add(0.0, 1.15, 0.0);
      double polar = Math.toRadians(this.num("radius-degrees", 46.0));

      for (int i = 0; i < 48; i++) {
         Location p = centre.clone().add(onDome(0.0F, polar, (Math.PI * 2) * i / 48.0).multiply(1.9));
         world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, GOLD);
      }

      for (int i = 0; i < 12; i++) {
         Location p = centre.clone().add(onDome(0.0F, polar * 0.55, (Math.PI * 2) * i / 12.0).multiply(1.9));
         world.spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, PALE);
      }
   }

   public void shatter(Player player, Location from) {
      AegisWard.Live ward = this.live.remove(player.getUniqueId());
      if (ward != null) {
         List<Display> gone = new ArrayList<>();
         gone.addAll(ward.facets);
         if (ward.disc != null) {
            gone.add(ward.disc);
         }

         gone.addAll(ward.ribs);
         gone.addAll(ward.runes);

         for (Display d : gone) {
            if (d != null && !d.isDead()) {
               d.remove();
            }
         }

         int ticks = Math.max(3, this.whole("sigil-fade-ticks", 6));
         List<Display> flare = new ArrayList<>();

         for (TextDisplay dx : ward.sigil) {
            if (dx != null && !dx.isDead()) {
               Transformation now = dx.getTransformation();
               dx.setInterpolationDelay(0);
               dx.setInterpolationDuration(ticks);
               dx.setTransformation(
                  new Transformation(
                     new Vector3f(now.getTranslation()).mul(1.8F),
                     new Quaternionf(now.getLeftRotation()),
                     new Vector3f(now.getScale()).mul(1.8F),
                     new Quaternionf(now.getRightRotation())
                  )
               );

               for (int step = 1; step <= ticks; step++) {
                  int alpha = 255 - 255 * step / ticks;
                  Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                     if (!dx.isDead()) {
                        dx.setTextOpacity((byte)Math.max(4, alpha));
                     }
                  }, step);
               }

               flare.add(dx);
            }
         }

         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            for (Display dxx : flare) {
               if (dxx != null && !dxx.isDead()) {
                  dxx.remove();
               }
            }
         }, ticks + 1L);
         ward.facets.clear();
         ward.ribs.clear();
         ward.runes.clear();
         ward.sigil.clear();
         ward.bloom.clear();
         this.cracks(player, from);
      }
   }

   private void cracks(Player player, Location impact) {
      World world = player.getWorld();
      float yaw = player.getLocation().getYaw();
      double reach = this.plugin.configs().main().getDouble(this.path.replace(".visual", "") + ".stand-off", 1.9);
      double radius = Math.toRadians(this.num("radius-degrees", 46.0));
      Location centre = player.getLocation().add(0.0, this.num("height", 1.15), 0.0);
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      Vector toHit = impact.toVector().subtract(centre.toVector());

      for (int line = 0; line < 7; line++) {
         double a = rng.nextDouble() * Math.PI * 2.0;
         double len = 0.6 + rng.nextDouble() * 0.9;

         for (double t = 0.08; t < len; t += 0.11) {
            Vector d = toHit.clone().add(new Vector(Math.cos(a) * t, Math.sin(a) * t * 0.8, 0.0)).normalize();
            Location at = centre.clone().add(d.multiply(reach + 0.04));
            world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, PALE);
         }
      }

      for (double ring = 0.5; ring <= 1.0; ring += 0.5) {
         for (int i = 0; i < 28; i++) {
            Location at = centre.clone().add(onDome(yaw, radius * ring, (Math.PI * 2) * i / 28.0).multiply(reach + 0.05));
            world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, ring < 1.0 ? PALE : GOLD);
            if (ring == 1.0 && i % 4 == 0) {
               world.spawnParticle(Particle.END_ROD, at, 0, 0.0, 0.12, 0.0, 1.0);
            }
         }
      }

      world.playSound(impact, Sound.BLOCK_GLASS_BREAK, 1.2F, 0.65F);
      world.playSound(impact, Sound.BLOCK_BEACON_DEACTIVATE, 0.9F, 1.5F);
   }

   public void clear(Player player) {
      AegisWard.Live ward = this.live.remove(player.getUniqueId());
      if (ward != null) {
         if (ward.disc != null && !ward.disc.isDead()) {
            ward.disc.remove();
         }

         for (Entity e : ward.facets) {
            if (e != null && !e.isDead()) {
               e.remove();
            }
         }

         for (Entity ex : ward.ribs) {
            if (ex != null && !ex.isDead()) {
               ex.remove();
            }
         }

         for (Entity exx : ward.runes) {
            if (exx != null && !exx.isDead()) {
               exx.remove();
            }
         }

         for (Entity exxx : ward.sigil) {
            if (exxx != null && !exxx.isDead()) {
               exxx.remove();
            }
         }

         ward.bloom.clear();
      }
   }

   public boolean shown(Player player) {
      return this.live.containsKey(player.getUniqueId());
   }

   public int sweepLeftovers() {
      int gone = 0;

      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains("flameffacore_aegis")) {
               entity.remove();
               gone++;
            }
         }
      }

      return gone;
   }

   public void shutdown() {
      for (UUID id : new ArrayList<>(this.live.keySet())) {
         Player player = Bukkit.getPlayer(id);
         if (player != null) {
            this.clear(player);
         }
      }

      this.live.clear();
      this.sweepLeftovers();
   }

   private record Cell(int ring, double polar, double azimuth, float width, float height) {
   }

   private static final class Live {
      final List<TextDisplay> facets = new ArrayList<>();
      ItemDisplay disc;
      final List<TextDisplay> ribs = new ArrayList<>();
      final List<TextDisplay> runes = new ArrayList<>();
      final List<TextDisplay> sigil = new ArrayList<>();
      final Map<Display, Transformation> bloom = new HashMap<>();
      int ticks;
   }
}
