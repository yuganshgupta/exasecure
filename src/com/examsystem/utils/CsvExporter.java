package com.examsystem.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CsvExporter {

    public static void exportAttemptReport(List<String[]> data, File destinationFile) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(destinationFile))) {
            // Write Header
            bw.write(escape("Attempt ID") + "," +
                     escape("Username") + "," +
                     escape("Full Name") + "," +
                     escape("Exam Title") + "," +
                     escape("Score") + "," +
                     escape("Max Score") + "," +
                     escape("Percentage %") + "," +
                     escape("Submitted At"));
            bw.newLine();

            // Write Data
            for (String[] row : data) {
                for (int i = 0; i < row.length; i++) {
                    bw.write(escape(row[i]));
                    if (i < row.length - 1) {
                        bw.write(",");
                    }
                }
                bw.newLine();
            }
        }
    }

    private static String escape(String field) {
        if (field == null) return "";
        boolean containsComma = field.contains(",");
        boolean containsQuote = field.contains("\"");
        boolean containsNewline = field.contains("\n") || field.contains("\r");

        if (containsComma || containsQuote || containsNewline) {
            String escapedQuote = field.replace("\"", "\"\"");
            return "\"" + escapedQuote + "\"";
        }
        return field;
    }
}
