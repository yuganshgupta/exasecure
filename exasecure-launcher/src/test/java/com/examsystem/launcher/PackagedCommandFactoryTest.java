package com.examsystem.launcher;

import com.examsystem.client.config.ConfigurationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;

class PackagedCommandFactoryTest {
    @TempDir Path directory;

    @Test void windowsFixedTargetsAndSpaces() throws Exception {
        Path root = layout("ExaSecure Operator image", false);
        var factory = new PackagedCommandFactory(root, "Windows 11");
        for (LaunchTarget target : LaunchTarget.values()) {
            String name = target == LaunchTarget.SERVER ? "ExaSecure Server.exe" : "ExaSecure Admin.exe";
            assertThat(factory.create(target).command()).containsExactly(root.resolve(name).toRealPath().toString());
            assertThat(factory.create(target).workingDirectory()).isEqualTo(root.toRealPath());
        }
    }

    @Test void macBundleContentsFixedTargets() throws Exception {
        Path root = layout("ExaSecure Operator.app/Contents", true);
        var factory = new PackagedCommandFactory(root, "Mac OS X");
        assertThat(factory.create(LaunchTarget.SERVER).executable()).isEqualTo(root.resolve("MacOS/ExaSecure Server").toRealPath());
        assertThat(factory.create(LaunchTarget.ADMIN).executable()).isEqualTo(root.resolve("MacOS/ExaSecure Admin").toRealPath());
    }

    @Test void missingComponentFailsWithoutWrapperFallback() throws Exception {
        Path root = Files.createDirectory(directory.resolve("incomplete"));
        Files.createFile(root.resolve("mvnw.cmd"));
        var factory = new PackagedCommandFactory(root, "Windows 11");
        assertThatThrownBy(() -> factory.create(LaunchTarget.SERVER)).isInstanceOf(IOException.class).hasMessageContaining("missing");
    }

    @Test void relativeMissingAndFileRootsRejected() throws Exception {
        assertThatThrownBy(() -> new PackagedCommandFactory(Path.of("relative"), "Windows 11")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new PackagedCommandFactory(directory.resolve("missing"), "Windows 11")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new PackagedCommandFactory(Files.createFile(directory.resolve("file")), "Windows 11")).isInstanceOf(IOException.class);
    }

    @Test void absoluteChildAndTraversalAreRejectedEvenIfTheyNormalizeInsideRoot() throws Exception {
        Path root = layout("trusted", false).toRealPath();
        for (Path bad : List.of(directory.resolve("outside"), Path.of("../outside"), Path.of("folder/../ExaSecure Server.exe"))) {
            assertThatThrownBy(() -> PackagedCommandFactory.resolveContained(root, bad)).isInstanceOf(IOException.class).hasMessageContaining("rejected");
        }
    }

    @Test void symlinkEscapeIsRejectedWhenFilesystemAllowsLinks() throws Exception {
        Path root = Files.createDirectory(directory.resolve("trusted"));
        Path outside = Files.createFile(directory.resolve("outside"));
        Path link = root.resolve("ExaSecure Server.exe");
        try { Files.createSymbolicLink(link, outside); }
        catch (IOException | UnsupportedOperationException e) {
            System.out.println("C03 symlink physical check unavailable: filesystem does not permit test link creation.");
            return;
        }
        assertThatThrownBy(() -> new PackagedCommandFactory(root, "Windows 11").create(LaunchTarget.SERVER))
                .isInstanceOf(IOException.class).hasMessageContaining("rejected");
        System.out.println("C03 symlink escape rejection exercised.");
    }

    @Test void unknownTargetsAndPlatformsRejected() throws Exception {
        assertThat(LaunchTarget.values()).containsExactly(LaunchTarget.SERVER, LaunchTarget.ADMIN);
        assertThatThrownBy(() -> LaunchTarget.valueOf("../java")).isInstanceOf(IllegalArgumentException.class);
        var factory = new PackagedCommandFactory(layout("image", false), "Windows 11");
        assertThatThrownBy(() -> factory.create(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PackagedCommandFactory(directory, "Linux")).isInstanceOf(IOException.class);
    }

    @Test void windowsJunctionEscapeIsRejected() throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")) return;
        Path root = Files.createDirectory(directory.resolve("junction image"));
        Path outside = Files.createDirectory(directory.resolve("outside executables"));
        Files.createFile(outside.resolve("ExaSecure Server"));
        var builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path $env:C03_LINK -Target $env:C03_TARGET | Out-Null");
        builder.environment().put("C03_LINK", root.resolve("MacOS").toString());
        builder.environment().put("C03_TARGET", outside.toString());
        Process process = builder.start();
        try {
            assertThat(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThatThrownBy(() -> new PackagedCommandFactory(root, "Mac OS X").create(LaunchTarget.SERVER))
                    .isInstanceOf(IOException.class).hasMessageContaining("rejected");
            System.out.println("C03 Windows junction escape rejection exercised.");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            // Delete just the known test junction, never walk its target.
            Files.deleteIfExists(root.resolve("MacOS"));
        }
    }

    @Test void installedPortConfigAndModePropagateWithoutCredentialsInCommands() throws Exception {
        Path root = layout("image", false);
        Path config = configFile("settings", 19123);
        var session = LaunchSession.prepare(ConfigurationMode.INSTALLED, root.toString(), "Windows 11", directory, new OperatorSettings(config));
        var server = session.builder(LaunchTarget.SERVER);
        var admin = session.builder(LaunchTarget.ADMIN);
        assertThat(server.command()).containsExactly(root.resolve("ExaSecure Server.exe").toRealPath().toString());
        assertThat(admin.command()).containsExactly(root.resolve("ExaSecure Admin.exe").toRealPath().toString());
        assertThat(server.environment()).containsEntry("EXASECURE_SERVER_CONFIG", config.toString())
                .containsEntry("EXASECURE_CONFIG_MODE", "installed").containsEntry("EXASECURE_PROCESS_CONTROL", "1");
        assertThat(admin.environment()).containsEntry("EXASECURE_SERVER_URL", "http://localhost:19123")
                .doesNotContainKeys("EXASECURE_SERVER_CONFIG", "EXASECURE_PROCESS_CONTROL", "DB_PASSWORD", "DB_USER", "DB_URL");
        assertThat(session.healthUri().toString()).isEqualTo("http://localhost:19123/health");
        assertThat(session.toString()).doesNotContain("fixture-password", "fixture-user", "jdbc:mysql");
    }

    @Test void installedConfigurationAndAnchorAreRequiredWithNoDevelopmentFallback() throws Exception {
        Path root = layout("image", false);
        var missing = new OperatorSettings(directory.resolve("missing.properties"));
        assertThatThrownBy(() -> LaunchSession.prepare(ConfigurationMode.INSTALLED, root.toString(), "Windows 11", directory, missing))
                .isInstanceOf(IOException.class).hasMessageContaining("configuration is missing");
        assertThatThrownBy(() -> LaunchSession.prepare(ConfigurationMode.INSTALLED, null, "Windows 11", directory, missing))
                .isInstanceOf(IOException.class).hasMessageContaining("exasecure.app.dir");
    }

    @Test void environmentSanitizationIsCaseInsensitiveAndPreservesOperatingSystemEssentials() {
        var env = new HashMap<String, String>();
        for (String key : List.of("DB_PASSWORD", "db_user", "DB_URL", "EXASECURE_DB_OTHER", "JAVA_TOOL_OPTIONS",
                "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "EXASECURE_MYSQL_TEST_PASSWORD", "EXASECURE_SERVER_CONFIG",
                "EXASECURE_SERVER_URL", "EXASECURE_CONFIG_MODE", "SERVER_PORT", "LD_PRELOAD", "DYLD_INSERT_LIBRARIES")) env.put(key, "fixture");
        env.put("SystemRoot", "C:\\Windows");
        env.put("PATH", "os-path");
        env.put("HOME", "os-home");
        env.put("TEMP", "os-temp");
        LaunchSession.sanitize(env);
        assertThat(env).containsOnlyKeys("SystemRoot", "PATH", "HOME", "TEMP");
    }

    @Test void developmentUsesExactC01CommandAndIgnoresInstalledInputs() throws Exception {
        Path root = Files.createDirectory(directory.resolve("development repo"));
        Files.createFile(root.resolve("mvnw.cmd"));
        var session = LaunchSession.prepare(ConfigurationMode.DEVELOPMENT, "ignored", "Windows 11", root,
                new OperatorSettings(directory.resolve("missing")));
        assertThat(session.builder(LaunchTarget.SERVER).command()).containsExactly(root.resolve("mvnw.cmd").toString(), "exec:java", "-pl", "exasecure-server");
        assertThat(session.builder(LaunchTarget.ADMIN).command()).containsExactly(root.resolve("mvnw.cmd").toString(), "exec:java", "-pl", "exasecure-admin-client");
        assertThat(session.healthUri().toString()).isEqualTo("http://localhost:8089/health");
        assertThat(session.builder(LaunchTarget.ADMIN).environment()).containsAllEntriesOf(System.getenv());
    }

    private Path layout(String name, boolean mac) throws Exception {
        Path root = Files.createDirectories(directory.resolve(name));
        Path executables = mac ? Files.createDirectory(root.resolve("MacOS")) : root;
        for (String target : List.of("ExaSecure Server", "ExaSecure Admin")) {
            // Empty disposable path fixtures only; no native binaries are generated or executed.
            Path file = Files.createFile(executables.resolve(target + (mac ? "" : ".exe")));
            file.toFile().setExecutable(true);
        }
        return root;
    }
    private Path configFile(String name, int port) throws Exception {
        Path file = directory.resolve(name + ".properties");
        Properties values = new Properties();
        values.setProperty("db.url", "jdbc:mysql://localhost/exasecure_test");
        values.setProperty("db.user", "fixture-user");
        values.setProperty("db.password", "fixture-password");
        values.setProperty("server.port", Integer.toString(port));
        try (var output = Files.newOutputStream(file)) { values.store(output, null); }
        return file;
    }
}
