package org.mcstat.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import org.mcstat.common.McStatCore;
import org.mcstat.common.command.McStatCommandHandler;
import org.mcstat.common.config.McStatConfig;
import org.mcstat.common.model.ServerHeartbeat;

public class McStatBungeePlugin extends Plugin {

    private McStatCore core;
    private McStatCommandHandler commandHandler;

    @Override
    public void onEnable() {
        McStatConfig config = BungeeConfigLoader.load(this);
        String serverName = "BungeeCord-Proxy";

        core = new McStatCore(config, getDataFolder().toPath(), getLogger());
        core.setServerName(serverName);

        core.setHeartbeatSupplier(() -> {
            ServerHeartbeat hb = new ServerHeartbeat();
            hb.setServerName(serverName);
            hb.setServerVersion(getProxy().getVersion());
            hb.setOnlinePlayers(getProxy().getOnlineCount());
            hb.setMaxPlayers(getProxy().getConfig().getPlayerLimit());
            hb.setTps(-1);
            return hb;
        });

        getProxy().getPluginManager().registerListener(this, new BungeePlayerListener(core));

        core.setPluginVersion(getDescription().getVersion());

        getProxy().getPluginManager().registerCommand(this, new Command("mcstat", "mcstat.admin") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    McStatConfig newConfig = BungeeConfigLoader.load(McStatBungeePlugin.this);
                    boolean connected = core.reload(newConfig);
                    if (connected) {
                        sender.sendMessage(new TextComponent("§6[McStat] §aConfiguration reloaded and API connection verified!"));
                    } else if (newConfig.isValid()) {
                        sender.sendMessage(new TextComponent("§6[McStat] §eConfiguration reloaded, but API connection could not be verified yet. Heartbeats will retry automatically."));
                    } else {
                        sender.sendMessage(new TextComponent("§6[McStat] §cConfiguration reloaded, but api-key is not configured."));
                    }
                    return;
                }

                String response = commandHandler.handleCommand(args);
                if (response != null) {
                    for (String line : response.split("\n")) {
                        sender.sendMessage(new TextComponent(line));
                    }
                }
            }
        });

        getProxy().getPluginManager().registerCommand(this, new Command("vote") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                String response = commandHandler.getVoteMessage();
                for (String line : response.split("\n")) {
                    sender.sendMessage(new TextComponent(line));
                }
            }
        });

        core.enable();
        commandHandler = new McStatCommandHandler(core);
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.disable();
        }
    }
}
