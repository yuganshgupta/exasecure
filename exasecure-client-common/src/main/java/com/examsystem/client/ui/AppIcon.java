package com.examsystem.client.ui;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Centralized utility for loading the official ExaSecure application icon
 * and applying it to JavaFX Stages, Dialogs, and child windows.
 */
public final class AppIcon {

    public static final String ICON_RESOURCE_PATH = "/icons/app_icon.png";
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};

    private static volatile List<Image> cachedIcons = null;
    private static volatile Image cachedPrimaryImage = null;
    private static volatile boolean loadAttempted = false;

    private AppIcon() {
    }

    /**
     * Returns the classpath URL for the ExaSecure icon resource, or null if missing.
     */
    public static URL getResourceUrl() {
        return AppIcon.class.getResource(ICON_RESOURCE_PATH);
    }

    /**
     * Returns an InputStream for the ExaSecure icon resource, or null if missing.
     */
    public static InputStream getResourceAsStream() {
        return AppIcon.class.getResourceAsStream(ICON_RESOURCE_PATH);
    }

    /**
     * Returns the primary application icon Image, or null if loading failed.
     */
    public static Image getPrimaryImage() {
        initIcons();
        return cachedPrimaryImage;
    }

    /**
     * Returns an unmodifiable list of multi-resolution application icon Images,
     * or an empty list if loading failed.
     */
    public static List<Image> getIcons() {
        initIcons();
        return cachedIcons;
    }

    /**
     * Applies the ExaSecure application icon to the specified Stage.
     * Gracefully ignores errors so an icon-loading issue never crashes the app.
     */
    public static void applyTo(Stage stage) {
        if (stage == null) return;
        try {
            List<Image> icons = getIcons();
            if (!icons.isEmpty()) {
                stage.getIcons().clear();
                for (Image icon : icons) {
                    stage.getIcons().add(icon);
                }
            }
        } catch (Throwable t) {
            System.err.println("Warning: Unable to apply application icon to stage: " + t.getMessage());
        }
    }

    /**
     * Applies the ExaSecure application icon to the Window of a Dialog.
     */
    public static void applyTo(Dialog<?> dialog) {
        if (dialog == null) return;
        try {
            DialogPane pane = dialog.getDialogPane();
            if (pane != null) {
                applyToPane(pane);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Applies the ExaSecure application icon to any Window that is a Stage.
     */
    public static void applyTo(Window window) {
        if (window instanceof Stage stage) {
            applyTo(stage);
        }
    }

    private static void applyToPane(DialogPane pane) {
        if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
            applyTo(stage);
        } else {
            pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    if (newScene.getWindow() instanceof Stage stage) {
                        applyTo(stage);
                    } else {
                        newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                            if (newWin instanceof Stage stage) {
                                applyTo(stage);
                            }
                        });
                    }
                }
            });
        }
    }

    private static synchronized void initIcons() {
        if (loadAttempted) return;
        loadAttempted = true;

        List<Image> icons = new ArrayList<>();
        try {
            URL url = getResourceUrl();
            if (url != null) {
                String externalForm = url.toExternalForm();
                for (int size : ICON_SIZES) {
                    try {
                        icons.add(new Image(externalForm, size, size, true, true));
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    Image fullRes = new Image(externalForm);
                    icons.add(fullRes);
                    cachedPrimaryImage = fullRes;
                } catch (Throwable ignored) {
                }
            }
            if (cachedPrimaryImage == null && !icons.isEmpty()) {
                cachedPrimaryImage = icons.get(icons.size() - 1);
            }
            cachedIcons = Collections.unmodifiableList(icons);
        } catch (Throwable t) {
            System.err.println("Warning: Failed to load application icon from " + ICON_RESOURCE_PATH + ": " + t.getMessage());
            cachedIcons = Collections.emptyList();
            cachedPrimaryImage = null;
        }
    }
}
