package org.mcstat.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.mcstat.common.McStatCore;
import org.mcstat.common.command.McStatCommandHandler;
import org.mcstat.common.config.McStatConfig;
import org.mcstat.common.model.ServerHeartbeat;

import java.nio.file.Path;
import java.util.logging.Logger;

@Plugin(
        id = "mcstat",
        name = "McStat",
        version = McStatVelocityPlugin.VERSION,
        description = "Server statistics tracking for mcstat.org",
        authors = {"McStat"},
        url = "https://mcstat.org"
)
public class McStatVelocityPlugin {

    // Velocity reads the version from an annotation, which needs a compile-time
    // constant, so unlike Spigot and Bungee it cannot pick up ${project.version}
    // from a descriptor. Keeping ONE constant here means a release bump cannot
    // leave the jar reporting the previous version to the update checker and
    // nagging about an update it already is.
    public static final String VERSION = "1.1.5";

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private McStatCore core;
    private McStatCommandHandler commandHandler;

    @Inject
    public McStatVelocityPlugin(ProxyServer server, org.slf4j.Logger slf4jLogger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = Logger.getLogger("McStat");
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        McStatConfig config = VelocityConfigLoader.load(dataDirectory, logger);
        String serverName = "Velocity-Proxy";

        core = new McStatCore(config, dataDirectory, logger);
        core.setServerName(serverName);

        core.setHeartbeatSupplier(() -> {
            ServerHeartbeat hb = new ServerHeartbeat();
            hb.setServerName(serverName);
            hb.setServerVersion(server.getVersion().toString());
            hb.setOnlinePlayers(server.getPlayerCount());
            hb.setMaxPlayers(server.getConfiguration().getShowMaxPlayers());
            hb.setTps(-1);
            return hb;
        });

        server.getEventManager().register(this, new VelocityPlayerListener(core));

        core.setPluginVersion(VERSION);
        core.configureUpdater("McStat-Velocity", dataDirectory);

        commandHandler = new McStatCommandHandler(core);
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("mcstat").build(),
                new McStatCommand()
        );

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("vote").build(),
                new VoteCommand()
        );

        core.enable();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (core != null) {
            core.disable();
        }
    }

    private Component legacyText(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private class McStatCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();

            if (invocation.arguments().length > 0
                    && invocation.arguments()[0].equalsIgnoreCase("reload")) {
                McStatConfig newConfig = VelocityConfigLoader.load(dataDirectory, logger);
                boolean connected = core.reload(newConfig);
                if (connected) {
                    source.sendMessage(legacyText("§6[McStat] §aConfiguration reloaded and API connection verified!"));
                } else if (newConfig.isValid()) {
                    source.sendMessage(legacyText("§6[McStat] §eConfiguration reloaded, but API connection could not be verified yet. Heartbeats will retry automatically."));
                } else {
                    source.sendMessage(legacyText("§6[McStat] §cConfiguration reloaded, but api-key is not configured."));
                }
                return;
            }

            String response = commandHandler.handleCommand(invocation.arguments());
            if (response != null) {
                for (String line : response.split("\n")) {
                    source.sendMessage(legacyText(line));
                }
            }
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("mcstat.admin");
        }
    }

    private class VoteCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String response = commandHandler.getVoteMessage();
            for (String line : response.split("\n")) {
                source.sendMessage(legacyText(line));
            }
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return true;
        }
    }
}
