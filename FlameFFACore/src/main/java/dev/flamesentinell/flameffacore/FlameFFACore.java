package dev.flamesentinell.flameffacore;

import dev.flamesentinell.flameffacore.chat.ChatGuard;
import dev.flamesentinell.flameffacore.command.AdminCommand;
import dev.flamesentinell.flameffacore.command.BoosterCommand;
import dev.flamesentinell.flameffacore.command.CosmeticsCommand;
import dev.flamesentinell.flameffacore.command.CrateCommand;
import dev.flamesentinell.flameffacore.command.KitCommand;
import dev.flamesentinell.flameffacore.command.LeaderboardCommand;
import dev.flamesentinell.flameffacore.command.RankKitCommand;
import dev.flamesentinell.flameffacore.command.SafezoneCommand;
import dev.flamesentinell.flameffacore.command.SpawnCommand;
import dev.flamesentinell.flameffacore.command.StaffCommand;
import dev.flamesentinell.flameffacore.command.StatsCommand;
import dev.flamesentinell.flameffacore.command.VillagerCommand;
import dev.flamesentinell.flameffacore.config.ConfigManager;
import dev.flamesentinell.flameffacore.cosmetic.CosmeticMenu;
import dev.flamesentinell.flameffacore.cosmetic.CosmeticService;
import dev.flamesentinell.flameffacore.crate.CrateRegistry;
import dev.flamesentinell.flameffacore.crate.CrateService;
import dev.flamesentinell.flameffacore.ffa.AfkWatcher;
import dev.flamesentinell.flameffacore.ffa.AntiCheat;
import dev.flamesentinell.flameffacore.ffa.ArenaService;
import dev.flamesentinell.flameffacore.ffa.ArenaSweeper;
import dev.flamesentinell.flameffacore.ffa.ProtectionListener;
import dev.flamesentinell.flameffacore.ffa.RulesListener;
import dev.flamesentinell.flameffacore.ffa.Safezone;
import dev.flamesentinell.flameffacore.ffa.ThrowAccuracy;
import dev.flamesentinell.flameffacore.item.ItemRegistry;
import dev.flamesentinell.flameffacore.kit.KitMenu;
import dev.flamesentinell.flameffacore.kit.KitService;
import dev.flamesentinell.flameffacore.pack.PackHost;
import dev.flamesentinell.flameffacore.rank.RankService;
import dev.flamesentinell.flameffacore.staff.FreezeService;
import dev.flamesentinell.flameffacore.staff.StaffService;
import dev.flamesentinell.flameffacore.storage.AuditStore;
import dev.flamesentinell.flameffacore.storage.BlobStore;
import dev.flamesentinell.flameffacore.storage.CosmeticStore;
import dev.flamesentinell.flameffacore.storage.CrateBlockStore;
import dev.flamesentinell.flameffacore.storage.Database;
import dev.flamesentinell.flameffacore.storage.KeyStore;
import dev.flamesentinell.flameffacore.storage.KitCooldownStore;
import dev.flamesentinell.flameffacore.storage.PendingStore;
import dev.flamesentinell.flameffacore.storage.PlayerStore;
import dev.flamesentinell.flameffacore.storage.TxnStore;
import dev.flamesentinell.flameffacore.storage.VillagerStore;
import dev.flamesentinell.flameffacore.tebex.TebexBridge;
import dev.flamesentinell.flameffacore.ui.CombatHud;
import dev.flamesentinell.flameffacore.ui.CooldownBar;
import dev.flamesentinell.flameffacore.ui.HudService;
import dev.flamesentinell.flameffacore.ui.LeaderboardService;
import dev.flamesentinell.flameffacore.util.Cooldowns;
import dev.flamesentinell.flameffacore.util.Tasks;
import dev.flamesentinell.flameffacore.villager.VillagerService;
import dev.flamesentinell.flameffacore.weapon.AegisShield;
import dev.flamesentinell.flameffacore.weapon.StunMace;
import dev.flamesentinell.flameffacore.weapon.TetherTrident;
import dev.flamesentinell.flameffacore.weapon.WeaponRegistry;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlameFFACore extends JavaPlugin {
   private ConfigManager configs;
   private Database database;
   private ExecutorService dbExecutor;
   private KeyStore keyStore;
   private PlayerStore playerStore;
   private AuditStore auditStore;
   private PendingStore pendingStore;
   private TxnStore txnStore;
   private ItemRegistry itemRegistry;
   private RankService rankService;
   private BlobStore blobStore;
   private KitService kitService;
   private KitMenu kitMenu;
   private CrateBlockStore crateBlockStore;
   private CrateRegistry crateRegistry;
   private CrateService crateService;
   private StaffService staffService;
   private FreezeService freezeService;
   private ArenaService arenaService;
   private Safezone safezone;
   private AfkWatcher afkWatcher;
   private ArenaSweeper sweeper;
   private AntiCheat antiCheat;
   private ChatGuard chatGuard;
   private TebexBridge tebexBridge;
   private HudService hudService;
   private PackHost packHost;
   private CombatHud combatHud;
   private CooldownBar cooldownBar;
   private LeaderboardService leaderboards;
   private CosmeticStore cosmeticStore;
   private CosmeticService cosmetics;
   private CosmeticMenu cosmeticMenu;
   private VillagerStore villagerStore;
   private VillagerService villagerService;
   private WeaponRegistry weaponRegistry;
   private SpawnCommand spawnCommand;
   private Cooldowns cooldowns;
   private KitCooldownStore kitCooldowns;
   private Tasks tasks;

   public void onEnable() {
      this.tasks = new Tasks(this);
      this.cooldowns = new Cooldowns();
      this.configs = new ConfigManager(this);
      this.configs.load();
      this.dbExecutor = Executors.newFixedThreadPool(4, var0 -> {
         Thread var1x = new Thread(var0, "FlameFFACore-DB");
         var1x.setDaemon(true);
         return var1x;
      });
      this.database = new Database(this);

      try {
         this.database.connect();
      } catch (Exception var2) {
         this.getSLF4JLogger().error("could not reach the database, disabling", var2);
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }

      this.keyStore = new KeyStore(this.database);
      this.playerStore = new PlayerStore(this.database);
      this.kitCooldowns = new KitCooldownStore(this.database);
      this.auditStore = new AuditStore(this.database);
      this.pendingStore = new PendingStore(this.database);
      this.txnStore = new TxnStore(this.database);
      this.itemRegistry = new ItemRegistry(this);
      this.rankService = new RankService(this);
      this.blobStore = new BlobStore(this.database);
      this.kitService = new KitService(this);
      this.kitService.load();
      this.kitMenu = new KitMenu(this);
      this.crateBlockStore = new CrateBlockStore(this.database);
      this.villagerStore = new VillagerStore(this.database);
      this.crateRegistry = new CrateRegistry(this);
      this.crateRegistry.load();
      this.crateRegistry.loadPlacements();
      this.crateService = new CrateService(this);
      this.crateService.load();
      this.staffService = new StaffService(this);
      this.freezeService = new FreezeService(this);
      this.safezone = new Safezone(this);
      this.safezone.load();
      this.afkWatcher = new AfkWatcher(this);
      this.afkWatcher.load();
      this.antiCheat = new AntiCheat(this);
      this.antiCheat.load();
      this.chatGuard = new ChatGuard(this);
      this.chatGuard.load();
      this.arenaService = new ArenaService(this);
      this.tebexBridge = new TebexBridge(this);
      this.hudService = new HudService(this);
      this.combatHud = new CombatHud(this);
      this.combatHud.load();
      this.cooldownBar = new CooldownBar(this);
      this.cooldownBar.load();
      this.leaderboards = new LeaderboardService(this);
      this.cosmeticStore = new CosmeticStore(this.database);
      this.cosmetics = new CosmeticService(this);
      this.cosmetics.load();
      this.cosmeticMenu = new CosmeticMenu(this);
      this.villagerService = new VillagerService(this);
      this.villagerService.load();
      this.weaponRegistry = new WeaponRegistry(this);
      Bukkit.getPluginManager().registerEvents(new RulesListener(this), this);
      this.sweeper = new ArenaSweeper(this);
      Bukkit.getPluginManager().registerEvents(this.sweeper, this);
      this.sweeper.start();
      Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
      Bukkit.getPluginManager().registerEvents(this.chatGuard, this);
      Bukkit.getPluginManager().registerEvents(this.afkWatcher, this);
      Bukkit.getPluginManager().registerEvents(this.antiCheat, this);
      Bukkit.getPluginManager().registerEvents(this.kitMenu, this);
      Bukkit.getPluginManager().registerEvents(this.crateService, this);
      Bukkit.getPluginManager().registerEvents(this.freezeService, this);
      Bukkit.getPluginManager().registerEvents(this.arenaService, this);
      Bukkit.getPluginManager().registerEvents(new ThrowAccuracy(this), this);
      Bukkit.getPluginManager().registerEvents(this.staffService, this);
      Bukkit.getPluginManager().registerEvents(this.tebexBridge, this);
      Bukkit.getPluginManager().registerEvents(this.hudService, this);
      Bukkit.getPluginManager().registerEvents(this.combatHud, this);
      Bukkit.getPluginManager().registerEvents(this.cosmetics, this);
      Bukkit.getPluginManager().registerEvents(this.cosmeticMenu, this);
      Bukkit.getPluginManager().registerEvents(this.villagerService, this);
      Bukkit.getPluginManager().registerEvents(this.weaponRegistry, this);
      this.spawnCommand = new SpawnCommand(this);
      Bukkit.getPluginManager().registerEvents(this.spawnCommand, this);
      this.packHost = new PackHost(this);
      this.packHost.start();
      Bukkit.getPluginManager().registerEvents(this.packHost, this);
      this.registerCommands();
      this.tasks.global(() -> {
         int var1x = this.combatHud.sweepLeftovers();
         if (var1x > 0) {
            this.getSLF4JLogger().info("cleared {} leftover damage numbers", var1x);
         }

         if (this.weaponRegistry.get("aegis-shield") instanceof AegisShield var2x) {
            int var5 = var2x.ward().sweepLeftovers();
            if (var5 > 0) {
               this.getSLF4JLogger().info("cleared {} leftover ward panels", var5);
            }
         }

         if (this.weaponRegistry.get("tether-trident") instanceof TetherTrident var4) {
            int var7 = var4.chain().sweepLeftovers();
            if (var7 > 0) {
               this.getSLF4JLogger().info("cleared {} leftover chain links", var7);
            }
         }

         this.hudService.start();
         this.leaderboards.start();
         this.villagerService.restore();
      });
      int var1 = this.configs.main().getInt("tebex.ledger-retention-days", 365);
      this.async(() -> {
         int var2x = this.keyStore.pruneLedger(var1);
         if (var2x > 0) {
            this.getSLF4JLogger().info("pruned {} key ledger rows older than {} days", var2x, var1);
         }
      });
      this.getSLF4JLogger().info("FlameFFACore enabled");
   }

   public void onDisable() {
      if (this.packHost != null) {
         this.packHost.stop();
      }

      if (this.sweeper != null) {
         this.sweeper.shutdown();
      }

      if (this.leaderboards != null) {
         this.leaderboards.shutdown();
      }

      if (this.combatHud != null) {
         this.combatHud.shutdown();
      }

      if (this.weaponRegistry != null && this.weaponRegistry.get("aegis-shield") instanceof AegisShield var1) {
         var1.ward().shutdown();
      }

      if (this.weaponRegistry != null && this.weaponRegistry.get("tether-trident") instanceof TetherTrident var4) {
         var4.chain().shutdown();
      }

      if (this.weaponRegistry != null && this.weaponRegistry.get("stun-mace") instanceof StunMace var5) {
         var5.shutdown();
      }

      if (this.kitMenu != null) {
         this.kitMenu.shutdown();
      }

      if (this.hudService != null) {
         this.hudService.shutdown();
      }

      if (this.dbExecutor != null) {
         this.dbExecutor.shutdown();

         try {
            this.dbExecutor.awaitTermination(5L, TimeUnit.SECONDS);
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
         }
      }

      if (this.tasks != null) {
         this.tasks.cancelAll();
      }

      if (this.configs != null) {
         this.configs.flushWrites();
      }

      if (this.database != null) {
         this.database.close();
      }
   }

   private void registerCommands() {
      this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, var1 -> {
         Commands var2 = (Commands)var1.registrar();
         var2.register("kit", "open the kit menu or equip a kit", List.of("kits"), new KitCommand(this));
         var2.register("rankkit", "set the kit for a rank from your inventory", List.of("setkit"), new RankKitCommand(this));
         var2.register("crate", "key balances, buy with tokens, open crates", List.of("keys", "crates"), new CrateCommand(this));
         var2.register("staff", "staff tools", List.of("ffa"), new StaffCommand(this));
         var2.register("ffaadmin", "console grant and admin tools", List.of("flameffacore"), new AdminCommand(this));
         var2.register("villager", "trader villager presets and spawning", List.of("trader", "npc"), new VillagerCommand(this));
         var2.register("spawn", "go back up to the safezone", List.of("hub", "lobby"), this.spawnCommand);
         var2.register("safezone", "set the no pvp spawn area", List.of("spawnzone"), new SafezoneCommand(this));
         var2.register("cosmetics", "pick your kill effect, trail and aura", List.of("cosmetic", "cc"), new CosmeticsCommand(this));
         var2.register("booster", "give or take the discord booster rank", List.of("boost"), new BoosterCommand(this));
         var2.register("stats", "your kills, deaths and damage", List.of("stat", "profile"), new StatsCommand(this));
         var2.register("leaderboard", "place a standing top ten", List.of("lb", "top"), new LeaderboardCommand(this));
      });
   }

   public void reloadAll() {
      this.configs.load();
      this.crateRegistry.load();
      this.crateRegistry.loadPlacements();
      this.rankService.load();
      this.safezone.load();
      this.afkWatcher.load();
      this.sweeper.start();
      this.antiCheat.load();
      this.combatHud.load();
      this.cooldownBar.load();
      this.cosmetics.load();
      this.staffService.load();
      this.chatGuard.load();
      this.kitService.load();
      this.crateService.load();
      this.villagerService.load();
      if (this.leaderboards != null) {
         this.leaderboards.start();
      }

      if (this.hudService != null) {
         this.hudService.refreshCrates();
         this.hudService.refreshTabList();
         this.villagerService.restore();
      }
   }

   public ConfigManager configs() {
      return this.configs;
   }

   public Database database() {
      return this.database;
   }

   public KeyStore keys() {
      return this.keyStore;
   }

   public KitCooldownStore kitCooldowns() {
      return this.kitCooldowns;
   }

   public PlayerStore players() {
      return this.playerStore;
   }

   public AuditStore audit() {
      return this.auditStore;
   }

   public PendingStore pending() {
      return this.pendingStore;
   }

   public TxnStore txns() {
      return this.txnStore;
   }

   public ItemRegistry items() {
      return this.itemRegistry;
   }

   public RankService ranks() {
      return this.rankService;
   }

   public BlobStore blobs() {
      return this.blobStore;
   }

   public KitService kits() {
      return this.kitService;
   }

   public KitMenu kitMenu() {
      return this.kitMenu;
   }

   public CrateBlockStore crateBlocks() {
      return this.crateBlockStore;
   }

   public CrateRegistry crateRegistry() {
      return this.crateRegistry;
   }

   public CrateService crates() {
      return this.crateService;
   }

   public StaffService staff() {
      return this.staffService;
   }

   public FreezeService freeze() {
      return this.freezeService;
   }

   public ArenaSweeper sweeper() {
      return this.sweeper;
   }

   public ArenaService arena() {
      return this.arenaService;
   }

   public Safezone safezone() {
      return this.safezone;
   }

   public AfkWatcher afk() {
      return this.afkWatcher;
   }

   public AntiCheat antiCheat() {
      return this.antiCheat;
   }

   public CooldownBar cooldownBar() {
      return this.cooldownBar;
   }

   public CombatHud combatHud() {
      return this.combatHud;
   }

   public LeaderboardService leaderboards() {
      return this.leaderboards;
   }

   public CosmeticStore cosmeticStore() {
      return this.cosmeticStore;
   }

   public CosmeticService cosmetics() {
      return this.cosmetics;
   }

   public CosmeticMenu cosmeticMenu() {
      return this.cosmeticMenu;
   }

   public ChatGuard chat() {
      return this.chatGuard;
   }

   public TebexBridge tebex() {
      return this.tebexBridge;
   }

   public HudService hud() {
      return this.hudService;
   }

   public VillagerStore villagerStore() {
      return this.villagerStore;
   }

   public VillagerService villagers() {
      return this.villagerService;
   }

   public WeaponRegistry weapons() {
      return this.weaponRegistry;
   }

   public Cooldowns cooldowns() {
      return this.cooldowns;
   }

   public void async(Runnable var1) {
      if (this.dbExecutor != null && !this.dbExecutor.isShutdown()) {
         this.dbExecutor.execute(() -> {
            try {
               var1.run();
            } catch (Exception var3) {
               this.getSLF4JLogger().error("async task failed", var3);
            }
         });
      }
   }

   public Tasks tasks() {
      return this.tasks;
   }

   public void sync(Runnable action) {
      this.tasks.global(action);
   }

   public void sync(Entity entity, Runnable action) {
      this.tasks.entity(entity, action);
   }
}
