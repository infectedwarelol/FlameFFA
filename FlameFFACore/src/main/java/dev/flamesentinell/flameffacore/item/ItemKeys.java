package dev.flamesentinell.flameffacore.item;

import org.bukkit.NamespacedKey;

public final class ItemKeys {
   public static final NamespacedKey KIT = key("kit");
   public static final NamespacedKey TOKEN = key("token");
   public static final NamespacedKey COMPRESSED = key("compressed");
   public static final NamespacedKey VOUCHER = key("voucher");
   public static final NamespacedKey MENU = key("menu");
   public static final NamespacedKey WEAPON = key("weapon");
   public static final NamespacedKey VILLAGER_PRESET = key("villager_preset");

   private ItemKeys() {
   }

   private static NamespacedKey key(String name) {
      return new NamespacedKey("flameffacore", name);
   }
}
