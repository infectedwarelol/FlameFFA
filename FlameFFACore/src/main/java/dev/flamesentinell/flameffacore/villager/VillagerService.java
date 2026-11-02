package dev.flamesentinell.flameffacore.villager;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.item.ItemKeys;
import dev.flamesentinell.flameffacore.storage.VillagerStore;
import dev.flamesentinell.flameffacore.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import dev.flamesentinell.flameffacore.util.Tasks;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public final class VillagerService implements Listener {
   public static final int EDITOR_SLOTS = 54;
   public static final Pattern PRESET_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
   private static final int[] TRADE_STARTS = new int[]{0, 4};
   private static final Set<Integer> DIVIDERS = Set.of(3, 7, 8);
   private final FlameFFACore plugin;
   private final Map<String, ItemStack[]> presets = Collections.synchronizedMap(new LinkedHashMap<>());
   private final Map<String, String> setBy = Collections.synchronizedMap(new LinkedHashMap<>());
   private final Map<UUID, VillagerStore.Row> live = new ConcurrentHashMap<>();

   public VillagerService(FlameFFACore var1) {
      this.plugin = var1;
   }

   public void load() {
      this.presets.clear();
      this.setBy.clear();
      this.plugin.blobs().loadAll("villager").forEach((var1, var2) -> {
         this.presets.put(var1, var2.contents());
         this.setBy.put(var1, var2.setBy());
      });
      this.plugin.getSLF4JLogger().info("loaded {} villager presets", this.presets.size());
   }

   public boolean exists(String var1) {
      return var1 != null && this.presets.containsKey(var1.toLowerCase(Locale.ROOT));
   }

   public Set<String> names() {
      synchronized (this.presets) {
         return new LinkedHashSet<>(this.presets.keySet());
      }
   }

   public String setBy(String var1) {
      return this.setBy.getOrDefault(var1, "-");
   }

   public boolean create(String var1, String var2) {
      String var3 = var1.toLowerCase(Locale.ROOT);
      if (PRESET_NAME.matcher(var3).matches() && !this.presets.containsKey(var3)) {
         ItemStack[] var4 = new ItemStack[54];
         if (!this.plugin.blobs().save("villager", var3, var4, var2)) {
            return false;
         } else {
            this.presets.put(var3, var4);
            this.setBy.put(var3, var2);
            return true;
         }
      } else {
         return false;
      }
   }

   public int delete(String var1) {
      String var2 = var1.toLowerCase(Locale.ROOT);
      if (this.presets.remove(var2) == null) {
         return -1;
      } else {
         this.setBy.remove(var2);
         this.plugin.blobs().delete("villager", var2);
         int var3 = 0;

         for (Entry var5 : new ArrayList<>(this.live.entrySet())) {
            if (((VillagerStore.Row)var5.getValue()).preset().equals(var2)) {
               Entity var6 = Bukkit.getEntity((UUID)var5.getKey());
               if (var6 != null) {
                  var6.remove();
               }

               this.live.remove(var5.getKey());
               var3++;
            }
         }

         this.plugin.villagerStore().removeByPreset(var2);
         return var3;
      }
   }

   public String setAmount(String var1, int var2, int var3, String var4) {
      String var5 = var1.toLowerCase(Locale.ROOT);
      ItemStack[] var6 = this.presets.get(var5);
      if (var6 == null) {
         return "no preset " + var5;
      } else if (var2 >= 0 && var2 < var6.length && var6[var2] != null && !var6[var2].getType().isAir()) {
         ItemStack[] var7 = new ItemStack[var6.length];

         for (int var8 = 0; var8 < var6.length; var8++) {
            var7[var8] = var6[var8] == null ? null : var6[var8].clone();
         }

         int var9 = var7[var2].getMaxStackSize();
         var7[var2].setAmount(Math.max(1, Math.min(var9, var3)));
         if (!this.plugin.blobs().save("villager", var5, var7, var4)) {
            return "save failed";
         } else {
            this.presets.put(var5, var7);
            this.setBy.put(var5, var4);
            return var5 + " slot " + var2 + " is now " + var7[var2].getType().getKey().getKey() + " x" + var7[var2].getAmount();
         }
      } else {
         return "slot " + var2 + " is empty";
      }
   }

   public int tradeCount(String var1) {
      return this.recipes(var1.toLowerCase(Locale.ROOT)).size();
   }

   public void openEditor(Player var1, String var2) {
      String var3 = var2.toLowerCase(Locale.ROOT);
      VillagerService.EditorHolder var4 = new VillagerService.EditorHolder(var3);
      Inventory var5 = Bukkit.createInventory(var4, 54, this.plugin.configs().msg("villager.editor-title", "preset", var3));
      var4.inventory = var5;
      ItemStack[] var6 = this.presets.get(var3);

      for (int var7 = 0; var7 < 54; var7++) {
         if (DIVIDERS.contains(var7 % 9)) {
            var5.setItem(var7, this.divider());
         } else if (var6 != null && var6[var7] != null) {
            var5.setItem(var7, var6[var7].clone());
         }
      }

      var1.openInventory(var5);
   }

   private ItemStack divider() {
      ItemStack var1 = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
      var1.editMeta(var1x -> {
         var1x.displayName(this.plugin.configs().msg("villager.divider-name").decoration(TextDecoration.ITALIC, false));
         ArrayList var2 = new ArrayList();

         for (String var4 : this.plugin.configs().messages().getStringList("villager.divider-lore")) {
            var2.add(Text.parse(var4).decoration(TextDecoration.ITALIC, false));
         }

         var1x.lore(var2);
      });
      return var1;
   }

   @EventHandler
   public void onEditorClick(InventoryClickEvent var1) {
      if (var1.getInventory().getHolder() instanceof VillagerService.EditorHolder) {
         int var2 = var1.getRawSlot();
         if (var2 >= 0 && var2 < 54 && DIVIDERS.contains(var2 % 9)) {
            var1.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onEditorDrag(InventoryDragEvent var1) {
      if (var1.getInventory().getHolder() instanceof VillagerService.EditorHolder) {
         for (int var3 : var1.getRawSlots()) {
            if (var3 < 54 && DIVIDERS.contains(var3 % 9)) {
               var1.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler
   public void onEditorClose(@NotNull InventoryCloseEvent var1) {
      if (var1.getInventory().getHolder() instanceof VillagerService.EditorHolder var2) {
         ItemStack[] var8 = new ItemStack[54];

         for (int var4 = 0; var4 < 54; var4++) {
            if (!DIVIDERS.contains(var4 % 9)) {
               ItemStack var5 = var1.getInventory().getItem(var4);
               if (var5 != null && !var5.getType().isAir()) {
                  var8[var4] = this.plugin.items().asTemplate(var5);
               }
            }
         }

         String var9 = var1.getPlayer().getName();
         if (!this.plugin.blobs().save("villager", var2.presetId, var8, var9)) {
            var1.getPlayer().sendMessage(this.plugin.configs().msg("villager.save-failed", "preset", var2.presetId));
         } else {
            this.presets.put(var2.presetId, var8);
            this.setBy.put(var2.presetId, var9);
            int var10 = this.recipes(var2.presetId).size();
            var1.getPlayer().sendMessage(this.plugin.configs().msg("villager.saved", "preset", var2.presetId, "count", String.valueOf(var10)));
            UUID var6 = var1.getPlayer().getUniqueId();
            String var7 = var2.presetId;
            this.plugin.async(() -> this.plugin.audit().staff(var6, var9, "villager_preset_set", null, var7, var10 + " trades"));
         }
      }
   }

   private List<MerchantRecipe> recipes(String var1) {
      ArrayList var2 = new ArrayList();
      ItemStack[] var3 = this.presets.get(var1);
      if (var3 == null) {
         return var2;
      } else {
         for (int var4 = 0; var4 < 6; var4++) {
            for (int var8 : TRADE_STARTS) {
               int var9 = var4 * 9 + var8;
               ItemStack var10 = var3[var9];
               ItemStack var11 = var3[var9 + 1];
               ItemStack var12 = var3[var9 + 2];
               if (!isEmpty(var12) && (!isEmpty(var10) || !isEmpty(var11))) {
                  MerchantRecipe var13 = new MerchantRecipe(var12.clone(), Integer.MAX_VALUE);
                  var13.setExperienceReward(false);
                  if (!isEmpty(var10)) {
                     var13.addIngredient(var10.clone());
                  }

                  if (!isEmpty(var11)) {
                     var13.addIngredient(var11.clone());
                  }

                  var2.add(var13);
               }
            }
         }

         return var2;
      }
   }

   private static boolean isEmpty(ItemStack var0) {
      return var0 == null || var0.getType().isAir();
   }

   public void openShop(Player var1, String var2) {
      List var3 = this.recipes(var2);
      if (var3.isEmpty()) {
         var1.sendMessage(this.plugin.configs().msg("villager.no-trades", "preset", var2));
      } else {
         Merchant var4 = Bukkit.createMerchant(this.displayName(var2));
         var4.setRecipes(var3);
         var1.openMerchant(var4, true);
      }
   }

   public Component displayName(String var1) {
      return this.plugin.configs().msg("villager.name-format", "name", var1);
   }

   public boolean isTrader(Entity var1) {
      return (var1 instanceof Villager || var1 instanceof Mannequin)
         && var1.getPersistentDataContainer().has(ItemKeys.VILLAGER_PRESET, PersistentDataType.STRING);
   }

   public String presetOf(Entity var1) {
      return (String)var1.getPersistentDataContainer().get(ItemKeys.VILLAGER_PRESET, PersistentDataType.STRING);
   }

   private LivingEntity materialise(String var1, Location var2, SkinFetcher.Skin var3) {
      Location var4 = var2.clone();
      return var3 != null ? (LivingEntity)var4.getWorld().spawn(var4, Mannequin.class, var4x -> {
         this.dress(var4x, var1, var4);
         var4x.setProfile(SkinFetcher.profile(var3));
         var4x.setDescription(Component.empty());
         var4x.setImmovable(true);
      }) : (LivingEntity)var4.getWorld().spawn(var4, Villager.class, var3x -> {
         this.dress(var3x, var1, var4);
         var3x.setProfession(Profession.NONE);
      });
   }

   private void dress(LivingEntity var1, String var2, Location var3) {
      var1.customName(this.displayName(var2));
      var1.setCustomNameVisible(true);
      var1.setAI(false);
      var1.setGravity(false);
      var1.setCollidable(false);
      var1.setInvulnerable(true);
      var1.setSilent(true);
      var1.setPersistent(true);
      var1.setRemoveWhenFarAway(false);
      var1.setRotation(var3.getYaw(), 0.0F);
      var1.getPersistentDataContainer().set(ItemKeys.VILLAGER_PRESET, PersistentDataType.STRING, var2);
   }

   public boolean spawn(String var1, Location var2, String var3) {
      String var4 = var1.toLowerCase(Locale.ROOT);
      if (this.presets.containsKey(var4) && var2.getWorld() != null) {
         LivingEntity var5 = this.materialise(var4, var2, null);
         long var6 = this.plugin.villagerStore().add(var4, var2, var5.getUniqueId(), var3);
         if (var6 < 0L) {
            var5.remove();
            return false;
         } else {
            this.live.put(var5.getUniqueId(), new VillagerStore.Row(var6, var5.getUniqueId(), var4, var2, null));
            return true;
         }
      } else {
         return false;
      }
   }

   private Entity nearest(Player var1, double var2) {
      Entity var4 = null;
      double var5 = var2 * var2;

      for (Entity var8 : var1.getNearbyEntities(var2, var2, var2)) {
         if (this.isTrader(var8) && this.live.containsKey(var8.getUniqueId())) {
            double var9 = var8.getLocation().distanceSquared(var1.getLocation());
            if (var9 < var5) {
               var5 = var9;
               var4 = var8;
            }
         }
      }

      return var4;
   }

   public String removeNearest(Player var1, double var2) {
      Entity var4 = this.nearest(var1, var2);
      if (var4 == null) {
         return null;
      } else {
         VillagerStore.Row var5 = this.live.remove(var4.getUniqueId());
         String var6 = var5.preset();
         var4.remove();
         this.plugin.async(() -> this.plugin.villagerStore().remove(var5.id()));
         return var6;
      }
   }

   public void skinNearest(Player var1, double var2, String var4) {
      Entity var5 = this.nearest(var1, var2);
      if (var5 == null) {
         var1.sendMessage(this.plugin.configs().msg("villager.none-near"));
      } else {
         UUID var6 = var5.getUniqueId();
         var1.sendMessage(this.plugin.configs().msg("villager.skin-looking", "name", var4));
         this.plugin.async(() -> {
            SkinFetcher.Skin var4x = SkinFetcher.fetch(var4);
            this.plugin.sync(var1, () -> {
               if (var4x == null) {
                  var1.sendMessage(this.plugin.configs().msg("villager.skin-unknown", "name", var4));
               } else {
                  VillagerStore.Row var5x = this.reskin(var6, var4x);
                  if (var5x == null) {
                     var1.sendMessage(this.plugin.configs().msg("villager.none-near"));
                  } else {
                     var1.sendMessage(this.plugin.configs().msg("villager.skin-set", "preset", var5x.preset(), "name", var4x.name()));
                  }
               }
            });
         });
      }
   }

   public String unskinNearest(Player var1, double var2) {
      Entity var4 = this.nearest(var1, var2);
      if (var4 == null) {
         return null;
      } else {
         VillagerStore.Row var5 = this.reskin(var4.getUniqueId(), null);
         return var5 == null ? null : var5.preset();
      }
   }

   private VillagerStore.Row reskin(UUID var1, SkinFetcher.Skin var2) {
      VillagerStore.Row var3 = this.live.remove(var1);
      Entity var4 = Bukkit.getEntity(var1);
      if (var3 == null) {
         return null;
      } else {
         if (var4 != null) {
            var4.remove();
         }

         LivingEntity var5 = this.materialise(var3.preset(), var3.location(), var2);
         VillagerStore.Row var6 = new VillagerStore.Row(var3.id(), var5.getUniqueId(), var3.preset(), var3.location(), var2);
         this.live.put(var5.getUniqueId(), var6);
         long var7 = var3.id();
         UUID var9 = var5.getUniqueId();
         this.plugin.async(() -> {
            this.plugin.villagerStore().rebind(var7, var9);
            this.plugin.villagerStore().setSkin(var7, var2);
         });
         return var6;
      }
   }

   public List<VillagerStore.Row> placed() {
      return new ArrayList<>(this.live.values());
   }

   public void restore() {
      this.live.clear();
      if (Tasks.FOLIA) {
         this.plugin.async(() -> {
            for (VillagerStore.Row row : this.plugin.villagerStore().loadAll()) {
               if (this.presets.containsKey(row.preset())) {
                  Location at = row.location();
                  if (at != null && at.getWorld() != null) {
                     at.getWorld().getChunkAtAsync(at).thenRun(() -> this.plugin.tasks().runAt(at, () -> this.restoreOne(row)));
                  }
               }
            }
         });
      } else {
         int rebuilt = 0;

         for (VillagerStore.Row row : this.plugin.villagerStore().loadAll()) {
            if (this.presets.containsKey(row.preset())) {
               row.location().getChunk().load();
               if (this.restoreOne(row)) {
                  rebuilt++;
               }
            }
         }

         this.plugin.getSLF4JLogger().info("restored {} trader villagers, {} rebuilt", this.live.size(), rebuilt);
      }
   }

   private boolean restoreOne(VillagerStore.Row var3) {
      Location var4 = var3.location();
      Entity var5 = var3.entity() == null ? null : Bukkit.getEntity(var3.entity());
      boolean var6 = var5 instanceof Mannequin == (var3.skin() != null);
      if (var5 != null && var5.isValid() && this.isTrader(var5) && var6) {
         this.live.put(var5.getUniqueId(), var3);
         return false;
      } else {
         if (var5 != null) {
            var5.remove();
         }

         LivingEntity var7 = this.materialise(var3.preset(), var4, var3.skin());
         long var8 = var3.id();
         UUID var10 = var7.getUniqueId();
         this.plugin.async(() -> this.plugin.villagerStore().rebind(var8, var10));
         this.live.put(var10, new VillagerStore.Row(var8, var10, var3.preset(), var4, var3.skin()));
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEntityEvent var1) {
      if (var1.getHand() == EquipmentSlot.HAND && this.isTrader(var1.getRightClicked())) {
         var1.setCancelled(true);
         String var2 = this.presetOf(var1.getRightClicked());
         if (var2 != null) {
            this.openShop(var1.getPlayer(), var2);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageEvent var1) {
      if (this.isTrader(var1.getEntity())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onTransform(EntityTransformEvent var1) {
      if (this.isTrader(var1.getEntity())) {
         var1.setCancelled(true);
      }
   }

   private static final class EditorHolder implements InventoryHolder {
      private final String presetId;
      private Inventory inventory;

      private EditorHolder(String var1) {
         this.presetId = var1;
      }

      @NotNull
      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
