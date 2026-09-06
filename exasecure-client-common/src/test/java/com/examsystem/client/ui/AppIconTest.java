package com.examsystem.client.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AppIconTest {

    @Test
    void resourcePathPointsToValidPng() throws IOException {
        URL url = AppIcon.getResourceUrl();
        assertThat(url).as("Icon resource URL").isNotNull();

        try (InputStream is = AppIcon.getResourceAsStream()) {
            assertThat(is).as("Icon resource input stream").isNotNull();
            byte[] header = is.readNBytes(8);
            assertThat(header).containsExactly((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                    (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A);
        }
    }

    @Test
    void nullTargetsDoNotThrow() {
        assertThatCode(() -> AppIcon.applyTo((javafx.stage.Stage) null)).doesNotThrowAnyException();
        assertThatCode(() -> AppIcon.applyTo((javafx.scene.control.Dialog<?>) null)).doesNotThrowAnyException();
        assertThatCode(() -> AppIcon.applyTo((javafx.stage.Window) null)).doesNotThrowAnyException();
    }
}
