package com.examsystem.launcher;

import com.examsystem.client.config.ConfigurationMode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** One immutable port/configuration snapshot for Server, Admin and health. No credentials retained. */
record LaunchSession(LaunchSpec server, LaunchSpec admin, boolean installed, Path configFile, int port) {
    @FunctionalInterface
    interface Factory { LaunchSession prepare() throws IOException; }

    static LaunchSession current() throws IOException {
        return prepare(ConfigurationMode.current(), System.getProperty("exasecure.app.dir"),
                System.getProperty("os.name"), Path.of(""), new OperatorSettings());
    }

    static LaunchSession prepare(ConfigurationMode mode, String anchor, String os, Path cwd,
                                 OperatorSettings settings) throws IOException {
        if (mode == ConfigurationMode.DEVELOPMENT) {
            return new LaunchSession(DevelopmentCommandFactory.create(LaunchTarget.SERVER, cwd, os),
                    DevelopmentCommandFactory.create(LaunchTarget.ADMIN, cwd, os), false, null, 8089);
        }
        if (anchor == null || anchor.isBlank()) throw new IOException("Packaged launch requires an absolute exasecure.app.dir.");
        var paths = new PackagedCommandFactory(Path.of(anchor), os);
        var database = settings.load();
        if (database == null) throw new IOException("Installed Server configuration is missing. Use Configure Installed Server.");
        return new LaunchSession(paths.create(LaunchTarget.SERVER), paths.create(LaunchTarget.ADMIN),
                true, settings.file().toAbsolutePath().normalize(), database.port);
    }

    URI healthUri() { return URI.create(baseUrl() + "/health"); }
    String baseUrl() { return "http://localhost:" + port; }

    ProcessBuilder builder(LaunchTarget target) {
        LaunchSpec spec = switch (target) { case SERVER -> server; case ADMIN -> admin; };
        var builder = new ProcessBuilder(spec.command()).directory(spec.workingDirectory().toFile());
        Map<String, String> env = builder.environment();
        if (installed) {
            String loopbackOnly = env.get("EXASECURE_SERVER_LOOPBACK_ONLY");
            sanitize(env);
            env.put("EXASECURE_CONFIG_MODE", "installed");
            if (target == LaunchTarget.SERVER) {
                env.put("EXASECURE_SERVER_CONFIG", configFile.toString());
                if (loopbackOnly != null) env.put("EXASECURE_SERVER_LOOPBACK_ONLY", loopbackOnly);
            } else {
                env.put("EXASECURE_SERVER_URL", baseUrl());
            }
        }
        // Development command and inherited overrides remain unchanged; only owned Server opts into pipes.
        if (target == LaunchTarget.SERVER) env.put("EXASECURE_PROCESS_CONTROL", "1");
        return builder;
    }

    static void sanitize(Map<String, String> environment) {
        environment.keySet().removeIf(key -> {
            String name = key.toUpperCase(Locale.ROOT);
            return name.equals("DB_PASSWORD") || name.equals("DB_USER") || name.equals("DB_URL")
                    || name.startsWith("EXASECURE_DB_") || name.startsWith("EXASECURE_MYSQL_TEST_")
                    || name.startsWith("EXASECURE_SERVER_") || name.equals("EXASECURE_PROCESS_CONTROL")
                    || name.equals("EXASECURE_CONFIG_MODE") || name.equals("SERVER_PORT")
                    || name.equals("JAVA_TOOL_OPTIONS") || name.equals("_JAVA_OPTIONS")
                    || name.equals("JDK_JAVA_OPTIONS") || name.equals("CLASSPATH")
                    || name.equals("LD_PRELOAD") || name.equals("LD_LIBRARY_PATH") || name.startsWith("DYLD_");
        });
    }
}
