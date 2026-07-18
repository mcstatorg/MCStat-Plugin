package org.mcstat.common.update;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class McStatUpdateService {

    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/mcstatorg/MCStat-Plugin/releases/latest";
    private static final int MAX_RELEASE_BODY_BYTES = 512_000;

    private final String artifactPrefix;
    private final Path updateDirectory;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final OkHttpClient httpClient;

    private volatile UpdateInfo lastUpdateInfo;
    private volatile String lastMessage = "Update check has not run yet.";

    public McStatUpdateService(String artifactPrefix, Path updateDirectory, Logger logger) {
        this.artifactPrefix = artifactPrefix;
        this.updateDirectory = updateDirectory;
        this.logger = logger;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public synchronized UpdateInfo check(String currentVersion) throws IOException {
        Request request = new Request.Builder()
                .url(LATEST_RELEASE_URL)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "McStat-Plugin/" + currentVersion)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub release check failed: HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("GitHub release check returned an empty response");
            }

            byte[] responseBytes = body.bytes();
            if (responseBytes.length > MAX_RELEASE_BODY_BYTES) {
                throw new IOException("GitHub release response is too large");
            }

            JsonObject release = gson.fromJson(new String(responseBytes, StandardCharsets.UTF_8), JsonObject.class);
            UpdateInfo info = parseRelease(release, currentVersion);
            lastUpdateInfo = info;
            lastMessage = info.getSummary();
            return info;
        }
    }

    public synchronized Path download(UpdateInfo info) throws IOException {
        if (info == null || !info.isUpdateAvailable()) {
            throw new IOException("No update is available");
        }
        if (info.getDownloadUrl() == null || info.getDownloadUrl().isEmpty()) {
            throw new IOException("The latest release does not include " + info.getExpectedAssetName());
        }

        Files.createDirectories(updateDirectory);
        Path target = updateDirectory.resolve(info.getExpectedAssetName());
        Path temp = updateDirectory.resolve(info.getExpectedAssetName() + ".tmp");

        Request request = new Request.Builder()
                .url(info.getDownloadUrl())
                .addHeader("User-Agent", "McStat-Plugin-Updater")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Update download failed: HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Update download returned an empty response");
            }

            try (InputStream in = body.byteStream(); OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }

        if (Files.size(temp) <= 0) {
            Files.deleteIfExists(temp);
            throw new IOException("Downloaded update file is empty");
        }

        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        lastMessage = "Downloaded " + target.getFileName() + ". Stop the server, replace the old jar, then start it again.";
        return target;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public UpdateInfo getLastUpdateInfo() {
        return lastUpdateInfo;
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    private UpdateInfo parseRelease(JsonObject release, String currentVersion) throws IOException {
        if (release == null || !release.has("tag_name")) {
            throw new IOException("GitHub release response is missing tag_name");
        }

        String latestVersion = normalizeVersion(release.get("tag_name").getAsString());
        String releaseUrl = readString(release, "html_url");
        String expectedAsset = artifactPrefix + "-" + latestVersion + ".jar";
        String downloadUrl = null;

        if (release.has("assets") && release.get("assets").isJsonArray()) {
            JsonArray assets = release.getAsJsonArray("assets");
            for (JsonElement assetElement : assets) {
                if (!assetElement.isJsonObject()) continue;
                JsonObject asset = assetElement.getAsJsonObject();
                String name = readString(asset, "name");
                if (expectedAsset.equals(name)) {
                    downloadUrl = readString(asset, "browser_download_url");
                    break;
                }
            }
        }

        boolean available = compareVersions(latestVersion, currentVersion) > 0;
        return new UpdateInfo(currentVersion, latestVersion, available, expectedAsset, downloadUrl, releaseUrl);
    }

    private String readString(JsonObject object, String key) {
        if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
            return object.get(key).getAsString();
        }
        return "";
    }

    private String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return leftValue < rightValue ? -1 : 1;
            }
        }
        return 0;
    }

    private int parseVersionPart(String value) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') break;
            digits.append(c);
        }
        if (digits.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static class UpdateInfo {
        private final String currentVersion;
        private final String latestVersion;
        private final boolean updateAvailable;
        private final String expectedAssetName;
        private final String downloadUrl;
        private final String releaseUrl;

        UpdateInfo(String currentVersion, String latestVersion, boolean updateAvailable,
                   String expectedAssetName, String downloadUrl, String releaseUrl) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.updateAvailable = updateAvailable;
            this.expectedAssetName = expectedAssetName;
            this.downloadUrl = downloadUrl;
            this.releaseUrl = releaseUrl;
        }

        public String getCurrentVersion() { return currentVersion; }
        public String getLatestVersion() { return latestVersion; }
        public boolean isUpdateAvailable() { return updateAvailable; }
        public String getExpectedAssetName() { return expectedAssetName; }
        public String getDownloadUrl() { return downloadUrl; }
        public String getReleaseUrl() { return releaseUrl; }

        public boolean canDownload() {
            return downloadUrl != null && !downloadUrl.isEmpty();
        }

        public String getSummary() {
            if (!updateAvailable) {
                return "McStat is up to date. Current version: " + currentVersion + ".";
            }
            if (canDownload()) {
                return "McStat " + latestVersion + " is available: " + expectedAssetName + ".";
            }
            return "McStat " + latestVersion + " is available, but " + expectedAssetName + " was not found in the release assets.";
        }
    }
}
