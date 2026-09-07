package com.examsystem.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Optional;
import java.util.Properties;

/** Student storage contains exactly one non-secret property: server.url. */
public final class StudentSettings {
    private final Path file;

    public StudentSettings() { this(new UserDataPaths().studentRoot().resolve("client.properties")); }
    public StudentSettings(Path file) { this.file = file; }

    public Optional<ServerConfig> load() throws IOException {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            values.load(input);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            throw new IOException("Student settings are malformed. Configure the Server again.");
        }
        if (!values.stringPropertyNames().equals(java.util.Set.of("server.url"))) {
            throw new IOException("Student settings must contain only server.url. Configure the Server again.");
        }
        return Optional.of(new ServerConfig(values.getProperty("server.url")));
    }

    public ServerConfig save(String address) throws IOException {
        ServerConfig config = new ServerConfig(address);
        Properties values = new Properties();
        values.setProperty("server.url", config.baseUrl());
        AtomicProperties.write(file, values);
        return config;
    }
}
