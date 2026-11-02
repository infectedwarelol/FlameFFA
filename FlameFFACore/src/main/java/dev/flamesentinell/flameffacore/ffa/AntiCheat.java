package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AntiCheat implements Listener {
   private final FlameFFACore plugin;
   private final Map<UUID, Integer> open = new HashMap<>();
   private final List<String> permissions = new ArrayList<>();
   private boolean enabled = true;
   private int grace = 20;
   private LuckPerms luckPerms;

   public AntiCheat(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void load() {
      FileConfiguration cfg = this.plugin.configs().main();
      this.enabled = cfg.getBoolean("anticheat.enabled", true);
      this.grace = Math.max(0, cfg.getInt("anticheat.grace-ticks", 20));
      this.permissions.clear();
      List<String> configured = cfg.getStringList("anticheat.permissions");
      this.permissions.addAll(configured.isEmpty() ? List.of("grim.exempt") : configured);

      try {
         this.luckPerms = LuckPermsProvider.get();
      } catch (IllegalStateException var4) {
         this.luckPerms = null;
         this.plugin.getSLF4JLogger().warn("luckperms is not ready, ability exemptions are off");
      }

      if (this.enabled) {
         this.plugin.getSLF4JLogger().info("ability exemptions on, granting {} while one runs", this.permissions);
      }
   }

   public void open(Player player, int ticks) {
      if (this.enabled && this.luckPerms != null && player != null) {
         UUID id = player.getUniqueId();
         int until = Bukkit.getCurrentTick() + Math.max(1, ticks) + this.grace;
         Integer standing = this.open.get(id);
         if (standing != null) {
            this.open.put(id, Math.max(standing, until));
         } else {
            this.open.put(id, until);
            this.grant(player, true);
         }
      }
   }

   public void close(Player player) {
      if (player != null && this.open.remove(player.getUniqueId()) != null) {
         this.grant(player, false);
      }
   }

   public boolean exempt(Player player) {
      return player != null && this.open.containsKey(player.getUniqueId());
   }

   public void tick() {
      if (!this.open.isEmpty()) {
         int now = Bukkit.getCurrentTick();
         Iterator<Entry<UUID, Integer>> it = this.open.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> entry = it.next();
            if (now >= entry.getValue()) {
               it.remove();
               Player player = Bukkit.getPlayer(entry.getKey());
               if (player != null) {
                  this.grant(player, false);
               }
            }
         }
      }
   }

   private void grant(Player player, boolean add) {
      if (this.luckPerms != null) {
         User user = this.luckPerms.getUserManager().getUser(player.getUniqueId());
         if (user != null) {
            for (String permission : this.permissions) {
               Node node = Node.builder(permission).build();
               if (add) {
                  user.transientData().add(node);
               } else {
                  user.transientData().remove(node);
               }
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.open.remove(event.getPlayer().getUniqueId());
   }

   public int openWindows() {
      return this.open.size();
   }

   public List<String> permissions() {
      return List.copyOf(this.permissions);
   }
}
