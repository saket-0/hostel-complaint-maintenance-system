package edu.vit.hcms.core.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton configuration holder for application-wide settings.
 * Loads from environment variables with sane defaults.
 * Thread-safe via ConcurrentHashMap and volatile instance.
 *
 * <p>Demonstrates the Singleton design pattern with lazy initialization.
 */
public final class AppConfig {

    private static volatile AppConfig instance;
    private final Map<String, String> properties = new ConcurrentHashMap<>();

    // Default configuration values
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("DB_URL", "jdbc:mysql://localhost:3306/hcms?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"),
            Map.entry("DB_USER", "hcms_user"),
            Map.entry("DB_PASSWORD", "hcms_pass"),
            Map.entry("DB_POOL_SIZE", "10"),
            Map.entry("JWT_SECRET", "change-me-in-production-use-env-variable"),
            Map.entry("JWT_EXPIRY_HOURS", "24"),
            Map.entry("PORT", "8080"),
            Map.entry("SLA_HIGH_HOURS", "24"),
            Map.entry("SLA_MEDIUM_HOURS", "72"),
            Map.entry("SLA_LOW_HOURS", "168"),
            Map.entry("ESCALATION_CHECK_MINUTES", "15"),
            Map.entry("AUTO_CLOSE_DAYS", "7"),
            Map.entry("REOPEN_WINDOW_DAYS", "7"),
            Map.entry("DUPLICATE_WINDOW_MINUTES", "10"),
            Map.entry("UPLOAD_DIR", "./uploads"),
            Map.entry("MAX_UPLOAD_SIZE_BYTES", "5242880"),
            Map.entry("SMTP_ENABLED", "false"),
            Map.entry("SMTP_HOST", ""),
            Map.entry("SMTP_PORT", "587"),
            Map.entry("SMTP_USER", ""),
            Map.entry("SMTP_PASSWORD", ""),
            Map.entry("PROFILE", "dev"),
            Map.entry("CORS_ORIGIN", "*"),
            Map.entry("RATE_LIMIT_LOGIN", "10"),
            Map.entry("RATE_LIMIT_COMPLAINT", "5")
    );

    /** Private constructor for singleton enforcement. */
    private AppConfig() {
        // Load defaults first, then override with environment variables
        properties.putAll(DEFAULTS);
        DEFAULTS.keySet().forEach(key -> {
            String envValue = System.getenv(key);
            if (envValue != null && !envValue.isBlank()) {
                properties.put(key, envValue);
            }
        });
    }

    /**
     * Returns the singleton instance (double-checked locking).
     *
     * @return the AppConfig singleton
     */
    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }

    /**
     * Gets a configuration value by key.
     *
     * @param key the configuration key
     * @return the value, or null if not set
     */
    public String get(String key) {
        return properties.get(key);
    }

    /**
     * Gets a configuration value, returning a default if not set.
     *
     * @param key          the configuration key
     * @param defaultValue fallback value
     * @return the value or the default
     */
    public String get(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a configuration value as an integer.
     *
     * @param key the configuration key
     * @return the integer value
     */
    public int getInt(String key) {
        return Integer.parseInt(properties.get(key));
    }

    /**
     * Gets a configuration value as a long.
     *
     * @param key the configuration key
     * @return the long value
     */
    public long getLong(String key) {
        return Long.parseLong(properties.get(key));
    }

    /**
     * Gets a configuration value as a boolean.
     *
     * @param key the configuration key
     * @return the boolean value
     */
    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(properties.get(key));
    }

    /** Returns true if the current profile is "dev". */
    public boolean isDevProfile() {
        return "dev".equalsIgnoreCase(get("PROFILE"));
    }

    /**
     * Sets a configuration value at runtime (for testing).
     *
     * @param key   the key
     * @param value the value
     */
    public void set(String key, String value) {
        properties.put(key, value);
    }

    /** Resets the singleton (for testing only). */
    public static synchronized void resetForTesting() {
        instance = null;
    }
}
