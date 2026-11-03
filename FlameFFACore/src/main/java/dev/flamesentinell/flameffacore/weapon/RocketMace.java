package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import dev.flamesentinell.flameffacore.util.Tasks;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RocketMace implements CustomWeapon, Listener {
   public static final String ID = "rocket-mace";
   private final FlameFFACore plugin;
   private final NamespacedKey standIn;
   private final Map<UUID, RocketMace.Flight> flights = new ConcurrentHashMap<>();
   private final Map<UUID, RocketMace.Pin> pins = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> passiveReady = new ConcurrentHashMap<>();
   private final Set<UUID> ticking = ConcurrentHashMap.newKeySet();
   private static final Key RETURN_GROUP = Key.key("flameffa", "rocket_return");

   public RocketMace(FlameFFACore var1) {
      this.plugin = var1;
      this.standIn = new NamespacedKey(var1, "rocket_return");
   }

   @Override
   public String id() {
      return "rocket-mace";
   }

   @Override
   public ItemStack build(FlameFFACore var1) {
      ItemStack var2 = ItemStack.of(Material.MACE);
      var2.addUnsafeEnchantment(Enchantment.DENSITY, 5);
      var2.setData(DataComponentTypes.ITEM_MODEL, Key.key(this.cfg().getString(this.path() + ".model", "flameffa:rocket_mace")));
      return var2;
   }

   @Override
   public List<String> check(ItemStack var1) {
      ArrayList var2 = new ArrayList();
      if (var1.getType() != Material.MACE) {
         var2.add("rocket mace is not a mace");
      }

      if (var1.getEnchantmentLevel(Enchantment.DENSITY) != 5) {
         var2.add("rocket mace is missing Density V");
      }

      if (var1.getEnchantmentLevel(Enchantment.WIND_BURST) != 0) {
         var2.add("rocket mace must not have Wind Burst");
      }

      return var2;
   }

   private FileConfiguration cfg() {
      return this.plugin.configs().main();
   }

   private boolean hittable(LivingEntity var1, Player var2) {
      if (var1 != var2 && var1.isValid() && !var1.isDead() && !(var1 instanceof ArmorStand) && !var1.isInvulnerable()) {
         if (var1.getScoreboardTags().contains("flameffacore_ghost")) {
            return false;
         } else {
            return !(var1 instanceof Player var3 && (var3.getGameMode() == GameMode.SPECTATOR || var3.getGameMode() == GameMode.CREATIVE))
               ? !this.plugin.safezone().contains(var1.getLocation())
               : false;
         }
      } else {
         return false;
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent var1) {
      if (var1.getDamager() instanceof Player var2 && var1.getEntity() instanceof Player var3) {
         if ("rocket-mace".equals(this.plugin.weapons().weaponId(var2.getInventory().getItemInMainHand()))) {
            int var9 = Bukkit.getCurrentTick();
            if (this.passiveReady.getOrDefault(var2.getUniqueId(), 0) <= var9 && !this.pins.containsKey(var3.getUniqueId())) {
               this.passiveReady.put(var2.getUniqueId(), var9 + this.cfg().getInt(this.path() + ".passive-cooldown-seconds", 30) * 20);
               int var5 = this.cfg().getInt(this.path() + ".pin-ticks", 24);
               World var6 = var3.getWorld();
               Firework var7 = (Firework)var6.spawn(var3.getEyeLocation(), Firework.class, var1x -> {
                  FireworkMeta var2x = var1x.getFireworkMeta();
                  var2x.setPower(3);
                  var2x.addEffect(FireworkEffect.builder().with(Type.BURST).withColor(new Color[]{Color.ORANGE, Color.RED}).build());
                  var1x.setFireworkMeta(var2x);
                  var1x.setShotAtAngle(true);
                  var1x.setTicksToDetonate(var5 + 40);
                  var1x.setPersistent(false);
                  var1x.setVelocity(new Vector(0.0, -0.1, 0.0));
               });
               this.pins.put(var3.getUniqueId(), new RocketMace.Pin(var7, var9 + var5));
               this.ensureTicker(var3);
               this.plugin.antiCheat().open(var3, var5 + 20);
               var6.playSound(var3.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 0.8F);
            }
         }
      }
   }

   private void tickPins(int var1, UUID only) {
      Iterator var2 = this.pins.entrySet().iterator();

      while (var2.hasNext()) {
         Entry var3 = (Entry)var2.next();
         if (!only.equals(var3.getKey())) {
            continue;
         }

         Player var4 = Bukkit.getPlayer((UUID)var3.getKey());
         RocketMace.Pin var5 = (RocketMace.Pin)var3.getValue();
         if (var4 != null && !var4.isDead() && var1 < var5.until() && !var4.isOnGround() && !var4.isInWater()) {
            Vector var6 = var4.getVelocity();
            var4.setVelocity(new Vector(var6.getX() * 0.9, -this.cfg().getDouble(this.path() + ".pin-speed", 1.2), var6.getZ() * 0.9));
            var4.setFallDistance(0.0F);
            if (var5.rocket().isValid()) {
               Tasks.move(var5.rocket(), var4.getEyeLocation().add(0.0, 0.35, 0.0));
               var5.rocket().setVelocity(new Vector(0.0, -0.1, 0.0));
            }

            var4.getWorld().spawnParticle(Particle.FIREWORK, var4.getEyeLocation().add(0.0, 0.6, 0.0), 3, 0.1, 0.1, 0.1, 0.02);
         } else {
            if (var5.rocket().isValid()) {
               var5.rocket().remove();
            }

            var2.remove();
         }
      }
   }

   @Override
   public boolean onAbility(FlameFFACore var1, Player var2, ItemStack var3) {
      if (this.flights.containsKey(var2.getUniqueId())) {
         return false;
      } else {
         PlayerInventory var4 = var2.getInventory();
         int var5 = var4.getHeldItemSlot();
         ItemStack var6 = var4.getItem(var5);
         if (var6 != null && "rocket-mace".equals(var1.weapons().weaponId(var6))) {
            RocketMace.Flight var7 = new RocketMace.Flight();
            var7.owner = var2.getUniqueId();
            var7.mace = var6.clone();
            var7.token = UUID.randomUUID().toString();
            var4.setItem(var5, this.standIn(var7));
            var2.setCooldown(RETURN_GROUP, this.cfg().getInt(this.path() + ".return-timeout-ticks", 60) + 20);
            Location var8 = var2.getEyeLocation();
            Vector var9 = var8.getDirection().normalize();
            var7.at = var8.clone().add(var9.clone().multiply(0.6));
            var7.vel = var9.multiply(this.cfg().getDouble(this.path() + ".throw-speed", 3.0));
            var7.display = (ItemDisplay)var2.getWorld()
               .spawn(
                  var7.at,
                  ItemDisplay.class,
                  var1x -> {
                     ItemStack var2x = var7.mace.clone();
                     var2x.editMeta(var0x -> var0x.setEnchantmentGlintOverride(false));
                     var1x.setItemStack(var2x);
                     var1x.setPersistent(false);
                     var1x.setTeleportDuration(1);
                     var1x.setBillboard(Billboard.FIXED);
                     var1x.setItemDisplayTransform(ItemDisplayTransform.GUI);
                     var1x.setTransformation(
                        new Transformation(
                           new Vector3f(),
                           new Quaternionf().rotationX((float)Math.toRadians(90.0)).rotateZ((float)Math.toRadians(45.0)),
                           new Vector3f(1.1F, 1.1F, 1.1F),
                           new Quaternionf()
                        )
                     );
                  }
               );
            this.flights.put(var2.getUniqueId(), var7);
            this.ensureTicker(var2);
            var2.getWorld().playSound(var8, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 1.1F);
            var2.getWorld().playSound(var8, Sound.ITEM_TRIDENT_THROW, 1.0F, 0.7F);
            return true;
         } else {
            return false;
         }
      }
   }

   private ItemStack standIn(RocketMace.Flight var1) {
      ItemStack var2 = ItemStack.of(Material.GRAY_DYE);
      var2.editMeta(
         var2x -> {
            var2x.displayName(
               MiniMessage.miniMessage()
                  .deserialize(this.cfg().getString(this.path() + ".returning-name", "<gray>Rocket Mace <dark_gray>(returning)"))
                  .decoration(TextDecoration.ITALIC, false)
            );
            var2x.getPersistentDataContainer().set(this.standIn, PersistentDataType.STRING, var1.token);
            var2x.setEnchantmentGlintOverride(false);
         }
      );
      var2.setData(DataComponentTypes.ITEM_MODEL, Key.key(this.cfg().getString(this.path() + ".model", "flameffa:rocket_mace")));
      var2.setData(DataComponentTypes.USE_COOLDOWN, (UseCooldown)UseCooldown.useCooldown(0.05F).cooldownGroup(RETURN_GROUP).build());
      return var2;
   }

   private String tokenOf(ItemStack var1) {
      return var1 != null && var1.hasItemMeta() ? (String)var1.getItemMeta().getPersistentDataContainer().get(this.standIn, PersistentDataType.STRING) : null;
   }

   private void giveBack(RocketMace.Flight var1) {
      ItemDisplay var7 = var1.display;
      if (var7 != null && var7.isValid()) {
         this.plugin.tasks().runOn(var7, var7::remove);
      }

      Player var2 = Bukkit.getPlayer(var1.owner);
      if (var2 != null) {
         var2.setCooldown(RETURN_GROUP, 0);
         PlayerInventory var3 = var2.getInventory();
         int var4 = -1;

         for (int var5 = 0; var5 < var3.getSize(); var5++) {
            if (this.tokenOf(var3.getItem(var5)) != null) {
               if (var4 < 0 && var1.token.equals(this.tokenOf(var3.getItem(var5)))) {
                  var4 = var5;
               } else {
                  var3.setItem(var5, null);
               }
            }
         }

         if (this.tokenOf(var2.getItemOnCursor()) != null) {
            var2.setItemOnCursor(null);
         }

         if (var4 >= 0) {
            var3.setItem(var4, var1.mace);
         } else {
            for (ItemStack var6 : var3.addItem(new ItemStack[]{var1.mace}).values()) {
               var2.getWorld().dropItemNaturally(var2.getLocation(), var6);
            }
         }

         var2.playSound(var2.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0F, 1.0F);
      }
   }

   private void explode(RocketMace.Flight var1, Location var2, LivingEntity var3, Player var4) {
      World var5 = var2.getWorld();
      var5.spawnParticle(Particle.EXPLOSION_EMITTER, var2, 1);
      var5.spawnParticle(Particle.FIREWORK, var2, 40, 0.3, 0.3, 0.3, 0.2);
      var5.playSound(var2, Sound.ENTITY_GENERIC_EXPLODE, 1.2F, 1.1F);
      var5.playSound(var2, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2F, 0.9F);
      if (var4 != null) {
         double var6 = this.cfg().getDouble(this.path() + ".blast-size", 4.5) / 2.0;
         ArrayList<LivingEntity> var8 = new ArrayList<>();

         for (Entity var10 : var5.getNearbyEntities(BoundingBox.of(var2, var6, var6, var6))) {
            if (var10 instanceof LivingEntity var11 && this.hittable(var11, var4)) {
               var8.add(var11);
            }
         }

         if (var3 != null && !var8.contains(var3)) {
            var8.add(var3);
         }

         if (var3 != null) {
            DamageSource var21 = DamageSource.builder(DamageType.PLAYER_EXPLOSION)
               .withCausingEntity(var4)
               .withDirectEntity(var4)
               .withDamageLocation(var2)
               .build();
            double var23 = this.cfg().getDouble(this.path() + ".damage", 47.7);
            switch (var5.getDifficulty()) {
               case EASY:
                  var23 = Math.max(var23, 2.0 * (var23 - 1.0));
                  break;
               case HARD:
                  var23 /= 1.5;
            }

            for (LivingEntity var13 : var8) {
               if (var13 instanceof Player var14) {
                  this.plugin.antiCheat().open(var14, 40);
               }

               var13.damage(var23, var21);
            }
         }

         double var22 = this.cfg().getDouble(this.path() + ".knockback", 1.2);
         double var24 = var6 * 1.75;

         for (LivingEntity var26 : var8) {
            Vector var15 = var26.getLocation().toVector().add(new Vector(0.0, 0.5, 0.0)).subtract(var2.toVector());
            double var16 = var15.length();
            if (var16 < 0.001) {
               var15 = var1.vel.clone();
            }

            double var18 = var22 * (1.0 - Math.min(var16, var24) / var24 * 0.6);
            if (var26 instanceof Player var20) {
               this.plugin.antiCheat().open(var20, 40);
            }

            var26.setVelocity(var26.getVelocity().add(var15.normalize().multiply(var18)).setY(Math.max(0.35, var26.getVelocity().getY() + 0.45 * var18)));
         }
      }
   }

   private void tickFlights(UUID only) {
      double var1 = this.cfg().getDouble(this.path() + ".hit-radius", 0.75);
      Iterator var3 = this.flights.entrySet().iterator();

      while (true) {
         RocketMace.Flight var4;
         Player var5;
         while (true) {
            if (!var3.hasNext()) {
               return;
            }

            Entry var25 = (Entry)var3.next();
            if (!only.equals(var25.getKey())) {
               continue;
            }

            var4 = (RocketMace.Flight)var25.getValue();
            var5 = Bukkit.getPlayer(var4.owner);
            if (var5 != null && var4.display != null && var4.display.isValid() && var5.getWorld() == var4.at.getWorld() && Bukkit.isOwnedByCurrentRegion(var4.at)) {
               var4.age++;
               if (!var4.back) {
                  Location var19 = var4.at.clone();
                  Vector var21 = var4.vel.clone();
                  RayTraceResult var8 = var4.at.getWorld().rayTraceBlocks(var19, var21, var21.length(), FluidCollisionMode.NEVER, true);
                  double var9 = var8 != null ? var8.getHitPosition().distance(var19.toVector()) : var21.length();
                  LivingEntity var11 = null;
                  double var12 = Double.MAX_VALUE;

                  for (LivingEntity var15 : var19.getNearbyLivingEntities(var21.length() + 3.0)) {
                     if (this.hittable(var15, var5)) {
                        RayTraceResult var16 = var15.getBoundingBox().expand(var1).rayTrace(var19.toVector(), var21, var9);
                        if (var16 != null) {
                           double var17 = var16.getHitPosition().distance(var19.toVector());
                           if (var17 < var12) {
                              var12 = var17;
                              var11 = var15;
                           }
                        }
                     }
                  }

                  if (var11 != null) {
                     Location var22 = var19.clone().add(var21.clone().normalize().multiply(var12));
                     this.explode(var4, var22, var11, var5);
                     var4.at = var22;
                     var4.back = true;
                  } else if (var8 != null) {
                     Location var23 = var8.getHitPosition().toLocation(var4.at.getWorld());
                     this.explode(var4, var23, null, var5);
                     var4.at = var23;
                     var4.back = true;
                  } else {
                     var4.at.add(var21);
                     var4.travelled = var4.travelled + var21.length();
                     if (var4.travelled >= this.cfg().getDouble(this.path() + ".range", 35.0)) {
                        var4.back = true;
                     }
                  }

                  Vector var24 = new Vector(-var4.vel.getZ(), 0.0, var4.vel.getX());
                  if (var24.lengthSquared() > 1.0E-6) {
                     var24.normalize().multiply(0.25);
                  }

                  var4.at.getWorld().spawnParticle(Particle.FIREWORK, var4.at.clone().add(var24), 2, 0.02, 0.02, 0.02, 0.01);
                  var4.at.getWorld().spawnParticle(Particle.FIREWORK, var4.at.clone().subtract(var24), 2, 0.02, 0.02, 0.02, 0.01);
                  var4.at.getWorld().spawnParticle(Particle.FLAME, var4.at, 1, 0.05, 0.05, 0.05, 0.0);
                  break;
               }

               var4.backAge++;
               Vector var6 = var5.getEyeLocation().toVector().subtract(var4.at.toVector());
               double var7 = var6.length();
               if (!(var7 < 1.5) && var4.backAge <= this.cfg().getInt(this.path() + ".return-timeout-ticks", 60)) {
                  var4.at.add(var6.normalize().multiply(Math.min(var7, this.cfg().getDouble(this.path() + ".return-speed", 3.0))));
                  var4.at.getWorld().spawnParticle(Particle.FIREWORK, var4.at, 1, 0.05, 0.05, 0.05, 0.0);
                  break;
               }

               this.giveBack(var4);
               var3.remove();
            } else {
               this.giveBack(var4);
               var3.remove();
            }
         }

         Location var20 = var4.at.clone();
         var20.setDirection(var4.back ? var5.getEyeLocation().toVector().subtract(var4.at.toVector()) : var4.vel);
         Tasks.move(var4.display, var20);
      }
   }

   @Override
   public void tick(FlameFFACore var1) {
      if (!this.passiveReady.isEmpty()) {
         int var2 = Bukkit.getCurrentTick();
         this.passiveReady.values().removeIf(var1x -> var1x <= var2);
      }
   }

   private void ensureTicker(Player player) {
      UUID uuid = player.getUniqueId();
      if (this.ticking.add(uuid)) {
         ScheduledTask ticker = this.plugin.tasks().entityTimer(player, task -> {
            if (this.pins.containsKey(uuid)) {
               this.tickPins(Bukkit.getCurrentTick(), uuid);
            }

            if (this.flights.containsKey(uuid)) {
               this.tickFlights(uuid);
            }

            if (!this.pins.containsKey(uuid) && !this.flights.containsKey(uuid)) {
               this.ticking.remove(uuid);
               task.cancel();
            }
         }, () -> this.ticking.remove(uuid), 1L, 1L);
         if (ticker == null) {
            this.ticking.remove(uuid);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDrop(PlayerDropItemEvent var1) {
      if (this.tokenOf(var1.getItemDrop().getItemStack()) != null) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onDeath(PlayerDeathEvent var1) {
      RocketMace.Flight var2 = this.flights.remove(var1.getPlayer().getUniqueId());
      List var3 = var1.getDrops();
      boolean var4 = false;

      for (int var5 = 0; var5 < var3.size(); var5++) {
         if (this.tokenOf((ItemStack)var3.get(var5)) != null) {
            if (var2 != null && !var4) {
               var3.set(var5, var2.mace);
               var4 = true;
            } else {
               var3.set(var5, null);
            }
         }
      }

      var3.removeIf(var0 -> var0 == null);
      if (var2 != null) {
         if (var2.display != null && var2.display.isValid()) {
            var2.display.remove();
         }

         if (!var4 && var1.getKeepInventory()) {
            var2.display = null;
            this.giveBack(var2);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      RocketMace.Flight var2 = this.flights.remove(var1.getPlayer().getUniqueId());
      if (var2 != null) {
         this.giveBack(var2);
      }

      RocketMace.Pin var3 = this.pins.remove(var1.getPlayer().getUniqueId());
      if (var3 != null && var3.rocket().isValid()) {
         var3.rocket().remove();
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      PlayerInventory var2 = var1.getPlayer().getInventory();

      for (int var3 = 0; var3 < var2.getSize(); var3++) {
         if (this.tokenOf(var2.getItem(var3)) != null) {
            var2.setItem(var3, null);
         }
      }
   }

   @EventHandler
   public void onDisable(PluginDisableEvent var1) {
      if (var1.getPlugin() == this.plugin) {
         for (RocketMace.Flight var3 : new ArrayList<>(this.flights.values())) {
            this.giveBack(var3);
         }

         this.flights.clear();

         for (RocketMace.Pin var5 : this.pins.values()) {
            if (var5.rocket().isValid()) {
               var5.rocket().remove();
            }
         }

         this.pins.clear();
      }
   }

   private static final class Flight {
      UUID owner;
      ItemStack mace;
      ItemDisplay display;
      Location at;
      Vector vel;
      double travelled;
      boolean back;
      int age;
      int backAge;
      String token;
   }

   private record Pin(Firework rocket, int until) {
   }
}
