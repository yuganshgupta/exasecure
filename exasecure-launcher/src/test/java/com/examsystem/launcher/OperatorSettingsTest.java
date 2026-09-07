package com.examsystem.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OperatorSettingsTest {
    @TempDir Path temp;

    @Test void savesMinimalServerContractAndPortForFutureAdminPropagation() throws Exception {
        Path file = temp.toRealPath().resolve("Operator/server/db.properties");
        OperatorSettings store = new OperatorSettings(file);
        assertThat(store.load()).isNull();
        String password = "  synthetic \\ \t\n\u03a9 = :  ";
        var settings = new OperatorSettings.DatabaseSettings(" jdbc:mysql://db-host:3306/test?useSSL=true ", " account ", password, "9089");
        store.save(settings);
        var loaded = store.load();
        assertThat(loaded.url).isEqualTo("jdbc:mysql://db-host:3306/test?useSSL=true");
        assertThat(loaded.user).isEqualTo("account");
        assertThat(loaded.password).isEqualTo(password);
        assertThat(loaded.port).isEqualTo(9089);
        assertThat(loaded.adminBaseUrl()).isEqualTo("http://localhost:9089");
        assertThat(loaded.toString()).doesNotContain(password, "account", "db-host");
        Properties p = new Properties();
        try (var input = Files.newInputStream(file)) { p.load(input); }
        assertThat(p.stringPropertyNames()).containsExactlyInAnyOrder("db.url", "db.user", "db.password", "server.port");
        store.save(new OperatorSettings.DatabaseSettings("jdbc:mysql://other/test", "other", " opaque ", "8089"));
        assertThat(store.load().password).isEqualTo(" opaque ");
        try (var files = Files.list(file.getParent())) { assertThat(files.toList()).containsExactly(file); }
    }

    @Test void invalidSetupCannotOverwriteExistingSettings() throws Exception {
        Path file = temp.toRealPath().resolve("server/db.properties");
        OperatorSettings store = new OperatorSettings(file);
        store.save(new OperatorSettings.DatabaseSettings("jdbc:mysql://host/test", "user", "synthetic", "8089"));
        byte[] previous = Files.readAllBytes(file);
        for (String url : List.of("bad", "jdbc:h2:mem:test", "jdbc:mysql:///database", "jdbc:mysql://host/", "jdbc:mysql://host:0/db",
                "jdbc:mysql://host:bad/db", "jdbc:mysql://host:65536/db", "jdbc:mysql://user:private@host/db",
                "jdbc:mysql://host/db?password=private", "jdbc:mysql://host/db?%75ser=private", "jdbc:mysql://host/db#private")) {
            assertThatThrownBy(() -> store.save(new OperatorSettings.DatabaseSettings(url, "user", "opaque", "8089")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("private").hasNoCause();
        }
        for (String port : List.of("bad", "0", "-1", "65536")) {
            assertThatThrownBy(() -> new OperatorSettings.DatabaseSettings("jdbc:mysql://host/test", "user", "opaque", port)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new OperatorSettings.DatabaseSettings("jdbc:mysql://host/test", " ", "opaque", "8089")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperatorSettings.DatabaseSettings("jdbc:mysql://host/test", "user", "", "8089")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(file)).isEqualTo(previous);
    }

    @Test void malformedExistingConfigurationIsReported() throws Exception {
        Path file = Files.writeString(temp.resolve("db.properties"), "db.password=\\uZZZZ");
        assertThatThrownBy(() -> new OperatorSettings(file).load()).isInstanceOf(java.io.IOException.class).hasNoCause();
    }

    @Test void launcherRuntimeCannotLoadServerOrDatabaseImplementation() {
        for (String name : List.of("com.examsystem.server.config.AppConfig", "com.mysql.cj.jdbc.Driver", "com.zaxxer.hikari.HikariDataSource")) {
            assertThatThrownBy(() -> Class.forName(name, false, OperatorSettings.class.getClassLoader())).isInstanceOf(ClassNotFoundException.class);
        }
    }
}
