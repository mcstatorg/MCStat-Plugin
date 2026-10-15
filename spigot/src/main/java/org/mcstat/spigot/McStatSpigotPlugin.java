package org.mcstat.spigot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstat.common.McStatCore;
import org.mcstat.common.command.McStatCommandHandler;

import java.util.List;

public class McStatSpigotPlugin extends JavaPlugin {

    private McStatCore core;
    private McStatCommandHandler commands;
    private SpigotPlatform platform;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        platform = new SpigotPlatform(this);
        core = new McStatCore(SpigotConfigLoader.load(getConfig()), platform, getDataFolder().toPath(), getLogger());
        core.setPluginVersion(getDescription().getVersion());
        core.configureUpdater("McStat-Spigot");
        commands = new McStatCommandHandler(core, () -> {
            reloadConfig();
            return SpigotConfigLoader.load(getConfig());
        });
        getServer().getPluginManager().registerEvents(new SpigotPlayerListener(core, platform), this);
        if (platform.needsLoginListener()) {
            getServer().getPluginManager().registerEvents(new SpigotLoginListener(platform), this);
        }
        getServer().getPluginManager().registerEvents(new SpigotGameplayListener(core.gameplay()), this);
        if (hasClass("org.bukkit.event.player.PlayerAdvancementDoneEvent")) {
            getServer().getPluginManager().registerEvents(new SpigotAdvancementListener(core.gameplay()), this);
        }
        core.enable();
    }

    @Override
    public void onDisable() {
        if (core != null) core.disable();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String language = sender instanceof Player ? platform.localeOf((Player) sender) : core.getConfig().getLanguage();
        if (command.getName().equalsIgnoreCase("vote")) {
            send(sender, commands.vote(language));
            return true;
        }
        if (!command.getName().equalsIgnoreCase("mcstat")) return false;
        if (!sender.hasPermission("mcstat.admin")) {
            send(sender, commands.noPermission(language));
            return true;
        }
        commands.handle(args, language, lines -> send(sender, lines));
        return true;
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static void send(CommandSender sender, List<String> lines) {
        sender.sendMessage(lines.toArray(new String[0]));
    }
}
