package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CobwebAxe implements CustomWeapon {
   public static final String ID = "cobweb-axe";
   private static final ItemStack WEB = ItemStack.of(Material.COBWEB);

   @Override
   public String id() {
      return "cobweb-axe";
   }

   @Override
   public ItemStack build(FlameFFACore var1) {
      ItemStack var2 = ItemStack.of(Material.NETHERITE_AXE);
      var2.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
      return var2;
   }

   @Override
   public List<String> check(ItemStack var1) {
      ArrayList var2 = new ArrayList();
      if (var1.getType() != Material.NETHERITE_AXE) {
         var2.add("cobweb axe is not a netherite axe");
      }

      if (var1.getEnchantmentLevel(Enchantment.SHARPNESS) != 5) {
         var2.add("cobweb axe is missing Sharpness V");
      }

      return var2;
   }

   @Override
   public boolean onAbility(FlameFFACore var1, Player var2, ItemStack var3) {
      FileConfiguration var4 = var1.configs().main();
      int var5 = Math.max(0, (var4.getInt(this.path() + ".size", 9) - 1) / 2);
      int var6 = Math.max(0, (var4.getInt(this.path() + ".height", 9) - 1) / 2);
      Location var7 = var2.getLocation();
      World var8 = var7.getWorld();
      int var9 = var7.getBlockX();
      int var10 = var7.getBlockY();
      int var11 = var7.getBlockZ();
      int var12 = var10 + var6;
      int var13 = var10 - var6;
      if (!var2.isOnGround() && var2.getVelocity().getY() <= 0.0) {
         var12 = var10 + 1;
         var13 = var10 + 1 - var6 * 2;
      }

      for (int var14 = var9 - var5; var14 <= var9 + var5; var14++) {
         for (int var15 = var11 - var5; var15 <= var11 + var5; var15++) {
            for (int var16 = var13; var16 <= var12; var16++) {
               Block var17 = var8.getBlockAt(var14, var16, var15);
               if (var17.getType() == Material.COBWEB) {
                  var1.sweeper().broke(var17);
                  var17.setType(Material.AIR, false);
                  this.burst(var17.getLocation().add(0.5, 0.5, 0.5));
               }
            }
         }
      }

      this.sweep(var7, var5);
      var8.playSound(var7, Sound.ITEM_AXE_STRIP, 1.0F, 0.7F);
      var8.playSound(var7, Sound.BLOCK_COBWEB_BREAK, 1.0F, 0.9F);
      var8.playSound(var7, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 0.8F);
      return true;
   }

   private void burst(Location var1) {
      World var2 = var1.getWorld();
      var2.spawnParticle(Particle.ITEM, var1, 10, 0.25, 0.25, 0.25, 0.08, WEB);
      var2.spawnParticle(Particle.CLOUD, var1, 3, 0.15, 0.15, 0.15, 0.01);
   }

   private void sweep(Location var1, int var2) {
      World var3 = var1.getWorld();
      Location var4 = var1.clone().add(0.0, 0.3, 0.0);
      byte var5 = 48;

      for (int var6 = 0; var6 < var5; var6++) {
         double var7 = var6 * Math.PI * 2.0 / var5;
         var3.spawnParticle(Particle.CLOUD, var4, 0, Math.cos(var7) * 0.45, 0.02, Math.sin(var7) * 0.45, 1.0);
         if (var6 % 4 == 0) {
            var3.spawnParticle(Particle.SWEEP_ATTACK, var4.clone().add(Math.cos(var7) * 1.6, 0.7, Math.sin(var7) * 1.6), 0);
         }
      }
   }
}
