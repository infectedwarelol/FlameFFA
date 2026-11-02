package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class EnderVault implements Listener {
   private static final int SIZE = 54;
   private static EnderVault instance;
   private final FlameFFACore plugin;
   private final File dir;
   private final Map<UUID, Inventory> open = new ConcurrentHashMap<>();
   private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
   private final ExecutorService io = Executors.newSingleThreadExecutor(var0 -> {
      Thread var1x = new Thread(var0, "FlameFFACore ender vault");
      var1x.setDaemon(true);
      return var1x;
   });

   private EnderVault(FlameFFACore var1) {
      this.plugin = var1;
      this.dir = new File(var1.getDataFolder(), "enderchests");
      this.dir.mkdirs();
      Bukkit.getPluginManager().registerEvents(this, var1);
      var1.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, var1x -> {
         Commands var2 = (Commands)var1x.registrar();
         var2.register("ec", "open your ender chest", List.of("enderchest", "echest"), new EnderVault.Cmd());
      });
   }

   public static EnderVault start(FlameFFACore var0) {
      if (instance == null || instance.plugin != var0) {
         instance = new EnderVault(var0);
      }

      CartVault.start(var0);
      KeyAll.start(var0);
      OfflineInvsee.start(var0);
      return instance;
   }

   private static void say(Player var0, String var1) {
      var0.sendMessage(MiniMessage.miniMessage().deserialize(var1));
   }

   private File file(UUID var1) {
      return new File(this.dir, var1 + ".bin");
   }

   private Inventory vault(Player var1) {
      return this.vault(var1.getUniqueId(), var1.getName(), var1);
   }

   private Inventory vault(UUID var1, String var2, Player var3) {
      Inventory var4 = this.open.get(var1);
      if (var4 != null) {
         return var4;
      } else {
         EnderVault.Holder var5 = new EnderVault.Holder(var1);
         var4 = Bukkit.createInventory(var5, 54, MiniMessage.miniMessage().deserialize("Ender Chest"));
         var5.inventory = var4;
         File var6 = this.file(var1);
         if (var6.exists()) {
            try {
               ItemStack[] var11 = ItemStack.deserializeItemsFromBytes(Files.readAllBytes(var6.toPath()));

               for (int var12 = 0; var12 < var11.length && var12 < 54; var12++) {
                  if (var11[var12] != null && !var11[var12].getType().isAir()) {
                     var4.setItem(var12, var11[var12]);
                  }
               }
            } catch (Exception var9) {
               this.plugin.getLogger().severe("ender vault of " + var2 + " could not be read  " + var9);
               throw new IllegalStateException("ender vault unreadable", var9);
            }

            this.open.put(var1, var4);
            return var4;
         } else if (var3 == null) {
            throw new IllegalStateException("no ender vault yet");
         } else {
            ItemStack[] var7 = var3.getEnderChest().getContents();

            for (int var8 = 0; var8 < var7.length && var8 < 54; var8++) {
               if (var7[var8] != null && !var7[var8].getType().isAir()) {
                  var4.setItem(var8, var7[var8].clone());
               }
            }

            var3.getEnderChest().clear();
            this.open.put(var1, var4);
            this.save(var1);
            return var4;
         }
      }
   }

   private void showOffline(Player var1, String var2) {
      OfflinePlayer var3 = Bukkit.getOfflinePlayerIfCached(var2);
      if (var3 != null && var3.getUniqueId() != null && (this.file(var3.getUniqueId()).exists() || this.open.containsKey(var3.getUniqueId()))) {
         try {
            var1.openInventory(this.vault(var3.getUniqueId(), var2, null));
            this.plugin.getLogger().info(var1.getName() + " opened the offline ender chest of " + var2);
         } catch (IllegalStateException var5) {
            say(var1, "<red>That ender chest could not be loaded.");
         }
      } else {
         say(var1, "<red>" + var2 + " has no ender chest saved yet.");
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onEcsee(PlayerCommandPreprocessEvent var1) {
      String[] var2 = var1.getMessage().trim().split("\\s+");
      if (var2.length >= 2) {
         String var3 = var2[0].toLowerCase(Locale.ROOT);
         if (var3.equals("/ecsee") || var3.equals("/flametweaks:ecsee")) {
            Player var4 = var1.getPlayer();
            if (var4.hasPermission("flametweaks.invsee") && Bukkit.getPlayerExact(var2[1]) == null) {
               var1.setCancelled(true);
               this.showOffline(var4, var2[1]);
            }
         }
      }
   }

   private void show(Player var1) {
      Inventory var2;
      try {
         var2 = this.vault(var1);
      } catch (IllegalStateException var4) {
         say(var1, "<red>Your ender chest could not be loaded, tell staff.");
         return;
      }

      this.save(var1.getUniqueId());
      var1.openInventory(var2);
      var1.playSound(var1.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6F, 1.0F);
   }

   private void save(UUID var1) {
      Inventory var2 = this.open.get(var1);
      if (var2 != null) {
         this.dirty.remove(var1);
         byte[] var3 = ItemStack.serializeItemsAsBytes(var2.getContents());
         Player var4 = Bukkit.getPlayer(var1);
         if (var4 != null) {
            this.plugin.tasks().runOn(var4, var4::saveData);
         }

         File var5 = this.file(var1);
         this.io.execute(() -> this.write(var5, var3));
      }
   }

   private void write(File var1, byte[] var2) {
      try {
         File var3 = new File(var1.getParentFile(), var1.getName() + ".tmp");
         Files.write(var3.toPath(), var2);
         Files.move(var3.toPath(), var1.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var4) {
         this.plugin.getLogger().severe("ender vault save failed " + var1.getName() + "  " + var4);
      }
   }

   private void touch(UUID var1, Entity viewer) {
      if (this.dirty.add(var1)) {
         if (this.plugin.tasks().entity(viewer, () -> this.save(var1)) == null) {
            this.dirty.remove(var1);
         }
      }
   }

   private boolean kit(ItemStack var1) {
      return this.plugin.configs().main().getBoolean("ender-vault.allow-kit-items", true)
         ? false
         : var1 != null && !var1.getType().isAir() && this.plugin.items().isKitItem(var1);
   }

   private static UUID owner(Inventory var0) {
      return var0.getHolder() instanceof EnderVault.Holder var1 ? var1.owner : null;
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
            var5 = this.kit(var1.getCursor())
               || var1.getClick() == ClickType.NUMBER_KEY && this.kit(var1.getWhoClicked().getInventory().getItem(var1.getHotbarButton()))
               || var1.getClick() == ClickType.SWAP_OFFHAND && this.kit(var1.getWhoClicked().getInventory().getItemInOffHand());
         } else {
            var5 = var1.isShiftClick() && this.kit(var1.getCurrentItem());
         }

         if (var5) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player var6) {
               say(var6, "<red>Kit items can not go in your ender chest.");
            }
         } else {
            this.touch(var3, var1.getWhoClicked());
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
         if (var4 && this.kit(var1.getOldCursor())) {
            var1.setCancelled(true);
         } else {
            this.touch(var3, var1.getWhoClicked());
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent var1) {
      UUID var2 = owner(var1.getInventory());
      if (var2 != null) {
         this.save(var2);
         Inventory var3 = this.open.get(var2);
         if (Bukkit.getPlayer(var2) == null && var3 != null && var3.getViewers().size() <= 1) {
            this.open.remove(var2);
         }

         if (var1.getPlayer() instanceof Player var4) {
            var4.playSound(var4.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.6F, 1.0F);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onBlock(PlayerInteractEvent var1) {
      if (var1.getHand() == EquipmentSlot.HAND
         && var1.getAction() == Action.RIGHT_CLICK_BLOCK
         && var1.getClickedBlock() != null
         && var1.getClickedBlock().getType() == Material.ENDER_CHEST
         && (!var1.getPlayer().isSneaking() || var1.getItem() == null)) {
         var1.setCancelled(true);
         this.show(var1.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onVanilla(InventoryOpenEvent var1) {
      if (var1.getInventory().getType() == InventoryType.ENDER_CHEST && var1.getPlayer() instanceof Player var2) {
         var1.setCancelled(true);
         Player var6 = null;

         for (Player var5 : Bukkit.getOnlinePlayers()) {
            if (var5.getEnderChest() == var1.getInventory()) {
               var6 = var5;
            }
         }

         Player var7 = var6;
         if (var7 != null && var7 != var2) {
            this.plugin.tasks().entity(var2, () -> {
               try {
                  var2.openInventory(this.vault(var7));
               } catch (IllegalStateException var4) {
                  say(var2, "<red>That ender chest could not be loaded.");
               }
            });
         } else {
            this.plugin.tasks().entity(var2, () -> this.show(var2));
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
         this.save(var2);
         if (var3.getViewers().stream().allMatch(var1x -> var1x.getUniqueId().equals(var2))) {
            for (HumanEntity var5 : List.copyOf(var3.getViewers())) {
               var5.closeInventory();
            }

            this.open.remove(var2);
         }
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

            this.save(var3);
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
            if (var2.length > 0 && EnderVault.this.plugin.staff().isOperator(var3)) {
               Player var5 = Bukkit.getPlayerExact(var2[0]);
               if (var5 == null) {
                  EnderVault.this.showOffline(var3, var2[0]);
               } else {
                  var3.openInventory(EnderVault.this.vault(var5));
               }
            } else if (EnderVault.this.plugin.arena().inCombat(var3)) {
               EnderVault.say(var3, "<red>You can not open your ender chest in combat.");
            } else {
               EnderVault.this.show(var3);
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
