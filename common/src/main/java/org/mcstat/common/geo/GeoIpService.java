package org.mcstat.common.geo;

import com.maxmind.db.Reader;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.*;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * Country lookup that never sends a player's IP anywhere: the plugin keeps a
 * local copy of the free DB-IP "IP to Country Lite" database (CC BY 4.0,
 * https://db-ip.com) and resolves addresses on the server itself. Only the
 * two-letter country code is reported to MCStat.
 *
 * The database is refreshed monthly. Any failure leaves lookups returning
 * null; the rest of the plugin is unaffected.
 */
public final class GeoIpService {

    private static final String URL_PATTERN = "https://download.db-ip.com/free/dbip-country-lite-%s.mmdb.gz";
    private static final long MAX_AGE_DAYS = 35;
    private static final long MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024;

    private final Path file;
    private final Logger logger;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private volatile Reader reader;

    public GeoIpService(Path dataDirectory, Logger logger) {
        this.file = dataDirectory.resolve("geo").resolve("dbip-country-lite.mmdb");
        this.logger = logger;
    }

    /** Opens the local copy and downloads a fresh one when it is missing or stale. Blocking; call off-thread. */
    public synchronized void refresh() {
        try {
            if (needsDownload()) download();
            if (reader == null && Files.exists(file)) reader = new Reader(file.toFile());
        } catch (IOException e) {
            logger.warning("Country database unavailable: " + e.getMessage());
        }
    }

    private boolean needsDownload() throws IOException {
        if (!Files.exists(file)) return true;
        Instant modified = Files.getLastModifiedTime(file).toInstant();
        return modified.isBefore(Instant.now().minus(MAX_AGE_DAYS, ChronoUnit.DAYS));
    }

    private void download() throws IOException {
        Files.createDirectories(file.getParent());
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        // A new month's file appears a day or so into the month: fall back to the previous one.
        for (YearMonth candidate : new YearMonth[] { month, month.minusMonths(1) }) {
            Request request = new Request.Builder()
                    .url(String.format(Locale.ROOT, URL_PATTERN, candidate))
                    .header("User-Agent", "McStat-Plugin")
                    .build();
            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) continue;
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (InputStream in = new GZIPInputStream(response.body().byteStream());
                     OutputStream out = Files.newOutputStream(tmp)) {
                    byte[] chunk = new byte[65536];
                    long total = 0;
                    int read;
                    while ((read = in.read(chunk)) != -1) {
                        total += read;
                        if (total > MAX_DOWNLOAD_BYTES) throw new IOException("country database larger than expected");
                        out.write(chunk, 0, read);
                    }
                }
                Reader fresh = new Reader(tmp.toFile());
                fresh.close(); // proves the file parses before it replaces the old one
                Reader old = reader;
                reader = null;
                if (old != null) old.close();
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                reader = new Reader(file.toFile());
                logger.info("Country database updated (DB-IP Lite " + candidate + ").");
                return;
            }
        }
        throw new IOException("could not download the DB-IP country database");
    }

    /** ISO country code for an address, or null (unknown, private, or database missing). */
    @SuppressWarnings("unchecked")
    public String country(InetAddress address) {
        Reader current = reader;
        if (current == null || address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return null;
        }
        try {
            Map<String, Object> record = current.get(address, Map.class);
            if (record == null) return null;
            Object country = record.get("country");
            if (!(country instanceof Map)) return null;
            Object iso = ((Map<String, Object>) country).get("iso_code");
            if (!(iso instanceof String)) return null;
            String code = ((String) iso).toUpperCase(Locale.ROOT);
            return code.matches("[A-Z]{2}") ? code : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public synchronized void close() {
        Reader current = reader;
        reader = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // closing on shutdown
            }
        }
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }
}
