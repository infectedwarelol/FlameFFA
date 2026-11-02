# FlameFFACore merge plan

Target: one Paper + Folia plugin, `dev.flamesentinell.flameffacore`, Java 21, Paper API 1.21.11.
Merged: FlameDaily, FlameHolograms, FlameSpawnGuard, FlameStore, FlameTrims, FlameTweaks, FlameWindBrake.
Not merged: FlameFFAProxy (Velocity), FlameFFALobby (quarantined in `_quarantine/`, on hold by request).

Status legend: `[ ]` todo, `[x]` done (file:line filled in phase 3).

## 0. Ground facts that drive the design

- Core is a `paper-plugin.yml` plugin. Paper plugins have no `commands:` section, so Bukkit's automatic
  `permission:` gate from the old `plugin.yml` files disappears. Every ported command gets an explicit
  check through `BasicCommand#permission()` plus in-handler checks per subcommand.
- Core already registers commands through `LifecycleEvents.COMMANDS` + `BasicCommand` (`FlameFFACore.java:284`).
  Ported commands use the same path.
- Only Core has a database. None of the 7 source plugins use SQL, so the single Hikari pool stays as is.
- Core has 88 `BukkitScheduler` call sites (inspect.sh baseline), source plugins add ~15 more. All move to one
  scheduler facade over Folia's `GlobalRegionScheduler` / `RegionScheduler` / `EntityScheduler` / `AsyncScheduler`.
  These APIs exist on Paper too, so one code path serves both.
- Old PDC namespaces come from old plugin names: `flameholograms:id`, `flametrims:applied`,
  `flametrims:template_{head,chest,legs,feet}`, `flametrims:material_{head,chest,legs,feet}`.
  They are kept byte-for-byte with `new NamespacedKey("flametrims", ...)` so no migration is needed.
- Scoreboard tags kept unchanged: `flamestore_npc`, `flamestore_rules_npc`, `flamestore_discord_npc`,
  `flamestore_media_npc`, `windbrake_bypass`, `smbot_bot`.
- Proxy contract (`FlameFFAProxy.java:69-116`), unchanged: channel `flameffa:hub`, request `UTF sub` then
  `Connect: UTF server` | `Count: UTF server` -> reply `UTF "Count", UTF server, int count` | `Ping` -> reply
  `UTF "Pong", UTF server, int total`. Core only uses `bungeecord:main` `UTF "Connect", UTF server`
  (`AfkWatcher.java:189-206`). Only the quarantined Lobby used `flameffa:hub`, so the merged JAR registers no
  incoming channel and trusts no proxy input.

## 1. Feature checklist

Paths are relative to `src/main/java/dev/flamesentinell/flameffacore/`.

### Shared infrastructure
- [x] `Tasks` scheduler facade, timers tracked, `cancelAll()` on disable: `util/Tasks.java:15`, `FlameFFACore.java` onDisable
- [x] `Module` contract + `modules.<name>.enabled` toggles: `module/Module.java:5`, `module/ModuleManager.java:36`, `resources/config.yml` `modules:`
- [x] Per-module config, first-boot migration from `plugins/<Old>/`, never overwrites: `module/ModuleConfig.java:32`
- [x] Explicit permission gate for every ported command: `module/ModuleCommand.java:32`
- [x] Atomic async YAML writer, flushed on disable: `config/ConfigManager.java:263`
- [x] Shared MiniMessage `util/Text.java`, shared LuckPerms `rank/RankService.java`, single Hikari pool `storage/Database.java` (no module needed a second one)
- [x] `folia-supported: true`: `resources/paper-plugin.yml:6`
- [x] All ported permission nodes declared with original defaults: `resources/paper-plugin.yml`
- [x] Modules registered: `FlameFFACore.java:211`

### daily
- [x] `/daily`, cooldown, free-slot check, roll: `daily/DailyModule.java:84`
- [x] atomic claim (double-claim fix): `daily/DailyModule.java:100`
- [x] bonus key for `flamedaily.bonus`, direct grant + audit instead of console dispatch: `daily/DailyModule.java:125`
- [x] `/flamedaily reload|reset <player>`: `daily/DailyModule.java:194`
- [x] messages `claimed` `wait` `full` `bonus`: `daily/DailyModule.java:222`, `resources/daily.yml`
- [x] `FlameDaily/claims.yml` migrated to `daily-claims.yml`: `daily/DailyModule.java:35`

### holograms
- [x] `/holo` + `hologram`, `holograms`, tab completion: `holograms/HologramsModule.java:120`, `holograms/HoloCommand.java:48`, `:422`
- [x] all 20 subcommands: `holograms/HoloCommand.java:48`
- [x] PDC `flameholograms:id` kept: `holograms/HologramsModule.java:69`
- [x] chunk load spawn / unload despawn / purge: `holograms/HologramsModule.java:382`
- [x] `{online}` `{max}` refresh: `holograms/HologramsModule.java:146`
- [x] defaults: `holograms/HologramsModule.java:217`
- [x] messages: `holograms/HologramsModule.java:409`, `resources/holograms.yml`
- [x] `FlameHolograms/config.yml` -> `holograms.yml`, `FlameHolograms/holograms.yml` -> `holograms-data.yml`: `holograms/HologramsModule.java:76`

### spawnguard
- [x] `/spawnguard`: `spawnguard/SpawnGuardModule.java:79`
- [x] drop guard + projectiles + action bar: `spawnguard/SpawnGuardModule.java:176`
- [x] clear on teleport, death, quit: `spawnguard/SpawnGuardModule.java:155`
- [x] collision rule (Paper only, see D1): `spawnguard/SpawnGuardModule.java:196`
- [x] uses Core safezone live: `spawnguard/SpawnGuardModule.java:101`
- [x] move handler early return: `spawnguard/SpawnGuardModule.java:137`

### store
- [x] `/store` `shop`, `/discord`, `/rules`, `/media`: `store/StoreModule.java:102`
- [x] `/flamestore` + 12 npc subcommands: `store/StoreModule.java:243`
- [x] `/customtag` with tag whitelist: `store/StoreModule.java:302`
- [x] 4 NPCs, skins, boots, sound, chunk refresh: `store/StoreNpc.java:101`
- [x] custom tag written to Core `ranks.yml` in memory + async, HUD refreshed: `store/StoreModule.java:339`
- [x] `FlameStore/config.yml` -> `store.yml`: `store/StoreModule.java:58`

### trims
- [x] `/trim` `trims`: `trims/TrimsModule.java:144`
- [x] menu: `trims/TrimsModule.java:168`
- [x] PDC `flametrims:*` kept: `trims/TrimsModule.java:61`
- [x] apply / strip: `trims/TrimsModule.java:417`
- [x] death, item spawn, armour stand, click/drag guards: `trims/TrimsModule.java:504`, `:230`
- [x] bots skipped: `trims/TrimsModule.java:418`
- no `trims.yml`: the plugin never had settings, an empty file would be dead weight

### tweaks
- [x] chat cooldown: `tweaks/TweaksModule.java:169`
- [x] trapdoors: `tweaks/TweaksModule.java:391`
- [x] `/tempban` `/permban` `/unban`: `tweaks/TweaksModule.java:264`
- [x] `/vanish` `v` + ghost: `tweaks/TweaksModule.java:414`
- [x] `/trash` `disposal` `bin`: `tweaks/TweaksModule.java:400`
- [x] `/freeze` `ss`: `tweaks/TweaksModule.java:240`
- [x] `/invsee` `/ecsee`, offline via Core: `tweaks/TweaksModule.java:194`, `ffa/OfflineInvsee.java:93`, `ffa/EnderVault.java:153`
- [x] `/watch` recorder + macro trap: `tweaks/Watch.java:709`
- [x] `/flametweaks`: `tweaks/TweaksModule.java:92`
- [x] reflection replaced: `tweaks/TweaksModule.java:417`
- [x] `FlameTweaks/config.yml` -> `tweaks.yml`: `tweaks/TweaksModule.java:61`; old recordings stay in `plugins/FlameTweaks/watch`

### windbrake
- [x] `/windbrake`: `windbrake/WindBrakeModule.java:65`
- [x] brake logic, 15 keys: `windbrake/WindBrakeModule.java:104`
- [x] bypass tag, charge removal: `windbrake/WindBrakeModule.java:162`
- [x] `FlameWindBrake/config.yml` -> `windbrake.yml`: `windbrake/WindBrakeModule.java:42`

### Core
- [x] commands unchanged: `FlameFFACore.java` `registerCommands`
- [x] every BukkitScheduler call migrated (inspect.sh checks): `ui/HudService.java:673`, `ffa/ArenaSweeper.java:211`, weapons, kit, crate, cosmetic, villager
- [x] scoreboard features skipped on Folia only (D1)

## 2. Collisions and decisions

| # | Collision | Decision |
|---|---|---|
| C1 | `/invsee`, `/ecsee`: Tweaks commands vs Core `OfflineInvsee.java:94` / `EnderVault.java:158` intercepting the same commands in `PlayerCommandPreprocessEvent` (offline targets) | Register real `invsee`/`ecsee` commands in the tweaks module. Online target -> Tweaks peek; offline target -> Core offline path called directly. Drop both preprocess intercepts. `/flametweaks:invsee` and `/flametweaks:ecsee` forms are kept as extra aliases. |
| C2 | `/freeze` (Tweaks) vs Core `staff freeze` | Keep `/freeze` + `ss`, delegate to `StaffService` directly instead of dispatching a command. |
| C3 | `/vanish` (Tweaks ghost) vs Core `staff vanish` / `StaffService.vanished` | Tweaks ghost keeps its behaviour, reads vanish state from `StaffService` directly. Single source of truth: Core. |
| C4 | Store `customtag` writes `plugins/FlameFFACore/ranks.yml` by hand and dispatches `flameffacore reload` | Use `ConfigManager.ranks()`, save async, reload ranks only. |
| C5 | SpawnGuard re-reads `plugins/FlameFFACore/config.yml` `arena.safezone` | Read Core `Safezone` live. |
| C6 | Daily mints via reflection + `ffaadmin` console dispatch | Call `ItemRegistry.mintKillTokens` and `KeyStore.grant` directly (with the existing ledger reason and txn id). |
| C7 | Holograms data file `holograms.yml` vs required module config name `holograms.yml` | Config -> `holograms.yml`, data -> `holograms-data.yml`. Old `FlameHolograms/config.yml` -> `holograms.yml`, old `FlameHolograms/holograms.yml` -> `holograms-data.yml`. |
| C8 | `AsyncChatEvent`: Tweaks cooldown (LOWEST), Core ChatGuard (LOWEST + HIGH), AfkWatcher (MONITOR), ArenaService forgive (MONITOR) | Keep separate handlers and priorities. Core listeners register first, as Core loaded before Tweaks. |
| C9 | `PlayerMoveEvent`: SpawnGuard, WindBrake, FreezeService, AfkWatcher, SpawnCommand, WeaponRegistry | Keep separate. SpawnGuard: early-return when block and on-ground state unchanged. WindBrake needs sub-block motion, keeps per-move sample (one map put). |
| C10 | `EntityDamageByEntityEvent`: Tweaks ghost (LOWEST), SpawnGuard (LOW), ArenaService (HIGH), CombatHud (MONITOR) | Keep priorities as is. |
| C11 | Schedulers: each plugin used BukkitScheduler | One `Tasks` facade, every handle tracked, `cancelAll()` in `onDisable`. |
| C12 | Message utils: each plugin had its own `MiniMessage.miniMessage()` | Shared `Text`; module messages keep their own config keys. |
| C13 | DB pools | none to merge, Core pool only. |
| C14 | LuckPerms | Only Core uses it; modules call `RankService`. |
| C15 | Permission namespaces | No overlap (`flameffacore.*`, `flamedaily.*`, `flameholo.*`, `spawnguard.*`, `flamestore.*`, `flametrims.*`, `flametweaks.*`, `windbrake.*`). All kept. |
| C16 | `smbot_bot` bot tag used by Core and Trims | Use Core `util/Bots.java`. |

## 3. Folia blockers (needs your call)

| # | Issue | Proposed decision |
|---|---|---|
| D1 | Folia does not implement the Bukkit Scoreboard API (`getMainScoreboard`, teams, objectives throw). Used by `HudService` (sidebar, tab, below-name health, 14 calls), `LeaderboardService`, `CombatHud`, `ArenaSweeper`, `AegisWard`, `ReaperScythe`, `RocketMace`, `TetherChain`, `WeaponRegistry`, `Bots`, and SpawnGuard collision teams. | Detect Folia at runtime. On Paper: unchanged. On Folia: skip the scoreboard calls (sidebar, below-name health, collision rule teams) and keep everything else. Scoreboard *tags* on entities still work on Folia. Building a packet-level sidebar would need a new dependency, which is forbidden. |
| D2 | `Bukkit.dispatchCommand` (AFK action command, freeze logout command, punish command) | Run on `GlobalRegionScheduler`. |
| D3 | `teleport()` | `teleportAsync()` everywhere. |
| D4 | Full-world entity scans (`world.getEntities()` in CombatHud, AegisWard, Store NPC sweep, Holograms purge) | Track spawned entities by UUID; clean on chunk load in the owning region. Startup sweeps run per loaded chunk on its region thread. |

## 4. Problems to fix during integration (from inventory, verified where marked)

Security
- S1 HIGH Daily double-claim / shared `YamlConfiguration` from many region threads (`FlameDaily.java:29-62`): atomic claim in `ConcurrentHashMap` (`compute`), persist async.
- S2 HIGH Ported commands lose the `plugin.yml` permission gate (all 7 plugins). Explicit `permission()` + per-subcommand checks.
- S3 HIGH Tebex key grants with no txn dedup (`TebexBridge.java:25-36`) while rank grants dedup (`:49`). Add `txns().claim()` to key grants. Verify first.
- S4 HIGH Crate open: double `keys().spend()` window (`CrateService.java:193-203`). Verify; guard per player before spend.
- S5 MED Invsee edit permission cached at open (`FlameTweaks.java:106`). Re-check `flametweaks.invsee.edit` on each click.
- S6 MED Holograms parse admin lines as full MiniMessage (`FlameHolo.java:278`, `HoloCommand.java:321`). Admin-only, so formatting stays; strip `click`/`hover`/`insert` tags so a hologram cannot carry run_command.
- S7 MED `/holo near` negative radius (`HoloCommand.java:338`), Daily negative config amounts (`FlameDaily.java:42-44`), Tweaks `limited-tempban-max-days` lower bound (`FlameTweaks.java:195`), `CrateService.buy` amount upper bound. Clamp.
- S8 MED Ban reason unbounded length (`FlameTweaks.java:215`). Cap at 256.
- S9 MED `ChatGuard.java:87` iterates online players from the async chat thread. Snapshot permission state, deliver via `Audience`.
- S10 LOW `VillagerService` editor/shop open without permission check (`:153`, `:273`). Callers are operator-gated commands; verify, add guard in the service.

Thread safety / Folia
- T1 103 BukkitScheduler call sites (Core 88 + sources). Map: global tick loops -> per-player `EntityScheduler` timers; block/location work -> `RegionScheduler`; IO -> `AsyncScheduler`.
- T2 Non-concurrent shared maps touched from several regions: `WeaponRegistry.java:48-49`, `TetherTrident.java:36-39`, `AegisShield.java:41`, `CometSpear.java:36`, SpawnGuard `:37-38`, WindBrake `:30-32`, Trims cache/pending. -> `ConcurrentHashMap` / `newKeySet`.
- T3 Sync file IO on game thread: `EnderVault.java:107`, `CartVault.java:148`, `OfflineInvsee.java:121,226,267,269`, `Safezone.java:124`, `AdminCommand.java:120`, `LeaderboardService.java:128`, `ArenaService.java:122`, Daily `:46`, Holograms save, Store `:364-368`, Watch writer per tick. -> AsyncScheduler, hop back for Bukkit API.
- T4 `OfflineInvsee.java:283-304` blocks the async login thread up to 5 s on a main-thread future. Rework with async close request.

Performance
- P1 `CombatHud.sweepLeftovers` scans every entity of every world each HUD tick. Track UUIDs.
- P2 HudService per-player raycast every tick (`crateLook`, `HudService.java:782`). Every 4 ticks, cache last block.
- P3 RocketMace untracked every-tick task (`RocketMace.java:72`), WindBrake hold tasks, Watch per-tick timer: tracked and cancelled on disable.
- P4 Trims full inventory sweep every 5 ticks for all players: per-player entity timer, skip players with no marked items.
- P5 SpawnGuard move handler early-return; Store NPC refresh every 10 ticks only when NPC chunk is loaded.
- P6 SkinFetcher new `HttpClient` per call: one shared client.
- P7 `PlayerStore.java:133` column concat: whitelisted switch, kept, no user input reaches it.

Haiku false positives (checked, no action)
- Trims/Tweaks/Holo "missing permission check": old plugin.yml `permission:` enforced it. Becomes real after merge, covered by S2.
- AfkWatcher move handler already early-returns (`AfkWatcher.java:68`).

## 5. Package layout

```
dev.flamesentinell.flameffacore
  module/Module.java, module/ModuleManager.java, module/ModuleConfig.java
  util/Tasks.java
  daily/DailyModule.java
  holograms/HologramsModule.java, Holo.java, HoloCommand.java
  spawnguard/SpawnGuardModule.java
  store/StoreModule.java, StoreNpc.java
  trims/TrimsModule.java
  tweaks/TweaksModule.java, Watch.java
  windbrake/WindBrakeModule.java
```

Modules enable after Core services, disable before them. A module failing to enable is logged and skipped;
the rest of the plugin keeps running.
