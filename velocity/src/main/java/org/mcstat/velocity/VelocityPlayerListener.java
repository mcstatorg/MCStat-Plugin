package org.mcstat.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import org.mcstat.common.McStatCore;

public class VelocityPlayerListener {

    private final McStatCore core;

    public VelocityPlayerListener(McStatCore core) {
        this.core = core;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        core.onPlayerJoin(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getUsername(),
                "proxy"
        );
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        core.onPlayerLeave(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getUsername(),
                "proxy"
        );
    }

}
