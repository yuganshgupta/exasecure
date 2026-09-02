package com.examsystem.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class AppConfig {
    public final int port;
    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;

    public AppConfig() {
        this.port = Integer.parseInt(System.getProperty("SERVER_PORT", System.getenv().getOrDefault("SERVER_PORT", "8080")));
        this.dbUrl = System.getProperty("DB_URL", System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/exasecure_dev"));
        this.dbUser = System.getProperty("DB_USER", System.getenv().getOrDefault("DB_USER", "exasecure"));
        this.dbPassword = System.getProperty("DB_PASSWORD", System.getenv().getOrDefault("DB_PASSWORD", "secret"));
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
