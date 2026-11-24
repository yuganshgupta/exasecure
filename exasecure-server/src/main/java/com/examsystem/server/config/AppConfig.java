package com.examsystem.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Map;
import java.nio.file.*;
import java.net.URI;

public class AppConfig {
    public final int port;
    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;
    public final boolean loopbackOnly;
    private final boolean installed;

    public AppConfig() {
        this(System.getProperties(), System.getenv(), Path.of(""));
    }

    /** Injectable inputs keep configuration tests independent of real credentials and CWD. */
    public AppConfig(Properties system, Map<String, String> environment, Path developmentDirectory) {
        String mode = value(system, environment, "exasecure.config.mode", "EXASECURE_CONFIG_MODE", "development", false);
        if (!mode.equalsIgnoreCase("development") && !mode.equalsIgnoreCase("installed")) {
            throw new IllegalArgumentException("Configuration mode must be development or installed.");
        }
        installed = mode.equalsIgnoreCase("installed");
        String bind = value(system, environment, "exasecure.server.loopbackOnly",
                "EXASECURE_SERVER_LOOPBACK_ONLY", "false", false);
        if (!bind.equalsIgnoreCase("true") && !bind.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("EXASECURE_SERVER_LOOPBACK_ONLY must be true or false.");
        }
        loopbackOnly = Boolean.parseBoolean(bind);
        Properties fileProps;
        if (installed) {
            String reference = value(system, environment, "exasecure.server.config", "EXASECURE_SERVER_CONFIG", "", false);
            try {
                Path file = Path.of(reference);
                if (reference.isBlank() || !file.isAbsolute() || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException();
                }
                fileProps = new Properties();
                try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { fileProps.load(input); }
            } catch (IOException | IllegalArgumentException e) {
                throw new IllegalArgumentException("Installed Server requires a readable absolute config-file path in exasecure.server.config or EXASECURE_SERVER_CONFIG.");
            }
            this.dbUrl = required(fileProps, "db.url");
            validateInstalledUrl(dbUrl);
            this.dbUser = required(fileProps, "db.user");
            this.dbPassword = fileProps.getProperty("db.password");
            if (dbPassword == null || dbPassword.isEmpty()) {
                throw new IllegalArgumentException("Installed Server requires a non-empty db.password.");
            }
            this.port = parsePort(required(fileProps, "server.port"));
            return;
        }
        fileProps = loadDbProperties(developmentDirectory);

        String defaultPort = fileProps.getProperty("server.port", "8089");
        this.port = Integer.parseInt(value(system, environment, "SERVER_PORT", "SERVER_PORT", defaultPort, false));

        String defaultDbUrl = fileProps.getProperty("db.url", "jdbc:mysql://localhost:3306/secure_exam_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC");
        this.dbUrl = value(system, environment, "DB_URL", "DB_URL", value(system, environment, "EXASECURE_DB_URL", "EXASECURE_DB_URL", defaultDbUrl, false), false);

        String defaultDbUser = fileProps.getProperty("db.user", "root");
        this.dbUser = value(system, environment, "DB_USER", "DB_USER", value(system, environment, "EXASECURE_DB_USER", "EXASECURE_DB_USER", defaultDbUser, false), false);

        String defaultDbPassword = fileProps.getProperty("db.password", "");
        this.dbPassword = value(system, environment, "DB_PASSWORD", "DB_PASSWORD", value(system, environment, "EXASECURE_DB_PASSWORD", "EXASECURE_DB_PASSWORD", defaultDbPassword, true), true);
    }

    public AppConfig(int port, String dbUrl, String dbUser, String dbPassword) {
        this.installed = false;
        this.loopbackOnly = false;
        this.port = port;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    private static String value(Properties system, Map<String, String> environment, String property, String env, String defaultValue, boolean opaque) {
        String val = system.getProperty(property);
        if (val != null && (opaque || !val.isBlank())) {
            return opaque ? val : val.trim();
        }
        val = environment.get(env);
        if (val != null && (opaque || !val.isBlank())) {
            return opaque ? val : val.trim();
        }
        return defaultValue;
    }

    private static Properties loadDbProperties(Path directory) {
        Properties props = new Properties();
        File[] candidates = new File[]{
                directory.resolve("db.properties").toFile(),
                directory.resolve("../db.properties").toFile()
        };
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                try (FileInputStream fis = new FileInputStream(candidate)) {
                    props.load(fis);
                    break;
                } catch (IOException ignored) {
                }
            }
        }
        return props;
    }

    public void requireDevelopmentBootstrap() {
        if (installed) throw new IllegalStateException("Development bootstrap is disabled in installed mode.");
    }

    private static String required(Properties values, String key) {
        String result = values.getProperty(key);
        if (result == null || result.isBlank()) throw new IllegalArgumentException("Installed Server requires " + key + ".");
        return result.trim();
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port > 0 && port <= 65535) return port;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("server.port must be an integer from 1 to 65535.");
    }

    /** Installed contract: single-host MySQL URL, named database, no embedded credentials. */
    private static void validateInstalledUrl(String value) {
        try {
            if (!value.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
            URI uri = URI.create(value.substring(5)).parseServerAuthority();
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || !uri.getPath().matches("/[A-Za-z0-9_-]+") || uri.getPort() == 0 || uri.getPort() > 65535
                    || uri.getRawAuthority().endsWith(":")) throw new IllegalArgumentException();
            String query = uri.getQuery();
            if (query != null && java.util.Arrays.stream(query.split("&")).anyMatch(part ->
                    part.split("=", 2)[0].equalsIgnoreCase("user") || part.split("=", 2)[0].equalsIgnoreCase("password"))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("db.url must be a single-host jdbc:mysql://host[:port]/database URL without embedded credentials or fragment.");
        }
    }

    public DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setMaximumPoolSize(10);
        return new HikariDataSource(config);
    }
}
