# MCStat - Free Minecraft Server Analytics

**MCStat** turns your server into a dashboard on **MCStat.org**: every player, every session, every block broken, and how your server performs while they play. Free, open source (MIT), and private by design: no chat text, no player IP addresses.

## What you get in the dashboard

- **Players:** a searchable list of everyone who joined, with playtime, active vs. AFK time, sessions, first and last seen, country, client, version and language. Open any player for their full profile: lifetime and 30-day gameplay, top blocks, top mobs, causes of death, playtime per day, recent sessions with the address they joined through, and their votes.
- **Gameplay:** server-wide totals per day and leaderboards for blocks broken and placed, mob and player kills, deaths, distance travelled, items crafted, fish caught, items enchanted, experience gained, advancements, and chat and command activity (counted, never read).
- **Audience:** active, new and returning players, retention after 1/7/30 days, busiest hours, and the mix of countries, languages, Minecraft versions, clients, Java vs. Bedrock and premium vs. offline-mode accounts.
- **Join addresses:** which domains and campaign addresses actually bring players.
- **Performance:** TPS, tick time (MSPT), CPU, memory, loaded chunks and entities over 24 hours to 90 days.
- **Votes:** your top voters, linked to their in-game profile.
- **Plugin control:** turn modules on or off and change the report interval from the dashboard; the plugin applies it within a minute, without a restart.

## Supported platforms

| Jar | Platforms |
| --- | --- |
| `McStat-Spigot` | Bukkit, Spigot, Paper, Purpur, Pufferfish, **Folia**, Bukkit-compatible hybrids (Mohist, Arclight, Magma) |
| `McStat-Velocity` | Velocity 3.x |
| `McStat-BungeeCord` | BungeeCord, Waterfall |

Minecraft 1.13+ on the Bukkit family, Java 8+. Works with offline-mode (cracked) servers, Geyser/Floodgate Bedrock players and ViaVersion.

## Privacy

- Never sent: chat and command contents, player IP addresses, inventories, world data.
- Country is looked up **on your server** with the free DB-IP Lite database, which the plugin downloads and refreshes monthly; the IP never leaves it.
- Every module can be switched off in the local config, and the dashboard cannot switch it back on.

## Reliable by design

- Batched, gzip-compressed, signed requests; all network work off the main thread.
- An on-disk spool keeps unsent data through outages and restarts (capped at 20 MB), and a retried batch is applied exactly once.
- Safe updates: the plugin checks GitHub Releases and can stage a new jar, but never hot-swaps a running server.
- Messages in 35 languages; players see them in their own client language.

## Installation

1. Add your server on https://mcstat.org and open **Dashboard → your server → Plugin**.
2. Create a plugin API key there.
3. Drop the jar for your platform into `plugins/`, start the server, and paste the key as `api-key` in `plugins/McStat/config.yml` (Velocity: `plugins/mcstat/config.json`).
4. Run `/mcstat reload` and `/mcstat status`.

## Configuration

```yaml
api-key: "YOUR_API_KEY_HERE"
api-base-url: "https://mcstat.org"
language: en
vote-url: ""

metrics:
  tps: true
  player-playtime: true
  player-events: true
  gameplay: true
  client-info: true
  country: true
  join-address: true

updates:
  check: true
  notify-admins: true
  auto-download: false
  check-interval-hours: 24
```

| Key | Description |
| --- | --- |
| `api-key` | The plugin API key from the dashboard. |
| `api-base-url` | Keep `https://mcstat.org`. |
| `language` | Console language. Players always see their own client language. |
| `vote-url` | Optional custom `/vote` link; empty uses your MCStat page. |
| `metrics.tps` | Performance and world load (servers only). |
| `metrics.player-playtime` | Sessions, playtime and AFK time. |
| `metrics.player-events` | Join/leave events; sessions need them. |
| `metrics.gameplay` | Gameplay counters (servers only). |
| `metrics.client-info` | Version, client brand, language, Java/Bedrock. |
| `metrics.country` | Country, looked up locally. |
| `metrics.join-address` | The domain a player connected through. |
| `updates.*` | Update checks, admin notices, optional staged download, interval in hours. |

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/mcstat status` | `mcstat.admin` | Connection, queue, modules, tracked players, version, updates. |
| `/mcstat reload` | `mcstat.admin` | Reload the config and reconnect. |
| `/mcstat link` | `mcstat.admin` | Masked API key and dashboard link. |
| `/mcstat update [download]` | `mcstat.admin` | Check for a new release; `download` stages it in `plugins/McStat/updates`. |
| `/vote` | everyone | Your server's MCStat vote link. |

## Troubleshooting

- **"Invalid API key"**: create a new plugin key in the dashboard, paste it, `/mcstat reload`.
- **Dashboard not updating**: `/mcstat status` should say connected; the server must reach `https://mcstat.org` over HTTPS (check firewalls and hosting panels).
- **No TPS or gameplay on a proxy**: expected. Proxies have no game tick, movement or gameplay; install the Spigot jar on the backend servers for those.
- **Network with a proxy and backends**: use the proxy jar for network-wide sessions, or the Spigot jar on each backend for per-server statistics, not both for the same players.

## Links

- Website and docs: https://mcstat.org/plugin
- Source and releases: https://github.com/mcstatorg/MCStat-Plugin
- IP geolocation by [DB-IP](https://db-ip.com) (DB-IP Lite, CC BY 4.0)

## License

MIT
