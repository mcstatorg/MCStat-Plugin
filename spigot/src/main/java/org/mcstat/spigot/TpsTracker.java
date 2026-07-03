package org.mcstat.spigot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public class TpsTracker implements Runnable {

    private final long[] tickTimestamps = new long[120];
    private int tickIndex = 0;
    private Method getTpsMethod;

    public TpsTracker(JavaPlugin plugin) {
        try {
            getTpsMethod = Bukkit.getServer().getClass().getMethod("getTPS");
        } catch (NoSuchMethodException e) {
            getTpsMethod = null;
        }

        if (getTpsMethod == null) {
            Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 1L);
        }
    }

    @Override
    public void run() {
        tickTimestamps[tickIndex] = System.currentTimeMillis();
        tickIndex = (tickIndex + 1) % tickTimestamps.length;
    }

    public double getTps() {
        if (getTpsMethod != null) {
            try {
                double[] tps = (double[]) getTpsMethod.invoke(Bukkit.getServer());
                return Math.min(tps[0], 20.0);
            } catch (Exception e) {
                return calculateManually();
            }
        }
        return calculateManually();
    }

    private double calculateManually() {
        int prevIndex = (tickIndex == 0) ? tickTimestamps.length - 1 : tickIndex - 1;
        int sampleSize = 100;
        int targetIndex = (tickIndex - sampleSize + tickTimestamps.length) % tickTimestamps.length;

        long newest = tickTimestamps[prevIndex];
        long oldest = tickTimestamps[targetIndex];

        if (newest == 0 || oldest == 0) return 20.0;

        double seconds = (newest - oldest) / 1000.0;
        if (seconds <= 0) return 20.0;

        return Math.min(sampleSize / seconds, 20.0);
    }
}
