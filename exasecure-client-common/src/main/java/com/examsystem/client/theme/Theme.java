package com.examsystem.client.theme;

public enum Theme {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");

    private final String displayName;

    Theme(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
