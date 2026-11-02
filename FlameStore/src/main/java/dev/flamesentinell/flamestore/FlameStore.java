package dev.flamesentinell.flamestore;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry.Builder;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlameStore extends JavaPlugin {
   private final MiniMessage mm = MiniMessage.miniMessage();
   private Dialog dialog;
   private Component chat;
   private StoreNpc npc;
   private StoreNpc rulesNpc;
   private StoreNpc discordNpc;
   private StoreNpc mediaNpc;
   private Dialog discordDialog;
   private Component discordChat;
   private ItemStack rulesBook;
   private ItemStack mediaBook;
   private static final Pattern ALLOWED = Pattern.compile(
      "</?(?:#[0-9a-fA-F]{6}|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white|b|bold|i|italic|u|underlined|gradient(?::#[0-9a-fA-F]{6})+|reset)>"
   );

   public void onEnable() {
      this.saveDefaultConfig();
      this.npc = new StoreNpc(this, "npc", "flamestore_npc", this::open);
      this.rulesNpc = new StoreNpc(this, "rules-npc", "flamestore_rules_npc", this::rules);
      this.discordNpc = new StoreNpc(this, "discord-npc", "flamestore_discord_npc", this::discord);
      this.mediaNpc = new StoreNpc(this, "media-npc", "flamestore_media_npc", this::media);
      this.load();
      this.getServer().getPluginManager().registerEvents(this.npc, this);
      this.getServer().getPluginManager().registerEvents(this.rulesNpc, this);
      this.getServer().getPluginManager().registerEvents(this.discordNpc, this);
      this.getServer().getPluginManager().registerEvents(this.mediaNpc, this);
      this.getServer().getScheduler().runTaskTimer(this, () -> {
         this.npc.refresh();
         this.rulesNpc.refresh();
         this.discordNpc.refresh();
         this.mediaNpc.refresh();
      }, 40L, 10L);
   }

   public void onDisable() {
      if (this.npc != null) {
         this.npc.despawn();
      }

      if (this.rulesNpc != null) {
         this.rulesNpc.despawn();
      }

      if (this.discordNpc != null) {
         this.discordNpc.despawn();
      }

      if (this.mediaNpc != null) {
         this.mediaNpc.despawn();
      }
   }

   void rules(Player var1) {
      var1.openBook(this.rulesBook);
   }

   void media(Player var1) {
      var1.openBook(this.mediaBook);
   }

   private ItemStack book(String var1, String var2) {
      ItemStack var3 = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta var4 = (BookMeta)var3.getItemMeta();
      var4.setTitle(var1);
      var4.setAuthor("Flame FFA");

      for (String var6 : this.getConfig().getStringList(var2)) {
         var4.addPages(new Component[]{this.mm.deserialize(var6)});
      }

      var3.setItemMeta(var4);
      return var3;
   }

   void discord(Player var1) {
      var1.showDialog(this.discordDialog);
      var1.sendMessage(this.discordChat);
   }

   void open(Player var1) {
      var1.showDialog(this.dialog);
      var1.sendMessage(this.chat);
   }

   private void load() {
      this.reloadConfig();
      String var1 = this.getConfig().getString("url", "https://store.flameffa.net");
      Component var2 = this.text("title");
      Component var3 = this.text("body");
      ActionButton var4 = ActionButton.builder(this.text("open-button")).width(160).action(DialogAction.staticAction(ClickEvent.openUrl(var1))).build();
      ActionButton var5 = ActionButton.builder(this.text("close-button")).width(100).build();
      this.dialog = Dialog.create(
         var4x -> ((Builder)var4x.empty())
            .base(DialogBase.builder(var2).body(List.of(DialogBody.plainMessage(var3))).build())
            .type(DialogType.confirmation(var4, var5))
      );
      this.chat = this.mm.deserialize(this.getConfig().getString("chat", ""), Placeholder.parsed("url", var1));
      this.npc.load();
      this.npc.spawn();
      this.rulesBook = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta var6 = (BookMeta)this.rulesBook.getItemMeta();
      var6.setTitle("Rules");
      var6.setAuthor("Flame FFA");

      for (String var8 : this.getConfig().getStringList("rules-book")) {
         var6.addPages(new Component[]{this.mm.deserialize(var8)});
      }

      this.rulesBook.setItemMeta(var6);
      this.rulesNpc.load();
      this.rulesNpc.spawn();
      String var10 = this.getConfig().getString("discord.url", "https://discord.gg/ETpnReMVpU");
      ActionButton var11 = ActionButton.builder(this.text("discord.open-button"))
         .width(160)
         .action(DialogAction.staticAction(ClickEvent.openUrl(var10)))
         .build();
      ActionButton var9 = ActionButton.builder(this.text("discord.close-button")).width(100).build();
      this.discordDialog = Dialog.create(
         var3x -> ((Builder)var3x.empty())
            .base(DialogBase.builder(this.text("discord.title")).body(List.of(DialogBody.plainMessage(this.text("discord.body")))).build())
            .type(DialogType.confirmation(var11, var9))
      );
      this.discordChat = this.mm.deserialize(this.getConfig().getString("discord.chat", ""), Placeholder.parsed("url", var10));
      this.discordNpc.load();
      this.discordNpc.spawn();
      this.mediaBook = this.book("Media Rank", "media-book");
      this.mediaNpc.load();
      this.mediaNpc.spawn();
   }

   private boolean msg(CommandSender var1, String var2) {
      var1.sendMessage(Component.text(var2));
      return true;
   }

   private Component text(String var1) {
      return this.mm.deserialize(this.getConfig().getString(var1, ""));
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var2.getName().equalsIgnoreCase("flamestore")) {
         String var16 = var4.length > 0 ? var4[0].toLowerCase() : "reload";
         switch (var16) {
            case "npc":
               if (var1 instanceof Player var19) {
                  this.npc.place(var19.getLocation());
                  return this.msg(var1, "store npc placed");
               }

               return this.msg(var1, "players only  use npcat from the console");
            case "npcat":
               if (var4.length >= 6 && Bukkit.getWorld(var4[1]) != null) {
                  try {
                     this.npc
                        .place(
                           new Location(
                              Bukkit.getWorld(var4[1]),
                              Double.parseDouble(var4[2]),
                              Double.parseDouble(var4[3]),
                              Double.parseDouble(var4[4]),
                              Float.parseFloat(var4[5]),
                              0.0F
                           )
                        );
                  } catch (NumberFormatException var12) {
                     return this.msg(var1, "bad number");
                  }

                  return this.msg(var1, "store npc placed");
               }

               return this.msg(var1, "/flamestore npcat <world> <x> <y> <z> <yaw>");
            case "npcremove":
               this.npc.remove();
               return this.msg(var1, "store npc removed");
            case "rulesnpc":
               if (var1 instanceof Player var18) {
                  this.rulesNpc.place(var18.getLocation());
                  return this.msg(var1, "rules npc placed");
               }

               return this.msg(var1, "players only  use rulesnpcat from the console");
            case "rulesnpcat":
               if (var4.length >= 6 && Bukkit.getWorld(var4[1]) != null) {
                  try {
                     this.rulesNpc
                        .place(
                           new Location(
                              Bukkit.getWorld(var4[1]),
                              Double.parseDouble(var4[2]),
                              Double.parseDouble(var4[3]),
                              Double.parseDouble(var4[4]),
                              Float.parseFloat(var4[5]),
                              0.0F
                           )
                        );
                  } catch (NumberFormatException var11) {
                     return this.msg(var1, "bad number");
                  }

                  return this.msg(var1, "rules npc placed");
               }

               return this.msg(var1, "/flamestore rulesnpcat <world> <x> <y> <z> <yaw>");
            case "discordnpc":
               if (var1 instanceof Player var17) {
                  this.discordNpc.place(var17.getLocation());
                  return this.msg(var1, "discord npc placed");
               }

               return this.msg(var1, "players only  use discordnpcat from the console");
            case "discordnpcat":
               if (var4.length >= 6 && Bukkit.getWorld(var4[1]) != null) {
                  try {
                     this.discordNpc
                        .place(
                           new Location(
                              Bukkit.getWorld(var4[1]),
                              Double.parseDouble(var4[2]),
                              Double.parseDouble(var4[3]),
                              Double.parseDouble(var4[4]),
                              Float.parseFloat(var4[5]),
                              0.0F
                           )
                        );
                  } catch (NumberFormatException var10) {
                     return this.msg(var1, "bad number");
                  }

                  return this.msg(var1, "discord npc placed");
               }

               return this.msg(var1, "/flamestore discordnpcat <world> <x> <y> <z> <yaw>");
            case "discordnpcremove":
               this.discordNpc.remove();
               return this.msg(var1, "discord npc removed");
            case "medianpc":
               if (var1 instanceof Player var8) {
                  this.mediaNpc.place(var8.getLocation());
                  return this.msg(var1, "media npc placed");
               }

               return this.msg(var1, "players only  use medianpcat from the console");
            case "medianpcat":
               if (var4.length >= 6 && Bukkit.getWorld(var4[1]) != null) {
                  try {
                     this.mediaNpc
                        .place(
                           new Location(
                              Bukkit.getWorld(var4[1]),
                              Double.parseDouble(var4[2]),
                              Double.parseDouble(var4[3]),
                              Double.parseDouble(var4[4]),
                              Float.parseFloat(var4[5]),
                              0.0F
                           )
                        );
                  } catch (NumberFormatException var9) {
                     return this.msg(var1, "bad number");
                  }

                  return this.msg(var1, "media npc placed");
               }

               return this.msg(var1, "/flamestore medianpcat <world> <x> <y> <z> <yaw>");
            case "medianpcremove":
               this.mediaNpc.remove();
               return this.msg(var1, "media npc removed");
            case "rulesnpcremove":
               this.rulesNpc.remove();
               return this.msg(var1, "rules npc removed");
            default:
               this.load();
               return this.msg(var1, "FlameStore reloaded");
         }
      } else if (var2.getName().equalsIgnoreCase("customtag")) {
         return this.customTag(var1, var4);
      } else if (var2.getName().equalsIgnoreCase("discord")) {
         if (var1 instanceof Player var15) {
            this.discord(var15);
         } else {
            var1.sendMessage(this.discordChat);
         }

         return true;
      } else if (var2.getName().equalsIgnoreCase("media")) {
         if (var1 instanceof Player var14) {
            this.media(var14);
         }

         return true;
      } else if (var2.getName().equalsIgnoreCase("rules")) {
         if (var1 instanceof Player var13) {
            this.rules(var13);
         }

         return true;
      } else {
         if (var1 instanceof Player var5) {
            this.open(var5);
         } else {
            var1.sendMessage(this.chat);
         }

         return true;
      }
   }

   private boolean customTag(CommandSender var1, String[] var2) {
      if (var2.length < 2) {
         var1.sendMessage("/customtag <player> <tag in minimessage>  or  /customtag <player> clear");
         return true;
      } else {
         OfflinePlayer var3 = Bukkit.getOfflinePlayer(var2[0]);
         String var4 = String.join(" ", Arrays.copyOfRange(var2, 1, var2.length)).trim();
         boolean var5 = var4.equalsIgnoreCase("clear");
         if (!var5) {
            String var6 = ALLOWED.matcher(var4).replaceAll("");
            if (var6.contains("<") || var6.contains(">")) {
               var1.sendMessage("only colours gradients bold italic and underline are allowed in a tag");
               return true;
            }

            int var7 = var6.strip().length();
            int var8 = this.getConfig().getInt("custom-tag.max-length", 16);
            if (var7 == 0 || var7 > var8) {
               var1.sendMessage("a tag needs 1 to " + var8 + " visible characters  this one has " + var7);
               return true;
            }
         }

         File var10 = new File(this.getDataFolder().getParentFile(), "FlameFFACore/ranks.yml");
         YamlConfiguration var11 = YamlConfiguration.loadConfiguration(var10);
         var11.set("custom-tags." + var3.getUniqueId(), var5 ? null : var4);

         try {
            var11.save(var10);
         } catch (IOException var9) {
            var1.sendMessage("could not save the tag " + var9.getMessage());
            return true;
         }

         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "flameffacore reload");
         this.getLogger().info(var1.getName() + " set the custom tag of " + var2[0] + " to " + (var5 ? "nothing" : var4));
         var1.sendMessage(
            this.mm
               .deserialize(var5 ? "<gray>Custom tag of <white>" + var2[0] + "</white> cleared." : "<gray>Custom tag of <white>" + var2[0] + "</white> is now ")
               .append((Component)(var5 ? Component.empty() : this.mm.deserialize(var4)))
         );
         return true;
      }
   }
}
