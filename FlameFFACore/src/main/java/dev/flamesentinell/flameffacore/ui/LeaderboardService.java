package dev.flamesentinell.flameffacore.ui;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.storage.PlayerStore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import dev.flamesentinell.flameffacore.util.Tasks;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class LeaderboardService {
   private static final String TAG = "flameffacore_board";
   public static final List<String> METRICS = List.of("kills", "deaths", "streak", "playtime");
   private final FlameFFACore plugin;
   private final List<TextDisplay> live = new CopyOnWriteArrayList<>();
   private final AtomicInteger generation = new AtomicInteger();
   private ScheduledTask task;

   public LeaderboardService(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public void start() {
      this.sweep();
      this.spawn();
      if (!this.live.isEmpty()) {
         this.plugin.getSLF4JLogger().info("spawned {} leaderboard(s)", this.live.size());
      }

      int seconds = Math.max(10, this.plugin.configs().main().getInt("leaderboards.refresh-seconds", 60));
      if (this.task != null) {
         this.task.cancel();
      }

      this.task = this.plugin.tasks().globalTimer(task -> this.refresh(), 40L, seconds * 20L);
   }

   public void shutdown() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      this.despawn();
   }

   public int sweep() {
      if (Tasks.FOLIA) {
         this.generation.incrementAndGet();
         this.removeLive();
         return 0;
      }

      int gone = 0;

      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains("flameffacore_board")) {
               entity.remove();
               gone++;
            }
         }
      }

      this.live.clear();
      return gone;
   }

   private void removeLive() {
      for (TextDisplay display : this.live) {
         if (display != null) {
            this.plugin.tasks().runOn(display, () -> {
               if (!display.isDead()) {
                  display.remove();
               }
            });
         }
      }

      this.live.clear();
   }

   private void despawn() {
      this.generation.incrementAndGet();
      this.removeLive();
   }

   private List<?> boards() {
      return this.plugin.configs().main().getMapList("leaderboards.placed");
   }

   public boolean place(Location at, String metric, int size) {
      if (!METRICS.contains(metric)) {
         return false;
      } else {
         Map<String, Object> board = Map.of("world", at.getWorld().getName(), "x", at.getX(), "y", at.getY(), "z", at.getZ(), "metric", metric, "size", size);
         this.plugin.configs().update("config.yml", cfg -> {
            List<Map<?, ?>> list = new ArrayList<>(cfg.getMapList("leaderboards.placed"));
            list.add(board);
            cfg.set("leaderboards.placed", list);
         });
         this.start();
         return true;
      }
   }

   public boolean removeNear(Location at, double range) {
      List<Map<?, ?>> list = new ArrayList<>(this.plugin.configs().main().getMapList("leaderboards.placed"));
      int best = -1;
      double closest = range * range;

      for (int i = 0; i < list.size(); i++) {
         Location spot = read(list.get(i));
         if (spot != null && at.getWorld() != null && spot.getWorld().equals(at.getWorld())) {
            double distance = spot.distanceSquared(at);
            if (distance <= closest) {
               closest = distance;
               best = i;
            }
         }
      }

      if (best < 0) {
         return false;
      } else {
         Location removed = read(list.get(best));
         if (removed != null) {
            this.plugin.tasks().runAt(removed, () -> removed.getWorld().removePluginChunkTicket(removed.getBlockX() >> 4, removed.getBlockZ() >> 4, this.plugin));
         }

         Map<?, ?> chosen = list.get(best);
         this.plugin.configs().update("config.yml", cfg -> {
            List<Map<?, ?>> current = new ArrayList<>(cfg.getMapList("leaderboards.placed"));
            current.remove(chosen);
            cfg.set("leaderboards.placed", current);
         });
         this.start();
         return true;
      }
   }

   public int count() {
      return this.boards().size();
   }

   private static Location read(Map<?, ?> map) {
      Object world = map.get("world");
      if (world == null) {
         return null;
      } else {
         World w = Bukkit.getWorld(String.valueOf(world));
         return w == null ? null : new Location(w, number(map.get("x")), number(map.get("y")), number(map.get("z")));
      }
   }

   private static double number(Object value) {
      return value instanceof Number n ? n.doubleValue() : 0.0;
   }

   private void spawn() {
      List<?> placed = this.boards();
      int current = this.generation.incrementAndGet();
      this.live.clear();
      this.live.addAll(Collections.nCopies(placed.size(), null));

      for (int index = 0; index < placed.size(); index++) {
         if (placed.get(index) instanceof Map<?, ?> map) {
            Location at = read(map);
            if (at != null) {
               int slot = index;
               this.plugin.tasks().runAt(at, () -> {
                  if (current != this.generation.get()) {
                     return;
                  }

                  TextDisplay display = (TextDisplay)at.getWorld().spawn(at, TextDisplay.class, d -> {
                     d.setBillboard(Billboard.CENTER);
                     d.setAlignment(TextAlignment.CENTER);
                     d.setShadowed(true);
                     d.setSeeThrough(false);
                     d.setPersistent(false);
                     d.setViewRange(1.2F);
                     d.addScoreboardTag("flameffacore_board");
                     d.text(this.plugin.configs().msg("leaderboard.loading"));
                  });
                  at.getWorld().setChunkForceLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4, false);
                  at.getWorld().addPluginChunkTicket(at.getBlockX() >> 4, at.getBlockZ() >> 4, this.plugin);
                  if (current == this.generation.get() && slot < this.live.size()) {
                     this.live.set(slot, display);
                  } else {
                     display.remove();
                     return;
                  }

                  if (Tasks.FOLIA) {
                     this.refreshOne(slot);
                  }
               });
            }
         }
      }

      this.refresh();
   }

   private void refresh() {
      this.refreshOne(-1);
   }

   private void refreshOne(int only) {
      List<?> placed = this.boards();
      if (!placed.isEmpty() && !this.live.isEmpty()) {
         this.plugin.async(() -> {
            List<Component> rendered = new ArrayList<>();

            for (int index = 0; index < placed.size(); index++) {
               Object raw = placed.get(index);
               if (only >= 0 && index != only) {
                  rendered.add(null);
               } else if (raw instanceof Map<?, ?> map) {
                  Object metricRaw = map.get("metric");
                  Object sizeRaw = map.get("size");
                  String metric = metricRaw == null ? "kills" : String.valueOf(metricRaw);
                  int size = sizeRaw == null ? 10 : (int)number(sizeRaw);
                  rendered.add(this.render(metric, Math.max(1, Math.min(15, size))));
               } else {
                  rendered.add(Component.empty());
               }
            }

            for (int i = 0; i < this.live.size() && i < rendered.size(); i++) {
               TextDisplay display = this.live.get(i);
               Component text = rendered.get(i);
               if (display != null && text != null) {
                  this.plugin.tasks().runOn(display, () -> {
                     if (!display.isDead()) {
                        display.text(text);
                     }
                  });
               }
            }
         });
      }
   }

   private Component render(String metric, int size) {
      List<PlayerStore.Top> rows = this.plugin.players().top(metric, size);
      Component out = this.plugin.configs().msg("leaderboard.title", "metric", metric.substring(0, 1).toUpperCase(Locale.ROOT) + metric.substring(1));
      if (rows.isEmpty()) {
         return out.append(Component.newline()).append(this.plugin.configs().msg("leaderboard.empty"));
      } else {
         for (int i = 0; i < rows.size(); i++) {
            PlayerStore.Top row = rows.get(i);
            out = out.append(Component.newline())
               .append(this.plugin.configs().msg("leaderboard.row", "rank", String.valueOf(i + 1), "player", row.name(), "value", value(metric, row.value())));
         }

         return out;
      }
   }

   private static String value(String metric, long raw) {
      byte var4 = -1;
      switch (metric.hashCode()) {
         case 1879712769:
            if (metric.equals("playtime")) {
               var4 = 0;
            }
         default:
            return switch (var4) {
               case 0 -> {
                  long minutes = raw / 60000L;
                  long hours = minutes / 60L;
                  yield hours > 0L ? hours + "h " + minutes % 60L + "m" : minutes + "m";
               }
               default -> String.valueOf(raw);
            };
      }
   }
}
