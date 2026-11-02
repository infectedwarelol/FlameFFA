package dev.flamesentinell.flameffacore.command;

import dev.flamesentinell.flameffacore.FlameFFACore;
import dev.flamesentinell.flameffacore.cosmetic.CosmeticService;
import dev.flamesentinell.flameffacore.crate.Crate;
import dev.flamesentinell.flameffacore.item.ItemKeys;
import dev.flamesentinell.flameffacore.kit.KitService;
import dev.flamesentinell.flameffacore.rank.Rank;
import dev.flamesentinell.flameffacore.staff.StaffRole;
import dev.flamesentinell.flameffacore.storage.BlobStore;
import dev.flamesentinell.flameffacore.weapon.CustomWeapon;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public final class AdminCommand implements BasicCommand {
   private static final String USAGE = "/ffaadmin grant key <player> <crate> <amount> [txn] | grant rank <player> <rank> <days> [txn] | revoke rank <player> <rank> | resetkit <player> [rank] | mint token|compressed <amount> [player] | mint weapon <id> [player] | reload | verify | testfx <weapon> [x y z] | testkill <cosmetic> [x y z] | sysinfo | setspawn";
   private final FlameFFACore plugin;

   public AdminCommand(FlameFFACore plugin) {
      this.plugin = plugin;
   }

   public boolean canUse(@NotNull CommandSender sender) {
      return this.plugin.staff().role(sender).atLeast(StaffRole.ADMIN);
   }

   public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
      CommandSender sender = source.getSender();
      if (!this.plugin.staff().role(sender).atLeast(StaffRole.ADMIN)) {
         sender.sendMessage(this.plugin.configs().msg("general.no-permission"));
      } else if (args.length == 0) {
         sender.sendMessage(
            this.plugin
               .configs()
               .msg(
                  "general.usage",
                  "usage",
                  "/ffaadmin grant key <player> <crate> <amount> [txn] | grant rank <player> <rank> <days> [txn] | revoke rank <player> <rank> | resetkit <player> [rank] | mint token|compressed <amount> [player] | mint weapon <id> [player] | reload | verify | testfx <weapon> [x y z] | testkill <cosmetic> [x y z] | sysinfo | setspawn"
               )
         );
      } else {
         String var4 = args[0].toLowerCase(Locale.ROOT);
         switch (var4) {
            case "grant":
               this.grant(sender, args);
               break;
            case "revoke":
               this.revoke(sender, args);
               break;
            case "resetkit":
               this.resetKit(sender, args);
               break;
            case "mint":
               this.mint(sender, args);
               break;
            case "testfx":
               this.testFx(sender, args);
               break;
            case "testkill":
               this.testKill(sender, args);
               break;
            case "sysinfo":
               this.sysInfo(sender);
               break;
            case "setspawn":
               this.setSpawn(sender);
               break;
            case "reload":
               this.plugin.reloadAll();
               sender.sendMessage(this.plugin.configs().msg("general.reloaded"));
               break;
            case "verify":
               this.verify(sender);
               break;
            default:
               sender.sendMessage(
                  this.plugin
                     .configs()
                     .msg(
                        "general.usage",
                        "usage",
                        "/ffaadmin grant key <player> <crate> <amount> [txn] | grant rank <player> <rank> <days> [txn] | revoke rank <player> <rank> | resetkit <player> [rank] | mint token|compressed <amount> [player] | mint weapon <id> [player] | reload | verify | testfx <weapon> [x y z] | testkill <cosmetic> [x y z] | sysinfo | setspawn"
                     )
               );
         }
      }
   }

   private void setSpawn(CommandSender sender) {
      if (sender instanceof Player player) {
         Location at = player.getLocation();
         FileConfiguration cfg = this.plugin.configs().main();
         cfg.set("arena.world", at.getWorld().getName());
         cfg.set("arena.spawn.x", Math.round(at.getX() * 100.0) / 100.0);
         cfg.set("arena.spawn.y", Math.round(at.getY() * 100.0) / 100.0);
         cfg.set("arena.spawn.z", Math.round(at.getZ() * 100.0) / 100.0);
         cfg.set("arena.spawn.yaw", Math.round(at.getYaw()));
         cfg.set("arena.spawn.pitch", Math.round(at.getPitch()));

         try {
            cfg.save(new File(this.plugin.getDataFolder(), "config.yml"));
         } catch (IOException var6) {
            this.plugin.getSLF4JLogger().error("could not save config.yml", var6);
            sender.sendMessage(this.plugin.configs().msg("spawn.save-failed"));
            return;
         }

         this.audit(sender, "setspawn", at.getWorld().getName(), at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ());
         sender.sendMessage(
            this.plugin
               .configs()
               .msg("spawn.set", "x", String.valueOf(at.getBlockX()), "y", String.valueOf(at.getBlockY()), "z", String.valueOf(at.getBlockZ()))
         );
      } else {
         sender.sendMessage(this.plugin.configs().msg("general.players-only"));
      }
   }

   private void verify(CommandSender sender) {
      List<String> bad = new ArrayList<>();
      List<Rank> ladder = this.plugin.ranks().ordered();

      for (Rank rank : ladder) {
         if (!this.plugin.kits().isSet(rank.id())) {
            bad.add("rank " + rank.id() + " has no kit set, use /rankkit set " + rank.id());
         }
      }

      if (!ladder.isEmpty() && ladder.get(0).cooldownSeconds() > 0) {
         bad.add("the base rank " + ladder.get(0).id() + " has a cooldown, players would respawn with nothing, set cooldown-seconds to 0 in ranks.yml");
      }

      if (!this.plugin.safezone().isSet()) {
         bad.add("no safezone set, nobody can take a kit and pvp is on at spawn, use /safezone");
      }

      for (Crate crate : this.plugin.crateRegistry().all().values()) {
         if (!this.plugin.crates().hasLoot(crate.id())) {
            bad.add("crate " + crate.id() + " has no loot set, use /crate " + crate.id() + " set");
         }

         if (this.plugin.crateRegistry().placedCount(crate.id()) == 0) {
            bad.add("crate " + crate.id() + " is not placed anywhere, use /crate place " + crate.id());
         }
      }

      for (Crate crate : this.plugin.crateRegistry().all().values()) {
         if (this.plugin.crates().hasLoot(crate.id())) {
            List<ItemStack> before = this.plugin.crates().pool(crate.id());
            int countBefore = before.size();
            int itemsBefore = before.stream().mapToInt(ItemStack::getAmount).sum();

            for (int i = 0; i < 16; i++) {
               this.plugin.crates().peekRoll(crate.id());
            }

            List<ItemStack> after = this.plugin.crates().pool(crate.id());
            int itemsAfter = after.stream().mapToInt(ItemStack::getAmount).sum();
            if (after.size() != countBefore || itemsAfter != itemsBefore) {
               bad.add(
                  "crate "
                     + crate.id()
                     + " loses loot when rolled, entries must survive every open ("
                     + countBefore
                     + "/"
                     + itemsBefore
                     + " -> "
                     + after.size()
                     + "/"
                     + itemsAfter
                     + ")"
               );
            }

            ItemStack sample = this.plugin.crates().peekRoll(crate.id());
            if (sample != null) {
               boolean matched = before.stream().anyMatch(e -> e.getType() == sample.getType() && e.getAmount() == sample.getAmount());
               if (!matched) {
                  bad.add("crate " + crate.id() + " changed a reward's stack size on the way out");
               }
            }
         }
      }

      String probeId = "__verify__";
      ItemStack[] probePool = new ItemStack[54];
      probePool[0] = ItemStack.of(Material.ARROW, 6);
      probePool[1] = ItemStack.of(Material.DIAMOND, 1);
      this.plugin.crates().installProbePool(probeId, probePool);

      try {
         boolean sawStack = false;

         for (int i = 0; i < 64; i++) {
            ItemStack rolled = this.plugin.crates().peekRoll(probeId);
            if (rolled != null && rolled.getType() == Material.ARROW) {
               sawStack = true;
               if (rolled.getAmount() != 6) {
                  bad.add("a crate slot holding 6 paid out " + rolled.getAmount());
                  break;
               }
            }
         }

         if (!sawStack) {
            bad.add("crate rolling never returned the stacked entry");
         }

         List<ItemStack> left = this.plugin.crates().pool(probeId);
         if (left.size() != 2) {
            bad.add("rolling consumed pool entries, a crate must stay winnable forever (2 -> " + left.size() + ")");
         }
      } finally {
         this.plugin.crates().dropProbePool(probeId);
      }

      try {
         ItemStack[] probe = new ItemStack[41];
         probe[0] = ItemStack.of(Material.NETHERITE_SPEAR, 1);
         probe[1] = ItemStack.of(Material.GOLDEN_APPLE, 64);
         probe[36] = ItemStack.of(Material.NETHERITE_HELMET, 1);
         probe[40] = ItemStack.of(Material.SHIELD, 1);
         ItemStack[] back = BlobStore.decode(BlobStore.encode(probe));
         boolean intact = back.length >= 41
            && back[0] != null
            && back[0].getType() == Material.NETHERITE_SPEAR
            && back[1] != null
            && back[1].getAmount() == 64
            && back[36] != null
            && back[40] != null;
         if (!intact) {
            bad.add("rank kit serialisation round trip lost data");
         }
      } catch (Exception var18) {
         bad.add("rank kit serialisation failed -> " + var18.getMessage());
      }

      ItemStack tokens = this.plugin.items().mintKillTokens(16);
      if (tokens.getAmount() != 16 || tokens.getMaxStackSize() < 16) {
         bad.add("kill tokens do not stack");
      }

      if (!this.plugin.items().dropsOnDeath(tokens)) {
         bad.add("kill tokens are locked, they must drop on death");
      }

      for (String id : this.plugin.weapons().ids()) {
         ItemStack weapon = this.plugin.weapons().mint(id);
         if (weapon != null && this.plugin.weapons().isWeapon(weapon)) {
            if (weapon.getItemMeta() == null || !weapon.getItemMeta().isUnbreakable()) {
               bad.add("weapon " + id + " is breakable, every custom weapon must be unbreakable");
            }

            bad.addAll(this.plugin.weapons().get(id).check(weapon));

            try {
               this.plugin.weapons().get(id).selfTest(this.plugin);
            } catch (RuntimeException var17) {
               bad.add("weapon " + id + " effects fail: " + var17.getMessage());
            }
         } else {
            bad.add("weapon " + id + " does not build or is not tagged");
         }
      }

      ItemStack compressed = this.plugin.items().mintCompressedTokens(16);
      if (compressed.getAmount() != 16 || compressed.getMaxStackSize() < 16 || !this.plugin.items().isCompressedToken(compressed)) {
         bad.add("compressed kill tokens do not stack or do not read back");
      }

      if (!this.plugin.items().dropsOnDeath(compressed)) {
         bad.add("compressed kill tokens are locked, they must drop on death");
      }

      for (Crate cratex : this.plugin.crateRegistry().all().values()) {
         ItemStack voucher = this.plugin.items().mintVoucher(cratex.id());
         if (voucher != null && this.plugin.items().isVoucher(voucher)) {
            if (!cratex.id().equals(this.plugin.items().voucherCrate(voucher))) {
               bad.add("crate " + cratex.id() + " voucher does not read back its crate");
            }

            if (!this.plugin.items().dropsOnDeath(voucher)) {
               bad.add("crate " + cratex.id() + " voucher is locked, it must drop like a token");
            }
         } else {
            bad.add("crate " + cratex.id() + " cannot mint a voucher, check its voucher material");
         }
      }

      ItemStack kitProbe = ItemStack.of(Material.NETHERITE_CHESTPLATE);
      kitProbe.editMeta(meta -> meta.getPersistentDataContainer().set(ItemKeys.KIT, PersistentDataType.BYTE, (byte)1));
      if (this.plugin.configs().main().getBoolean("loot.drop-kit-items", true)) {
         if (!this.plugin.items().dropsOnDeath(kitProbe)) {
            bad.add("kit gear is set to drop but dropsOnDeath says otherwise");
         }

         if (this.plugin.items().isKitItem(this.plugin.items().releaseKitItem(kitProbe))) {
            bad.add("looted kit gear keeps its kit tag, it would be wiped from the looter");
         }
      }

      StringBuilder cd = new StringBuilder();

      for (Rank rankx : ladder) {
         cd.append(cd.isEmpty() ? "" : " ")
            .append(rankx.id())
            .append('=')
            .append(rankx.cooldownSeconds() == 0 ? "free" : KitService.formatTime(rankx.cooldownSeconds()));
      }

      sender.sendMessage(
         Component.text(
            "verify: "
               + this.plugin.kits().all().size()
               + "/"
               + this.plugin.ranks().all().size()
               + " kits set, "
               + this.plugin.crates().pooledCrates()
               + "/"
               + this.plugin.crateRegistry().all().size()
               + " crates stocked, "
               + this.plugin.crateRegistry().placedCount()
               + " placed, "
               + bad.size()
               + " problems"
         )
      );
      sender.sendMessage(Component.text("  kit cooldowns: " + cd));
      sender.sendMessage(Component.text("  cooldown rows held: " + this.plugin.kitCooldowns().prune() + " expired pruned"));

      for (String line : bad) {
         sender.sendMessage(Component.text("  " + line));
      }
   }

   private boolean mayGrant(CommandSender sender, String targetToken) {
      if (this.plugin.staff().isTrusted(sender)) {
         return true;
      } else {
         sender.sendMessage(this.plugin.configs().msg("general.owner-only"));
         return false;
      }
   }

   private void audit(CommandSender sender, String action, String targetToken, String detail) {
      UUID actorId = sender instanceof Player p ? p.getUniqueId() : null;
      String actorName = sender.getName();
      Player online = Bukkit.getPlayerExact(targetToken);
      UUID targetId = online == null ? null : online.getUniqueId();
      this.plugin.async(() -> this.plugin.audit().staff(actorId, actorName, action, targetId, targetToken, detail));
   }

   private void testKill(CommandSender sender, String[] args) {
      if (!this.plugin.staff().isTrusted(sender)) {
         sender.sendMessage(this.plugin.configs().msg("general.owner-only"));
      } else if (args.length < 2) {
         sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/ffaadmin testkill <cosmetic> [x y z]"));
      } else {
         CosmeticService.Cosmetic cosmetic = this.plugin.cosmetics().find("kill", args[1]);
         if (cosmetic == null) {
            StringBuilder ids = new StringBuilder();

            for (CosmeticService.Cosmetic c : this.plugin.cosmetics().catalogue("kill")) {
               ids.append(ids.isEmpty() ? "" : ", ").append(c.id());
            }

            sender.sendMessage(Component.text("kill effects: " + ids));
         } else {
            Location at;
            if (args.length >= 5) {
               try {
                  at = new Location(
                     this.plugin.arena().spawn().getWorld(), Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4])
                  );
               } catch (NumberFormatException var7) {
                  sender.sendMessage(this.plugin.configs().msg("safezone.bad-coords"));
                  return;
               }
            } else if (sender instanceof Player p) {
               at = p.getLocation();
            } else {
               at = this.plugin.arena().spawn();
            }

            this.plugin.cosmetics().demo(sender instanceof Player p2 ? p2 : null, at, cosmetic);
            sender.sendMessage(Component.text("playing " + cosmetic.id() + " at " + (int)at.getX() + " " + (int)at.getY() + " " + (int)at.getZ()));
         }
      }
   }

   private void testFx(CommandSender sender, String[] args) {
      if (!this.plugin.staff().isTrusted(sender)) {
         sender.sendMessage(this.plugin.configs().msg("general.owner-only"));
      } else if (args.length < 2) {
         sender.sendMessage(this.plugin.configs().msg("general.usage", "usage", "/ffaadmin testfx <weapon> [x y z]"));
      } else {
         CustomWeapon weapon = this.plugin.weapons().get(args[1]);
         if (weapon == null) {
            sender.sendMessage(this.plugin.configs().msg("weapon.unknown", "id", args[1]));
         } else {
            Location at;
            if (args.length >= 5) {
               try {
                  at = new Location(
                     this.plugin.arena().spawn().getWorld(), Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4])
                  );
               } catch (NumberFormatException var7) {
                  sender.sendMessage(this.plugin.configs().msg("safezone.bad-coords"));
                  return;
               }
            } else if (sender instanceof Player p) {
               at = p.getLocation().add(0.0, 1.0, 0.0);
            } else {
               at = this.plugin.arena().spawn();
            }

            weapon.demo(this.plugin, at);
            sender.sendMessage(Component.text("firing " + weapon.id() + " at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()));
         }
      }
   }

   private void sysInfo(CommandSender sender) {
      if (!this.plugin.staff().isTrusted(sender)) {
         sender.sendMessage(this.plugin.configs().msg("general.owner-only"));
      } else {
         String model = "unknown";
         String mhz = "";
         int threads = 0;

         try {
            for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
               if (line.startsWith("model name") && model.equals("unknown")) {
                  model = line.substring(line.indexOf(58) + 1).trim();
               } else if (line.startsWith("cpu MHz") && mhz.isEmpty()) {
                  mhz = line.substring(line.indexOf(58) + 1).trim();
               } else if (line.startsWith("processor")) {
                  threads++;
               }
            }
         } catch (Exception var8) {
            model = "could not read /proc/cpuinfo, " + var8.getMessage();
         }

         Runtime rt = Runtime.getRuntime();
         sender.sendMessage(Component.text("cpu: " + model));
         sender.sendMessage(
            Component.text("  host threads: " + threads + (mhz.isEmpty() ? "" : " at " + mhz + " MHz") + ", this server may use " + rt.availableProcessors())
         );
         sender.sendMessage(Component.text("  memory: " + (rt.maxMemory() >> 20) + " MB max, " + (rt.totalMemory() >> 20) + " MB reserved"));
         sender.sendMessage(
            Component.text(
               "  os: "
                  + System.getProperty("os.name")
                  + " "
                  + System.getProperty("os.version")
                  + " "
                  + System.getProperty("os.arch")
                  + ", java "
                  + System.getProperty("java.version")
            )
         );
         String lower = model.toLowerCase(Locale.ROOT);
         boolean vague = lower.contains("qemu")
            || lower.contains("kvm")
            || lower.contains("virtual")
            || lower.contains("common")
            || lower.equals("unknown")
            || !lower.matches(".*\\d{3,}.*");
         if (vague) {
            sender.sendMessage(
               Component.text("  that name carries no model number, so this is a virtual machine hiding the real chip. ask the host which node you are on")
            );
         } else {
            sender.sendMessage(Component.text("  a real 9950X names itself in full: AMD Ryzen 9 9950X 16-Core Processor, 32 host threads"));
         }

         sender.sendMessage(Component.text("  running a single thread benchmark, one moment"));
         this.plugin
            .async(
               () -> {
                  long best = Long.MAX_VALUE;

                  for (int pass = 0; pass < 3; pass++) {
                     long start = System.nanoTime();
                     long x = 88172645463325252L;
                     double acc = 0.0;

                     for (int i = 0; i < 120000000; i++) {
                        long var14 = x ^ x << 13;
                        long var15 = var14 ^ var14 >>> 7;
                        x = var15 ^ var15 << 17;
                        acc += (x & 65535L) * 1.0000001;
                     }

                     long took = (System.nanoTime() - start) / 1000000L;
                     if (acc != 0.0 && took < best) {
                        best = took;
                     }
                  }

                  long ms = best;
                  this.plugin
                     .sync(
                        () -> {
                           sender.sendMessage(
                              Component.text(
                                 "  single thread: "
                                    + ms
                                    + " ms for the fixed run, lower is faster. run it on a machine you know to have something to compare against"
                              )
                           );
                           sender.sendMessage(
                              Component.text(
                                 "  ability exemptions: "
                                    + this.plugin.antiCheat().permissions()
                                    + ", "
                                    + this.plugin.antiCheat().openWindows()
                                    + " open right now"
                              )
                           );
                        }
                     );
               }
            );
      }
   }

   private void grant(CommandSender sender, String[] args) {
      if (args.length < 5) {
         sender.sendMessage(
            this.plugin
               .configs()
               .msg(
                  "general.usage",
                  "usage",
                  "/ffaadmin grant key <player> <crate> <amount> [txn] | grant rank <player> <rank> <days> [txn] | revoke rank <player> <rank> | resetkit <player> [rank] | mint token|compressed <amount> [player] | mint weapon <id> [player] | reload | verify | testfx <weapon> [x y z] | testkill <cosmetic> [x y z] | sysinfo | setspawn"
               )
         );
      } else {
         String kind = args[1].toLowerCase(Locale.ROOT);
         String target = args[2];
         String value = args[3];
         int number = parse(args[4], 1);
         String txn = args.length > 5 ? args[5] : null;
         if (!this.mayGrant(sender, target)) {
            this.audit(sender, "grant_denied", target, kind + " " + value + " x" + number);
         } else {
            switch (kind) {
               case "key":
                  this.audit(sender, "grant_key", target, value + " x" + number + " txn=" + txn);
                  this.plugin.tebex().grantKeys(sender, target, value, number, txn);
                  break;
               case "rank":
                  this.audit(sender, "grant_rank", target, value + " " + number + "d txn=" + txn);
                  this.plugin.tebex().grantRank(sender, target, value, number, txn);
                  break;
               default:
                  sender.sendMessage(
                     this.plugin
                        .configs()
                        .msg(
                           "general.usage",
                           "usage",
                           "/ffaadmin grant key <player> <crate> <amount> [txn] | grant rank <player> <rank> <days> [txn] | revoke rank <player> <rank> | resetkit <player> [rank] | mint token|compressed <amount> [player] | mint weapon <id> [player] | reload | verify | testfx <weapon> [x y z] | testkill <cosmetic> [x y z] | sysinfo | setspawn"
                        )
                  );
            }
         }
      }
   }

   private void mint(CommandSender sender, String[] args) {
      Player player;
      if (sender instanceof Player self) {
         if (!this.plugin.staff().isRoot(self)) {
            self.sendMessage(this.plugin.configs().msg("general.owner-only"));
            this.audit(sender, "mint_denied", self.getName(), String.join(" ", args));
            return;
         }

         player = self;
      } else {
         player = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : null;
         if (player == null) {
            sender.sendMessage(this.plugin.configs().msg("general.unknown-player", "name", args.length >= 4 ? args[3] : "?"));
            return;
         }
      }

      if (args.length < 3) {
         sender.sendMessage(
            this.plugin
               .configs()
               .msg(
                  "general.usage",
                  "usage",
                  "/ffaadmin grant key <player> <crate> <amount> [txn] | grant rank <player> <rank> <days> [txn] | revoke rank <player> <rank> | resetkit <player> [rank] | mint token|compressed <amount> [player] | mint weapon <id> [player] | reload | verify | testfx <weapon> [x y z] | testkill <cosmetic> [x y z] | sysinfo | setspawn"
               )
         );
      } else {
         int amount = Math.max(1, Math.min(64, parse(args[2], 1)));
         String what = args[1].toLowerCase(Locale.ROOT);
         ItemStack stack;
         switch (what) {
            case "token":
               stack = this.plugin.items().mintKillTokens(amount);
               break;
            case "compressed":
               stack = this.plugin.items().mintCompressedTokens(amount);
               break;
            case "weapon":
               stack = this.plugin.weapons().mint(args[2]);
               if (stack == null) {
                  sender.sendMessage(this.plugin.configs().msg("weapon.unknown", "id", args[2]));
                  return;
               }

               amount = 1;
               break;
            default:
               sender.sendMessage(
                  this.plugin
                     .configs()
                     .msg(
                        "general.usage",
                        "usage",
                        "/ffaadmin grant key <player> <crate> <amount> [txn] | grant rank <player> <rank> <days> [txn] | revoke rank <player> <rank> | resetkit <player> [rank] | mint token|compressed <amount> [player] | mint weapon <id> [player] | reload | verify | testfx <weapon> [x y z] | testkill <cosmetic> [x y z] | sysinfo | setspawn"
                     )
               );
               return;
         }

         for (ItemStack over : player.getInventory().addItem(new ItemStack[]{stack}).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), over);
         }

         what = "weapon".equalsIgnoreCase(args[1]) ? args[2].toLowerCase(Locale.ROOT) : args[1].toLowerCase(Locale.ROOT);
         player.sendMessage(this.plugin.configs().msg("general.minted", "amount", String.valueOf(amount), "what", what));
         this.audit(sender, "mint_" + what, player.getName(), "x" + amount);
      }
   }

   private void resetKit(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(
            this.plugin
               .configs()
               .msg(
                  "general.usage",
                  "usage",
                  "/ffaadmin grant key <player> <crate> <amount> [txn] | grant rank <player> <rank> <days> [txn] | revoke rank <player> <rank> | resetkit <player> [rank] | mint token|compressed <amount> [player] | mint weapon <id> [player] | reload | verify | testfx <weapon> [x y z] | testkill <cosmetic> [x y z] | sysinfo | setspawn"
               )
         );
      } else {
         Player target = Bukkit.getPlayerExact(args[1]);
         if (target == null) {
            sender.sendMessage(this.plugin.configs().msg("general.unknown-player", "name", args[1]));
         } else {
            String rankId = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
            if (rankId != null && this.plugin.ranks().rank(rankId) == null) {
               sender.sendMessage(this.plugin.configs().msg("rankkit.unknown-rank", "rank", rankId));
            } else {
               UUID uuid = target.getUniqueId();
               this.audit(sender, "reset_kit_cooldown", args[1], rankId == null ? "all" : rankId);
               this.plugin.async(() -> this.plugin.kitCooldowns().clear(uuid, rankId));
               sender.sendMessage(Component.text("cleared kit cooldown " + (rankId == null ? "(all)" : rankId) + " for " + target.getName()));
            }
         }
      }
   }

   private void revoke(CommandSender sender, String[] args) {
      if (args.length < 4 || !"rank".equalsIgnoreCase(args[1])) {
         sender.sendMessage(
            this.plugin
               .configs()
               .msg(
                  "general.usage",
                  "usage",
                  "/ffaadmin grant key <player> <crate> <amount> [txn] | grant rank <player> <rank> <days> [txn] | revoke rank <player> <rank> | resetkit <player> [rank] | mint token|compressed <amount> [player] | mint weapon <id> [player] | reload | verify | testfx <weapon> [x y z] | testkill <cosmetic> [x y z] | sysinfo | setspawn"
               )
         );
      } else if (!this.mayGrant(sender, args[2])) {
         this.audit(sender, "revoke_denied", args[2], args[3]);
      } else {
         Player target = Bukkit.getPlayerExact(args[2]);
         if (target == null) {
            sender.sendMessage(this.plugin.configs().msg("general.unknown-player", "name", args[2]));
         } else {
            this.audit(sender, "revoke_rank", args[2], args[3]);
            this.plugin.ranks().revoke(target.getUniqueId(), args[3]);
            sender.sendMessage(this.plugin.configs().msg("rank.revoked", "rank", args[3]));
         }
      }
   }

   @NotNull
   public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
      if (args.length <= 1) {
         return List.of("grant", "revoke", "resetkit", "mint", "reload", "verify", "setspawn");
      } else if (args.length == 2 && "mint".equalsIgnoreCase(args[0])) {
         return List.of("token", "compressed", "weapon");
      } else if (args.length == 3 && "mint".equalsIgnoreCase(args[0]) && "weapon".equalsIgnoreCase(args[1])) {
         return new ArrayList<>(this.plugin.weapons().ids());
      } else if (args.length == 3 && "resetkit".equalsIgnoreCase(args[0])) {
         return new ArrayList<>(this.plugin.ranks().all().keySet());
      } else if (args.length != 2 || !"grant".equalsIgnoreCase(args[0]) && !"revoke".equalsIgnoreCase(args[0])) {
         if (args.length == 4 && "grant".equalsIgnoreCase(args[0])) {
            if ("key".equalsIgnoreCase(args[1])) {
               return new ArrayList<>(this.plugin.crateRegistry().all().keySet());
            }

            if ("rank".equalsIgnoreCase(args[1])) {
               return new ArrayList<>(this.plugin.ranks().all().keySet());
            }
         }

         return List.of();
      } else {
         return List.of("key", "rank");
      }
   }

   private static int parse(String raw, int fallback) {
      try {
         return Integer.parseInt(raw);
      } catch (NumberFormatException var3) {
         return fallback;
      }
   }
}
