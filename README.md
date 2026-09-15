# MCStat Minecraft Plugin

MCStat connects Minecraft servers and proxy networks to https://mcstat.org with signed HTTPS telemetry.

## Packages

| Package | Use it for |
| --- | --- |
| `McStat-Spigot-1.1.5.jar` | Bukkit, CraftBukkit, Spigot, Paper, Purpur, Pufferfish, Folia, and Bukkit-compatible hybrid servers such as Mohist, Arclight, Magma, and CatServer |
| `McStat-Velocity-1.1.5.jar` | Velocity 3.x proxy networks |
| `McStat-BungeeCord-1.1.5.jar` | BungeeCord and Waterfall proxy networks |

Pure Fabric, Quilt, Forge, NeoForge, or Sponge servers need a native platform adapter unless they run a Bukkit-compatible hybrid layer. The shared `common` module exists so those adapters can reuse the same API client, queue, command, and telemetry model.

## What It Sends

- Server heartbeat: online players, max players, uptime, TPS where available, and timestamp.
- Player sessions: UUID, name, session start, and tracked playtime.
- Join and leave events when enabled.
- Signed requests using the server API key and local installation ID.

The plugin does not collect chat messages, inventories, private commands, player IP addresses, or world data.

## Build

```bash
mvn clean package
```

Compiled jars are written to each module's `target/` directory:

- `spigot/target/McStat-Spigot-1.1.5.jar`
- `velocity/target/McStat-Velocity-1.1.5.jar`
- `bungee/target/McStat-BungeeCord-1.1.5.jar`

## Install

1. Create or open your server on MCStat.org.
2. Generate a server API key with ingest permission.
3. Download the jar for your platform.
4. Put it in `/plugins`, restart, and edit `plugins/MCStat/config.yml`.
5. Run `/mcstat status`.

Velocity stores config as `config.json` in its plugin data directory.

## Commands

- `/mcstat status` - connection, queue, uptime, and tracked player status.
- `/mcstat reload` - reload local configuration.
- `/mcstat update` - check GitHub Releases for a newer MCStat jar.
- `/mcstat update download` - download the matching platform jar to the safe local update folder.
- `/mcstat link` - masked API key and dashboard link.
- `/vote` - MCStat vote link for the connected server.

## Updates

MCStat checks the official GitHub Releases feed once per day by default. When a newer version is available, it logs a single admin warning with the release link.

Automatic download is opt-in with `updates.auto-download: true`. Downloaded jars are written to the plugin data folder under `updates/`; the plugin never hot-swaps a running Minecraft server or proxy. Stop the server, replace the old jar with the downloaded one, then start it again.

## License

MIT. See `LICENSE`.
