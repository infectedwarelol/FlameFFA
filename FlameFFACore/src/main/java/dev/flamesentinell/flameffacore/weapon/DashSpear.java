package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public final class DashSpear extends DashWeapon {
   public static final String ID = "dash-spear";

   @Override
   public String id() {
      return "dash-spear";
   }

   @Override
   public ItemStack build(FlameFFACore var1) {
      ItemStack var2 = ItemStack.of(Material.NETHERITE_SPEAR);
      var2.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
      var2.addUnsafeEnchantment(Enchantment.LUNGE, 3);
      return var2;
   }

   @Override
   public List<String> check(ItemStack var1) {
      ArrayList var2 = new ArrayList();
      if (var1.getType() != Material.NETHERITE_SPEAR) {
         var2.add("dash spear is not a netherite spear");
      }

      if (var1.getEnchantmentLevel(Enchantment.SHARPNESS) != 5) {
         var2.add("dash spear is missing Sharpness V");
      }

      if (var1.getEnchantmentLevel(Enchantment.LUNGE) != 3) {
         var2.add("dash spear is missing Lunge III");
      }

      return var2;
   }
}
