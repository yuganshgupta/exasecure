package com.examsystem.client.config;

import java.nio.file.Path;
import java.util.Locale;

/** Non-secret path infrastructure shared by desktop applications. Does not create files. */
public final class UserDataPaths {
    private final Path root;

    public UserDataPaths() {
        this(System.getProperty("os.name"), Path.of(System.getProperty("user.home")), System.getenv("LOCALAPPDATA"));
    }

    public UserDataPaths(String osName, Path userHome, String localAppData) {
        if (!userHome.isAbsolute()) throw new IllegalArgumentException("User home must be absolute.");
        String os = osName.toLowerCase(Locale.ROOT);
        Path base = userHome.resolve(".exasecure");
        if (os.startsWith("windows")) {
            base = localAppData != null && !localAppData.isBlank() && Path.of(localAppData).isAbsolute()
                    ? Path.of(localAppData).resolve("ExaSecure") : userHome.resolve("AppData/Local/ExaSecure");
        } else if (os.startsWith("mac") || os.equals("darwin")) {
            base = userHome.resolve("Library/Application Support/ExaSecure");
        }
        root = base.normalize();
    }

    public Path studentRoot() { return root.resolve("Student"); }
    public Path operatorRoot() { return root.resolve("Operator"); }
}
