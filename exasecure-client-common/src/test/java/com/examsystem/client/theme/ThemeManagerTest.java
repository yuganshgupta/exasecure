package com.examsystem.client.theme;

import org.junit.jupiter.api.Test;

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
}
