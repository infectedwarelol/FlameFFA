package dev.flamesentinell.flameffacore.crate;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.storage.BlobStore;
import dev.flamesentinell.flameffacore.storage.KeyStore;
import dev.flamesentinell.flameffacore.util.Text;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;

public final class CrateService implements Listener {
   public static final int EDITOR_SLOTS = 54;
   private final FlameFFACore plugin;
   private final SecureRandom random = new SecureRandom();
   private final Map<String, ItemStack[]> loot = new ConcurrentHashMap<>();
   private final Map<String, String> setBy = new ConcurrentHashMap<>();
   private final Map<UUID, CrateService.Roll> rolls = new ConcurrentHashMap<>();
   private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
   private static final int[] STEPS = new int[]{2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 6, 7};
   public static final NamespacedKey WEIGHT = new NamespacedKey("flameffa", "crate_weight");

   public CrateService(FlameFFACore var1) {
      this.plugin = var1;
   }

   public void load() {
      this.loot.clear();
      this.setBy.clear();

      for (Entry var2 : this.plugin.blobs().loadAll("crateloot").entrySet()) {
         this.loot.put((String)var2.getKey(), ((BlobStore.Blob)var2.getValue()).contents());
         this.setBy.put((String)var2.getKey(), ((BlobStore.Blob)var2.getValue()).setBy());
      }

      this.plugin.getSLF4JLogger().info("loaded {} crate loot pools", this.loot.size());
   }

   public List<ItemStack> pool(String var1) {
      ArrayList var2 = new ArrayList();
      ItemStack[] var3 = this.loot.get(var1);
      if (var3 == null) {
         return var2;
      } else {
         for (ItemStack var7 : var3) {
            if (var7 != null && !var7.getType().isAir()) {
               var2.add(var7.clone());
            }
         }

         return var2;
      }
   }

   public boolean hasLoot(String var1) {
      return !this.pool(var1).isEmpty();
   }

   public int pooledCrates() {
      int var1 = 0;

      for (String var3 : this.plugin.crateRegistry().all().keySet()) {
         if (this.hasLoot(var3)) {
            var1++;
         }
      }

      return var1;
   }

   public String setBy(String var1) {
      return this.setBy.getOrDefault(var1, "-");
   }

   public void openEditor(Player var1, Crate var2) {
      CrateService.EditorHolder var3 = new CrateService.EditorHolder(var2.id());
      Inventory var4 = Bukkit.createInventory(var3, 54, this.plugin.configs().msg("crate.editor-title", "crate", var2.id()));
      var3.inventory = var4;
      ItemStack[] var5 = this.loot.get(var2.id());
      if (var5 != null) {
         for (int var6 = 0; var6 < var5.length && var6 < 54; var6++) {
            var4.setItem(var6, var5[var6] == null ? null : var5[var6].clone());
         }
      }

      var1.openInventory(var4);
   }

   @EventHandler
   public void onEditorClose(@NotNull InventoryCloseEvent var1) {
      if (var1.getInventory().getHolder() instanceof CrateService.EditorHolder var2) {
         ItemStack[] var10 = new ItemStack[54];
         int var4 = 0;

         for (int var5 = 0; var5 < 54; var5++) {
            ItemStack var6 = var1.getInventory().getItem(var5);
            if (var6 != null && !var6.getType().isAir()) {
               var10[var5] = this.plugin.items().asTemplate(var6);
               var4++;
            }
         }

         String var11 = var1.getPlayer().getName();
         boolean var12 = this.plugin.blobs().save("crateloot", var2.crateId, var10, var11);
         if (!var12) {
            var1.getPlayer().sendMessage(this.plugin.configs().msg("crate.save-failed", "crate", var2.crateId));
         } else {
            this.loot.put(var2.crateId, var10);
            this.setBy.put(var2.crateId, var11);
            var1.getPlayer()
               .sendMessage(
                  this.plugin
                     .configs()
                     .msg(
                        "crate.loot-saved",
                        "crate",
                        var2.crateId,
                        "count",
                        String.valueOf(var4),
                        "chance",
                        var4 == 0 ? "0" : String.valueOf(Math.round(100.0 / var4))
                     )
               );
            UUID var7 = var1.getPlayer().getUniqueId();
            String var8 = var4 + " entries";
            String var9 = var2.crateId;
            this.plugin.async(() -> this.plugin.audit().staff(var7, var11, "crate_loot_set", null, var9, var8));
         }
      }
   }

   private int freeSlots(Player var1) {
      int var2 = 0;

      for (int var3 = 0; var3 < 36; var3++) {
         ItemStack var4 = var1.getInventory().getItem(var3);
         if (var4 == null || var4.getType().isAir()) {
            var2++;
         }
      }

      return var2;
   }

   private int requiredSlots() {
      return Math.max(1, this.plugin.configs().main().getInt("crates.required-free-slots", 1));
   }

   public void openFromBalance(Player var1, Crate var2) {
      if (!this.hasLoot(var2.id())) {
         var1.sendMessage(this.plugin.configs().msg("crate.no-loot", "crate", var2.id()));
      } else if (this.freeSlots(var1) < this.requiredSlots()) {
         var1.sendMessage(this.plugin.configs().msg("keys.need-slots", "slots", String.valueOf(this.requiredSlots())));
      } else if (!this.busy.add(var1.getUniqueId())) {
         var1.sendMessage(Text.parse("<gray>Wait for your crate to finish rolling."));
      } else {
         UUID var3 = var1.getUniqueId();
         this.plugin.async(() -> {
            KeyStore.Result var4 = this.plugin.keys().spend(var3, var2.id(), 1, "crate_open", var1.getName());
            this.plugin.sync(var1, () -> {
               if (var4 != KeyStore.Result.OK) {
                  this.busy.remove(var3);
                  var1.sendMessage(this.plugin.configs().msg("keys.not-enough", "crate", var2.id()));
               } else {
                  this.reward(var1, var2);
               }
            });
         });
      }
   }

   private static double weight(ItemStack var0) {
      Double var1 = var0.hasItemMeta() ? (Double)var0.getItemMeta().getPersistentDataContainer().get(WEIGHT, PersistentDataType.DOUBLE) : null;
      return var1 == null ? -1.0 : var1;
   }

   public ItemStack peekRoll(String var1) {
      List<ItemStack> var2 = this.pool(var1);
      if (var2.isEmpty()) {
         return null;
      } else {
         double var3 = 0.0;
         boolean var5 = false;

         for (ItemStack var7 : var2) {
            double var8 = weight(var7);
            if (var8 >= 0.0) {
               var5 = true;
            }
         }

         if (!var5) {
            return (ItemStack)var2.get(this.random.nextInt(var2.size()));
         } else {
            for (ItemStack var13 : var2) {
               var3 += Math.max(0.0, weight(var13));
            }

            double var12 = this.random.nextDouble() * var3;
            ItemStack var14 = (ItemStack)var2.get(var2.size() - 1);

            for (ItemStack var10 : var2) {
               var12 -= Math.max(0.0, weight(var10));
               if (var12 < 0.0) {
                  var14 = var10;
                  break;
               }
            }

            ItemStack var15 = var14.clone();
            var15.editMeta(var0 -> var0.getPersistentDataContainer().remove(WEIGHT));
            return var15;
         }
      }
   }

   public void installProbePool(String var1, ItemStack[] var2) {
      this.loot.put(var1, var2);
   }

   public void dropProbePool(String var1) {
      this.loot.remove(var1);
   }

   private void reward(Player var1, Crate var2) {
      ItemStack var3 = this.peekRoll(var2.id());
      if (var3 != null && var1.isOnline()) {
         CrateService.RollHolder var4 = new CrateService.RollHolder();
         Inventory var5 = Bukkit.createInventory(var4, 27, Text.parse(var2.display()));
         var4.inventory = var5;
         ItemStack var6 = pane(Material.BLACK_STAINED_GLASS_PANE);

         for (int var7 = 0; var7 < 27; var7++) {
            var5.setItem(var7, var6);
         }

         var5.setItem(4, pane(Material.LIME_STAINED_GLASS_PANE));
         var5.setItem(22, pane(Material.LIME_STAINED_GLASS_PANE));
         CrateService.Roll var9 = new CrateService.Roll();
         var9.crate = var2;
         var9.won = var3;
         var9.inv = var5;

         for (int var8 = 0; var8 < 9; var8++) {
            var9.reel[var8] = this.peekRoll(var2.id());
         }

         this.paint(var9);
         this.rolls.put(var1.getUniqueId(), var9);
         var1.openInventory(var5);
         this.step(var1.getUniqueId());
      } else {
         this.busy.remove(var1.getUniqueId());
         if (var3 != null) {
            this.grant(var1, var2, var3);
         }
      }
   }

   private static ItemStack pane(Material var0) {
      ItemStack var1 = ItemStack.of(var0, 1);
      var1.editMeta(var0x -> var0x.setHideTooltip(true));
      return var1;
   }

   private void paint(CrateService.Roll var1) {
      for (int var2 = 0; var2 < 9; var2++) {
         var1.inv.setItem(9 + var2, var1.reel[var2] == null ? null : var1.reel[var2].clone());
      }
   }

   private void step(UUID var1) {
      CrateService.Roll var2 = this.rolls.get(var1);
      if (var2 != null) {
         if (var2.frame >= STEPS.length) {
            this.finish(var1);
         } else {
            Player var4 = Bukkit.getPlayer(var1);
            if (var4 == null) {
               this.finish(var1);
               return;
            }

            var2.task = this.plugin.tasks().entityLater(var4, () -> {
               CrateService.Roll var2x = this.rolls.get(var1);
               if (var2x != null) {
                  System.arraycopy(var2x.reel, 1, var2x.reel, 0, 8);
                  var2x.reel[8] = var2x.frame == STEPS.length - 5 ? var2x.won : this.peekRoll(var2x.crate.id());
                  var2x.frame++;
                  this.paint(var2x);
                  Player var3 = Bukkit.getPlayer(var1);
                  if (var3 != null) {
                     var3.playSound(var3.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6F, 0.8F + var2x.frame * 0.04F);
                  }

                  this.step(var1);
               }
            }, STEPS[var2.frame]);
         }
      }
   }

   private void finish(UUID var1) {
      CrateService.Roll var2 = this.rolls.remove(var1);
      this.busy.remove(var1);
      if (var2 != null) {
         if (var2.task != null) {
            var2.task.cancel();
         }

         Player var3 = Bukkit.getPlayer(var1);
         if (var3 != null) {
            if (var3.getOpenInventory().getTopInventory() == var2.inv) {
               this.plugin.tasks().entityLater(var3, () -> {
                  if (var3.isOnline() && var3.getOpenInventory().getTopInventory() == var2.inv) {
                     var3.closeInventory();
                  }
               }, 20L);
            }

            this.grant(var3, var2.crate, var2.won);
         }
      }
   }

   @EventHandler
   public void onRollClick(InventoryClickEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof CrateService.RollHolder) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onRollDrag(InventoryDragEvent var1) {
      if (var1.getView().getTopInventory().getHolder() instanceof CrateService.RollHolder) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onRollQuit(PlayerQuitEvent var1) {
      this.finish(var1.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onRollDisable(PluginDisableEvent var1) {
      if (var1.getPlugin() == this.plugin) {
         for (UUID var3 : new ArrayList<>(this.rolls.keySet())) {
            this.finish(var3);
         }
      }
   }

   private void grant(Player var1, Crate var2, ItemStack var3) {
      if (var3 != null) {
         for (ItemStack var5 : var1.getInventory().addItem(new ItemStack[]{var3}).values()) {
            var1.getWorld().dropItemNaturally(var1.getLocation(), var5);
         }

         String var6 = var3.getItemMeta() != null && var3.getItemMeta().hasDisplayName()
            ? Text.plain(var3.getItemMeta().displayName())
            : var3.getType().getKey().getKey();
         var1.playSound(var1.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0F, 1.2F);
         var1.sendMessage(this.plugin.configs().msg("crate.opened", "reward", var6));
         Bukkit.broadcast(
            this.plugin.configs().msg("crate.broadcast", "player", var1.getName(), "crate", Text.plain(Text.parse(var2.display())), "reward", var6)
         );
      }
   }

   public void buy(Player var1, Crate var2, int var3) {
      int var4 = var2.tokenPrice();
      if (var3 < 1) {
         return;
      } else if (var4 <= 0 || (long)var4 * var3 > Integer.MAX_VALUE) {
         var1.sendMessage(this.plugin.configs().msg("crate.not-for-sale", "crate", var2.id()));
      } else {
         int var5 = var4 * var3;
         int var6 = this.plugin.items().countTokens(var1);
         if (var6 < var5) {
            var1.sendMessage(this.plugin.configs().msg("crate.not-enough-tokens", "cost", String.valueOf(var5), "held", String.valueOf(var6)));
         } else {
            int var7 = this.plugin.items().takeTokens(var1, var5);
            if (var7 < var5) {
               var1.getInventory().addItem(new ItemStack[]{this.plugin.items().mintKillTokens(var7)});
               var1.sendMessage(this.plugin.configs().msg("crate.not-enough-tokens", "cost", String.valueOf(var5), "held", String.valueOf(var7)));
            } else {
               UUID var8 = var1.getUniqueId();
               this.plugin
                  .async(
                     () -> {
                        this.plugin.keys().grant(var8, var2.id(), var3, "token_purchase", var1.getName(), null);
                        this.plugin
                           .sync(
                              var1,
                              () -> var1.sendMessage(
                                 this.plugin.configs().msg("crate.bought", "amount", String.valueOf(var3), "crate", var2.id(), "cost", String.valueOf(var5))
                              )
                           );
                     }
                  );
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent var1) {
      if (var1.getHand() == EquipmentSlot.HAND && var1.getAction() == Action.RIGHT_CLICK_BLOCK && var1.getClickedBlock() != null) {
         Crate var2 = this.plugin.crateRegistry().atBlock(var1.getClickedBlock().getLocation());
         if (var2 != null) {
            var1.setCancelled(true);
            this.openFromBalance(var1.getPlayer(), var2);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onVoucherUse(PlayerInteractEvent var1) {
      if (var1.getHand() == EquipmentSlot.HAND && (var1.getAction() == Action.RIGHT_CLICK_AIR || var1.getAction() == Action.RIGHT_CLICK_BLOCK)) {
         ItemStack var2 = var1.getItem();
         if (this.plugin.items().isVoucher(var2)) {
            var1.setCancelled(true);
            Player var3 = var1.getPlayer();
            String var4 = this.plugin.items().voucherCrate(var2);
            Crate var5 = this.plugin.crateRegistry().get(var4);
            if (var5 == null) {
               var3.sendMessage(this.plugin.configs().msg("voucher.dead", "crate", String.valueOf(var4)));
            } else {
               var2.setAmount(var2.getAmount() - 1);
               UUID var6 = var3.getUniqueId();
               this.plugin.async(() -> {
                  KeyStore.Result var4x = this.plugin.keys().grant(var6, var5.id(), 1, "voucher", var3.getName(), null);
                  this.plugin.sync(var3, () -> {
                     if (var4x != KeyStore.Result.OK) {
                        var3.getInventory().addItem(new ItemStack[]{this.plugin.items().mintVoucher(var5.id())});
                        var3.sendMessage(this.plugin.configs().msg("voucher.failed"));
                     } else {
                        var3.playSound(var3.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.4F);
                        var3.sendMessage(this.plugin.configs().msg("voucher.redeemed", "crate", Text.plain(Text.parse(var5.display()))));
                     }
                  });
               });
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onCurrencyPlace(BlockPlaceEvent var1) {
      if (this.plugin.items().isCurrency(var1.getItemInHand())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent var1) {
      if (this.plugin.crateRegistry().isPlaced(var1.getBlock().getLocation())) {
         var1.setCancelled(true);
         var1.getPlayer().sendMessage(this.plugin.configs().msg("crate.protected"));
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPlaceAdjacent(BlockPlaceEvent var1) {
      if (var1.getBlock().getType() == CrateRegistry.CRATE_BLOCK) {
         Block var2 = var1.getBlock();

         for (BlockFace var6 : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (this.plugin.crateRegistry().isPlaced(var2.getRelative(var6).getLocation())) {
               var1.setCancelled(true);
               var1.getPlayer().sendMessage(this.plugin.configs().msg("crate.protected"));
               return;
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onEntityExplode(EntityExplodeEvent var1) {
      var1.blockList().removeIf(var1x -> this.plugin.crateRegistry().isPlaced(var1x.getLocation()));
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onBlockExplode(BlockExplodeEvent var1) {
      var1.blockList().removeIf(var1x -> this.plugin.crateRegistry().isPlaced(var1x.getLocation()));
   }

   private static final class EditorHolder implements InventoryHolder {
      private final String crateId;
      private Inventory inventory;

      private EditorHolder(String var1) {
         this.crateId = var1;
      }

      @NotNull
      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static final class Roll {
      Crate crate;
      ItemStack won;
      Inventory inv;
      ScheduledTask task;
      ItemStack[] reel = new ItemStack[9];
      int frame;
   }

   private static final class RollHolder implements InventoryHolder {
      Inventory inventory;

      @NotNull
      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
