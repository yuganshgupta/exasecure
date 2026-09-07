package com.examsystem.client.config;

/** Explicit configuration mode; no working-directory or executable detection. */
public enum ConfigurationMode {
    DEVELOPMENT, INSTALLED;

    public static ConfigurationMode current() {
        return resolve(System.getProperty("exasecure.config.mode"), System.getenv("EXASECURE_CONFIG_MODE"));
    }

    public static ConfigurationMode resolve(String property, String environment) {
        String value = property != null && !property.isBlank() ? property : environment;
        if (value == null || value.isBlank() || value.trim().equalsIgnoreCase("development")) return DEVELOPMENT;
        if (value.trim().equalsIgnoreCase("installed")) return INSTALLED;
        throw new IllegalArgumentException("Configuration mode must be development or installed.");
    }
}
