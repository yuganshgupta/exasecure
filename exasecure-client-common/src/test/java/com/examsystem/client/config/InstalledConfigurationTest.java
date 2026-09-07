package com.examsystem.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;

class InstalledConfigurationTest {
    @TempDir Path temp;

    @Test void platformPathsAreIndependentOfHostOsAndWorkingDirectory() {
        Path home = temp.resolve("home");
        Path local = temp.resolve("local");
        assertThat(new UserDataPaths("Windows 11", home, local.toString()).studentRoot()).isEqualTo(local.resolve("ExaSecure/Student"));
        assertThat(new UserDataPaths("Windows 11", home, local.toString()).operatorRoot()).isEqualTo(local.resolve("ExaSecure/Operator"));
        assertThat(new UserDataPaths("Windows 11", home, null).studentRoot()).isEqualTo(home.resolve("AppData/Local/ExaSecure/Student"));
        assertThat(new UserDataPaths("Windows 11", home, "relative").studentRoot()).isEqualTo(home.resolve("AppData/Local/ExaSecure/Student"));
        for (String os : List.of("Mac OS X", "Darwin")) {
            assertThat(new UserDataPaths(os, home, local.toString()).studentRoot()).isEqualTo(home.resolve("Library/Application Support/ExaSecure/Student"));
            assertThat(new UserDataPaths(os, home, null).operatorRoot()).isEqualTo(home.resolve("Library/Application Support/ExaSecure/Operator"));
        }
        assertThat(new UserDataPaths("Linux", home, local.toString()).studentRoot()).isEqualTo(home.resolve(".exasecure/Student"));
        assertThatThrownBy(() -> new UserDataPaths("Linux", Path.of("relative"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void explicitModePrecedenceAndInvalidModes() {
        assertThat(ConfigurationMode.resolve(null, null)).isEqualTo(ConfigurationMode.DEVELOPMENT);
        assertThat(ConfigurationMode.resolve("development", "installed")).isEqualTo(ConfigurationMode.DEVELOPMENT);
        assertThat(ConfigurationMode.resolve(" ", "INSTALLED")).isEqualTo(ConfigurationMode.INSTALLED);
        assertThatThrownBy(() -> ConfigurationMode.resolve("instaled", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void validUrlsNormalizeWithoutNetworkingAndRetainProxyBasePaths() {
        for (String url : List.of("http://localhost", "http://127.0.0.1:8089", "http://exam-server:9090", "https://exam.example:443", "http://[::1]:8089")) {
            assertThat(new ServerConfig(url).baseUrl()).isEqualTo(url);
        }
        ServerConfig config = new ServerConfig(" HTTPS://EXAM.EXAMPLE:8443/exams%20lab/// ");
        assertThat(config.baseUrl()).isEqualTo("https://exam.example:8443/exams%20lab");
        assertThat(config.apiV1Url()).isEqualTo("https://exam.example:8443/exams%20lab/api/v1");
        assertThat(config.healthUrl()).isEqualTo("https://exam.example:8443/exams%20lab/health");
    }

    @Test void invalidUrlsNeverEchoInputOrCredentials() {
        for (String url : Arrays.asList(null, "", "localhost:8089", "ftp://host", "http:///path", "http://", "http://user:private@host",
                "https://host?x=1", "https://host#fragment", "http://host:abc", "http://host:", "http://host:-1", "http://host:0",
                "http://host:65536", "http://host:999999999999", "http://bad host", "http://host/[%", "http://host?", "http://host#")) {
            assertThatThrownBy(() -> new ServerConfig(url)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("private").hasNoCause();
        }
    }

    @Test void studentSaveLoadReplacesOnlyUrlAndRejectsInvalidSave() throws Exception {
        Path file = temp.toRealPath().resolve("Student/client.properties");
        StudentSettings settings = new StudentSettings(file);
        assertThat(settings.load()).isEmpty();
        settings.save("HTTPS://EXAM-SERVER:8443///");
        assertThat(settings.load().orElseThrow().baseUrl()).isEqualTo("https://exam-server:8443");
        settings.save("http://other:8089");
        byte[] previous = Files.readAllBytes(file);
        assertThatThrownBy(() -> settings.save("user:private@host")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(file)).isEqualTo(previous);
        Properties p = new Properties();
        try (var input = Files.newInputStream(file)) { p.load(input); }
        assertThat(p.stringPropertyNames()).containsExactly("server.url");
        try (var files = Files.list(file.getParent())) { assertThat(files.toList()).containsExactly(file); }
    }

    @Test void malformedAndUnexpectedStudentPropertiesFailWithoutDefaulting() throws Exception {
        Path file = temp.resolve("client.properties");
        StudentSettings settings = new StudentSettings(file);
        for (String content : List.of("server.url=bad", "server.url=http://host\ndb.password=private", "server.url=\\uZZZZ", "")) {
            Files.writeString(file, content);
            assertThatThrownBy(settings::load).isInstanceOfAny(IOException.class, IllegalArgumentException.class);
        }
    }

    @Test void atomicFailureRetainsOldFileAndRemovesTemporaryFile() throws Exception {
        Path file = temp.toRealPath().resolve("config/settings.properties");
        Properties original = new Properties();
        original.setProperty("setting", "old");
        AtomicProperties.write(file, original);
        byte[] bytes = Files.readAllBytes(file);
        Properties next = new Properties();
        next.setProperty("setting", "new");
        for (boolean unsupported : List.of(true, false)) {
            assertThatThrownBy(() -> AtomicProperties.write(file, next, (source, target) -> {
                assertThat(Files.readString(source)).contains("setting=new");
                assertThat(Files.readAllBytes(target)).isEqualTo(bytes);
                if (unsupported) throw new AtomicMoveNotSupportedException("source", "target", "test");
                throw new IOException("injected replacement failure");
            })).isInstanceOf(IOException.class);
            assertThat(Files.readAllBytes(file)).isEqualTo(bytes);
            try (var files = Files.list(file.getParent())) { assertThat(files.toList()).containsExactly(file); }
        }
    }

    @Test void accountPermissionsAndOpaquePropertiesRoundTripOnNativeFilesystem() throws Exception {
        Path file = temp.toRealPath().resolve("private/settings.properties");
        String opaque = "  spaces \\ = : ! # \t\n\u03a9 trailing  ";
        Properties p = new Properties();
        p.setProperty("opaque", opaque);
        AtomicProperties.write(file, p);
        Properties loaded = new Properties();
        try (var input = Files.newInputStream(file)) { loaded.load(input); }
        assertThat(loaded.getProperty("opaque")).isEqualTo(opaque);
        var posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            assertThat(posix.readAttributes().permissions()).isEqualTo(PosixFilePermissions.fromString("rw-------"));
            assertThat(Files.getPosixFilePermissions(file.getParent())).isEqualTo(PosixFilePermissions.fromString("rwx------"));
        } else {
            for (Path path : List.of(file, file.getParent())) {
                var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
                assertThat(acl).isNotNull();
                assertThat(acl.getAcl()).hasSize(1);
                assertThat(acl.getAcl().getFirst().principal()).isEqualTo(acl.getOwner());
                assertThat(acl.getAcl().getFirst().type()).isEqualTo(AclEntryType.ALLOW);
            }
        }
    }

    @Test void directoryDestinationAndNonDirectoryParentAreRejected() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("destination"));
        assertThatThrownBy(() -> AtomicProperties.write(directory, new Properties())).isInstanceOf(IOException.class);
        Path parentFile = Files.writeString(temp.resolve("file"), "untouched");
        assertThatThrownBy(() -> AtomicProperties.write(parentFile.resolve("config"), new Properties())).isInstanceOf(IOException.class);
        assertThat(Files.readString(parentFile)).isEqualTo("untouched");
    }
}
