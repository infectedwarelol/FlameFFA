package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class StunMace implements CustomWeapon, Listener {
   public static final String ID = "stun-mace";
   private static final NamespacedKey SPEED_KEY = new NamespacedKey("flameffacore", "stun_speed");
   private static final NamespacedKey JUMP_KEY = new NamespacedKey("flameffacore", "stun_jump");
   private static final DustOptions STAR = new DustOptions(Color.fromRGB(255, 243, 154), 0.9F);
   private final FlameFFACore plugin;
   private final Map<UUID, Integer> stunned = new HashMap<>();
   private final Map<UUID, Integer> immune = new HashMap<>();

   public StunMace(FlameFFACore plugin) {
      this.plugin = plugin;
      Bukkit.getPluginManager().registerEvents(this, plugin);
   }

   @Override
   public String id() {
      return "stun-mace";
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      ItemStack mace = ItemStack.of(Material.MACE);
      mace.addUnsafeEnchantment(Enchantment.DENSITY, 5);
      mace.addUnsafeEnchantment(Enchantment.WIND_BURST, 1);
      return mace;
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.MACE) {
         bad.add("stun mace is not a mace");
      }

      if (built.getEnchantmentLevel(Enchantment.DENSITY) != 5) {
         bad.add("stun mace is missing Density V");
      }

      if (built.getEnchantmentLevel(Enchantment.WIND_BURST) != 1) {
         bad.add("stun mace is missing Wind Burst I");
      }

      return bad;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
         if ("stun-mace".equals(this.plugin.weapons().weaponId(attacker.getInventory().getItemInMainHand()))) {
            int now = Bukkit.getCurrentTick();
            if (this.immune.getOrDefault(victim.getUniqueId(), 0) <= now && !this.stunned.containsKey(victim.getUniqueId())) {
               FileConfiguration cfg = this.plugin.configs().main();
               int ticks = Math.max(1, cfg.getInt(this.path() + ".stun-ticks", 20));
               this.pin(victim, ticks);
               this.stunned.put(victim.getUniqueId(), now + ticks);
               this.immune.put(victim.getUniqueId(), now + ticks + cfg.getInt(this.path() + ".immune-seconds", 3) * 20);
               this.plugin.antiCheat().open(victim, ticks + 10);
               Location at = victim.getLocation();
               World world = at.getWorld();
               world.playSound(at, Sound.BLOCK_BELL_RESONATE, 1.0F, 1.6F);
               world.playSound(at, Sound.ITEM_MACE_SMASH_AIR, 0.6F, 0.7F);
               world.spawnParticle(Particle.ENCHANTED_HIT, at.clone().add(0.0, 1.6, 0.0), 12, 0.3, 0.2, 0.3, 0.1);
               this.stars(victim, 0);
            }
         }
      }
   }

   private void pin(Player player, int ticks) {
      AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
      AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
      if (speed != null) {
         speed.removeModifier(SPEED_KEY);
         speed.addTransientModifier(new AttributeModifier(SPEED_KEY, -1.0, Operation.MULTIPLY_SCALAR_1));
      }

      if (jump != null) {
         jump.removeModifier(JUMP_KEY);
         jump.addTransientModifier(new AttributeModifier(JUMP_KEY, -1.0, Operation.MULTIPLY_SCALAR_1));
      }

      player.setGravity(false);
      player.setVelocity(new Vector(0, 0, 0));
      player.setFallDistance(0.0F);
   }

   private void release(Player player) {
      AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
      AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
      if (speed != null) {
         speed.removeModifier(SPEED_KEY);
      }

      if (jump != null) {
         jump.removeModifier(JUMP_KEY);
      }

      player.setGravity(true);
   }

   public boolean isStunned(Player player) {
      return this.stunned.containsKey(player.getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onKnockback(EntityKnockbackEvent event) {
      if (event.getEntity() instanceof Player player && this.stunned.containsKey(player.getUniqueId())) {
         event.setCancelled(true);
      }
   }

   private void stars(Player player, int frame) {
      Location head = player.getLocation().add(0.0, 2.05, 0.0);
      World world = head.getWorld();

      for (int i = 0; i < 5; i++) {
         double a = frame * 0.5 + i * Math.PI * 2.0 / 5.0;
         world.spawnParticle(Particle.DUST, head.clone().add(Math.cos(a) * 0.45, 0.0, Math.sin(a) * 0.45), 1, 0.0, 0.0, 0.0, 0.0, STAR);
      }
   }

   @Override
   public void tick(FlameFFACore plugin) {
      if (this.stunned.isEmpty()) {
         if (!this.immune.isEmpty()) {
            int now = Bukkit.getCurrentTick();
            this.immune.values().removeIf(until -> until <= now);
         }
      } else {
         int now = Bukkit.getCurrentTick();
         Iterator<Entry<UUID, Integer>> it = this.stunned.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
               if (entry.getValue() > now && !player.isDead()) {
                  player.setVelocity(new Vector(0, 0, 0));
                  this.stars(player, now);
               } else {
                  this.release(player);
                  it.remove();
               }
            } else {
               it.remove();
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      if (this.stunned.remove(event.getPlayer().getUniqueId()) != null) {
         this.release(event.getPlayer());
      }

      this.immune.remove(event.getPlayer().getUniqueId());
   }

   public void shutdown() {
      for (UUID id : this.stunned.keySet()) {
         Player player = Bukkit.getPlayer(id);
         if (player != null) {
            this.release(player);
         }
      }

      this.stunned.clear();
   }
}
