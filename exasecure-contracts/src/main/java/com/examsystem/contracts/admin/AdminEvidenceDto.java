package com.examsystem.contracts.admin;

public record AdminEvidenceDto(int id, int eventId, String contentType, byte[] data) {}
