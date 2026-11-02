package dev.flamesentinell.daily;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlameDaily extends JavaPlugin {
   private File dataFile;
   private YamlConfiguration data;

   public void onEnable() {
      this.saveDefaultConfig();
      this.dataFile = new File(this.getDataFolder(), "claims.yml");
      this.data = YamlConfiguration.loadConfiguration(this.dataFile);
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var2.getName().equalsIgnoreCase("flamedaily")) {
         return this.admin(var1, var4);
      } else if (var1 instanceof Player var5) {
         long var6 = System.currentTimeMillis();
         long var8 = this.data.getLong(var5.getUniqueId().toString(), 0L) + this.getConfig().getLong("cooldown-hours", 24L) * 3600000L;
         if (var6 < var8) {
            var5.sendMessage(this.mm("wait", "time", left(var8 - var6)));
            return true;
         } else if (var5.getInventory().firstEmpty() < 0) {
            var5.sendMessage(this.mm("full", "time", ""));
            return true;
         } else {
            int var10 = this.getConfig().getInt("min-tokens", 4);
            int var11 = Math.max(var10, this.getConfig().getInt("max-tokens", 16));
            int var12 = roll(var10, var11, this.getConfig().getDouble("average", 7.6));
            this.data.set(var5.getUniqueId().toString(), var6);
            this.save();
            this.give(var5, var12);
            var5.sendMessage(this.mm("claimed", "amount", String.valueOf(var12)));
            String var13 = this.getConfig().getString("bonus-key.crate", "mythic");
            if (var5.hasPermission("flamedaily.bonus") && var13 != null && !var13.isBlank()) {
               int var14 = Math.max(1, this.getConfig().getInt("bonus-key.amount", 1));
               Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ffaadmin grant key " + var5.getName() + " " + var13 + " " + var14);
               var5.sendMessage(this.mm("bonus", "amount", String.valueOf(var14)));
            }

            this.getLogger().info(var5.getName() + " claimed " + var12 + " kill tokens");
            return true;
         }
      } else {
         var1.sendMessage("players only");
         return true;
      }
   }

   private void give(Player var1, int var2) {
      try {
         Plugin var3 = Bukkit.getPluginManager().getPlugin("FlameFFACore");
         Object var4 = var3.getClass().getMethod("items").invoke(var3);
         ItemStack var5 = (ItemStack)var4.getClass().getMethod("mintKillTokens", int.class).invoke(var4, var2);

         for (ItemStack var7 : var1.getInventory().addItem(new ItemStack[]{var5}).values()) {
            var1.getWorld().dropItemNaturally(var1.getLocation(), var7);
         }
      } catch (RuntimeException | ReflectiveOperationException var8) {
         this.getLogger().warning("direct mint failed  using the command " + var8);
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ffaadmin mint token " + var2 + " " + var1.getName());
      }
   }

   private static int roll(int var0, int var1, double var2) {
      int var4 = var1 - var0 + 1;
      if (var4 == 1) {
         return var0;
      } else {
         double var5 = Math.max(var0 + 0.01, Math.min(var1 - 0.01, var2)) - var0;
         double var7 = 0.01;
         double var9 = 100.0;

         for (int var11 = 0; var11 < 80; var11++) {
            double var12 = Math.sqrt(var7 * var9);
            if (mean(var12, var4) < var5) {
               var7 = var12;
            } else {
               var9 = var12;
            }
         }

         double var19 = Math.sqrt(var7 * var9);
         double var13 = 0.0;
         double[] var15 = new double[var4];

         for (int var16 = 0; var16 < var4; var16++) {
            var13 += var15[var16] = Math.pow(var19, var16);
         }

         double var20 = ThreadLocalRandom.current().nextDouble(var13);

         for (int var18 = 0; var18 < var4; var18++) {
            if ((var20 -= var15[var18]) < 0.0) {
               return var0 + var18;
            }
         }

         return var1;
      }
   }

   private static double mean(double var0, int var2) {
      double var3 = 0.0;
      double var5 = 0.0;

      for (int var7 = 0; var7 < var2; var7++) {
         double var8 = Math.pow(var0, var7);
         var3 += var8;
         var5 += var7 * var8;
      }

      return var5 / var3;
   }

   private boolean admin(CommandSender var1, String[] var2) {
      if (var2.length >= 1 && var2[0].equalsIgnoreCase("reload")) {
         this.reloadConfig();
         var1.sendMessage("FlameDaily reloaded");
      } else if (var2.length >= 2 && var2[0].equalsIgnoreCase("reset")) {
         OfflinePlayer var3 = Bukkit.getOfflinePlayer(var2[1]);
         this.data.set(var3.getUniqueId().toString(), null);
         this.save();
         var1.sendMessage("daily reset for " + var2[1]);
      } else {
         var1.sendMessage("/flamedaily reload|reset <player>");
      }

      return true;
   }

   private Component mm(String var1, String var2, String var3) {
      String var4 = this.getConfig().getString(var1, "");
      return MiniMessage.miniMessage().deserialize(var4, Placeholder.unparsed(var2, var3));
   }

   private static String left(long var0) {
      long var2 = (var0 + 59999L) / 60000L;
      return var2 >= 60L ? var2 / 60L + "h " + var2 % 60L + "m" : var2 + "m";
   }

   private void save() {
      try {
         this.data.save(this.dataFile);
      } catch (IOException var2) {
         this.getLogger().warning("could not save claims " + var2.getMessage());
      }
   }
}
