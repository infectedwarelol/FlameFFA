package dev.flamesentinell.flameffacore.kit;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.ffa.CartVault;
import dev.flamesentinell.flameffacore.item.ItemKeys;
import dev.flamesentinell.flameffacore.rank.Rank;
import dev.flamesentinell.flameffacore.util.Bots;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

public final class KitService {
   private static final int INVENTORY_SLOTS = 41;
   private final FlameFFACore plugin;
   private volatile Map<String, RankKit> kits = new LinkedHashMap<>();

   public KitService(FlameFFACore var1) {
      this.plugin = var1;
   }

   public void load() {
      Map<String, RankKit> var1 = new LinkedHashMap<>();
      this.plugin.blobs().loadAll("rankkit").forEach((var2, var3) -> var1.put(var2, new RankKit(var2, var3.contents(), var3.setBy(), var3.setAt())));
      synchronized (this) {
         this.kits = var1;
      }

      this.plugin.getSLF4JLogger().info("loaded {} rank kits", var1.size());
   }

   public RankKit get(String var1) {
      return var1 == null ? null : this.kits.get(var1.toLowerCase());
   }

   public Map<String, RankKit> all() {
      return this.kits;
   }

   public boolean isSet(String var1) {
      return this.get(var1) != null;
   }

   public boolean capture(Player var1, Rank var2) {
      ItemStack[] var3 = var1.getInventory().getContents();
      ItemStack[] var4 = new ItemStack[41];

      for (int var5 = 0; var5 < var3.length && var5 < 41; var5++) {
         ItemStack var6 = var3[var5];
         if (var6 != null && !var6.getType().isAir()) {
            var4[var5] = this.plugin.items().asTemplate(var6);
         }
      }

      boolean var7 = this.plugin.blobs().save("rankkit", var2.id(), var4, var1.getName());
      if (var7) {
         synchronized (this) {
            Map<String, RankKit> var8 = new LinkedHashMap<>(this.kits);
            var8.put(var2.id(), new RankKit(var2.id(), var4, var1.getName(), System.currentTimeMillis()));
            this.kits = var8;
         }
      }

      return var7;
   }

   public boolean clear(String var1) {
      synchronized (this) {
         Map<String, RankKit> var2 = new LinkedHashMap<>(this.kits);
         var2.remove(var1);
         this.kits = var2;
      }

      return this.plugin.blobs().delete("rankkit", var1);
   }

   public boolean canUse(Player var1, Rank var2) {
      return !this.plugin.ranks().hasKit(var2.id())
         ? false
         : var1.hasPermission("flameffacore.kit." + var2.id()) || this.plugin.ranks().unlockedRanks(var1).contains(var2.id());
   }

   public boolean give(Player var1, Rank var2) {
      if (Bots.involved(var1)) {
         var1.sendMessage(this.plugin.configs().msg("bots.in-fight"));
         return false;
      } else if (var1.isDead() || !var1.isOnline()) {
         return false;
      } else if (!this.canUse(var1, var2)) {
         var1.sendMessage(this.plugin.configs().msg("kit.locked", "kit", var2.id(), "rank", var2.id()));
         return false;
      } else {
         RankKit var3 = this.get(var2.id());
         if (var3 == null) {
            var1.sendMessage(this.plugin.configs().msg("kit.not-set", "rank", var2.id()));
            return false;
         } else if (this.plugin.arena().inCombat(var1)) {
            var1.sendMessage(this.plugin.configs().msg("kit.combat"));
            return false;
         } else if (!this.plugin.arena().inSafezone(var1.getLocation())) {
            var1.sendMessage(this.plugin.configs().msg("kit.not-at-spawn"));
            return false;
         } else if (!this.isEmptyHanded(var1)) {
            var1.sendMessage(this.plugin.configs().msg("kit.not-empty"));
            return false;
         } else {
            long var4 = this.cooldown(var1, var2);
            if (var4 > 0L) {
               var1.sendMessage(this.plugin.configs().msg("kit.cooldown", "kit", var2.id(), "time", formatTime(var4), "seconds", String.valueOf(var4)));
               return false;
            } else {
               this.apply(var1, var3);
               boolean var6 = false;

               for (ItemStack var10 : var1.getInventory().getContents()) {
                  if (var10 != null && this.plugin.items().isKitItem(var10)) {
                     var6 = true;
                     break;
                  }
               }

               if (!var6) {
                  this.plugin.getLogger().warning("kit " + var2.id() + " for " + var1.getName() + " came out empty  no cooldown started");
                  var1.sendMessage(this.plugin.configs().msg("kit.not-set", "rank", var2.id()));
                  return false;
               } else {
                  this.startCooldown(var1, var2);
                  if (var2.id().equals(this.plugin.configs().main().getString("cart-vault.rank", "speardemon"))) {
                     CartVault.refresh(this.plugin, var1);
                  }

                  this.plugin.async(() -> this.plugin.players().setLastKit(var1.getUniqueId(), var2.id()));
                  var1.sendMessage(this.plugin.configs().msg("kit.given", "kit", var2.id()));
                  return true;
               }
            }
         }
      }
   }

   public long cooldown(Player var1, Rank var2) {
      return var2.cooldownSeconds() > 0 && !var1.hasPermission("flameffacore.kit.nocooldown")
         ? this.plugin.kitCooldowns().remaining(var1.getUniqueId(), var2.id())
         : 0L;
   }

   private void startCooldown(Player var1, Rank var2) {
      if (var2.cooldownSeconds() > 0 && !var1.hasPermission("flameffacore.kit.nocooldown")) {
         UUID var3 = var1.getUniqueId();
         String var4 = var2.id();
         long var6 = this.plugin.kitCooldowns().mark(var3, var4, var2.cooldownSeconds());
         this.plugin.async(() -> this.plugin.kitCooldowns().persist(var3, var4, var6));
      }
   }

   public static String formatTime(long var0) {
      long var2 = var0 / 3600L;
      long var4 = var0 % 3600L / 60L;
      long var6 = var0 % 60L;
      if (var2 > 0L) {
         return var2 + "h " + var4 + "m";
      } else {
         return var4 > 0L ? var4 + "m " + var6 + "s" : var6 + "s";
      }
   }

   public boolean isEmptyHanded(Player var1) {
      for (ItemStack var5 : var1.getInventory().getContents()) {
         if (var5 != null && !var5.getType().isAir() && !this.plugin.items().isKitItem(var5)) {
            return false;
         }
      }

      ItemStack var6 = var1.getItemOnCursor();
      return var6.getType().isAir() || this.plugin.items().isKitItem(var6);
   }

   private void apply(Player var1, RankKit var2) {
      PlayerInventory var3 = var1.getInventory();
      ItemStack[] var4 = new ItemStack[41];

      for (int var5 = 0; var5 < var2.contents().length && var5 < 41; var5++) {
         ItemStack var6 = var2.contents()[var5];
         if (var6 != null && !var6.getType().isAir()) {
            ItemStack var7 = var6.clone();
            if (!this.plugin.items().isCurrency(var7)) {
               var7.editMeta(var0 -> var0.getPersistentDataContainer().set(ItemKeys.KIT, PersistentDataType.BYTE, (byte)1));
            }

            var4[var5] = var7;
         }
      }

      var3.clear();
      var3.setContents(KitLayouts.of(this.plugin).arrange(var1.getUniqueId(), var2.rankId(), var4));
      var1.getActivePotionEffects().forEach(var1x -> var1.removePotionEffect(var1x.getType()));
      var1.setFireTicks(0);
      var1.setFoodLevel(20);
      var1.setSaturation(20.0F);
      AttributeInstance var8 = var1.getAttribute(Attribute.MAX_HEALTH);
      var1.setHealth(var8 == null ? 20.0 : var8.getValue());
      var3.setHeldItemSlot(0);
      var1.updateInventory();
   }

   public void wipeKitItems(Player var1) {
      PlayerInventory var2 = var1.getInventory();

      for (int var3 = 0; var3 < var2.getSize(); var3++) {
         ItemStack var4 = var2.getItem(var3);
         if (var4 != null && this.plugin.items().isKitItem(var4)) {
            var2.setItem(var3, null);
         }
      }
   }
}
