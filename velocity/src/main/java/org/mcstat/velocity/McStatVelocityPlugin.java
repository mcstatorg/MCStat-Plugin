package org.mcstat.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.mcstat.common.McStatCore;
import org.mcstat.common.command.McStatCommandHandler;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

@Plugin(
        id = "mcstat",
        name = "McStat",
        version = McStatVelocityPlugin.VERSION,
        description = "Server and player statistics for mcstat.org",
        authors = {"McStat"},
        url = "https://mcstat.org"
)
public class McStatVelocityPlugin {

    // Velocity reads the version from an annotation, which needs a compile-time
    // constant; keep it in step with the pom version on every release.
    public static final String VERSION = "1.2.2";

    private final ProxyServer server;
    private final Logger logger = Logger.getLogger("McStat");
    private final Path dataDirectory;
    private McStatCore core;
    private McStatCommandHandler commands;

    @Inject
    public McStatVelocityPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        core = new McStatCore(VelocityConfigLoader.load(dataDirectory, logger), new VelocityPlatform(server), dataDirectory, logger);
        core.setPluginVersion(VERSION);
        core.configureUpdater("McStat-Velocity");
        commands = new McStatCommandHandler(core, () -> VelocityConfigLoader.load(dataDirectory, logger));
        server.getEventManager().register(this, new VelocityPlayerListener(core));
        server.getCommandManager().register(server.getCommandManager().metaBuilder("mcstat").plugin(this).build(), new McStatCommand());
        server.getCommandManager().register(server.getCommandManager().metaBuilder("vote").plugin(this).build(), new VoteCommand());
        core.enable();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (core != null) core.disable();
    }

    private String languageOf(CommandSource source) {
        return source instanceof Player ? VelocityPlatform.localeOf((Player) source) : core.getConfig().getLanguage();
    }

    private static void send(CommandSource source, List<String> lines) {
        for (String line : lines) source.sendMessage(LegacyComponentSerializer.legacySection().deserialize(line));
    }

    private final class McStatCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            commands.handle(invocation.arguments(), languageOf(source), lines -> send(source, lines));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("mcstat.admin");
        }
    }

    private final class VoteCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            send(source, commands.vote(languageOf(source)));
        }
    }
}
