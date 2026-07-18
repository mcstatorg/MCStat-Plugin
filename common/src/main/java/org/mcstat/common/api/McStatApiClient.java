package org.mcstat.common.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.mcstat.common.config.McStatConfig;
import org.mcstat.common.model.PlayerInfo;
import org.mcstat.common.model.ServerHeartbeat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class McStatApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String installationId;
    private final Gson gson;
    private final Logger logger;

    public McStatApiClient(McStatConfig config, String installationId, Logger logger) {
        this.baseUrl = config.getApiBaseUrl();
        this.apiKey = config.getApiKey();
        this.installationId = installationId;
        this.gson = new Gson();
        this.logger = logger;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public boolean validateApiKey() {
        long timestamp = System.currentTimeMillis();
        String signature = computeSignature("GET", "/api/v1/validate", null, timestamp);

        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/validate")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-Installation-Id", installationId)
                .addHeader("X-Timestamp", String.valueOf(timestamp))
                .addHeader("X-Signature", signature)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            logger.warning("[McStat] API key validation failed: " + e.getMessage());
            return false;
        }
    }

    public String validateAndGetServerSlug() {
        long timestamp = System.currentTimeMillis();
        String signature = computeSignature("GET", "/api/v1/validate", null, timestamp);

        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/validate")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-Installation-Id", installationId)
                .addHeader("X-Timestamp", String.valueOf(timestamp))
                .addHeader("X-Signature", signature)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            String body = response.body() != null ? response.body().string() : null;
            if (body == null) return null;
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json.has("server")) {
                JsonObject server = json.getAsJsonObject("server");
                if (server.has("slug")) {
                    return server.get("slug").getAsString();
                }
            }
            return null;
        } catch (IOException e) {
            logger.warning("[McStat] Failed to fetch server slug: " + e.getMessage());
            return null;
        }
    }

    public void sendServerStats(ServerHeartbeat heartbeat, ApiCallback callback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "server_stats");
        payload.addProperty("timestamp", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .format(new java.util.Date(heartbeat.getTimestamp())));

        JsonObject data = new JsonObject();
        data.addProperty("onlinePlayers", heartbeat.getOnlinePlayers());
        data.addProperty("maxPlayers", heartbeat.getMaxPlayers());
        if (heartbeat.getTps() >= 0) {
            data.addProperty("tps", heartbeat.getTps());
        }
        if (heartbeat.getPlayers() != null) {
            com.google.gson.JsonArray players = new com.google.gson.JsonArray();
            for (PlayerInfo player : heartbeat.getPlayers()) {
                JsonObject playerJson = new JsonObject();
                playerJson.addProperty("uuid", player.getUuid().toString());
                playerJson.addProperty("name", player.getName());
                playerJson.addProperty("sessionStartTime", player.getSessionStartTime());
                playerJson.addProperty("totalPlayTimeMillis", player.getTotalPlayTimeMillis());
                players.add(playerJson);
            }
            data.add("players", players);
        }
        payload.add("data", data);

        sendIngest(payload, callback, "server stats");
    }

    public void sendPlayerJoin(String playerUuid, String playerName, ApiCallback callback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "player_join");

        JsonObject data = new JsonObject();
        data.addProperty("playerUuid", playerUuid);
        data.addProperty("playerName", playerName);
        payload.add("data", data);

        sendIngest(payload, callback, "player join");
    }

    public void sendPlayerLeave(String playerUuid, ApiCallback callback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "player_leave");

        JsonObject data = new JsonObject();
        data.addProperty("playerUuid", playerUuid);
        payload.add("data", data);

        sendIngest(payload, callback, "player leave");
    }

    private void sendIngest(JsonObject payload, ApiCallback callback, String eventType) {
        String json = gson.toJson(payload);
        long timestamp = System.currentTimeMillis();
        String signature = computeSignature("POST", "/api/v1/ingest", json, timestamp);

        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/ingest")
                .addHeader("X-API-Key", apiKey)
                .addHeader("X-Installation-Id", installationId)
                .addHeader("X-Timestamp", String.valueOf(timestamp))
                .addHeader("X-Signature", signature)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.warning("[McStat] " + eventType + " send failed: " + e.getMessage());
                if (callback != null) callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                int status = response.code();
                String responseBody = "";
                try {
                    responseBody = response.body() != null ? response.body().string() : "";
                } catch (IOException ignored) {
                    responseBody = "";
                } finally {
                    response.close();
                }
                if (response.isSuccessful()) {
                    if (callback != null) callback.onSuccess();
                } else {
                    String detail = responseBody.isEmpty() ? "" : " - " + responseBody;
                    logger.warning("[McStat] " + eventType + " rejected: HTTP " + status + detail);
                    if (isRetryableStatus(status) && callback != null) {
                        callback.onFailure(new IOException("HTTP " + status + detail));
                    }
                }
            }
        });
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private String computeSignature(String method, String path, String body, long timestamp) {
        try {
            String message = apiKey + "." + method + "." + path + "." + (body != null ? body : "") + "." + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(installationId.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.warning("[McStat] Signature computation failed: " + e.getMessage());
            return "";
        }
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    public interface ApiCallback {
        void onSuccess();
        void onFailure(Exception e);
    }
}
