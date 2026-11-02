package dev.flamesentinell.flameffacore.weapon;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;

public interface CustomWeapon {
   String id();

   default String path() {
      return "weapons." + this.id();
   }

   default CustomWeapon.Trigger trigger() {
      return CustomWeapon.Trigger.SNEAK_CLICK;
   }

   ItemStack build(FlameFFACore var1);

   default boolean onAbility(FlameFFACore plugin, Player player, ItemStack held) {
      return false;
   }

   default boolean onHoldStart(FlameFFACore plugin, Player player, ItemStack held) {
      return false;
   }

   default boolean chargesWhileHeld(FlameFFACore plugin) {
      return false;
   }

   default int maxHoldTicks(FlameFFACore plugin) {
      return 20;
   }

   default boolean onRelease(FlameFFACore plugin, Player player, boolean fire) {
      return false;
   }

   default boolean onThrow(FlameFFACore plugin, Player player, Projectile projectile) {
      return false;
   }

   default void tick(FlameFFACore plugin) {
   }

   default void demo(FlameFFACore plugin, Location at) {
   }

   default void selfTest(FlameFFACore plugin) {
   }

   default List<String> check(ItemStack built) {
      return List.of();
   }

   public static enum Trigger {
      SNEAK_CLICK,
      CLICK,
      HOLD_CLICK,
      THROW;
   }
}
