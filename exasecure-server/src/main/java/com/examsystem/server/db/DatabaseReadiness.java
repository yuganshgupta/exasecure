package com.examsystem.server.db;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DatabaseReadiness {
    private final DataSource dataSource;
    private final AtomicBoolean checking = new AtomicBoolean();

    public DatabaseReadiness(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isReady() {
        // Bound the entire probe without shortening the shared pool timeout for exam writes.
        if (!checking.compareAndSet(false, true)) return false;
        var result = new CompletableFuture<Boolean>();
        Thread worker = Thread.startVirtualThread(() -> {
            boolean available = false;
            try (var connection = dataSource.getConnection()) {
                available = connection.isValid(1);
            } catch (SQLException | RuntimeException unavailable) {
                available = false;
            } finally {
                checking.set(false);
                result.complete(available);
            }
        });
        try {
            return result.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            return false;
        } catch (TimeoutException | ExecutionException unavailable) {
            worker.interrupt();
            return false;
        }
    }
}
