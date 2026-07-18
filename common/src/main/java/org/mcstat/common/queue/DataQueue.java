package org.mcstat.common.queue;

import org.mcstat.common.model.ServerHeartbeat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DataQueue {

    private static final int MAX_QUEUE_SIZE = 100;

    private final ConcurrentLinkedQueue<ServerHeartbeat> pendingHeartbeats = new ConcurrentLinkedQueue<>();

    public void enqueue(ServerHeartbeat heartbeat) {
        pendingHeartbeats.add(heartbeat);
        while (pendingHeartbeats.size() > MAX_QUEUE_SIZE) {
            pendingHeartbeats.poll();
        }
    }

    public List<ServerHeartbeat> drainAll() {
        List<ServerHeartbeat> drained = new ArrayList<>();
        ServerHeartbeat hb;
        while ((hb = pendingHeartbeats.poll()) != null) {
            drained.add(hb);
        }
        return drained;
    }

    public List<ServerHeartbeat> drainUpTo(int maxItems) {
        List<ServerHeartbeat> drained = new ArrayList<>();
        ServerHeartbeat hb;
        while (drained.size() < maxItems && (hb = pendingHeartbeats.poll()) != null) {
            drained.add(hb);
        }
        return drained;
    }

    public int size() {
        return pendingHeartbeats.size();
    }

    public boolean isEmpty() {
        return pendingHeartbeats.isEmpty();
    }
}
