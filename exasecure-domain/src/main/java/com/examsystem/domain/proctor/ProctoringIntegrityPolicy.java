package com.examsystem.domain.proctor;

import java.util.Locale;

/**
 * Authoritative examination integrity classification derived from persisted proctoring state.
 * Any acknowledged qualifying violation (one or more strikes, a persisted FOCUS_LOST incident,
 * or a persisted flag) classifies the attempt as FLAGGED rather than CLEAN.
 */
public final class ProctoringIntegrityPolicy {
    public static final String STATUS_CLEAN = "CLEAN";
    public static final String STATUS_FLAGGED = "FLAGGED";

    public record IntegrityClassification(boolean flagged, String proctoringStatus) {}

    private ProctoringIntegrityPolicy() {}

    public static IntegrityClassification classify(
            int focusLostCount,
            boolean hasQualifyingIncident,
            boolean persistedFlagged,
            String persistedProctoringStatus) {
        String normalized = persistedProctoringStatus == null
                ? ""
                : persistedProctoringStatus.trim().toUpperCase(Locale.ROOT);
        boolean flagged = focusLostCount > 0
                || hasQualifyingIncident
                || persistedFlagged
                || STATUS_FLAGGED.equals(normalized);
        return new IntegrityClassification(flagged, flagged ? STATUS_FLAGGED : STATUS_CLEAN);
    }
}
