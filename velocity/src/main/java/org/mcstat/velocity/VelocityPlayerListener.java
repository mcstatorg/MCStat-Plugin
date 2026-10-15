package org.mcstat.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import org.mcstat.common.McStatCore;

/** Network-wide sessions: a player is online from login until they leave the proxy. */
final class VelocityPlayerListener {

    private final McStatCore core;

    VelocityPlayerListener(McStatCore core) {
        this.core = core;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPostLogin(PostLoginEvent event) {
        core.onPlayerJoin(event.getPlayer().getUniqueId(), event.getPlayer().getUsername());
    }

    @Subscribe(order = PostOrder.LAST)
    public void onDisconnect(DisconnectEvent event) {
        // A refused duplicate login (same UUID already online) fires this for the
        // NEW connection; ending the session then would log out the player who
        // is still playing. Any other status is a real departure — and a no-op
        // for a connection that never got a session.
        if (event.getLoginStatus() != DisconnectEvent.LoginStatus.CONFLICTING_LOGIN) {
            core.onPlayerQuit(event.getPlayer().getUniqueId());
        }
    }
}
