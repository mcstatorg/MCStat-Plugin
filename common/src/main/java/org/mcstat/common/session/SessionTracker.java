package org.mcstat.common.session;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Who is online, since when, and how much of that time was AFK.
 *
 * AFK is measured, not guessed: every gap between two activities (moving,
 * turning, chatting, running a command, interacting) longer than the AFK
 * threshold counts as AFK for the part beyond the threshold. activity() only
 * stores a timestamp, so calling it from a movement listener is cheap.
 */
public final class SessionTracker {

    public static final class Session {
        public final UUID uuid;
        public final String name;
        public final long joinedAt;
        /** Set once the join event has been queued (see McStatCore). */
        public final AtomicBoolean joinSent = new AtomicBoolean(false);
        private long lastActivity;
        private long afkMillis;

        Session(UUID uuid, String name, long joinedAt) {
            this.uuid = uuid;
            this.name = name;
            this.joinedAt = joinedAt;
            this.lastActivity = joinedAt;
        }

        synchronized void activity(long now, long thresholdMillis) {
            long idle = now - lastActivity;
            if (idle > thresholdMillis) afkMillis += idle - thresholdMillis;
            lastActivity = Math.max(lastActivity, now);
        }

        /** AFK seconds so far, counting the idle stretch in progress. */
        public synchronized long afkSeconds(long now, long thresholdMillis) {
            long idle = now - lastActivity;
            long current = idle > thresholdMillis ? idle - thresholdMillis : 0;
            return (afkMillis + current) / 1000L;
        }
    }

    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile long thresholdMillis = 5 * 60_000L;

    public void setAfkMinutes(int minutes) {
        this.thresholdMillis = Math.max(1, minutes) * 60_000L;
    }

    public long thresholdMillis() {
        return thresholdMillis;
    }

    public Session start(UUID uuid, String name, long now) {
        Session session = new Session(uuid, name, now);
        sessions.put(uuid, session);
        return session;
    }

    public Session end(UUID uuid) {
        return sessions.remove(uuid);
    }

    public Session get(UUID uuid) {
        return sessions.get(uuid);
    }

    public void activity(UUID uuid, long now) {
        Session session = sessions.get(uuid);
        if (session != null) session.activity(now, thresholdMillis);
    }

    public List<Session> snapshot() {
        return new ArrayList<>(sessions.values());
    }

    public int size() {
        return sessions.size();
    }

    public void clear() {
        sessions.clear();
    }
}
