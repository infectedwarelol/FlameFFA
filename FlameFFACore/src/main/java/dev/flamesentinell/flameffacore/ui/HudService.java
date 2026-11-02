package dev.flamesentinell.flameffacore.ui;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.crate.Crate;
import dev.flamesentinell.flameffacore.crate.CrateRegistry;
import dev.flamesentinell.flameffacore.rank.Rank;
import dev.flamesentinell.flameffacore.staff.StaffRole;
import dev.flamesentinell.flameffacore.storage.CrateBlockStore;
import dev.flamesentinell.flameffacore.storage.PlayerStore;
import dev.flamesentinell.flameffacore.util.Bots;
import dev.flamesentinell.flameffacore.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import dev.flamesentinell.flameffacore.util.Tasks;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;

public final class HudService implements Listener {
   private static final String CRATE_TAG = "flameffacore_crate";
   private static final int TICK_PERIOD = 5;
   private static final int PLAYTIME_EVERY = 12;
   private static final String[] ENTRIES = new String[]{"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e"};
   private final FlameFFACore plugin;
   private final Map<UUID, HudService.Session> sessions = new ConcurrentHashMap<>();
   private final Map<UUID, String> tagKeys = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> shownHealth = new ConcurrentHashMap<>();
   private static final String HP_OBJECTIVE = "flamehp";
   private static final String BOT_TEAM = "smbot_bot";
   private final List<TextDisplay> crateLabels = new CopyOnWriteArrayList<>();
   private volatile Set<Long> crateChunks = Set.of();
   private final Map<UUID, ScheduledTask> tickers = new ConcurrentHashMap<>();
   private final Map<UUID, int[]> playerTicks = new ConcurrentHashMap<>();
   private ScheduledTask task;
   private int ticks;

   public HudService(FlameFFACore var1) {
      this.plugin = var1;
   }

   public void start() {
      this.plugin.crateRegistry().materialiseAll();
      this.spawnCrateLabels();

      for (Player var2 : Bukkit.getOnlinePlayers()) {
         this.plugin.tasks().runOn(var2, () -> {
            if (!Bots.is(var2)) {
               this.attach(var2);
            }

            this.startTicker(var2);
         });
      }

      this.task = this.plugin.tasks().globalTimer(task -> this.tick(), 5L, 5L);
   }

   private void startTicker(Player player) {
      UUID uuid = player.getUniqueId();
      Tasks.cancel(this.tickers.remove(uuid));
      this.playerTicks.put(uuid, new int[1]);
      ScheduledTask ticker = this.plugin.tasks().entityTimer(player, task -> this.tick(player), () -> {
         this.tickers.remove(uuid);
         this.playerTicks.remove(uuid);
      }, 5L, 5L);
      if (ticker != null) {
         this.tickers.put(uuid, ticker);
      }
   }

   public void shutdown() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      for (ScheduledTask ticker : this.tickers.values()) {
         ticker.cancel();
      }

      this.tickers.clear();
      this.playerTicks.clear();

      for (HudService.Session var2 : this.sessions.values()) {
         this.clearKeyLine(var2);
      }

      for (Player var4 : Bukkit.getOnlinePlayers()) {
         this.flush(var4.getUniqueId(), true);
      }

      this.sessions.clear();
      this.despawnCrateLabels();
   }

   public Component rankTag(Player var1) {
      String var2 = this.tagDisplay(var1);
      return (Component)(var2 == null ? Component.empty() : Text.tag(var2));
   }

   private String[] staffGroup(Player var1) {
      ConfigurationSection var2 = this.plugin.configs().ranks().getConfigurationSection("staff");
      if (var2 == null) {
         return null;
      } else {
         String[] var3 = null;
         int var4 = Integer.MIN_VALUE;
         HashSet var5 = null;

         try {
            User var6 = LuckPermsProvider.get().getUserManager().getUser(var1.getUniqueId());
            if (var6 != null) {
               var5 = new HashSet();

               for (Group var8 : var6.getInheritedGroups(var6.getQueryOptions())) {
                  var5.add(var8.getName().toLowerCase(Locale.ROOT));
               }
            }
         } catch (Throwable var12) {
         }

         for (String var14 : var2.getKeys(false)) {
            String var15 = var2.getString(var14 + ".group");
            String var9 = var2.getString(var14 + ".display");
            if (var15 != null
               && !var15.isBlank()
               && var9 != null
               && !var9.isBlank()
               && (var5 != null ? var5.contains(var15.toLowerCase(Locale.ROOT)) : var1.hasPermission("group." + var15))) {
               int var10 = var2.getInt(var14 + ".weight", 0);
               if (var10 > var4) {
                  var4 = var10;
                  String var11 = var14.length() > 12 ? var14.substring(0, 12) : var14;
                  var3 = new String[]{var9, String.valueOf(Math.max(0, var10)), var11};
               }
            }
         }

         return var3;
      }
   }

   private String tagDisplay(Player var1) {
      if (this.plugin.configs().main().getBoolean("tab.staff-tags", true)) {
         StaffRole var2 = this.plugin.staff().role(var1);
         String[] var3 = var2 == StaffRole.OWNER ? null : this.staffGroup(var1);
         if (var3 != null) {
            return var3[0];
         }

         if (var2 != StaffRole.NONE) {
            String var4 = this.plugin.configs().ranks().getString("staff." + var2.id() + ".display");
            if (var4 != null && !var4.isBlank()) {
               return var4;
            }
         }
      }

      Rank var5 = this.plugin.ranks().highest(var1);
      if (var5 != null && "custom".equalsIgnoreCase(var5.id())) {
         String var6 = this.plugin.configs().ranks().getString("custom-tags." + var1.getUniqueId());
         if (var6 != null && !var6.isBlank()) {
            return var6;
         }
      }

      return var5 == null ? null : var5.display();
   }

   private String tagKey(Player var1) {
      if (botTagged(var1)) {
         return "zbot";
      } else {
         if (this.plugin.configs().main().getBoolean("tab.staff-tags", true)) {
            StaffRole var2 = this.plugin.staff().role(var1);
            String[] var3 = var2 == StaffRole.OWNER ? null : this.staffGroup(var1);
            if (var3 != null) {
               return String.format(Locale.ROOT, "a%03d%s", 999 - Math.min(999, Integer.parseInt(var3[1])), var3[2]);
            }

            if (var2 != StaffRole.NONE) {
               return String.format(Locale.ROOT, "a%03d%s", 999 - Math.min(999, var2.weight()), var2.id());
            }
         }

         Rank var4 = this.plugin.ranks().highest(var1);
         if (var4 == null) {
            return "c";
         } else {
            String var5 = var4.id().length() > 12 ? var4.id().substring(0, 12) : var4.id();
            return String.format(Locale.ROOT, "b%03d%s", 999 - Math.max(0, Math.min(999, var4.weight())), var5);
         }
      }
   }

   private static boolean botTagged(Player var0) {
      if (Bots.is(var0)) {
         return true;
      } else if (Tasks.FOLIA) {
         return false;
      } else {
         Team var1 = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("smbot_bot");
         return var1 != null && var1.hasEntry(var0.getName());
      }
   }

   private Component tagPrefix(Player var1) {
      if (botTagged(var1)) {
         return Component.text("[BOT] ", NamedTextColor.RED, new TextDecoration[]{TextDecoration.BOLD});
      } else {
         Component var2 = this.rankTag(var1);
         return (Component)(var2.equals(Component.empty()) ? Component.empty() : var2.append(Component.space()));
      }
   }

   Component hud(Component var1) {
      String var2 = this.plugin.configs().main().getString("tab.font", "flameffa:bold");
      if (var2 != null && !var2.isBlank()) {
         try {
            return var1.font(Key.key(var2));
         } catch (Exception var4) {
            return var1;
         }
      } else {
         return var1;
      }
   }

   private void placeTag(Scoreboard var1, Player var2, String var3, Component var4) {
      Team var5 = var1.getTeam(var3);
      if (var5 == null) {
         var5 = var1.registerNewTeam(var3);
         var5.color(NamedTextColor.WHITE);
         var5.setCanSeeFriendlyInvisibles(false);
      }

      var4 = this.hud(var4);
      if (!var4.equals(var5.prefix())) {
         var5.prefix(var4);
      }

      if (!var5.hasEntry(var2.getName())) {
         var5.addEntry(var2.getName());
      }

      Objective var6 = var1.getObjective("flamehp");
      if (var6 != null) {
         var6.getScore(var2.getName()).setScore((int)Math.ceil(var2.getHealth()));
      }
   }

   private void setupBoardTags(Scoreboard var1) {
      if (this.plugin.configs().main().getBoolean("tab.health-below-name", true)) {
         Objective var2 = var1.registerNewObjective("flamehp", Criteria.DUMMY, this.hud(this.plugin.configs().msg("hud.below-name")), RenderType.INTEGER);
         var2.setDisplaySlot(DisplaySlot.BELOW_NAME);
      }

      for (Player var3 : Bukkit.getOnlinePlayers()) {
         this.placeTag(var1, var3, this.tagKey(var3), this.tagPrefix(var3));
      }
   }

   public void refreshTag(Player var1, boolean var2) {
      if (Tasks.FOLIA) {
         return;
      }

      String var3 = this.tagKey(var1);
      Component var4 = this.tagPrefix(var1);
      String var5 = var3 + "|" + Text.plain(var4) + "|" + var4.hashCode();
      if (var2 || !var5.equals(this.tagKeys.get(var1.getUniqueId()))) {
         this.tagKeys.put(var1.getUniqueId(), var5);

         for (HudService.Session var7 : this.sessions.values()) {
            if (var7.board != null) {
               this.placeTag(var7.board, var1, var3, var4);
            }
         }
      }
   }

   private void sweepWindCharges() {
      if (Tasks.FOLIA) {
         return;
      }

      int var1 = this.plugin.configs().main().getInt("sweep.wind-charge-seconds", 10) * 20;
      if (var1 > 0) {
         for (World var3 : Bukkit.getWorlds()) {
            for (AbstractWindCharge var5 : var3.getEntitiesByClass(AbstractWindCharge.class)) {
               if (var5.getTicksLived() > var1) {
                  var5.remove();
               }
            }
         }
      }
   }

   private void pushHealth(Player var2) {
      if (!Tasks.FOLIA) {
         int var3 = (int)Math.ceil(var2.getHealth());
         Integer var4 = this.shownHealth.put(var2.getUniqueId(), var3);
         if (var4 == null || var4 != var3) {
            for (HudService.Session var6 : this.sessions.values()) {
               Objective var7 = var6.board == null ? null : var6.board.getObjective("flamehp");
               if (var7 != null) {
                  var7.getScore(var2.getName()).setScore(var3);
               }
            }
         }
      }
   }

   private void dropTag(Player var1) {
      this.tagKeys.remove(var1.getUniqueId());
      this.shownHealth.remove(var1.getUniqueId());
      if (Tasks.FOLIA) {
         return;
      }

      for (HudService.Session var3 : this.sessions.values()) {
         if (var3.board != null) {
            Team var4 = var3.board.getEntryTeam(var1.getName());
            if (var4 != null && !var4.getName().startsWith("line")) {
               var4.removeEntry(var1.getName());
            }

            Objective var5 = var3.board.getObjective("flamehp");
            if (var5 != null) {
               var5.getScore(var1.getName()).resetScore();
            }
         }
      }
   }

   public void listName(Player var1) {
      HudService.Session var2 = this.sessions.get(var1.getUniqueId());
      String var3 = this.tagKey(var1) + "|" + this.tagDisplay(var1);
      if (var2 == null || !var3.equals(var2.listRank)) {
         if (var2 != null) {
            var2.listRank = var3;
         }

         var1.playerListName(
            this.hud(
               Text.parse(
                  this.plugin.configs().messages().getString("tab.name-format", "<rank> <player>"), "rank", this.rankTag(var1), "player", var1.getName()
               )
            )
         );
      }
   }

   public void tabList(Player var1) {
      FileConfiguration var2 = this.plugin.configs().main();
      if (var2.getBoolean("tab.enabled", true)) {
         String var3 = var2.getString("tab.version", "");
         if (var3 == null || var3.isBlank()) {
            var3 = Bukkit.getMinecraftVersion();
         }

         HudService.Session var4 = this.sessions.get(var1.getUniqueId());
         String var5 = String.valueOf(this.onlineCount());
         String var6 = String.valueOf(var1.getPing());
         Component var7 = ((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.empty()
                                 .append(Component.newline()))
                              .append(this.plugin.configs().msg("tab.title")))
                           .append(Component.newline()))
                        .append(this.plugin.configs().msg("tab.subtitle", "region", var2.getString("tab.region", ""), "version", var3)))
                     .append(Component.newline()))
                  .append(Component.newline()))
               .append(this.plugin.configs().msg("tab.header-stats", "online", var5, "ping", var6)))
            .append(Component.newline());
         Component var8 = ((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)((TextComponent)Component.empty()
                                 .append(Component.newline()))
                              .append(
                                 Text.parse(
                                    this.plugin.configs().messages().getString("tab.footer-rank", ""),
                                    "rank",
                                    this.rankTag(var1),
                                    "playtime",
                                    var4 == null ? "0m" : this.playtime(var4)
                                 )
                              ))
                           .append(Component.newline()))
                        .append(Component.newline()))
                     .append(this.plugin.configs().msg("tab.footer")))
                  .append(Component.newline()))
               .append(this.plugin.configs().msg("tab.footer-ip", "ip", var2.getString("tab.ip", ""))))
            .append(Component.newline());
         String var9 = var5 + "|" + var6 + "|" + Text.plain(var8);
         if (var4 == null || !var9.equals(var4.footer)) {
            if (var4 != null) {
               var4.footer = var9;
            }

            var1.sendPlayerListHeaderAndFooter(this.hud(var7), this.hud(var8));
         }
      }
   }

   private int onlineCount() {
      int var1 = 0;

      for (Player var3 : Bukkit.getOnlinePlayers()) {
         if (!Bots.is(var3) && !this.plugin.staff().vanished(var3)) {
            var1++;
         }
      }

      return var1;
   }

   public void refreshTabList() {
      for (Player var2 : Bukkit.getOnlinePlayers()) {
         this.plugin.tasks().runOn(var2, () -> {
            if (var2.isOnline() && !Bots.is(var2)) {
               HudService.Session var3 = this.sessions.get(var2.getUniqueId());
               if (var3 != null) {
                  var3.listRank = null;
                  var3.footer = null;
               }

               this.tabList(var2);
               this.listName(var2);
               this.refreshTag(var2, true);
            }
         });
      }
   }

   private void spawnCrateLabels() {
      this.despawnCrateLabels();

      for (CrateBlockStore.Placement var2 : this.plugin.crateRegistry().placements()) {
         Crate var3 = this.plugin.crateRegistry().get(var2.crateId());
         Location var4 = var2.location();
         if (var3 != null && var4 != null && var4.getWorld() != null) {
            Location var5 = var4.clone().add(0.5, 1.55, 0.5);
            this.plugin.tasks().runAt(var5, () -> {
               TextDisplay var6 = (TextDisplay)var5.getWorld().spawn(var5, TextDisplay.class, var2x -> {
                  var2x.text(this.hud(Text.parse(var3.display())));
                  var2x.setBillboard(Billboard.CENTER);
                  var2x.setSeeThrough(false);
                  var2x.setViewRange(0.4F);
                  var2x.setPersistent(false);
                  var2x.addScoreboardTag("flameffacore_crate");
               });
               this.crateLabels.add(var6);
            });
         }
      }

      Set<Long> chunks = new HashSet<>();

      for (Location var8 : this.plugin.crateRegistry().locations()) {
         int var9 = var8.getBlockX() >> 4;
         int var10 = var8.getBlockZ() >> 4;

         for (int var11 = -1; var11 <= 1; var11++) {
            for (int var12 = -1; var12 <= 1; var12++) {
               chunks.add(Chunk.getChunkKey(var9 + var11, var10 + var12));
            }
         }
      }

      this.crateChunks = Set.copyOf(chunks);

      this.plugin.getSLF4JLogger().info("spawned {} crate labels", this.crateLabels.size());
   }

   private void despawnCrateLabels() {
      for (TextDisplay var2 : this.crateLabels) {
         this.plugin.tasks().runOn(var2, () -> {
            if (var2.isValid()) {
               var2.remove();
            }
         });
      }

      this.crateLabels.clear();

      for (Location var7 : this.plugin.crateRegistry().locations()) {
         if (var7.getWorld() != null) {
            Location var8 = var7.clone().add(0.5, 1.5, 0.5);
            this.plugin.tasks().runAt(var8, () -> {
               for (Entity var4 : var8.getWorld().getNearbyEntities(var8, 3.0, 3.0, 3.0)) {
                  if (var4 instanceof TextDisplay var5 && var5.getScoreboardTags().contains("flameffacore_crate")) {
                     var5.remove();
                  }
               }
            });
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      this.startTicker(var1.getPlayer());
      if (Bots.is(var1.getPlayer())) {
         this.plugin.tasks().entity(var1.getPlayer(), () -> {
            if (var1.getPlayer().isOnline()) {
               this.refreshTag(var1.getPlayer(), true);
            }
         });
      } else {
         this.tabList(var1.getPlayer());
         this.listName(var1.getPlayer());
         Player var2 = var1.getPlayer();

         for (HudService.Session var4 : this.sessions.values()) {
            TextDisplay var5 = var4.keyLine;
            if (var5 != null) {
               var2.hideEntity(this.plugin, var5);
            }
         }

         this.attach(var2);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onWindCharge(ProjectileLaunchEvent event) {
      if (Tasks.FOLIA && event.getEntity() instanceof AbstractWindCharge charge) {
         int limit = this.plugin.configs().main().getInt("sweep.wind-charge-seconds", 10) * 20;
         if (limit > 0) {
            this.plugin.tasks().entityLater(charge, () -> {
               if (charge.isValid()) {
                  charge.remove();
               }
            }, limit + 1L);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      Tasks.cancel(this.tickers.remove(var1.getPlayer().getUniqueId()));
      this.playerTicks.remove(var1.getPlayer().getUniqueId());
      this.dropTag(var1.getPlayer());
      if (!Bots.is(var1.getPlayer())) {
         this.flush(var1.getPlayer().getUniqueId(), true);
      }
   }

   private void attach(Player var1) {
      HudService.Session var2 = new HudService.Session();
      this.sessions.put(var1.getUniqueId(), var2);
      if (!Tasks.FOLIA) {
         this.attachBoard(var1, var2);
      }

      UUID var7 = var1.getUniqueId();
      this.plugin.async(() -> {
         PlayerStore.Stats var3x = this.plugin.players().stats(var7);
         this.plugin.sync(var1, () -> {
            HudService.Session var4x = this.sessions.get(var7);
            if (var4x != null) {
               var4x.kills = var3x.kills();
               var4x.deaths = var3x.deaths();
               var4x.bestStreak = var3x.bestStreak();
               var4x.storedPlaytimeMs = var3x.playtimeMs();
               this.render(var1, var4x, true);
            }
         });
      });
   }

   private void attachBoard(Player var1, HudService.Session var2) {
      Scoreboard var3 = Bukkit.getScoreboardManager().getNewScoreboard();
      Objective var4 = var3.registerNewObjective("flameffa", Criteria.DUMMY, this.hud(this.plugin.configs().msg("hud.title")));
      var4.setDisplaySlot(DisplaySlot.SIDEBAR);
      var4.numberFormat(NumberFormat.blank());

      for (int var5 = 0; var5 < ENTRIES.length; var5++) {
         Team var6 = var3.registerNewTeam("line" + var5);
         var6.addEntry(ENTRIES[var5]);
      }

      var2.board = var3;
      this.setupBoardTags(var3);
      var1.setScoreboard(var3);
      this.refreshTag(var1, true);
   }

   private void flush(UUID var1, boolean var2) {
      HudService.Session var3 = var2 ? this.sessions.remove(var1) : this.sessions.get(var1);
      if (var3 != null) {
         this.clearKeyLine(var3);
         long var4 = System.currentTimeMillis() - var3.joinedAt;
         var3.joinedAt = System.currentTimeMillis();
         var3.storedPlaytimeMs += var4;
         this.plugin.async(() -> this.plugin.players().addPlaytime(var1, var4));
      }
   }

   public void onKill(Player var1, int var2) {
      HudService.Session var3 = this.sessions.get(var1.getUniqueId());
      if (var3 != null) {
         var3.kills++;
         var3.bestStreak = Math.max(var3.bestStreak, var2);
         this.render(var1, var3, false);
      }
   }

   public void onDeath(Player var1) {
      HudService.Session var2 = this.sessions.get(var1.getUniqueId());
      if (var2 != null) {
         var2.deaths++;
         this.render(var1, var2, false);
      }
   }

   private void tick() {
      this.ticks++;
      if (this.ticks % 12 == 0) {
         this.relabelIfLost();
      }

      if (this.ticks % 4 == 0) {
         this.sweepWindCharges();
      }

      this.plugin.weapons().tick();
      this.plugin.antiCheat().tick();
   }

   private void tick(Player player) {
      int[] counter = this.playerTicks.get(player.getUniqueId());
      if (counter != null && player.isOnline()) {
         int now = ++counter[0];
         boolean slow = now % 12 == 0;
         boolean mid = now % 4 == 0;
         if (slow) {
            this.refreshTag(player, false);
         }

         this.pushHealth(player);
         this.plugin.weapons().tick(player);
         if (!Bots.is(player)) {
            boolean sparring = Bots.sparring(player);
            if (!sparring) {
               this.plugin.safezone().tick(player);
            }

            this.plugin.cooldownBar().tick(player);
            if (mid) {
               if (!sparring) {
                  this.plugin.afk().tick(player);
               }

               this.plugin.combatHud().tick(player);
               this.plugin.cosmetics().tick(player);
            }

            HudService.Session session = this.sessions.get(player.getUniqueId());
            if (session != null) {
               if (slow) {
                  this.render(player, session, false);
                  this.listName(player);
                  this.tabList(player);
               }

               this.crateLook(player, session);
            }
         }
      }
   }

   private void relabelIfLost() {
      if (!Tasks.FOLIA && !Bukkit.getOnlinePlayers().isEmpty() && !this.crateLabels.isEmpty()) {
         for (TextDisplay var2 : this.crateLabels) {
            if (!var2.isValid()) {
               this.plugin.crateRegistry().materialiseAll();
               this.spawnCrateLabels();
               return;
            }
         }
      }
   }

   private void render(Player var1, HudService.Session var2, boolean var3) {
      if (var2.board == null) {
         return;
      }

      List var4 = this.lines(var1, var2);
      int var5 = Math.min(var4.size(), ENTRIES.length);

      for (int var6 = 0; var6 < var5; var6++) {
         String var7 = (String)var4.get(var6);
         if (var3 || var6 >= var2.shown || !var7.equals(var2.rendered[var6])) {
            var2.rendered[var6] = var7;
            Team var8 = var2.board.getTeam("line" + var6);
            if (var8 != null) {
               var8.prefix(this.hud(Text.parse(var7)));
            }
         }
      }

      if (var3 || var5 != var2.shown) {
         Objective var9 = var2.board.getObjective("flameffa");
         if (var9 != null) {
            for (int var10 = 0; var10 < var5; var10++) {
               var9.getScore(ENTRIES[var10]).setScore(var5 - var10);
            }
         }

         for (int var11 = var5; var11 < var2.shown; var11++) {
            var2.board.resetScores(ENTRIES[var11]);
            var2.rendered[var11] = null;
         }

         var2.shown = var5;
      }
   }

   private List<String> lines(Player var1, HudService.Session var2) {
      int var3 = this.plugin.arena().streak(var1.getUniqueId());
      FileConfiguration var4 = this.plugin.configs().main();
      ArrayList var5 = new ArrayList();
      var5.add(this.line("hud.date", "date", this.date()));
      var5.add(this.line("hud.spacer-1"));
      var5.add(this.line("hud.player", "player", var1.getName()));
      var5.add(this.line("hud.spacer-2"));
      var5.add(this.line("hud.kills", "value", String.valueOf(var2.kills)));
      var5.add(this.line("hud.deaths", "value", String.valueOf(var2.deaths)));
      var5.add(this.line("hud.streak", "value", String.valueOf(var3)));
      var5.add(this.line("hud.kd", "value", ratio(var2.kills, var2.deaths)));
      var5.add(this.line("hud.spacer-3"));
      var5.add(this.line("hud.playtime", "value", this.playtime(var2)));
      var5.add(this.line("hud.online", "value", String.valueOf(this.onlineCount())));
      var5.add(this.line("hud.ping", "value", String.valueOf(var1.getPing())));
      int var6 = 0;

      for (Crate var8 : this.plugin.crateRegistry().all().values()) {
         var6 += Math.max(0, this.plugin.keys().cached(var1.getUniqueId(), var8.id()));
      }

      if (var6 > 0) {
         var5.add(this.line("hud.keys-total", "value", String.valueOf(var6)));
      }

      String var9 = var4.getString("tab.ip", "");
      if (!var9.isBlank()) {
         var5.add(this.line("hud.spacer-4"));
         var5.add(this.line("hud.ip", "ip", var9));
      }

      return var5;
   }

   private String date() {
      FileConfiguration var1 = this.plugin.configs().main();

      ZoneId var2;
      try {
         var2 = ZoneId.of(var1.getString("tab.timezone", "Europe/Amsterdam"));
      } catch (Exception var6) {
         var2 = ZoneId.systemDefault();
      }

      String var3 = var1.getString("tab.date-format", "d MMM, yyyy");

      try {
         return DateTimeFormatter.ofPattern(var3, Locale.ENGLISH).format(ZonedDateTime.now(var2));
      } catch (Exception var5) {
         return LocalDate.now(var2).toString();
      }
   }

   private static String ratio(int var0, int var1) {
      return var1 <= 0 ? String.format(Locale.ROOT, "%.2f", (double)var0) : String.format(Locale.ROOT, "%.2f", (double)var0 / var1);
   }

   private String line(String var1, String... var2) {
      String var3 = this.plugin.configs().messages().getString(var1, "");

      for (byte var4 = 0; var4 + 1 < var2.length; var4 += 2) {
         var3 = var3.replace("<" + var2[var4] + ">", var2[var4 + 1]);
      }

      return var3;
   }

   String playtime(HudService.Session var1) {
      long var2 = var1.storedPlaytimeMs + (System.currentTimeMillis() - var1.joinedAt);
      long var4 = var2 / 60000L;
      long var6 = var4 / 60L;
      if (var6 >= 24L) {
         return var6 / 24L + "d " + var6 % 24L + "h";
      } else {
         return var6 > 0L ? var6 + "h " + var4 % 60L + "m" : var4 + "m";
      }
   }

   private void crateLook(Player var1, HudService.Session var2) {
      if (!this.crateChunks.contains(Chunk.getChunkKey((int)Math.floor(var1.getX()) >> 4, (int)Math.floor(var1.getZ()) >> 4))) {
         if (var2.keyLine != null) {
            this.clearKeyLine(var2);
            var2.lookingAt = null;
         }
      } else {
         Location var3 = this.blockInSight(var1);
         Crate var4 = var3 == null ? null : this.plugin.crateRegistry().atBlock(var3);
         String var5 = var4 == null ? null : var4.id();
         if (var5 == null) {
            this.clearKeyLine(var2);
            var2.lookingAt = null;
         } else {
            int var6 = this.plugin.keys().cached(var1.getUniqueId(), var5);
            String var7 = CrateRegistry.key(var3);
            String var8 = var5 + ":" + var6;
            boolean var9 = !var7.equals(var2.lookingAt) || var2.keyLine == null || !var2.keyLine.isValid();
            if (var9 || !var8.equals(var2.keyText)) {
               Component var10 = this.plugin.configs().msg("hud.crate-keys", "amount", String.valueOf(var6), "crate", Text.plain(Text.parse(var4.display())));
               var2.keyText = var8;
               if (var9) {
                  this.clearKeyLine(var2);
                  var2.lookingAt = var7;
                  var2.keyLine = this.spawnKeyLine(var1, var3, var10);
               } else {
                  var2.keyLine.text(this.hud(var10));
               }
            }
         }
      }
   }

   private Location blockInSight(Player var1) {
      RayTraceResult var2 = var1.rayTraceBlocks(5.0);
      return var2 != null && var2.getHitBlock() != null ? var2.getHitBlock().getLocation() : null;
   }

   private TextDisplay spawnKeyLine(Player var1, Location var2, Component var3) {
      if (var2.getWorld() == null) {
         return null;
      } else {
         Location var4 = var2.clone().add(0.5, 1.28, 0.5);
         TextDisplay var5 = (TextDisplay)var4.getWorld().spawn(var4, TextDisplay.class, var2x -> {
            var2x.text(this.hud(var3));
            var2x.setBillboard(Billboard.CENTER);
            var2x.setSeeThrough(false);
            var2x.setViewRange(0.4F);
            var2x.setPersistent(false);
            var2x.setVisibleByDefault(false);
         });
         var1.showEntity(this.plugin, var5);
         return var5;
      }
   }

   private void clearKeyLine(HudService.Session var1) {
      TextDisplay var2 = var1.keyLine;
      if (var2 != null) {
         var1.keyLine = null;
         this.plugin.tasks().runOn(var2, () -> {
            if (var2.isValid()) {
               var2.remove();
            }
         });
      }
   }

   public void refreshCrates() {
      this.plugin.crateRegistry().materialiseAll();

      for (HudService.Session var2 : this.sessions.values()) {
         this.clearKeyLine(var2);
         var2.lookingAt = null;
      }

      this.spawnCrateLabels();
   }

   private static final class Session {
      int kills;
      int deaths;
      int bestStreak;
      long storedPlaytimeMs;
      long joinedAt = System.currentTimeMillis();
      Scoreboard board;
      final String[] rendered = new String[HudService.ENTRIES.length];
      int shown;
      String lookingAt;
      volatile TextDisplay keyLine;
      String keyText;
      String listRank;
      String footer;
   }
}
