package dev.flamesentinell.flameffacore.kit;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public final class KitLayouts implements Listener {
   private static KitLayouts instance;
   private final FlameFFACore plugin;
   private final NamespacedKey src;
   private final File dir;
   private final Map<UUID, YamlConfiguration> cache = new HashMap<>();

   private KitLayouts(FlameFFACore var1) {
      this.plugin = var1;
      this.src = new NamespacedKey(var1, "layout_src");
      this.dir = new File(var1.getDataFolder(), "kit_layouts");
      Bukkit.getPluginManager().registerEvents(this, var1);
   }

   public static KitLayouts of(FlameFFACore var0) {
      if (instance == null || instance.plugin != var0) {
         instance = new KitLayouts(var0);
      }

      return instance;
   }

   private static int gui(int var0) {
      return var0 < 9 ? 27 + var0 : var0 - 9;
   }

   private static int inv(int var0) {
      return var0 >= 27 ? var0 - 27 : var0 + 9;
   }

   private YamlConfiguration file(UUID var1) {
      return this.cache.computeIfAbsent(var1, var1x -> {
         File var2 = new File(this.dir, var1x + ".yml");
         return var2.exists() ? YamlConfiguration.loadConfiguration(var2) : new YamlConfiguration();
      });
   }

   public int[] layout(UUID var1, String var2) {
      int[] var3 = new int[36];
      int var4 = 0;

      while (var4 < 36) {
         var3[var4] = var4++;
      }

      List var9 = this.file(var1).getIntegerList(var2);
      if (var9.size() != 36) {
         return var3;
      } else {
         boolean[] var5 = new boolean[36];

         for (int var6 = 0; var6 < 36; var6++) {
            int var7 = (Integer)var9.get(var6);
            if (var7 < 0 || var7 >= 36 || var5[var7]) {
               int var8 = 0;

               while (var8 < 36) {
                  var3[var8] = var8++;
               }

               return var3;
            }

            var5[var7] = true;
            var3[var6] = var7;
         }

         return var3;
      }
   }

   public ItemStack[] arrange(UUID var1, String var2, ItemStack[] var3) {
      int[] var4 = this.layout(var1, var2);
      ItemStack[] var5 = (ItemStack[])var3.clone();

      for (int var6 = 0; var6 < 36 && var6 < var3.length; var6++) {
         var5[var6] = null;
      }

      for (int var7 = 0; var7 < 36 && var7 < var3.length; var7++) {
         var5[var4[var7]] = var3[var7];
      }

      return var5;
   }

   private void save(UUID var1, String var2, int[] var3) {
      YamlConfiguration var4 = this.file(var1);
      if (var3 == null) {
         var4.set(var2, null);
      } else {
         ArrayList var5 = new ArrayList();

         for (int var9 : var3) {
            var5.add(var9);
         }

         var4.set(var2, var5);
      }

      this.dir.mkdirs();

      try {
         var4.save(new File(this.dir, var1 + ".yml"));
      } catch (IOException var10) {
         this.plugin.getLogger().warning("kit layout save failed " + var10.getMessage());
      }
   }

   private static void say(Player var0, String var1) {
      var0.sendMessage(MiniMessage.miniMessage().deserialize(var1));
   }

   public void reset(Player var1, String var2) {
      this.save(var1.getUniqueId(), var2, null);
      say(var1, "<gold>Kit layout for <yellow>" + var2 + "</yellow> reset.");
   }

   public void open(Player var1, RankKit var2) {
      KitLayouts.Holder var3 = new KitLayouts.Holder(var2.rankId());
      Inventory var4 = Bukkit.createInventory(var3, 36, MiniMessage.miniMessage().deserialize("<dark_gray>Edit layout: " + var2.rankId()));
      var3.inventory = var4;
      int[] var5 = this.layout(var1.getUniqueId(), var2.rankId());
      ItemStack[] var6 = var2.contents();

      for (int var7 = 0; var7 < 36 && var7 < var6.length; var7++) {
         if (var6[var7] != null && !var6[var7].getType().isAir()) {
            ItemStack var8 = var6[var7].clone();
            int var9 = var7;
            var8.editMeta(var2x -> var2x.getPersistentDataContainer().set(this.src, PersistentDataType.INTEGER, var9));
            var4.setItem(gui(var5[var7]), var8);
         }
      }

      var1.openInventory(var4);
      say(var1, "<gold>Move the items where you want them, then close to save. <gray>The bottom row is your hotbar.");
   }

   private boolean tagged(ItemStack var1) {
      return var1 != null && var1.hasItemMeta() && var1.getItemMeta().getPersistentDataContainer().has(this.src, PersistentDataType.INTEGER);
   }

   private void purge(Player var1) {
      PlayerInventory var2 = var1.getInventory();

      for (int var3 = 0; var3 < var2.getSize(); var3++) {
         if (this.tagged(var2.getItem(var3))) {
            var2.setItem(var3, null);
         }
      }

      if (this.tagged(var1.getItemOnCursor())) {
         var1.setItemOnCursor(null);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onClick(InventoryClickEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof KitLayouts.Holder) {
         boolean var2 = var1.getClickedInventory() == var1.getView().getTopInventory();
         InventoryAction var3 = var1.getAction();
         if (!var2
            || var1.isShiftClick()
            || var3 == InventoryAction.HOTBAR_SWAP
            || var3 == InventoryAction.COLLECT_TO_CURSOR
            || var3 == InventoryAction.MOVE_TO_OTHER_INVENTORY
            || var3.name().startsWith("DROP")
            || var1.getClick().isKeyboardClick()) {
            var1.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof KitLayouts.Holder) {
         int var2 = var1.getView().getTopInventory().getSize();

         for (int var4 : var1.getRawSlots()) {
            if (var4 >= var2) {
               var1.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent var1) {
      if (var1.getInventory().getHolder() instanceof KitLayouts.Holder var2 && var1.getPlayer() instanceof Player var3) {
         Inventory var12 = var1.getInventory();
         ItemStack var5 = var3.getItemOnCursor();
         if (this.tagged(var5)) {
            int var6 = var12.firstEmpty();
            if (var6 >= 0) {
               var12.setItem(var6, var5);
            }

            var3.setItemOnCursor(null);
         }

         int[] var13 = this.layout(var3.getUniqueId(), var2.kit);
         boolean[] var7 = new boolean[36];

         for (int var8 = 0; var8 < var12.getSize(); var8++) {
            ItemStack var9 = var12.getItem(var8);
            if (this.tagged(var9)) {
               int var10 = (Integer)var9.getItemMeta().getPersistentDataContainer().get(this.src, PersistentDataType.INTEGER);
               if (var10 >= 0 && var10 < 36 && !var7[var10]) {
                  var7[var10] = true;
                  var13[var10] = inv(var8);
               }
            }
         }

         boolean[] var14 = new boolean[36];

         for (int var15 = 0; var15 < 36; var15++) {
            if (var7[var15]) {
               var14[var13[var15]] = true;
            }
         }

         int var16 = 0;

         for (int var17 = 0; var17 < 36; var17++) {
            if (!var7[var17]) {
               while (var16 < 36 && var14[var16]) {
                  var16++;
               }

               var13[var17] = var16;
               var14[var16] = true;
            }
         }

         var12.clear();
         this.save(var3.getUniqueId(), var2.kit, var13);
         Bukkit.getScheduler().runTask(this.plugin, () -> this.purge(var3));
         say(var3, "<green>Kit layout for <yellow>" + var2.kit + "</yellow> saved. <gray>It applies the next time you take the kit.");
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      this.purge(var1.getPlayer());
   }

   private static final class Holder implements InventoryHolder {
      final String kit;
      Inventory inventory;

      Holder(String var1) {
         this.kit = var1;
      }

      @NotNull
      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
