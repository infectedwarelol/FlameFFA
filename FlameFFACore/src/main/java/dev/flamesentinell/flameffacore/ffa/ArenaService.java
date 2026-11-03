package dev.flamesentinell.flameffacore.ffa;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Tasks;
import dev.flamesentinell.flameffacore.item.ItemKeys;
import dev.flamesentinell.flameffacore.storage.BlobStore;
import dev.flamesentinell.flameffacore.util.Bots;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

public final class ArenaService implements Listener {
   private static final String COMBAT = "combat";
   private static final String SPAWN_PROTECTION = "spawnprot";
   private static final String SAFEZONE_WARNED = "szwarn";
   private final FlameFFACore plugin;
   private final Map<UUID, Integer> streaks = new ConcurrentHashMap<>();
   private final Map<UUID, Set<UUID>> taggedBy = new ConcurrentHashMap<>();

   public ArenaService(FlameFFACore var1) {
      this.plugin = var1;
      this.loadStreaks();
      EnderVault.start(var1);
      var1.async(this::upgradeDashSpears);
   }

   private void upgradeDashSpears() {
      int var1 = 0;

      for (String var5 : new String[]{"rankkit", "crateloot", "villager"}) {
         for (Entry var7 : this.plugin.blobs().loadAll(var5).entrySet()) {
            ItemStack[] var8 = ((BlobStore.Blob)var7.getValue()).contents();
            boolean var9 = false;

            for (ItemStack var13 : var8) {
               if (var13 != null && var13.hasItemMeta()) {
                  String var14 = (String)var13.getItemMeta().getPersistentDataContainer().get(ItemKeys.WEAPON, PersistentDataType.STRING);
                  if ("dash-spear".equals(var14) && var13.getEnchantmentLevel(Enchantment.LUNGE) < 3) {
                     var13.addUnsafeEnchantment(Enchantment.LUNGE, 3);
                     var9 = true;
                     var1++;
                  }
               }
            }

            if (var9) {
               this.plugin.blobs().save(var5, (String)var7.getKey(), var8, "dash spear lunge");
            }
         }
      }

      if (var1 > 0) {
         this.plugin.kits().load();
         this.plugin.crates().load();
         this.plugin.villagers().load();
         this.plugin.getLogger().info("gave lunge iii to " + var1 + " stored dash spears");
      }
   }

   private File streakFile() {
      return new File(this.plugin.getDataFolder(), "streaks.yml");
   }

   private void loadStreaks() {
      File var1 = this.streakFile();
      if (var1.exists()) {
         YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);

         for (String var4 : var2.getKeys(false)) {
            try {
               int var5 = var2.getInt(var4);
               if (var5 > 0) {
                  this.streaks.put(UUID.fromString(var4), var5);
               }
            } catch (IllegalArgumentException var6) {
            }
         }
      }
   }

   private void saveStreaks() {
      YamlConfiguration var1 = new YamlConfiguration();
      this.streaks.forEach((var1x, var2) -> {
         if (var2 > 0) {
            var1.set(var1x.toString(), var2);
         }
      });

      this.plugin.configs().saveAsync(var1, this.streakFile());
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onStackPickup(PlayerAttemptPickupItemEvent var1) {
      ItemStack var2 = var1.getItem().getItemStack();
      if (var2.getMaxStackSize() > 1 && !this.plugin.items().isKitItem(var2) && !this.plugin.items().isCurrency(var2)) {
         PlayerInventory var3 = var1.getPlayer().getInventory();

         for (int var4 = 0; var4 < var3.getSize(); var4++) {
            ItemStack var5 = var3.getItem(var4);
            if (var5 != null && this.plugin.items().isKitItem(var5)) {
               ItemStack var6 = this.plugin.items().asTemplate(var5);
               if (var6.isSimilar(var2)) {
                  var3.setItem(var4, var6);
               }
            }
         }
      }
   }

   @EventHandler
   public void onStreakDisable(PluginDisableEvent var1) {
      if (var1.getPlugin() == this.plugin) {
         this.saveStreaks();
      }
   }

   public boolean inCombat(Player var1) {
      return this.plugin.cooldowns().active(var1.getUniqueId(), "combat");
   }

   public int streak(UUID var1) {
      return this.streaks.getOrDefault(var1, 0);
   }

   public void tag(Player var1, Player var2) {
      if (!Bots.involved(var1) && !Bots.involved(var2)) {
         int var3 = this.plugin.configs().main().getInt("arena.combat-tag-seconds", 25);
         boolean var4 = !this.inCombat(var1);
         this.plugin.cooldowns().set(var1.getUniqueId(), "combat", var3);
         if (var4) {
            this.taggedBy.remove(var1.getUniqueId());
         }

         this.taggedBy.computeIfAbsent(var1.getUniqueId(), var0 -> ConcurrentHashMap.newKeySet()).add(var2.getUniqueId());
         if (var4) {
            var1.sendMessage(this.plugin.configs().msg("arena.combat-tagged", "seconds", String.valueOf(var3)));
            this.plugin.tasks().entityLater(var1, () -> {
               if (var1.isOnline() && !this.inCombat(var1)) {
                  this.taggedBy.remove(var1.getUniqueId());
                  var1.sendMessage(this.plugin.configs().msg("arena.combat-over"));
               }
            }, var3 * 20L + 10L);
         }
      }
   }

   public int forgive(Player var1) {
      UUID var2 = var1.getUniqueId();
      int var3 = 0;

      for (Entry var5 : this.taggedBy.entrySet()) {
         UUID var6 = (UUID)var5.getKey();
         if (!var6.equals(var2) && ((Set)var5.getValue()).remove(var2) && ((Set)var5.getValue()).isEmpty()) {
            this.taggedBy.remove(var6);
            Player var7 = Bukkit.getPlayer(var6);
            if (var7 != null && this.inCombat(var7)) {
               this.plugin.cooldowns().clear(var6, "combat");
               var7.sendMessage(this.plugin.configs().msg("arena.combat-forgiven", "player", var1.getName()));
               var3++;
            }
         }
      }

      return var3;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent var1) {
      String var2 = this.plugin.configs().main().getString("arena.forgive-phrase", "mb all");
      if (var2 != null && !var2.isBlank()) {
         String var3 = PlainTextComponentSerializer.plainText().serialize(var1.message()).trim();
         if (var3.equalsIgnoreCase(var2.trim())) {
            Player var4 = var1.getPlayer();
            this.plugin.sync(var4, () -> {
               int var2x = this.forgive(var4);
               var4.sendMessage(this.plugin.configs().msg(var2x > 0 ? "arena.forgave" : "arena.forgave-none", "count", String.valueOf(var2x)));
            });
         }
      }
   }

   public Location spawn() {
      ConfigurationSection var1 = this.plugin.configs().main().getConfigurationSection("arena.spawn");
      World var2 = Bukkit.getWorld(this.plugin.configs().main().getString("arena.world", "world"));
      if (var2 == null) {
         var2 = (World)Bukkit.getWorlds().get(0);
      }

      return var1 == null
         ? var2.getSpawnLocation()
         : new Location(var2, var1.getDouble("x"), var1.getDouble("y"), var1.getDouble("z"), (float)var1.getDouble("yaw"), (float)var1.getDouble("pitch"));
   }

   public boolean inSafezone(Location var1) {
      return this.plugin.safezone().contains(var1);
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      Player var2 = var1.getPlayer();
      if (!Bots.is(var2)) {
         Tasks.move(var2, this.spawn());
         this.plugin.cooldowns().set(var2.getUniqueId(), "spawnprot", this.plugin.configs().main().getInt("arena.spawn-protection-seconds", 5));
         this.plugin.async(() -> {
            this.plugin.kitCooldowns().load(var2.getUniqueId());
            this.plugin.keys().loadCache(var2.getUniqueId());
         });
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      Player var2 = var1.getPlayer();
      if (!Bots.involved(var2)) {
         UUID var3 = var2.getUniqueId();
         this.plugin.sync(this::saveStreaks);
         this.taggedBy.remove(var3);
         this.plugin.safezone().forget(var3);
         boolean var4 = false;
         if (this.inCombat(var2) && this.plugin.configs().main().getBoolean("arena.combat-log-kill", true)) {
            var2.setHealth(0.0);
            Bukkit.broadcast(this.plugin.configs().msg("arena.combat-log", "player", var2.getName()));
            var4 = true;
         }

         if (var4 || this.plugin.configs().main().getBoolean("arena.wipe-kit-on-quit", false)) {
            this.plugin.kits().wipeKitItems(var2);
         }

         this.plugin.cooldowns().clearAll(var3);
         this.plugin.kitCooldowns().forget(var3);
         this.plugin.keys().forget(var3);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent var1) {
      if (var1.getEntity() instanceof Player var2) {
         Player var4 = this.resolveAttacker(var1);
         if (var4 != null) {
            if (!this.inSafezone(var2.getLocation()) && !this.inSafezone(var4.getLocation())) {
               if (this.plugin.cooldowns().active(var2.getUniqueId(), "spawnprot")) {
                  var1.setCancelled(true);
               } else {
                  this.plugin.cooldowns().clear(var4.getUniqueId(), "spawnprot");
                  this.tag(var2, var4);
                  this.tag(var4, var2);
               }
            } else {
               var1.setCancelled(true);
               if (!this.plugin.cooldowns().active(var4.getUniqueId(), "szwarn")) {
                  this.plugin.cooldowns().set(var4.getUniqueId(), "szwarn", this.plugin.configs().main().getInt("arena.safezone-warning-seconds", 10));
                  var4.sendMessage(this.plugin.configs().msg("arena.safezone"));
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onKnockback(EntityKnockbackEvent var1) {
      if (var1.getEntity() instanceof Player var2 && this.inSafezone(var2.getLocation())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDeath(PlayerDeathEvent var1) {
      Player var2 = var1.getPlayer();
      if (!Bots.is(var2)) {
         this.plugin.cooldowns().clear(var2.getUniqueId(), "combat");
         this.taggedBy.remove(var2.getUniqueId());
      }

      if (!Bots.involved(var2) && !Bots.involved(var2.getKiller())) {
         UUID var3 = var2.getUniqueId();
         List<ItemStack> var4 = var1.getDrops();

         for (int var5 = 0; var5 < var4.size(); var5++) {
            ItemStack var6 = (ItemStack)var4.get(var5);
            if (!this.plugin.items().dropsOnDeath(var6)) {
               var4.set(var5, null);
            } else {
               var4.set(var5, this.plugin.items().releaseKitItem(var6));
            }
         }

         var4.removeIf(var0 -> var0 == null || var0.getType().isAir());
         int var11 = this.streaks.getOrDefault(var3, 0);
         this.streaks.remove(var3);
         this.taggedBy.remove(var3);
         this.plugin.cooldowns().clear(var3, "combat");
         this.plugin.async(() -> this.plugin.players().addDeath(var3));
         this.plugin.hud().onDeath(var2);
         Player var12 = var2.getKiller();
         if (var12 == null) {
            var1.deathMessage(this.plugin.configs().msg("arena.death-alone", "victim", var2.getName()));
         } else {
            var1.deathMessage(this.plugin.configs().msg("arena.death", "victim", var2.getName(), "killer", var12.getName()));
            int var7 = this.streaks.merge(var12.getUniqueId(), 1, Integer::sum);
            this.plugin.async(() -> this.plugin.players().addKill(var12.getUniqueId(), var7));
            this.plugin.hud().onKill(var12, var7);
            this.plugin.cosmetics().onKill(var12, var2.getLocation());
            int var8 = this.plugin.configs().main().getInt("tokens.per-kill", 1);
            this.plugin.tasks().runOn(var12, () -> {
               if (var8 > 0) {
                  for (ItemStack var10 : var12.getInventory().addItem(new ItemStack[]{this.plugin.items().mintKillTokens(var8)}).values()) {
                     var12.getWorld().dropItemNaturally(var12.getLocation(), var10);
                  }
               }

               if (!var12.isDead()) {
                  var12.setHealth(Math.min(var12.getMaxHealth(), var12.getHealth() + 4.0));
               }
            });
            if (var11 >= 5) {
               Bukkit.broadcast(
                  this.plugin.configs().msg("arena.streak-ended", "killer", var12.getName(), "victim", var2.getName(), "streak", String.valueOf(var11))
               );
            }

            this.announceStreak(var12, var7);
            this.rewardStreak(var12, var7);
         }
      }
   }

   @EventHandler
   public void onRespawn(PlayerRespawnEvent var1) {
      Player var2 = var1.getPlayer();
      this.plugin.cooldowns().clear(var2.getUniqueId(), "combat");
      this.taggedBy.remove(var2.getUniqueId());
      if (!Bots.involved(var2)) {
         var1.setRespawnLocation(this.spawn());
         this.plugin.cooldowns().set(var2.getUniqueId(), "spawnprot", this.plugin.configs().main().getInt("arena.spawn-protection-seconds", 5));
         var2.sendMessage(this.plugin.configs().msg("kit.respawn-empty"));
      }
   }

   private boolean announced(int var1) {
      return this.plugin.configs().main().getIntegerList("killstreaks.announce-at").contains(var1) || this.huge(var1);
   }

   private boolean huge(int var1) {
      int var2 = this.plugin.configs().main().getInt("killstreaks.big-every", 100);
      return var2 > 0 && var1 >= var2 && var1 % var2 == 0;
   }

   private void announceStreak(Player var1, int var2) {
      if (this.plugin.configs().main().getBoolean("killstreaks.enabled", true) && this.announced(var2)) {
         String[] var3 = new String[]{"player", var1.getName(), "streak", String.valueOf(var2)};
         if (!this.huge(var2)) {
            Bukkit.broadcast(this.plugin.configs().msg("arena.killstreak", var3));
            return;
         }

         Bukkit.broadcast(this.plugin.configs().msg("arena.killstreak-huge", var3));
         Title var4 = Title.title(
            this.plugin.configs().msg("arena.killstreak-huge-title", var3),
            this.plugin.configs().msg("arena.killstreak-huge-subtitle", var3),
            Times.times(Duration.ofMillis(250L), Duration.ofMillis(2500L), Duration.ofMillis(750L))
         );

         for (Player var6 : Bukkit.getOnlinePlayers()) {
            var6.showTitle(var4);
            var6.playSound(var6.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
            var6.playSound(var6.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5F, 1.2F);
         }
      }
   }

   private void rewardStreak(Player var1, int var2) {
      if (this.plugin.configs().main().getBoolean("killstreaks.enabled", true)) {
         ConfigurationSection var3 = this.plugin.configs().main().getConfigurationSection("killstreaks.rewards." + var2);
         if (var3 != null) {
            if (var3.getBoolean("broadcast", false) && !this.announced(var2)) {
               Bukkit.broadcast(this.plugin.configs().msg("arena.killstreak", "player", var1.getName(), "streak", String.valueOf(var2)));
            }

            ConfigurationSection var4 = var3.getConfigurationSection("keys");
            if (var4 != null) {
               UUID var5 = var1.getUniqueId();

               for (String var7 : var4.getKeys(false)) {
                  int var8 = var4.getInt(var7);
                  this.plugin.async(() -> this.plugin.keys().grant(var5, var7, var8, "killstreak", "server", null));
               }
            }
         }
      }
   }

   private Player resolveAttacker(EntityDamageByEntityEvent var1) {
      if (var1.getDamager() instanceof Player var2) {
         return var2;
      } else {
         return var1.getDamager() instanceof Projectile var4 && var4.getShooter() instanceof Player var6 ? var6 : null;
      }
   }
}
