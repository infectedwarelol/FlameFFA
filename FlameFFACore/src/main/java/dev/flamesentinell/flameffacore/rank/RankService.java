package dev.flamesentinell.flameffacore.rank;

import java.util.concurrent.ConcurrentHashMap;
import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.util.Bots;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.InheritanceNode.Builder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class RankService {
   private final FlameFFACore plugin;
   private volatile Map<String, Rank> ranks = Map.of();
   private volatile Set<String> hidden = Set.of();
   private volatile Set<String> noKit = Set.of();
   private LuckPerms luckPerms;

   public RankService(FlameFFACore var1) {
      this.plugin = var1;
      this.load();
   }

   public void load() {
      Map<String, Rank> loadedRanks = new LinkedHashMap<>();
      Set<String> loadedHidden = ConcurrentHashMap.newKeySet();
      Set<String> loadedNoKit = ConcurrentHashMap.newKeySet();
      ConfigurationSection var1 = this.plugin.configs().ranks().getConfigurationSection("ranks");
      if (var1 != null) {
         for (String var3 : var1.getKeys(false)) {
            ConfigurationSection var4 = var1.getConfigurationSection(var3);
            if (var4 != null) {
               if (var4.getBoolean("hidden", false)) {
                  loadedHidden.add(var3);
               }

               if (!var4.getBoolean("kit", true)) {
                  loadedNoKit.add(var3);
               }

               Material var5 = Material.matchMaterial(var4.getString("icon", "IRON_SPEAR"));
               int var6 = this.plugin.configs().main().getInt("arena.kit-cooldown-seconds", 0);
               loadedRanks
                  .put(
                     var3,
                     new Rank(
                        var3,
                        var4.getString("group", var3),
                        var4.getInt("weight", 0),
                        var4.getString("display", var3),
                        var5 == null ? Material.PAPER : var5,
                        Math.max(0, var4.getInt("cooldown-seconds", var6)),
                        var4.getBoolean("perk", false),
                        var4.getString("locked-hint")
                     )
                  );
            }
         }
      }

      this.ranks = Collections.unmodifiableMap(loadedRanks);
      this.hidden = loadedHidden;
      this.noKit = loadedNoKit;
   }

   private LuckPerms lp() {
      if (this.luckPerms == null) {
         this.luckPerms = LuckPermsProvider.get();
      }

      return this.luckPerms;
   }

   public Rank rank(String var1) {
      return this.ranks.get(var1);
   }

   public boolean isHidden(String var1) {
      return this.hidden.contains(var1);
   }

   public boolean hasKit(String var1) {
      return !this.noKit.contains(var1);
   }

   public Map<String, Rank> all() {
      return this.ranks;
   }

   public List<Rank> ordered() {
      ArrayList var1 = new ArrayList<>(this.ranks.values());
      var1.sort(Comparator.comparingInt(Rank::weight));
      return var1;
   }

   public Rank highest(Player var1) {
      Rank var2 = this.ranks.get("default");
      if (Bots.is(var1)) {
         return var2;
      } else {
         User var3 = this.lp().getUserManager().getUser(var1.getUniqueId());
         if (var3 == null) {
            return var2;
         } else {
            List var4 = var3.getInheritedGroups(var3.getQueryOptions()).stream().map(var0 -> var0.getName()).toList();
            Rank var5 = this.ranks.get("default");

            for (Rank var7 : this.ranks.values()) {
               if (!this.hidden.contains(var7.id()) && var4.contains(var7.group()) && (var5 == null || var7.weight() > var5.weight())) {
                  var5 = var7;
               }
            }

            return var5;
         }
      }
   }

   public List<String> unlockedRanks(Player var1) {
      ArrayList var2 = new ArrayList();
      Rank var3 = this.highest(var1);
      int var4 = var3 == null ? 0 : var3.weight();

      for (Rank var6 : this.ordered()) {
         if (var6.weight() <= var4 && !this.hidden.contains(var6.id())) {
            var2.add(var6.id());
         }
      }

      if (!this.hidden.isEmpty() && !Bots.is(var1)) {
         User var10 = this.lp().getUserManager().getUser(var1.getUniqueId());
         if (var10 != null) {
            List var12 = var10.getInheritedGroups(var10.getQueryOptions()).stream().map(var0 -> var0.getName()).toList();

            for (String var8 : this.hidden) {
               Rank var9 = this.ranks.get(var8);
               if (var9 != null && !var2.contains(var8) && var12.contains(var9.group())) {
                  var2.add(var8);
               }
            }
         }
      }

      for (Rank var13 : this.ranks.values()) {
         if (!var2.contains(var13.id()) && var1.hasPermission("flameffacore.kit." + var13.id())) {
            var2.add(var13.id());
         }
      }

      var2.removeIf(this.noKit::contains);
      return var2;
   }

   public CompletableFuture<Boolean> grant(UUID var1, String var2, int var3) {
      Rank var4 = this.ranks.get(var2);
      if (var4 == null) {
         return CompletableFuture.completedFuture(false);
      } else {
         boolean var5 = this.plugin.configs().main().getBoolean("tebex.stack-renewals", true);
         return this.lp()
            .getUserManager()
            .modifyUser(
               var1,
               var4x -> {
                  Instant var5x = null;
                  if (var5 && var3 > 0) {
                     for (Node var7 : var4x.data().toCollection()) {
                        if (var7 instanceof InheritanceNode var8
                           && var8.getGroupName().equalsIgnoreCase(var4.group())
                           && var7.getExpiry() != null
                           && (var5x == null || var7.getExpiry().isAfter(var5x))) {
                           var5x = var7.getExpiry();
                        }
                     }
                  }

                  if (!var4.perk()) {
                     var4x.data()
                        .clear(
                           NodeType.INHERITANCE
                              .predicate(
                                 var1xx -> this.ranks
                                       .values()
                                       .stream()
                                       .anyMatch(var1xxx -> !var1xxx.perk() && var1xxx.group().equalsIgnoreCase(var1xx.getGroupName()))
                                    && !"default".equalsIgnoreCase(var1xx.getGroupName())
                              )
                        );
                  }

                  Builder var9 = InheritanceNode.builder(var4.group());
                  if (var3 > 0) {
                     Instant var10 = var5x != null && var5x.isAfter(Instant.now()) ? var5x : Instant.now();
                     var9.expiry(var10.plus(Duration.ofDays(var3)));
                  }

                  var4x.data().add(var9.build());
               }
            )
            .thenApply(var0 -> true);
      }
   }

   public CompletableFuture<Boolean> revoke(UUID var1, String var2) {
      Rank var3 = this.ranks.get(var2);
      return var3 == null
         ? CompletableFuture.completedFuture(false)
         : this.lp()
            .getUserManager()
            .modifyUser(var1, var1x -> var1x.data().clear(NodeType.INHERITANCE.predicate(var1xx -> var1xx.getGroupName().equalsIgnoreCase(var3.group()))))
            .thenApply(var0 -> true);
   }
}
