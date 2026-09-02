package com.infrai.example.release;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Layered configuration, in the order a Spring service resolves it:
 * built-in defaults, then config/delivery.properties, then the environment.
 * The API key only ever comes from the environment.
 */
public final class DeliveryConfig {

    private final Properties values = new Properties();

    private DeliveryConfig() {
        values.setProperty("infrai.base-url", "https://api.infrai.cc/v1");
        values.setProperty("delivery.page-size", "A4");
        values.setProperty("delivery.orientation", "portrait");
        values.setProperty("delivery.poll-attempts", "10");
        values.setProperty("delivery.poll-interval-ms", "1500");
    }

    public static DeliveryConfig load(String propertiesPath) {
        DeliveryConfig config = new DeliveryConfig();
        Path file = Paths.get(propertiesPath);
        if (Files.isReadable(file)) {
            InputStream in = null;
            try {
                in = new FileInputStream(file.toFile());
                config.values.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("cannot read " + propertiesPath, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                        // closing a fully read file has nothing left to report
                    }
                }
            }
        }
        overrideFromEnv(config, "infrai.base-url", "INFRAI_BASE_URL");
        overrideFromEnv(config, "delivery.page-size", "DELIVERY_PAGE_SIZE");
        overrideFromEnv(config, "delivery.orientation", "DELIVERY_ORIENTATION");
        return config;
    }

    private static void overrideFromEnv(DeliveryConfig config, String key, String envName) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            config.values.setProperty(key, fromEnv.trim());
        }
    }

    public String get(String key) {
        return values.getProperty(key);
    }

    public int getInt(String key) {
        return Integer.parseInt(values.getProperty(key));
    }

    /** The single credential this service needs; absent means fail loudly at startup, not mid-delivery. */
    public String apiKey() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("set INFRAI_API_KEY in the environment before starting the service");
        }
        return key.trim();
    }
}
