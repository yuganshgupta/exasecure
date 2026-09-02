package com.examsystem.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AppConfig {
    public final int port;
    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;

    public AppConfig() {
        Properties fileProps = loadDbProperties();

        String defaultPort = fileProps.getProperty("server.port", "8089");
        this.port = Integer.parseInt(getPropOrEnv("SERVER_PORT", defaultPort));

        String defaultDbUrl = fileProps.getProperty("db.url", "jdbc:mysql://localhost:3306/secure_exam_db?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC");
        this.dbUrl = getPropOrEnv("DB_URL", getPropOrEnv("EXASECURE_DB_URL", defaultDbUrl));

        String defaultDbUser = fileProps.getProperty("db.user", "root");
        this.dbUser = getPropOrEnv("DB_USER", getPropOrEnv("EXASECURE_DB_USER", defaultDbUser));

        String defaultDbPassword = fileProps.getProperty("db.password", "");
        this.dbPassword = getPropOrEnv("DB_PASSWORD", getPropOrEnv("EXASECURE_DB_PASSWORD", defaultDbPassword));
    }

    public AppConfig(int port, String dbUrl, String dbUser, String dbPassword) {
        this.port = port;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    private static String getPropOrEnv(String name, String defaultValue) {
        String val = System.getProperty(name);
        if (val != null && !val.isBlank()) {
            return val.trim();
        }
        val = System.getenv(name);
        if (val != null && !val.isBlank()) {
            return val.trim();
        }
        return defaultValue;
    }

    private static Properties loadDbProperties() {
        Properties props = new Properties();
        File[] candidates = new File[]{
                new File("db.properties"),
                new File("../db.properties")
        };
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                try (FileInputStream fis = new FileInputStream(candidate)) {
                    props.load(fis);
                    break;
                } catch (IOException ignored) {
                }
            }
        }
        return props;
    }

    public DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setMaximumPoolSize(10);
        return new HikariDataSource(config);
    }
}
