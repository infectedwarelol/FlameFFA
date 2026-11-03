package dev.flamesentinell.flameffacore.cosmetic;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class CosmeticMenu implements Listener {
   private static final int SIZE = 36;
   private final FlameFFACore plugin;

   public CosmeticMenu(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   private NamespacedKey slotKey() {
      return new NamespacedKey(this.plugin, "cosmetic_slot");
   }

   private NamespacedKey idKey() {
      return new NamespacedKey(this.plugin, "cosmetic_id");
   }

   public void open(Player player) {
      CosmeticMenu.Holder holder = new CosmeticMenu.Holder();
      Inventory menu = Bukkit.createInventory(holder, 36, this.plugin.configs().msg("cosmetics.title"));
      holder.inventory = menu;
      int row = 0;

      for (String slot : List.of("kill", "aura")) {
         menu.setItem(row * 18, this.label(slot));
         int column = 0;
         menu.setItem(row * 18 + 2, this.entry(player, slot, null));
         column++;

         for (CosmeticService.Cosmetic cosmetic : this.plugin.cosmetics().catalogue(slot)) {
            int index = row * 18 + 2 + column;
            if (index >= row * 18 + 9) {
               break;
            }

            menu.setItem(index, this.entry(player, slot, cosmetic));
            column++;
         }

         row++;
      }

      player.openInventory(menu);
   }

   private void reopen(Player player) {
      this.plugin.tasks().entity(player, () -> {
         if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder(false) instanceof CosmeticMenu.Holder) {
            this.open(player);
         }
      });
   }

   private ItemStack label(String slot) {
      byte var4 = -1;
      switch (slot.hashCode()) {
         case 3291998:
            if (slot.equals("kill")) {
               var4 = 0;
            }
         default:
            ItemStack item = ItemStack.of(switch (var4) {
               case 0 -> Material.NETHERITE_SWORD;
               default -> Material.NETHER_STAR;
            });
            item.editMeta(meta -> {
               meta.displayName(this.plugin.configs().msg("cosmetics.slot-" + slot).decoration(TextDecoration.ITALIC, false));
               meta.setEnchantmentGlintOverride(false);
            });
            return item;
      }
   }

   private ItemStack entry(Player player, String slot, CosmeticService.Cosmetic cosmetic) {
      boolean none = cosmetic == null;
      boolean owned = none || this.plugin.cosmetics().owns(player, cosmetic);
      boolean active = none ? this.plugin.cosmetics().picked(player, slot) == null : cosmetic.id().equals(this.plugin.cosmetics().picked(player, slot));
      ItemStack item = ItemStack.of(none ? Material.BARRIER : (owned ? Material.FIREWORK_STAR : Material.GRAY_DYE));
      item.editMeta(
         meta -> {
            meta.displayName(
               Text.parse(none ? this.plugin.configs().get("cosmetics.yml").getString("none-display", "<gray>None") : cosmetic.display())
                  .decoration(TextDecoration.ITALIC, false)
            );
            List<Component> lore = new ArrayList<>();
            if (!owned) {
               lore.add(this.plugin.configs().msg("cosmetics.locked").decoration(TextDecoration.ITALIC, false));
            } else if (active) {
               lore.add(this.plugin.configs().msg("cosmetics.active").decoration(TextDecoration.ITALIC, false));
            } else {
               lore.add(this.plugin.configs().msg("cosmetics.select").decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            if (active) {
               meta.setEnchantmentGlintOverride(true);
            }

            meta.getPersistentDataContainer().set(this.slotKey(), PersistentDataType.STRING, slot);
            meta.getPersistentDataContainer().set(this.idKey(), PersistentDataType.STRING, none ? "none" : cosmetic.id());
         }
      );
      return item;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      boolean menu = event.getView().getTopInventory().getHolder(false) instanceof CosmeticMenu.Holder;
      if (menu) {
         event.setCancelled(true);
      }

      if (event.getWhoClicked() instanceof Player player && (!menu || event.getClickedInventory() == event.getView().getTopInventory())) {
         ItemStack clicked = event.getCurrentItem();
         if (clicked != null && clicked.hasItemMeta()) {
            PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
            String slot = (String)pdc.get(this.slotKey(), PersistentDataType.STRING);
            String id = (String)pdc.get(this.idKey(), PersistentDataType.STRING);
            if (slot != null && id != null) {
               event.setCancelled(true);
               if (id.equals("none")) {
                  this.plugin.cosmetics().pick(player, slot, null);
                  player.sendMessage(this.plugin.configs().msg("cosmetics.cleared", "slot", slot));
                  this.reopen(player);
               } else {
                  CosmeticService.Cosmetic cosmetic = this.plugin.cosmetics().find(slot, id);
                  if (cosmetic != null) {
                     if (!this.plugin.cosmetics().owns(player, cosmetic)) {
                        player.sendMessage(this.plugin.configs().msg("cosmetics.locked-message"));
                     } else {
                        this.plugin.cosmetics().pick(player, slot, id);
                        player.sendMessage(this.plugin.configs().msg("cosmetics.picked", "name", Text.plain(Text.parse(cosmetic.display()))));
                        this.reopen(player);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder(false) instanceof CosmeticMenu.Holder) {
         event.setCancelled(true);
      }
   }

   private static final class Holder implements InventoryHolder {
      private Inventory inventory;

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}
