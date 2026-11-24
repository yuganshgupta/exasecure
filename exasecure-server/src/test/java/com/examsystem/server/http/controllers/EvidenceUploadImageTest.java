package com.examsystem.server.http.controllers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceUploadImageTest {
    @Test
    void enforcesPixelAndAxisLimitsBeforeDecode() {
        assertThat(EvidenceUploadImage.allowedDimensions(5120, 2880)).isTrue();
        assertThat(EvidenceUploadImage.allowedDimensions(5000, 5000)).isFalse();
        assertThat(EvidenceUploadImage.allowedDimensions(8193, 1)).isFalse();
        assertThat(EvidenceUploadImage.allowedDimensions(100, 0)).isFalse();
        assertThat(EvidenceUploadImage.allowedDimensions(Integer.MAX_VALUE, Integer.MAX_VALUE)).isFalse();
    }
}
