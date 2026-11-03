package dev.flamesentinell.flameffacore.trims;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.module.Module;
import dev.flamesentinell.flameffacore.module.ModuleCommand;
import dev.flamesentinell.flameffacore.util.Bots;
import dev.flamesentinell.flameffacore.util.Tasks;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class TrimsModule implements Module, Listener {
   private static final String NAMESPACE = "flametrims";
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final String[] NAMES = new String[]{"Helmet", "Chestplate", "Leggings", "Boots"};
   private static final Material[] PREVIEW = new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
   private static final int[] COL = new int[]{1, 3, 5, 7};
   private static final int SIZE = 27;
   private static final int INFO = 4;
   private static final String TEMPLATE_END = "_ARMOR_TRIM_SMITHING_TEMPLATE";
   private static final Map<Material, String> MATS = new EnumMap<>(Material.class);
   private final FlameFFACore plugin;
   private final MiniMessage mm = MiniMessage.miniMessage();
   private final NamespacedKey mark = new NamespacedKey(NAMESPACE, "applied");
   private final NamespacedKey[] templateKeys = new NamespacedKey[4];
   private final NamespacedKey[] materialKeys = new NamespacedKey[4];
   private final Map<UUID, ArmorTrim[]> cache = new ConcurrentHashMap<>();
   private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
   private final Map<UUID, ScheduledTask> sweepers = new ConcurrentHashMap<>();

   static {
      MATS.put(Material.IRON_INGOT, "iron");
      MATS.put(Material.COPPER_INGOT, "copper");
      MATS.put(Material.GOLD_INGOT, "gold");
      MATS.put(Material.LAPIS_LAZULI, "lapis");
      MATS.put(Material.EMERALD, "emerald");
      MATS.put(Material.DIAMOND, "diamond");
      MATS.put(Material.NETHERITE_INGOT, "netherite");
      MATS.put(Material.REDSTONE, "redstone");
      MATS.put(Material.AMETHYST_SHARD, "amethyst");
      MATS.put(Material.QUARTZ, "quartz");
      MATS.put(Material.RESIN_BRICK, "resin");
   }

   public TrimsModule(FlameFFACore plugin) {
      this.plugin = plugin;

      for (int index = 0; index < 4; index++) {
         String slot = SLOTS[index].name().toLowerCase(Locale.ROOT);
         this.templateKeys[index] = new NamespacedKey(NAMESPACE, "template_" + slot);
         this.materialKeys[index] = new NamespacedKey(NAMESPACE, "material_" + slot);
      }
   }

   @Override
   public String id() {
      return "trims";
   }

   @Override
   public void enable() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.plugin.tasks().runOn(player, () -> this.startSweeper(player));
      }
   }

   @Override
   public void disable() {
      HandlerList.unregisterAll(this);

      for (ScheduledTask sweeper : this.sweepers.values()) {
         sweeper.cancel();
      }

      this.sweepers.clear();

      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof TrimsModule.Menu menu) {
               this.save(player, menu.inv);
               menu.inv.clear();
               player.closeInventory();
            }
         } catch (RuntimeException offThread) {
            this.plugin.getSLF4JLogger().warn("could not save the open trim menu of {}", player.getName());
         }
      }

      this.cache.clear();
      this.pending.clear();
   }

   @Override
   public void commands(Commands commands) {
      ModuleCommand.register(commands, "trim", "pick the trim for each armor slot", List.of("trims"), "flametrims.use", null, (sender, label, args) -> {
         this.command(sender);
         return true;
      }, null);
   }

   private void command(CommandSender sender) {
      if (sender instanceof Player player) {
         this.open(player);
      } else {
         sender.sendMessage("players only");
      }
   }

   private void startSweeper(Player player) {
      UUID uuid = player.getUniqueId();
      Tasks.cancel(this.sweepers.remove(uuid));
      ScheduledTask sweeper = this.plugin.tasks().entityTimer(player, task -> this.sweep(player), () -> this.sweepers.remove(uuid), 20L, 5L);
      if (sweeper != null) {
         this.sweepers.put(uuid, sweeper);
      }
   }

   private void open(Player player) {
      if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof TrimsModule.Menu) {
         player.closeInventory();
      }

      TrimsModule.Menu menu = new TrimsModule.Menu();
      Inventory inv = Bukkit.createInventory(menu, SIZE, this.mm.deserialize("<gradient:#ff7a18:#ffb347>Armor Trims</gradient>"));
      menu.inv = inv;
      inv.setMaxStackSize(1);
      ItemStack fill = this.named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());

      for (int slot = 0; slot < SIZE; slot++) {
         inv.setItem(slot, fill);
      }

      inv.setItem(
         INFO,
         this.named(
            Material.BOOK,
            "<gold>How it works",
            List.of(
               "<gray>Put a <white>smithing template</white> in the middle row",
               "<gray>and a <white>material</white> in the bottom row.",
               "<gray>Any armor you put on in that slot",
               "<gray>shows the trim. Take it off and",
               "<gray>the trim goes away.",
               "<gray>Take items out to get them back."
            )
         )
      );
      PersistentDataContainer pdc = player.getPersistentDataContainer();

      for (int index = 0; index < 4; index++) {
         inv.setItem(9 + COL[index], load(pdc, this.templateKeys[index]));
         inv.setItem(18 + COL[index], load(pdc, this.materialKeys[index]));
      }

      this.refreshPreviews(inv);
      player.openInventory(inv);
   }

   private static int pieceOf(int raw) {
      for (int index = 0; index < 4; index++) {
         if (raw == 9 + COL[index] || raw == 18 + COL[index]) {
            return index;
         }
      }

      return -1;
   }

   private static boolean isTemplateSlot(int raw) {
      return raw >= 9 && raw < 18;
   }

   private boolean fits(int raw, ItemStack item) {
      if (empty(item)) {
         return true;
      } else {
         return isTemplateSlot(raw) ? pattern(item) != null : material(item) != null;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getView().getTopInventory().getHolder(false) instanceof TrimsModule.Menu menu && event.getWhoClicked() instanceof Player player) {
         int raw = event.getRawSlot();
         InventoryAction action = event.getAction();
         if (action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.UNKNOWN || action == InventoryAction.NOTHING) {
            event.setCancelled(true);
         } else if (raw >= 0) {
            if (raw >= SIZE) {
               if (event.isShiftClick()) {
                  event.setCancelled(true);
                  ItemStack current = event.getCurrentItem();
                  if (!empty(current)) {
                     int target = -1;

                     for (int index = 0; index < 4 && target < 0; index++) {
                        int templateSlot = 9 + COL[index];
                        int materialSlot = 18 + COL[index];
                        if (pattern(current) != null && empty(menu.inv.getItem(templateSlot))) {
                           target = templateSlot;
                        } else if (material(current) != null && empty(menu.inv.getItem(materialSlot))) {
                           target = materialSlot;
                        }
                     }

                     if (target < 0) {
                        player.sendMessage(this.mm.deserialize("<red>That isn't a trim template or trim material, or every slot for it is full."));
                     } else {
                        ItemStack left = current.clone();
                        menu.inv.setItem(target, left.asOne());
                        left.setAmount(left.getAmount() - 1);
                        event.setCurrentItem(left.getAmount() > 0 ? left : null);
                        this.changed(player);
                     }
                  }
               }
            } else if (pieceOf(raw) < 0) {
               event.setCancelled(true);
            } else {
               switch (action) {
                  case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY, DROP_ALL_SLOT, DROP_ONE_SLOT -> {
                  }
                  case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                     if (!this.fits(raw, event.getCursor())) {
                        event.setCancelled(true);
                        player.sendMessage(
                           this.mm
                              .deserialize(
                                 isTemplateSlot(raw)
                                    ? "<red>This row takes armor trim smithing templates."
                                    : "<red>This row takes trim materials like iron, gold, diamond or amethyst."
                              )
                        );
                        return;
                     }
                  }
                  default -> {
                     event.setCancelled(true);
                     return;
                  }
               }

               this.changed(player);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder(false) instanceof TrimsModule.Menu) {
         for (int raw : event.getRawSlots()) {
            if (raw < SIZE) {
               event.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (event.getInventory().getHolder(false) instanceof TrimsModule.Menu menu && event.getPlayer() instanceof Player player) {
         this.save(player, menu.inv);
         menu.inv.clear();
         this.applyAll(player);
      }
   }

   private void changed(Player player) {
      this.plugin.tasks().entity(player, () -> {
         if (player.isOnline()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof TrimsModule.Menu menu) {
               this.save(player, menu.inv);
               this.refreshPreviews(menu.inv);
            }

            this.applyAll(player);
         }
      });
   }

   private void refreshPreviews(Inventory inv) {
      for (int index = 0; index < 4; index++) {
         TrimPattern pattern = pattern(inv.getItem(9 + COL[index]));
         TrimMaterial material = material(inv.getItem(18 + COL[index]));
         List<String> lore = new ArrayList<>();
         lore.add("<gray>Template: " + (pattern == null ? "<dark_gray>none" : "<white>" + pretty(name(RegistryKey.TRIM_PATTERN, pattern))));
         lore.add("<gray>Material: " + (material == null ? "<dark_gray>none" : "<white>" + pretty(name(RegistryKey.TRIM_MATERIAL, material))));
         lore.add(
            pattern != null && material != null
               ? "<green>Active on any " + NAMES[index].toLowerCase(Locale.ROOT) + " you wear"
               : "<yellow>Needs both a template and a material"
         );
         ItemStack preview = this.named(PREVIEW[index], "<gold>" + NAMES[index], lore);
         if (pattern != null && material != null) {
            preview.editMeta(ArmorMeta.class, armor -> armor.setTrim(new ArmorTrim(material, pattern)));
         }

         inv.setItem(COL[index], preview);
      }
   }

   private void save(Player player, Inventory inv) {
      PersistentDataContainer pdc = player.getPersistentDataContainer();

      for (int index = 0; index < 4; index++) {
         store(pdc, this.templateKeys[index], inv.getItem(9 + COL[index]));
         store(pdc, this.materialKeys[index], inv.getItem(18 + COL[index]));
      }

      this.cache.remove(player.getUniqueId());
   }

   private static void store(PersistentDataContainer pdc, NamespacedKey key, ItemStack item) {
      if (empty(item)) {
         pdc.remove(key);
      } else {
         pdc.set(key, PersistentDataType.BYTE_ARRAY, item.serializeAsBytes());
      }
   }

   private static ItemStack load(PersistentDataContainer pdc, NamespacedKey key) {
      byte[] bytes = pdc.get(key, PersistentDataType.BYTE_ARRAY);
      if (bytes == null) {
         return null;
      } else {
         try {
            return ItemStack.deserializeBytes(bytes);
         } catch (RuntimeException corrupt) {
            return null;
         }
      }
   }

   private ArmorTrim[] wanted(Player player) {
      return this.cache.computeIfAbsent(player.getUniqueId(), uuid -> {
         PersistentDataContainer pdc = player.getPersistentDataContainer();
         ArmorTrim[] trims = new ArmorTrim[4];

         for (int index = 0; index < 4; index++) {
            TrimPattern pattern = pattern(load(pdc, this.templateKeys[index]));
            TrimMaterial material = material(load(pdc, this.materialKeys[index]));
            if (pattern != null && material != null) {
               trims[index] = new ArmorTrim(material, pattern);
            }
         }

         return trims;
      });
   }

   private boolean marked(ItemStack item) {
      return !empty(item) && item.getPersistentDataContainer().has(this.mark);
   }

   private void strip(ItemStack item) {
      item.editMeta(meta -> {
         if (meta instanceof ArmorMeta armor) {
            armor.setTrim(null);
         }

         meta.getPersistentDataContainer().remove(this.mark);
      });
   }

   private void applyAll(Player player) {
      if (!Bots.is(player)) {
         ArmorTrim[] wanted = this.wanted(player);
         EntityEquipment equipment = player.getEquipment();

         for (int index = 0; index < 4; index++) {
            ItemStack item = equipment.getItem(SLOTS[index]);
            if (!empty(item) && Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(item.getType())) {
               boolean marked = this.marked(item);
               if (wanted[index] == null) {
                  if (marked) {
                     this.strip(item);
                     equipment.setItem(SLOTS[index], item, true);
                  }
               } else {
                  ItemMeta meta = item.getItemMeta();
                  if (meta instanceof ArmorMeta armor && (marked || !armor.hasTrim()) && (!marked || !wanted[index].equals(armor.getTrim()))) {
                     armor.setTrim(wanted[index]);
                     armor.getPersistentDataContainer().set(this.mark, PersistentDataType.BYTE, (byte)1);
                     item.setItemMeta(armor);
                     equipment.setItem(SLOTS[index], item, true);
                  }
               }
            }
         }
      }
   }

   private void sweep(Player player) {
      if (player.isOnline() && !Bots.is(player)) {
         PlayerInventory inventory = player.getInventory();

         for (int slot = 0; slot <= 40; slot++) {
            if (slot < 36 || slot > 39) {
               ItemStack item = inventory.getItem(slot);
               if (this.marked(item)) {
                  this.strip(item);
                  inventory.setItem(slot, item);
               }
            }
         }

         ItemStack cursor = player.getItemOnCursor();
         if (this.marked(cursor)) {
            this.strip(cursor);
            player.setItemOnCursor(cursor);
         }

         Inventory top = player.getOpenInventory().getTopInventory();
         if (!(top.getHolder(false) instanceof TrimsModule.Menu)) {
            for (int slot = 0; slot < top.getSize(); slot++) {
               ItemStack item = top.getItem(slot);
               if (this.marked(item)) {
                  this.strip(item);
                  top.setItem(slot, item);
               }
            }
         }

         this.applyAll(player);
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.startSweeper(event.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onEquip(EntityEquipmentChangedEvent event) {
      if (event.getEntity() instanceof Player player && this.pending.add(player.getUniqueId())) {
         if (this.plugin.tasks().entity(player, () -> {
            this.pending.remove(player.getUniqueId());
            if (player.isOnline()) {
               this.applyAll(player);
            }
         }) == null) {
            this.pending.remove(player.getUniqueId());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDeath(PlayerDeathEvent event) {
      for (ItemStack item : event.getDrops()) {
         if (this.marked(item)) {
            this.strip(item);
         }
      }

      for (ItemStack item : event.getItemsToKeep()) {
         if (this.marked(item)) {
            this.strip(item);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onSpawn(ItemSpawnEvent event) {
      Item entity = event.getEntity();
      ItemStack item = entity.getItemStack();
      if (this.marked(item)) {
         this.strip(item);
         entity.setItemStack(item);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onStand(PlayerArmorStandManipulateEvent event) {
      if (this.marked(event.getPlayerItem())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(this.mm.deserialize("<red>Try again in a moment."));
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.cache.remove(uuid);
      this.pending.remove(uuid);
      Tasks.cancel(this.sweepers.remove(uuid));
   }

   private static TrimPattern pattern(ItemStack item) {
      if (empty(item)) {
         return null;
      } else {
         String name = item.getType().name();
         if (!name.endsWith(TEMPLATE_END)) {
            return null;
         } else {
            String key = name.substring(0, name.length() - TEMPLATE_END.length()).toLowerCase(Locale.ROOT);
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(NamespacedKey.minecraft(key));
         }
      }
   }

   private static TrimMaterial material(ItemStack item) {
      if (empty(item)) {
         return null;
      } else {
         String key = MATS.get(item.getType());
         return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).get(NamespacedKey.minecraft(key));
      }
   }

   private static boolean empty(ItemStack item) {
      return item == null || item.isEmpty();
   }

   private static <T extends Keyed> String name(RegistryKey<T> registry, T value) {
      NamespacedKey key = RegistryAccess.registryAccess().getRegistry(registry).getKey(value);
      return key == null ? "?" : key.getKey();
   }

   private static String pretty(String key) {
      String spaced = key.replace('_', ' ');
      return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
   }

   private ItemStack named(Material type, String name, List<String> lore) {
      ItemStack item = new ItemStack(type);
      item.editMeta(meta -> {
         meta.displayName(this.mm.deserialize(name).decoration(TextDecoration.ITALIC, false));
         List<Component> lines = new ArrayList<>();

         for (String line : lore) {
            lines.add(this.mm.deserialize(line).decoration(TextDecoration.ITALIC, false));
         }

         meta.lore(lines);
         meta.addItemFlags(ItemFlag.values());
      });
      return item;
   }

   private static final class Menu implements InventoryHolder {
      Inventory inv;

      public Inventory getInventory() {
         return this.inv;
      }
   }
}
