package com.examsystem.server.config;

import com.examsystem.server.bootstrap.DevBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AppConfigTest {
    @TempDir Path temp;

    Properties valid() {
        Properties p = new Properties();
        p.setProperty("db.url", "jdbc:mysql://db-host:3306/disposable?useSSL=true");
        p.setProperty("db.user", "configured-user");
        p.setProperty("db.password", "  synthetic \\ : = \t\n\u03a9  ");
        p.setProperty("server.port", "9089");
        return p;
    }

    Path write(Path file, Properties p) throws Exception {
        Files.createDirectories(file.getParent());
        try (var output = Files.newOutputStream(file)) { p.store(output, "synthetic test configuration"); }
        return file;
    }

    Properties installed(Path file) {
        Properties p = new Properties();
        p.setProperty("exasecure.config.mode", "installed");
        if (file != null) p.setProperty("exasecure.server.config", file.toString());
        return p;
    }

    @Test void explicitInstalledPathIsSoleSourceAndPreservesOpaquePassword() throws Exception {
        Properties file = valid();
        Path path = write(temp.resolve("Operator/server/db.properties"), file);
        Properties system = installed(path);
        system.setProperty("DB_PASSWORD", "ignored-argument");
        system.setProperty("DB_USER", "ignored-user");
        system.setProperty("SERVER_PORT", "1");
        AppConfig config = new AppConfig(system, Map.of("DB_URL", "invalid"), temp);
        assertThat(config.port).isEqualTo(9089);
        assertThat(config.dbUrl).isEqualTo(file.getProperty("db.url"));
        assertThat(config.dbUser).isEqualTo("configured-user");
        assertThat(config.dbPassword).isEqualTo(file.getProperty("db.password"));
        assertThatThrownBy(config::requireDevelopmentBootstrap).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DevBootstrap.run(config)).isInstanceOf(IllegalStateException.class);
    }

    @Test void installedEnvironmentPathAndPropertyPrecedence() throws Exception {
        Path path = write(temp.resolve("chosen.properties"), valid());
        var env = Map.of("EXASECURE_CONFIG_MODE", "installed", "EXASECURE_SERVER_CONFIG", path.toString());
        assertThat(new AppConfig(new Properties(), env, temp).port).isEqualTo(9089);
        assertThatThrownBy(() -> new AppConfig(installed(temp.resolve("missing")), env, temp)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void installedDoesNotSearchCwdOrParentOrUseDefaults() throws Exception {
        Path child = Files.createDirectory(temp.resolve("child"));
        write(temp.resolve("db.properties"), valid());
        write(child.resolve("db.properties"), valid());
        for (Path file : Arrays.asList(null, Path.of("db.properties"), temp.resolve("missing"), temp)) {
            assertThatThrownBy(() -> new AppConfig(installed(file), Map.of(), child)).isInstanceOf(IllegalArgumentException.class);
        }
        Path empty = write(temp.resolve("empty.properties"), new Properties());
        assertThatThrownBy(() -> new AppConfig(installed(empty), Map.of("DB_USER", "root", "DB_PASSWORD", ""), child)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void requiredSettingsAndPortsFailClosedWithoutLeakingValues() throws Exception {
        for (String key : valid().stringPropertyNames()) {
            Properties p = valid();
            p.remove(key);
            Path path = write(temp.resolve("missing-key.properties"), p);
            assertThatThrownBy(() -> new AppConfig(installed(path), Map.of(), temp)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String port : List.of("bad", "0", "-1", "65536", "999999999999")) {
            Properties p = valid();
            p.setProperty("server.port", port);
            Path path = write(temp.resolve("port.properties"), p);
            assertThatThrownBy(() -> new AppConfig(installed(path), Map.of(), temp)).isInstanceOf(IllegalArgumentException.class);
        }
        Properties p = valid();
        p.setProperty("db.password", "");
        Path path = write(temp.resolve("empty-password.properties"), p);
        assertThatThrownBy(() -> new AppConfig(installed(path), Map.of(), temp)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void installedRejectsInvalidOrCredentialBearingJdbcUrls() throws Exception {
        for (String url : List.of("bad", "jdbc:h2:mem:test", "jdbc:mysql:///database", "jdbc:mysql://host/", "jdbc:mysql://host:0/db",
                "jdbc:mysql://host:bad/db", "jdbc:mysql://host:65536/db", "jdbc:mysql://user:private@host/db",
                "jdbc:mysql://host/db?password=private", "jdbc:mysql://host/db?%75ser=private", "jdbc:mysql://host/db#private")) {
            Properties p = valid();
            p.setProperty("db.url", url);
            Path path = write(temp.resolve("url.properties"), p);
            assertThatThrownBy(() -> new AppConfig(installed(path), Map.of(), temp))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("private").hasNoCause();
        }
    }

    @Test void installedMalformedPropertiesFailWithoutFallback() throws Exception {
        Path file = Files.writeString(temp.resolve("malformed.properties"), "db.password=\\uZZZZ");
        assertThatThrownBy(() -> new AppConfig(installed(file), Map.of(), temp)).isInstanceOf(IllegalArgumentException.class).hasNoCause();
        Properties p = installed(file);
        p.setProperty("exasecure.config.mode", "unknown");
        assertThatThrownBy(() -> new AppConfig(p, Map.of(), temp)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void developmentKeepsFileLookupDefaultsAndExistingOverrideOrder() throws Exception {
        Path child = Files.createDirectory(temp.resolve("child"));
        AppConfig defaults = new AppConfig(new Properties(), Map.of(), child);
        assertThat(defaults.port).isEqualTo(8089);
        assertThat(defaults.dbUser).isEqualTo("root");
        assertThat(defaults.dbPassword).isEmpty();
        defaults.requireDevelopmentBootstrap();
        write(temp.resolve("db.properties"), valid());
        assertThat(new AppConfig(new Properties(), Map.of(), child).port).isEqualTo(9089);
        Properties local = valid();
        local.setProperty("server.port", "8888");
        write(child.resolve("db.properties"), local);
        assertThat(new AppConfig(new Properties(), Map.of(), child).port).isEqualTo(8888);
        Properties system = new Properties();
        system.setProperty("EXASECURE_DB_USER", "alias-property");
        assertThat(new AppConfig(system, Map.of("DB_USER", "direct-environment"), child).dbUser).isEqualTo("direct-environment");
        system.setProperty("DB_USER", " direct-property ");
        assertThat(new AppConfig(system, Map.of("DB_USER", "direct-environment"), child).dbUser).isEqualTo("direct-property");
        assertThat(new AppConfig(new Properties(), Map.of("EXASECURE_DB_USER", "alias-environment"), child).dbUser).isEqualTo("alias-environment");
        system.setProperty("DB_URL", "jdbc:mysql://property/test");
        system.setProperty("SERVER_PORT", "9999");
        AppConfig config = new AppConfig(system, Map.of("DB_URL", "jdbc:mysql://env/test", "SERVER_PORT", "1111"), child);
        assertThat(config.dbUrl).isEqualTo("jdbc:mysql://property/test");
        assertThat(config.port).isEqualTo(9999);
    }

    @Test void developmentPasswordsAreOpaqueIncludingExplicitEmptyOverrides() {
        Properties system = new Properties();
        system.setProperty("EXASECURE_DB_PASSWORD", " alias ");
        assertThat(new AppConfig(system, Map.of(), temp).dbPassword).isEqualTo(" alias ");
        assertThat(new AppConfig(system, Map.of("DB_PASSWORD", " env "), temp).dbPassword).isEqualTo(" env ");
        for (String password : List.of(" property ", "  ", "")) {
            system.setProperty("DB_PASSWORD", password);
            assertThat(new AppConfig(system, Map.of("DB_PASSWORD", "env"), temp).dbPassword).isEqualTo(password);
        }
    }
}
