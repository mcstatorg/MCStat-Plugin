package org.mcstat.common.platform;

import java.net.InetAddress;

/**
 * Connection facts about a player. The address is only used on this machine
 * to resolve a country code; it is never sent anywhere.
 */
public final class JoinInfo {

    public final String hostname;
    public final Integer protocol;
    public final String brand;
    public final String locale;
    public final InetAddress address;
    public final boolean bedrock;
    /** Epoch millis this server first saw the player, 0 when unknown. */
    public final long firstPlayed;

    public JoinInfo(String hostname, Integer protocol, String brand, String locale,
                    InetAddress address, boolean bedrock, long firstPlayed) {
        this.hostname = hostname;
        this.protocol = protocol;
        this.brand = brand;
        this.locale = locale;
        this.address = address;
        this.bedrock = bedrock;
        this.firstPlayed = firstPlayed;
    }
}
