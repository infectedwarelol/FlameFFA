package dev.flamesentinell.flameffacore.item;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.crate.Crate;
import dev.flamesentinell.flameffacore.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemRegistry {
   private final FlameFFACore plugin;

   public ItemRegistry(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public ItemStack mintKillTokens(int amount) {
      FileConfiguration cfg = this.plugin.configs().main();
      Material material = Material.matchMaterial(cfg.getString("tokens.material", "NETHER_STAR"));
      ItemStack stack = ItemStack.of(material == null ? Material.NETHER_STAR : material, Math.max(1, amount));
      stack.editMeta(meta -> {
         meta.displayName(Text.parse(cfg.getString("tokens.name", "<gold>Kill Token")).decoration(TextDecoration.ITALIC, false));
         List<Component> lore = new ArrayList<>();

         for (String line : cfg.getStringList("tokens.lore")) {
            lore.add(Text.parse(line).decoration(TextDecoration.ITALIC, false));
         }

         if (!lore.isEmpty()) {
            meta.lore(lore);
         }

         meta.getPersistentDataContainer().set(ItemKeys.TOKEN, PersistentDataType.BYTE, (byte)1);
      });
      return stack;
   }

   public ItemStack mintCompressedTokens(int amount) {
      FileConfiguration cfg = this.plugin.configs().main();
      Material material = Material.matchMaterial(cfg.getString("tokens.compressed.material", "PEARLESCENT_FROGLIGHT"));
      ItemStack stack = ItemStack.of(material == null ? Material.PEARLESCENT_FROGLIGHT : material, Math.max(1, amount));
      String worth = String.valueOf(cfg.getInt("tokens.compressed.worth", 64));
      stack.editMeta(
         meta -> {
            meta.displayName(
               Text.parse(cfg.getString("tokens.compressed.name", "<aqua>Compressed Kill Token"), "worth", worth).decoration(TextDecoration.ITALIC, false)
            );
            List<Component> lore = new ArrayList<>();

            for (String line : cfg.getStringList("tokens.compressed.lore")) {
               lore.add(Text.parse(line, "worth", worth).decoration(TextDecoration.ITALIC, false));
            }

            if (!lore.isEmpty()) {
               meta.lore(lore);
            }

            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(ItemKeys.COMPRESSED, PersistentDataType.BYTE, (byte)1);
         }
      );
      return stack;
   }

   public boolean isKillToken(ItemStack stack) {
      ItemMeta meta = metaOf(stack);
      return meta != null && meta.getPersistentDataContainer().has(ItemKeys.TOKEN, PersistentDataType.BYTE);
   }

   public boolean isCompressedToken(ItemStack stack) {
      ItemMeta meta = metaOf(stack);
      return meta != null && meta.getPersistentDataContainer().has(ItemKeys.COMPRESSED, PersistentDataType.BYTE);
   }

   public int countTokens(Player player) {
      int n = 0;

      for (ItemStack stack : player.getInventory().getContents()) {
         if (this.isKillToken(stack)) {
            n += stack.getAmount();
         }
      }

      return n;
   }

   public int takeTokens(Player player, int amount) {
      int left = amount;
      PlayerInventory inv = player.getInventory();

      for (int i = 0; i < inv.getSize() && left > 0; i++) {
         ItemStack stack = inv.getItem(i);
         if (this.isKillToken(stack)) {
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            left -= take;
         }
      }

      return amount - left;
   }

   public ItemStack mintVoucher(String crateId) {
      Crate crate = this.plugin.crateRegistry().get(crateId);
      if (crate == null) {
         return null;
      } else {
         String display = Text.plain(Text.parse(crate.display()));
         ItemStack stack = ItemStack.of(crate.voucher(), 1);
         stack.editMeta(meta -> {
            meta.displayName(this.plugin.configs().msg("voucher.name", "crate", display).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();

            for (String line : this.plugin.configs().messages().getStringList("voucher.lore")) {
               lore.add(Text.parse(line, "crate", display).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ItemKeys.VOUCHER, PersistentDataType.STRING, crate.id());
         });
         return stack;
      }
   }

   public boolean isVoucher(ItemStack stack) {
      ItemMeta meta = metaOf(stack);
      return meta != null && meta.getPersistentDataContainer().has(ItemKeys.VOUCHER, PersistentDataType.STRING);
   }

   public String voucherCrate(ItemStack stack) {
      ItemMeta meta = metaOf(stack);
      return meta == null ? null : (String)meta.getPersistentDataContainer().get(ItemKeys.VOUCHER, PersistentDataType.STRING);
   }

   public boolean isCurrency(ItemStack stack) {
      return this.isKillToken(stack) || this.isCompressedToken(stack) || this.isVoucher(stack);
   }

   public boolean isKitItem(ItemStack stack) {
      ItemMeta meta = metaOf(stack);
      return meta != null && meta.getPersistentDataContainer().has(ItemKeys.KIT, PersistentDataType.BYTE);
   }

   public boolean dropsOnDeath(ItemStack stack) {
      if (stack == null || stack.getType().isAir()) {
         return false;
      } else {
         return this.isKitItem(stack) ? this.plugin.configs().main().getBoolean("loot.drop-kit-items", true) : true;
      }
   }

   public boolean isManuallyDroppable(ItemStack stack) {
      if (stack == null || stack.getType().isAir() || this.isCurrency(stack)) {
         return true;
      } else {
         return this.isKitItem(stack) ? this.plugin.configs().main().getBoolean("loot.allow-manual-drop", false) : true;
      }
   }

   public ItemStack asTemplate(ItemStack source) {
      ItemStack copy = source.clone();
      copy.editMeta(meta -> meta.getPersistentDataContainer().remove(ItemKeys.KIT));
      return copy;
   }

   public ItemStack releaseKitItem(ItemStack stack) {
      return this.isKitItem(stack) ? this.asTemplate(stack) : stack;
   }

   private static ItemMeta metaOf(ItemStack stack) {
      return stack != null && !stack.getType().isAir() && stack.hasItemMeta() ? stack.getItemMeta() : null;
   }
}
