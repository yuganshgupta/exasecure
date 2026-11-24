package com.examsystem.domain.proctor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProctoringIntegrityPolicyTest {

    @Test
    void zeroIncidentsAndNoFlagsClassifyAsClean() {
        var result = ProctoringIntegrityPolicy.classify(0, false, false, "CLEAN");
        assertThat(result.flagged()).isFalse();
        assertThat(result.proctoringStatus()).isEqualTo("CLEAN");

        var blankResult = ProctoringIntegrityPolicy.classify(0, false, false, null);
        assertThat(blankResult.flagged()).isFalse();
        assertThat(blankResult.proctoringStatus()).isEqualTo("CLEAN");
    }

    @Test
    void oneOrTwoStrikesClassifyAsFlaggedEvenWhenPersistedTerminationFlagIsFalse() {
        var oneStrike = ProctoringIntegrityPolicy.classify(1, true, false, "CLEAN");
        assertThat(oneStrike.flagged()).isTrue();
        assertThat(oneStrike.proctoringStatus()).isEqualTo("FLAGGED");

        var twoStrikes = ProctoringIntegrityPolicy.classify(2, true, false, "CLEAN");
        assertThat(twoStrikes.flagged()).isTrue();
        assertThat(twoStrikes.proctoringStatus()).isEqualTo("FLAGGED");
    }

    @Test
    void threeStrikesOrPersistedFlagClassifyAsFlagged() {
        var threeStrikes = ProctoringIntegrityPolicy.classify(3, true, true, "FLAGGED");
        assertThat(threeStrikes.flagged()).isTrue();
        assertThat(threeStrikes.proctoringStatus()).isEqualTo("FLAGGED");

        var persistedFlagOnly = ProctoringIntegrityPolicy.classify(0, false, true, "CLEAN");
        assertThat(persistedFlagOnly.flagged()).isTrue();
        assertThat(persistedFlagOnly.proctoringStatus()).isEqualTo("FLAGGED");

        var persistedStatusOnly = ProctoringIntegrityPolicy.classify(0, false, false, "flagged");
        assertThat(persistedStatusOnly.flagged()).isTrue();
        assertThat(persistedStatusOnly.proctoringStatus()).isEqualTo("FLAGGED");
    }

    @Test
    void persistedQualifyingIncidentAloneClassifiesAsFlagged() {
        var incidentOnly = ProctoringIntegrityPolicy.classify(0, true, false, "CLEAN");
        assertThat(incidentOnly.flagged()).isTrue();
        assertThat(incidentOnly.proctoringStatus()).isEqualTo("FLAGGED");
    }
}
