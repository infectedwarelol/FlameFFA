package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Particle.DustTransition;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class PhoenixMace implements CustomWeapon, Listener {
   public static final String ID = "phoenix-mace";
   private static final DustOptions GOLD = new DustOptions(Color.fromRGB(255, 201, 60), 1.3F);
   private static final DustOptions ORANGE = new DustOptions(Color.fromRGB(255, 122, 26), 1.2F);
   private static final DustOptions RED = new DustOptions(Color.fromRGB(232, 38, 43), 1.1F);
   private static final DustTransition EMBER = new DustTransition(Color.fromRGB(255, 224, 102), Color.fromRGB(179, 18, 10), 1.8F);
   private static final Color FLASH_GOLD = Color.fromRGB(255, 215, 74);
   private final FlameFFACore plugin;
   private final Map<UUID, PhoenixMace.Anchor> anchors = new HashMap<>();

   public PhoenixMace(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   @Override
   public String id() {
      return "phoenix-mace";
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      ItemStack mace = ItemStack.of(Material.MACE);
      mace.addUnsafeEnchantment(Enchantment.DENSITY, 5);
      mace.addUnsafeEnchantment(Enchantment.WIND_BURST, 2);
      return mace;
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.MACE) {
         bad.add("phoenix mace is not a mace");
      }

      if (built.getEnchantmentLevel(Enchantment.DENSITY) != 5) {
         bad.add("phoenix mace is missing Density V");
      }

      if (built.getEnchantmentLevel(Enchantment.WIND_BURST) != 2) {
         bad.add("phoenix mace is missing Wind Burst II");
      }

      return bad;
   }

   @Override
   public boolean onAbility(FlameFFACore plugin, Player player, ItemStack held) {
      if (this.anchors.containsKey(player.getUniqueId())) {
         return false;
      } else {
         int seconds = Math.max(1, plugin.configs().main().getInt(this.path() + ".duration-seconds", 10));
         PhoenixMace.Anchor anchor = new PhoenixMace.Anchor(
            player.getLocation().clone(), player.getHealth(), player.getFoodLevel(), player.getSaturation(), Bukkit.getCurrentTick() + seconds * 20
         );
         this.anchors.put(player.getUniqueId(), anchor);
         World world = anchor.at.getWorld();
         world.playSound(anchor.at, Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.7F);
         world.playSound(anchor.at, Sound.ENTITY_BLAZE_AMBIENT, 0.8F, 1.4F);
         this.mark(plugin, player, anchor, seconds * 20);
         return true;
      }
   }

   private void mark(FlameFFACore plugin, Player player, PhoenixMace.Anchor anchor, int ticks) {
      BukkitTask[] handle = new BukkitTask[1];
      int[] left = new int[]{ticks};
      Location c = anchor.at.clone().add(0.0, 0.1, 0.0);
      World world = c.getWorld();
      handle[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
         left[0] -= 2;
         if (left[0] >= 0 && this.anchors.get(player.getUniqueId()) == anchor) {
            double angle = left[0] * 0.25;

            for (int i = 0; i < 4; i++) {
               double a = angle + i * (Math.PI / 2);
               Location at = c.clone().add(Math.cos(a) * 1.1, 0.2 + Math.sin(a * 2.0) * 0.15, Math.sin(a) * 1.1);
               world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, i % 2 == 0 ? GOLD : RED);
            }

            world.spawnParticle(Particle.DUST, c, 2, 0.6, 0.1, 0.6, 0.0, ORANGE);
            world.spawnParticle(Particle.FLAME, c, 2, 0.4, 0.2, 0.4, 0.01);
            if (left[0] % 10 == 0) {
               world.spawnParticle(Particle.END_ROD, c.clone().add(0.0, 1.2, 0.0), 1, 0.3, 0.6, 0.3, 0.0);
            }
         } else {
            handle[0].cancel();
         }
      }, 0L, 2L);
   }

   @Override
   public void tick(FlameFFACore plugin) {
      if (!this.anchors.isEmpty()) {
         int now = Bukkit.getCurrentTick();
         Iterator<Entry<UUID, PhoenixMace.Anchor>> it = this.anchors.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, PhoenixMace.Anchor> e = it.next();
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || player.isDead()) {
               it.remove();
            } else if (now >= e.getValue().until) {
               it.remove();
               this.rebirth(plugin, player, e.getValue());
            }
         }
      }
   }

   private void rebirth(FlameFFACore plugin, Player player, PhoenixMace.Anchor anchor) {
      Location to = anchor.at.clone();
      to.setYaw(player.getLocation().getYaw());
      to.setPitch(player.getLocation().getPitch());
      player.setFallDistance(0.0F);
      player.setFireTicks(0);
      player.teleport(to, TeleportCause.PLUGIN);
      AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
      double cap = max == null ? 20.0 : max.getValue();
      player.setHealth(Math.max(1.0, Math.min(cap, anchor.health)));
      player.setFoodLevel(anchor.food);
      player.setSaturation(anchor.saturation);
      World world = to.getWorld();
      Location at = to.clone().add(0.0, 1.0, 0.0);
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH_GOLD);
      world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 120, 1.2, 1.0, 1.2, 0.0, EMBER);
      world.spawnParticle(Particle.DUST, at, 60, 1.0, 0.8, 1.0, 0.0, GOLD);
      world.spawnParticle(Particle.DUST, at, 40, 0.8, 0.8, 0.8, 0.0, ORANGE);
      world.spawnParticle(Particle.FLAME, at, 80, 0.5, 0.6, 0.5, 0.12);
      world.spawnParticle(Particle.LAVA, at, 16, 0.6, 0.4, 0.6, 0.0);
      world.spawnParticle(Particle.END_ROD, at, 30, 0.4, 0.6, 0.4, 0.2);
      world.playSound(at, Sound.ITEM_TOTEM_USE, 0.7F, 1.2F);
      world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.8F);
      world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0F, 0.7F);
      this.wings(plugin, player, 12);
   }

   private void wings(FlameFFACore plugin, Player player, int ticks) {
      BukkitTask[] handle = new BukkitTask[1];
      int[] left = new int[]{ticks};
      handle[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
         if (--left[0] >= 0 && player.isOnline()) {
            Location at = player.getLocation().add(0.0, 1.0, 0.0);
            player.getWorld().spawnParticle(Particle.DUST, at, 4, 0.5, 0.5, 0.5, 0.0, GOLD);
            player.getWorld().spawnParticle(Particle.FLAME, at, 3, 0.4, 0.4, 0.4, 0.03);
         } else {
            handle[0].cancel();
         }
      }, 0L, 1L);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onLethal(EntityDamageEvent event) {
      if (!this.anchors.isEmpty() && event.getEntity() instanceof Player player) {
         if (!(event.getFinalDamage() < player.getHealth())) {
            PhoenixMace.Anchor anchor = this.anchors.remove(player.getUniqueId());
            if (anchor != null) {
               event.setCancelled(true);
               this.rebirth(this.plugin, player, anchor);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDeath(PlayerDeathEvent event) {
      this.anchors.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.anchors.remove(event.getPlayer().getUniqueId());
   }

   @Override
   public void selfTest(FlameFFACore plugin) {
      Location at = plugin.arena().spawn();
      World world = at.getWorld();
      world.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, FLASH_GOLD);
      world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.0, 0.0, 0.0, 0.0, EMBER);
      world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, GOLD);
      world.spawnParticle(Particle.FLAME, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.LAVA, at, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticle(Particle.END_ROD, at, 1, 0.0, 0.0, 0.0, 0.0);
   }

   private static final class Anchor {
      final Location at;
      final double health;
      final int food;
      final float saturation;
      final int until;

      Anchor(Location at, double health, int food, float saturation, int until) {
         this.at = at;
         this.health = health;
         this.food = food;
         this.saturation = saturation;
         this.until = until;
      }
   }
}
