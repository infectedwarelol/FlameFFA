package dev.flamesentinell.flameffacore.weapon;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.SkinParts;
import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.entity.TeleportFlag.Relative;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import dev.flamesentinell.flameffacore.util.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class ProjectionSpear implements CustomWeapon {
   public static final String ID = "projection-spear";
   public static final String GHOST_TAG = "flameffacore_ghost";

   @Override
   public String id() {
      return "projection-spear";
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      ItemStack spear = ItemStack.of(Material.NETHERITE_SPEAR);
      spear.addUnsafeEnchantment(Enchantment.LUNGE, 3);
      spear.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
      return spear;
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.NETHERITE_SPEAR) {
         bad.add("projection spear is not a netherite spear");
      }

      if (built.getEnchantmentLevel(Enchantment.LUNGE) != 3) {
         bad.add("projection spear is missing Lunge III");
      }

      if (built.getEnchantmentLevel(Enchantment.SHARPNESS) != 5) {
         bad.add("projection spear is missing Sharpness V");
      }

      return bad;
   }

   @Override
   public boolean onAbility(FlameFFACore plugin, Player player, ItemStack held) {
      FileConfiguration cfg = plugin.configs().main();
      double base = cfg.getDouble(this.path() + ".range", 15.0);
      double boost = cfg.getDouble(this.path() + ".range-per-speed", 20.0);
      double maxRange = cfg.getDouble(this.path() + ".max-range", 40.0);
      int frames = Math.max(1, cfg.getInt(this.path() + ".frames", 6));
      int travel = Math.max(2, cfg.getInt(this.path() + ".travel-ticks", 4));
      int linger = Math.max(0, cfg.getInt(this.path() + ".linger-ticks", 8));
      Location before = player.getLocation();
      plugin.tasks().entity(player, () -> {
         if (player.isOnline() && !player.isDead()) {
            double speed = player.getWorld().equals(before.getWorld()) ? player.getLocation().distance(before) : 0.0;
            double range = Math.min(maxRange, base + speed * boost);
            this.project(plugin, player, held, range, frames, travel - 1, linger);
         }
      });
      return true;
   }

   private void project(FlameFFACore plugin, Player player, ItemStack held, double range, int frames, int travel, int linger) {
      Location from = player.getLocation();
      Vector dir = from.getDirection().normalize();
      Location target = this.aim(player, range);
      if (target != null) {
         final Location settled = this.settle(target, dir);
         Vector delta = settled.toVector().subtract(from.toVector());
         double distance = delta.length();
         if (!(distance < 0.5)) {
            Vector step = delta.clone().normalize();
            double spacing = distance / (frames + 1);
            float yaw = from.getYaw();
            float pitch = from.getPitch();
            List<Mannequin> ghosts = new CopyOnWriteArrayList<>();

            for (int i = 1; i <= frames; i++) {
               Location at = from.clone().add(step.clone().multiply(i * spacing));
               at.setYaw(yaw);
               at.setPitch(pitch);
               long delay = Math.round((double)(i - 1) * travel / frames);
               plugin.tasks().regionLater(at, () -> {
                  if (player.isOnline() && Bukkit.isOwnedByCurrentRegion(player)) {
                     ghosts.add(this.ghost(player, at, held));
                  }
               }, delay);
            }

            plugin.tasks().entityLater(player, () -> {
               if (player.isOnline() && !player.isDead()) {
                  Location land = settled.clone();
                  land.setYaw(player.getLocation().getYaw());
                  land.setPitch(player.getLocation().getPitch());
                  float fall = player.getFallDistance();
                  TeleportFlag[] keep = new TeleportFlag[]{Relative.VELOCITY_X, Relative.VELOCITY_Y, Relative.VELOCITY_Z, Relative.VELOCITY_ROTATION};
                  if (Tasks.FOLIA) {
                     player.teleportAsync(land, TeleportCause.PLUGIN, keep).thenAccept(moved -> plugin.tasks().runOn(player, () -> player.setFallDistance(fall)));
                  } else {
                     player.teleport(land, TeleportCause.PLUGIN, keep);
                     player.setFallDistance(fall);
                  }
               } else {
                  clear(plugin, ghosts);
               }
            }, travel);
            plugin.tasks().globalLater(() -> clear(plugin, ghosts), travel + linger);
         }
      }
   }

   private static void clear(FlameFFACore plugin, List<Mannequin> ghosts) {
      for (Mannequin ghost : ghosts) {
         if (ghost != null) {
            plugin.tasks().runOn(ghost, () -> {
               if (ghost.isValid()) {
                  ghost.remove();
               }
            });
         }
      }

      ghosts.clear();
   }

   private Location aim(Player player, double range) {
      Location eye = player.getEyeLocation();
      Vector dir = eye.getDirection().normalize();
      RayTraceResult hit = player.rayTraceBlocks(range);
      if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
         Location face = hit.getHitBlock().getRelative(hit.getHitBlockFace()).getLocation();
         return face.add(0.5, 0.0, 0.5);
      } else {
         Location out = eye.clone().add(dir.clone().multiply(range));
         return out.subtract(0.0, player.getEyeHeight(), 0.0);
      }
   }

   private Location settle(Location target, Vector dir) {
      Location spot = target.clone();
      Vector back = dir.clone().multiply(-0.5);

      for (int i = 0; i < 8; i++) {
         if (spot.getBlock().isPassable() && spot.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
            return spot;
         }

         spot.add(back);
      }

      return spot;
   }

   private Mannequin ghost(Player player, Location at, ItemStack held) {
      return (Mannequin)at.getWorld().spawn(at, Mannequin.class, m -> {
         m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
         SkinParts parts = (SkinParts)player.getClientOption(ClientOption.SKIN_PARTS);
         if (parts != null) {
            m.setSkinParts(parts);
         }

         m.setMainHand(player.getMainHand());
         m.setDescription(Component.empty());
         m.setCustomNameVisible(false);
         m.setImmovable(true);
         m.setGravity(false);
         m.setSilent(true);
         m.setPersistent(false);
         m.setCollidable(false);
         m.setInvulnerable(true);
         m.addScoreboardTag("flameffacore_ghost");
         Pose pose = player.getPose();
         if (Mannequin.validPoses().contains(pose)) {
            m.setPose(pose, true);
         }

         EntityEquipment equip = m.getEquipment();
         PlayerInventory mine = player.getInventory();
         equip.setHelmet(copy(mine.getHelmet()));
         equip.setChestplate(copy(mine.getChestplate()));
         equip.setLeggings(copy(mine.getLeggings()));
         equip.setBoots(copy(mine.getBoots()));
         equip.setItem(EquipmentSlot.HAND, held.clone());
         equip.setItem(EquipmentSlot.OFF_HAND, copy(mine.getItemInOffHand()));
      });
   }

   private static ItemStack copy(ItemStack worn) {
      return worn != null && !worn.getType().isAir() ? worn.clone() : null;
   }
}
