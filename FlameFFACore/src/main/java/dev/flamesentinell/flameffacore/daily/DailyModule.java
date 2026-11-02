package dev.flamesentinell.flameffacore.daily;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.module.Module;
import dev.flamesentinell.flameffacore.module.ModuleCommand;
import dev.flamesentinell.flameffacore.module.ModuleConfig;
import io.papermc.paper.command.brigadier.Commands;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class DailyModule implements Module {
   private static final long HOUR_MS = 3600000L;
   private final FlameFFACore plugin;
   private final ModuleConfig config;
   private final File claimsFile;
   private final Map<UUID, Long> claims = new ConcurrentHashMap<>();

   public DailyModule(FlameFFACore plugin) {
      this.plugin = plugin;
      this.config = new ModuleConfig(plugin, "daily.yml", "FlameDaily", "config.yml");
      this.claimsFile = new File(plugin.getDataFolder(), "daily-claims.yml");
   }

   @Override
   public String id() {
      return "daily";
   }

   @Override
   public void enable() {
      this.config.load();
      ModuleConfig.migrate(this.plugin, ModuleConfig.legacyFile(this.plugin, "FlameDaily", "claims.yml"), this.claimsFile);
      this.claims.clear();
      YamlConfiguration stored = YamlConfiguration.loadConfiguration(this.claimsFile);

      for (String key : stored.getKeys(false)) {
         try {
            this.claims.put(UUID.fromString(key), stored.getLong(key, 0L));
         } catch (IllegalArgumentException malformed) {
            this.plugin.getSLF4JLogger().warn("skipping bad daily claim entry {}", key);
         }
      }
   }

   @Override
   public void disable() {
      this.save();
      this.claims.clear();
   }

   @Override
   public void commands(Commands commands) {
      ModuleCommand.register(commands, "daily", "claim your daily kill tokens", List.of(), "flamedaily.use", null, (sender, label, args) -> this.daily(sender), null);
      ModuleCommand.register(
         commands,
         "flamedaily",
         "reload or reset a daily",
         List.of(),
         "flamedaily.admin",
         "/flamedaily reload|reset <player>",
         (sender, label, args) -> this.admin(sender, args),
         (sender, args) -> args.length <= 1 ? List.of("reload", "reset") : null
      );
   }

   private FileConfiguration cfg() {
      return this.config.get();
   }

   private boolean daily(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("players only");
         return true;
      } else {
         UUID uuid = player.getUniqueId();
         long now = System.currentTimeMillis();
         long cooldown = Math.max(0L, this.cfg().getLong("cooldown-hours", 24L)) * HOUR_MS;
         long next = this.claims.getOrDefault(uuid, 0L) + cooldown;
         if (now < next) {
            player.sendMessage(this.mm("wait", "time", left(next - now)));
            return true;
         } else if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(this.mm("full", "time", ""));
            return true;
         } else {
            long stamped = this.claims.compute(uuid, (key, last) -> last != null && now < last + cooldown ? last : now);
            if (stamped != now) {
               player.sendMessage(this.mm("wait", "time", left(stamped + cooldown - now)));
               return true;
            } else {
               this.save();
               int min = Math.max(1, this.cfg().getInt("min-tokens", 4));
               int max = Math.max(min, this.cfg().getInt("max-tokens", 16));
               int amount = roll(min, max, this.cfg().getDouble("average", 7.6));
               this.give(player, amount);
               player.sendMessage(this.mm("claimed", "amount", String.valueOf(amount)));
               String crate = this.cfg().getString("bonus-key.crate", "mythic");
               if (player.hasPermission("flamedaily.bonus") && crate != null && !crate.isBlank()) {
                  int keys = Math.max(1, this.cfg().getInt("bonus-key.amount", 1));
                  this.grantBonus(player, crate, keys);
                  player.sendMessage(this.mm("bonus", "amount", String.valueOf(keys)));
               }

               this.plugin.getSLF4JLogger().info("{} claimed {} kill tokens", player.getName(), amount);
               return true;
            }
         }
      }
   }

   private void grantBonus(Player player, String crate, int keys) {
      CommandSender console = Bukkit.getConsoleSender();
      UUID target = player.getUniqueId();
      String name = player.getName();
      this.plugin.async(() -> this.plugin.audit().staff(null, console.getName(), "grant_key", target, name, crate + " x" + keys + " txn=null"));
      this.plugin.tebex().grantKeys(console, name, crate, keys, null);
   }

   private void give(Player player, int amount) {
      ItemStack tokens = this.plugin.items().mintKillTokens(amount);

      for (ItemStack leftover : player.getInventory().addItem(tokens).values()) {
         player.getWorld().dropItemNaturally(player.getLocation(), leftover);
      }
   }

   private static int roll(int min, int max, double average) {
      int span = max - min + 1;
      if (span <= 1) {
         return min;
      } else {
         double target = Math.max(min + 0.01, Math.min(max - 0.01, average)) - min;
         double low = 0.01;
         double high = 100.0;

         for (int step = 0; step < 80; step++) {
            double ratio = Math.sqrt(low * high);
            if (mean(ratio, span) < target) {
               low = ratio;
            } else {
               high = ratio;
            }
         }

         double ratio = Math.sqrt(low * high);
         double total = 0.0;
         double[] weights = new double[span];

         for (int index = 0; index < span; index++) {
            weights[index] = Math.pow(ratio, index);
            total += weights[index];
         }

         double pick = ThreadLocalRandom.current().nextDouble(total);

         for (int index = 0; index < span; index++) {
            pick -= weights[index];
            if (pick < 0.0) {
               return min + index;
            }
         }

         return max;
      }
   }

   private static double mean(double ratio, int span) {
      double weightSum = 0.0;
      double valueSum = 0.0;

      for (int index = 0; index < span; index++) {
         double weight = Math.pow(ratio, index);
         weightSum += weight;
         valueSum += index * weight;
      }

      return valueSum / weightSum;
   }

   private boolean admin(CommandSender sender, String[] args) {
      if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
         this.config.load();
         sender.sendMessage("FlameDaily reloaded");
      } else if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
         String name = args[1];
         OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
         if (cached != null) {
            this.reset(sender, name, cached.getUniqueId());
         } else {
            this.plugin.async(() -> {
               UUID uuid = Bukkit.getOfflinePlayer(name).getUniqueId();
               this.plugin.sync(() -> this.reset(sender, name, uuid));
            });
         }
      } else {
         sender.sendMessage("/flamedaily reload|reset <player>");
      }

      return true;
   }

   private void reset(CommandSender sender, String name, UUID uuid) {
      this.claims.remove(uuid);
      this.save();
      sender.sendMessage("daily reset for " + name);
   }

   private Component mm(String key, String placeholder, String value) {
      return MiniMessage.miniMessage().deserialize(this.cfg().getString(key, ""), Placeholder.unparsed(placeholder, value));
   }

   private static String left(long millis) {
      long minutes = (millis + 59999L) / 60000L;
      return minutes >= 60L ? minutes / 60L + "h " + minutes % 60L + "m" : minutes + "m";
   }

   private void save() {
      YamlConfiguration out = new YamlConfiguration();
      this.claims.forEach((uuid, at) -> out.set(uuid.toString(), at));
      this.plugin.configs().saveAsync(out, this.claimsFile);
   }
}
