package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public final class DashMace extends DashWeapon {
   public static final String ID = "dash-mace";

   @Override
   public String id() {
      return "dash-mace";
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      ItemStack mace = ItemStack.of(Material.MACE);
      mace.addUnsafeEnchantment(Enchantment.DENSITY, 5);
      return mace;
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.MACE) {
         bad.add("dash mace is not a mace");
      }

      if (built.getEnchantmentLevel(Enchantment.DENSITY) != 5) {
         bad.add("dash mace is missing Density V");
      }

      return bad;
   }
}
