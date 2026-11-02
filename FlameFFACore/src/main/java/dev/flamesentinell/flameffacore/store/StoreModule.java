package dev.flamesentinell.flameffacore.store;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.module.Module;
import dev.flamesentinell.flameffacore.module.ModuleCommand;
import dev.flamesentinell.flameffacore.module.ModuleConfig;
import dev.flamesentinell.flameffacore.util.Tasks;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public final class StoreModule implements Module {
   private static final Pattern ALLOWED = Pattern.compile(
      "</?(?:#[0-9a-fA-F]{6}|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white|b|bold|i|italic|u|underlined|gradient(?::#[0-9a-fA-F]{6})+|reset)>"
   );
   private final FlameFFACore plugin;
   private final ModuleConfig config;
   private final MiniMessage mm = MiniMessage.miniMessage();
   private final StoreNpc npc;
   private final StoreNpc rulesNpc;
   private final StoreNpc discordNpc;
   private final StoreNpc mediaNpc;
   private volatile Dialog dialog;
   private volatile Component chat = Component.empty();
   private volatile Dialog discordDialog;
   private volatile Component discordChat = Component.empty();
   private volatile ItemStack rulesBook;
   private volatile ItemStack mediaBook;
   private ScheduledTask refreshTask;

   public StoreModule(FlameFFACore plugin) {
      this.plugin = plugin;
      this.config = new ModuleConfig(plugin, "store.yml", "FlameStore", "config.yml");
      this.npc = new StoreNpc(this, "npc", "flamestore_npc", this::open);
      this.rulesNpc = new StoreNpc(this, "rules-npc", "flamestore_rules_npc", this::rules);
      this.discordNpc = new StoreNpc(this, "discord-npc", "flamestore_discord_npc", this::discord);
      this.mediaNpc = new StoreNpc(this, "media-npc", "flamestore_media_npc", this::media);
   }

   @Override
   public String id() {
      return "store";
   }

   @Override
   public void enable() {
      this.load();

      for (StoreNpc each : this.npcs()) {
         Bukkit.getPluginManager().registerEvents(each, this.plugin);
      }

      this.refreshTask = this.plugin.tasks().globalTimer(task -> {
         for (StoreNpc each : this.npcs()) {
            each.refresh();
         }
      }, 40L, 10L);
   }

   @Override
   public void disable() {
      Tasks.cancel(this.refreshTask);
      this.refreshTask = null;

      for (StoreNpc each : this.npcs()) {
         HandlerList.unregisterAll(each);
         try {
            each.despawnNow();
         } catch (RuntimeException offThread) {
            this.plugin.getSLF4JLogger().warn("store npc could not be removed on shutdown");
         }
      }
   }

   @Override
   public void commands(Commands commands) {
      ModuleCommand.register(commands, "store", "open the store", List.of("shop"), null, null, (sender, label, args) -> {
         if (sender instanceof Player player) {
            this.open(player);
         } else {
            sender.sendMessage(this.chat);
         }

         return true;
      }, null);
      ModuleCommand.register(commands, "discord", "join the flame ffa discord", List.of(), null, null, (sender, label, args) -> {
         if (sender instanceof Player player) {
            this.discord(player);
         } else {
            sender.sendMessage(this.discordChat);
         }

         return true;
      }, null);
      ModuleCommand.register(commands, "rules", "read the server rules", List.of(), null, null, (sender, label, args) -> {
         if (sender instanceof Player player) {
            this.rules(player);
         }

         return true;
      }, null);
      ModuleCommand.register(commands, "media", "how to get the media rank", List.of(), null, null, (sender, label, args) -> {
         if (sender instanceof Player player) {
            this.media(player);
         }

         return true;
      }, null);
      ModuleCommand.register(
         commands,
         "flamestore",
         "reload the store popup  npc npcat npcremove  rulesnpc rulesnpcat rulesnpcremove  discordnpc discordnpcat discordnpcremove  medianpc medianpcat medianpcremove place the npcs",
         List.of(),
         "flamestore.admin",
         null,
         (sender, label, args) -> this.admin(sender, args),
         null
      );
      ModuleCommand.register(
         commands,
         "customtag",
         "set the tag of a custom rank player",
         List.of(),
         "flamestore.customtag",
         "/customtag <player> <tag>|clear",
         (sender, label, args) -> this.customTag(sender, args),
         null
      );
   }

   FlameFFACore plugin() {
      return this.plugin;
   }

   FileConfiguration cfg() {
      return this.config.get();
   }

   void saveConfig() {
      this.config.saveAsync();
   }

   private List<StoreNpc> npcs() {
      return List.of(this.npc, this.rulesNpc, this.discordNpc, this.mediaNpc);
   }

   void rules(Player player) {
      player.openBook(this.rulesBook);
   }

   void media(Player player) {
      player.openBook(this.mediaBook);
   }

   void discord(Player player) {
      player.showDialog(this.discordDialog);
      player.sendMessage(this.discordChat);
   }

   void open(Player player) {
      player.showDialog(this.dialog);
      player.sendMessage(this.chat);
   }

   private ItemStack book(String title, String path) {
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta)book.getItemMeta();
      meta.setTitle(title);
      meta.setAuthor("Flame FFA");

      for (String page : this.cfg().getStringList(path)) {
         meta.addPages(this.mm.deserialize(page));
      }

      book.setItemMeta(meta);
      return book;
   }

   private Dialog popup(String titlePath, String bodyPath, String openPath, String closePath, String url) {
      ActionButton open = ActionButton.builder(this.text(openPath)).width(160).action(DialogAction.staticAction(ClickEvent.openUrl(url))).build();
      ActionButton close = ActionButton.builder(this.text(closePath)).width(100).build();
      Component title = this.text(titlePath);
      Component body = this.text(bodyPath);
      return Dialog.create(
         builder -> builder.empty().base(DialogBase.builder(title).body(List.of(DialogBody.plainMessage(body))).build()).type(DialogType.confirmation(open, close))
      );
   }

   private void load() {
      this.config.load();
      String url = this.cfg().getString("url", "https://store.flameffa.net");
      this.dialog = this.popup("title", "body", "open-button", "close-button", url);
      this.chat = this.mm.deserialize(this.cfg().getString("chat", ""), Placeholder.parsed("url", url));
      this.npc.load();
      this.npc.spawn();
      this.rulesBook = this.book("Rules", "rules-book");
      this.rulesNpc.load();
      this.rulesNpc.spawn();
      String discordUrl = this.cfg().getString("discord.url", "https://discord.gg/ETpnReMVpU");
      this.discordDialog = this.popup("discord.title", "discord.body", "discord.open-button", "discord.close-button", discordUrl);
      this.discordChat = this.mm.deserialize(this.cfg().getString("discord.chat", ""), Placeholder.parsed("url", discordUrl));
      this.discordNpc.load();
      this.discordNpc.spawn();
      this.mediaBook = this.book("Media Rank", "media-book");
      this.mediaNpc.load();
      this.mediaNpc.spawn();
   }

   private Component text(String path) {
      return this.mm.deserialize(this.cfg().getString(path, ""));
   }

   private static boolean msg(CommandSender sender, String text) {
      sender.sendMessage(Component.text(text));
      return true;
   }

   private boolean admin(CommandSender sender, String[] args) {
      String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "reload";
      return switch (sub) {
         case "npc" -> this.placeHere(sender, this.npc, "store", "npcat");
         case "npcat" -> this.placeAt(sender, this.npc, "store", "npcat", args);
         case "npcremove" -> this.removeNpc(sender, this.npc, "store");
         case "rulesnpc" -> this.placeHere(sender, this.rulesNpc, "rules", "rulesnpcat");
         case "rulesnpcat" -> this.placeAt(sender, this.rulesNpc, "rules", "rulesnpcat", args);
         case "rulesnpcremove" -> this.removeNpc(sender, this.rulesNpc, "rules");
         case "discordnpc" -> this.placeHere(sender, this.discordNpc, "discord", "discordnpcat");
         case "discordnpcat" -> this.placeAt(sender, this.discordNpc, "discord", "discordnpcat", args);
         case "discordnpcremove" -> this.removeNpc(sender, this.discordNpc, "discord");
         case "medianpc" -> this.placeHere(sender, this.mediaNpc, "media", "medianpcat");
         case "medianpcat" -> this.placeAt(sender, this.mediaNpc, "media", "medianpcat", args);
         case "medianpcremove" -> this.removeNpc(sender, this.mediaNpc, "media");
         default -> {
            this.load();
            yield msg(sender, "FlameStore reloaded");
         }
      };
   }

   private boolean placeHere(CommandSender sender, StoreNpc target, String kind, String fallback) {
      if (sender instanceof Player player) {
         target.place(player.getLocation());
         return msg(sender, kind + " npc placed");
      } else {
         return msg(sender, "players only  use " + fallback + " from the console");
      }
   }

   private boolean placeAt(CommandSender sender, StoreNpc target, String kind, String sub, String[] args) {
      World world = args.length >= 6 ? Bukkit.getWorld(args[1]) : null;
      if (world == null) {
         return msg(sender, "/flamestore " + sub + " <world> <x> <y> <z> <yaw>");
      } else {
         try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);
            float yaw = Float.parseFloat(args[5]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
               return msg(sender, "bad number");
            }

            target.place(new Location(world, x, y, z, yaw, 0.0F));
         } catch (NumberFormatException malformed) {
            return msg(sender, "bad number");
         }

         return msg(sender, kind + " npc placed");
      }
   }

   private boolean removeNpc(CommandSender sender, StoreNpc target, String kind) {
      target.remove();
      return msg(sender, kind + " npc removed");
   }

   private boolean customTag(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage("/customtag <player> <tag in minimessage>  or  /customtag <player> clear");
         return true;
      } else {
         String name = args[0];
         String tag = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
         boolean clear = tag.equalsIgnoreCase("clear");
         if (!clear) {
            String visible = ALLOWED.matcher(tag).replaceAll("");
            if (visible.contains("<") || visible.contains(">")) {
               sender.sendMessage("only colours gradients bold italic and underline are allowed in a tag");
               return true;
            }

            int length = visible.strip().length();
            int max = this.cfg().getInt("custom-tag.max-length", 16);
            if (length == 0 || length > max) {
               sender.sendMessage("a tag needs 1 to " + max + " visible characters  this one has " + length);
               return true;
            }
         }

         OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
         if (cached != null) {
            this.applyTag(sender, name, cached.getUniqueId(), clear ? null : tag);
         } else {
            this.plugin.async(() -> {
               UUID uuid = Bukkit.getOfflinePlayer(name).getUniqueId();
               this.plugin.sync(() -> this.applyTag(sender, name, uuid, clear ? null : tag));
            });
         }

         return true;
      }
   }

   private void applyTag(CommandSender sender, String name, UUID uuid, String tag) {
      this.plugin.configs().ranks().set("custom-tags." + uuid, tag);
      this.plugin.configs().saveAsync("ranks.yml");
      this.plugin.hud().refreshTabList();
      this.plugin.getSLF4JLogger().info("{} set the custom tag of {} to {}", sender.getName(), name, tag == null ? "nothing" : tag);
      sender.sendMessage(
         this.mm
            .deserialize(
               tag == null ? "<gray>Custom tag of <white><player></white> cleared." : "<gray>Custom tag of <white><player></white> is now ",
               Placeholder.unparsed("player", name)
            )
            .append(tag == null ? Component.empty() : this.mm.deserialize(tag))
      );
   }
}
