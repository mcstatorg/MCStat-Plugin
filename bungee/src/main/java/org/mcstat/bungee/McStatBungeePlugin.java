package org.mcstat.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import org.mcstat.common.McStatCore;
import org.mcstat.common.command.McStatCommandHandler;

import java.util.List;

public class McStatBungeePlugin extends Plugin {

    private McStatCore core;
    private McStatCommandHandler commands;

    @Override
    public void onEnable() {
        core = new McStatCore(BungeeConfigLoader.load(this), new BungeePlatform(getProxy()), getDataFolder().toPath(), getLogger());
        core.setPluginVersion(getDescription().getVersion());
        core.configureUpdater("McStat-BungeeCord");
        commands = new McStatCommandHandler(core, () -> BungeeConfigLoader.load(this));
        getProxy().getPluginManager().registerListener(this, new BungeePlayerListener(core));
        getProxy().getPluginManager().registerCommand(this, new Command("mcstat", "mcstat.admin") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                commands.handle(args, languageOf(sender), lines -> send(sender, lines));
            }
        });
        getProxy().getPluginManager().registerCommand(this, new Command("vote") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                send(sender, commands.vote(languageOf(sender)));
            }
        });
        core.enable();
    }

    @Override
    public void onDisable() {
        if (core != null) core.disable();
    }

    private String languageOf(CommandSender sender) {
        return sender instanceof ProxiedPlayer ? BungeePlatform.localeOf((ProxiedPlayer) sender) : core.getConfig().getLanguage();
    }

    private static void send(CommandSender sender, List<String> lines) {
        for (String line : lines) sender.sendMessage(TextComponent.fromLegacyText(line));
    }
}
