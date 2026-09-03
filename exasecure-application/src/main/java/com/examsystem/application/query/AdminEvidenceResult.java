package com.examsystem.application.query;

public record AdminEvidenceResult(
        int id,
        int eventId,
        String contentType,
        byte[] data,
        String evidenceType
) {
    public AdminEvidenceResult(int id, int eventId, String contentType, byte[] data) {
        this(id, eventId, contentType, data, "INITIAL");
    }

    public AdminEvidenceResult {
        if (data != null) {
            data = data.clone();
        }
    }

    @Override
    public byte[] data() {
        return data != null ? data.clone() : null;
    }
}
