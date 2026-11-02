package dev.flamesentinell.flameffacore.weapon;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.item.ItemKeys;
import dev.flamesentinell.flameffacore.util.Text;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

public final class WeaponRegistry implements Listener {
   private static final String COOLDOWN = "weapon:";
   private static final int RELEASE_GAP = 6;
   public static final String GROUND = "ground";
   private final FlameFFACore plugin;
   private final Map<String, CustomWeapon> weapons = new LinkedHashMap<>();
   private final Map<UUID, WeaponRegistry.Hold> holds = new HashMap<>();
   private final Set<UUID> fallImmune = new HashSet<>();

   public WeaponRegistry(FlameFFACore var1) {
      this.plugin = var1;
      this.register(new ProjectionSpear());
      this.register(new CometSpear(var1));
      this.register(new DashPotion());
      this.register(new PhoenixMace(var1));
      this.register(new TetherTrident(var1));
      this.register(new OverkillMace(var1));
      this.register(new AegisShield(var1));
      this.register(new DashSword());
      this.register(new DashSpear());
      this.register(new DashMace());
      this.register(new CobwebAxe());
      this.register(new SaturationCandle());
      this.register(new StunMace(var1));
      this.register(new RocketMace(var1));
      this.register(new ReaperScythe(var1));
   }

   private void register(CustomWeapon var1) {
      this.weapons.put(var1.id(), var1);
      if (var1 instanceof Listener var2) {
         Bukkit.getPluginManager().registerEvents(var2, this.plugin);
      }
   }

   public Set<String> ids() {
      return this.weapons.keySet();
   }

   public CustomWeapon get(String var1) {
      return var1 == null ? null : this.weapons.get(var1.toLowerCase(Locale.ROOT));
   }

   public ItemStack mint(String var1) {
      CustomWeapon var2 = this.get(var1);
      if (var2 == null) {
         return null;
      } else {
         ItemStack var3 = var2.build(this.plugin);
         FileConfiguration var4 = this.plugin.configs().main();
         var3.editMeta(
            var2x -> {
               var2x.setUnbreakable(true);
               String var3x = var4.getString(var2.path() + ".name");
               if (var3x != null) {
                  var2x.displayName(Text.parse(var3x).decoration(TextDecoration.ITALIC, false));
               }

               ArrayList var4x = new ArrayList();

               for (String var6 : var4.getStringList(var2.path() + ".lore")) {
                  var4x.add(
                     Text.parse(
                           var6,
                           "cooldown",
                           String.valueOf(var4.getInt(var2.path() + ".cooldown-seconds", 0)),
                           "duration",
                           String.valueOf(var4.getInt(var2.path() + ".duration-seconds", 0)),
                           "range",
                           String.valueOf((int)var4.getDouble(var2.path() + ".range", 0.0)),
                           "size",
                           String.valueOf(var4.getInt(var2.path() + ".size", 0)),
                           "level_roman",
                           roman(var4.getInt(var2.path() + ".level", 1)),
                           "stun",
                           String.format(Locale.ROOT, "%.1f", var4.getInt(var2.path() + ".stun-ticks", 0) / 20.0)
                        )
                        .decoration(TextDecoration.ITALIC, false)
                  );
               }

               if (!var4x.isEmpty()) {
                  var2x.lore(var4x);
               }

               var2x.getPersistentDataContainer().set(ItemKeys.WEAPON, PersistentDataType.STRING, var2.id());
            }
         );
         return var3;
      }
   }

   public boolean isWeapon(ItemStack var1) {
      return this.weaponId(var1) != null;
   }

   public String weaponId(ItemStack var1) {
      return var1 != null && !var1.getType().isAir() && var1.hasItemMeta()
         ? (String)var1.getPersistentDataContainer().get(ItemKeys.WEAPON, PersistentDataType.STRING)
         : null;
   }

   private static String roman(int var0) {
      String[] var1 = new String[]{"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
      return var0 >= 1 && var0 <= 10 ? var1[var0 - 1] : String.valueOf(var0);
   }

   private static String bucket(CustomWeapon var0, String var1) {
      return "weapon:" + var0.id() + (var1 == null ? "" : ":" + var1);
   }

   public long cooldownLeft(Player var1, CustomWeapon var2) {
      return this.cooldownLeft(var1, var2, null);
   }

   public long cooldownLeft(Player var1, CustomWeapon var2, String var3) {
      return this.plugin.cooldowns().remaining(var1.getUniqueId(), bucket(var2, var3));
   }

   public void startCooldown(Player var1, CustomWeapon var2) {
      this.startCooldown(var1, var2, null);
   }

   public void startCooldownSeconds(Player var1, CustomWeapon var2, int var3) {
      this.plugin.cooldowns().set(var1.getUniqueId(), bucket(var2, null), var3);
   }

   public void startCooldown(Player var1, CustomWeapon var2, String var3) {
      String var4 = var3 == null ? "cooldown-seconds" : var3 + "-cooldown-seconds";
      int var5 = this.plugin.configs().main().getInt(var2.path() + "." + var4, 0);
      this.plugin.cooldowns().set(var1.getUniqueId(), bucket(var2, var3), var5);
   }

   public List<WeaponRegistry.Cooling> hotbar(Player var1) {
      ArrayList var2 = new ArrayList(6);
      HashSet var3 = new HashSet();
      PlayerInventory var4 = var1.getInventory();
      FileConfiguration var5 = this.plugin.configs().main();

      for (int var6 = 0; var6 < 9; var6++) {
         CustomWeapon var7 = this.get(this.weaponId(var4.getItem(var6)));
         if (var7 != null && var3.add(var7.id())) {
            String var8 = "cooldown-seconds";
            long var9 = this.cooldownLeft(var1, var7, null);
            if (var1.isOnGround() && var5.contains(var7.path() + ".ground-cooldown-seconds")) {
               var8 = "ground-cooldown-seconds";
               var9 = this.cooldownLeft(var1, var7, "ground");
            }

            int var11 = var5.getInt(var7.path() + "." + var8, 0);
            var2.add(
               new WeaponRegistry.Cooling(
                  var5.getString(var7.path() + ".short-name", var7.id()),
                  var5.getString(var7.path() + ".hud-colour", "<white>"),
                  var9,
                  (int)Math.max((long)var11, var9)
               )
            );
         }
      }

      return var2;
   }

   public boolean isHolding(Player var1) {
      return this.holds.containsKey(var1.getUniqueId());
   }

   public void grantFallImmunity(Player var1) {
      this.fallImmune.add(var1.getUniqueId());
      this.plugin.antiCheat().open(var1, this.plugin.configs().main().getInt("anticheat.launch-ticks", 200));
   }

   private void exempt(Player var1) {
      this.plugin.antiCheat().open(var1, this.plugin.configs().main().getInt("anticheat.window-ticks", 60));
   }

   public void tick() {
      for (CustomWeapon var2 : this.weapons.values()) {
         var2.tick(this.plugin);
      }

      if (!this.holds.isEmpty()) {
         int var8 = Bukkit.getCurrentTick();
         Iterator var9 = this.holds.entrySet().iterator();

         while (var9.hasNext()) {
            Entry var3 = (Entry)var9.next();
            WeaponRegistry.Hold var4 = (WeaponRegistry.Hold)var3.getValue();
            Player var5 = Bukkit.getPlayer((UUID)var3.getKey());
            if (var5 == null) {
               var9.remove();
            } else {
               boolean var6 = var4.charges ? var8 - var4.started > 2 && !var5.isHandRaised() : var8 - var4.lastClick > 6;
               boolean var7 = var8 - var4.started >= var4.weapon.maxHoldTicks(this.plugin);
               if (var6 || var7 || var5.isDead() || var5.isOnGround()) {
                  var9.remove();
                  if (var4.weapon.onRelease(this.plugin, var5, !var5.isDead())) {
                     this.exempt(var5);
                     this.startCooldown(var5, var4.weapon);
                  }
               }
            }
         }
      }

      if (!this.fallImmune.isEmpty()) {
         this.fallImmune.removeIf(var0 -> {
            Player var1 = Bukkit.getPlayer(var0);
            return var1 == null || var1.isOnGround() || var1.isInWater();
         });
      }
   }

   private void dropHold(Player var1, boolean var2) {
      WeaponRegistry.Hold var3 = this.holds.remove(var1.getUniqueId());
      if (var3 != null) {
         var3.weapon.onRelease(this.plugin, var1, var2);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onUse(PlayerInteractEvent var1) {
      if (var1.getHand() == EquipmentSlot.HAND && (var1.getAction() == Action.RIGHT_CLICK_AIR || var1.getAction() == Action.RIGHT_CLICK_BLOCK)) {
         ItemStack var2 = var1.getItem();
         CustomWeapon var3 = this.get(this.weaponId(var2));
         if (var3 != null && var3.trigger() != CustomWeapon.Trigger.THROW) {
            Player var4 = var1.getPlayer();
            boolean var5 = var3.trigger() == CustomWeapon.Trigger.HOLD_CLICK;
            boolean var6 = var5 && this.holds.containsKey(var4.getUniqueId());
            if ((var6 || var4.isSneaking() || var3.trigger() == CustomWeapon.Trigger.CLICK)
               && (var1.getClickedBlock() == null || !this.plugin.crateRegistry().isPlaced(var1.getClickedBlock().getLocation()))
               && !this.plugin.freeze().frozen(var4)) {
               if (var5) {
                  WeaponRegistry.Hold var7 = this.holds.get(var4.getUniqueId());
                  if (var7 != null) {
                     var7.lastClick = Bukkit.getCurrentTick();
                     if (!var7.charges) {
                        var1.setCancelled(true);
                     }
                  } else {
                     long var8 = this.cooldownLeft(var4, var3, null);
                     if (var8 == 0L && var3.onHoldStart(this.plugin, var4, var2)) {
                        this.exempt(var4);
                        boolean var15 = var3.chargesWhileHeld(this.plugin);
                        if (!var15) {
                           var1.setCancelled(true);
                        }

                        this.holds.put(var4.getUniqueId(), new WeaponRegistry.Hold(var3, Bukkit.getCurrentTick(), var15));
                     } else {
                        long var10 = this.cooldownLeft(var4, var3, "ground");
                        if (var10 == 0L && var3.onAbility(this.plugin, var4, var2)) {
                           var1.setCancelled(true);
                           this.exempt(var4);
                           this.startCooldown(var4, var3, "ground");
                        } else {
                           long var12 = var4.isOnGround() ? var10 : var8;
                           if (var12 > 0L) {
                              var1.setCancelled(true);
                              this.plugin.cooldownBar().flash(var4, this.plugin.configs().msg("weapon.cooldown", "seconds", String.valueOf(var12)));
                           }
                        }
                     }
                  }
               } else {
                  var1.setCancelled(true);
                  long var14 = this.cooldownLeft(var4, var3);
                  if (var14 > 0L) {
                     this.plugin.cooldownBar().flash(var4, this.plugin.configs().msg("weapon.cooldown", "seconds", String.valueOf(var14)));
                  } else if (var3.onAbility(this.plugin, var4, var2)) {
                     this.exempt(var4);
                     this.startCooldown(var4, var3);
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onThrow(PlayerLaunchProjectileEvent var1) {
      CustomWeapon var2 = this.get(this.weaponId(var1.getItemStack()));
      if (var2 != null && var2.trigger() == CustomWeapon.Trigger.THROW) {
         Player var3 = var1.getPlayer();
         if (this.plugin.freeze().frozen(var3)) {
            var1.setCancelled(true);
         } else {
            long var4 = this.cooldownLeft(var3, var2);
            if (var4 > 0L) {
               var1.setCancelled(true);
               this.plugin.cooldownBar().flash(var3, this.plugin.configs().msg("weapon.cooldown", "seconds", String.valueOf(var4)));
            } else if (var2.onThrow(this.plugin, var3, var1.getProjectile())) {
               this.exempt(var3);
               this.startCooldown(var3, var2);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onStopUsing(PlayerStopUsingItemEvent var1) {
      WeaponRegistry.Hold var2 = this.holds.get(var1.getPlayer().getUniqueId());
      if (var2 != null && var2.charges && var1.getTicksHeldFor() >= 2) {
         this.holds.remove(var1.getPlayer().getUniqueId());
         if (var2.weapon.onRelease(this.plugin, var1.getPlayer(), true)) {
            this.startCooldown(var1.getPlayer(), var2.weapon);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onHoldMove(PlayerMoveEvent var1) {
      if (!this.holds.isEmpty() && this.holds.containsKey(var1.getPlayer().getUniqueId())) {
         Location var2 = var1.getFrom();
         Location var3 = var1.getTo();
         if (var2.getX() != var3.getX() || var2.getY() != var3.getY() || var2.getZ() != var3.getZ()) {
            Location var4 = var2.clone();
            var4.setYaw(var3.getYaw());
            var4.setPitch(var3.getPitch());
            var1.setTo(var4);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onFall(EntityDamageEvent var1) {
      if (var1.getCause() == DamageCause.FALL && var1.getEntity() instanceof Player var2 && this.fallImmune.remove(var2.getUniqueId())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.dropHold(var1.getPlayer(), false);
      this.fallImmune.remove(var1.getPlayer().getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDeath(PlayerDeathEvent var1) {
      this.dropHold(var1.getPlayer(), false);
      this.fallImmune.remove(var1.getPlayer().getUniqueId());
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDamage(PlayerItemDamageEvent var1) {
      if (this.isWeapon(var1.getItem())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onGhostHurt(EntityDamageEvent var1) {
      if (var1.getEntity().getScoreboardTags().contains("flameffacore_ghost")) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onGhostTouch(PlayerInteractAtEntityEvent var1) {
      if (var1.getRightClicked().getScoreboardTags().contains("flameffacore_ghost")) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onGhostDeath(EntityDeathEvent var1) {
      if (var1.getEntity().getScoreboardTags().contains("flameffacore_ghost")) {
         var1.getDrops().clear();
      }
   }

   public record Cooling(String name, String colour, long left, int total) {
   }

   private static final class Hold {
      final CustomWeapon weapon;
      final int started;
      final boolean charges;
      int lastClick;

      Hold(CustomWeapon var1, int var2, boolean var3) {
         this.weapon = var1;
         this.started = var2;
         this.charges = var3;
         this.lastClick = var2;
      }
   }
}
