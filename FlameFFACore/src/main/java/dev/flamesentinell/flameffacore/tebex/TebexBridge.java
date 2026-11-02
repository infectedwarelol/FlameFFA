package dev.flamesentinell.flameffacore.tebex;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.storage.KeyStore;
import dev.flamesentinell.flameffacore.storage.PendingStore;
import dev.flamesentinell.flameffacore.util.Bots;
import dev.flamesentinell.flameffacore.util.Text;
import dev.flamesentinell.flameffacore.util.Uuids;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class TebexBridge implements Listener {
   private final FlameFFACore plugin;

   public TebexBridge(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void grantKeys(CommandSender actor, String targetToken, String crateId, int amount, String txn) {
      UUID target = this.resolve(targetToken);
      if (target == null) {
         actor.sendMessage(this.plugin.configs().msg("general.unknown-player", "name", targetToken));
      } else if (this.plugin.crateRegistry().get(crateId) == null) {
         actor.sendMessage(this.plugin.configs().msg("crate.unknown", "crate", crateId));
      } else {
         this.plugin.async(() -> {
            KeyStore.Result result = this.plugin.keys().grant(target, crateId, amount, "tebex", actor.getName(), txn);
            this.plugin.sync(() -> this.report(actor, target, result, amount + "x " + crateId + " key", txn));
         });
      }
   }

   public void grantRank(CommandSender actor, String targetToken, String rankId, int days, String txn) {
      UUID target = this.resolve(targetToken);
      if (target == null) {
         actor.sendMessage(this.plugin.configs().msg("general.unknown-player", "name", targetToken));
      } else if (this.plugin.ranks().rank(rankId) == null) {
         actor.sendMessage(this.plugin.configs().msg("general.usage", "usage", "unknown rank " + rankId));
      } else {
         this.plugin
            .async(
               () -> {
                  boolean owned = this.plugin.txns().claim(txn, "rank", target, rankId + " " + days + "d");
                  if (!owned) {
                     this.plugin.sync(() -> actor.sendMessage(this.plugin.configs().msg("tebex.duplicate-txn", "txn", String.valueOf(txn))));
                  } else {
                     this.plugin
                        .ranks()
                        .grant(target, rankId, days)
                        .thenRun(
                           () -> this.plugin
                              .sync(
                                 () -> {
                                    this.notifyOrQueue(
                                       target,
                                       days > 0
                                          ? this.plugin.configs().messages().getString("rank.granted-temp", "")
                                          : this.plugin.configs().messages().getString("rank.granted", ""),
                                       rankId,
                                       days
                                    );
                                    actor.sendMessage(this.plugin.configs().msg("tebex.delivered", "what", rankId + (days > 0 ? " " + days + "d" : "")));
                                    Player online = Bukkit.getPlayer(target);
                                    if (online != null) {
                                       Bukkit.broadcast(this.plugin.configs().msg("rank.broadcast", "player", online.getName(), "rank", rankId));
                                    }
                                 }
                              )
                        );
                  }
               }
            );
      }
   }

   private void report(CommandSender actor, UUID target, KeyStore.Result result, String what, String txn) {
      switch (result) {
         case OK:
            actor.sendMessage(this.plugin.configs().msg("tebex.delivered", "what", what));
            Player online = Bukkit.getPlayer(target);
            if (online != null) {
               online.sendMessage(this.plugin.configs().msg("keys.granted", "amount", what.split("x")[0].trim(), "crate", what));
            }
            break;
         case DUPLICATE_TXN:
            actor.sendMessage(this.plugin.configs().msg("tebex.duplicate-txn", "txn", String.valueOf(txn)));
            break;
         default:
            actor.sendMessage(this.plugin.configs().msg("general.usage", "usage", "grant failed, check the console"));
      }
   }

   public void notifyOrQueue(UUID target, String rawMessage, String rankId, int days) {
      Player online = Bukkit.getPlayer(target);
      if (online != null) {
         online.sendMessage(Text.parse(rawMessage, "rank", rankId, "days", String.valueOf(days)));
      } else {
         if (this.plugin.configs().main().getBoolean("tebex.queue-offline", true)) {
            this.plugin.async(() -> this.plugin.pending().queue(target, "notice", "rank " + rankId + (days > 0 ? " for " + days + " days" : "")));
         }
      }
   }

   public UUID resolve(String token) {
      if (token != null && !token.isBlank()) {
         UUID direct = Uuids.parseOrNull(token);
         if (direct != null) {
            return direct;
         } else {
            if (token.length() == 32) {
               String dashed = token.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5");
               UUID parsed = Uuids.parseOrNull(dashed);
               if (parsed != null) {
                  return parsed;
               }
            }

            Player online = Bukkit.getPlayerExact(token);
            if (online != null) {
               return online.getUniqueId();
            } else {
               OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(token);
               return offline != null ? offline.getUniqueId() : this.plugin.players().uuidByName(token);
            }
         }
      } else {
         return null;
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (!Bots.is(player)) {
         this.plugin.async(() -> {
            for (PendingStore.Pending pending : this.plugin.pending().open(player.getUniqueId())) {
               if (this.plugin.pending().markDelivered(pending.id())) {
                  this.plugin.sync(() -> player.sendMessage(this.plugin.configs().msg("tebex.delivered", "what", pending.payload())));
               }
            }
         });
      }
   }
}
