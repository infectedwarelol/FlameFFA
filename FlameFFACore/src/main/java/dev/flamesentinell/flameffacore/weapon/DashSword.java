package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public final class DashSword extends DashWeapon {
   public static final String ID = "dash-sword";

   @Override
   public String id() {
      return "dash-sword";
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      ItemStack sword = ItemStack.of(Material.NETHERITE_SWORD);
      sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
      return sword;
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.NETHERITE_SWORD) {
         bad.add("dash sword is not a netherite sword");
      }

      if (built.getEnchantmentLevel(Enchantment.SHARPNESS) != 5) {
         bad.add("dash sword is missing Sharpness V");
      }

      return bad;
   }
}
