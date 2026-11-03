package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.crate.Crate;
import dev.flamesentinell.flameffacore.crate.CrateService;
import dev.flamesentinell.flameffacore.storage.CrateBlockStore;
import dev.flamesentinell.flameffacore.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

public final class CrateCommand implements BasicCommand {
   private final FlameFFACore plugin;

   public CrateCommand(FlameFFACore var1) {
      this.plugin = var1;
   }

   public void execute(@NotNull CommandSourceStack var1, @NotNull String[] var2) {
      if (var2.length > 0 && "import".equalsIgnoreCase(var2[0])) {
         this.importLoot(var1.getSender(), var2);
      } else if (var1.getSender() instanceof Player var3) {
         if (var2.length == 0) {
            this.showBalance(var3);
         } else {
            String var10 = var2[0].toLowerCase(Locale.ROOT);
            switch (var10) {
               case "place":
                  this.place(var3, var2);
                  return;
               case "remove":
                  this.remove(var3);
                  return;
               case "list":
                  this.listPlaced(var3);
                  return;
               case "voucher":
                  this.voucher(var3, var2);
                  return;
            }

            Crate var5 = this.plugin.crateRegistry().get(var10);
            if (var5 != null && var2.length > 1 && "set".equalsIgnoreCase(var2[1])) {
               this.editor(var3, var5);
            } else if ("set".equals(var10)) {
               if (var2.length < 2) {
                  var3.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/crate <crate> set"));
               } else {
                  Crate var11 = this.plugin.crateRegistry().get(var2[1]);
                  if (var11 == null) {
                     var3.sendMessage(this.plugin.configs().msg("crate.unknown", "crate", var2[1]));
                  } else {
                     this.editor(var3, var11);
                  }
               }
            } else if (var2.length < 2) {
               var3.sendMessage(
                  this.plugin
                     .configs()
                     .msg("general.usage", "usage", "/crate open|buy <crate> [n] | place <crate> | remove | list | voucher <crate> | <crate> set")
               );
            } else {
               Crate var12 = this.plugin.crateRegistry().get(var2[1]);
               if (var12 == null) {
                  var3.sendMessage(this.plugin.configs().msg("crate.unknown", "crate", var2[1]));
               } else {
                  switch (var10) {
                     case "open":
                        this.plugin.crates().openFromBalance(var3, var12);
                        break;
                     case "buy":
                        int var9 = var2.length > 2 ? parse(var2[2]) : 1;
                        this.plugin.crates().buy(var3, var12, Math.max(1, Math.min(64, var9)));
                        break;
                     default:
                        var3.sendMessage(
                           this.plugin
                              .configs()
                              .msg("general.usage", "usage", "/crate open|buy <crate> [n] | place <crate> | remove | list | voucher <crate> | <crate> set")
                        );
                  }
               }
            }
         }
      } else {
         var1.getSender().sendMessage(this.plugin.configs().msg("general.players-only"));
      }
   }

   private void importLoot(CommandSender var1, String[] var2) {
      if (var1 instanceof Player var3 && !this.plugin.staff().isOperator(var3)) {
         var1.sendMessage(this.plugin.configs().msg("general.operator-only"));
      } else if (var2.length >= 2 && this.plugin.crateRegistry().get(var2[1]) != null) {
         String var4 = this.plugin.crateRegistry().get(var2[1]).id();
         File var5 = new File(this.plugin.getDataFolder(), "crate_import/" + var4 + ".yml");
         if (!var5.exists()) {
            var1.sendMessage("missing " + var5.getPath());
         } else {
            YamlConfiguration var6 = YamlConfiguration.loadConfiguration(var5);
            ArrayList var7 = new ArrayList();
            StringBuilder var8 = new StringBuilder();

            for (Map var10 : var6.getMapList("entries")) {
               String var11 = String.valueOf(var10.get("item"));
               int var12 = var10.get("amount") instanceof Number var13 ? var13.intValue() : 1;
               int var27 = var10.get("slots") instanceof Number var28 ? var28.intValue() : 1;
               double var29;
               double var34 = var29 = var10.get("chance") instanceof Number var18 ? var18.doubleValue() : -1.0;
               if (var29 >= 0.0) {
                  var27 = 1;
               }

               ItemStack var30;
               try {
                  if (var11.equalsIgnoreCase("killtokens")) {
                     var30 = this.plugin.items().mintKillTokens(var12);
                  } else if (var11.equalsIgnoreCase("compressedtokens")) {
                     var30 = this.plugin.items().mintCompressedTokens(var12);
                  } else if (var11.toLowerCase(Locale.ROOT).startsWith("tokenbundle:")) {
                     String[] var31 = var11.split(":");
                     if (var31.length != 3) {
                        throw new IllegalArgumentException("use tokenbundle:<compressed>:<tokens>");
                     }

                     int var20 = Integer.parseInt(var31[1].trim());
                     int var21 = Integer.parseInt(var31[2].trim());
                     ArrayList var22 = new ArrayList();
                     if (var20 > 0) {
                        var22.add(this.plugin.items().mintCompressedTokens(var20));
                     }

                     if (var21 > 0) {
                        var22.add(this.plugin.items().mintKillTokens(var21));
                     }

                     var30 = ItemStack.of(Material.BUNDLE);
                     var30.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(var22));
                     String var23 = this.plugin
                        .configs()
                        .main()
                        .getString("tokens.bundle-name", "<aqua><compressed>x Compressed Kill Token <gray>+ <gold><tokens>x Kill Tokens");
                     var30.editMeta(
                        var3x -> var3x.displayName(
                           Text.parse(var23, "compressed", String.valueOf(var20), "tokens", String.valueOf(var21)).decoration(TextDecoration.ITALIC, false)
                        )
                     );
                  } else if (var11.startsWith("weapon:")) {
                     var30 = this.plugin.weapons().mint(var11.substring(7));
                     if (var30 == null) {
                        throw new IllegalArgumentException("unknown weapon");
                     }
                  } else {
                     var30 = Bukkit.getItemFactory().createItemStack(var11);
                  }
               } catch (IllegalArgumentException var24) {
                  var1.sendMessage("bad item " + var11 + "  " + var24.getMessage());
                  return;
               }

               var30.setAmount(var12);
               if (var29 >= 0.0) {
                  var30.editMeta(var2x -> var2x.getPersistentDataContainer().set(CrateService.WEIGHT, PersistentDataType.DOUBLE, var29));
               }

               for (int var32 = 0; var32 < var27; var32++) {
                  var7.add(this.plugin.items().asTemplate(var30));
               }

               var8.append(var11).append(" x").append(var12).append(var29 >= 0.0 ? " at " + var29 + "%" : " in " + var27).append("  ");
            }

            if (!var7.isEmpty() && var7.size() <= 54) {
               ItemStack[] var25 = new ItemStack[54];

               for (int var26 = 0; var26 < var7.size(); var26++) {
                  var25[var26] = (ItemStack)var7.get(var26);
               }

               String actorName = var1.getName();
               int slotCount = var7.size();
               String summary = var8.toString();
               this.plugin.async(() -> {
                  boolean saved = this.plugin.blobs().save("crateloot", var4, var25, actorName);
                  this.plugin.sync(() -> {
                     if (!saved) {
                        var1.sendMessage("save failed");
                     } else {
                        this.plugin.crates().installProbePool(var4, var25);
                        var1.sendMessage("crate " + var4 + " loot set  " + slotCount + " slots  " + summary);
                     }
                  });
               });
            } else {
               var1.sendMessage("need 1 to 54 slots  got " + var7.size());
            }
         }
      } else {
         var1.sendMessage("/crate import <crate>  reads plugins/FlameFFACore/crate_import/<crate>.yml");
      }
   }

   private void voucher(Player var1, String[] var2) {
      if (!this.plugin.staff().isOperator(var1)) {
         var1.sendMessage(this.plugin.configs().msg("general.operator-only"));
      } else if (var2.length < 2) {
         var1.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/crate voucher <crate> [amount]"));
      } else {
         Crate var3 = this.plugin.crateRegistry().get(var2[1]);
         if (var3 == null) {
            var1.sendMessage(this.plugin.configs().msg("crate.unknown", "crate", var2[1]));
         } else {
            int var4 = var2.length > 2 ? Math.max(1, Math.min(64, parse(var2[2]))) : 1;
            ItemStack var5 = this.plugin.items().mintVoucher(var3.id());
            var5.setAmount(var4);

            for (ItemStack var7 : var1.getInventory().addItem(new ItemStack[]{var5}).values()) {
               var1.getWorld().dropItemNaturally(var1.getLocation(), var7);
            }

            var1.sendMessage(this.plugin.configs().msg("voucher.given", "candles", String.valueOf(var4), "crate", var3.id()));
         }
      }
   }

   private void place(Player var1, String[] var2) {
      if (!this.plugin.staff().isOperator(var1)) {
         var1.sendMessage(this.plugin.configs().msg("general.operator-only"));
      } else if (var2.length < 2) {
         var1.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/crate place <crate>"));
      } else {
         Crate var3 = this.plugin.crateRegistry().get(var2[1]);
         if (var3 == null) {
            var1.sendMessage(this.plugin.configs().msg("crate.unknown", "crate", var2[1]));
         } else {
            Location var4 = this.targetBlock(var1);
            if (var4 == null) {
               var1.sendMessage(this.plugin.configs().msg("crate.no-target"));
            } else if (this.plugin.crateRegistry().isPlaced(var4)) {
               var1.sendMessage(this.plugin.configs().msg("crate.already-placed"));
            } else if (!this.plugin.crateRegistry().place(var3.id(), var4, var1.getName())) {
               var1.sendMessage(this.plugin.configs().msg("crate.place-failed"));
            } else {
               this.plugin.hud().refreshCrates();
               var1.sendMessage(
                  this.plugin
                     .configs()
                     .msg(
                        "crate.placed",
                        "crate",
                        var3.id(),
                        "x",
                        String.valueOf(var4.getBlockX()),
                        "y",
                        String.valueOf(var4.getBlockY()),
                        "z",
                        String.valueOf(var4.getBlockZ())
                     )
               );
               this.plugin
                  .async(
                     () -> this.plugin
                        .audit()
                        .staff(
                           var1.getUniqueId(),
                           var1.getName(),
                           "crate_place",
                           null,
                           var3.id(),
                           var4.getBlockX() + " " + var4.getBlockY() + " " + var4.getBlockZ()
                        )
                  );
            }
         }
      }
   }

   private void remove(Player var1) {
      if (!this.plugin.staff().isOperator(var1)) {
         var1.sendMessage(this.plugin.configs().msg("general.operator-only"));
      } else {
         RayTraceResult var2 = var1.rayTraceBlocks(6.0);
         Location var3 = var2 != null && var2.getHitBlock() != null ? var2.getHitBlock().getLocation() : null;
         if (var3 != null && this.plugin.crateRegistry().isPlaced(var3)) {
            Crate var4 = this.plugin.crateRegistry().atBlock(var3);
            this.plugin.crateRegistry().unplace(var3);
            this.plugin.hud().refreshCrates();
            var1.sendMessage(this.plugin.configs().msg("crate.removed", "crate", var4 == null ? "?" : var4.id()));
            this.plugin
               .async(
                  () -> this.plugin
                     .audit()
                     .staff(
                        var1.getUniqueId(),
                        var1.getName(),
                        "crate_remove",
                        null,
                        var4 == null ? "?" : var4.id(),
                        var3.getBlockX() + " " + var3.getBlockY() + " " + var3.getBlockZ()
                     )
               );
         } else {
            var1.sendMessage(this.plugin.configs().msg("crate.not-a-crate"));
         }
      }
   }

   private void listPlaced(Player var1) {
      List<CrateBlockStore.Placement> var2 = this.plugin.crateRegistry().placements();
      if (var2.isEmpty()) {
         var1.sendMessage(this.plugin.configs().msg("crate.none-placed"));
      } else {
         var1.sendMessage(this.plugin.configs().msg("crate.list-header", "count", String.valueOf(var2.size())));

         for (CrateBlockStore.Placement var4 : var2) {
            Location var5 = var4.location();
            var1.sendMessage(
               this.plugin
                  .configs()
                  .msg(
                     "crate.list-line",
                     "crate",
                     var4.crateId(),
                     "world",
                     var5.getWorld() == null ? "?" : var5.getWorld().getName(),
                     "x",
                     String.valueOf(var5.getBlockX()),
                     "y",
                     String.valueOf(var5.getBlockY()),
                     "z",
                     String.valueOf(var5.getBlockZ())
                  )
            );
         }
      }
   }

   private Location targetBlock(Player var1) {
      RayTraceResult var2 = var1.rayTraceBlocks(6.0);
      if (var2 != null && var2.getHitBlock() != null && var2.getHitBlockFace() != null) {
         Location var3 = var2.getHitBlock().getRelative(var2.getHitBlockFace()).getLocation();
         if (var3.getBlock().getType().isAir()) {
            return var3;
         }
      }

      Location var4 = var1.getLocation().getBlock().getLocation();
      return var4.getBlock().getType().isAir() ? var4 : null;
   }

   private void editor(Player var1, Crate var2) {
      if (!this.plugin.staff().isOperator(var1)) {
         var1.sendMessage(this.plugin.configs().msg("general.operator-only"));
      } else {
         this.plugin.crates().openEditor(var1, var2);
         var1.sendMessage(this.plugin.configs().msg("crate.editor-hint", "crate", var2.id()));
      }
   }

   private void showBalance(Player var1) {
      UUID var2 = var1.getUniqueId();
      this.plugin
         .async(
            () -> {
               Map var3 = this.plugin.keys().balances(var2);
               this.plugin
                  .sync(
                     () -> {
                        if (var3.isEmpty()) {
                           var1.sendMessage(this.plugin.configs().msg("keys.none"));
                        } else {
                           var1.sendMessage(this.plugin.configs().msg("keys.balance-header"));
                           var3.forEach(
                              (var2xxx, var3x) -> var1.sendMessage(
                                 this.plugin.configs().msg("keys.balance-line", "crate", String.valueOf(var2xxx), "amount", String.valueOf(var3x))
                              )
                           );
                        }
                     }
                  );
            }
         );
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack var1, @NotNull String[] var2) {
      if (var2.length <= 1) {
         ArrayList var4 = new ArrayList<>(List.of("open", "buy", "place", "remove", "list", "voucher"));
         var4.addAll(this.plugin.crateRegistry().all().keySet());
         return var4;
      } else if (var2.length != 2 || !"place".equalsIgnoreCase(var2[0]) && !"voucher".equalsIgnoreCase(var2[0])) {
         if (var2.length == 2 && this.plugin.crateRegistry().get(var2[0]) != null) {
            return List.of("set");
         } else if (var2.length == 2) {
            ArrayList<String> var3 = new ArrayList<>(this.plugin.crateRegistry().all().keySet());
            var3.removeIf(var1x -> !var1x.startsWith(var2[1].toLowerCase(Locale.ROOT)));
            return var3;
         } else {
            return List.of();
         }
      } else {
         return new ArrayList<>(this.plugin.crateRegistry().all().keySet());
      }
   }

   private static int parse(String var0) {
      try {
         return Integer.parseInt(var0);
      } catch (NumberFormatException var2) {
         return 1;
      }
   }
}
