package dev.flamesentinell.flameffacore.kit;

import org.bukkit.inventory.ItemStack;

public record RankKit(String rankId, ItemStack[] contents, String setBy, long setAt) {
   public int filledSlots() {
      int n = 0;

      for (ItemStack stack : this.contents) {
         if (stack != null && !stack.getType().isAir()) {
            n++;
         }
      }

      return n;
   }
}
