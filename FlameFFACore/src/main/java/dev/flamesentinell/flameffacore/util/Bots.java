package dev.flamesentinell.flameffacore.util;

import org.bukkit.entity.Player;

public final class Bots {
   public static final String TAG = "smbot_bot";
   public static final String SPAR_TAG = "smbot_spar";

   private Bots() {
   }

   public static boolean is(Player player) {
      return player != null && player.getScoreboardTags().contains("smbot_bot");
   }

   public static boolean sparring(Player player) {
      return player != null && player.getScoreboardTags().contains("smbot_spar");
   }

   public static boolean involved(Player player) {
      return is(player) || sparring(player);
   }
}
