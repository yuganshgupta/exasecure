package com.examsystem.launcher;

import com.examsystem.client.config.AtomicProperties;
import com.examsystem.client.config.UserDataPaths;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Properties;

/** Operator-only file writer. No JDBC driver, connection, or Server implementation dependency. */
final class OperatorSettings {
    private final Path file;

    OperatorSettings() { this(new UserDataPaths().operatorRoot().resolve("server/db.properties")); }
    OperatorSettings(Path file) { this.file = file; }
    Path file() { return file; }

    // Port is the single installed source for future C03 Server, health, and Admin URL propagation.
    static final class DatabaseSettings {
        final String url;
        final String user;
        final String password;
        final int port;

        DatabaseSettings(String url, String user, String password, String port) {
            this.url = url == null ? "" : url.trim();
            validateUrl(this.url);
            if (user == null || user.isBlank()) throw new IllegalArgumentException("Database user is required.");
            this.user = user.trim();
            if (password == null || password.isEmpty()) throw new IllegalArgumentException("Database password is required.");
            this.password = password;
            try {
                this.port = Integer.parseInt(port.trim());
                if (this.port < 1 || this.port > 65535) throw new IllegalArgumentException();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Server port must be an integer from 1 to 65535.");
            }
        }

        String adminBaseUrl() { return "http://localhost:" + port; }
        @Override public String toString() { return "DatabaseSettings[redacted]"; }
    }

    DatabaseSettings load() throws IOException {
        Properties p = new Properties();
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { p.load(input); }
        catch (NoSuchFileException e) { return null; }
        catch (IllegalArgumentException e) { throw new IOException("Installed database settings are malformed."); }
        return new DatabaseSettings(p.getProperty("db.url"), p.getProperty("db.user"), p.getProperty("db.password"), p.getProperty("server.port"));
    }

    void save(DatabaseSettings settings) throws IOException {
        Properties p = new Properties();
        p.setProperty("db.url", settings.url);
        p.setProperty("db.user", settings.user);
        p.setProperty("db.password", settings.password);
        p.setProperty("server.port", Integer.toString(settings.port));
        AtomicProperties.write(file, p);
    }

    // Intentionally local to the Operator artifact. Server independently validates this file contract.
    private static void validateUrl(String value) {
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
            throw new IllegalArgumentException("Database URL must be jdbc:mysql://host[:port]/database, without embedded credentials or fragment.");
        }
    }
}
