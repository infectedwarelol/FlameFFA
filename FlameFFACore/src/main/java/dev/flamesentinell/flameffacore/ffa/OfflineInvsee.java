package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class OfflineInvsee implements Listener {
   private static OfflineInvsee instance;
   private static final int SIZE = 45;
   private static final int ARMOR = 36;
   private static final int OFFHAND = 40;
   private static final String[] EQUIP = new String[]{"head", "chest", "legs", "feet", "offhand"};
   private final FlameFFACore plugin;
   private final Map<UUID, Inventory> open = new ConcurrentHashMap<>();

   private OfflineInvsee(FlameFFACore var1) {
      this.plugin = var1;
      Bukkit.getPluginManager().registerEvents(this, var1);
   }

   public static OfflineInvsee start(FlameFFACore var0) {
      if (instance == null || instance.plugin != var0) {
         instance = new OfflineInvsee(var0);
      }

      return instance;
   }

   private static void say(Player var0, String var1) {
      var0.sendMessage(MiniMessage.miniMessage().deserialize(var1));
   }

   private File file(UUID var1) {
      return new File(new File(((World)Bukkit.getWorlds().get(0)).getWorldFolder(), "playerdata"), var1 + ".dat");
   }

   private static int guiSlot(int var0) {
      return var0 < 9 ? 27 + var0 : var0 - 9;
   }

   private static int invSlot(int var0) {
      return var0 >= 27 ? var0 - 27 : var0 + 9;
   }

   private static boolean filler(int var0) {
      return var0 > 40 && var0 < 45;
   }

   private static ItemStack toStack(Map<String, Object> var0, int var1) throws Exception {
      LinkedHashMap var2 = new LinkedHashMap(var0);
      var2.remove("Slot");
      var2.put("DataVersion", var1);
      return ItemStack.deserializeBytes(Nbt.writeGzip(var2));
   }

   private static Map<String, Object> toTag(ItemStack var0) throws Exception {
      Map var1 = Nbt.readGzip(var0.serializeAsBytes());
      var1.remove("DataVersion");
      return var1;
   }

   public static void openOffline(Player viewer, String name) {
      if (instance != null) {
         instance.showOffline(viewer, name);
      }
   }

   private void showOffline(Player var1, String var2) {
      OfflinePlayer var3 = Bukkit.getOfflinePlayerIfCached(var2);
      if (var3 != null && var3.hasPlayedBefore()) {
         UUID var4 = var3.getUniqueId();
         Inventory var5 = this.open.get(var4);
         if (var5 != null) {
            var1.openInventory(var5);
         } else {
            File var6 = this.file(var4);
            String var19 = var3.getName() != null ? var3.getName() : var2;
            this.plugin.async(() -> {
               Map var7;
               try {
                  var7 = var6.exists() ? Nbt.readGzip(Files.readAllBytes(var6.toPath())) : null;
               } catch (Exception var17) {
                  this.plugin.getLogger().warning("offline invsee of " + var2 + " failed  " + var17);
                  this.plugin.sync(var1, () -> say(var1, "<red>Could not read " + var2 + "'s inventory."));
                  return;
               }

               this.plugin.sync(var1, () -> {
                  if (var7 == null) {
                     say(var1, "<red>No saved inventory for " + var2 + ".");
                  } else if (var1.isOnline()) {
                     Inventory var20 = this.open.get(var4);
                     if (var20 == null) {
                        try {
                           var20 = this.build(var4, var19, var7);
                        } catch (Exception var18) {
                           this.plugin.getLogger().warning("offline invsee of " + var2 + " failed  " + var18);
                           say(var1, "<red>Could not read " + var2 + "'s inventory.");
                           return;
                        }

                        Inventory var21 = this.open.putIfAbsent(var4, var20);
                        if (var21 != null) {
                           var20 = var21;
                        }
                     }

                     var1.openInventory(var20);
                  }
               });
            });
         }
      } else {
         say(var1, "<red>" + var2 + " has never played here.");
      }
   }

   private Inventory build(UUID var4, String var19, Map var7) throws Exception {
      int var8 = var7.get("DataVersion") instanceof Integer var9 ? var9 : -1;
      boolean var18 = var8 != Bukkit.getUnsafe().getDataVersion();
      OfflineInvsee.Holder var11 = new OfflineInvsee.Holder(var4, var19, var18);
      Inventory var5 = Bukkit.createInventory(
         var11, 45, MiniMessage.miniMessage().deserialize(MiniMessage.miniMessage().escapeTags(var19) + "'s inventory (offline" + (var18 ? ", view only" : "") + ")")
      );
      var11.inventory = var5;
      List var12 = Nbt.list(var7.get("Inventory"));
      if (var12 != null) {
         for (Object var14 : var12) {
            Map var15 = (Map)var14;
            int var16 = ((Number)var15.getOrDefault("Slot", (byte)-1)).intValue();
            if (var16 >= 0 && var16 < 36) {
               var5.setItem(guiSlot(var16), toStack(var15, var8));
            } else if (var16 >= 100 && var16 <= 103) {
               var5.setItem(36 + (103 - var16), toStack(var15, var8));
            } else if (var16 == -106) {
               var5.setItem(40, toStack(var15, var8));
            }
         }
      }

      if (var7.get("equipment") instanceof Map var20) {
         for (int var23 = 0; var23 < EQUIP.length; var23++) {
            if (var20.get(EQUIP[var23]) instanceof Map var26) {
               var5.setItem(36 + var23, toStack(var26, var8));
            }
         }
      }

      ItemStack var21 = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      var21.editMeta(var0 -> var0.displayName(MiniMessage.miniMessage().deserialize("<gray>")));

      for (int var24 = 41; var24 < 45; var24++) {
         var5.setItem(var24, var21);
      }

      return var5;
   }

   private static OfflineInvsee.Holder holder(Inventory var0) {
      return var0.getHolder() instanceof OfflineInvsee.Holder var1 ? var1 : null;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onClick(InventoryClickEvent var1) {
      OfflineInvsee.Holder var2 = holder(var1.getView().getTopInventory());
      if (var2 != null) {
         boolean var3 = var1.getRawSlot() >= 0 && var1.getRawSlot() < 45;
         if (var2.readOnly || var3 && filler(var1.getRawSlot()) || !var3 && var1.isShiftClick()) {
            var1.setCancelled(true);
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDrag(InventoryDragEvent var1) {
      OfflineInvsee.Holder var2 = holder(var1.getView().getTopInventory());
      if (var2 != null) {
         if (var2.readOnly || var1.getRawSlots().stream().anyMatch(var0 -> var0 < 45 && filler(var0))) {
            var1.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent var1) {
      OfflineInvsee.Holder var2 = holder(var1.getInventory());
      if (var2 != null) {
         if (!var1.getInventory().getViewers().stream().anyMatch(var1x -> var1x != var1.getPlayer())) {
            this.open.remove(var2.owner);
            if (!var2.readOnly) {
               if (Bukkit.getPlayer(var2.owner) != null) {
                  if (var1.getPlayer() instanceof Player var5) {
                     say(var5, "<red>" + var2.name + " came online, changes were not saved.");
                  }
               } else {
                  this.save(var2, var1.getInventory(), var1.getPlayer() instanceof Player var3 ? var3 : null);
               }
            }
         }
      }
   }

   private void save(OfflineInvsee.Holder var1, Inventory var2, Player var3) {
      File var4 = this.file(var1.owner);

      try {
         Map var5 = Nbt.readGzip(Files.readAllBytes(var4.toPath()));
         Nbt.Tags var6 = new Nbt.Tags((byte)10);
         boolean var8 = !(!(var5.get("equipment") instanceof Map) && var5.get("Inventory") instanceof Nbt.Tags var9)
            || !var9.stream().anyMatch(var0 -> var0 instanceof Map var2x && var2x.get("Slot") instanceof Byte var1x && (var1x >= 100 || var1x == -106));

         for (int var15 = 0; var15 < 36; var15++) {
            ItemStack var10 = var2.getItem(var15);
            if (var10 != null && !var10.getType().isAir()) {
               Map var11 = toTag(var10);
               LinkedHashMap var12 = new LinkedHashMap();
               var12.put("Slot", (byte)invSlot(var15));
               var12.putAll(var11);
               var6.add(var12);
            }
         }

         LinkedHashMap var16 = var5.get("equipment") instanceof Map var17 ? new LinkedHashMap(var17) : new LinkedHashMap();

         for (int var18 = 0; var18 < EQUIP.length; var18++) {
            ItemStack var21 = var2.getItem(36 + var18);
            boolean var23 = var21 == null || var21.getType().isAir();
            if (var8) {
               if (var23) {
                  var16.remove(EQUIP[var18]);
               } else {
                  var16.put(EQUIP[var18], toTag(var21));
               }
            } else if (!var23) {
               LinkedHashMap var13 = new LinkedHashMap();
               var13.put("Slot", (byte)(var18 < 4 ? 103 - var18 : -106));
               var13.putAll(toTag(var21));
               var6.add(var13);
            }
         }

         var5.put("Inventory", var6);
         if (var8) {
            var5.put("equipment", var16);
         }

         File var19 = new File(var4.getParentFile(), var4.getName() + ".invsee_bak");
         Files.copy(var4.toPath(), var19.toPath(), StandardCopyOption.REPLACE_EXISTING);
         File var22 = new File(var4.getParentFile(), var4.getName() + ".invsee_tmp");
         Files.write(var22.toPath(), Nbt.writeGzip(var5));
         Files.move(var22.toPath(), var4.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         this.plugin.getLogger().info("offline invsee saved " + var1.name + (var3 != null ? " by " + var3.getName() : ""));
      } catch (Exception var14) {
         this.plugin.getLogger().warning("offline invsee save of " + var1.name + " failed  " + var14);
         if (var3 != null) {
            say(var3, "<red>Could not save " + var1.name + "'s inventory.");
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPreLogin(AsyncPlayerPreLoginEvent var1) {
      if (var1.getLoginResult() == Result.ALLOWED && this.open.containsKey(var1.getUniqueId())) {
         Inventory var3 = this.open.get(var1.getUniqueId());
         if (var3 != null) {
            List<CompletableFuture<Void>> closing = new ArrayList<>();

            for (HumanEntity var5 : List.copyOf(var3.getViewers())) {
               CompletableFuture<Void> closed = new CompletableFuture<>();
               closing.add(closed);
               if (this.plugin.tasks().entity(var5, () -> {
                  try {
                     var5.closeInventory();
                  } finally {
                     closed.complete(null);
                  }
               }) == null) {
                  closed.complete(null);
               }
            }

            try {
               CompletableFuture.allOf(closing.toArray(CompletableFuture[]::new)).get(5L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
               Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException slow) {
               this.plugin.getSLF4JLogger().warn("offline invsee of {} did not close in time", var1.getName());
            }
         }
      }
   }

   private static final class Holder implements InventoryHolder {
      final UUID owner;
      final String name;
      final boolean readOnly;
      Inventory inventory;

      Holder(UUID var1, String var2, boolean var3) {
         this.owner = var1;
         this.name = var2;
         this.readOnly = var3;
      }

      @NotNull
      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
