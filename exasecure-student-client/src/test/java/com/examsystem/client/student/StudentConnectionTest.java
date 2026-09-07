package com.examsystem.client.student;

import com.examsystem.client.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class StudentConnectionTest {
    @TempDir Path temp;
    StudentSettings store() throws Exception { return new StudentSettings(temp.toRealPath().resolve("Student/client.properties")); }

    @Test void precedencePropertyThenEnvironmentThenPersistence() throws Exception {
        StudentSettings settings = store();
        settings.save("http://saved:8089");
        assertThat(connection(settings, "http://property", "http://environment").client().getConfig().baseUrl()).isEqualTo("http://property");
        assertThat(connection(settings, " ", "http://environment").client().getConfig().baseUrl()).isEqualTo("http://environment");
        assertThat(connection(settings, null, null).client().getConfig().baseUrl()).isEqualTo("http://saved:8089");
    }

    @Test void installedFirstRunHasNoClientAndDevelopmentRetainsLocalhost() throws Exception {
        StudentSettings settings = store();
        StudentConnection installed = connection(settings, null, null);
        assertThat(installed.client()).isNull();
        assertThat(installed.canConfigure()).isTrue();
        assertThat(settings.load()).isEmpty(); // Cancellation does not fabricate or persist a config.
        StudentConnection development = new StudentConnection(settings, ConfigurationMode.DEVELOPMENT, null, null);
        assertThat(development.client().getConfig().baseUrl()).isEqualTo(ServerConfig.DEFAULT_SERVER_URL);
    }

    @Test void invalidManagedOverrideFailsAndCannotBeSilentlyOverriddenByUi() throws Exception {
        StudentSettings settings = store();
        settings.save("http://saved");
        StudentConnection connection = connection(settings, "http://user:secret@host", "http://environment");
        assertThat(connection.client()).isNull();
        assertThat(connection.error()).contains("managed").doesNotContain("secret");
        assertThat(connection.canConfigure()).isFalse();
        assertThatThrownBy(() -> connection.save("http://other")).isInstanceOf(IllegalStateException.class);
        assertThat(settings.load().orElseThrow().baseUrl()).isEqualTo("http://saved");
    }

    @Test void malformedSavedValueEntersSetupAndCanBeRepaired() throws Exception {
        Path file = temp.toRealPath().resolve("client.properties");
        Files.writeString(file, "server.url=invalid");
        StudentConnection connection = connection(new StudentSettings(file), null, null);
        assertThat(connection.client()).isNull();
        assertThat(connection.error()).isNotBlank();
        connection.save("http://repaired");
        assertThat(connection.client().getConfig().baseUrl()).isEqualTo("http://repaired");
        assertThat(connection.error()).isNull();
    }

    @Test void savingRebuildsUnauthenticatedStateAndInvalidSavePreservesClient() throws Exception {
        StudentConnection connection = connection(store(), null, null);
        connection.save("http://first");
        var previous = connection.client();
        assertThatThrownBy(() -> connection.save("invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThat(connection.client()).isSameAs(previous);
        connection.save("http://second");
        assertThat(connection.client()).isNotSameAs(previous);
        assertThat(connection.client().getSessionState()).isNotSameAs(previous.getSessionState());
        assertThat(connection.client().getSessionState().isAuthenticated()).isFalse();
        assertThat(connection(store(), null, null).client().getConfig().baseUrl()).isEqualTo("http://second");
    }

    @Test void authenticationAndSignedInLockRejectServerChangesUntilLogout() throws Exception {
        StudentConnection connection = connection(store(), null, null);
        connection.save("http://first");
        connection.lock();
        assertThat(connection.canConfigure()).isFalse();
        assertThatThrownBy(() -> connection.save("http://second")).isInstanceOf(IllegalStateException.class);
        assertThat(connection.client().getConfig().baseUrl()).isEqualTo("http://first");
        connection.loggedOut();
        connection.save("http://second");
        assertThat(connection.canConfigure()).isTrue();
    }

    @Test void productionRuntimeHasNoServerOperatorOrDatabaseImplementation() {
        for (String name : new String[]{"com.examsystem.server.config.AppConfig", "com.examsystem.launcher.OperatorSettings",
                "com.mysql.cj.jdbc.Driver", "com.zaxxer.hikari.HikariDataSource"}) {
            assertThatThrownBy(() -> Class.forName(name, false, StudentConnection.class.getClassLoader())).isInstanceOf(ClassNotFoundException.class);
        }
    }

    private StudentConnection connection(StudentSettings store, String property, String environment) {
        return new StudentConnection(store, ConfigurationMode.INSTALLED, property, environment);
    }
}
