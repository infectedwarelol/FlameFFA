# Flame FFA server plugins — source

Source for the custom plugins running on the Flame FFA network (flameffa.net): Paper 1.21.11 servers behind a Velocity proxy. Author tag in the code: flamesentinell.

## What is in here

| Plugin | Runs on | What it does |
|---|---|---|
| FlameFFACore | FFA | The core: kits and kit layouts, ranks and custom tags, combat tag, arena sweep, crates, kill tokens, ender vault and cart vault, custom weapons (Reaper Scythe, Rocket Mace, Comet Spear, Stun Mace and others), HUD, Tebex bridge, staff tools, resource pack host |
| FlameTweaks | FFA | Chat cooldown, invsee/ecsee, freeze, tempban/permban/unban, trash, vanish, watch recorder |
| FlameTrims | FFA | `/trim` menu, armour trims that follow the armour slot |
| FlameStore | FFA | Store and media NPCs |
| FlameDaily | FFA | Daily rewards |
| FlameHolograms | FFA | Holograms |
| FlameSpawnGuard | FFA | Spawn protection rules |
| FlameWindBrake | FFA | Wind charge tuning |
| FlameFFALobby | Lobby | Lobby plugin (hub NPC and so on) |
| FlameFFAProxy | Velocity proxy | Proxy plugin |

The SmBot plugin (the PvP bot) is not included on purpose.

## Read this first: most of this is decompiled

The original source trees were never kept; only the built jars were. So:

- **FlameTrims** and **FlameFFACore's `weapon/ReaperScythe.java`** are the original hand-written source.
- **Everything else is decompiled from the exact jars running on the servers on 1 Nov 2026** (Vineflower). The logic is exact, but:
  - local variable names are lost (`var1`, `var2` ...) in many files, and most comments are gone;
  - a handful of decompiler slips were fixed by hand so it compiles (missing generic types on some local lists, one lambda variable name in `AegisWard`, one effectively-final variable in `ProjectionSpear`).
- FlameFFACore here is the build **waiting in `/plugins/update`** (it goes live on the next restart). It includes the Reaper Scythe cleave nerf (`cleave-hearts`, armour aware), kit items allowed in the ender chest (`ender-vault.allow-kit-items`), combat tag cleared on death and respawn, kit guards, kit gear kept on a normal leave (`arena.wipe-kit-on-quit`), the kit cooldown bypass permission (`flameffacore.kit.nocooldown`), the legendary crate import types (`compressedtokens`, `tokenbundle:<compressed>:<tokens>`) and the arena sweep water/rail fix.
- The lobby and proxy jars are the ones on those servers now.
- `src/main/resources` holds the **default** configs packed in each jar, not the live server configs (those live on the panel under `plugins/<Plugin>/`).

## Building

Each folder is its own Gradle project (Java 21):

```
cd FlameFFACore
gradle build        # jar ends up in build/libs/
```

All ten were built this way once with Gradle 9.2.1 and every one compiled, and the decompiled ones produce the same class list as the live jars. Nothing here has been run on a server from these rebuilt jars; the live server runs the original jars.

Dependencies are compile-only: `paper-api 1.21.11-R0.1-SNAPSHOT` for the Paper plugins, plus `net.luckperms:api:5.4` and HikariCP for FlameFFACore (its `FlameFFACoreLoader` downloads HikariCP, H2 and MariaDB at runtime), and `velocity-api 3.4.0-SNAPSHOT` for the proxy.

## Things to know

- FlameFFACore is a Paper plugin (`paper-plugin.yml` with a loader), not a classic Bukkit plugin. LuckPerms is required. Tebex is optional.
- SmBot bots carry the scoreboard tag `smbot_bot` and sparring players `smbot_spar`; FlameFFACore (`util/Bots`) skips its combat, kit and death logic for them. Keep that if the bot plugin stays on the server.
- Config keys added recently re-appear automatically from the jar defaults when missing (FlameFFACore merges defaults on load), so set a key to a no-op value instead of deleting it.
