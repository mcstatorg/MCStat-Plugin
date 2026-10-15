package org.mcstat.common.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import okhttp3.*;
import org.mcstat.common.config.McStatConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HTTPS client for the MCStat plugin API (protocol v2).
 *
 * Every call is synchronous and made from the core's background thread; the
 * caller decides what a failure means (retry later, drop, pause).
 */
public class McStatApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_DETAIL_LENGTH = 240;

    /** What a request signs: the canonical API path and the uncompressed JSON body. */
    private static final class Signed {
        final String path;
        final String body;

        Signed(String path, String body) {
            this.path = path;
            this.body = body;
        }
    }

    public static final class Response {
        public final int status;
        public final JsonObject data;
        public final String detail;

        Response(int status, JsonObject data, String detail) {
            this.status = status;
            this.data = data;
            this.detail = detail;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        /** 0 (network error), 408, 429 and 5xx are worth retrying; other 4xx are not. */
        public boolean retryable() {
            return status == 0 || status == 408 || status == 429 || status >= 500;
        }
    }

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String installationId;
    private final Gson gson = new Gson();
    private final Logger logger;

    public McStatApiClient(McStatConfig config, String installationId, Logger logger) {
        this.baseUrl = config.getApiBaseUrl();
        this.apiKey = config.getApiKey();
        this.installationId = installationId;
        this.logger = logger;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(this::signRequest)
                .build();
    }

    public OkHttpClient http() {
        return httpClient;
    }

    /** GET /api/v1/validate — is the key valid, and which server is it for. */
    public Response validate() {
        Request request = base("/api/v1/validate", null).get().build();
        return execute(request);
    }

    /** POST /api/v1/ingest/hello — handshake; the answer carries the remote config. */
    public Response hello(JsonObject payload) {
        String json = gson.toJson(payload);
        Request request = base("/api/v1/ingest/hello", json)
                .post(RequestBody.create(json, JSON))
                .build();
        return execute(request);
    }

    /**
     * POST /api/v1/ingest/batch — one spooled batch. The body travels gzip
     * compressed; the signature covers the JSON before compression, which is
     * what the server verifies after decompressing it.
     */
    public Response sendBatch(String json, byte[] gzipped) {
        Request request = base("/api/v1/ingest/batch", json)
                .header("Content-Encoding", "gzip")
                .post(RequestBody.create(gzipped, JSON))
                .build();
        return execute(request);
    }

    private Request.Builder base(String path, String body) {
        return new Request.Builder()
                .url(baseUrl + path)
                .tag(Signed.class, new Signed(path, body))
                .header("X-API-Key", apiKey)
                .header("X-Installation-Id", installationId)
                .header("User-Agent", "McStat-Plugin");
    }

    private Response execute(Request request) {
        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            JsonObject data = null;
            try {
                JsonElement parsed = gson.fromJson(text, JsonElement.class);
                if (parsed != null && parsed.isJsonObject()) {
                    JsonObject root = parsed.getAsJsonObject();
                    // createSuccessResponse wraps payloads as {"data": …, "success": true}.
                    data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;
                }
            } catch (JsonParseException ignored) {
                // Non-JSON error page (proxy, Cloudflare): keep the status, show a short detail.
            }
            return new Response(response.code(), data, response.isSuccessful() ? "" : detail(text));
        } catch (IOException e) {
            return new Response(0, null, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static String detail(String text) {
        if (text == null) return "";
        String trimmed = text.replaceAll("\\s+", " ").trim();
        return trimmed.length() > MAX_DETAIL_LENGTH ? trimmed.substring(0, MAX_DETAIL_LENGTH) + "..." : trimmed;
    }

    /**
     * Stamps X-Timestamp and X-Signature when the call actually goes out, not
     * when it was built: a request that waited in OkHttp's dispatcher (or a
     * retry) is signed fresh instead of arriving with an expired stamp, which
     * a server owner would read as a broken API key.
     */
    private okhttp3.Response signRequest(Interceptor.Chain chain) throws IOException {
        Request original = chain.request();
        Signed signed = original.tag(Signed.class);
        String path = signed != null ? signed.path : original.url().encodedPath();
        String body = signed != null && signed.body != null ? signed.body : "";
        long timestamp = System.currentTimeMillis();
        Request stamped = original.newBuilder()
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Signature", computeSignature(original.method(), path, body, timestamp))
                .build();
        return chain.proceed(stamped);
    }

    private String computeSignature(String method, String path, String body, long timestamp) {
        try {
            String message = apiKey + "." + method + "." + path + "." + body + "." + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(installationId.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.warning("Signature computation failed: " + e.getMessage());
            return "";
        }
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
