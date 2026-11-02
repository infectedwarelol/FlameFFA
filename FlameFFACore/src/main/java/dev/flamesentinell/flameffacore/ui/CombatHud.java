package dev.flamesentinell.flameffacore.ui;

import dev.flamesentinell.flameffacore.FlameFFACore;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public final class CombatHud implements Listener {
   private static final String TAG = "flameffacore_damage";
   private final FlameFFACore plugin;
   private final Map<UUID, BossBar> bars = new HashMap<>();
   private final ArrayDeque<Object[]> fading = new ArrayDeque<>();
   private final Map<String, Component> numberText = new HashMap<>();
   private boolean barEnabled = true;
   private boolean numbersEnabled = true;
   private int numberTicks = 16;

   public CombatHud(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void load() {
      FileConfiguration cfg = this.plugin.configs().main();
      this.barEnabled = cfg.getBoolean("combat-hud.bar", true);
      this.numbersEnabled = cfg.getBoolean("combat-hud.damage-numbers", true);
      this.numberTicks = Math.max(4, cfg.getInt("combat-hud.damage-number-ticks", 16));
   }

   public void tick(Player player) {
      if (this.barEnabled) {
         UUID id = player.getUniqueId();
         long left = this.plugin.arena().inCombat(player) ? this.plugin.cooldowns().remaining(id, "combat") : 0L;
         if (left <= 0L) {
            BossBar bar = this.bars.remove(id);
            if (bar != null) {
               player.hideBossBar(bar);
            }
         } else {
            int total = Math.max(1, this.plugin.configs().main().getInt("arena.combat-tag-seconds", 25));
            float progress = Math.min(1.0F, Math.max(0.0F, (float)left / total));
            BossBar bar = this.bars.get(id);
            if (bar == null) {
               bar = BossBar.bossBar(this.title(left), progress, Color.RED, Overlay.NOTCHED_10);
               this.bars.put(id, bar);
               player.showBossBar(bar);
            } else {
               bar.name(this.title(left));
               bar.progress(progress);
            }
         }
      }
   }

   private Component title(long seconds) {
      return this.plugin.configs().msg("combat.bar", "seconds", String.valueOf(seconds));
   }

   public void drop(Player player) {
      BossBar bar = this.bars.remove(player.getUniqueId());
      if (bar != null) {
         player.hideBossBar(bar);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.bars.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent event) {
      if (this.numbersEnabled && event.getEntity() instanceof Player victim) {
         if (event.getDamager() instanceof Player attacker && !attacker.equals(victim)) {
            double amount = event.getFinalDamage();
            if (!(amount <= 0.0)) {
               this.show(attacker, victim.getLocation(), amount);
            }
         }
      }
   }

   private void show(Player viewer, Location at, double amount) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      Location spot = at.clone().add((rng.nextDouble() - 0.5) * 0.8, 1.4 + rng.nextDouble() * 0.4, (rng.nextDouble() - 0.5) * 0.8);
      String label = String.format(Locale.ROOT, "%.1f", amount);
      Component text = this.numberText.get(label);
      if (text == null) {
         if (this.numberText.size() > 512) {
            this.numberText.clear();
         }

         text = this.plugin.configs().msg("combat.damage", "amount", label);
         this.numberText.put(label, text);
      }

      Component shown = text;
      TextDisplay display = (TextDisplay)spot.getWorld()
         .spawn(
            spot,
            TextDisplay.class,
            d -> {
               d.text(shown);
               d.setBillboard(Billboard.CENTER);
               d.setAlignment(TextAlignment.CENTER);
               d.setShadowed(true);
               d.setSeeThrough(true);
               d.setPersistent(false);
               d.setViewRange(0.35F);
               d.addScoreboardTag("flameffacore_damage");
               d.setVisibleByDefault(false);
               d.setTeleportDuration(this.numberTicks);
               d.setTransformation(
                  new Transformation(
                     new Vector3f(), d.getTransformation().getLeftRotation(), new Vector3f(0.8F, 0.8F, 0.8F), d.getTransformation().getRightRotation()
                  )
               );
            }
         );
      viewer.showEntity(this.plugin, display);
      Location end = spot.clone().add(0.0, 0.06 * this.numberTicks, 0.0);
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (display.isValid()) {
            display.teleport(end);
         }
      });
      this.fading.add(new Object[]{display, Bukkit.getCurrentTick() + this.numberTicks});
   }

   public void sweepNumbers() {
      int now = Bukkit.getCurrentTick();

      while (!this.fading.isEmpty() && (Integer)this.fading.peek()[1] <= now) {
         ((TextDisplay)this.fading.poll()[0]).remove();
      }
   }

   public int sweepLeftovers() {
      int gone = 0;

      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains("flameffacore_damage")) {
               entity.remove();
               gone++;
            }
         }
      }

      return gone;
   }

   public void shutdown() {
      for (Entry<UUID, BossBar> entry : this.bars.entrySet()) {
         Player player = Bukkit.getPlayer(entry.getKey());
         if (player != null) {
            player.hideBossBar(entry.getValue());
         }
      }

      this.bars.clear();
      this.sweepLeftovers();
   }
}
