package com.examsystem.server.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;

class PackagedServerBootstrapTest {
    @TempDir Path directory;

    @Test void forcesInstalledModeAndRefusesDevelopmentDefaults() {
        Properties properties = new Properties();
        properties.setProperty("exasecure.config.mode", "development");
        assertThatThrownBy(() -> PackagedServerBootstrap.installedConfig(properties, Map.of("DB_URL", "ignored")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("absolute config-file path");
        assertThat(properties.getProperty("exasecure.config.mode")).isEqualTo("development");
    }

    @Test void loadsOnlyInstalledFileAndCannotEnableDevBootstrap() throws Exception {
        Path file = directory.resolve("installed.properties");
        Files.writeString(file, "db.url=jdbc:mysql://localhost/exasecure_test\ndb.user=fixture-user\ndb.password=fixture-password\nserver.port=19123\n");
        Properties properties = new Properties();
        properties.setProperty("exasecure.bootstrap", "true");
        var config = PackagedServerBootstrap.installedConfig(properties, Map.of("EXASECURE_SERVER_CONFIG", file.toString(),
                "SERVER_PORT", "8089", "DB_PASSWORD", "ignored", "EXASECURE_CONFIG_MODE", "development"));
        assertThat(config.port).isEqualTo(19123);
        assertThat(config.dbPassword).isEqualTo("fixture-password");
        assertThatThrownBy(config::requireDevelopmentBootstrap).isInstanceOf(IllegalStateException.class);
    }

    @Test void packagedMainRejectsBootstrapArgumentsBeforeConfigurationOrDatabaseAccess() {
        assertThatThrownBy(() -> PackagedServerBootstrap.main(new String[]{"--bootstrap"}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not accept");
    }
}
