package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.util.Vector;

public final class TetherTrident implements CustomWeapon, Listener {
   public static final String ID = "tether-trident";
   private final FlameFFACore plugin;
   private final TetherChain chain;
   private final Map<UUID, UUID> inFlight = new ConcurrentHashMap<>();
   private final Map<UUID, TetherTrident.Tether> tethers = new ConcurrentHashMap<>();
   private final Map<UUID, ScheduledTask> drags = new ConcurrentHashMap<>();
   private final Map<UUID, ScheduledTask> homing = new ConcurrentHashMap<>();

   public TetherTrident(FlameFFACore var1) {
      this.plugin = var1;
      this.chain = new TetherChain(var1, this.path());
   }

   public TetherChain chain() {
      return this.chain;
   }

   @Override
   public String id() {
      return "tether-trident";
   }

   @Override
   public CustomWeapon.Trigger trigger() {
      return CustomWeapon.Trigger.THROW;
   }

   @Override
   public ItemStack build(FlameFFACore var1) {
      ItemStack var2 = ItemStack.of(Material.TRIDENT);
      var2.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
      var2.addUnsafeEnchantment(Enchantment.IMPALING, 5);
      return var2;
   }

   @Override
   public List<String> check(ItemStack var1) {
      ArrayList var2 = new ArrayList();
      if (var1.getType() != Material.TRIDENT) {
         var2.add("tether trident is not a trident");
      }

      if (var1.getEnchantmentLevel(Enchantment.LOYALTY) != 3) {
         var2.add("tether trident is missing Loyalty III, it would never come back");
      }

      if (var1.getEnchantmentLevel(Enchantment.RIPTIDE) > 0) {
         var2.add("tether trident has Riptide, which cancels Loyalty");
      }

      return var2;
   }

   @Override
   public boolean onThrow(FlameFFACore var1, Player var2, Projectile var3) {
      this.inFlight.put(var3.getUniqueId(), var2.getUniqueId());
      var2.getWorld().playSound(var2.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0F, 0.8F);
      Player var4 = this.aimTarget(var2);
      if (var4 != null) {
         this.home(var3, var4);
      }

      return false;
   }

   private Player aimTarget(Player var1) {
      FileConfiguration var2 = this.plugin.configs().main();
      double var3 = Math.cos(Math.toRadians(var2.getDouble(this.path() + ".homing-cone-degrees", 30.0)));
      double var5 = var2.getDouble(this.path() + ".range", 75.0);
      Location var7 = var1.getEyeLocation();
      Vector var8 = var7.getDirection().normalize();
      Player var9 = null;
      double var10 = var3;

      for (Player var13 : var1.getWorld().getPlayers()) {
         if (!var13.equals(var1) && Bukkit.isOwnedByCurrentRegion(var13) && !var13.isDead() && var13.getGameMode() != GameMode.SPECTATOR && var1.canSee(var13)) {
            Vector var14 = var13.getLocation().add(0.0, var13.getHeight() * 0.6, 0.0).toVector().subtract(var7.toVector());
            double var15 = var14.length();
            if (!(var15 < 0.5) && !(var15 > var5)) {
               double var17 = var14.multiply(1.0 / var15).dot(var8);
               if (var17 > var10) {
                  var10 = var17;
                  var9 = var13;
               }
            }
         }
      }

      return var9;
   }

   private void home(Projectile var1, Player var2) {
      UUID var3 = var1.getUniqueId();
      double var4 = Math.max(0.0, Math.min(1.0, this.plugin.configs().main().getDouble(this.path() + ".homing-turn", 0.3)));
      ScheduledTask var13 = this.plugin.tasks().entityTimer(var1, task -> {
         boolean var6 = var1 instanceof AbstractArrow var7 && var7.isInBlock();
         if (var1.isValid() && !var6 && this.inFlight.containsKey(var3) && var2.isOnline() && !var2.isDead() && Bukkit.isOwnedByCurrentRegion(var2) && var2.getWorld().equals(var1.getWorld())) {
            Vector var12 = var1.getVelocity();
            double var8 = Math.max(var12.length(), 1.5);
            Vector var10 = var2.getLocation().add(0.0, var2.getHeight() * 0.6, 0.0).toVector().subtract(var1.getLocation().toVector());
            if (var10.lengthSquared() < 1.0E-4 || var12.lengthSquared() < 1.0E-4) {
               return;
            }

            Vector var11 = var12.normalize().multiply(1.0 - var4).add(var10.normalize().multiply(var4));
            if (var11.lengthSquared() > 1.0E-4) {
               var1.setVelocity(var11.normalize().multiply(var8));
            }
         } else {
            this.stopHoming(var3);
         }
      }, () -> this.stopHoming(var3), 1L, 1L);
      if (var13 != null) {
         this.homing.put(var3, var13);
      }
   }

   private void stopHoming(UUID var1) {
      ScheduledTask var2 = this.homing.remove(var1);
      if (var2 != null) {
         var2.cancel();
      }
   }

   private static boolean shielded(Player var0, Projectile var1) {
      if (!var0.isBlocking()) {
         return false;
      } else {
         Vector var2 = var0.getLocation().getDirection().setY(0);
         Vector var3 = var1.getLocation().toVector().subtract(var0.getLocation().toVector()).setY(0);
         return var2.lengthSquared() < 1.0E-4 || var3.lengthSquared() < 1.0E-4 || var2.normalize().dot(var3.normalize()) > 0.0;
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onLand(ProjectileHitEvent var1) {
      UUID var2 = this.inFlight.remove(var1.getEntity().getUniqueId());
      this.stopHoming(var1.getEntity().getUniqueId());
      if (var2 != null) {
         Player var3 = Bukkit.getPlayer(var2);
         if (var3 != null) {
            boolean var5 = Bukkit.isOwnedByCurrentRegion(var3);
            if (var5
               && var1.getHitEntity() instanceof Player var4
               && !var4.equals(var3)
               && !shielded(var4, var1.getEntity())
               && !this.inSpawn(var4)
               && !this.inSpawn(var3)) {
               this.attach(var3, var4);
               return;
            }

            this.plugin.weapons().startCooldownSeconds(var3, this, this.plugin.configs().main().getInt(this.path() + ".miss-cooldown-seconds", 8));
            this.plugin.tasks().runOn(var3, () -> var3.playSound(var3.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7F, 1.4F));
         }
      }
   }

   private boolean inSpawn(Player var1) {
      return var1 != null && this.plugin.safezone().isSet() && this.plugin.safezone().contains(var1.getLocation());
   }

   private void attach(Player var1, Player var2) {
      FileConfiguration var3 = this.plugin.configs().main();
      int var4 = Math.max(1, var3.getInt(this.path() + ".duration-seconds", 8));
      TetherTrident.Tether var5 = new TetherTrident.Tether();
      var5.target = var2.getUniqueId();
      var5.endsAt = Bukkit.getCurrentTick() + var4 * 20;
      var5.yanksLeft = Math.max(1, var3.getInt(this.path() + ".max-yanks", 3));
      TetherTrident.Tether var6 = this.tethers.put(var1.getUniqueId(), var5);
      if (var6 != null && var6.chain != null) {
         var6.chain.cancel();
      }

      this.plugin.weapons().startCooldown(var1, this, null);
      this.chain.attach(var1, var2);
      this.plugin.cooldownBar().flash(var1, this.plugin.configs().msg("weapon.tether-attached", "player", var2.getName()));
      this.plugin.cooldownBar().flash(var2, this.plugin.configs().msg("weapon.tether-hooked", "player", var1.getName()));
      var5.chain = this.plugin.tasks().entityTimer(var1, task -> this.draw(var1), null, 0L, 2L);
   }

   private void draw(Player var1) {
      TetherTrident.Tether var2 = this.tethers.get(var1.getUniqueId());
      if (var2 != null) {
         Player var3 = Bukkit.getPlayer(var2.target);
         if (var3 == null || !Bukkit.isOwnedByCurrentRegion(var3) || var3.isDead() || var1.isDead() || !var1.isOnline() || !var1.getWorld().equals(var3.getWorld())) {
            this.release(var1, "gone");
         } else if (!this.inSpawn(var1) && !this.inSpawn(var3)) {
            if (Bukkit.getCurrentTick() >= var2.endsAt) {
               this.release(var1, "expired");
            } else {
               Location var4 = var1.getEyeLocation();
               Location var5 = var3.getLocation().add(0.0, 1.0, 0.0);
               double var6 = this.plugin.configs().main().getDouble(this.path() + ".range", 75.0);
               double var8 = var4.distance(var5);
               if (var8 > var6) {
                  this.release(var1, "snapped");
               } else {
                  this.chain.draw(var1, var4, var5);
               }
            }
         } else {
            this.release(var1, "gone");
         }
      }
   }

   private void release(Player var1, String var2) {
      TetherTrident.Tether var3 = this.tethers.remove(var1.getUniqueId());
      if (var3 != null) {
         if (var3.chain != null) {
            var3.chain.cancel();
         }

         Player var4 = Bukkit.getPlayer(var3.target);
         Location var5 = var1.isOnline() ? var1.getEyeLocation() : null;
         Location var6 = var4 != null && var1.isOnline() && Bukkit.isOwnedByCurrentRegion(var4) && var4.getWorld().equals(var1.getWorld()) ? var4.getLocation().add(0.0, 1.0, 0.0) : null;
         this.chain.release(var1, var5, var6, var2.equals("snapped"));
         if (var1.isOnline()) {
            this.plugin.cooldownBar().flash(var1, this.plugin.configs().msg("weapon.tether-" + var2));
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onClick(PlayerInteractEvent var1) {
      if (var1.getHand() == EquipmentSlot.HAND && (var1.getAction() == Action.RIGHT_CLICK_AIR || var1.getAction() == Action.RIGHT_CLICK_BLOCK)) {
         Player var2 = var1.getPlayer();
         TetherTrident.Tether var3 = this.tethers.get(var2.getUniqueId());
         if (var3 != null) {
            ItemStack var4 = var1.getItem();
            if (var4 != null && var4.getType() == Material.TRIDENT) {
               var1.setCancelled(true);
               var1.setUseItemInHand(Result.DENY);
               int var5 = Bukkit.getCurrentTick();
               if (var5 >= var3.nextYank) {
                  Player var6 = Bukkit.getPlayer(var3.target);
                  if (var6 != null && Bukkit.isOwnedByCurrentRegion(var6) && !this.inSpawn(var6) && !this.inSpawn(var2)) {
                     FileConfiguration var7 = this.plugin.configs().main();
                     var3.nextYank = var5 + Math.max(4, var7.getInt(this.path() + ".yank-cooldown-ticks", 30));
                     if (--var3.yanksLeft <= 0) {
                        this.reel(var2, var6, var2.isSneaking());
                        this.release(var2, "spent");
                     } else {
                        this.reel(var2, var6, var2.isSneaking());
                     }
                  } else {
                     this.release(var2, "gone");
                  }
               }
            }
         }
      }
   }

   private void reel(Player var1, Player var2, boolean var3) {
      FileConfiguration var4 = this.plugin.configs().main();
      Player var5 = var3 ? var1 : var2;
      Player var6 = var3 ? var2 : var1;
      double var7 = var4.getDouble(this.path() + (var3 ? ".self-pull-speed" : ".pull-speed"), var3 ? 1.1 : 0.95);
      int var9 = Math.max(1, var4.getInt(this.path() + ".pull-ticks", 14));
      double var10 = var4.getDouble(this.path() + ".pull-lift", 0.12);
      double var12 = var4.getDouble(this.path() + ".pull-stop-distance", 2.0);
      this.plugin.antiCheat().open(var5, Math.max(var9 + 10, var4.getInt("anticheat.window-ticks", 60)));
      if (var3) {
         this.plugin.weapons().grantFallImmunity(var1);
      }

      this.drag(var5, var6, var7, var10, var12, var9);
      this.chain.reel(var1, var2, var3);
   }

   private void drag(Player var1, Player var2, double var3, double var5, double var7, int var9) {
      UUID var10 = var1.getUniqueId();
      this.stopDrag(var10);
      int[] var11 = new int[]{var9};
      ScheduledTask var13 = this.plugin.tasks().entityTimer(var1, task -> {
         if (--var11[0] >= 0 && var1.isOnline() && !var1.isDead() && var2.isOnline() && Bukkit.isOwnedByCurrentRegion(var2) && var2.getWorld().equals(var1.getWorld())) {
            Vector var11x = var2.getLocation().add(0.0, 0.8, 0.0).toVector().subtract(var1.getLocation().toVector());
            if (!(var11x.length() <= var7) && !this.inSpawn(var1) && !this.inSpawn(var2)) {
               Vector var12 = var11x.normalize().multiply(var3);
               var12.setY(var12.getY() + var5);
               var1.setVelocity(var12);
               var1.setFallDistance(0.0F);
            } else {
               this.stopDrag(var10);
            }
         } else {
            this.stopDrag(var10);
         }
      }, () -> this.stopDrag(var10), 0L, 1L);
      if (var13 != null) {
         this.drags.put(var10, var13);
      }
   }

   private void stopDrag(UUID var1) {
      ScheduledTask var2 = this.drags.remove(var1);
      if (var2 != null) {
         var2.cancel();
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      TetherTrident.Tether var2 = this.tethers.get(var1.getPlayer().getUniqueId());
      if (var2 != null) {
         this.release(var1.getPlayer(), "gone");
      }

      this.inFlight.values().removeIf(var1x -> var1x.equals(var1.getPlayer().getUniqueId()));
      this.stopDrag(var1.getPlayer().getUniqueId());
   }

   @Override
   public void tick(FlameFFACore var1) {
      if (!this.inFlight.isEmpty()) {
         this.inFlight.keySet().removeIf(var0 -> Bukkit.getEntity(var0) == null);
      }

      this.homing.keySet().removeIf(var1x -> {
         if (this.inFlight.containsKey(var1x)) {
            return false;
         } else {
            ScheduledTask var2 = this.homing.get(var1x);
            if (var2 != null) {
               var2.cancel();
            }

            return true;
         }
      });
   }

   @Override
   public void selfTest(FlameFFACore var1) {
      this.chain.selfTest(var1.arena().spawn());
   }

   @Override
   public void demo(FlameFFACore var1, Location var2) {
      double var3 = var1.configs().main().getDouble(this.path() + ".range", 75.0);
      this.chain.demo(var2, Math.min(24.0, var3));
      var1.getSLF4JLogger().info("tether test: chain drawn {} blocks", (int)Math.min(24.0, var3));
   }

   private static final class Tether {
      UUID target;
      int endsAt;
      int nextYank;
      int yanksLeft;
      ScheduledTask chain;
   }
}
