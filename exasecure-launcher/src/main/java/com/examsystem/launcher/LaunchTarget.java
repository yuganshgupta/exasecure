package com.examsystem.launcher;

enum LaunchTarget {
    SERVER("exasecure-server"),
    ADMIN("exasecure-admin-client");

    private final String moduleName;

    LaunchTarget(String moduleName) {
        this.moduleName = moduleName;
    }

    String moduleName() {
        return moduleName;
    }
}
