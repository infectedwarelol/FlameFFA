package dev.flamesentinell.flameffacore.chat;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.staff.StaffRole;
import dev.flamesentinell.flameffacore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatGuard implements Listener {
   private final FlameFFACore plugin;
   private final ChatFilter filter = new ChatFilter();
   private final Map<UUID, ChatGuard.Strikes> strikes = new ConcurrentHashMap<>();

   public ChatGuard(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void load() {
      FileConfiguration cfg = this.plugin.configs().chat();
      this.filter.load(cfg.getStringList("banned"), cfg.getStringList("allowed"));
      this.plugin.getSLF4JLogger().info("chat guard watching for {} terms", this.filter.size());
   }

   public ChatFilter filter() {
      return this.filter;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onFormat(AsyncChatEvent event) {
      String format = this.plugin.configs().messages().getString("chat.format", "<rank> <white><player><dark_gray>: <gray><message>");
      int split = format.indexOf("<message>");
      String head = split < 0 ? format : format.substring(0, split);
      String tail = split < 0 ? "" : format.substring(split + "<message>".length());
      event.renderer((source, name, message, viewer) -> {
         Component out = Text.parse(head, "rank", this.plugin.hud().rankTag(source), "player", source.getName()).append(message);
         return tail.isEmpty() ? out : out.append(Text.parse(tail));
      });
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent event) {
      FileConfiguration cfg = this.plugin.configs().chat();
      if (cfg.getBoolean("enabled", true)) {
         Player player = event.getPlayer();
         String hit = this.filter.scan(Text.plain(event.message()));
         if (hit != null) {
            if (cfg.getBoolean("cancel-message", true)) {
               event.setCancelled(true);
            }

            player.sendMessage(this.plugin.configs().msg("chat.blocked"));
            String said = Text.plain(event.message());
            this.plugin.sync(() -> this.punish(player, hit, said));
         }
      }
   }

   private void punish(Player player, String hit, String said) {
      FileConfiguration cfg = this.plugin.configs().chat();
      UUID uuid = player.getUniqueId();
      long decay = Math.max(0L, cfg.getLong("strike-decay-minutes", 60L)) * 60000L;
      long now = System.currentTimeMillis();
      ChatGuard.Strikes s = this.strikes.computeIfAbsent(uuid, k -> new ChatGuard.Strikes());
      if (decay > 0L && s.last > 0L && now - s.last > decay) {
         s.count = 0;
      }

      s.count++;
      s.last = now;
      this.plugin.async(() -> this.plugin.audit().staff(uuid, player.getName(), "chat_slur", uuid, player.getName(), hit + " strike " + s.count));
      if (cfg.getBoolean("notify-staff", true)) {
         for (Player online : Bukkit.getOnlinePlayers()) {
            if (this.plugin.staff().role(online) != StaffRole.NONE) {
               online.sendMessage(
                  this.plugin.configs().msg("chat.staff-alert", "player", player.getName(), "strikes", String.valueOf(s.count), "message", said)
               );
            }
         }
      }

      int limit = cfg.getInt("strike-limit", 3);
      String command = cfg.getString("punish-command", "");
      if (limit > 0 && s.count >= limit && command != null && !command.isBlank()) {
         this.strikes.remove(uuid);
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", player.getName()));
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      ChatGuard.Strikes s = this.strikes.get(event.getPlayer().getUniqueId());
      if (s == null || s.count <= 0) {
         this.strikes.remove(event.getPlayer().getUniqueId());
      }
   }

   private static final class Strikes {
      int count;
      long last;
   }
}
