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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class OverkillMace implements CustomWeapon, Listener {
   public static final String ID = "overkill-mace";
   private final FlameFFACore plugin;
   private final OverkillFx fx;
   private final Map<UUID, OverkillMace.State> states = new ConcurrentHashMap<>();

   public OverkillMace(FlameFFACore plugin) {
      this.plugin = plugin;
      this.fx = new OverkillFx(plugin, this.path());
   }

   @Override
   public String id() {
      return "overkill-mace";
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      ItemStack mace = ItemStack.of(Material.MACE);
      this.base(mace);
      return mace;
   }

   private void base(ItemStack mace) {
      mace.removeEnchantment(Enchantment.FIRE_ASPECT);
      mace.addUnsafeEnchantment(Enchantment.DENSITY, 5);
      mace.addUnsafeEnchantment(Enchantment.WIND_BURST, 1);
   }

   private void ignite(ItemStack mace) {
      FileConfiguration cfg = this.plugin.configs().main();
      mace.addUnsafeEnchantment(Enchantment.DENSITY, cfg.getInt(this.path() + ".density-level", 10));
      mace.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, cfg.getInt(this.path() + ".fire-aspect-level", 3));
      mace.addUnsafeEnchantment(Enchantment.WIND_BURST, cfg.getInt(this.path() + ".wind-burst-level", 2));
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.MACE) {
         bad.add("overkill mace is not a mace");
      }

      if (built.getEnchantmentLevel(Enchantment.DENSITY) != 5) {
         bad.add("overkill mace is missing Density V");
      }

      return bad;
   }

   private ItemStack held(Player player) {
      ItemStack main = player.getInventory().getItemInMainHand();
      return "overkill-mace".equals(this.plugin.weapons().weaponId(main)) ? main : null;
   }

   private ItemStack anywhere(Player player) {
      for (ItemStack stack : player.getInventory().getContents()) {
         if (stack != null && "overkill-mace".equals(this.plugin.weapons().weaponId(stack))) {
            return stack;
         }
      }

      return null;
   }

   private boolean buffed(Player player) {
      OverkillMace.State state = this.states.get(player.getUniqueId());
      return state != null && state.buffUntil > Bukkit.getCurrentTick();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
         ItemStack mace = this.held(attacker);
         if (mace != null && !this.buffed(attacker)) {
            if (DamageType.MACE_SMASH.equals(event.getDamageSource().getDamageType())) {
               FileConfiguration cfg = this.plugin.configs().main();
               if (this.plugin.weapons().cooldownLeft(attacker, this) <= 0L) {
                  int now = Bukkit.getCurrentTick();
                  int window = Math.max(1, cfg.getInt(this.path() + ".stack-window-seconds", 6)) * 20;
                  int need = Math.max(1, cfg.getInt(this.path() + ".stacks-needed", 5));
                  OverkillMace.State state = this.states.computeIfAbsent(attacker.getUniqueId(), k -> new OverkillMace.State());
                  if (state.stackUntil <= now) {
                     state.stacks = 0;
                  }

                  state.stacks++;
                  state.stackUntil = now + window;
                  if (state.stacks >= need) {
                     this.engage(attacker, state, mace);
                  } else {
                     this.fx.stack(attacker, victim, state.stacks, need);
                  }
               }
            }
         }
      }
   }

   private void engage(Player player, OverkillMace.State state, ItemStack mace) {
      int seconds = Math.max(1, this.plugin.configs().main().getInt(this.path() + ".duration-seconds", 15));
      state.stacks = 0;
      state.stackUntil = 0;
      state.buffUntil = Bukkit.getCurrentTick() + seconds * 20;
      this.ignite(mace);
      this.fx.ignite(player, seconds);
      this.plugin.antiCheat().open(player, seconds * 20);
   }

   private void fade(Player player, OverkillMace.State state, boolean spent) {
      state.buffUntil = 0;
      ItemStack mace = this.anywhere(player);
      if (mace != null) {
         this.base(mace);
      }

      if (!spent && player.isOnline()) {
         this.plugin.cooldownBar().flash(player, this.plugin.configs().msg("weapon.overkill-faded"));
         this.fx.fade(player);
      }
   }

   @Override
   public boolean onAbility(FlameFFACore plugin, Player player, ItemStack held) {
      OverkillMace.State state = this.states.get(player.getUniqueId());
      if (state != null && state.buffUntil > Bukkit.getCurrentTick()) {
         FileConfiguration cfg = plugin.configs().main();
         double damage = cfg.getDouble(this.path() + ".blast.damage", 30.0);
         double radius = cfg.getDouble(this.path() + ".blast.radius", 9.0);
         double knockback = cfg.getDouble(this.path() + ".blast.knockback", 1.1);
         Location at = player.getLocation();
         DamageSource source = DamageSource.builder(DamageType.GENERIC_KILL).withCausingEntity(player).withDirectEntity(player).build();
         int hit = 0;

         for (Player victim : at.getWorld().getNearbyPlayers(at, radius)) {
            if (!victim.equals(player) && !victim.isDead() && victim.getGameMode() != GameMode.SPECTATOR && !plugin.arena().inSafezone(victim.getLocation())) {
               Vector push = victim.getLocation().toVector().subtract(at.toVector());
               if (push.lengthSquared() < 0.01) {
                  push = player.getLocation().getDirection();
               }

               victim.setVelocity(push.normalize().multiply(knockback).setY(0.6));
               victim.damage(damage, source);
               hit++;
            }
         }

         this.blast(at, radius);
         if (hit > 0) {
            plugin.cooldownBar().flash(player, plugin.configs().msg("weapon.overkill-blast", "hit", String.valueOf(hit)));
         }

         this.fade(player, state, true);
         this.fx.release(player);
         return true;
      } else {
         plugin.cooldownBar().flash(player, plugin.configs().msg("weapon.overkill-not-ready"));
         return false;
      }
   }

   private void blast(Location at, double radius) {
      this.fx.nova(at, radius);
   }

   @Override
   public void tick(FlameFFACore plugin) {
      if (!this.states.isEmpty()) {
         this.states.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
      }
   }

   @Override
   public void tick(FlameFFACore plugin, Player player) {
      OverkillMace.State state = this.states.get(player.getUniqueId());
      if (state != null) {
         int now = Bukkit.getCurrentTick();
         int need = Math.max(1, plugin.configs().main().getInt(this.path() + ".stacks-needed", 5));
         if (state.buffUntil > now) {
            this.fx.aura(player, state.buffUntil - now);
            int total = Math.max(1, plugin.configs().main().getInt(this.path() + ".duration-seconds", 15)) * 20;
            int left = state.buffUntil - now;
            this.fx.holdLit(player, (double)left / total, (left + 19) / 20);
         } else if (state.buffUntil > 0) {
            this.fade(player, state, false);
            this.fx.release(player);
            plugin.weapons().startCooldown(player, this);
         } else {
            if (state.stacks > 0 && state.stackUntil <= now) {
               state.stacks = 0;
            }

            if (state.stacks == 0) {
               this.fx.release(player);
               this.states.remove(player.getUniqueId(), state);
            } else {
               this.fx.hold(player, state.stacks, need);
            }
         }
      }
   }

   private void forget(Player player) {
      this.fx.release(player);
      OverkillMace.State state = this.states.remove(player.getUniqueId());
      if (state != null && state.buffUntil > 0) {
         ItemStack mace = this.anywhere(player);
         if (mace != null) {
            this.base(mace);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.forget(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDeath(PlayerDeathEvent event) {
      this.forget(event.getPlayer());
   }

   @Override
   public void demo(FlameFFACore plugin, Location at) {
      this.blast(at, plugin.configs().main().getDouble(this.path() + ".blast.radius", 9.0));
   }

   @Override
   public void selfTest(FlameFFACore plugin) {
      this.fx.selfTest(plugin.arena().spawn());
      DamageSource.builder(DamageType.GENERIC_KILL).build();
   }

   private static final class State {
      int stacks;
      int stackUntil;
      int buffUntil;
   }
}
