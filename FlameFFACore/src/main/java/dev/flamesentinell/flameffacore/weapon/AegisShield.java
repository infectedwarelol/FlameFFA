package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class AegisShield implements CustomWeapon, Listener {
   public static final String ID = "aegis-shield";
   private static final DustOptions GOLD = new DustOptions(Color.fromRGB(255, 210, 74), 1.0F);
   private static final DustOptions PALE = new DustOptions(Color.fromRGB(255, 243, 196), 0.7F);
   private static final Color FLASH = Color.fromRGB(255, 226, 138);
   private final FlameFFACore plugin;
   private final AegisWard ward;
   private final Map<UUID, Integer> wards = new HashMap<>();

   public AegisShield(FlameFFACore plugin) {
      this.plugin = plugin;
      this.ward = new AegisWard(plugin, this.path());
   }

   @Override
   public String id() {
      return "aegis-shield";
   }

   public AegisWard ward() {
      return this.ward;
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      return ItemStack.of(Material.SHIELD);
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.SHIELD) {
         bad.add("aegis shield is not a shield");
      }

      return bad;
   }

   public boolean up(Player player) {
      Integer until = this.wards.get(player.getUniqueId());
      return until != null && until > Bukkit.getCurrentTick();
   }

   @Override
   public boolean onAbility(FlameFFACore plugin, Player player, ItemStack held) {
      if (this.up(player)) {
         return false;
      } else {
         int seconds = Math.max(1, plugin.configs().main().getInt(this.path() + ".duration-seconds", 6));
         this.wards.put(player.getUniqueId(), Bukkit.getCurrentTick() + seconds * 20);
         World world = player.getWorld();
         world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.8F);
         world.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8F, 1.4F);
         this.hold(player, seconds * 20);
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onOffHand(PlayerInteractEvent event) {
      if (event.getHand() == EquipmentSlot.OFF_HAND) {
         if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack off = player.getInventory().getItemInOffHand();
            if (player.isSneaking() && "aegis-shield".equals(this.plugin.weapons().weaponId(off))) {
               if (!this.plugin.freeze().frozen(player)) {
                  event.setCancelled(true);
                  long left = this.plugin.weapons().cooldownLeft(player, this);
                  if (left > 0L) {
                     this.plugin.cooldownBar().flash(player, this.plugin.configs().msg("weapon.cooldown", "seconds", String.valueOf(left)));
                  } else {
                     if (this.onAbility(this.plugin, player, off)) {
                        this.plugin.weapons().startCooldown(player, this);
                     }
                  }
               }
            }
         }
      }
   }

   private void hold(Player player, int ticks) {
      BukkitTask[] handle = new BukkitTask[1];
      int[] left = new int[]{ticks};
      long every = Math.max(1, Math.min(4, this.plugin.configs().main().getInt(this.path() + ".visual.update-ticks", 2)));
      handle[0] = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         left[0] = (int)(left[0] - every);
         if (left[0] <= 0 || !player.isOnline() || !this.up(player)) {
            this.ward.clear(player);
            handle[0].cancel();
         } else if (!player.isHandRaised()) {
            this.ward.clear(player);
         } else {
            this.ward.draw(player);
         }
      }, 0L, every);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent event) {
      if (!this.wards.isEmpty() && event.getEntity() instanceof Player victim && this.up(victim)) {
         Entity damager = event.getDamager();
         Entity source = damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter ? shooter : damager;
         if (!source.equals(victim)) {
            Vector to = source.getLocation().toVector().subtract(victim.getLocation().toVector());
            to.setY(0);
            if (!(to.lengthSquared() < 0.01)) {
               Vector look = victim.getLocation().getDirection().setY(0);
               if (!(look.lengthSquared() < 0.01)) {
                  double arc = this.plugin.configs().main().getDouble(this.path() + ".arc", 0.35);
                  if (!(look.normalize().dot(to.normalize()) < arc)) {
                     event.setCancelled(true);
                     this.wards.remove(victim.getUniqueId());
                     this.ward.shatter(victim, source.getLocation().add(0.0, 1.0, 0.0));
                     if (damager instanceof Projectile projectilex) {
                        projectilex.remove();
                     }

                     double power = this.plugin.configs().main().getDouble(this.path() + ".repel-power", 1.6);
                     double lift = this.plugin.configs().main().getDouble(this.path() + ".repel-lift", 0.42);
                     source.setVelocity(to.clone().normalize().multiply(power).setY(lift));
                     this.shatter(victim, source.getLocation());
                     this.plugin.cooldownBar().flash(victim, this.plugin.configs().msg("weapon.aegis-held"));
                     if (source instanceof Player attacker) {
                        this.plugin.cooldownBar().flash(attacker, this.plugin.configs().msg("weapon.aegis-repelled", "player", victim.getName()));
                     }
                  }
               }
            }
         }
      }
   }

   private void shatter(Player victim, Location from) {
      World world = victim.getWorld();
      Vector forward = from.toVector().subtract(victim.getLocation().toVector()).setY(0);
      if (forward.lengthSquared() < 0.01) {
         forward = victim.getLocation().getDirection();
      }

      Location at = victim.getEyeLocation().add(forward.normalize().multiply(1.6));
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH);
      world.spawnParticle(Particle.DUST, at, 90, 1.1, 1.1, 0.6, 0.0, GOLD);
      world.spawnParticle(Particle.DUST, at, 40, 0.9, 0.9, 0.5, 0.0, PALE);
      world.spawnParticle(Particle.END_ROD, at, 26, 0.6, 0.6, 0.4, 0.25);
      world.spawnParticle(Particle.ENCHANTED_HIT, at, 30, 0.7, 0.7, 0.5, 0.4);
      world.playSound(at, Sound.ITEM_SHIELD_BLOCK, 1.4F, 0.7F);
      world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2F, 1.4F);
      world.playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9F, 1.2F);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onDisable(PlayerShieldDisableEvent event) {
      if (this.up(event.getPlayer())) {
         event.setCancelled(true);
         event.getPlayer().playSound(event.getPlayer(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.6F);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.wards.remove(event.getPlayer().getUniqueId());
      this.ward.clear(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDeath(PlayerDeathEvent event) {
      this.wards.remove(event.getPlayer().getUniqueId());
      this.ward.clear(event.getPlayer());
   }

   @Override
   public void tick(FlameFFACore plugin) {
      if (!this.wards.isEmpty()) {
         int now = Bukkit.getCurrentTick();
         this.wards.values().removeIf(until -> until <= now);
      }
   }

   @Override
   public void demo(FlameFFACore plugin, Location at) {
      this.ward.demo(at);
      World world = at.getWorld();
      Location centre = at.clone().add(0.0, 1.15, 0.0);
      world.spawnParticle(Particle.FLASH, centre, 1, 0.0, 0.0, 0.0, 0.0, FLASH);
      world.spawnParticle(Particle.DUST, centre, 90, 1.1, 1.1, 0.6, 0.0, GOLD);
      world.spawnParticle(Particle.END_ROD, centre, 26, 0.6, 0.6, 0.4, 0.25);
      world.playSound(at, Sound.ITEM_SHIELD_BLOCK, 1.4F, 0.7F);
   }

   @Override
   public void selfTest(FlameFFACore plugin) {
      Location at = plugin.arena().spawn();
      World world = at.getWorld();
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, GOLD);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, PALE);
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH);
      world.spawnParticle(Particle.END_ROD, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.ENCHANTED_HIT, at, 1, 0.0, 0.0, 0.0, 0.0);
      String hex = plugin.configs().main().getString(this.path() + ".visual.colour", "#ffc63a");
      Integer.parseInt(hex.replace("#", ""), 16);
      this.ward.reload();
      int entities = this.ward.entityCount();
      if (entities > 80) {
         throw new IllegalStateException("aegis ward is " + entities + " entities, keep it under 80");
      } else if (this.ward.sigilCount() == 0) {
         throw new IllegalStateException("sigil.png gave no points, the ward would have no sigil");
      }
   }
}
