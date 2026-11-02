// author flamesentinell
package dev.flamesentinell.flametrims;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
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
import org.bukkit.plugin.java.JavaPlugin;

/**
 * per player trims that follow the armor slot  any piece put on gets the chosen trim
 * and loses it again the moment it leaves the slot  templates and materials live in the menu
 */
public final class FlameTrims extends JavaPlugin implements Listener {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] NAMES = {"Helmet", "Chestplate", "Leggings", "Boots"};
    private static final Material[] PREVIEW = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
    private static final int[] COL = {1, 3, 5, 7};
    private static final int SIZE = 27, INFO = 4;
    private static final String TEMPLATE_END = "_ARMOR_TRIM_SMITHING_TEMPLATE";
    private static final Map<Material, String> MATS = new EnumMap<>(Material.class);
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

    private final MiniMessage mm = MiniMessage.miniMessage();
    private NamespacedKey mark;
    private NamespacedKey[] tKey, mKey;
    private final Map<UUID, ArmorTrim[]> cache = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();

    private static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    @Override
    public void onEnable() {
        mark = new NamespacedKey(this, "applied");
        tKey = new NamespacedKey[4];
        mKey = new NamespacedKey[4];
        for (int i = 0; i < 4; i++) {
            String s = SLOTS[i].name().toLowerCase(Locale.ROOT);
            tKey[i] = new NamespacedKey(this, "template_" + s);
            mKey[i] = new NamespacedKey(this, "material_" + s);
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::sweep, 20L, 5L);
    }

    @Override
    public void onDisable() {
        // events no longer reach a disabled plugin so open menus are saved by hand
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu m) {
                save(p, m.inv);
                m.inv.clear();
                p.closeInventory();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("players only");
            return true;
        }
        open(p);
        return true;
    }

    // ---------- menu ----------

    private void open(Player p) {
        Menu m = new Menu();
        Inventory inv = Bukkit.createInventory(m, SIZE, mm.deserialize("<gradient:#ff7a18:#ffb347>Armor Trims</gradient>"));
        m.inv = inv;
        inv.setMaxStackSize(1);
        ItemStack fill = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int s = 0; s < SIZE; s++) inv.setItem(s, fill);
        inv.setItem(INFO, named(Material.BOOK, "<gold>How it works", List.of(
                "<gray>Put a <white>smithing template</white> in the middle row",
                "<gray>and a <white>material</white> in the bottom row.",
                "<gray>Any armor you put on in that slot",
                "<gray>shows the trim. Take it off and",
                "<gray>the trim goes away.",
                "<gray>Take items out to get them back.")));
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        for (int i = 0; i < 4; i++) {
            inv.setItem(9 + COL[i], load(pdc, tKey[i]));
            inv.setItem(18 + COL[i], load(pdc, mKey[i]));
        }
        refreshPreviews(inv);
        p.openInventory(inv);
    }

    private static int pieceOf(int raw) {
        for (int i = 0; i < 4; i++) if (raw == 9 + COL[i] || raw == 18 + COL[i]) return i;
        return -1;
    }

    private static boolean isTemplateSlot(int raw) {
        return raw >= 9 && raw < 18;
    }

    private boolean fits(int raw, ItemStack it) {
        if (it == null || it.isEmpty()) return true;
        return isTemplateSlot(raw) ? pattern(it) != null : material(it) != null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu m)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        InventoryAction a = e.getAction();
        if (a == InventoryAction.COLLECT_TO_CURSOR || a == InventoryAction.UNKNOWN || a == InventoryAction.NOTHING) {
            e.setCancelled(true);
            return;
        }
        if (raw < 0) return;
        if (raw >= SIZE) {
            if (!e.isShiftClick()) return;
            // shift click from the own inventory goes one item at a time into the first free slot it fits
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.isEmpty()) return;
            int to = -1;
            for (int i = 0; i < 4 && to < 0; i++) {
                int t = 9 + COL[i], mt = 18 + COL[i];
                if (pattern(cur) != null && empty(m.inv.getItem(t))) to = t;
                else if (material(cur) != null && empty(m.inv.getItem(mt))) to = mt;
            }
            if (to < 0) {
                p.sendMessage(mm.deserialize("<red>That isn't a trim template or trim material, or every slot for it is full."));
                return;
            }
            ItemStack left = cur.clone();
            m.inv.setItem(to, left.asOne());
            left.setAmount(left.getAmount() - 1);
            e.setCurrentItem(left.getAmount() > 0 ? left : null);
            changed(p);
            return;
        }
        if (pieceOf(raw) < 0) {
            e.setCancelled(true);
            return;
        }
        switch (a) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY, DROP_ALL_SLOT, DROP_ONE_SLOT -> {
            }
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                if (!fits(raw, e.getCursor())) {
                    e.setCancelled(true);
                    p.sendMessage(mm.deserialize(isTemplateSlot(raw) ? "<red>This row takes armor trim smithing templates." : "<red>This row takes trim materials like iron, gold, diamond or amethyst."));
                    return;
                }
            }
            default -> {
                e.setCancelled(true);
                return;
            }
        }
        changed(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < SIZE) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Menu m)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        save(p, m.inv);
        // the items now live in player data only
        m.inv.clear();
        applyAll(p);
    }

    /** saves on the next tick once the click has moved the items  also refreshes the previews and the worn armor */
    private void changed(Player p) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu m) {
                save(p, m.inv);
                refreshPreviews(m.inv);
            }
            applyAll(p);
        });
    }

    private void refreshPreviews(Inventory inv) {
        for (int i = 0; i < 4; i++) {
            TrimPattern pat = pattern(inv.getItem(9 + COL[i]));
            TrimMaterial mat = material(inv.getItem(18 + COL[i]));
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Template: " + (pat == null ? "<dark_gray>none" : "<white>" + pretty(name(RegistryKey.TRIM_PATTERN, pat))));
            lore.add("<gray>Material: " + (mat == null ? "<dark_gray>none" : "<white>" + pretty(name(RegistryKey.TRIM_MATERIAL, mat))));
            lore.add(pat != null && mat != null ? "<green>Active on any " + NAMES[i].toLowerCase(Locale.ROOT) + " you wear" : "<yellow>Needs both a template and a material");
            ItemStack prev = named(PREVIEW[i], "<gold>" + NAMES[i], lore);
            if (pat != null && mat != null) prev.editMeta(ArmorMeta.class, am -> am.setTrim(new ArmorTrim(mat, pat)));
            inv.setItem(COL[i], prev);
        }
    }

    // ---------- storage ----------

    private void save(Player p, Inventory inv) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        for (int i = 0; i < 4; i++) {
            store(pdc, tKey[i], inv.getItem(9 + COL[i]));
            store(pdc, mKey[i], inv.getItem(18 + COL[i]));
        }
        cache.remove(p.getUniqueId());
    }

    private static void store(PersistentDataContainer pdc, NamespacedKey k, ItemStack it) {
        if (empty(it)) pdc.remove(k);
        else pdc.set(k, PersistentDataType.BYTE_ARRAY, it.serializeAsBytes());
    }

    private static ItemStack load(PersistentDataContainer pdc, NamespacedKey k) {
        byte[] b = pdc.get(k, PersistentDataType.BYTE_ARRAY);
        if (b == null) return null;
        try {
            return ItemStack.deserializeBytes(b);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private ArmorTrim[] wanted(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), u -> {
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            ArmorTrim[] w = new ArmorTrim[4];
            for (int i = 0; i < 4; i++) {
                TrimPattern pat = pattern(load(pdc, tKey[i]));
                TrimMaterial mat = material(load(pdc, mKey[i]));
                if (pat != null && mat != null) w[i] = new ArmorTrim(mat, pat);
            }
            return w;
        });
    }

    // ---------- trims on worn armor ----------

    private boolean marked(ItemStack it) {
        return it != null && !it.isEmpty() && it.getPersistentDataContainer().has(mark);
    }

    private void strip(ItemStack it) {
        it.editMeta(m -> {
            if (m instanceof ArmorMeta am) am.setTrim(null);
            m.getPersistentDataContainer().remove(mark);
        });
    }

    private static boolean bot(Player p) {
        return p.getScoreboardTags().contains("smbot_bot");
    }

    private void applyAll(Player p) {
        if (bot(p)) return;
        ArmorTrim[] w = wanted(p);
        EntityEquipment eq = p.getEquipment();
        for (int i = 0; i < 4; i++) {
            ItemStack it = eq.getItem(SLOTS[i]);
            if (empty(it) || !Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(it.getType())) continue;
            boolean mk = marked(it);
            if (w[i] == null) {
                if (mk) {
                    strip(it);
                    eq.setItem(SLOTS[i], it, true);
                }
                continue;
            }
            ItemMeta meta = it.getItemMeta();
            if (!(meta instanceof ArmorMeta am)) continue;
            // a trim made at a smithing table stays as it is
            if (!mk && am.hasTrim()) continue;
            if (mk && w[i].equals(am.getTrim())) continue;
            am.setTrim(w[i]);
            am.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(am);
            eq.setItem(SLOTS[i], it, true);
        }
    }

    /** every five ticks  trims leave anything outside the armor slots and worn pieces get theirs */
    private void sweep() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (bot(p)) continue;
            PlayerInventory inv = p.getInventory();
            for (int s = 0; s <= 40; s++) {
                if (s >= 36 && s <= 39) continue;
                ItemStack it = inv.getItem(s);
                if (marked(it)) {
                    strip(it);
                    inv.setItem(s, it);
                }
            }
            ItemStack cur = p.getItemOnCursor();
            if (marked(cur)) {
                strip(cur);
                p.setItemOnCursor(cur);
            }
            Inventory top = p.getOpenInventory().getTopInventory();
            if (!(top.getHolder(false) instanceof Menu)) {
                for (int s = 0; s < top.getSize(); s++) {
                    ItemStack it = top.getItem(s);
                    if (marked(it)) {
                        strip(it);
                        top.setItem(s, it);
                    }
                }
            }
            applyAll(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEquip(EntityEquipmentChangedEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!pending.add(p.getUniqueId())) return;
        Bukkit.getScheduler().runTask(this, () -> {
            pending.remove(p.getUniqueId());
            if (p.isOnline()) applyAll(p);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        for (ItemStack it : e.getDrops()) if (marked(it)) strip(it);
        for (ItemStack it : e.getItemsToKeep()) if (marked(it)) strip(it);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        ItemStack it = item.getItemStack();
        if (marked(it)) {
            strip(it);
            item.setItemStack(it);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStand(PlayerArmorStandManipulateEvent e) {
        if (marked(e.getPlayerItem())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(mm.deserialize("<red>Try again in a moment."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cache.remove(e.getPlayer().getUniqueId());
        pending.remove(e.getPlayer().getUniqueId());
    }

    // ---------- items ----------

    private static TrimPattern pattern(ItemStack it) {
        if (empty(it)) return null;
        String n = it.getType().name();
        if (!n.endsWith(TEMPLATE_END)) return null;
        String k = n.substring(0, n.length() - TEMPLATE_END.length()).toLowerCase(Locale.ROOT);
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(NamespacedKey.minecraft(k));
    }

    private static TrimMaterial material(ItemStack it) {
        if (empty(it)) return null;
        String k = MATS.get(it.getType());
        return k == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).get(NamespacedKey.minecraft(k));
    }

    private static boolean empty(ItemStack it) {
        return it == null || it.isEmpty();
    }

    private static <T extends org.bukkit.Keyed> String name(RegistryKey<T> reg, T t) {
        NamespacedKey k = RegistryAccess.registryAccess().getRegistry(reg).getKey(t);
        return k == null ? "?" : k.getKey();
    }

    private static String pretty(String k) {
        String s = k.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private ItemStack named(Material type, String name, List<String> lore) {
        ItemStack it = new ItemStack(type);
        it.editMeta(m -> {
            m.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(mm.deserialize(s).decoration(TextDecoration.ITALIC, false));
            m.lore(l);
            m.addItemFlags(ItemFlag.values());
        });
        return it;
    }
}
