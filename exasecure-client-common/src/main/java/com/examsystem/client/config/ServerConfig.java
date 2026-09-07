package com.examsystem.client.config;

import java.net.URI;
import java.util.Objects;
import java.util.Locale;

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

    /** Preserves an optional reverse-proxy base path; strips trailing slashes and folds scheme/host case. */
    private static String normalizeUrl(String raw) {
        try {
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException();
            URI uri = URI.create(raw.trim()).parseServerAuthority();
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535
                    || uri.getRawAuthority().endsWith(":")) throw new IllegalArgumentException();
            String path = uri.getRawPath();
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return scheme.toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                    + (uri.getPort() == -1 ? "" : ":" + uri.getPort()) + path;
        } catch (Exception e) {
            // Never echo rejected input: it may contain credentials.
            throw new IllegalArgumentException("Enter an absolute HTTP or HTTPS URL with a host and valid port (1-65535), without credentials, query, or fragment.");
        }
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
