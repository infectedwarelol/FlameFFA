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

### Shared infrastructure
- [ ] `Tasks` scheduler facade (global / region / entity / async, cancellable handles, all tracked and cancelled in `onDisable`)
- [ ] `Module` contract (`enable()`, `disable()`), toggled by `modules.<name>.enabled` in config.yml (default true)
- [ ] Per-module config file loader with first-boot migration from `plugins/<OldPlugin>/config.yml`, never overwriting an existing file
- [ ] Shared MiniMessage service: existing `util/Text.java`, player text always via `Placeholder.unparsed` or escape
- [ ] Shared LuckPerms hook: existing `rank/RankService.java`
- [ ] `folia-supported: true` in paper-plugin.yml
- [ ] All ported permission nodes declared in paper-plugin.yml with original defaults

### daily (FlameDaily, `daily.yml`, data `daily-claims.yml`)
- [ ] `/daily` (perm `flamedaily.use`, default true): cooldown `cooldown-hours` 24, free-slot check, roll `min-tokens` 4 .. `max-tokens` 16 around `average` 7.6, gives kill tokens
- [ ] bonus key for `flamedaily.bonus` (default false): `bonus-key.crate` mythic x `bonus-key.amount` 1
- [ ] `/flamedaily reload|reset <player>` (perm `flamedaily.admin`, op)
- [ ] messages `claimed`, `wait`, `full`, `bonus`
- [ ] migrate `plugins/FlameDaily/claims.yml` (uuid -> epoch ms)
- [ ] reflection `FlameFFACore.items().mintKillTokens` and console `ffaadmin mint/grant` replaced by direct calls

### holograms (FlameHolograms, `holograms.yml`, data `holograms-data.yml`)
- [ ] `/holo` aliases `hologram`, `holograms` (perm `flameholo.admin`, op), tab completion
- [ ] subcommands: create, createat, addline, setline, insertline, removeline, movehere, tp, delete, info, list, near, scale, billboard, background, shadow, seethrough, width, reload, help
- [ ] TextDisplay per hologram, PDC `flameholograms:id`, chunk load spawn / unload despawn, stale purge on enable
- [ ] `{online}` / `{max}` live refresh every `refresh-seconds` (5)
- [ ] defaults: scale 1.0, billboard center, shadow true, see-through false, line-width 240, background none
- [ ] all `messages.*`
- [ ] migrate `plugins/FlameHolograms/config.yml` and `holograms.yml`

### spawnguard (FlameSpawnGuard, `spawnguard.yml`)
- [ ] `/spawnguard` reload (perm `spawnguard.admin`, op)
- [ ] drop guard: player who leaves the safezone mid-air cannot hit until landing, action bar `blocked-message`, `block-projectiles`
- [ ] clear on teleport (except ender pearl), death, quit
- [ ] `no-push-in-safezone` collision rule toggle
- [ ] safezone read from Core `Safezone` service instead of re-parsing `FlameFFACore/config.yml`

### store (FlameStore, `store.yml`)
- [ ] `/store` alias `shop`, `/discord`, `/rules`, `/media` (dialogs, books, chat links)
- [ ] `/flamestore` (perm `flamestore.admin`, op): reload, npc, npcat, npcremove, rulesnpc, rulesnpcat, rulesnpcremove, discordnpc, discordnpcat, discordnpcremove, medianpc, medianpcat, medianpcremove
- [ ] `/customtag <player> <tag>|clear` (perm `flamestore.customtag`, op), whitelist tag regex, `custom-tag.max-length` 16
- [ ] 4 Mannequin NPCs + TextDisplay titles, skins, boots colour, click sound, chunk-load refresh
- [ ] customtag writes Core `ranks.yml` through Core config service (async save) and calls Core reload directly
- [ ] migrate `plugins/FlameStore/config.yml`

### trims (FlameTrims, `trims.yml`, no settings today)
- [ ] `/trim` alias `trims` (perm `flametrims.use`, default true)
- [ ] per-slot template/material menu, stored in player PDC (`flametrims:template_*`, `flametrims:material_*`)
- [ ] trim applied to worn armour, marked `flametrims:applied`, stripped when armour leaves the armour slot
- [ ] strip on death drops, item spawn, armour stand; block click/drag dupes; save on close and on disable
- [ ] bots (`smbot_bot`) skipped

### tweaks (FlameTweaks, `tweaks.yml`)
- [ ] chat cooldown `chat-cooldown-seconds` 3, bypass `flametweaks.chat.bypass`
- [ ] trapdoors only flip in creative
- [ ] `/tempban <player> <days> [reason]`, `/permban`, `/unban` (perms `flametweaks.ban`, `flametweaks.tempban.limited` with `limited-tempban-max-days` 7, `flametweaks.ban.override`), `ban-appeal`, staff notify
- [ ] `/vanish` alias `v` (perm `flametweaks.vanish`), ghost: hidden, fly, invulnerable, no targeting, restore on join
- [ ] `/trash` aliases `disposal`, `bin`, blocked in combat, `trash-title`
- [ ] `/freeze` alias `ss` -> Core staff freeze
- [ ] `/invsee`, `/ecsee` (perm `flametweaks.invsee`, edit `flametweaks.invsee.edit`), offline targets via Core `OfflineInvsee` / `EnderVault`
- [ ] `/watch add|remove|list <player>` (perm `flametweaks.watch`): per-tick recorder to `watch/<name>/<ts>.jsonl.gz`, macro trap `watch-trap` / `watch-trap-seconds`, `watch-players`
- [ ] `/flametweaks` reload (perm `flametweaks.admin`)
- [ ] reflection calls to Core `cooldowns()` / `staff()` replaced by direct calls
- [ ] migrate `plugins/FlameTweaks/config.yml`; old recordings stay in `plugins/FlameTweaks/watch` (not moved)

### windbrake (FlameWindBrake, `windbrake.yml`)
- [ ] `/windbrake` reload (perm `windbrake.admin`, op)
- [ ] wind charge brake: all 15 keys (`enabled`, `require-airborne`, `require-falling`, `min-fall-velocity`, `min-pitch-down`, `min-ground-distance`, `cooldown-ms`, multipliers, `hold-ticks`, sound, `debug`)
- [ ] `windbrake_bypass` tag, braked charge removed on hit
- [ ] migrate `plugins/FlameWindBrake/config.yml`

### Core (existing, behaviour unchanged)
- [ ] all 12 Brigadier commands + `ec`, `cartinv`, `keyall` and aliases unchanged
- [ ] every `BukkitScheduler` call migrated (list in section 4)
- [ ] scoreboard features handled per section 3, decision D1

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
