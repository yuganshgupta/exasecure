package com.examsystem.client.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerConfigTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(ServerConfig.SYS_PROP);
    }

    @Test
    void defaultServerUrl_isLocalhost8089() {
        ServerConfig config = new ServerConfig();
        assertThat(config.baseUrl()).isEqualTo("http://localhost:8089");
        assertThat(config.apiV1Url()).isEqualTo("http://localhost:8089/api/v1");
        assertThat(config.healthUrl()).isEqualTo("http://localhost:8089/health");
    }

    @Test
    void customServerUrl_stripsTrailingSlashes() {
        ServerConfig config = new ServerConfig("http://192.168.1.100:9090///");
        assertThat(config.baseUrl()).isEqualTo("http://192.168.1.100:9090");
        assertThat(config.apiV1Url()).isEqualTo("http://192.168.1.100:9090/api/v1");
    }

    @Test
    void systemProperty_overridesDefault() {
        System.setProperty(ServerConfig.SYS_PROP, "https://exam.university.edu:8443");
        ServerConfig config = new ServerConfig();
        assertThat(config.baseUrl()).isEqualTo("https://exam.university.edu:8443");
        assertThat(config.apiV1Url()).isEqualTo("https://exam.university.edu:8443/api/v1");
    }

    @Test
    void nonHttpScheme_throwsException() {
        assertThatThrownBy(() -> new ServerConfig("ftp://localhost:21"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
