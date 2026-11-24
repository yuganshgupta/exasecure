package com.examsystem.client.admin.views;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvExportTest {
    @TempDir Path directory;

    @Test
    void replacesExistingExportWithCompleteUtf8Data() throws Exception {
        Path target = Files.writeString(directory.resolve("attempts.csv"), "Old export");
        String row = AdminDashboardView.escapeCsvCell("Åsa \"河\"\nSecond line") + ","
                + AdminDashboardView.escapeCsvCell("=1+1") + "\n";
        CsvExport.write(target, writer -> writer.write(row));
        assertThat(Files.readAllBytes(target)).containsExactly(row.getBytes(StandardCharsets.UTF_8));
        assertOnlyTarget(target);
    }

    @Test
    void failedWritePreservesExistingFileAndRemovesStage() throws Exception {
        Path target = Files.writeString(directory.resolve("attempts.csv"), "Old export");
        assertThatThrownBy(() -> CsvExport.write(target, writer -> {
            writer.write("Partial export");
            throw new IOException("injected write failure");
        })).hasMessage("injected write failure");
        assertThat(Files.readString(target)).isEqualTo("Old export");
        assertOnlyTarget(target);
    }

    @Test
    void interruptedExportCannotReplaceExistingFile() throws Exception {
        Path target = Files.writeString(directory.resolve("attempts.csv"), "Old export");
        try {
            assertThatThrownBy(() -> CsvExport.write(target, writer -> {
                writer.write("Interrupted export");
                Thread.currentThread().interrupt();
            })).isInstanceOf(InterruptedIOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(Files.readString(target)).isEqualTo("Old export");
        assertOnlyTarget(target);
    }

    private void assertOnlyTarget(Path target) throws IOException {
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).containsExactly(target);
        }
    }
}
