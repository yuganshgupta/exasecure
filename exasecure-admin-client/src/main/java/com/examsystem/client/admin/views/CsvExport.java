package com.examsystem.client.admin.views;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class CsvExport {
    @FunctionalInterface
    interface Content {
        void write(Writer writer) throws IOException;
    }

    private CsvExport() {}

    static void write(Path destination, Content content) throws IOException {
        checkInterrupted();
        Path target = destination.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".exasecure-export-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                content.write(writer);
            }
            checkInterrupted();
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("CSV export interrupted");
    }
}
