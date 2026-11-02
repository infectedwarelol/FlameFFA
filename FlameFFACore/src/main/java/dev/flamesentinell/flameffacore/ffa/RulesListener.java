package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Bots;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

public final class RulesListener implements Listener {
   private final FlameFFACore plugin;

   public RulesListener(FlameFFACore var1) {
      this.plugin = var1;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent var1) {
      ItemStack var2 = var1.getItemDrop().getItemStack();
      boolean var3 = this.plugin.items().isKitItem(var2) && this.plugin.arena().inSafezone(var1.getPlayer().getLocation());
      if (var3 || !this.plugin.items().isManuallyDroppable(var2)) {
         var1.setCancelled(true);
         var1.getPlayer().sendMessage(this.plugin.configs().msg("loot.no-drop"));
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent var1) {
      if (!this.plugin.staff().isRoot(var1.getPlayer())) {
         String var2 = var1.getMessage().toLowerCase(Locale.ROOT).split(" ")[0];
         if (Bots.sparring(var1.getPlayer())) {
            for (String var4 : this.plugin.configs().main().getStringList("bots.spar-blocked-commands")) {
               if (var2.equals(var4.toLowerCase(Locale.ROOT))) {
                  var1.setCancelled(true);
                  var1.getPlayer().sendMessage(this.plugin.configs().msg("bots.in-fight"));
                  return;
               }
            }
         }

         for (String var10 : this.plugin.configs().main().getStringList("security.owner-only-commands")) {
            if (var2.equals(var10.toLowerCase(Locale.ROOT))) {
               var1.setCancelled(true);
               var1.getPlayer().sendMessage(this.plugin.configs().msg("general.owner-only"));
               String var5 = var1.getPlayer().getName();
               UUID var6 = var1.getPlayer().getUniqueId();
               String var7 = var1.getMessage();
               this.plugin
                  .async(() -> this.plugin.audit().staff(var6, var5, "owner_command_denied", null, null, var7.length() > 255 ? var7.substring(0, 255) : var7));
               return;
            }
         }

         if (!var1.getPlayer().hasPermission("flameffacore.staff.bypass")) {
            for (String var11 : this.plugin.configs().main().getStringList("security.blocked-commands")) {
               if (var2.equals(var11.toLowerCase(Locale.ROOT))) {
                  var1.setCancelled(true);
                  var1.getPlayer().sendMessage(this.plugin.configs().msg("general.no-permission"));
                  return;
               }
            }
         }
      }
   }
}
