package com.examsystem.client.student;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.config.*;
import com.examsystem.client.session.SessionState;
import java.io.IOException;

/** Logged-out setup state; also locks configuration throughout authentication and signed-in use. */
public final class StudentConnection {
    private final StudentSettings settings;
    private final String managedAddress;
    private ApiClient client;
    private boolean locked;
    private String error;

    public StudentConnection() {
        this(new StudentSettings(), ConfigurationMode.current(), System.getProperty(ServerConfig.SYS_PROP),
                System.getenv(ServerConfig.ENV_VAR));
    }

    public StudentConnection(StudentSettings settings, ConfigurationMode mode, String property, String environment) {
        this.settings = settings;
        this.managedAddress = property != null && !property.isBlank() ? property
                : environment != null && !environment.isBlank() ? environment : null;
        try {
            ServerConfig config = managedAddress != null ? new ServerConfig(managedAddress)
                    : settings.load().orElseGet(() -> mode == ConfigurationMode.DEVELOPMENT ? new ServerConfig(ServerConfig.DEFAULT_SERVER_URL) : null);
            if (config != null) client = new ApiClient(config, new SessionState());
        } catch (IOException | IllegalArgumentException e) {
            error = managedAddress != null ? "The managed Server address is invalid. Correct the JVM property or environment variable."
                    : "Saved Server settings could not be read. Please configure the Server again.";
        }
    }

    public ApiClient client() { return client; }
    public String error() { return error; }
    public boolean managed() { return managedAddress != null; }
    public boolean canConfigure() {
        return !locked && !managed() && (client == null || !client.getSessionState().isAuthenticated());
    }
    public void lock() { locked = true; }
    public void loggedOut() { locked = false; }

    public void save(String address) throws IOException {
        if (!canConfigure()) throw new IllegalStateException("Server configuration is available only while logged out and without a managed override.");
        ServerConfig config = settings.save(address);
        client = new ApiClient(config, new SessionState());
        error = null;
    }
}
