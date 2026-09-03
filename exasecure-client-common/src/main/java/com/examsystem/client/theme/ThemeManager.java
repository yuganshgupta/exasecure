package com.examsystem.client.theme;

import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

public class ThemeManager {
    private static final String PREF_KEY = "exasecure_theme";
    private static final ThemeManager INSTANCE = new ThemeManager();

    private Theme currentTheme = Theme.SYSTEM;
    private final Set<Scene> registeredScenes = Collections.newSetFromMap(new WeakHashMap<>());
    private final Preferences preferences = Preferences.userNodeForPackage(ThemeManager.class);

    private ThemeManager() {
        try {
            String saved = preferences.get(PREF_KEY, Theme.SYSTEM.name());
            currentTheme = Theme.valueOf(saved);
        } catch (Exception e) {
            currentTheme = Theme.SYSTEM;
        }
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public Theme getTheme() {
        return currentTheme;
    }

    public void setTheme(Theme theme) {
        if (theme == null) theme = Theme.SYSTEM;
        this.currentTheme = theme;
        try {
            preferences.put(PREF_KEY, theme.name());
        } catch (Exception ignored) {
        }
        for (Scene scene : registeredScenes) {
            applyThemeToScene(scene);
        }
    }

    public void applyTheme(Scene scene) {
        if (scene == null) return;
        registeredScenes.add(scene);
        applyThemeToScene(scene);
    }

    public void applyTheme(javafx.scene.control.Dialog<?> dialog) {
        if (dialog == null) return;
        applyTheme(dialog.getDialogPane());
    }

    public void applyTheme(javafx.scene.control.DialogPane pane) {
        if (pane == null) return;
        pane.getStylesheets().clear();
        String externalForm = getCssResourceExternalForm();
        if (externalForm != null) {
            pane.getStylesheets().add(externalForm);
        }
        if (pane.getScene() != null) {
            applyTheme(pane.getScene());
        }
    }

    private void applyThemeToScene(Scene scene) {
        scene.getStylesheets().clear();
        String externalForm = getCssResourceExternalForm();
        if (externalForm != null) {
            scene.getStylesheets().add(externalForm);
        }
    }

    public String getCssResourceExternalForm() {
        String cssPath = getCssResourcePath();
        if (cssPath != null) {
            var resource = getClass().getResource(cssPath);
            if (resource != null) {
                return resource.toExternalForm();
            }
        }
        return null;
    }

    private String getCssResourcePath() {
        Theme effective = currentTheme;
        if (effective == Theme.SYSTEM) {
            effective = Theme.LIGHT;
        }
        return effective == Theme.DARK ? "/styles/dark.css" : "/styles/light.css";
    }

    public ComboBox<Theme> createThemeSelector() {
        ComboBox<Theme> comboBox = new ComboBox<>(FXCollections.observableArrayList(Theme.values()));
        comboBox.setValue(currentTheme);
        comboBox.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal != currentTheme) {
                setTheme(newVal);
            }
        });
        return comboBox;
    }
}
