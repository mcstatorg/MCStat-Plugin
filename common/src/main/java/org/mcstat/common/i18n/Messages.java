package org.mcstat.common.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin messages in the 35 languages MCStat itself is available in.
 *
 * Players see their own client language; the console uses `language` from the
 * config (English by default). Bundles are UTF-8 — Java 8's ResourceBundle
 * would read them as ISO-8859-1 and mangle every non-Latin script — and
 * arguments are substituted by plain {0}/{1} replacement rather than
 * MessageFormat, which treats the apostrophes in French or Italian text as
 * quoting and silently eats them.
 */
public final class Messages {

    public static final String DEFAULT = "en";

    /** Same codes as the MCStat website (and its URL prefixes). */
    public static final Set<String> SUPPORTED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "en", "ru", "tr", "de", "fr", "es", "pt-BR", "pt-PT", "zh-Hans", "zh-Hant", "ja", "ko", "hi", "id",
            "pl", "it", "nl", "sv", "fil", "vi", "th", "uk", "ar", "ms", "af", "no", "fi", "da", "ro", "cs",
            "el", "hu", "he", "bn", "ur")));

    private static final Map<String, Properties> BUNDLES = new ConcurrentHashMap<>();

    private Messages() {}

    /**
     * Maps a client locale ("tr_tr", "pt_BR", "zh-TW", "nb_no") or a config
     * value ("tr", "pt-BR") to a supported language code.
     */
    public static String resolve(String locale) {
        if (locale == null || locale.trim().isEmpty()) return DEFAULT;
        String normalized = locale.trim().replace('_', '-');
        for (String code : SUPPORTED) {
            if (code.equalsIgnoreCase(normalized)) return code;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        String language = lower.split("-")[0];
        String region = lower.contains("-") ? lower.substring(lower.indexOf('-') + 1) : "";
        switch (language) {
            case "pt": return region.equals("pt") ? "pt-PT" : "pt-BR";
            case "zh": return region.equals("tw") || region.equals("hk") || region.equals("mo") || region.equals("hant") ? "zh-Hant" : "zh-Hans";
            case "nb":
            case "nn":
            case "no": return "no";
            case "tl":
            case "fil": return "fil";
            case "iw": return "he";
            case "in": return "id";
            default: return SUPPORTED.contains(language) ? language : DEFAULT;
        }
    }

    public static String get(String language, String key, Object... args) {
        String text = bundle(resolve(language)).getProperty(key);
        if (text == null) text = bundle(DEFAULT).getProperty(key, key);
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    private static Properties bundle(String code) {
        return BUNDLES.computeIfAbsent(code, c -> {
            Properties properties = new Properties();
            try (InputStream in = Messages.class.getResourceAsStream("/messages/messages_" + c + ".properties")) {
                if (in != null) {
                    try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        properties.load(reader);
                    }
                }
            } catch (IOException ignored) {
                // Missing or unreadable bundle: callers fall back to English.
            }
            return properties;
        });
    }
}
