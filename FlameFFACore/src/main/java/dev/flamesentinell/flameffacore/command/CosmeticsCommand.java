package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class CosmeticsCommand implements BasicCommand {
   private final FlameFFACore plugin;

   public CosmeticsCommand(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (source.getSender() instanceof Player player) {
         if (!this.plugin.cosmetics().enabled()) {
            player.sendMessage(this.plugin.configs().msg("cosmetics.disabled"));
         } else {
            this.plugin.cosmeticMenu().open(player);
         }
      } else {
         source.getSender().sendMessage(this.plugin.configs().msg("general.players-only"));
      }
   }
}
