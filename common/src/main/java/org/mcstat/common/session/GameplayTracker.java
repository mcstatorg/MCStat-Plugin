package org.mcstat.common.session;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player gameplay counters between two reports. Listeners add to them from
 * any thread; the heartbeat (or the player's quit) drains them into a delta
 * that is sent once and reset. Only counts are kept — never chat or command
 * text.
 */
public final class GameplayTracker {

    public enum Counter {
        BLOCKS_BROKEN("bb"), BLOCKS_PLACED("bp"), MOB_KILLS("mk"), PLAYER_KILLS("pk"), DEATHS("d"),
        ITEMS_CRAFTED("cr"), FISH_CAUGHT("fi"), ITEMS_ENCHANTED("en"), XP_GAINED("xp"),
        MESSAGES("msg"), COMMANDS("cmd"), ADVANCEMENTS("adv");

        final String wire;

        Counter(String wire) {
            this.wire = wire;
        }
    }

    public enum Breakdown {
        BLOCKS("bbm"), MOBS("mkm"), DEATH_CAUSES("dc");

        final String wire;

        Breakdown(String wire) {
            this.wire = wire;
        }
    }

    /** Distinct keys per breakdown per report; the rest are counted as "other". */
    private static final int MAX_KEYS = 63;

    private static final class Stats {
        final long[] counts = new long[Counter.values().length];
        double distance;
        @SuppressWarnings("unchecked")
        final Map<String, Integer>[] breakdowns = new Map[] { new HashMap<>(), new HashMap<>(), new HashMap<>() };
        boolean empty = true;
    }

    private final ConcurrentHashMap<UUID, Stats> players = new ConcurrentHashMap<>();

    private Stats stats(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> new Stats());
    }

    public void add(UUID uuid, Counter counter, long amount) {
        if (amount <= 0) return;
        Stats s = stats(uuid);
        synchronized (s) {
            s.counts[counter.ordinal()] += amount;
            s.empty = false;
        }
    }

    public void addDistance(UUID uuid, double meters) {
        if (!(meters > 0)) return;
        Stats s = stats(uuid);
        synchronized (s) {
            s.distance += meters;
            s.empty = false;
        }
    }

    public void add(UUID uuid, Breakdown breakdown, String key) {
        String clean = normalize(key);
        Stats s = stats(uuid);
        synchronized (s) {
            Map<String, Integer> map = s.breakdowns[breakdown.ordinal()];
            if (!map.containsKey(clean) && map.size() >= MAX_KEYS) clean = "other";
            map.merge(clean, 1, Integer::sum);
            s.empty = false;
        }
    }

    /** Lower-case id the server accepts ([a-z0-9_:.-], at most 48 chars). */
    private static String normalize(String key) {
        if (key == null) return "unknown";
        String lower = key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_:.-]", "_");
        if (lower.isEmpty()) return "unknown";
        return lower.length() > 48 ? lower.substring(0, 48) : lower;
    }

    /** Everything since the last drain as a protocol delta, or null when nothing happened. */
    public JsonObject drain(UUID uuid) {
        Stats s = players.get(uuid);
        if (s == null) return null;
        synchronized (s) {
            if (s.empty) return null;
            JsonObject delta = new JsonObject();
            for (Counter counter : Counter.values()) {
                long value = Math.min(1_000_000L, s.counts[counter.ordinal()]);
                if (value > 0) delta.addProperty(counter.wire, value);
            }
            long meters = Math.min(1_000_000L, Math.round(s.distance));
            if (meters > 0) delta.addProperty("dist", meters);
            for (Breakdown breakdown : Breakdown.values()) {
                Map<String, Integer> map = s.breakdowns[breakdown.ordinal()];
                if (map.isEmpty()) continue;
                JsonObject obj = new JsonObject();
                for (Map.Entry<String, Integer> entry : map.entrySet()) obj.addProperty(entry.getKey(), Math.min(1_000_000, entry.getValue()));
                delta.add(breakdown.wire, obj);
                map.clear();
            }
            java.util.Arrays.fill(s.counts, 0);
            // Keep the sub-metre remainder so slow walking still adds up.
            s.distance -= meters;
            s.empty = true;
            return delta.size() > 0 ? delta : null;
        }
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public void clear() {
        players.clear();
    }
}
