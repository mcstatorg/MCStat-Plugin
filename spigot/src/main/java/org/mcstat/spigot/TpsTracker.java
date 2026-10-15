package org.mcstat.spigot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ticks-per-second for Spigot builds that have no getTPS() (Paper and Folia
 * do, and use that). Counts real ticks with a 1-tick task over the last ~5s.
 */
final class TpsTracker implements Runnable {

    private static final int SAMPLE = 100;
    private final long[] tickTimes = new long[SAMPLE + 1];
    private int index;
    private int filled;

    TpsTracker(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    @Override
    public synchronized void run() {
        tickTimes[index] = System.nanoTime();
        index = (index + 1) % tickTimes.length;
        if (filled < tickTimes.length) filled++;
    }

    synchronized Double getTps() {
        if (filled < tickTimes.length) return null;
        long newest = tickTimes[(index - 1 + tickTimes.length) % tickTimes.length];
        long oldest = tickTimes[index];
        double seconds = (newest - oldest) / 1_000_000_000.0;
        return seconds > 0 ? Math.min(20.0, SAMPLE / seconds) : null;
    }
}
