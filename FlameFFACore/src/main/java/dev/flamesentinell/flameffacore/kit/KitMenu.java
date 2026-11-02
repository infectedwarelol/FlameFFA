package dev.flamesentinell.flameffacore.kit;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.config.ConfigManager;
import dev.flamesentinell.flameffacore.item.ItemKeys;
import dev.flamesentinell.flameffacore.rank.Rank;
import dev.flamesentinell.flameffacore.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public final class KitMenu implements Listener {
   private static final String TAG = "menu:";
   private static final String BACK = "back";
   private static final String EQUIP = "equip:";
   private static final int ROWS = 5;
   private final FlameFFACore plugin;
   private final Map<UUID, KitMenu.Holder> open = new HashMap<>();
   private BukkitTask ticker;

   public KitMenu(FlameFFACore var1) {
      this.plugin = var1;
   }

   public void open(Player var1) {
      KitMenu.Holder var2 = new KitMenu.Holder(null);
      Inventory var3 = Bukkit.createInventory(var2, 45, this.plugin.configs().msg("kit.menu-title"));
      var2.inventory = var3;
      this.paint(var1, var3);
      var1.openInventory(var3);
      this.open.put(var1.getUniqueId(), var2);
      this.tick();
   }

   private void paint(Player var1, Inventory var2) {
      ItemStack var3 = pane(Material.BLACK_STAINED_GLASS_PANE, "");

      for (int var4 = 0; var4 < var2.getSize(); var4++) {
         var2.setItem(var4, var3);
      }

      List<Rank> var12 = this.plugin.ranks().ordered();
      var12.removeIf(var1x -> !this.plugin.ranks().hasKit(var1x.id()));
      List var5 = this.plugin.ranks().unlockedRanks(var1);
      boolean var6 = this.plugin.kits().isEmptyHanded(var1);
      byte var7 = 2;
      int var8 = var7 * 9 + Math.max(0, (9 - Math.min(9, var12.size())) / 2);
      int var9 = var12.size() * 2 - 1 <= 9 ? 2 : 1;
      if (var9 == 2) {
         var8 = var7 * 9 + (9 - (var12.size() * 2 - 1)) / 2;
      }

      for (int var10 = 0; var10 < var12.size() && var10 < 9; var10++) {
         Rank var11 = (Rank)var12.get(var10);
         var2.setItem(var8 + var10 * var9, this.icon(var1, var11, var5.contains(var11.id()), var6));
      }

      var2.setItem(var2.getSize() - 5, this.info());
   }

   private ItemStack icon(Player var1, Rank var2, boolean var3, boolean var4) {
      RankKit var5 = this.plugin.kits().get(var2.id());
      long var6 = this.plugin.kits().cooldown(var1, var2);
      ConfigManager var8 = this.plugin.configs();
      Material var9;
      if (!var3) {
         var9 = Material.GRAY_STAINED_GLASS_PANE;
      } else if (var5 == null) {
         var9 = Material.BARRIER;
      } else if (var6 > 0L) {
         var9 = Material.RED_STAINED_GLASS_PANE;
      } else {
         var9 = var2.icon();
      }

      ItemStack var10 = ItemStack.of(var9, 1);
      boolean var11 = var3 && var5 != null && var6 == 0L && var4;
      var10.editMeta(var8x -> {
         var8x.displayName(Text.parse(var2.display()).decoration(TextDecoration.ITALIC, false));
         ArrayList var9x = new ArrayList();
         if (var5 != null) {
            var9x.add(line(var8.msg("kit.menu-items", "count", String.valueOf(var5.filledSlots()))));
         }

         if (var2.cooldownSeconds() > 0) {
            var9x.add(line(var8.msg("kit.menu-cooldown-every", "time", KitService.formatTime(var2.cooldownSeconds()))));
         }

         var9x.add(Component.empty());
         if (!var3) {
            var9x.add(line(var8.msg("kit.menu-locked", "rank", var2.display())));
            String var10x = var2.lockedHint() != null ? var2.lockedHint() : var8.messages().getString("kit.menu-store", "");
            if (var10x != null && !var10x.isBlank()) {
               var9x.add(line(Text.parse(var10x)));
            }
         } else if (var5 == null) {
            var9x.add(line(var8.msg("kit.menu-not-set")));
         } else if (var6 > 0L) {
            var9x.add(line(var8.msg("kit.menu-ready-in", "time", KitService.formatTime(var6))));
         } else if (!var4) {
            var9x.add(line(var8.msg("kit.menu-drop-loot")));
         } else {
            var9x.add(line(var8.msg("kit.menu-equip")));
         }

         if (var5 != null) {
            var9x.add(line(var8.msg("kit.menu-preview")));
         }

         var8x.lore(var9x);
         if (var11) {
            var8x.setEnchantmentGlintOverride(true);
         }

         var8x.getPersistentDataContainer().set(ItemKeys.MENU, PersistentDataType.STRING, "menu:" + var2.id());
      });
      return var10;
   }

   private ItemStack info() {
      ItemStack var1 = ItemStack.of(Material.BOOK, 1);
      var1.editMeta(var1x -> {
         var1x.displayName(line(this.plugin.configs().msg("kit.menu-info-title")));
         ArrayList var2 = new ArrayList();

         for (String var4 : this.plugin.configs().messages().getStringList("kit.menu-info")) {
            var2.add(line(Text.parse(var4)));
         }

         var1x.lore(var2);
      });
      return var1;
   }

   public void preview(Player var1, Rank var2) {
      RankKit var3 = this.plugin.kits().get(var2.id());
      if (var3 == null) {
         var1.sendMessage(this.plugin.configs().msg("kit.not-set", "rank", var2.display()));
      } else {
         KitMenu.Holder var4 = new KitMenu.Holder(var2.id());
         Inventory var5 = Bukkit.createInventory(var4, 54, this.plugin.configs().msg("kit.preview-title", "rank", var2.display()));
         var4.inventory = var5;
         ItemStack var6 = pane(Material.BLACK_STAINED_GLASS_PANE, "");

         for (int var7 = 0; var7 < 54; var7++) {
            var5.setItem(var7, var6);
         }

         ItemStack[] var10 = var3.contents();
         var5.setItem(0, this.shown(var10, 39));
         var5.setItem(1, this.shown(var10, 38));
         var5.setItem(2, this.shown(var10, 37));
         var5.setItem(3, this.shown(var10, 36));
         var5.setItem(5, this.shown(var10, 40));

         for (int var8 = 9; var8 < 36; var8++) {
            var5.setItem(18 + (var8 - 9), this.shown(var10, var8));
         }

         for (int var11 = 0; var11 < 9; var11++) {
            var5.setItem(45 + var11, this.shown(var10, var11));
         }

         boolean var12 = this.plugin.ranks().unlockedRanks(var1).contains(var2.id());
         if (var12 && this.plugin.kits().cooldown(var1, var2) == 0L) {
            ItemStack var9 = ItemStack.of(Material.LIME_DYE, 1);
            var9.editMeta(var2x -> {
               var2x.displayName(line(this.plugin.configs().msg("kit.preview-equip", "rank", var2.display())));
               var2x.getPersistentDataContainer().set(ItemKeys.MENU, PersistentDataType.STRING, "equip:" + var2.id());
            });
            var5.setItem(7, var9);
         }

         ItemStack var13 = ItemStack.of(Material.ARROW, 1);
         var13.editMeta(var1x -> {
            var1x.displayName(line(this.plugin.configs().msg("kit.preview-back")));
            var1x.getPersistentDataContainer().set(ItemKeys.MENU, PersistentDataType.STRING, "back");
         });
         var5.setItem(8, var13);
         var1.openInventory(var5);
      }
   }

   private ItemStack shown(ItemStack[] var1, int var2) {
      ItemStack var3 = var2 < var1.length ? var1[var2] : null;
      return var3 != null && !var3.getType().isAir() ? var3.clone() : pane(Material.GRAY_STAINED_GLASS_PANE, "");
   }

   @EventHandler
   public void onClick(@NotNull InventoryClickEvent var1) {
      if (var1.getInventory().getHolder() instanceof KitMenu.Holder var2) {
         var1.setCancelled(true);
         if (var1.getWhoClicked() instanceof Player var7) {
            ItemStack var8 = var1.getCurrentItem();
            if (var8 != null && var8.hasItemMeta()) {
               String var5 = (String)var8.getItemMeta().getPersistentDataContainer().get(ItemKeys.MENU, PersistentDataType.STRING);
               if (var5 != null) {
                  if (var5.equals("back")) {
                     this.open(var7);
                  } else if (var5.startsWith("equip:")) {
                     Rank var6 = this.plugin.ranks().rank(var5.substring("equip:".length()));
                     if (var6 != null) {
                        var7.closeInventory();
                        this.plugin.kits().give(var7, var6);
                     }
                  } else if (var5.startsWith("menu:")) {
                     Rank var9 = this.plugin.ranks().rank(var5.substring("menu:".length()));
                     if (var9 != null) {
                        if (var1.isRightClick()) {
                           this.preview(var7, var9);
                        } else if (var1.isLeftClick()) {
                           var7.closeInventory();
                           this.plugin.kits().give(var7, var9);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getInventory().getHolder() instanceof KitMenu.Holder) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent var1) {
      if (var1.getInventory().getHolder() instanceof KitMenu.Holder var2 && var2.preview == null) {
         this.open.remove(var1.getPlayer().getUniqueId(), var2);
      }
   }

   private void tick() {
      if (this.ticker == null) {
         this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            this.open.entrySet().removeIf(var1 -> {
               Player var2 = Bukkit.getPlayer(var1.getKey());
               if (var2 != null && var2.getOpenInventory().getTopInventory() == var1.getValue().inventory) {
                  this.paint(var2, var1.getValue().inventory);
                  return false;
               } else {
                  return true;
               }
            });
            if (this.open.isEmpty()) {
               this.ticker.cancel();
               this.ticker = null;
            }
         }, 20L, 20L);
      }
   }

   public void shutdown() {
      if (this.ticker != null) {
         this.ticker.cancel();
         this.ticker = null;
      }

      this.open.clear();
   }

   private static Component line(Component var0) {
      return var0.decoration(TextDecoration.ITALIC, false);
   }

   private static ItemStack pane(Material var0, String var1) {
      ItemStack var2 = ItemStack.of(var0, 1);
      var2.editMeta(var1x -> {
         var1x.displayName(Component.text(var1));
         var1x.setHideTooltip(true);
      });
      return var2;
   }

   private static final class Holder implements InventoryHolder {
      private final String preview;
      private Inventory inventory;

      private Holder(String var1) {
         this.preview = var1;
      }

      @NotNull
      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
