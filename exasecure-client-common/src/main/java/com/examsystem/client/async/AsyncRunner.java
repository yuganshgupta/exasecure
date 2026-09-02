package com.examsystem.client.async;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AsyncRunner {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "exasecure-async-worker");
        t.setDaemon(true);
        return t;
    });

    public static void execute(Runnable task) {
        EXECUTOR.execute(task);
    }

    public static <T> void runAsync(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        EXECUTOR.execute(() -> {
            try {
                T result = task.call();
                if (onSuccess != null) {
                    runOnFxThread(() -> onSuccess.accept(result));
                }
            } catch (Throwable t) {
                if (onError != null) {
                    runOnFxThread(() -> onError.accept(t));
                }
            }
        });
    }

    public static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            try {
                Platform.runLater(action);
            } catch (IllegalStateException e) {
                // In non-JavaFX test contexts where Toolkit isn't running, execute directly
                action.run();
            }
        }
    }
}
