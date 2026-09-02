package com.examsystem.client.admin;

import com.examsystem.client.admin.views.AdminDashboardView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportSanitizationTest {

    @Test
    void nullAndEmptyValues_areEscapedAsEmptyQuotes() {
        assertThat(AdminDashboardView.escapeCsvCell(null)).isEqualTo("\"\"");
        assertThat(AdminDashboardView.escapeCsvCell("")).isEqualTo("\"\"");
    }

    @Test
    void standardValuesAndEmbeddedQuotes_areEscapedProperly() {
        assertThat(AdminDashboardView.escapeCsvCell("Alice Smith")).isEqualTo("\"Alice Smith\"");
        assertThat(AdminDashboardView.escapeCsvCell("Alice \"Bob\" Smith")).isEqualTo("\"Alice \"\"Bob\"\" Smith\"");
    }

    @Test
    void formulaInjectionPrefixes_areNeutralizedWithSingleQuote() {
        assertThat(AdminDashboardView.escapeCsvCell("=SUM(A1:A10)")).isEqualTo("\"'=SUM(A1:A10)\"");
        assertThat(AdminDashboardView.escapeCsvCell("+123456")).isEqualTo("\"'+123456\"");
        assertThat(AdminDashboardView.escapeCsvCell("-100")).isEqualTo("\"'-100\"");
        assertThat(AdminDashboardView.escapeCsvCell("@test")).isEqualTo("\"'@test\"");
        assertThat(AdminDashboardView.escapeCsvCell("\tcmd")).isEqualTo("\"'\tcmd\"");
        assertThat(AdminDashboardView.escapeCsvCell("\rcmd")).isEqualTo("\"'\rcmd\"");
    }
}
