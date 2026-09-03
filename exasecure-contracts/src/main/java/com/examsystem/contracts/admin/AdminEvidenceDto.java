package com.examsystem.contracts.admin;

public record AdminEvidenceDto(
        int id,
        int eventId,
        String contentType,
        byte[] data,
        String evidenceType
) {
    public AdminEvidenceDto(int id, int eventId, String contentType, byte[] data) {
        this(id, eventId, contentType, data, "INITIAL");
    }

    public AdminEvidenceDto {
        if (data != null) {
            data = data.clone();
        }
    }

    @Override
    public byte[] data() {
        return data != null ? data.clone() : null;
    }
}
