package com.examsystem.client.config;

import java.net.URI;
import java.util.Objects;

public class ServerConfig {
    public static final String DEFAULT_SERVER_URL = "http://localhost:8089";
    public static final String ENV_VAR = "EXASECURE_SERVER_URL";
    public static final String SYS_PROP = "exasecure.server.url";

    private final String baseUrl;

    public ServerConfig() {
        this(resolveUrl());
    }

    public ServerConfig(String rawUrl) {
        this.baseUrl = normalizeUrl(rawUrl);
    }

    private static String resolveUrl() {
        String prop = System.getProperty(SYS_PROP);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(ENV_VAR);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return DEFAULT_SERVER_URL;
    }

    private static String normalizeUrl(String raw) {
        String url = (raw == null || raw.isBlank()) ? DEFAULT_SERVER_URL : raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Server URL must use http or https scheme: " + raw);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid server URL: " + raw, e);
        }
        return url;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiV1Url() {
        return baseUrl + "/api/v1";
    }

    public String healthUrl() {
        return baseUrl + "/health";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerConfig that = (ServerConfig) o;
        return Objects.equals(baseUrl, that.baseUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseUrl);
    }

    @Override
    public String toString() {
        return baseUrl;
    }
}
