package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class DashPotion implements CustomWeapon {
   public static final String ID = "dash-potion";

   @Override
   public String id() {
      return "dash-potion";
   }

   @Override
   public CustomWeapon.Trigger trigger() {
      return CustomWeapon.Trigger.THROW;
   }

   @Override
   public ItemStack build(FlameFFACore plugin) {
      ItemStack potion = ItemStack.of(Material.SPLASH_POTION);
      potion.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.STRONG_STRENGTH));
      return potion;
   }

   @Override
   public List<String> check(ItemStack built) {
      List<String> bad = new ArrayList<>();
      if (built.getType() != Material.SPLASH_POTION) {
         bad.add("dash potion is not a splash potion");
      }

      if (!(built.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() == PotionType.STRONG_STRENGTH)) {
         bad.add("dash potion is not strength ii");
      }

      return bad;
   }

   @Override
   public boolean onThrow(FlameFFACore plugin, Player player, Projectile projectile) {
      double speed = plugin.configs().main().getDouble(this.path() + ".speed", 4.0);
      player.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(speed));
      player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0F, 1.3F);
      return true;
   }
}
