package com.examsystem.server.bootstrap;

import com.examsystem.server.config.AppConfig;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/** Future native Server main. Arguments never select a main class or development bootstrap. */
public final class PackagedServerBootstrap {
    private PackagedServerBootstrap() { }

    public static void main(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("Packaged Server does not accept launch arguments.");
        ManagedServerControl.run(installedConfig(System.getProperties(), System.getenv()));
    }

    static AppConfig installedConfig(Properties properties, Map<String, String> environment) {
        Properties installed = new Properties();
        installed.putAll(properties);
        installed.setProperty("exasecure.config.mode", "installed");
        return new AppConfig(installed, environment, Path.of(""));
    }
}
