package dev.flamesentinell.flameffacore.ui;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Text;
import dev.flamesentinell.flameffacore.weapon.WeaponRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class CooldownBar {
   private final FlameFFACore plugin;
   private final Map<UUID, Integer> holds = new HashMap<>();
   private final Map<UUID, Boolean> drawn = new HashMap<>();
   private final Map<UUID, Component> pinned = new HashMap<>();
   private final Map<UUID, Integer> pinUntil = new HashMap<>();
   private final Map<UUID, Component> lastSent = new HashMap<>();
   private final Map<UUID, Integer> lastSentTick = new HashMap<>();
   private Component separatorPart = Component.empty();
   private final Map<String, Component> lines = new HashMap<>();
   private boolean enabled = true;
   private int holdTicks = 30;
   private String entry = "<colour><seconds>s";
   private String ready = "<colour>READY";
   private String separator = "<dark_gray> // ";

   public CooldownBar(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void load() {
      FileConfiguration cfg = this.plugin.configs().main();
      this.enabled = cfg.getBoolean("combat-hud.cooldown-bar", true);
      this.holdTicks = Math.max(5, cfg.getInt("combat-hud.action-bar-hold-ticks", 30));
      FileConfiguration msg = this.plugin.configs().get("messages.yml");
      this.entry = msg.getString("cooldown-bar.entry", this.entry);
      this.ready = msg.getString("cooldown-bar.ready", this.ready);
      this.separator = msg.getString("cooldown-bar.separator", this.separator);
      this.separatorPart = Text.parse(this.separator);
      this.lines.clear();
   }

   public void flash(Player player, Component message) {
      this.flash(player, message, this.holdTicks);
   }

   public void flash(Player player, Component message, int ticks) {
      player.sendActionBar(message);
      this.holds.put(player.getUniqueId(), Bukkit.getCurrentTick() + Math.max(1, ticks));
   }

   public void pin(Player player, Component message, int ticks) {
      this.pinned.put(player.getUniqueId(), message);
      this.pinUntil.put(player.getUniqueId(), Bukkit.getCurrentTick() + ticks);
   }

   public void unpin(Player player) {
      this.pinned.remove(player.getUniqueId());
      this.pinUntil.remove(player.getUniqueId());
   }

   public void drop(Player player) {
      this.holds.remove(player.getUniqueId());
      this.drawn.remove(player.getUniqueId());
      this.lastSent.remove(player.getUniqueId());
      this.lastSentTick.remove(player.getUniqueId());
      this.unpin(player);
   }

   private void send(Player player, UUID id, Component out) {
      int now = Bukkit.getCurrentTick();
      Integer at = this.lastSentTick.get(id);
      if (at == null || now - at >= 20 || !out.equals(this.lastSent.get(id))) {
         this.lastSent.put(id, out);
         this.lastSentTick.put(id, now);
         player.sendActionBar(out);
      }
   }

   public void tick(Player player) {
      if (this.enabled) {
         UUID id = player.getUniqueId();
         Integer hold = this.holds.get(id);
         if (hold != null) {
            if (hold > Bukkit.getCurrentTick()) {
               return;
            }

            this.holds.remove(id);
         }

         Integer pinTo = this.pinUntil.get(id);
         if (pinTo != null) {
            if (pinTo > Bukkit.getCurrentTick()) {
               Component pin = this.pinned.get(id);
               if (pin != null) {
                  this.drawn.put(id, Boolean.TRUE);
                  this.send(player, id, pin);
               }

               return;
            }

            this.unpin(player);
         }

         List<WeaponRegistry.Cooling> cooling = this.plugin.weapons().hotbar(player);
         if (cooling.isEmpty()) {
            if (this.drawn.remove(id) != null) {
               this.lastSent.remove(id);
               this.lastSentTick.remove(id);
               player.sendActionBar(Component.empty());
            }
         } else {
            Component out = Component.empty();

            for (int i = 0; i < cooling.size(); i++) {
               if (i > 0) {
                  out = out.append(this.separatorPart);
               }

               out = out.append(this.line(cooling.get(i)));
            }

            this.drawn.put(id, Boolean.TRUE);
            this.send(player, id, out);
         }
      }
   }

   private Component line(WeaponRegistry.Cooling c) {
      String key = c.colour() + "\u0000" + c.name() + "\u0000" + c.left();
      Component cached = this.lines.get(key);
      if (cached != null) {
         return cached;
      } else {
         if (this.lines.size() > 4096) {
            this.lines.clear();
         }

         String template = (c.left() > 0L ? this.entry : this.ready).replace("<colour>", c.colour());
         Component out = Text.parse(template, "name", c.name(), "seconds", String.valueOf(c.left()));
         this.lines.put(key, out);
         return out;
      }
   }
}
