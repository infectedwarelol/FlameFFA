package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class ProtectionListener implements Listener {
   private final FlameFFACore plugin;

   public ProtectionListener(FlameFFACore var1) {
      this.plugin = var1;
   }

   private boolean mayBuild(Player var1) {
      return var1.getGameMode() == GameMode.CREATIVE || var1.hasPermission("flameffacore.build");
   }

   private boolean listed(String var1, Material var2) {
      for (String var4 : this.plugin.configs().main().getStringList("protection." + var1)) {
         if (var4.equalsIgnoreCase(var2.name())) {
            return true;
         }
      }

      return false;
   }

   private boolean unlockPlace(Player var1, Material var2) {
      ConfigurationSection var3 = this.plugin.configs().main().getConfigurationSection("protection.unlock-placeable");
      if (var3 == null) {
         return false;
      } else {
         List var4 = null;

         for (String var6 : var3.getKeys(false)) {
            boolean var7 = false;

            for (String var9 : var3.getStringList(var6)) {
               if (var9.equalsIgnoreCase(var2.name())) {
                  var7 = true;
               }
            }

            if (var7) {
               if (var4 == null) {
                  var4 = this.plugin.ranks().unlockedRanks(var1);
               }

               if (var4.contains(var6)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static boolean isWater(Block var0) {
      return var0.getType() == Material.WATER ? true : var0.getBlockData() instanceof Waterlogged var1 && var1.isWaterlogged();
   }

   private boolean allowedHere(Player var1, Location var2) {
      return !this.plugin.arena().inSafezone(var2) && !this.plugin.arena().inSafezone(var1.getLocation());
   }

   private void deny(Player var1) {
      var1.sendMessage(this.plugin.configs().msg("protection.denied"));
   }

   private void denyBreak(Player var1) {
      var1.sendMessage(this.plugin.configs().msg("protection.no-break"));
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent var1) {
      if (!this.mayBuild(var1.getPlayer())) {
         Block var2 = var1.getBlock();
         if (this.listed("breakable", var2.getType())
            || (Tag.RAILS.isTagged(var2.getType()) || var2.getType() == Material.FIRE) && this.unlockPlace(var1.getPlayer(), var2.getType())) {
            if (!this.plugin.configs().main().getBoolean("protection.break-drops", false)) {
               var1.setDropItems(false);
               var1.setExpToDrop(0);
            }

            this.plugin.sweeper().broke(var2);
         } else {
            var1.setCancelled(true);
            this.denyBreak(var1.getPlayer());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent var1) {
      if (!this.mayBuild(var1.getPlayer())) {
         Block var2 = var1.getBlock();
         if ((this.listed("placeable", var2.getType()) || this.unlockPlace(var1.getPlayer(), var2.getType()))
            && this.allowedHere(var1.getPlayer(), var2.getLocation())) {
            BlockState var3 = var1.getBlockReplacedState();
            if (!var3.getType().isAir() && var3.getType() != Material.WATER) {
               this.plugin.sweeper().replaced(var2, var3.getBlockData());
            } else {
               this.plugin.sweeper().placed(var2);
            }
         } else {
            var1.setCancelled(true);
            this.deny(var1.getPlayer());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onRailPlaced(BlockPlaceEvent var1) {
      Material var2 = var1.getBlock().getType();
      if (Tag.RAILS.isTagged(var2) || var2 == Material.FIRE || var2 == Material.SOUL_FIRE) {
         this.plugin.sweeper().placed(var1.getBlock());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBucketEmpty(PlayerBucketEmptyEvent var1) {
      if (!this.mayBuild(var1.getPlayer())) {
         Block var2 = var1.getBlock();
         if (this.listed("buckets", var1.getBucket()) && this.allowedHere(var1.getPlayer(), var2.getLocation())) {
            Block var3 = var1.getBlockClicked().getRelative(var1.getBlockFace());
            this.plugin.tasks().region(var2.getLocation(), () -> {
               for (Block var6 : new Block[]{var2, var3}) {
                  if (var6.getType() == Material.WATER) {
                     this.plugin.sweeper().placed(var6);
                  } else if (var6.getBlockData() instanceof Waterlogged var7 && var7.isWaterlogged()) {
                     this.plugin.sweeper().waterlogged(var6);
                  }
               }
            });
         } else {
            var1.setCancelled(true);
            this.deny(var1.getPlayer());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBucketFill(PlayerBucketFillEvent var1) {
      if (!this.mayBuild(var1.getPlayer())) {
         Block var2 = isWater(var1.getBlockClicked()) ? var1.getBlockClicked() : var1.getBlock();
         if (isWater(var2) && this.listed("buckets", Material.WATER_BUCKET) && this.allowedHere(var1.getPlayer(), var2.getLocation())) {
            this.plugin.sweeper().broke(var2);
         } else {
            var1.setCancelled(true);
            this.deny(var1.getPlayer());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onHanging(HangingBreakByEntityEvent var1) {
      if (!(var1.getRemover() instanceof Player var2 && this.mayBuild(var2))) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onExplode(EntityExplodeEvent var1) {
      var1.blockList().clear();
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBlockExplode(BlockExplodeEvent var1) {
      var1.blockList().clear();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onIgnite(BlockIgniteEvent var1) {
      if (var1.getCause() == IgniteCause.FLINT_AND_STEEL && var1.getPlayer() != null) {
         Block var2 = var1.getBlock();
         this.plugin.tasks().region(var2.getLocation(), () -> {
            if (var2.getType() == Material.FIRE || var2.getType() == Material.SOUL_FIRE) {
               this.plugin.sweeper().placed(var2);
            }
         });
      }
   }

   private boolean fireHarmless(Entity var1) {
      return this.plugin.configs().main().getBoolean("protection.fire-harmless", true) && (var1 instanceof LivingEntity || var1 instanceof Item);
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onFireLight(EntityCombustByBlockEvent var1) {
      Block var2 = var1.getCombuster();
      if (var2 != null && (var2.getType() == Material.FIRE || var2.getType() == Material.SOUL_FIRE) && this.fireHarmless(var1.getEntity())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onFireHurt(EntityDamageEvent var1) {
      if (var1.getCause() == DamageCause.FIRE && this.fireHarmless(var1.getEntity())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onFlintOnLight(PlayerInteractEvent var1) {
      if (var1.getAction() == Action.RIGHT_CLICK_BLOCK
         && var1.getClickedBlock() != null
         && var1.getItem() != null
         && var1.getItem().getType() == Material.FLINT_AND_STEEL) {
         Block var2 = var1.getClickedBlock().getRelative(var1.getBlockFace());
         if (var2.getType() == Material.LIGHT) {
            Player var3 = var1.getPlayer();
            if (this.mayBuild(var3) || this.listed("placeable", Material.FIRE) || this.unlockPlace(var3, Material.FIRE)) {
               if (!this.mayBuild(var3) && !this.allowedHere(var3, var2.getLocation())) {
                  this.deny(var3);
               } else {
                  BlockData var4 = var2.getBlockData().clone();
                  var2.setType(Material.FIRE, true);
                  if (var2.getType() != Material.FIRE) {
                     var2.setBlockData(var4, false);
                  } else {
                     var1.setCancelled(true);
                     this.plugin.sweeper().replaced(var2, var4);
                     var2.getWorld().playSound(var2.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_FLINTANDSTEEL_USE, 1.0F, 1.0F);
                     if (var3.getGameMode() != GameMode.CREATIVE) {
                        ItemStack var5 = var1.getItem();
                        var3.getInventory().setItem(var1.getHand(), var5.damage(1, var3));
                     }

                     var3.swingHand(var1.getHand());
                  }
               }
            }
         }
      }
   }
}
