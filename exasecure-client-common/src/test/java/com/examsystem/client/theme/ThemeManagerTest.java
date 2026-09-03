package com.examsystem.client.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeManagerTest {

    @Test
    void singletonReturnsNonNull() {
        ThemeManager manager = ThemeManager.getInstance();
        assertThat(manager).isNotNull();
    }

    @Test
    void themeValuesAreValid() {
        assertThat(Theme.values()).containsExactly(Theme.LIGHT, Theme.DARK, Theme.SYSTEM);
        assertThat(Theme.LIGHT.getDisplayName()).isEqualTo("Light");
        assertThat(Theme.DARK.getDisplayName()).isEqualTo("Dark");
        assertThat(Theme.SYSTEM.getDisplayName()).isEqualTo("System");
    }

    @Test
    void themeSelectionCanBeUpdated() {
        ThemeManager manager = ThemeManager.getInstance();
        manager.setTheme(Theme.LIGHT);
        assertThat(manager.getTheme()).isEqualTo(Theme.LIGHT);

        manager.setTheme(Theme.DARK);
        assertThat(manager.getTheme()).isEqualTo(Theme.DARK);

        manager.setTheme(Theme.SYSTEM);
        assertThat(manager.getTheme()).isEqualTo(Theme.SYSTEM);
    }

    @Test
    void bothThemesDefineCriticalProductInteractionStates() throws IOException {
        for (String resource : new String[]{"/styles/light.css", "/styles/dark.css"}) {
            String css;
            try (var stream = ThemeManagerTest.class.getResourceAsStream(resource)) {
                assertThat(stream).as(resource).isNotNull();
                css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertThat(css)
                    .contains(".app-root", ".login-card", ".btn-primary", ".btn-admin")
                    .contains(".table-row-cell:hover:filled", ".table-row-cell:selected:focused")
                    .contains(".combo-box-popup .list-cell:hover", ".combo-box-popup .list-cell:filled:selected")
                    .contains(".badge-active", ".badge-submitted", ".badge-expired", ".badge-clean", ".badge-flagged", ".badge-inactive")
                    .contains(".navigator-current", ".navigator-answered", ".navigator-flagged", ".navigator-failed")
                    .contains(".violation-warning", ".violation-danger", ".evidence-canvas", ".product-dialog");
        }
    }
}
