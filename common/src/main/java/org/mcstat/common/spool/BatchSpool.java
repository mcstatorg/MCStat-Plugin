package org.mcstat.common.spool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.mcstat.common.api.McStatApiClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * At-least-once delivery of plugin events, applied at most once by the server.
 *
 * Events are collected in memory, sealed every few seconds into numbered batch
 * files (gzip, written atomically), and a batch file is deleted only after
 * MCStat accepted it. A batch carries a fixed id, so the server ignores a
 * retry of a batch it already applied. Nothing is lost to a restart, a crash
 * after sealing, or an MCStat outage: the spool simply drains when the API is
 * back.
 *
 * Bounded on both ends: the in-memory buffer drops its oldest events past
 * MAX_BUFFERED, and the spool directory drops its oldest batches past
 * MAX_SPOOL_BYTES — an install that cannot reach MCStat for days must not fill
 * the server's disk.
 */
public final class BatchSpool {

    private static final int MAX_EVENTS_PER_BATCH = 500;
    private static final int MAX_BUFFERED = 20_000;
    private static final long MAX_SPOOL_BYTES = 20L * 1024 * 1024;
    private static final int KEEP_REJECTED = 5;
    private static final String SUFFIX = ".json.gz";

    public enum Outcome { IDLE, SENT, RETRY_LATER }

    private final Path dir;
    private final Path rejectedDir;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final ConcurrentLinkedQueue<JsonObject> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger buffered = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();
    private volatile long lastSentAt;
    private volatile String lastError;

    public BatchSpool(Path dataDirectory, Logger logger) {
        this.dir = dataDirectory.resolve("queue");
        this.rejectedDir = dir.resolve("rejected");
        this.logger = logger;
    }

    /** Removes half-written batch files left by a crash during a previous write. */
    public synchronized void recover() {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).forEach(BatchSpool::deleteQuietly);
        } catch (IOException e) {
            logger.warning("Could not clean the event spool: " + e.getMessage());
        }
    }

    /** Queues one event. Safe from any thread. */
    public void add(JsonObject event) {
        buffer.add(event);
        if (buffered.incrementAndGet() > MAX_BUFFERED && buffer.poll() != null) {
            buffered.decrementAndGet();
            dropped.incrementAndGet();
        }
    }

    /** Writes everything buffered into batch files. */
    public synchronized void seal() {
        while (!buffer.isEmpty()) {
            JsonArray events = new JsonArray();
            JsonObject event;
            while (events.size() < MAX_EVENTS_PER_BATCH && (event = buffer.poll()) != null) {
                buffered.decrementAndGet();
                events.add(event);
            }
            if (events.size() == 0) return;
            JsonObject batch = new JsonObject();
            batch.addProperty("v", 2);
            batch.addProperty("id", UUID.randomUUID().toString());
            batch.add("events", events);
            write(batch);
        }
        int lost = dropped.getAndSet(0);
        if (lost > 0) logger.warning("Event buffer overflowed; dropped " + lost + " oldest events.");
    }

    private void write(JsonObject batch) {
        try {
            Files.createDirectories(dir);
            // Time-ordered names: batches are replayed in the order they were sealed.
            String name = String.format("%013d-%s%s", System.currentTimeMillis(), batch.get("id").getAsString(), SUFFIX);
            Path tmp = dir.resolve(name + ".tmp");
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp))) {
                out.write(gson.toJson(batch).getBytes(StandardCharsets.UTF_8));
            }
            try {
                Files.move(tmp, dir.resolve(name), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            enforceCap();
        } catch (IOException e) {
            logger.warning("Could not write event batch to disk: " + e.getMessage());
        }
    }

    private void enforceCap() throws IOException {
        List<Path> files = pending();
        long total = 0;
        for (Path file : files) total += Files.size(file);
        int removed = 0;
        for (int i = 0; i < files.size() && total > MAX_SPOOL_BYTES; i++) {
            total -= Files.size(files.get(i));
            Files.deleteIfExists(files.get(i));
            removed++;
        }
        if (removed > 0) logger.warning("Event spool over " + (MAX_SPOOL_BYTES >> 20) + " MB; dropped " + removed + " oldest batches.");
    }

    private List<Path> pending() throws IOException {
        if (!Files.isDirectory(dir)) return Collections.emptyList();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(SUFFIX)).forEach(files::add);
        }
        Collections.sort(files);
        return files;
    }

    /**
     * Sends sealed batches oldest first, stopping at the first failure worth
     * retrying. `onConfig` receives the remote config carried by each answer.
     */
    public synchronized Outcome send(McStatApiClient client, Consumer<JsonObject> onConfig) {
        List<Path> files;
        try {
            files = pending();
        } catch (IOException e) {
            lastError = e.getMessage();
            return Outcome.RETRY_LATER;
        }
        if (files.isEmpty()) return Outcome.IDLE;

        for (Path file : files) {
            byte[] gzipped;
            String json;
            try {
                gzipped = Files.readAllBytes(file);
                json = gunzip(gzipped);
            } catch (IOException e) {
                // Unreadable (disk error, manual edit): it can never be sent, so drop it.
                logger.warning("Dropping unreadable batch " + file.getFileName() + ": " + e.getMessage());
                deleteQuietly(file);
                continue;
            }

            McStatApiClient.Response response = client.sendBatch(json, gzipped);
            if (response.ok()) {
                deleteQuietly(file);
                lastSentAt = System.currentTimeMillis();
                lastError = null;
                if (response.data != null && response.data.has("config") && response.data.get("config").isJsonObject()) {
                    onConfig.accept(response.data.getAsJsonObject("config"));
                }
                continue;
            }
            lastError = "HTTP " + response.status + (response.detail.isEmpty() ? "" : " - " + response.detail);
            if (response.retryable() || response.status == 401 || response.status == 403) {
                // 401/403: the key or binding is wrong right now; keep the data
                // until the owner fixes it rather than throwing it away.
                return Outcome.RETRY_LATER;
            }
            // Any other 4xx will never succeed: set it aside so it cannot block
            // everything queued behind it.
            logger.warning("MCStat rejected a batch (" + lastError + "); moved to queue/rejected.");
            quarantine(file);
        }
        return Outcome.SENT;
    }

    private void quarantine(Path file) {
        try {
            Files.createDirectories(rejectedDir);
            Files.move(file, rejectedDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            List<Path> kept = new ArrayList<>();
            try (Stream<Path> stream = Files.list(rejectedDir)) {
                stream.forEach(kept::add);
            }
            Collections.sort(kept);
            for (int i = 0; i < kept.size() - KEEP_REJECTED; i++) deleteQuietly(kept.get(i));
        } catch (IOException e) {
            deleteQuietly(file);
        }
    }

    private static String gunzip(byte[] bytes) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length * 4);
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) out.write(chunk, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Next pass retries.
        }
    }

    public int bufferedEvents() {
        return buffered.get();
    }

    public int spooledBatches() {
        try {
            return pending().size();
        } catch (IOException e) {
            return -1;
        }
    }

    public long lastSentAt() {
        return lastSentAt;
    }

    public String lastError() {
        return lastError;
    }
}
