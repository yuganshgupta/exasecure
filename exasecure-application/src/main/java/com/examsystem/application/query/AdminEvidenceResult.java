package com.examsystem.application.query;

public record AdminEvidenceResult(
        int id,
        int eventId,
        String contentType,
        byte[] data
) {
    public AdminEvidenceResult {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
