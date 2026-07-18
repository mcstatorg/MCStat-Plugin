package org.mcstat.spigot;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstat.common.McStatCore;
import org.mcstat.common.command.McStatCommandHandler;
import org.mcstat.common.config.McStatConfig;
import org.mcstat.common.model.ServerHeartbeat;

public class McStatSpigotPlugin extends JavaPlugin {

    private McStatCore core;
    private McStatCommandHandler commandHandler;
    private TpsTracker tpsTracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        McStatConfig config = SpigotConfigLoader.load(getConfig());
        String serverName = Bukkit.getServer().getName();

        core = new McStatCore(config, getDataFolder().toPath(), getLogger());
        core.setServerName(serverName);

        tpsTracker = new TpsTracker(this);

        core.setHeartbeatSupplier(() -> {
            ServerHeartbeat hb = new ServerHeartbeat();
            hb.setServerName(serverName);
            hb.setServerVersion(Bukkit.getVersion());
            hb.setOnlinePlayers(Bukkit.getOnlinePlayers().size());
            hb.setMaxPlayers(Bukkit.getMaxPlayers());
            hb.setTps(config.isSendTps() ? tpsTracker.getTps() : -1);
            return hb;
        });

        getServer().getPluginManager().registerEvents(
                new SpigotPlayerListener(core, serverName), this);

        core.setPluginVersion(getDescription().getVersion());
        core.enable();

        commandHandler = new McStatCommandHandler(core);
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.disable();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("vote")) {
            sender.sendMessage(commandHandler.getVoteMessage().split("\n"));
            return true;
        }

        if (!command.getName().equalsIgnoreCase("mcstat")) return false;

        if (!sender.hasPermission("mcstat.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            McStatConfig newConfig = SpigotConfigLoader.load(getConfig());
            boolean connected = core.reload(newConfig);
            if (connected) {
                sender.sendMessage("§6[McStat] §aConfiguration reloaded and API connection verified!");
            } else if (newConfig.isValid()) {
                sender.sendMessage("§6[McStat] §eConfiguration reloaded, but API connection could not be verified yet. Heartbeats will retry automatically.");
            } else {
                sender.sendMessage("§6[McStat] §cConfiguration reloaded, but api-key is not configured.");
            }
            return true;
        }

        String response = commandHandler.handleCommand(args);
        if (response != null) {
            sender.sendMessage(response.split("\n"));
        }
        return true;
    }
}
