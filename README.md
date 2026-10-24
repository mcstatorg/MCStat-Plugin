# MCStat Minecraft Plugin

MCStat connects Minecraft servers and proxy networks to https://mcstat.org. It reports performance, player sessions and gameplay statistics, and the owner reads them in the MCStat dashboard: who plays, for how long, what they do, how the server performs. Free, open source, MIT.

## Packages

| Package | Use it for |
| --- | --- |
| `McStat-Spigot-1.2.1.jar` | Bukkit, Spigot, Paper, Purpur, Pufferfish, Folia, and Bukkit-compatible hybrids (Mohist, Arclight, Magma) |
| `McStat-Velocity-1.2.1.jar` | Velocity 3.x proxy networks |
| `McStat-BungeeCord-1.2.1.jar` | BungeeCord and Waterfall proxy networks |

Java 8 or newer; Minecraft 1.13 or newer on the Bukkit family (tested on Paper 1.21). Folia is supported natively (no Bukkit scheduler is used).

## What it collects

What is collected is chosen per server in the dashboard (**Dashboard → your server → Plugin**); changes reach the plugin within a minute, without a restart. Each module can also be switched off in the local config, and the dashboard can never turn on what is off there.

| Module | Data | Platforms |
| --- | --- | --- |
| Performance | TPS, tick time (MSPT, Paper), CPU, memory | servers |
| World load | loaded chunks, entities | servers (not Folia) |
| Sessions | joins, leaves, playtime, online players | all |
| AFK time | idle time inside each session; riding, drifting in water, jumping in place and clicking without moving do not count as activity | servers |
| Gameplay | blocks broken/placed (per type), mob and player kills (per type), deaths (per cause), distance, crafting, fishing, enchanting, XP, advancements, chat and command **counts** | servers |
| Client info | Minecraft version (ViaVersion-aware), client brand, client language, Java/Bedrock (Floodgate) | all |
| Country | country code, looked up **on your server** with the free DB-IP Lite database (downloaded to the plugin folder's `geo/`, refreshed monthly) | all |
| Join address | the domain a player typed to connect | all |

Never collected: chat or command contents, player IP addresses, inventories, world data. Cracked/offline-mode and Bedrock players are tracked per server like everyone else.

## How it sends

- Events are batched every heartbeat (30–300 s, set in the dashboard), gzip-compressed and signed (HMAC-SHA256 with the API key and the local installation ID).
- Batches wait in an on-disk spool (`plugins/McStat/spool`, capped at 20 MB) until MCStat accepts them, so a network or MCStat outage loses nothing; a retried batch is applied exactly once.
- All network work runs off the main thread.

## Install

1. Add your server on MCStat.org and open **Dashboard → your server → Plugin**.
2. Create a plugin API key there and copy it.
3. Put the jar for your platform in `plugins/`, start the server once, and paste the key as `api-key` in `plugins/McStat/config.yml` (Velocity: `plugins/mcstat/config.json`).
4. Run `/mcstat reload`, then `/mcstat status`. The dashboard shows the connection within a minute.

## Config

```yaml
api-key: "YOUR_API_KEY_HERE"
api-base-url: "https://mcstat.org"
language: en            # console language; players see their own client language
vote-url: ""            # optional custom /vote link

metrics:                # local switches: false keeps a module off whatever the dashboard says
  tps: true             # performance and world load
  player-playtime: true # sessions and AFK time
  player-events: true   # join/leave events (needed for sessions)
  gameplay: true        # gameplay counters
  client-info: true
  country: true
  join-address: true

updates:
  check: true
  notify-admins: true
  auto-download: false
  check-interval-hours: 24
```

Proxies have the same keys except `tps` and `gameplay` (a proxy sees no ticks, movement or gameplay).

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/mcstat status` | `mcstat.admin` | connection, queue, modules, tracked players, version, updates |
| `/mcstat reload` | `mcstat.admin` | reload the config and reconnect |
| `/mcstat link` | `mcstat.admin` | masked API key and dashboard link |
| `/mcstat update [download]` | `mcstat.admin` | check GitHub Releases; `download` stages the new jar in `plugins/McStat/updates` |
| `/vote` | everyone | the server's MCStat vote link |

Messages are available in 35 languages; players see them in their own client language.

## Updates

MCStat checks the official GitHub Releases once a day and logs one notice when a newer version exists. `updates.auto-download: true` (or `/mcstat update download`) only stages the jar in `plugins/McStat/updates/`; a running server is never hot-swapped. Stop the server, replace the jar, start it again.

## Build

```bash
mvn clean package
```

Jars are written to `spigot/target/`, `velocity/target/` and `bungee/target/`. Gson, OkHttp and the MaxMind reader are shaded and relocated.

## Attribution

IP geolocation by [DB-IP](https://db-ip.com) (DB-IP Lite, CC BY 4.0).

## License

MIT. See `LICENSE`.
