package com.examsystem.launcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record LaunchSpec(Path executable, List<String> arguments, Path workingDirectory) {
    LaunchSpec {
        Objects.requireNonNull(executable, "executable");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(workingDirectory, "workingDirectory");
    }

    List<String> command() {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(arguments);
        return List.copyOf(command);
    }
}
