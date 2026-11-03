package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Tasks;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class CartVault implements Listener {
   private static final int SIZE = 27;
   private static CartVault instance;
   private final FlameFFACore plugin;
   private final File dir;
   private final Map<UUID, Inventory> open = new ConcurrentHashMap<>();
   private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
   private final ExecutorService io = Executors.newSingleThreadExecutor(var0 -> {
      Thread var1x = new Thread(var0, "FlameFFACore cart vault");
      var1x.setDaemon(true);
      return var1x;
   });
   private static final Set<Material> ALLOWED = Set.of(
      Material.TNT_MINECART,
      Material.RAIL,
      Material.POWERED_RAIL,
      Material.DETECTOR_RAIL,
      Material.ACTIVATOR_RAIL,
      Material.TOTEM_OF_UNDYING,
      Material.EXPERIENCE_BOTTLE,
      Material.ARROW,
      Material.WIND_CHARGE,
      Material.ENDER_PEARL
   );

   private CartVault(FlameFFACore var1) {
      this.plugin = var1;
      this.dir = new File(var1.getDataFolder(), "cartvaults");
      this.dir.mkdirs();
      Bukkit.getPluginManager().registerEvents(this, var1);
      var1.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, var1x -> {
         Commands var2 = (Commands)var1x.registrar();
         var2.register("cartinv", "open your cart inventory", List.of("cartvault"), new CartVault.Cmd());
      });
   }

   public static CartVault start(FlameFFACore var0) {
      if (instance == null || instance.plugin != var0) {
         instance = new CartVault(var0);
      }

      return instance;
   }

   public static void refresh(FlameFFACore var0, Player var1) {
      CartVault var2 = start(var0);

      Inventory var3;
      try {
         var3 = var2.vault(var1);
      } catch (IllegalStateException var5) {
         return;
      }

      var3.clear();
      stock(var3);
      var2.save(var1);
      say(var1, "<gray>Your cart inventory was restocked.");
   }

   private boolean unlocked(Player var1) {
      return this.plugin.ranks().unlockedRanks(var1).contains(this.plugin.configs().main().getString("cart-vault.rank", "speardemon"));
   }

   private static void stock(Inventory var0) {
      add(var0, Material.TNT_MINECART, 11);
      add(var0, Material.RAIL, 128);
      add(var0, Material.TOTEM_OF_UNDYING, 5);
      add(var0, Material.EXPERIENCE_BOTTLE, 192);
      add(var0, Material.ARROW, 128);
      add(var0, Material.WIND_CHARGE, 128);
      add(var0, Material.ENDER_PEARL, 32);
   }

   private static void add(Inventory var0, Material var1, int var2) {
      int var3 = var1.getMaxStackSize();

      for (int var4 = var2; var4 > 0; var4 -= var3) {
         int var5 = var0.firstEmpty();
         if (var5 < 0) {
            return;
         }

         var0.setItem(var5, ItemStack.of(var1, Math.min(var3, var4)));
      }
   }

   private static void say(Player var0, String var1) {
      var0.sendMessage(MiniMessage.miniMessage().deserialize(var1));
   }

   private File file(UUID var1) {
      return new File(this.dir, var1 + ".bin");
   }

   private Inventory vault(Player var1) {
      UUID var2 = var1.getUniqueId();
      Inventory var3 = this.open.get(var2);
      if (var3 != null) {
         return var3;
      } else {
         CartVault.Holder var4 = new CartVault.Holder(var2);
         var3 = Bukkit.createInventory(var4, 27, MiniMessage.miniMessage().deserialize("Cart Inventory"));
         var4.inventory = var3;
         byte[] var5;
         try {
            var5 = this.read(this.file(var2));
         } catch (Exception var8) {
            this.plugin.getLogger().severe("cart vault of " + var1.getName() + " could not be read  " + var8);
            throw new IllegalStateException("cart vault unreadable", var8);
         }

         if (var5 != null) {
            try {
               ItemStack[] var6 = ItemStack.deserializeItemsFromBytes(var5);

               for (int var7 = 0; var7 < var6.length && var7 < 27; var7++) {
                  if (var6[var7] != null && !var6[var7].getType().isAir()) {
                     var3.setItem(var7, var6[var7]);
                  }
               }
            } catch (Exception var8) {
               this.plugin.getLogger().severe("cart vault of " + var1.getName() + " could not be read  " + var8);
               throw new IllegalStateException("cart vault unreadable", var8);
            }

            Inventory var9 = this.open.putIfAbsent(var2, var3);
            return var9 != null ? var9 : var3;
         } else {
            stock(var3);
            Inventory var9 = this.open.putIfAbsent(var2, var3);
            if (var9 == null) {
               this.save(var1);
               return var3;
            } else {
               return var9;
            }
         }
      }
   }

   private byte[] read(File file) throws Exception {
      return this.io.submit(() -> file.exists() ? Files.readAllBytes(file.toPath()) : null).get(10L, TimeUnit.SECONDS);
   }

   private void show(Player var1) {
      Inventory var2;
      try {
         var2 = this.vault(var1);
      } catch (IllegalStateException var4) {
         say(var1, "<red>Your cart inventory could not be loaded, tell staff.");
         return;
      }

      if (Tasks.viewedFromOtherRegion(var2, null)) {
         say(var1, "<red>Someone in another area is using that inventory, try again soon.");
         return;
      }

      this.save(var1);
      var1.openInventory(var2);
      var1.playSound(var1.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6F, 1.0F);
   }

   private void save(Player var1) {
      UUID var2 = var1.getUniqueId();
      Inventory var3 = this.open.get(var2);
      if (var3 != null) {
         this.dirty.remove(var2);
         byte[] var4 = ItemStack.serializeItemsAsBytes(var3.getContents());
         if (var1.isOnline()) {
            this.plugin.tasks().runOn(var1, var1::saveData);
         }

         File var5 = this.file(var2);
         this.io.execute(() -> this.write(var5, var4));
      }
   }

   private void write(File var1, byte[] var2) {
      try {
         File var3 = new File(var1.getParentFile(), var1.getName() + ".tmp");
         Files.write(var3.toPath(), var2);
         Files.move(var3.toPath(), var1.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var4) {
         this.plugin.getLogger().severe("cart vault save failed " + var1.getName() + "  " + var4);
      }
   }

   private void touch(UUID var1) {
      if (this.dirty.add(var1)) {
         Player var2 = Bukkit.getPlayer(var1);
         if (var2 == null || this.plugin.tasks().entity(var2, () -> this.save(var2)) == null) {
            this.dirty.remove(var1);
         }
      }
   }

   private boolean bad(ItemStack var1) {
      return var1 != null && !var1.getType().isAir() && (!ALLOWED.contains(var1.getType()) || this.plugin.items().isKitItem(var1));
   }

   private static UUID owner(Inventory var0) {
      return var0.getHolder() instanceof CartVault.Holder var1 ? var1.owner : null;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onClick(InventoryClickEvent var1) {
      Inventory var2 = var1.getView().getTopInventory();
      UUID var3 = owner(var2);
      if (var3 != null) {
         boolean var4 = var1.getRawSlot() >= 0 && var1.getRawSlot() < var2.getSize();
         boolean var5;
         if (var4) {
            var5 = this.bad(var1.getCursor())
               || var1.getClick() == ClickType.NUMBER_KEY && this.bad(var1.getWhoClicked().getInventory().getItem(var1.getHotbarButton()))
               || var1.getClick() == ClickType.SWAP_OFFHAND && this.bad(var1.getWhoClicked().getInventory().getItemInOffHand());
         } else {
            var5 = var1.isShiftClick() && this.bad(var1.getCurrentItem());
         }

         if (var5) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player var6) {
               say(
                  var6,
                  "<red>Only TNT minecarts, rails, totems, Bottles o' Enchanting, arrows, wind charges and ender pearls can go in here, and no kit items."
               );
            }
         } else {
            this.touch(var3);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDrag(InventoryDragEvent var1) {
      Inventory var2 = var1.getView().getTopInventory();
      UUID var3 = owner(var2);
      if (var3 != null) {
         boolean var4 = var1.getRawSlots().stream().anyMatch(var1x -> var1x < var2.getSize());
         if (var4 && this.bad(var1.getOldCursor())) {
            var1.setCancelled(true);
         } else {
            this.touch(var3);
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent var1) {
      UUID var2 = owner(var1.getInventory());
      if (var2 != null) {
         Player var3 = Bukkit.getPlayer(var2);
         if (var3 != null) {
            this.save(var3);
         }

         if (var1.getPlayer() instanceof Player var4) {
            var4.playSound(var4.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.6F, 1.0F);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onQuit(PlayerQuitEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      Inventory var3 = this.open.get(var2);
      if (var3 != null) {
         for (HumanEntity var5 : List.copyOf(var3.getViewers())) {
            var5.closeInventory();
         }

         this.save(var1.getPlayer());
         this.open.remove(var2);
      }
   }

   @EventHandler
   public void onDisable(PluginDisableEvent var1) {
      if (var1.getPlugin() == this.plugin) {
         for (UUID var3 : List.copyOf(this.open.keySet())) {
            Inventory var4 = this.open.get(var3);

            for (HumanEntity var6 : List.copyOf(var4.getViewers())) {
               var6.closeInventory();
            }

            Player var8 = Bukkit.getPlayer(var3);
            if (var8 != null) {
               this.save(var8);
            }
         }

         this.io.shutdown();

         try {
            this.io.awaitTermination(10L, TimeUnit.SECONDS);
         } catch (InterruptedException var7) {
         }
      }
   }

   private final class Cmd implements BasicCommand {
      public void execute(@NotNull CommandSourceStack var1, @NotNull String[] var2) {
         if (var1.getSender() instanceof Player var3) {
            if (var2.length > 0 && CartVault.this.plugin.staff().isOperator(var3)) {
               Player var5 = Bukkit.getPlayerExact(var2[0]);
               if (var5 == null) {
                  CartVault.say(var3, "<red>That player is not online.");
               } else {
                  try {
                     Inventory var7 = CartVault.this.vault(var5);
                     if (Tasks.viewedFromOtherRegion(var7, var5)) {
                        CartVault.say(var3, "<red>Someone in another area is using that inventory, try again soon.");
                     } else {
                        var3.openInventory(var7);
                     }
                  } catch (IllegalStateException var6) {
                     CartVault.say(var3, "<red>That cart inventory could not be loaded.");
                  }
               }
            } else if (!CartVault.this.unlocked(var3)) {
               CartVault.say(var3, "<red>The cart inventory comes with the Spear Cart Demon unlock.");
            } else if (CartVault.this.plugin.arena().inCombat(var3) && !CartVault.this.plugin.configs().main().getBoolean("cart-vault.in-combat", true)) {
               CartVault.say(var3, "<red>You can not open your cart inventory in combat.");
            } else {
               CartVault.this.show(var3);
            }
         } else {
            var1.getSender().sendMessage("players only");
         }
      }
   }

   private static final class Holder implements InventoryHolder {
      final UUID owner;
      Inventory inventory;

      Holder(UUID var1) {
         this.owner = var1;
      }

      @NotNull
      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
